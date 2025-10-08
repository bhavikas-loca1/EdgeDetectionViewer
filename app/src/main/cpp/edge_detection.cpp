//
// Created by my lapi on 08-10-2025.
//
//
// Created by my lapi on 08-10-2025.
//
#include "image_processor.h"
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "EdgeDetection"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace EdgeDetection {

/**
 * @brief Apply Canny edge detection to input image
 * @param inputMat Input image matrix (BGR or RGBA format)
 * @param outputMat Output edge image (single channel)
 * @param lowThreshold Lower threshold for edge detection (default: 100)
 * @param highThreshold Upper threshold for edge detection (default: 200)
 * @param kernelSize Gaussian blur kernel size (default: 3)
 * @return true if successful, false otherwise
 */
    bool applyCanny(const cv::Mat& inputMat, cv::Mat& outputMat,
                    double lowThreshold = 100.0, double highThreshold = 200.0,
                    int kernelSize = 3) {
        try {
            if (inputMat.empty()) {
                LOGE("Input matrix is empty");
                return false;
            }

            cv::Mat grayMat;

            // Convert to grayscale if needed
            if (inputMat.channels() == 4) {
                cv::cvtColor(inputMat, grayMat, cv::COLOR_RGBA2GRAY);
            } else if (inputMat.channels() == 3) {
                cv::cvtColor(inputMat, grayMat, cv::COLOR_BGR2GRAY);
            } else {
                grayMat = inputMat.clone();
            }

            // Apply Gaussian blur for noise reduction
            cv::Mat blurredMat;
            cv::GaussianBlur(grayMat, blurredMat, cv::Size(kernelSize, kernelSize), 1.4);

            // Apply Canny edge detection
            cv::Canny(blurredMat, outputMat, lowThreshold, highThreshold, 3, false);

            LOGI("Canny edge detection completed successfully");
            return true;

        } catch (const cv::Exception& e) {
            LOGE("OpenCV exception in applyCanny: %s", e.what());
            return false;
        } catch (const std::exception& e) {
            LOGE("Standard exception in applyCanny: %s", e.what());
            return false;
        }
    }

/**
 * @brief Apply Sobel edge detection to input image
 * @param inputMat Input image matrix
 * @param outputMat Output edge image
 * @param kernelSize Sobel kernel size (1, 3, 5, 7)
 * @return true if successful, false otherwise
 */
    bool applySobel(const cv::Mat& inputMat, cv::Mat& outputMat, int kernelSize = 3) {
        try {
            if (inputMat.empty()) {
                LOGE("Input matrix is empty for Sobel");
                return false;
            }

            cv::Mat grayMat;

            // Convert to grayscale if needed
            if (inputMat.channels() == 4) {
                cv::cvtColor(inputMat, grayMat, cv::COLOR_RGBA2GRAY);
            } else if (inputMat.channels() == 3) {
                cv::cvtColor(inputMat, grayMat, cv::COLOR_BGR2GRAY);
            } else {
                grayMat = inputMat.clone();
            }

            // Apply Gaussian blur
            cv::Mat blurredMat;
            cv::GaussianBlur(grayMat, blurredMat, cv::Size(3, 3), 0);

            // Compute Sobel derivatives
            cv::Mat sobelX, sobelY;
            cv::Sobel(blurredMat, sobelX, CV_64F, 1, 0, kernelSize);
            cv::Sobel(blurredMat, sobelY, CV_64F, 0, 1, kernelSize);

            // Compute magnitude
            cv::Mat magnitude;
            cv::magnitude(sobelX, sobelY, magnitude);

            // Convert to 8-bit
            magnitude.convertTo(outputMat, CV_8UC1);

            LOGI("Sobel edge detection completed successfully");
            return true;

        } catch (const cv::Exception& e) {
            LOGE("OpenCV exception in applySobel: %s", e.what());
            return false;
        } catch (const std::exception& e) {
            LOGE("Standard exception in applySobel: %s", e.what());
            return false;
        }
    }

/**
 * @brief Convert edge detection result to RGBA format for OpenGL texture
 * @param edgeMat Input edge image (single channel)
 * @param rgbaMat Output RGBA image
 * @return true if successful, false otherwise
 */
    bool edgeToRGBA(const cv::Mat& edgeMat, cv::Mat& rgbaMat) {
        try {
            if (edgeMat.empty()) {
                LOGE("Edge matrix is empty");
                return false;
            }

            // Convert single channel edge image to RGBA
            // White edges on transparent background
            std::vector<cv::Mat> channels(4);
            channels[0] = edgeMat; // Blue channel
            channels[1] = edgeMat; // Green channel
            channels[2] = edgeMat; // Red channel
            channels[3] = edgeMat; // Alpha channel

            cv::merge(channels, rgbaMat);

            return true;

        } catch (const cv::Exception& e) {
            LOGE("OpenCV exception in edgeToRGBA: %s", e.what());
            return false;
        }
    }

/**
 * @brief Process camera frame with optimized parameters for real-time performance
 * @param inputData Input frame data (RGBA format)
 * @param width Frame width
 * @param height Frame height
 * @param outputData Output processed frame data
 * @return true if successful, false otherwise
 */
    bool processFrame(const uint8_t* inputData, int width, int height, uint8_t* outputData) {
        try {
            if (!inputData || !outputData) {
                LOGE("Invalid input or output data pointers");
                return false;
            }

            // Create OpenCV Mat from input data
            cv::Mat inputMat(height, width, CV_8UC4, (void*)inputData);

            // Apply Canny edge detection with optimized parameters for real-time
            cv::Mat edgeMat;
            if (!applyCanny(inputMat, edgeMat, 50.0, 150.0, 3)) {
                LOGE("Failed to apply Canny edge detection");
                return false;
            }

            // Convert edges to RGBA format
            cv::Mat outputMat;
            if (!edgeToRGBA(edgeMat, outputMat)) {
                LOGE("Failed to convert edges to RGBA");
                return false;
            }

            // Copy processed data to output buffer
            if (outputMat.isContinuous()) {
                memcpy(outputData, outputMat.data, width * height * 4);
            } else {
                // Handle non-continuous data
                for (int i = 0; i < height; i++) {
                    memcpy(outputData + i * width * 4,
                           outputMat.ptr<uint8_t>(i),
                           width * 4);
                }
            }

            return true;

        } catch (const cv::Exception& e) {
            LOGE("OpenCV exception in processFrame: %s", e.what());
            return false;
        } catch (const std::exception& e) {
            LOGE("Standard exception in processFrame: %s", e.what());
            return false;
        }
    }

} // namespace EdgeDetection