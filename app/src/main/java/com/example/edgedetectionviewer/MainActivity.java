package com.example.edgedetectionviewer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Main Activity for Edge Detection Viewer
 * Handles camera permissions, UI setup, and coordinate between camera and OpenGL rendering
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private TextureView cameraTextureView;
    private GLSurfaceView glSurfaceView;
    private TextView statsTextView;
    private Button toggleProcessingButton;
    private Button captureFrameButton;

    // Core components
    private CameraRenderer cameraRenderer;
    private GLTextureRenderer glTextureRenderer;

    // State management
    private boolean isProcessingEnabled = true;
    private boolean isCameraInitialized = false;
    private boolean isGLInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "MainActivity onCreate");

        // Initialize native library
        if (!EdgeDetectionJNI.isLibraryLoaded()) {
            showError("Failed to load native library");
            return;
        }

        // Initialize UI components
        initializeUI();

        // Check camera permission
        if (checkCameraPermission()) {
            initializeCamera();
        } else {
            requestCameraPermission();
        }

        // Initialize OpenGL
        initializeOpenGL();
    }

    /**
     * Initialize UI components and set up event listeners
     */
    private void initializeUI() {
        cameraTextureView = findViewById(R.id.camera_texture_view);
        glSurfaceView = findViewById(R.id.gl_surface_view);
        statsTextView = findViewById(R.id.stats_text_view);
        toggleProcessingButton = findViewById(R.id.toggle_processing_button);
        captureFrameButton = findViewById(R.id.capture_frame_button);

        // Set up button listeners
        toggleProcessingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleProcessing();
            }
        });

        captureFrameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureFrame();
            }
        });

        // Initial UI state
        updateUI();
    }

    /**
     * Initialize camera renderer
     */
    private void initializeCamera() {
        try {
            cameraRenderer = new CameraRenderer(this, cameraTextureView);
            cameraRenderer.setFrameCallback(new CameraRenderer.FrameCallback() {
                @Override
                public void onFrameAvailable(byte[] frameData, int width, int height) {
                    processFrame(frameData, width, height);
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> showError("Camera error: " + error));
                }
            });

            isCameraInitialized = true;
            Log.i(TAG, "Camera initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize camera: " + e.getMessage());
            showError("Failed to initialize camera: " + e.getMessage());
        }
    }

    /**
     * Initialize OpenGL surface view and renderer
     */
    private void initializeOpenGL() {
        try {
            // Set OpenGL ES version
            glSurfaceView.setEGLContextClientVersion(2);

            // Create custom renderer
            glTextureRenderer = new GLTextureRenderer(this);
            glSurfaceView.setRenderer(glTextureRenderer);

            // Set render mode to only render when data changes
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

            isGLInitialized = true;
            Log.i(TAG, "OpenGL initialized successfully");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize OpenGL: " + e.getMessage());
            showError("Failed to initialize OpenGL: " + e.getMessage());
        }
    }

    /**
     * Process camera frame with edge detection
     */
    private void processFrame(byte[] frameData, int width, int height) {
        if (!isProcessingEnabled || !isGLInitialized || frameData == null) {
            return;
        }

        try {
            // Process frame using native code
            byte[] processedData = EdgeDetectionJNI.processFrame(frameData, width, height);

            if (processedData != null && glTextureRenderer != null) {
                // Update OpenGL texture with processed data
                glTextureRenderer.updateTexture(processedData, width, height);

                // Trigger render
                glSurfaceView.requestRender();

                // Update performance stats
                updatePerformanceStats();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing frame: " + e.getMessage());
        }
    }

    /**
     * Toggle edge detection processing on/off
     */
    private void toggleProcessing() {
        isProcessingEnabled = !isProcessingEnabled;
        updateUI();

        String message = isProcessingEnabled ? "Processing enabled" : "Processing disabled";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.i(TAG, message);
    }

    /**
     * Capture current frame for analysis
     */
    private void captureFrame() {
        if (glTextureRenderer != null) {
            glTextureRenderer.captureFrame();
            Toast.makeText(this, "Frame captured", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update UI elements based on current state
     */
    private void updateUI() {
        runOnUiThread(() -> {
            toggleProcessingButton.setText(isProcessingEnabled ? "Disable Processing" : "Enable Processing");
            captureFrameButton.setEnabled(isGLInitialized);
        });
    }

    /**
     * Update performance statistics display
     */
    private void updatePerformanceStats() {
        try {
            String stats = EdgeDetectionJNI.getPerformanceStats();
            runOnUiThread(() -> {
                if (statsTextView != null) {
                    statsTextView.setText(stats);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating performance stats: " + e.getMessage());
        }
    }

    /**
     * Check if camera permission is granted
     */
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request camera permission from user
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    /**
     * Handle permission request result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera permission granted");
                initializeCamera();
            } else {
                Log.w(TAG, "Camera permission denied");
                showError("Camera permission is required for edge detection");
            }
        }
    }

    /**
     * Show error message to user
     */
    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            Log.e(TAG, message);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "MainActivity onResume");

        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }

        if (cameraRenderer != null && isCameraInitialized) {
            cameraRenderer.startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "MainActivity onPause");

        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }

        if (cameraRenderer != null) {
            cameraRenderer.stopCamera();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "MainActivity onDestroy");

        // Cleanup resources
        if (cameraRenderer != null) {
            cameraRenderer.cleanup();
        }

        if (glTextureRenderer != null) {
            glTextureRenderer.cleanup();
        }

        // Cleanup native resources
        EdgeDetectionJNI.cleanup();
    }
}