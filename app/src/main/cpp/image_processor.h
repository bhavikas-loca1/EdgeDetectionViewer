#ifndef IMAGE_PROCESSOR_H
#define IMAGE_PROCESSOR_H

#include <opencv2/opencv.hpp>
#include <cstdint>

namespace EdgeDetection {

/**
 * @brief Apply Canny edge detection algorithm
 * @param inputMat Input image matrix (BGR/RGBA format)
 * @param outputMat Output edge image (single channel)
 * @param lowThreshold Lower threshold for edge detection
 * @param highThreshold Upper threshold for edge detection
 * @param kernelSize Gaussian blur kernel size
 * @return true if successful, false otherwise
 */
    bool applyCanny(const cv::Mat& inputMat, cv::Mat& outputMat,
                    double lowThreshold, double highThreshold,
                    int kernelSize);

/**
 * @brief Apply Sobel edge detection algorithm
 * @param inputMat Input image matrix
 * @param outputMat Output edge image
 * @param kernelSize Sobel kernel size (1, 3, 5, 7)
 * @return true if successful, false otherwise
 */
    bool applySobel(const cv::Mat& inputMat, cv::Mat& outputMat, int kernelSize);

/**
 * @brief Convert single channel edge image to RGBA format for OpenGL
 * @param edgeMat Input edge image (single channel)
 * @param rgbaMat Output RGBA image
 * @return true if successful, false otherwise
 */
    bool edgeToRGBA(const cv::Mat& edgeMat, cv::Mat& rgbaMat);

/**
 * @brief Process camera frame with edge detection (optimized for real-time)
 * @param inputData Input frame data (RGBA format)
 * @param width Frame width in pixels
 * @param height Frame height in pixels
 * @param outputData Output processed frame data (RGBA format)
 * @return true if successful, false otherwise
 */
    bool processFrame(const uint8_t* inputData, int width, int height, uint8_t* outputData);

/**
 * @brief Structure to hold processing statistics
 */
    struct ProcessingStats {
        double processingTime;      // Processing time in milliseconds
        int framesProcessed;        // Total frames processed
        double averageFps;          // Average FPS
        int currentThreshold1;      // Current lower threshold
        int currentThreshold2;      // Current upper threshold
    };

/**
 * @brief Get current processing statistics
 * @return ProcessingStats structure with current metrics
 */
    ProcessingStats getProcessingStats();

/**
 * @brief Reset processing statistics
 */
    void resetProcessingStats();

/**
 * @brief Update edge detection parameters dynamically
 * @param lowThreshold New lower threshold
 * @param highThreshold New upper threshold
 * @param blurKernel New Gaussian blur kernel size
 */
    void updateProcessingParameters(double lowThreshold, double highThreshold, int blurKernel);

} // namespace EdgeDetection

// OpenGL ES related structures and functions
namespace GLRenderer {

/**
 * @brief OpenGL texture information structure
 */
    struct TextureInfo {
        unsigned int textureId;     // OpenGL texture ID
        int width;                  // Texture width
        int height;                 // Texture height
        unsigned int format;        // Texture format (GL_RGBA, etc.)
    };

/**
 * @brief Shader program information
 */
    struct ShaderProgram {
        unsigned int programId;     // Shader program ID
        int positionAttrib;         // Vertex position attribute location
        int texCoordAttrib;         // Texture coordinate attribute location
        int textureUniform;         // Texture uniform location
        int mvpMatrixUniform;       // MVP matrix uniform location
    };

/**
 * @brief Initialize OpenGL ES context and resources
 * @param width Surface width
 * @param height Surface height
 * @return true if successful, false otherwise
 */
    bool initializeGL(int width, int height);

/**
 * @brief Create and compile shader program
 * @param vertexShaderSource Vertex shader source code
 * @param fragmentShaderSource Fragment shader source code
 * @return ShaderProgram structure with compiled program info
 */
    ShaderProgram createShaderProgram(const char* vertexShaderSource, const char* fragmentShaderSource);

/**
 * @brief Create OpenGL texture for camera frames
 * @param width Texture width
 * @param height Texture height
 * @return TextureInfo structure with texture details
 */
    TextureInfo createTexture(int width, int height);

/**
 * @brief Update texture with processed frame data
 * @param textureInfo Texture to update
 * @param pixelData New pixel data (RGBA format)
 */
    void updateTexture(const TextureInfo& textureInfo, const uint8_t* pixelData);

/**
 * @brief Render texture to screen
 * @param shaderProgram Shader program to use
 * @param textureInfo Texture to render
 */
    void renderTexture(const ShaderProgram& shaderProgram, const TextureInfo& textureInfo);

/**
 * @brief Cleanup OpenGL resources
 * @param shaderProgram Shader program to cleanup
 * @param textureInfo Texture to cleanup
 */
    void cleanupGL(const ShaderProgram& shaderProgram, const TextureInfo& textureInfo);

/**
 * @brief Handle surface size change
 * @param newWidth New surface width
 * @param newHeight New surface height
 */
    void onSurfaceChanged(int newWidth, int newHeight);

} // namespace GLRenderer

#endif // IMAGE_PROCESSOR_H