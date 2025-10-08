package com.example.edgedetectionviewer;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES 2.0 Renderer for displaying processed camera frames
 * Handles texture creation, shader compilation, and frame rendering
 */
public class GLTextureRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "GLTextureRenderer";

    // Shader source code
    private static final String VERTEX_SHADER_CODE =
            "#version 100\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec2 aTexCoord;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform mat4 uMVPMatrix;\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * aPosition;\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "}";

    private static final String FRAGMENT_SHADER_CODE =
            "#version 100\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                    "}";

    // Vertex coordinates for a quad
    private static final float[] QUAD_VERTICES = {
            // Position     // Texture coordinates
            -1.0f,  1.0f,   0.0f, 0.0f,  // Top-left
            -1.0f, -1.0f,   0.0f, 1.0f,  // Bottom-left
            1.0f, -1.0f,   1.0f, 1.0f,  // Bottom-right
            1.0f,  1.0f,   1.0f, 0.0f   // Top-right
    };

    private static final short[] QUAD_INDICES = {
            0, 1, 2,    // First triangle
            0, 2, 3     // Second triangle
    };

    // OpenGL objects
    private int shaderProgram;
    private int textureId;
    private int vertexBuffer;
    private int indexBuffer;

    // Shader attribute/uniform locations
    private int positionHandle;
    private int texCoordHandle;
    private int textureHandle;
    private int mvpMatrixHandle;

    // Matrix for transformations
    private float[] mvpMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];

    // Buffers for vertex data
    private FloatBuffer vertexFloatBuffer;
    private ByteBuffer indexByteBuffer;

    // Texture dimensions
    private int textureWidth = 640;
    private int textureHeight = 480;

    // Context reference
    private Context context;

    // Performance tracking
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private float currentFPS = 0.0f;

    public GLTextureRenderer(Context context) {
        this.context = context;
        initializeBuffers();
    }

    /**
     * Initialize vertex and index buffers
     */
    private void initializeBuffers() {
        // Create vertex buffer
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        vertexFloatBuffer = byteBuffer.asFloatBuffer();
        vertexFloatBuffer.put(QUAD_VERTICES);
        vertexFloatBuffer.position(0);

        // Create index buffer
        indexByteBuffer = ByteBuffer.allocateDirect(QUAD_INDICES.length * 2);
        indexByteBuffer.order(ByteOrder.nativeOrder());
        for (short index : QUAD_INDICES) {
            indexByteBuffer.putShort(index);
        }
        indexByteBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");

        // Set clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Create shader program
        createShaderProgram();

        // Create texture
        createTexture();

        // Create OpenGL buffers
        createBuffers();

        // Initialize native OpenGL
        EdgeDetectionJNI.initGL(textureWidth, textureHeight);

        checkGLError("onSurfaceCreated");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.i(TAG, "onSurfaceChanged: " + width + "x" + height);

        // Set viewport
        GLES20.glViewport(0, 0, width, height);

        // Calculate aspect ratio
        float ratio = (float) width / height;

        // Set up projection matrix
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);

        // Set up view matrix
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

        // Calculate MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        checkGLError("onSurfaceChanged");
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Calculate FPS
        calculateFPS();

        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Use shader program
        GLES20.glUseProgram(shaderProgram);

        // Bind vertex buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);

        // Enable vertex attributes
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, 0);

        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 16, 8);

        // Set MVP matrix
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(textureHandle, 0);

        // Bind index buffer and draw
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, QUAD_INDICES.length,
                GLES20.GL_UNSIGNED_SHORT, 0);

        // Disable vertex attributes
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);

        checkGLError("onDrawFrame");
    }

    /**
     * Create and compile shader program
     */
    private void createShaderProgram() {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);

        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShader);
        GLES20.glAttachShader(shaderProgram, fragmentShader);
        GLES20.glLinkProgram(shaderProgram);

        // Check linking status
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(shaderProgram);
            Log.e(TAG, "Failed to link shader program: " + error);
            GLES20.glDeleteProgram(shaderProgram);
            shaderProgram = 0;
            return;
        }

        // Get attribute and uniform locations
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord");
        textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");

        // Clean up individual shaders
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        Log.i(TAG, "Shader program created successfully");
    }

    /**
     * Compile individual shader
     */
    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        // Check compilation status
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, "Failed to compile shader: " + error);
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    /**
     * Create texture for displaying processed frames
     */
    private void createTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Allocate texture memory
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                textureWidth, textureHeight, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, null);

        Log.i(TAG, "Texture created: ID=" + textureId + ", Size=" + textureWidth + "x" + textureHeight);
    }

    /**
     * Create OpenGL buffer objects
     */
    private void createBuffers() {
        // Create vertex buffer
        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);

        vertexBuffer = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexFloatBuffer.capacity() * 4,
                vertexFloatBuffer, GLES20.GL_STATIC_DRAW);

        // Create index buffer
        indexBuffer = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexByteBuffer.capacity(),
                indexByteBuffer, GLES20.GL_STATIC_DRAW);

        Log.i(TAG, "OpenGL buffers created");
    }

    /**
     * Update texture with new frame data
     */
    public void updateTexture(byte[] pixelData, int width, int height) {
        if (textureId == 0 || pixelData == null) {
            Log.w(TAG, "Cannot update texture: invalid texture ID or pixel data");
            return;
        }

        // Update texture dimensions if changed
        if (width != textureWidth || height != textureHeight) {
            textureWidth = width;
            textureHeight = height;
            Log.i(TAG, "Texture dimensions updated: " + width + "x" + height);
        }

        // Create byte buffer for pixel data
        ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(pixelData.length);
        pixelBuffer.put(pixelData);
        pixelBuffer.position(0);

        // Update texture data
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);

        checkGLError("updateTexture");
    }

    /**
     * Capture current frame for analysis
     */
    public void captureFrame() {
        // This could save the current texture to a file or pass it to analysis
        Log.i(TAG, "Frame captured (functionality can be extended)");
    }

    /**
     * Calculate and update FPS
     */
    private void calculateFPS() {
        frameCount++;
        long currentTime = System.currentTimeMillis();

        if (lastFrameTime == 0) {
            lastFrameTime = currentTime;
            return;
        }

        long deltaTime = currentTime - lastFrameTime;
        if (deltaTime >= 1000) { // Update FPS every second
            currentFPS = (frameCount * 1000.0f) / deltaTime;
            frameCount = 0;
            lastFrameTime = currentTime;

            Log.d(TAG, "Current FPS: " + String.format("%.1f", currentFPS));
        }
    }

    /**
     * Get current FPS
     */
    public float getCurrentFPS() {
        return currentFPS;
    }

    /**
     * Check for OpenGL errors
     */
    private void checkGLError(String operation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL error in " + operation + ": " + error);
            throw new RuntimeException("OpenGL error in " + operation + ": " + error);
        }
    }

    /**
     * Cleanup OpenGL resources
     */
    public void cleanup() {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
        }

        if (shaderProgram != 0) {
            GLES20.glDeleteProgram(shaderProgram);
            shaderProgram = 0;
        }

        if (vertexBuffer != 0 || indexBuffer != 0) {
            GLES20.glDeleteBuffers(2, new int[]{vertexBuffer, indexBuffer}, 0);
            vertexBuffer = 0;
            indexBuffer = 0;
        }

        Log.i(TAG, "OpenGL resources cleaned up");
    }
}