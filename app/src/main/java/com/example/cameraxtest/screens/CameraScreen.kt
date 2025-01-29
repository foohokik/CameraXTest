package com.example.cameraxtest.screens

import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.surface.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cameraxtest.FaceDetectorProcessor
import com.example.cameraxtest.PreviewScaleType
import com.example.cameraxtest.R
import com.example.cameraxtest.model.SourceInfo
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.awaitCancellation

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    if (cameraPermissionState.status.isGranted) {
        CameraPreviewContent(modifier, viewModel)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .wrapContentSize()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                stringResource(R.string.require_permission_after_refuse)
            } else {
                stringResource(R.string.require_permission)
            }
            Text(textToShow, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text(stringResource(R.string.unleash_camera))
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    InitCamera(viewModel = viewModel, cameraLens = uiState.cameraLens)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        with(LocalDensity.current) {
            Box(
                modifier = Modifier
                    .size(
                        height = uiState.sourceInfo.height.toDp(),
                        width = uiState.sourceInfo.width.toDp()
                    )
                    .scale(
                        viewModel.onCalculateScale(
                            constraints,
                            uiState.sourceInfo,
                            PreviewScaleType.CENTER_CROP
                        )
                    )
            )
            {
                previewViewCamera(
                    uiState.surfaceRequest,
                    modifier = modifier
                )

                DetectedFaces(
                    faces = uiState.listFaces,
                    sourceInfo = uiState.sourceInfo
                )
            }
        }

    }

    SwitchCameraLens(onLensChange = { viewModel.switchLens(uiState.cameraLens) })
}

@Composable
fun InitCamera(cameraLens: Int, viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraPreviewUseCase = remember {
        Preview.Builder().build().apply {
            setSurfaceProvider { newSurfaceRequest ->
                viewModel.onUpdateSurfaceRequests(newSurfaceRequest)
            }
        }
    }
    val cameraSelector =
        remember(cameraLens) { CameraSelector.Builder().requireLensFacing(cameraLens).build() }

    LaunchedEffect(cameraLens) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(context)
        val analysis = bindAnalysisUseCase({ viewModel.onUpdateSourceInfo(it) },
            { viewModel.onUpdateListFaces(it) })

        processCameraProvider.bindToLifecycle(
            lifecycleOwner, cameraSelector, cameraPreviewUseCase, analysis
        )

        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }
}

private fun bindAnalysisUseCase(
    updateSourceInfo: (ImageProxy) -> Unit,
    updateListFaces: (List<Face>) -> Unit
): ImageAnalysis? {

    val imageProcessor = try {
        FaceDetectorProcessor.getInstance()
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
                updateSourceInfo.invoke(imageProxy)
                sourceInfoUpdated = true
            }
            try {
                imageProcessor.processImageProxy(imageProxy,
                    onDetectionFinished = { updateListFaces.invoke(it) })
            } catch (e: MlKitException) {
                Log.e(
                    "CAMERA", "Failed to process image. Error: " + e.localizedMessage
                )
            }
        }
    )
    return analysisUseCase
}

@Composable
fun previewViewCamera(
    surfaceRequest: SurfaceRequest?,
    modifier: Modifier
) {
    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            implementationMode = ImplementationMode.EMBEDDED,
            modifier = modifier
        )
    }
}

@Composable
fun DetectedFaces(
    faces: List<Face>,
    sourceInfo: SourceInfo
) {
    Box {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val needToMirror = sourceInfo.isImageFlipped
            for (face in faces) {
                val left =
                    if (needToMirror) {
                        size.width - face.boundingBox.right.toFloat()
                    } else {
                        face.boundingBox.left.toFloat()
                    }
                drawRect(
                    color = Color.LightGray,
                    style = Stroke(1.dp.toPx()),
                    topLeft = Offset(left, face.boundingBox.top.toFloat()),
                    size = Size(
                        face.boundingBox.width().toFloat(),
                        face.boundingBox.height().toFloat()
                    )
                )
            }
        }
    }
}

@Composable
fun SwitchCameraLens(
    onLensChange: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 50.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Button(
            onClick = onLensChange,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .wrapContentSize()
        ) {
            Text(
                text = stringResource(R.string.switch_camera),
                color = Color.White
            )
        }
    }
}

