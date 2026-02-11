package com.aandiclub.online.judge.sandbox

import com.aandiclub.online.judge.config.SandboxProperties
import com.aandiclub.online.judge.domain.Language
import com.aandiclub.online.judge.domain.TestCaseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class SandboxInput(
    val code: String,
    val args: List<Any?>,
)

data class SandboxOutput(
    val status: TestCaseStatus,
    val output: String?,
    val error: String?,
    val timeMs: Double,
    val memoryMb: Double,
)

@Component
class SandboxRunner(private val properties: SandboxProperties) {

    private val log = LoggerFactory.getLogger(SandboxRunner::class.java)

    suspend fun run(language: Language, input: SandboxInput): SandboxOutput =
        withContext(Dispatchers.IO) {
            val image = properties.images[language.value]
                ?: error("No sandbox image configured for language: $language")

            val startNs = System.nanoTime()

            val result = withTimeoutOrNull(properties.timeLimitSeconds * 1_000L) {
                runContainer(image, input)
            }

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

            result ?: SandboxOutput(
                status = TestCaseStatus.TIME_LIMIT_EXCEEDED,
                output = null,
                error = "Execution exceeded ${properties.timeLimitSeconds}s limit",
                timeMs = elapsedMs,
                memoryMb = 0.0,
            )
        }

    /**
     * Phase 1 구현 대상:
     * docker run --rm --network none
     *   --cpus {cpuLimit} --memory {memoryLimitMb}m
     *   --read-only --no-new-privileges --pids-limit {pidsLimit}
     *   -i {image}
     * stdin: JSON({ code, args })  →  stdout: JSON({ output, error, time_ms, memory_mb })
     */
    private fun runContainer(image: String, input: SandboxInput): SandboxOutput {
        TODO("Phase 1: implement Docker ProcessBuilder execution")
    }
}
