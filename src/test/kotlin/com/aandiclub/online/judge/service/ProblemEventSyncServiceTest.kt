package com.aandiclub.online.judge.service

import com.aandiclub.online.judge.domain.Problem
import com.aandiclub.online.judge.repository.ProblemRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

class ProblemEventSyncServiceTest {
    private val problemRepository = mockk<ProblemRepository>()
    private val objectMapper = ObjectMapper()
    private val testCaseEventPublisher = mockk<TestCaseEventPublisher>(relaxed = true)
    private val service = ProblemEventSyncService(problemRepository, objectMapper, testCaseEventPublisher)

    @Test
    fun `sync upserts problem test cases from SNS envelope`() = runTest {
        val savedSlot = slot<Problem>()
        every { problemRepository.save(capture(savedSlot)) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "Type":"Notification",
              "Message":"{\"eventType\":\"PROBLEM_CREATED\",\"problemId\":\"prob-uuid-1\",\"testCases\":[{\"caseId\":1,\"input\":[3,5],\"output\":8},{\"caseId\":2,\"input\":[10,2],\"output\":12}]}"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.UPSERTED, outcome)
        assertEquals("prob-uuid-1", savedSlot.captured.problemId)
        assertEquals(2, savedSlot.captured.testCases.size)
        assertEquals(listOf(3, 5), savedSlot.captured.testCases[0].args)
        assertEquals(8, savedSlot.captured.testCases[0].expectedOutput)
    }

    @Test
    fun `sync skips payload when problem id is missing`() = runTest {
        val raw = """
            {
              "eventType":"PROBLEM_CREATED",
              "testCases":[{"caseId":1,"input":[1,2],"output":3}]
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.SKIPPED, outcome)
        verify(exactly = 0) { problemRepository.save(any()) }
    }

    @Test
    fun `sync processes PROBLEM_UPDATED event`() = runTest {
        val savedSlot = slot<Problem>()
        every { problemRepository.save(capture(savedSlot)) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "eventType":"PROBLEM_UPDATED",
              "problemId":"prob-uuid-2",
              "testCases":[{"caseId":1,"input":[1,2],"output":3}]
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.UPSERTED, outcome)
        assertEquals("prob-uuid-2", savedSlot.captured.problemId)
    }

    @Test
    fun `sync processes TEST_CASE_UPDATED event`() = runTest {
        val savedSlot = slot<Problem>()
        every { problemRepository.save(capture(savedSlot)) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "eventType":"TEST_CASE_UPDATED",
              "problemId":"prob-uuid-3",
              "testCases":[{"caseId":1,"input":[5,5],"output":10}]
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.UPSERTED, outcome)
        assertEquals("prob-uuid-3", savedSlot.captured.problemId)
    }

    @Test
    fun `sync skips PROBLEM_DELETED event`() = runTest {
        val raw = """
            {
              "eventType":"PROBLEM_DELETED",
              "problemId":"prob-uuid-4",
              "testCases":[{"caseId":1,"input":[1,2],"output":3}]
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.SKIPPED, outcome)
        verify(exactly = 0) { problemRepository.save(any()) }
    }

    @Test
    fun `sync processes event without eventType for backward compatibility`() = runTest {
        val savedSlot = slot<Problem>()
        every { problemRepository.save(capture(savedSlot)) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "problemId":"prob-uuid-5",
              "testCases":[{"caseId":1,"input":[7,8],"output":15}]
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.UPSERTED, outcome)
        assertEquals("prob-uuid-5", savedSlot.captured.problemId)
    }

    @Test
    fun `sync publishes event after successful upsert`() = runTest {
        every { problemRepository.save(any()) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "eventType":"PROBLEM_CREATED",
              "problemId":"prob-uuid-6",
              "testCases":[{"caseId":1,"input":[1,2],"output":3},{"caseId":2,"input":[3,4],"output":7}]
            }
        """.trimIndent()

        service.sync(raw)

        coVerify { testCaseEventPublisher.publishTestCaseUpdated("prob-uuid-6", 2) }
    }

    @Test
    fun `sync infers scalar and structured values from string payloads`() = runTest {
        val savedSlot = slot<Problem>()
        every { problemRepository.save(capture(savedSlot)) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "eventType":"PROBLEM_CREATED",
              "problemId":"prob-uuid-typed",
              "testCases":[
                {
                  "caseId":"1",
                  "input":["1","2.5","true","null","[1,2]","{\"label\":\"sum\"}","hello","01"],
                  "output":"3"
                }
              ]
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(ProblemEventSyncOutcome.UPSERTED, outcome)
        assertEquals(listOf(1, 2.5, true, null, listOf(1, 2), mapOf("label" to "sum"), "hello", "01"), savedSlot.captured.testCases[0].args)
        assertEquals(3, savedSlot.captured.testCases[0].expectedOutput)
    }
}
