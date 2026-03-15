package com.aandiclub.online.judge.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "judge.openapi")
data class OpenApiProperties(
    val serverUrl: String = "",
)

@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
)
@EnableConfigurationProperties(OpenApiProperties::class)
class SwaggerConfig(
    private val openApiProperties: OpenApiProperties,
) {
    @Bean
    fun baseOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("AANDI Club Online Judge API")
                .version("v1")
                .description(
                    """
                    Asynchronous online judge API for code submissions and live result streaming.

                    Basic flow:
                    1. Authorize with a Bearer JWT in Swagger UI.
                    2. Open `POST /v1/submissions` and pick one of the built-in language examples.
                    3. Subscribe to the SSE stream for live test-case events.
                    4. Poll the final result endpoint when you need the aggregated status.
                    5. Inspect your per-problem history via `GET /v1/problems/{problemId}/submissions/me`.

                    Notes:
                    - Submission creation returns quickly with HTTP 202; actual judging runs asynchronously.
                    - SSE emits `test_case_result`, `done`, and `error` events.
                    - JWT auth is enabled by default and requires at least USER role.
                    - `quiz-101` is preconfigured with 10 sum test cases for quick Swagger testing.
                    """.trimIndent()
                )
        )
        .addTagsItem(
            Tag()
                .name("Submissions")
                .description("Create submissions, consume live SSE updates, and fetch final verdicts.")
        )
        .addTagsItem(
            Tag()
                .name("Problem Submissions")
                .description("List the authenticated user's submission history for a problem.")
        )
        .addTagsItem(
            Tag()
                .name("Admin Submissions")
                .description("View all stored submission records with ADMIN privileges.")
        )

    @Bean
    fun publicServerUrlOpenApiCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val publicServerUrl = openApiProperties.serverUrl.trim()
        if (publicServerUrl.isBlank()) return@OpenApiCustomizer

        openApi.servers = listOf(
            Server()
                .url(publicServerUrl)
                .description("Public API"),
        )
    }
}
