package com.aandiclub.online.judge.api

import org.springframework.web.server.ServerWebExchange

data class RequestAccess(
    val submitterId: String,
    val roles: Set<String>,
) {
    val isAdmin: Boolean = roles.any { it in ADMIN_ROLES }

    companion object {
        private val ADMIN_ROLES = setOf("ADMIN", "SUPER_ADMIN", "ROOT")
    }
}

fun ServerWebExchange.requestAccess(): RequestAccess =
    RequestAccess(
        submitterId = getAttribute<String>(JwtExchangeAttributes.SUBJECT).orEmpty().ifBlank { "anonymous" },
        roles = getAttribute<Set<String>>(JwtExchangeAttributes.ROLES).orEmpty(),
    )
