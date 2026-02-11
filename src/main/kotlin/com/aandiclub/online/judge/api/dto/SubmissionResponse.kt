package com.aandiclub.online.judge.api.dto

import com.aandiclub.online.judge.domain.SubmissionStatus
import com.aandiclub.online.judge.domain.TestCaseResult

data class SubmissionAccepted(
    val submissionId: String,
    val streamUrl: String,
)

data class SubmissionResult(
    val submissionId: String,
    val status: SubmissionStatus,
    val testCases: List<TestCaseResult>,
)
