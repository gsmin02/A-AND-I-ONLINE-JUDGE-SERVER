package com.aandiclub.online.judge.api

import com.aandiclub.online.judge.api.dto.SubmissionAccepted
import com.aandiclub.online.judge.api.dto.SubmissionRequest
import com.aandiclub.online.judge.api.dto.SubmissionResult
import com.aandiclub.online.judge.domain.Language
import com.aandiclub.online.judge.domain.SubmissionStatus
import com.aandiclub.online.judge.service.SubmissionService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.codec.ServerSentEvent
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class SubmissionControllerTest {

    private val submissionService = mockk<SubmissionService>()
    private val controller = SubmissionController(submissionService)

    @Test
    fun `submit calls service with jwt subject and returns 202`() = runTest {
        val exchange = exchange("/v1/submissions")
        exchange.attributes[JwtExchangeAttributes.SUBJECT] = "user-1"
        val request = SubmissionRequest(
            publicCode = "A00123",
            problemId = "quiz-101",
            language = Language.PYTHON,
            code = "def solution(a, b): return a + b",
        )
        val expected = SubmissionAccepted(
            submissionId = "test-uuid",
            streamUrl = "/v1/submissions/test-uuid/stream",
        )
        coEvery { submissionService.createSubmission(request, "user-1") } returns expected

        val response = controller.submit(request, exchange)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("test-uuid", response.body?.submissionId)
        coVerify(exactly = 1) { submissionService.createSubmission(request, "user-1") }
    }

    @Test
    fun `streamResults delegates to service with access context`() = runTest {
        val exchange = exchange("/v1/submissions/test-uuid/stream")
        exchange.attributes[JwtExchangeAttributes.SUBJECT] = "user-1"
        val event = ServerSentEvent.builder<String>()
            .event("test_case_result")
            .data("""{"caseId":1,"status":"PASSED"}""")
            .build()
        coEvery { submissionService.streamResults("test-uuid", "user-1", false) } returns flowOf(event)

        val flow = controller.streamResults("test-uuid", exchange)

        assertNotNull(flow)
        coVerify(exactly = 1) { submissionService.streamResults("test-uuid", "user-1", false) }
    }

    @Test
    fun `getResult returns 200 with body when submission exists`() = runTest {
        val exchange = exchange("/v1/submissions/test-uuid")
        exchange.attributes[JwtExchangeAttributes.SUBJECT] = "user-1"
        val result = SubmissionResult(
            submissionId = "test-uuid",
            status = SubmissionStatus.ACCEPTED,
            testCases = emptyList(),
        )
        coEvery { submissionService.getResult("test-uuid", "user-1", false) } returns result

        val response = controller.getResult("test-uuid", exchange)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(SubmissionStatus.ACCEPTED, response.body?.status)
    }

    @Test
    fun `getResult returns 404 when submission not found or not finished`() = runTest {
        val exchange = exchange("/v1/submissions/missing-id")
        exchange.attributes[JwtExchangeAttributes.SUBJECT] = "user-1"
        coEvery { submissionService.getResult("missing-id", "user-1", false) } returns null

        val response = controller.getResult("missing-id", exchange)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNull(response.body)
    }

    private fun exchange(path: String): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build())
}
