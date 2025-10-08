package com.example.edgedetectionviewer;

/**
 * JNI Bridge class for native edge detection operations
 * This class provides the interface between Java code and native C++ implementation
 */
public class EdgeDetectionJNI {

    // Load native library
    static {
        try {
            System.loadLibrary("native-lib");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("EdgeDetectionJNI", "Failed to load native library: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Initialize the native edge detection system
     * @return true if initialization successful
     */
    public static native boolean nativeInit();

    /**
     * Process camera frame with edge detection
     * @param inputData Input image data as byte array (RGBA format)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @return Processed image as byte array (RGBA format)
     */
    public static native byte[] processFrame(byte[] inputData, int width, int height);

    /**
     * Initialize OpenGL ES renderer
     * @param width Surface width in pixels
     * @param height Surface height in pixels
     * @return true if initialization successful
     */
    public static native boolean initGL(int width, int height);

    /**
     * Create shader program for texture rendering
     * @return Shader program ID, or 0 if failed
     */
    public static native int createShaderProgram();

    /**
     * Create OpenGL texture for camera frames
     * @param width Texture width in pixels
     * @param height Texture height in pixels
     * @return Texture ID, or 0 if failed
     */
    public static native int createTexture(int width, int height);

    /**
     * Update texture with processed frame data
     * @param textureId OpenGL texture ID
     * @param pixelData Processed image data (RGBA format)
     * @param width Image width in pixels
     * @param height Image height in pixels
     */
    public static native void updateTexture(int textureId, byte[] pixelData, int width, int height);

    /**
     * Render current frame to screen
     * @param programId Shader program ID
     * @param textureId Texture ID to render
     */
    public static native void renderFrame(int programId, int textureId);

    /**
     * Get current performance statistics
     * @return String containing performance metrics (FPS, frame count, etc.)
     */
    public static native String getPerformanceStats();

    /**
     * Update edge detection parameters at runtime
     * @param lowThreshold Lower threshold for Canny edge detection
     * @param highThreshold Upper threshold for Canny edge detection
     * @param blurKernel Gaussian blur kernel size (3, 5, 7)
     */
    public static native void updateParameters(double lowThreshold, double highThreshold, int blurKernel);

    /**
     * Cleanup native resources
     * Should be called when the application is shutting down
     */
    public static native void cleanup();

    /**
     * Check if native library is properly loaded and initialized
     * @return true if library is ready for use
     */
    public static boolean isLibraryLoaded() {
        try {
            nativeInit();
            return true;
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.e("EdgeDetectionJNI", "Native library not properly loaded", e);
            return false;
        }
    }
}