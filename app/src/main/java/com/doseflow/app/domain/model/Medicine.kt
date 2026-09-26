package com.doseflow.app.domain.model

import java.time.Instant

data class Medicine(
    val id: String,
    val name: String,
    val dosage: String,
    val unit: String,
    val instructions: String = "",
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
