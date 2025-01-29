package com.example.cameraxtest.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceRequest
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.ViewModel
import com.example.cameraxtest.PreviewScaleType
import com.example.cameraxtest.model.CameraUiState
import com.example.cameraxtest.model.SourceInfo
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    fun onUpdateSurfaceRequests(surfaceRequest: SurfaceRequest?) {
        _uiState.update {
            it.copy(surfaceRequest = surfaceRequest)
        }
    }

    fun onUpdateSourceInfo(imageProxy: ImageProxy) {
        _uiState.update { it.copy(sourceInfo = obtainSourceInfo(it.cameraLens, imageProxy)) }
    }

    fun onUpdateListFaces(newListFaces: List<Face>) {
        _uiState.update { it.copy(listFaces = newListFaces) }
    }

    private fun obtainSourceInfo(lens: Int, imageProxy: ImageProxy): SourceInfo {
        val isImageFlipped = lens == CameraSelector.LENS_FACING_FRONT
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees == 0 || rotationDegrees == 180) {
            SourceInfo(
                height = imageProxy.height,
                width = imageProxy.width,
                isImageFlipped = isImageFlipped
            )
        } else {
            SourceInfo(
                height = imageProxy.width,
                width = imageProxy.height,
                isImageFlipped = isImageFlipped
            )
        }
    }

    fun onCalculateScale(
        constraints: Constraints,
        sourceInfo: SourceInfo,
        scaleType: PreviewScaleType
    ): Float {
        val heightRatio = constraints.maxHeight.toFloat() / sourceInfo.height
        val widthRatio = constraints.maxWidth.toFloat() / sourceInfo.width
        return when (scaleType) {
            PreviewScaleType.FIT_CENTER -> kotlin.math.min(heightRatio, widthRatio)
            PreviewScaleType.CENTER_CROP -> kotlin.math.max(heightRatio, widthRatio)
        }
    }


    fun switchLens(lens: Int) = if (CameraSelector.LENS_FACING_FRONT == lens) {
        _uiState.update { it.copy(cameraLens = CameraSelector.LENS_FACING_BACK) }
    } else {
        _uiState.update { it.copy(cameraLens = CameraSelector.LENS_FACING_FRONT) }
    }

}