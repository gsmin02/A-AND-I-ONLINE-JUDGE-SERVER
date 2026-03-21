package com.aandiclub.online.judge.service

import com.aandiclub.online.judge.domain.User
import com.aandiclub.online.judge.repository.SubmissionRepository
import com.aandiclub.online.judge.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

class UserEventSyncServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val submissionRepository = mockk<SubmissionRepository>()
    private val mongoTemplate = mockk<ReactiveMongoTemplate>()
    private val objectMapper = ObjectMapper()
    private val service = UserEventSyncService(
        userRepository,
        submissionRepository,
        mongoTemplate,
        objectMapper
    )

    @Test
    fun `sync upserts user profile from SNS envelope`() = runTest {
        val savedSlot = slot<User>()
        every { userRepository.findById(any<String>()) } returns Mono.empty()
        every { userRepository.save(capture(savedSlot)) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "Type":"Notification",
              "Message":"{\"eventId\":\"8d8b5c7f-8b9d-4d7a-8d33-6f1b0f3d5a12\",\"type\":\"UserProfileUpdated\",\"occurredAt\":\"2026-03-19T03:12:45.123Z\",\"userId\":\"1d5b2b9d-2d74-4a1e-9fd9-3b7d6a9d6e11\",\"username\":\"user_07\",\"role\":\"USER\",\"userTrack\":\"NO\",\"cohort\":0,\"cohortOrder\":1,\"publicCode\":\"#NO001\",\"nickname\":\"new profile\",\"profileImageUrl\":\"https://images.aandiclub.com/new.png\",\"version\":1}"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.UPSERTED, outcome)
        assertEquals("1d5b2b9d-2d74-4a1e-9fd9-3b7d6a9d6e11", savedSlot.captured.userId)
        assertEquals("#NO001", savedSlot.captured.publicCode)
        assertEquals("user_07", savedSlot.captured.username)
        assertEquals("new profile", savedSlot.captured.nickname)
        assertEquals("USER", savedSlot.captured.role)
        assertEquals("NO", savedSlot.captured.userTrack)
        assertEquals(0, savedSlot.captured.cohort)
        assertEquals(1, savedSlot.captured.cohortOrder)
        assertEquals("https://images.aandiclub.com/new.png", savedSlot.captured.profileImageUrl)
        assertEquals(1, savedSlot.captured.version)
    }

    @Test
    fun `sync processes UserProfileUpdated event`() = runTest {
        every { userRepository.findById(any<String>()) } returns Mono.empty()
        every { userRepository.save(any()) } answers { Mono.just(firstArg()) }

        val raw = """
            {
              "type":"UserProfileUpdated",
              "userId":"user-789",
              "username":"testuser",
              "publicCode":"#TEST001"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.UPSERTED, outcome)
    }

    @Test
    fun `sync skips when userId is missing`() = runTest {
        val raw = """
            {
              "type":"UserProfileUpdated",
              "username":"testuser",
              "publicCode":"#TEST001"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.SKIPPED, outcome)
    }

    @Test
    fun `sync skips when username is missing`() = runTest {
        val raw = """
            {
              "type":"UserProfileUpdated",
              "userId":"user-123",
              "publicCode":"#TEST001"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.SKIPPED, outcome)
    }

    @Test
    fun `sync skips when publicCode is missing`() = runTest {
        val raw = """
            {
              "type":"UserProfileUpdated",
              "userId":"user-123",
              "username":"testuser"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.SKIPPED, outcome)
    }

    @Test
    fun `sync skips non-UserProfileUpdated events`() = runTest {
        val raw = """
            {
              "type":"PROBLEM_CREATED",
              "userId":"user-123",
              "username":"testuser",
              "publicCode":"#TEST001"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.SKIPPED, outcome)
    }

    @Test
    fun `sync skips event without type field`() = runTest {
        val raw = """
            {
              "userId":"user-123",
              "username":"testuser",
              "publicCode":"#TEST001"
            }
        """.trimIndent()

        val outcome = service.sync(raw)

        assertEquals(UserEventSyncOutcome.SKIPPED, outcome)
    }
}
