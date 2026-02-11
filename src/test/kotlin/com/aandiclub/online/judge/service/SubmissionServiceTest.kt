package com.aandiclub.online.judge.service

import com.aandiclub.online.judge.api.dto.SubmissionRequest
import com.aandiclub.online.judge.domain.Language
import com.aandiclub.online.judge.domain.Submission
import com.aandiclub.online.judge.domain.SubmissionStatus
import com.aandiclub.online.judge.repository.SubmissionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Mono

class SubmissionServiceTest {

    private val submissionRepository = mockk<SubmissionRepository>()
    private val redisTemplate = mockk<ReactiveStringRedisTemplate>()
    private val listenerContainer = mockk<ReactiveRedisMessageListenerContainer>()

    private val service = SubmissionService(submissionRepository, redisTemplate, listenerContainer)

    // ── createSubmission ──────────────────────────────────────────────────

    @Test
    fun `createSubmission saves submission and returns SubmissionAccepted`() = runTest {
        val request = SubmissionRequest(
            problemId = "quiz-101",
            language = Language.PYTHON,
            code = "def solution(a, b):\n    return a + b",
        )
        val savedSubmission = Submission(
            id = "saved-uuid",
            problemId = "quiz-101",
            language = Language.PYTHON,
            code = "def solution(a, b):\n    return a + b",
        )

        val savedSlot = slot<Submission>()
        every { submissionRepository.save(capture(savedSlot)) } returns Mono.just(savedSubmission)

        val result = service.createSubmission(request)

        assertEquals("saved-uuid", result.submissionId)
        assertEquals("/v1/submissions/saved-uuid/stream", result.streamUrl)

        verify(exactly = 1) { submissionRepository.save(any()) }
        val captured = savedSlot.captured
        assertEquals("quiz-101", captured.problemId)
        assertEquals(Language.PYTHON, captured.language)
        assertEquals(SubmissionStatus.PENDING, captured.status)
    }

    @Test
    fun `createSubmission sets initial status to PENDING`() = runTest {
        val request = SubmissionRequest(
            problemId = "quiz-202",
            language = Language.KOTLIN,
            code = "fun solution(a: Int, b: Int) = a + b",
        )
        val capturedSlot = slot<Submission>()
        every { submissionRepository.save(capture(capturedSlot)) } answers {
            Mono.just(capturedSlot.captured)
        }

        service.createSubmission(request)

        assertEquals(SubmissionStatus.PENDING, capturedSlot.captured.status)
    }

    // ── getResult ─────────────────────────────────────────────────────────

    @Test
    fun `getResult returns null when submission does not exist`() = runTest {
        every { submissionRepository.findById("nonexistent") } returns Mono.empty()

        val result = service.getResult("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getResult returns SubmissionResult when submission exists`() = runTest {
        val submission = Submission(
            id = "existing-uuid",
            problemId = "quiz-303",
            language = Language.DART,
            code = "int solution(int a, int b) => a + b;",
            status = SubmissionStatus.ACCEPTED,
        )
        every { submissionRepository.findById("existing-uuid") } returns Mono.just(submission)

        val result = service.getResult("existing-uuid")

        assertNotNull(result)
        assertEquals("existing-uuid", result!!.submissionId)
        assertEquals(SubmissionStatus.ACCEPTED, result.status)
        assertEquals(emptyList<Nothing>(), result.testCases)
    }
}
