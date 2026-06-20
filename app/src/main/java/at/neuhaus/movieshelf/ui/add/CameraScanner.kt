package at.neuhaus.movieshelf.ui.add

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun CameraScanner(
    onDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    
    var detectedText by remember { mutableStateOf("") }
    var detectedBarcode by remember { mutableStateOf("") }
    var flashEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                processImageProxy(textRecognizer, barcodeScanner, imageProxy, 
                                    onTextFound = { text -> detectedText = text },
                                    onBarcodeFound = { barcode -> detectedBarcode = barcode }
                                )
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (exc: Exception) {
                        Log.e("CameraScanner", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay UI
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                }
                
                IconButton(onClick = { 
                    flashEnabled = !flashEnabled
                    cameraControl?.enableTorch(flashEnabled)
                }) {
                    Icon(
                        if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Blitz",
                        tint = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val displayValue = if (detectedBarcode.isNotEmpty()) "Barcode: $detectedBarcode" else detectedText
                
                if (displayValue.isNotEmpty()) {
                    Card(
                        modifier = Modifier.padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = displayValue,
                            modifier = Modifier.padding(16.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Button(
                        onClick = { onDetected(if (detectedBarcode.isNotEmpty()) detectedBarcode else detectedText) },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(if (detectedBarcode.isNotEmpty()) "Barcode suchen" else "Titel übernehmen")
                    }
                } else {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Titel oder Barcode scannen",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            // ML-Kit-Detektoren halten native Ressourcen und müssen geschlossen werden.
            textRecognizer.close()
            barcodeScanner.close()
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onTextFound: (String) -> Unit,
    onBarcodeFound: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        // Zuerst Barcode versuchen (da präziser)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull()?.rawValue
                if (barcode != null) {
                    onBarcodeFound(barcode)
                } else {
                    // Wenn kein Barcode, dann Text versuchen
                    textRecognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            val result = visionText.textBlocks
                                .map { it.text }
                                .filter { it.length > 3 }
                                .maxByOrNull { it.length } ?: ""
                            
                            if (result.isNotEmpty()) {
                                onTextFound(result.replace("\n", " "))
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
}
