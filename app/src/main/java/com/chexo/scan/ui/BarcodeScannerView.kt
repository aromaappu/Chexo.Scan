package com.chexo.scan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import com.chexo.scan.ui.theme.LocalAppColors

private const val IDLE_TIMEOUT_MS = 35 * 60 * 1000L

private fun playBeep(context: android.content.Context) {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        Handler(Looper.getMainLooper()).postDelayed({
            try { toneGen.release() } catch (_: Exception) {}
        }, 300)
    } catch (_: Exception) {}
}

@Composable
fun BarcodeScannerView(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var paused by remember { mutableStateOf(true) }
    var lastScanTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Idle timeout — auto-pause after 35 minutes
    LaunchedEffect(paused) {
        if (!paused) {
            lastScanTime = System.currentTimeMillis()
            while (!paused) {
                kotlinx.coroutines.delay(10_000)
                if (System.currentTimeMillis() - lastScanTime > IDLE_TIMEOUT_MS) {
                    paused = true
                    break
                }
            }
        }
    }

    // Clean up handler when composable is removed (tab switch)
    DisposableEffect(Unit) {
        onDispose {
            paused = true
            handler.removeCallbacksAndMessages(null)
        }
    }

    if (hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, colors.primary, RoundedCornerShape(12.dp))
                .background(if (paused) colors.surface else Color.Black)
                .clickable {
                    if (paused) {
                        paused = false
                        lastScanTime = System.currentTimeMillis()
                    } else {
                        paused = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!paused) {
                // Camera is ON — show preview and scan
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                val barcodeScanner = remember { BarcodeScanning.getClient() }

                DisposableEffect(Unit) {
                    onDispose {
                        cameraExecutor.shutdown()
                        barcodeScanner.close()
                        handler.removeCallbacksAndMessages(null)
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (!paused && enabled) {
                                            @Suppress("DEPRECATION")
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val image = InputImage.fromMediaImage(
                                                    mediaImage,
                                                    imageProxy.imageInfo.rotationDegrees
                                                )
                                                barcodeScanner.process(image)
                                                    .addOnSuccessListener { barcodes ->
                                                        if (!paused && enabled) {
                                                            val barcode = barcodes.firstOrNull {
                                                                it.valueType in listOf(
                                                                    Barcode.TYPE_PRODUCT,
                                                                    Barcode.TYPE_ISBN,
                                                                    Barcode.TYPE_TEXT,
                                                                    Barcode.TYPE_URL,
                                                                    Barcode.TYPE_UNKNOWN
                                                                )
                                                            }
                                                            if (barcode != null) {
                                                                val value = barcode.rawValue ?: barcode.displayValue ?: ""
                                                                if (value.isNotEmpty()) {
                                                                    paused = true
                                                                    lastScanTime = System.currentTimeMillis()
                                                                    playBeep(ctx)
                                                                    onBarcodeScanned(value)
                                                                    handler.postDelayed({
                                                                        paused = false
                                                                        lastScanTime = System.currentTimeMillis()
                                                                    }, 1500)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (_: Exception) {}

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Scanning indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            colors.primary.copy(alpha = 0.7f),
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Scanning...", color = Color.White, fontSize = 12.sp)
                }
            } else {
                // Camera is OFF — show tap to scan placeholder
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            Color(0x88888888),
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("⏸ Paused — Tap to Scan", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, colors.primary, RoundedCornerShape(12.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission required to scan", fontSize = 14.sp, color = Color.Gray)
        }
    }
}
