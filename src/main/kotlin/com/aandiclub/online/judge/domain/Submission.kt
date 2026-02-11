package com.aandiclub.online.judge.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.Instant
import java.util.UUID

@Document(collection = "submissions")
data class Submission(
    @Id val id: String = UUID.randomUUID().toString(),
    val problemId: String,
    val language: Language,
    @Field("code") val code: String,
    var status: SubmissionStatus = SubmissionStatus.PENDING,
    var testCaseResults: List<TestCaseResult> = emptyList(),
    val createdAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
)
