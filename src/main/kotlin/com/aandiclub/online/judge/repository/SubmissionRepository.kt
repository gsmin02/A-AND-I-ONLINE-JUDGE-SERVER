package com.aandiclub.online.judge.repository

import com.aandiclub.online.judge.domain.Submission
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository

@Repository
interface SubmissionRepository : ReactiveMongoRepository<Submission, String>
