package com.aandiclub.online.judge.api.dto

import com.aandiclub.online.judge.domain.Language
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class SubmissionRequest(
    @field:NotBlank val problemId: String,
    @field:NotNull val language: Language,
    @field:NotBlank @field:Size(max = 65_536) val code: String,
    val options: SubmissionOptions = SubmissionOptions(),
)

data class SubmissionOptions(
    val realtimeFeedback: Boolean = true,
)
