package com.example.flamassignment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.opengl.GLSurfaceView
import com.example.flamassignment.gl.EdgeGLRenderer
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.system.measureNanoTime

class MainActivity : AppCompatActivity() {
    companion object {
        init { System.loadLibrary("native-lib") }
    }

    // JNI function: sends RGBA bytes, width, height; returns processed bytes (single-channel)
    external fun processFrame(inputBytes: ByteArray, width: Int, height: Int): ByteArray

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: EdgeGLRenderer
    private lateinit var toggleBtn: Button
    private lateinit var fpsText: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var showEdge = true

    // simple FPS calc
    private var frames = 0
    private var lastFpsTs = System.currentTimeMillis()
    private var currentFps = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glView = findViewById(R.id.gl_view)
        toggleBtn = findViewById(R.id.toggle_btn)
        fpsText = findViewById(R.id.fps_text)

        // GLSurfaceView + renderer
        glView.setEGLContextClientVersion(2)
        renderer = EdgeGLRenderer()
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        toggleBtn.setOnClickListener {
            showEdge = !showEdge
            toggleBtn.text = if (showEdge) "Mode: EDGE" else "Mode: RAW"
        }

        // request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                // request RGBA frames (API level dependent)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                handleImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                Log.e("MainActivity", "bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleImageProxy(imageProxy: ImageProxy) {
        // convert ImageProxy (RGBA) to ByteArray
        val width = imageProxy.width
        val height = imageProxy.height

        // get RGBA buffer (this only works when ImageAnalysis OUTPUT_IMAGE_FORMAT_RGBA_8888)
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Process frame in JNI (we call on camera thread; keep it fast)
        // Measure processing time (for FPS)
        var procBytes: ByteArray? = null
        val timeNs = measureNanoTime {
            try {
                procBytes = processFrame(bytes, width, height)
            } catch (e: Exception) {
                Log.e("MainActivity", "JNI processing failed: ${e.message}")
            }
        }

        // update FPS
        frames++
        val now = System.currentTimeMillis()
        if (now - lastFpsTs >= 1000) {
            currentFps = frames
            frames = 0
            lastFpsTs = now
            runOnUiThread {
                fpsText.text = "FPS: $currentFps | ${width}x${height}"
            }
        }

        // If processing worked and showEdge is true send processed to GL, else send original
        procBytes?.let { pb ->
            if (showEdge) {
                renderer.updateFrame(pb, width, height, /*isGray=*/true)
            } else {
                // raw RGBA -> convert to RGB-like texture (GL expects RGBA)
                renderer.updateFrame(bytes, width, height, /*isGray=*/false)
            }
            // Request GL to draw
            glView.requestRender()
        }

        imageProxy.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
