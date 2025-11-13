package com.example.flamassignment.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EdgeGLRenderer : GLSurfaceView.Renderer {
    // simple textured quad
    private val vertexCoords = floatArrayOf(
        -1f, -1f,
         1f, -1f,
        -1f,  1f,
         1f,  1f
    )
    private val texCoords = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )
    private val vBuf: FloatBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertexCoords).apply { position(0) }
    private val tBuf: FloatBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(texCoords).apply { position(0) }

    private var program = 0
    private var texId = -1
    private var uTex = 0
    private var aPos = 0
    private var aTex = 0

    // frame buffer from Kotlin (updated by updateFrame)
    @Volatile private var latestBitmap: Bitmap? = null
    @Volatile private var updated = false

    override fun onSurfaceCreated(glUnused: GL10?, config: EGLConfig?) {
        val vertexShader = """
            attribute vec2 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vTex = aTex;
            }
        """.trimIndent()

        val fragShader = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
        """.trimIndent()

        program = createProgram(vertexShader, fragShader)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTex = GLES20.glGetUniformLocation(program, "uTex")

        // create texture
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        texId = tex[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (updated) {
            latestBitmap?.let { bmp ->
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                // replace texture contents
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            }
            updated = false
        }

        GLES20.glUseProgram(program)
        vBuf.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vBuf)

        tBuf.position(0)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tBuf)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(uTex, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    // called from main thread to update image bytes; expects either RGBA bytes or gray bytes
    fun updateFrame(bytes: ByteArray, width: Int, height: Int, isGray: Boolean) {
        // convert bytes to Bitmap (ARGB_8888)
        val bmp = if (isGray) {
            // create single-channel bitmap by copying gray into RGB channels
            val pixels = IntArray(width * height)
            var idx = 0
            for (i in 0 until width*height) {
                val v = bytes[i].toInt() and 0xFF
                val p = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                pixels[idx++] = p
            }
            Bitmap.createBitmap(pixels, width, height, Config.ARGB_8888)
        } else {
            // bytes expected as RGBA
            val bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            val intBuf = IntArray(width*height)
            var bi = 0
            var biByte = 0
            for (i in 0 until width*height) {
                val r = bytes[biByte++].toInt() and 0xFF
                val g = bytes[biByte++].toInt() and 0xFF
                val b = bytes[biByte++].toInt() and 0xFF
                val a = bytes[biByte++].toInt() and 0xFF
                intBuf[bi++] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            bmp.setPixels(intBuf, 0, width, 0, 0, width, height)
            bmp
        }

        // swap in
        latestBitmap?.recycle()
        latestBitmap = bmp
        updated = true
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vs: String, fs: String): Int {
        val v = loadShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }
}
