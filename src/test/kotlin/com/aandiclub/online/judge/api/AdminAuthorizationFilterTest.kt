package com.aandiclub.online.judge.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AdminAuthorizationFilterTest {
    private val filter = AdminAuthorizationFilter()

    @Test
    fun `admin role can access admin endpoints`() {
        val chain = RecordingChain()
        val exchange = exchange("/v1/admin/submissions")
        exchange.attributes[JwtExchangeAttributes.ROLES] = setOf("ADMIN")

        filter.filter(exchange, chain).block()

        assertEquals(1, chain.called)
        assertEquals(null, exchange.response.statusCode)
    }

    @Test
    fun `root role can access admin endpoints`() {
        val chain = RecordingChain()
        val exchange = exchange("/v1/admin/submissions")
        exchange.attributes[JwtExchangeAttributes.ROLES] = setOf("ROOT")

        filter.filter(exchange, chain).block()

        assertEquals(1, chain.called)
        assertEquals(null, exchange.response.statusCode)
    }

    @Test
    fun `non admin role gets 403`() {
        val chain = RecordingChain()
        val exchange = exchange("/v1/admin/submissions")
        exchange.attributes[JwtExchangeAttributes.ROLES] = setOf("USER")

        filter.filter(exchange, chain).block()

        assertEquals(0, chain.called)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
    }

    @Test
    fun `non admin path is ignored`() {
        val chain = RecordingChain()
        val exchange = exchange("/v1/submissions")
        exchange.attributes[JwtExchangeAttributes.ROLES] = setOf("USER")

        filter.filter(exchange, chain).block()

        assertEquals(1, chain.called)
        assertEquals(null, exchange.response.statusCode)
    }

    private fun exchange(path: String, method: HttpMethod = HttpMethod.GET): ServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build())

    private class RecordingChain : WebFilterChain {
        var called: Int = 0

        override fun filter(exchange: ServerWebExchange): Mono<Void> {
            called += 1
            return Mono.empty()
        }
    }
}
