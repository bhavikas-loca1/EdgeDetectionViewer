//
// Created by my lapi on 08-10-2025.
//
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <string>
#include <memory>
#include <chrono>

#include "image_processor.h"

#define LOG_TAG "EdgeDetectionJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global variables for performance tracking
static std::chrono::high_resolution_clock::time_point lastFrameTime;
static int frameCount = 0;
static double averageFps = 0.0;

// Shader source code (embedded as strings)
const char* vertexShaderSource = R"(
#version 100
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
uniform mat4 uMVPMatrix;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
)";

const char* fragmentShaderSource = R"(
#version 100
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;

void main() {
    vec4 textureColor = texture2D(uTexture, vTexCoord);
    gl_FragColor = textureColor;
}
)";

extern "C" {

/**
 * @brief Initialize the native edge detection system
 * @param env JNI environment
 * @param thiz Java object instance
 * @return true if initialization successful
 */
JNIEXPORT jboolean JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_nativeInit(
        JNIEnv* env, jobject thiz) {

    LOGI("Initializing native edge detection system");

    try {
        // Reset performance counters
        frameCount = 0;
        averageFps = 0.0;
        lastFrameTime = std::chrono::high_resolution_clock::now();

        // Initialize OpenCV (if needed)
        LOGI("Native initialization completed successfully");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Exception during native initialization: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * @brief Process camera frame with edge detection
 * @param env JNI environment
 * @param thiz Java object instance
 * @param inputArray Input image data as byte array (RGBA)
 * @param width Image width
 * @param height Image height
 * @return Processed image as byte array (RGBA)
 */
JNIEXPORT jbyteArray JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_processFrame(
        JNIEnv* env, jobject thiz, jbyteArray inputArray, jint width, jint height) {

    auto frameStart = std::chrono::high_resolution_clock::now();

    try {
        if (!inputArray) {
            LOGE("Input array is null");
            return nullptr;
        }

        // Get input data
        jbyte* inputBytes = env->GetByteArrayElements(inputArray, nullptr);
        if (!inputBytes) {
            LOGE("Failed to get input byte array elements");
            return nullptr;
        }

        jsize inputLength = env->GetArrayLength(inputArray);
        if (inputLength != width * height * 4) {
            LOGE("Input array size mismatch: expected %d, got %d",
                 width * height * 4, inputLength);
            env->ReleaseByteArrayElements(inputArray, inputBytes, JNI_ABORT);
            return nullptr;
        }

        // Create output array
        jbyteArray outputArray = env->NewByteArray(inputLength);
        if (!outputArray) {
            LOGE("Failed to create output byte array");
            env->ReleaseByteArrayElements(inputArray, inputBytes, JNI_ABORT);
            return nullptr;
        }

        jbyte* outputBytes = env->GetByteArrayElements(outputArray, nullptr);
        if (!outputBytes) {
            LOGE("Failed to get output byte array elements");
            env->ReleaseByteArrayElements(inputArray, inputBytes, JNI_ABORT);
            return nullptr;
        }

        // Process frame with edge detection
        bool success = EdgeDetection::processFrame(
                reinterpret_cast<const uint8_t*>(inputBytes),
                width, height,
                reinterpret_cast<uint8_t*>(outputBytes)
        );

        // Release input array
        env->ReleaseByteArrayElements(inputArray, inputBytes, JNI_ABORT);

        if (!success) {
            LOGE("Frame processing failed");
            env->ReleaseByteArrayElements(outputArray, outputBytes, JNI_ABORT);
            return nullptr;
        }

        // Commit output array
        env->ReleaseByteArrayElements(outputArray, outputBytes, 0);

        // Update performance metrics
        frameCount++;
        auto frameEnd = std::chrono::high_resolution_clock::now();
        auto frameDuration = std::chrono::duration_cast<std::chrono::milliseconds>
                (frameEnd - frameStart).count();

        if (frameCount % 30 == 0) {  // Log every 30 frames
            auto totalDuration = std::chrono::duration_cast<std::chrono::milliseconds>
                    (frameEnd - lastFrameTime).count();
            averageFps = 30000.0 / totalDuration;  // 30 frames / duration in ms * 1000
            lastFrameTime = frameEnd;

            LOGI("Frame %d processed in %ld ms, Average FPS: %.2f",
                 frameCount, frameDuration, averageFps);
        }

        return outputArray;

    } catch (const std::exception& e) {
        LOGE("Exception in processFrame: %s", e.what());
        return nullptr;
    }
}

/**
 * @brief Initialize OpenGL ES renderer
 * @param env JNI environment
 * @param thiz Java object instance
 * @param width Surface width
 * @param height Surface height
 * @return true if successful
 */
JNIEXPORT jboolean JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_initGL(
        JNIEnv* env, jobject thiz, jint width, jint height) {

    LOGI("Initializing OpenGL ES renderer (%dx%d)", width, height);

    try {
        bool success = GLRenderer::initializeGL(width, height);
        return success ? JNI_TRUE : JNI_FALSE;

    } catch (const std::exception& e) {
        LOGE("Exception in initGL: %s", e.what());
        return JNI_FALSE;
    }
}

/**
 * @brief Create shader program for texture rendering
 * @param env JNI environment
 * @param thiz Java object instance
 * @return Shader program ID
 */
JNIEXPORT jint JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_createShaderProgram(
        JNIEnv* env, jobject thiz) {

    try {
        GLRenderer::ShaderProgram program = GLRenderer::createShaderProgram(
                vertexShaderSource, fragmentShaderSource);

        return static_cast<jint>(program.programId);

    } catch (const std::exception& e) {
        LOGE("Exception in createShaderProgram: %s", e.what());
        return 0;
    }
}

/**
 * @brief Create OpenGL texture for rendering
 * @param env JNI environment
 * @param thiz Java object instance
 * @param width Texture width
 * @param height Texture height
 * @return Texture ID
 */
JNIEXPORT jint JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_createTexture(
        JNIEnv* env, jobject thiz, jint width, jint height) {

    try {
        GLRenderer::TextureInfo textureInfo = GLRenderer::createTexture(width, height);
        return static_cast<jint>(textureInfo.textureId);

    } catch (const std::exception& e) {
        LOGE("Exception in createTexture: %s", e.what());
        return 0;
    }
}

/**
 * @brief Update texture with processed frame data
 * @param env JNI environment
 * @param thiz Java object instance
 * @param textureId OpenGL texture ID
 * @param pixelData Processed image data
 * @param width Image width
 * @param height Image height
 */
JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_updateTexture(
        JNIEnv* env, jobject thiz, jint textureId, jbyteArray pixelData,
        jint width, jint height) {

try {
if (!pixelData) {
LOGE("Pixel data is null");
return;
}

jbyte* pixels = env->GetByteArrayElements(pixelData, nullptr);
if (!pixels) {
LOGE("Failed to get pixel data elements");
return;
}

GLRenderer::TextureInfo textureInfo;
textureInfo.textureId = static_cast<unsigned int>(textureId);
textureInfo.width = width;
textureInfo.height = height;
textureInfo.format = 0x1908; // GL_RGBA

GLRenderer::updateTexture(textureInfo,
reinterpret_cast<const uint8_t*>(pixels));

env->ReleaseByteArrayElements(pixelData, pixels, JNI_ABORT);

} catch (const std::exception& e) {
LOGE("Exception in updateTexture: %s", e.what());
}
}

/**
 * @brief Render texture to screen
 * @param env JNI environment
 * @param thiz Java object instance
 * @param programId Shader program ID
 * @param textureId Texture ID
 */
JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_renderFrame(
        JNIEnv* env, jobject thiz, jint programId, jint textureId) {

try {
GLRenderer::ShaderProgram program;
program.programId = static_cast<unsigned int>(programId);
// Note: In a real implementation, you'd store these attribute/uniform locations
program.positionAttrib = 0;
program.texCoordAttrib = 1;
program.textureUniform = 0;
program.mvpMatrixUniform = -1;

GLRenderer::TextureInfo textureInfo;
textureInfo.textureId = static_cast<unsigned int>(textureId);

GLRenderer::renderTexture(program, textureInfo);

} catch (const std::exception& e) {
LOGE("Exception in renderFrame: %s", e.what());
}
}

/**
 * @brief Get current performance statistics
 * @param env JNI environment
 * @param thiz Java object instance
 * @return String containing performance metrics
 */
JNIEXPORT jstring JNICALL
        Java_com_example_edgedetectionviewer_EdgeDetectionJNI_getPerformanceStats(
        JNIEnv* env, jobject thiz) {

try {
std::string stats = "Frames: " + std::to_string(frameCount) +
                    ", FPS: " + std::to_string(averageFps);

return env->NewStringUTF(stats.c_str());

} catch (const std::exception& e) {
LOGE("Exception in getPerformanceStats: %s", e.what());
return env->NewStringUTF("Error getting stats");
}
}

/**
 * @brief Cleanup native resources
 * @param env JNI environment
 * @param thiz Java object instance
 */
JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_EdgeDetectionJNI_cleanup(
        JNIEnv* env, jobject thiz) {

LOGI("Cleaning up native resources");

try {
// Reset performance counters
frameCount = 0;
averageFps = 0.0;

LOGI("Native cleanup completed");

} catch (const std::exception& e) {
LOGE("Exception during cleanup: %s", e.what());
}
}

} // extern "C"