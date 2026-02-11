package com.aandiclub.online.judge.service

import com.aandiclub.online.judge.api.dto.SubmissionAccepted
import com.aandiclub.online.judge.api.dto.SubmissionRequest
import com.aandiclub.online.judge.api.dto.SubmissionResult
import com.aandiclub.online.judge.domain.Submission
import com.aandiclub.online.judge.repository.SubmissionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service

@Service
class SubmissionService(
    private val submissionRepository: SubmissionRepository,
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
) {
    private val log = LoggerFactory.getLogger(SubmissionService::class.java)

    suspend fun createSubmission(request: SubmissionRequest): SubmissionAccepted {
        val submission = Submission(
            problemId = request.problemId,
            language = request.language,
            code = request.code,
        )
        val saved = submissionRepository.save(submission).awaitSingle()
        log.info("Submission created: id={}, language={}", saved.id, saved.language)

        // TODO Phase 3: 워커 코루틴에 judge 태스크 디스패치
        return SubmissionAccepted(
            submissionId = saved.id,
            streamUrl = "/v1/submissions/${saved.id}/stream",
        )
    }

    /**
     * TODO Phase 4: Redis Pub/Sub 채널 "submission:{id}" 을 구독하여
     * 테스트 케이스 결과를 SSE 이벤트로 변환해 Flow로 방출.
     *
     * listenerContainer.receive(ChannelTopic.of("submission:$submissionId"))
     *     .asFlow()
     *     .map { msg -> ServerSentEvent.builder<String>().event("test_case_result").data(msg).build() }
     */
    fun streamResults(submissionId: String): Flow<ServerSentEvent<String>> = flow {
        // Phase 4 구현 전 placeholder — 스트림 즉시 종료
    }

    suspend fun getResult(submissionId: String): SubmissionResult? {
        val submission = submissionRepository.findById(submissionId).awaitSingleOrNull()
            ?: return null
        return SubmissionResult(
            submissionId = submission.id,
            status = submission.status,
            testCases = submission.testCaseResults,
        )
    }
}
