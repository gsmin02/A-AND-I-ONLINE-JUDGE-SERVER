package com.aandiclub.online.judge.domain

data class TestCaseResult(
    val caseId: Int,
    val status: TestCaseStatus,
    val timeMs: Double = 0.0,
    val memoryMb: Double = 0.0,
    val output: String? = null,
    val error: String? = null,
)
