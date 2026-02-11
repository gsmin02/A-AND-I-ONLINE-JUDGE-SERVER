package com.aandiclub.online.judge.sandbox

import com.aandiclub.online.judge.config.SandboxProperties
import com.aandiclub.online.judge.domain.Language
import com.aandiclub.online.judge.domain.TestCaseStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SandboxRunnerTest {

    private val properties = SandboxProperties(
        timeLimitSeconds = 1,
        memoryLimitMb = 128,
        cpuLimit = "1.0",
        pidsLimit = 50,
        images = mapOf(
            "python" to "judge-sandbox-python:latest",
            "kotlin" to "judge-sandbox-kotlin:latest",
            "dart"   to "judge-sandbox-dart:latest",
        ),
    )

    private val runner = SandboxRunner(properties)

    // ── 설정 로드 ─────────────────────────────────────────────────────────

    @Test
    fun `sandbox properties are loaded correctly`() {
        assertEquals(1, properties.timeLimitSeconds)
        assertEquals(128, properties.memoryLimitMb)
        assertEquals("1.0", properties.cpuLimit)
        assertEquals(50, properties.pidsLimit)
        assertEquals("judge-sandbox-python:latest", properties.images["python"])
        assertEquals("judge-sandbox-kotlin:latest", properties.images["kotlin"])
        assertEquals("judge-sandbox-dart:latest", properties.images["dart"])
    }

    // ── 미구현 단계 동작 확인 (Phase 1 구현 전) ───────────────────────────

    @Test
    fun `run throws NotImplementedError before Phase 1 is implemented`() = runTest {
        val input = SandboxInput(
            code = "def solution(a, b): return a + b",
            args = listOf(3, 5),
        )

        assertThrows<NotImplementedError> {
            runner.run(Language.PYTHON, input)
        }
    }

    @Test
    fun `run throws NotImplementedError for kotlin before Phase 1`() = runTest {
        val input = SandboxInput(
            code = "fun solution(a: Int, b: Int) = a + b",
            args = listOf(3, 5),
        )

        assertThrows<NotImplementedError> {
            runner.run(Language.KOTLIN, input)
        }
    }

    // ── 미설정 언어 처리 ──────────────────────────────────────────────────

    @Test
    fun `run throws IllegalStateException when language image is not configured`() = runTest {
        val emptyProps = SandboxProperties(images = emptyMap())
        val runnerWithNoImages = SandboxRunner(emptyProps)

        val input = SandboxInput(code = "def solution(): pass", args = emptyList())

        val ex = assertThrows<IllegalStateException> {
            runnerWithNoImages.run(Language.PYTHON, input)
        }
        assert(ex.message!!.contains("PYTHON"))
    }

    // ── SandboxInput / SandboxOutput 데이터 클래스 ────────────────────────

    @Test
    fun `SandboxInput stores code and args correctly`() {
        val input = SandboxInput(
            code = "def solution(x): return x * 2",
            args = listOf(42),
        )
        assertEquals("def solution(x): return x * 2", input.code)
        assertEquals(listOf(42), input.args)
    }

    @Test
    fun `SandboxOutput with TIME_LIMIT_EXCEEDED has correct status`() {
        val output = SandboxOutput(
            status = TestCaseStatus.TIME_LIMIT_EXCEEDED,
            output = null,
            error = "Exceeded 1s limit",
            timeMs = 1000.0,
            memoryMb = 0.0,
        )
        assertEquals(TestCaseStatus.TIME_LIMIT_EXCEEDED, output.status)
        assertNotNull(output.error)
    }
}
