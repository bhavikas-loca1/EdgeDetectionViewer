package com.example.edgedetectionviewer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import androidx.core.app.ActivityCompat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Camera2 API implementation for capturing real-time frames
 * Handles camera initialization, preview setup, and frame data extraction
 */
public class CameraRenderer {

    private static final String TAG = "CameraRenderer";

    // Camera configuration
    private static final int PREFERRED_WIDTH = 640;
    private static final int PREFERRED_HEIGHT = 480;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    // Camera objects
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;

    // Threading
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // UI and context
    private Context context;
    private TextureView textureView;
    private String cameraId;
    private Size previewSize;

    // Callback interface
    public interface FrameCallback {
        void onFrameAvailable(byte[] frameData, int width, int height);
        void onError(String error);
    }

    private FrameCallback frameCallback;

    // State management
    private boolean isCameraOpened = false;
    private boolean isCapturing = false;

    public CameraRenderer(Context context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        setupTextureView();
    }

    /**
     * Set callback for receiving processed frames
     */
    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }

    /**
     * Setup TextureView for camera preview
     */
    private void setupTextureView() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "Surface texture available: " + width + "x" + height);
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.i(TAG, "Surface texture size changed: " + width + "x" + height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.i(TAG, "Surface texture destroyed");
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                // Called when new frame is available - can be used for additional processing
            }
        });
    }

    /**
     * Start background thread for camera operations
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        Log.i(TAG, "Background thread started");
    }

    /**
     * Stop background thread
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
                Log.i(TAG, "Background thread stopped");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }

    /**
     * Open camera and set up preview
     */
    public void openCamera() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (frameCallback != null) {
                frameCallback.onError("Camera permission not granted");
            }
            return;
        }

        startBackgroundThread();

        try {
            // Find best camera (prefer back-facing)
            cameraId = getBestCameraId();
            if (cameraId == null) {
                if (frameCallback != null) {
                    frameCallback.onError("No suitable camera found");
                }
                return;
            }

            // Get camera characteristics
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                if (frameCallback != null) {
                    frameCallback.onError("Cannot get camera stream configuration");
                }
                return;
            }

            // Choose optimal preview size
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));
            Log.i(TAG, "Selected preview size: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            // Setup image reader for frame processing
            setupImageReader();

            // Open camera
            cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error opening camera", e);
            if (frameCallback != null) {
                frameCallback.onError("Error opening camera: " + e.getMessage());
            }
        }
    }

    /**
     * Find the best camera ID (prefer back-facing camera)
     */
    private String getBestCameraId() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();

            // First, try to find back-facing camera
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.i(TAG, "Selected back-facing camera: " + id);
                    return id;
                }
            }

            // If no back-facing camera, use the first available
            if (cameraIds.length > 0) {
                Log.i(TAG, "Selected camera: " + cameraIds[0]);
                return cameraIds[0];
            }

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accessing camera list", e);
        }

        return null;
    }

    /**
     * Choose optimal preview size based on preferences
     */
    private Size chooseOptimalSize(Size[] choices) {
        // Filter sizes that are not too large
        List<Size> validSizes = Arrays.asList(choices);

        // Sort by area (smallest first for performance)
        Collections.sort(validSizes, new Comparator<Size>() {
            @Override
            public int compare(Size lhs, Size rhs) {
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                        (long) rhs.getWidth() * rhs.getHeight());
            }
        });

        // Try to find size close to preferred dimensions
        for (Size size : validSizes) {
            if (size.getWidth() <= MAX_PREVIEW_WIDTH &&
                    size.getHeight() <= MAX_PREVIEW_HEIGHT &&
                    size.getWidth() >= PREFERRED_WIDTH &&
                    size.getHeight() >= PREFERRED_HEIGHT) {
                return size;
            }
        }

        // If no ideal size found, use the largest available (but not too large)
        for (Size size : validSizes) {
            if (size.getWidth() <= MAX_PREVIEW_WIDTH &&
                    size.getHeight() <= MAX_PREVIEW_HEIGHT) {
                return size;
            }
        }

        // Fallback to first available size
        return validSizes.get(0);
    }

    /**
     * Setup ImageReader for capturing frame data
     */
    private void setupImageReader() {
        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                ImageFormat.YUV_420_888, 2);

        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = reader.acquireLatestImage();
                if (image != null && frameCallback != null) {
                    processImageFrame(image);
                    image.close();
                }
            }
        }, backgroundHandler);
    }

    /**
     * Process captured image frame and convert to byte array
     */
    private void processImageFrame(Image image) {
        try {
            // Convert YUV_420_888 to RGB byte array
            byte[] rgbBytes = convertYUVToRGB(image);

            if (rgbBytes != null && frameCallback != null) {
                frameCallback.onFrameAvailable(rgbBytes,
                        image.getWidth(),
                        image.getHeight());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing image frame", e);
            if (frameCallback != null) {
                frameCallback.onError("Error processing frame: " + e.getMessage());
            }
        }
    }

    /**
     * Convert YUV_420_888 image to RGBA byte array
     */
    private byte[] convertYUVToRGB(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize);

        // Copy U and V planes (interleaved for NV21)
        byte[] uvPixel = new byte[2];
        int uvIndex = ySize;
        for (int i = 0; i < uSize; i++) {
            uvPixel[0] = vBuffer.get(i);
            uvPixel[1] = uBuffer.get(i);
            nv21[uvIndex++] = uvPixel[0];
            nv21[uvIndex++] = uvPixel[1];
        }

        // Convert to RGBA
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] rgbaBytes = new byte[width * height * 4];

        convertYUV420ToRGBA(nv21, rgbaBytes, width, height);

        return rgbaBytes;
    }

    /**
     * Convert YUV420 to RGBA format
     */
    private void convertYUV420ToRGBA(byte[] yuv, byte[] rgba, int width, int height) {
        int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & yuv[yp]) - 16;
                if (y < 0) y = 0;

                if ((i & 1) == 0) {
                    v = (0xff & yuv[uvp++]) - 128;
                    u = (0xff & yuv[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) r = 0; else if (r > 262143) r = 262143;
                if (g < 0) g = 0; else if (g > 262143) g = 262143;
                if (b < 0) b = 0; else if (b > 262143) b = 262143;

                int pixelIndex = yp * 4;
                rgba[pixelIndex] = (byte) ((r >> 10) & 0xff);     // R
                rgba[pixelIndex + 1] = (byte) ((g >> 10) & 0xff); // G
                rgba[pixelIndex + 2] = (byte) ((b >> 10) & 0xff); // B
                rgba[pixelIndex + 3] = (byte) 255;                // A
            }
        }
    }

    /**
     * Camera state callback
     */
    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "Camera opened successfully");
            cameraDevice = camera;
            isCameraOpened = true;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.w(TAG, "Camera disconnected");
            camera.close();
            cameraDevice = null;
            isCameraOpened = false;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.e(TAG, "Camera error: " + error);
            camera.close();
            cameraDevice = null;
            isCameraOpened = false;

            if (frameCallback != null) {
                frameCallback.onError("Camera error: " + error);
            }
        }
    };

    /**
     * Create camera preview session
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            Surface surface = new Surface(texture);
            Surface readerSurface = imageReader.getSurface();

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(readerSurface);

            // Set auto-focus and auto-exposure
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            cameraDevice.createCaptureSession(Arrays.asList(surface, readerSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            if (cameraDevice == null) return;

                            captureSession = session;
                            startCapture();
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure camera capture session");
                            if (frameCallback != null) {
                                frameCallback.onError("Failed to configure camera session");
                            }
                        }
                    }, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating camera preview session", e);
            if (frameCallback != null) {
                frameCallback.onError("Error creating preview session: " + e.getMessage());
            }
        }
    }

    /**
     * Start camera capture
     */
    public void startCamera() {
        if (captureSession != null && !isCapturing) {
            startCapture();
        }
    }

    /**
     * Start capture process
     */
    private void startCapture() {
        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            isCapturing = true;
            Log.i(TAG, "Camera capture started");

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error starting camera capture", e);
            if (frameCallback != null) {
                frameCallback.onError("Error starting capture: " + e.getMessage());
            }
        }
    }

    /**
     * Stop camera capture
     */
    public void stopCamera() {
        isCapturing = false;

        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                Log.i(TAG, "Camera capture stopped");
            } catch (CameraAccessException e) {
                Log.e(TAG, "Error stopping camera capture", e);
            }
        }
    }

    /**
     * Cleanup camera resources
     */
    public void cleanup() {
        stopCamera();

        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }

        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        stopBackgroundThread();

        isCameraOpened = false;
        Log.i(TAG, "Camera resources cleaned up");
    }

    /**
     * Get current preview size
     */
    public Size getPreviewSize() {
        return previewSize;
    }

    /**
     * Check if camera is currently opened
     */
    public boolean isCameraOpened() {
        return isCameraOpened;
    }

    /**
     * Check if camera is currently capturing
     */
    public boolean isCapturing() {
        return isCapturing;
    }
}