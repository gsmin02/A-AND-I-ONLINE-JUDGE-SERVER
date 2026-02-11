# PLAN.md — 함수형 코드 판정(Judge) API 서버 구축 계획

## 1. 기술 스택

| 레이어 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | Spring Boot 4.x + Spring WebFlux | Kotlin 코루틴 네이티브 지원, Reactive 스트리밍 |
| 언어 / 비동기 | Kotlin 2.x + kotlinx-coroutines-reactor | `suspend` 함수 기반 비동기, Reactor 브리지 |
| 실시간 통신 | SSE (`ServerSentEvent<T>`) | WebFlux 내장, HTTP/1.1 호환, 서버→클라이언트 스트리밍 |
| 메시지 브로커 | Redis Pub/Sub (Reactive) | 워커 코루틴 ↔ SSE 핸들러 브리지 |
| DB | MongoDB (Spring Data MongoDB Reactive) | 스키마 유연성, Reactive 드라이버 |
| 샌드박스 | Docker (ProcessBuilder + Dispatchers.IO) | 언어별 격리 실행, JVM 외부 프로세스 비동기 래핑 |
| 빌드 | Gradle + Kotlin DSL, JDK 21 | |

### 보류/추후 고려
- **WebSocket**: SSE로 충분하지 않은 양방향 케이스 발생 시 전환 (starter는 이미 포함)
- **gVisor (runsc)**: Phase 5에서 Docker 대체 런타임으로 선택적 적용

---

## 2. 시스템 아키텍처

```
Client
  │
  ├─ POST /v1/submissions          ├─ GET /v1/submissions/{id}/stream (SSE)
  │
  ▼
┌─────────────────────────────────────┐
│   Spring WebFlux (SubmissionController)  │
│   suspend fun submit()              │
│   fun streamResults(): Flow<SSE>    │
└────────────────┬────────────────────┘
                 │
        ┌────────┴────────┐
        ▼                 ▼
┌───────────────┐  ┌─────────────────────────┐
│   MongoDB     │  │   SubmissionService      │
│  (Submission  │  │   - save() / findById()  │
│   이력 저장)  │  │   - launch judge coroutine│
└───────────────┘  └───────────┬─────────────┘
                               │ Dispatchers.IO
                               ▼
                   ┌─────────────────────────┐
                   │   JudgeWorker (coroutine)│
                   │   - 테스트 케이스 순회   │
                   │   - SandboxRunner.run()  │
                   │   - Redis PUBLISH        │
                   └───────────┬─────────────┘
                               │ docker run (ProcessBuilder)
                               ▼
                   ┌─────────────────────────┐
                   │   Sandbox Container      │
                   │  (Python / Kotlin / Dart)│
                   │   stdin: JSON args       │
                   │   stdout: JSON result    │
                   └─────────────────────────┘

  Redis Pub/Sub 흐름:
  JudgeWorker ──PUBLISH──▶ Redis ──SUBSCRIBE──▶ SSE handler ──stream──▶ Client
```

---

## 3. 프로젝트 구조

```
src/main/kotlin/com/aandiclub/online/judge/
├── Application.kt
├── config/
│   └── AppConfig.kt           # SandboxProperties, Redis beans
├── domain/
│   ├── Submission.kt          # @Document (MongoDB)
│   ├── TestCaseResult.kt      # 내장 도큐먼트
│   └── enums.kt               # Language, SubmissionStatus, TestCaseStatus
├── repository/
│   └── SubmissionRepository.kt  # ReactiveMongoRepository
├── api/
│   ├── SubmissionController.kt  # POST /v1/submissions, GET .../stream
│   └── dto/
│       ├── SubmissionRequest.kt
│       └── SubmissionResponse.kt
├── service/
│   └── SubmissionService.kt   # 제출 저장, 코루틴 워커 기동, SSE 스트림
├── worker/
│   └── JudgeWorker.kt         # Phase 3: 테스트 케이스 실행 및 결과 발행
└── sandbox/
    └── SandboxRunner.kt       # Phase 1: Docker ProcessBuilder 래퍼

docker/
├── docker-compose.yml         # MongoDB + Redis (로컬 개발 인프라)
└── sandbox/
    ├── python/Dockerfile      # Phase 1
    ├── kotlin/Dockerfile      # Phase 1
    └── dart/Dockerfile        # Phase 1
```

---

## 4. 코루틴 실행 모델

### HTTP 요청 처리 (WebFlux + 코루틴)
```kotlin
// Controller: suspend 함수 → WebFlux가 자동으로 Mono로 래핑
@PostMapping
suspend fun submit(@Valid @RequestBody req: SubmissionRequest): ResponseEntity<SubmissionAccepted>

// SSE 스트림: Flow<ServerSentEvent<String>> → WebFlux가 Flux로 변환
@GetMapping("/{id}/stream", produces = [TEXT_EVENT_STREAM_VALUE])
fun streamResults(@PathVariable id: String): Flow<ServerSentEvent<String>>
```

### 백그라운드 워커 (Phase 3)
```kotlin
// SubmissionService.createSubmission() 내부
coroutineScope.launch(Dispatchers.IO) {
    judgeWorker.execute(submissionId, code, language, testCases)
}

// JudgeWorker.execute()
testCases.forEach { tc ->
    val output = sandboxRunner.run(language, SandboxInput(code, tc.args))
    redisTemplate.convertAndSend("submission:$submissionId", output.toJson()).awaitSingle()
}
```

### SSE 구독 (Phase 4)
```kotlin
// Redis Pub/Sub → Flow<ServerSentEvent>
listenerContainer.receive(ChannelTopic.of("submission:$submissionId"))
    .asFlow()
    .map { msg -> ServerSentEvent.builder<String>().data(msg.message).build() }
```

---

## 5. 구현 단계별 핵심 목표

### Phase 0 — 프로젝트 기반 ✅
`build.gradle.kts`, `application.yaml`, `docker-compose.yml`, 패키지 골격.

### Phase 1 — Docker 샌드박스 실행 래퍼
언어별 Docker 이미지 + `SandboxRunner` ProcessBuilder 구현.
`solution(args) → JSON stdout` 파이프라인 완성 및 격리 실행 검증.

격리 설정:
```
--network none
--cpus {cpuLimit}  --memory {memoryLimitMb}m
--read-only  --no-new-privileges  --pids-limit {pidsLimit}
```

### Phase 2 — 성능 측정 모듈
- Python: `tracemalloc` + `time.perf_counter_ns()` (컨테이너 내부)
- Kotlin/Dart: 언어 내부 타이머 + Docker Stats API (컨테이너 외부 메모리)

### Phase 3 — 비동기 워커 (코루틴 기반)
`JudgeWorker` 코루틴으로 테스트 케이스 순차 실행 → Redis PUBLISH.
`CoroutineScope` + `SupervisorJob`으로 워커 생명주기 관리.

### Phase 4 — SSE 실시간 스트리밍
Redis SUBSCRIBE → `Flow<ServerSentEvent<String>>` 변환 완성.
`Last-Event-ID` 지원으로 클라이언트 재연결 시 미수신 이벤트 재전송.

### Phase 5 — 보안 강화 및 마무리
입력 검증, Rate Limiting, 구조적 로깅, `/health` 엔드포인트, gVisor 옵션 문서화.

---

## 6. API 명세 요약

### POST /v1/submissions
```json
Request:
{
  "problemId": "quiz-101",
  "language": "PYTHON",
  "code": "def solution(a, b):\n    return a + b",
  "options": { "realtimeFeedback": true }
}

Response 202 Accepted:
{
  "submissionId": "uuid-...",
  "streamUrl": "/v1/submissions/uuid-.../stream"
}
```

### GET /v1/submissions/{submissionId}/stream  (SSE)
```
event: test_case_result
data: {"caseId":1,"status":"PASSED","timeMs":12.3,"memoryMb":4.2}

event: test_case_result
data: {"caseId":2,"status":"WRONG_ANSWER","timeMs":8.1,"memoryMb":3.9}

event: done
data: {"submissionId":"uuid-...","overallStatus":"WRONG_ANSWER"}
```

### GET /v1/submissions/{submissionId}
```json
{
  "submissionId": "uuid-...",
  "status": "ACCEPTED",
  "testCases": [
    { "caseId": 1, "status": "PASSED", "timeMs": 12.3, "memoryMb": 4.2 }
  ]
}
```

---

## 7. 보안 위협 모델 및 대응

| 위협 | 대응 |
|---|---|
| 악성 코드의 시스템 파일 접근 | `--read-only` + tmpfs 마운트 |
| 무한 루프 / CPU 독점 | `--cpus` + `withTimeoutOrNull` (코루틴 타임아웃) |
| 메모리 폭탄 (fork bomb 포함) | `--memory` + `--pids-limit` |
| 인터넷 통신 시도 | `--network none` |
| 권한 상승 | `--no-new-privileges` + 비root 사용자 |
| 코드 인젝션 | `@Valid` + UUID 기반 임시 파일명 |
