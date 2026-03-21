package com.aandiclub.online.judge.service

import com.aandiclub.online.judge.domain.User
import com.aandiclub.online.judge.repository.SubmissionRepository
import com.aandiclub.online.judge.repository.UserRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant

enum class UserEventSyncOutcome {
    UPSERTED,
    DELETED,
    SKIPPED,
}

@Service
class UserEventSyncService(
    private val userRepository: UserRepository,
    private val submissionRepository: SubmissionRepository,
    private val mongoTemplate: ReactiveMongoTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(UserEventSyncService::class.java)

    suspend fun sync(rawBody: String): UserEventSyncOutcome {
        val root = runCatching { objectMapper.readTree(rawBody) }
            .getOrElse { ex ->
                log.warn("Skipping user event: invalid JSON body ({})", ex.message)
                return UserEventSyncOutcome.SKIPPED
            }

        val payload = unwrapSnsEnvelope(root) ?: return UserEventSyncOutcome.SKIPPED

        val eventType = payload.get("type")?.asText()
        if (eventType.isNullOrBlank()) {
            log.debug("Skipping user event: no type field")
            return UserEventSyncOutcome.SKIPPED
        }

        return when (eventType) {
            "UserProfileUpdated" -> handleUserProfileUpdated(payload)
            "UserDeleted" -> handleUserDeleted(payload)
            else -> {
                log.debug("Skipping user event: unknown type {}", eventType)
                UserEventSyncOutcome.SKIPPED
            }
        }
    }

    private suspend fun handleUserProfileUpdated(payload: JsonNode): UserEventSyncOutcome {
        val userId = payload.get("userId")?.asText()
        if (userId.isNullOrBlank()) {
            log.debug("Skipping user event: no userId field")
            return UserEventSyncOutcome.SKIPPED
        }

        val username = payload.get("username")?.asText()
        if (username.isNullOrBlank()) {
            log.debug("Skipping user event: no username field")
            return UserEventSyncOutcome.SKIPPED
        }

        val publicCode = payload.get("publicCode")?.asText()
        if (publicCode.isNullOrBlank()) {
            log.debug("Skipping user event: no publicCode field")
            return UserEventSyncOutcome.SKIPPED
        }

        // Check if publicCode changed
        val existingUser = userRepository.findById(userId).awaitSingleOrNull()
        val publicCodeChanged = existingUser != null && existingUser.publicCode != publicCode

        val user = User(
            userId = userId,
            publicCode = publicCode,
            username = username,
            nickname = payload.get("nickname")?.asText(),
            role = payload.get("role")?.asText(),
            userTrack = payload.get("userTrack")?.asText(),
            cohort = payload.get("cohort")?.asInt(),
            cohortOrder = payload.get("cohortOrder")?.asInt(),
            profileImageUrl = payload.get("profileImageUrl")?.asText(),
            version = payload.get("version")?.asInt(),
            updatedAt = Instant.now(),
        )

        userRepository.save(user).awaitSingle()

        // Update submissions if publicCode changed
        if (publicCodeChanged) {
            updateSubmissionPublicCodes(userId, publicCode)
        }

        log.info("User profile upserted: userId={}, publicCode={}, username={}", userId, publicCode, username)
        return UserEventSyncOutcome.UPSERTED
    }

    private suspend fun handleUserDeleted(payload: JsonNode): UserEventSyncOutcome {
        val userId = payload.get("userId")?.asText()
        if (userId.isNullOrBlank()) {
            log.debug("Skipping user delete event: no userId field")
            return UserEventSyncOutcome.SKIPPED
        }

        // Delete user
        userRepository.deleteById(userId).awaitSingleOrNull()

        // Update submissions to use legacy values
        val query = Query.query(Criteria.where("submitterId").`is`(userId))
        val update = Update()
            .set("submitterId", "deleted-${userId}")
            .set("submitterPublicCode", "DELETED")

        val result = mongoTemplate.updateMulti(query, update, "submissions").awaitSingle()

        log.info("User deleted: userId={}, submissions updated={}", userId, result.modifiedCount)
        return UserEventSyncOutcome.DELETED
    }

    private suspend fun updateSubmissionPublicCodes(userId: String, newPublicCode: String) {
        val query = Query.query(Criteria.where("submitterId").`is`(userId))
        val update = Update().set("submitterPublicCode", newPublicCode)

        val result = mongoTemplate.updateMulti(query, update, "submissions").awaitSingle()

        log.info("Updated submission publicCodes: userId={}, newPublicCode={}, count={}",
            userId, newPublicCode, result.modifiedCount)
    }

    private fun unwrapSnsEnvelope(root: JsonNode): JsonNode? {
        val messageNode = root.get("Message") ?: return root
        return when {
            messageNode.isTextual -> runCatching { objectMapper.readTree(messageNode.asText()) }.getOrNull()
            messageNode.isObject -> messageNode
            else -> null
        }
    }
}
