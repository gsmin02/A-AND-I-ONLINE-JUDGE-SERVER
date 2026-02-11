[요구사항 명세서] 함수형 코드 판정(Judge) API 서버
1. 프로젝트 개요
   목적: 사용자가 제출한 Dart, Kotlin, Python 코드를 격리된 환경에서 실행하고, 각 테스트 케이스별 결과, 실행 시간, 메모리 사용량을 실시간으로 검증 및 반환함.

핵심 모델: 함수형 인터페이스 검증 (특정 함수에 파라미터를 주입하여 반환값을 체크).

2. 시스템 아키텍처 및 흐름
   API 레이어: 사용자로부터 코드와 문제 ID를 수신.

워커(Worker) 레이어: 비동기 큐를 통해 제출된 코드를 순차적/병렬 처리.

샌드박스(Sandbox): Docker 컨테이너를 사용하여 OS 및 네트워크와 격리된 환경에서 코드 실행.

프로파일러(Profiler): 각 테스트 케이스 실행 시 CPU 타임과 메모리 점유율 측정.

3. 기능적 요구사항 (Functional Requirements)
   3.1 언어별 실행 지원
   지원 언어: Python (3.x), Kotlin (JVM), Dart.

코드 형식: 사용자는 특정 이름을 가진 함수(예: solution)를 제출함.

환경 구축: 각 언어에 최적화된 런타임 환경(Docker Image) 사전 구성.

3.2 테스트 케이스 검증 (Validation)
입력 주입: JSON 형태의 테스트 케이스 데이터를 각 언어의 데이터 타입으로 변환하여 함수의 인자로 전달.

결과 비교: 함수의 반환값과 기대값(Expected Output)의 일치 여부 판정.

상태 정의:

ACCEPTED: 모든 테스트 케이스 통과.

WRONG_ANSWER: 반환값이 기대값과 불일치.

TIME_LIMIT_EXCEEDED: 설정된 제한 시간 초과.

MEMORY_LIMIT_EXCEEDED: 설정된 메모리 제한 초과.

RUNTIME_ERROR: 실행 중 예외 발생.

COMPILE_ERROR: 문법 오류 등으로 인한 실행 실패.

3.3 성능 측정 (Metrics)
연산 시간(Time): 각 테스트 케이스별 함수 호출부터 반환까지의 순수 소요 시간(ms) 측정.

메모리(Memory): 각 테스트 케이스 실행 중 도달한 피크 메모리 사용량(MB) 측정.

4. 비기능적 요구사항 (Non-Functional Requirements)
   4.1 보안 (Security)
   격리: Docker 또는 gVisor를 사용하여 커널 레벨 접근 차단.

네트워크: 컨테이너의 외부 인터넷 연결을 완전히 차단.

자원 제한:

CPU 사용률 제한 (예: 1 Core 미만).

최대 실행 시간 제한 (예: 케이스당 2초).

최대 메모리 제한 (예: 128MB~512MB).

4.2 응답성 (Responsiveness)
실시간 피드백: 전체 결과가 나올 때까지 대기하지 않고, 각 테스트 케이스가 완료될 때마다 SSE(Server-Sent Events) 또는 WebSocket을 통해 결과를 즉시 전송.

5. API 상세 명세
   5.1 제출 API (Endpoint: POST /v1/submissions)
   Request Body:

JSON
{
"problem_id": "quiz-101",
"language": "python",
"code": "def solution(a, b):\n    return a + b",
"options": {
"realtime_feedback": true
}
}
Response (Final Success):
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| submission_id | String | 제출 고유 식별자 |
| status | String | 전체 최종 상태 (ACCEPTED, WA 등) |
| test_cases | Array | 개별 테스트 케이스 결과 배열 |
| test_cases[].time_ms | Double | 해당 케이스 소요 시간 (ms) |
| test_cases[].memory_mb | Double | 해당 케이스 피크 메모리 (MB) |
| test_cases[].result | String | "PASSED" 또는 오류 메시지 |

6. 개발 로드맵
   1단계: Docker 기반의 언어별 코드 실행 래퍼(Wrapper) 개발.

2단계: tracemalloc(Python) 및 cgroup(OS) 연동을 통한 성능 측정 모듈 개발.

3단계: 비동기 처리를 위한 Celery/Redis 큐 시스템 구축.

4단계: 실시간 결과 전송을 위한 SSE/Websocket 레이어 구현.