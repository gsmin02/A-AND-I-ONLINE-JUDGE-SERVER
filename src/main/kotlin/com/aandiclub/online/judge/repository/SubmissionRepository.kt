package com.aandiclub.online.judge.repository

import com.aandiclub.online.judge.domain.Submission
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface SubmissionRepository : ReactiveMongoRepository<Submission, String> {
    fun findAllByOrderByCreatedAtDesc(): Flux<Submission>
    fun findAllBySubmitterIdAndProblemIdOrderByCreatedAtDesc(submitterId: String, problemId: String): Flux<Submission>
}
