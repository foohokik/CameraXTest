package com.example.cameraxtest.model

import androidx.compose.runtime.Immutable

@Immutable
data class SourceInfo(
    val width: Int,
    val height: Int,
    val isImageFlipped: Boolean,
)