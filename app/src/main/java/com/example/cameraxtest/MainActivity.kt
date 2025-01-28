package com.example.cameraxtest

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.SurfaceRequest
import androidx.camera.view.PreviewView
import androidx.camera.viewfinder.surface.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cameraxtest.ui.theme.CameraXTestTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.face.Face

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraXTestTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                val viewModel = remember { CameraViewModel() }
                val cameraLens =  viewModel.cameraLensFlow.collectAsState()
                CameraPreviewScreen(viewModel, cameraLens.value)
            }
        }
    }


    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun CameraPreviewScreen(
        viewModel: CameraViewModel,
        lens:Int,
        modifier: Modifier = Modifier
    ) {
        val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewContent(modifier, viewModel, lens)
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                    // If the user has denied the permission but the rationale can be shown,
                    // then gently explain why the app requires this permission
                    "Whoops! Looks like we need your camera to work our magic!" +
                            "Don't worry, we just wanna see your pretty face (and maybe some cats).  " +
                            "Grant us permission and let's get this party started!"
                } else {
                    // If it's the first time the user lands on this feature, or the user
                    // doesn't want to be asked again for this permission, explain that the
                    // permission is required
                    "Hi there! We need your camera to work our magic! ✨\n" +
                            "Grant us permission and let's get this party started! \uD83C\uDF89"
                }
                Text(textToShow, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Unleash the Camera!")
                }
            }
        }
    }

    @Composable
    private fun CameraPreviewContent(
        modifier: Modifier = Modifier,
        viewModel: CameraViewModel,
        cameraLens: Int
    ) {
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }

        val surfaceRequest by viewModel.surfaceRequests.collectAsStateWithLifecycle()
        val sourceInfo = remember { mutableStateOf(SourceInfo(10, 10, false)) }
        val listFaces = viewModel.listFacesFlow.collectAsState()
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current


        Log.d("sourceInfo", sourceInfo.value.toString())
        LaunchedEffect(sourceInfo, cameraLens) {
            viewModel.bindToCamera(
                previewView,
                context,
                lifecycleOwner,
                setSourceInfo = { sourceInfo.value = it },
                cameraLens
            )
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            with(LocalDensity.current) {
                Box(
                    modifier = Modifier
                        .size(
                            height = sourceInfo.value.height.toDp(),
                            width = sourceInfo.value.width.toDp()
                        )
                        .scale(
                            calculateScale(
                                constraints,
                                sourceInfo.value,
                                PreviewScaleType.CENTER_CROP
                            )
                        )
                )
                {
//                    previewViewCamera(
//                        surfaceRequest,
//                        modifier = modifier
//                    )

                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            previewView.apply {
                                this.scaleType = PreviewView.ScaleType.FIT_CENTER
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                            previewView
                        }
                    )

                    DetectedFaces(
                        faces = listFaces.value,
                        sourceInfo = sourceInfo.value
                    )
                }
            }
            Row (
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center
            ) {
                SwitchCameraLens(onLensChange = { viewModel.switchLens(cameraLens) })
            }

        }

    }

//    @Composable
//    fun previewViewCamera(
//        surfaceRequest:SurfaceRequest?,
//        modifier: Modifier
//    ) {
//        surfaceRequest?.let { request ->
//            CameraXViewfinder(
//                surfaceRequest = request,
//                implementationMode = ImplementationMode.EXTERNAL,
//                modifier = modifier
//            )
//        }
//    }

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
                        if (needToMirror) size.width - face.boundingBox.right.toFloat() else face.boundingBox.left.toFloat()
                    drawRect(
                        color = Color.Green,
                        style = Stroke(2.dp.toPx()),
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
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Button(
                onClick = onLensChange,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = "Switch camera",
                    color = Color.White
                )
            }
        }
    }

     private fun calculateScale(
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



//    @Preview(showBackground = true)
//    @Composable
//    fun Mainpreview() {
//        CameraXTestTheme {
//            CameraPreviewScreen()
//        }
//    }

}