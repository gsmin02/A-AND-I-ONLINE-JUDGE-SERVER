package com.aandiclub.online.judge.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class User(
    @Id
    val userId: String,
    @Indexed(unique = true)
    val publicCode: String,
    val username: String,
    val nickname: String? = null,
    val role: String? = null,
    val userTrack: String? = null,
    val cohort: Int? = null,
    val cohortOrder: Int? = null,
    val profileImageUrl: String? = null,
    val version: Int? = null,
    val updatedAt: Instant = Instant.now(),
)
