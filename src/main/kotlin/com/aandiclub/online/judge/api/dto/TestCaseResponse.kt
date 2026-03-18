package com.aandiclub.online.judge.api.dto

import com.aandiclub.online.judge.domain.TestCase
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Response containing test cases for a specific problem")
data class ProblemTestCaseRecord(
    @Schema(description = "Problem identifier", example = "quiz-101")
    val problemId: String,

    @Schema(description = "List of test cases for this problem")
    val testCases: List<TestCase>,

    @Schema(description = "Last update timestamp for this problem's test cases", example = "2026-03-15T10:00:00Z")
    val updatedAt: Instant,
)
