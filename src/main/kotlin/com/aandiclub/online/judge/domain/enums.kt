package com.aandiclub.online.judge.domain

enum class Language(val value: String) {
    PYTHON("python"),
    KOTLIN("kotlin"),
    DART("dart")
}

enum class SubmissionStatus {
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILE_ERROR
}

enum class TestCaseStatus {
    PASSED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILE_ERROR
}
