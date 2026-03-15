package com.aandiclub.online.judge.api

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 60)
class AdminAuthorizationFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!isTarget(exchange)) return chain.filter(exchange)

        val roles = exchange.getAttribute<Set<String>>(JwtExchangeAttributes.ROLES).orEmpty()
        if (roles.any { it in ADMIN_ROLES }) return chain.filter(exchange)

        val response = exchange.response
        response.statusCode = HttpStatus.FORBIDDEN
        response.headers.contentType = MediaType.APPLICATION_JSON
        val body = """{"error":"FORBIDDEN","message":"ADMIN role is required"}"""
        val buffer = response.bufferFactory().wrap(body.toByteArray(StandardCharsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }

    private fun isTarget(exchange: ServerWebExchange): Boolean {
        if (exchange.request.method?.name() == "OPTIONS") return false
        return exchange.request.path.pathWithinApplication().value().startsWith("/v1/admin/")
    }

    companion object {
        private val ADMIN_ROLES = setOf("ADMIN", "SUPER_ADMIN", "ROOT")
    }
}
