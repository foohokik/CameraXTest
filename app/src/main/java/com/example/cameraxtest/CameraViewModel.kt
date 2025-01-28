package com.example.cameraxtest

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraViewModel : ViewModel() {

    private val _cameraLensFlow = MutableStateFlow(0)
    val cameraLensFlow: StateFlow<Int>
        get() = _cameraLensFlow.asStateFlow()

    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequests: StateFlow<SurfaceRequest?>
        get() = _surfaceRequests.asStateFlow()

    private val _listFacesFlow = MutableStateFlow<List<Face>>(emptyList())
    val listFacesFlow: StateFlow<List<Face>>
        get() = _listFacesFlow.asStateFlow()


    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        setSourceInfo: (SourceInfo) -> Unit,
        cameraLens: Int) {

        val cameraPreviewUseCase = Preview.Builder().build().apply {
            setSurfaceProvider { newSurfaceRequest ->
                _surfaceRequests.update { newSurfaceRequest }
            }
        }
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraLens).build()
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)
        val analysis = bindAnalysisUseCase(cameraLens, setSourceInfo)

        processCameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, cameraPreviewUseCase, analysis
        )

        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }


    private fun bindAnalysisUseCase(
        lens: Int,
        setSourceInfo: (SourceInfo) -> Unit,
    ): ImageAnalysis? {

        val imageProcessor = try {
            FaceDetectorProcessor()
        } catch (e: Exception) {
            Log.e("CAMERA", "Can not create image processor", e)
            return null
        }
        val analysisUseCase = ImageAnalysis.Builder().build()

        var sourceInfoUpdated = false

        analysisUseCase.setAnalyzer(
            TaskExecutors.MAIN_THREAD,
            { imageProxy: ImageProxy ->
                if (!sourceInfoUpdated) {
                    setSourceInfo(obtainSourceInfo(lens, imageProxy))
                    sourceInfoUpdated = true
                }
                try {
                    imageProcessor.processImageProxy(imageProxy,
                        onDetectionFinished = { _listFacesFlow.value = it })
                } catch (e: MlKitException) {
                    Log.e(
                        "CAMERA", "Failed to process image. Error: " + e.localizedMessage
                    )
                }
            }
        )
        return analysisUseCase
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

//    private fun calculateScale(
//        constraints: Constraints,
//        sourceInfo: SourceInfo,
//        scaleType: PreviewScaleType
//    ): Float {
//        val heightRatio = constraints.maxHeight.toFloat() / sourceInfo.height
//        val widthRatio = constraints.maxWidth.toFloat() / sourceInfo.width
//        return when (scaleType) {
//            PreviewScaleType.FIT_CENTER -> kotlin.math.min(heightRatio, widthRatio)
//            PreviewScaleType.CENTER_CROP -> kotlin.math.max(heightRatio, widthRatio)
//        }
//    }


    fun switchLens(lens: Int) = if (CameraSelector.LENS_FACING_FRONT == lens) {
        _cameraLensFlow.update { CameraSelector.LENS_FACING_BACK }
    } else {
        _cameraLensFlow.update { CameraSelector.LENS_FACING_FRONT }
    }

}