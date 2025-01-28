package com.example.cameraxtest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.cameraxtest.ui.theme.CameraXTestTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CameraXTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel = remember { CameraViewModel() }
                    val cameraLens = viewModel.cameraLensFlow.collectAsState()
                    CameraPreviewScreen(viewModel, cameraLens.value)
                }
            }
        }
    }


}