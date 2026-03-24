package com.aandiclub.online.judge.api

import com.aandiclub.online.judge.api.dto.AdminTestCaseRecord
import com.aandiclub.online.judge.api.dto.ProblemTestCaseRecord
import com.aandiclub.online.judge.service.ProblemService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class AdminTestCaseControllerTest {
    private val problemService = mockk<ProblemService>()
    private val controller = AdminTestCaseController(problemService)

    @Test
    fun `getAllTestCases returns all problem test case records`() = runTest {
        val records = listOf(
            ProblemTestCaseRecord(
                problemId = "quiz-101",
                testCases = listOf(
                    AdminTestCaseRecord(
                        caseId = 1,
                        args = listOf(3, 5),
                        argTypes = listOf("INTEGER", "INTEGER"),
                        expectedOutput = 8,
                        expectedOutputType = "INTEGER",
                    ),
                    AdminTestCaseRecord(
                        caseId = 2,
                        args = listOf(10, 20),
                        argTypes = listOf("INTEGER", "INTEGER"),
                        expectedOutput = 30,
                        expectedOutputType = "INTEGER",
                    ),
                ),
                updatedAt = Instant.parse("2026-03-15T10:00:00Z"),
            ),
            ProblemTestCaseRecord(
                problemId = "quiz-102",
                testCases = listOf(
                    AdminTestCaseRecord(
                        caseId = 1,
                        args = listOf("hello"),
                        argTypes = listOf("STRING"),
                        expectedOutput = "HELLO",
                        expectedOutputType = "STRING",
                    ),
                ),
                updatedAt = Instant.parse("2026-03-15T11:00:00Z"),
            ),
        )
        coEvery { problemService.getAllProblemsWithTestCases() } returns records

        val response = controller.getAllTestCases()

        assertEquals(200, response.statusCode.value())
        assertEquals(records, response.body)
        coVerify(exactly = 1) { problemService.getAllProblemsWithTestCases() }
    }

    @Test
    fun `getAllTestCases returns empty list when no problems exist`() = runTest {
        coEvery { problemService.getAllProblemsWithTestCases() } returns emptyList()

        val response = controller.getAllTestCases()

        assertEquals(200, response.statusCode.value())
        assertEquals(emptyList<ProblemTestCaseRecord>(), response.body)
        coVerify(exactly = 1) { problemService.getAllProblemsWithTestCases() }
    }
}
