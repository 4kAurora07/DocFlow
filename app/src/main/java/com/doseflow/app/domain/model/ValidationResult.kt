package com.doseflow.app.domain.model

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult {
        constructor(singleError: String) : this(listOf(singleError))
    }

    val isValid: Boolean get() = this is Valid
}
