package com.aandiclub.online.judge.repository

import com.aandiclub.online.judge.domain.User
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface UserRepository : ReactiveMongoRepository<User, String> {
    fun findByPublicCode(publicCode: String): Mono<User>
}
