package com.aandiclub.online.judge.api

import com.aandiclub.online.judge.api.dto.SubmissionAccepted
import com.aandiclub.online.judge.api.dto.SubmissionRequest
import com.aandiclub.online.judge.api.dto.SubmissionResult
import com.aandiclub.online.judge.service.SubmissionService
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/submissions")
class SubmissionController(
    private val submissionService: SubmissionService,
) {
    @PostMapping
    suspend fun submit(
        @Valid @RequestBody request: SubmissionRequest,
    ): ResponseEntity<SubmissionAccepted> {
        val accepted = submissionService.createSubmission(request)
        return ResponseEntity.accepted().body(accepted)
    }

    // SSE 스트림: 각 테스트 케이스 완료 시 즉시 이벤트 전송
    @GetMapping("/{submissionId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamResults(
        @PathVariable submissionId: String,
    ): Flow<ServerSentEvent<String>> = submissionService.streamResults(submissionId)

    // 동기 결과 조회 (realtime_feedback: false 또는 완료 후 폴링)
    @GetMapping("/{submissionId}")
    suspend fun getResult(
        @PathVariable submissionId: String,
    ): ResponseEntity<SubmissionResult> {
        val result = submissionService.getResult(submissionId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }
}
