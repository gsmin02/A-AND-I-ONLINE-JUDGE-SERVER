package com.aandiclub.online.judge.service

import com.aandiclub.online.judge.api.dto.ProblemTestCaseRecord
import com.aandiclub.online.judge.repository.ProblemRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service

@Service
class ProblemService(
    private val problemRepository: ProblemRepository,
) {
    suspend fun getAllProblemsWithTestCases(): List<ProblemTestCaseRecord> =
        problemRepository.findAll()
            .collectList()
            .awaitSingle()
            .map { problem ->
                ProblemTestCaseRecord(
                    problemId = problem.problemId,
                    testCases = problem.testCases,
                    updatedAt = problem.updatedAt,
                )
            }
}
