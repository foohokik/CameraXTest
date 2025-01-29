package com.example.cameraxtest.model

import androidx.camera.core.SurfaceRequest
import androidx.compose.runtime.Immutable
import com.google.mlkit.vision.face.Face

@Immutable
data class CameraUiState(
    val sourceInfo: SourceInfo = SourceInfo(10, 10, false),
    val cameraLens: Int = 0,
    val surfaceRequest: SurfaceRequest? = null,
    val listFaces: List<Face> = emptyList()
)
