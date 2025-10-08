//
// Created by my lapi on 08-10-2025.
//
#include "image_processor.h"
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "GLRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace GLRenderer {

// Vertex data for texture quad (position + texture coordinates)
    static const float quadVertices[] = {
            // Positions    // Texture coords
            -1.0f,  1.0f,   0.0f, 0.0f,  // Top-left
            -1.0f, -1.0f,   0.0f, 1.0f,  // Bottom-left
            1.0f, -1.0f,   1.0f, 1.0f,  // Bottom-right
            1.0f,  1.0f,   1.0f, 0.0f   // Top-right
    };

    static const unsigned int quadIndices[] = {
            0, 1, 2,    // First triangle
            0, 2, 3     // Second triangle
    };

// OpenGL objects
    static unsigned int VAO = 0;
    static unsigned int VBO = 0;
    static unsigned int EBO = 0;

/**
 * @brief Check OpenGL errors and log them
 * @param operation Description of the operation that was performed
 */
    static void checkGLError(const char* operation) {
        GLenum error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGE("OpenGL error after %s: 0x%x", operation, error);
        }
    }

/**
 * @brief Compile a shader from source code
 * @param type Shader type (GL_VERTEX_SHADER or GL_FRAGMENT_SHADER)
 * @param source Shader source code
 * @return Compiled shader ID or 0 if failed
 */
    static unsigned int compileShader(unsigned int type, const char* source) {
        unsigned int shader = glCreateShader(type);
        if (shader == 0) {
            LOGE("Failed to create shader");
            return 0;
        }

        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);

        // Check compilation status
        int success;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
        if (!success) {
            char infoLog[512];
            glGetShaderInfoLog(shader, 512, nullptr, infoLog);
            LOGE("Shader compilation failed: %s", infoLog);
            glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    bool initializeGL(int width, int height) {
        try {
            LOGI("Initializing OpenGL ES renderer (%dx%d)", width, height);

            // Set viewport
            glViewport(0, 0, width, height);
            checkGLError("glViewport");

            // Enable blending for transparency
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            checkGLError("glBlendFunc");

            // Generate and bind vertex array object
            glGenBuffers(1, &VAO);
            glGenBuffers(1, &VBO);
            glGenBuffers(1, &EBO);

            glBindBuffer(GL_ARRAY_BUFFER, VBO);
            glBufferData(GL_ARRAY_BUFFER, sizeof(quadVertices), quadVertices, GL_STATIC_DRAW);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(quadIndices), quadIndices, GL_STATIC_DRAW);

            checkGLError("Buffer setup");

            // Clear color
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

            LOGI("OpenGL ES initialization completed successfully");
            return true;

        } catch (const std::exception& e) {
            LOGE("Exception in initializeGL: %s", e.what());
            return false;
        }
    }

    ShaderProgram createShaderProgram(const char* vertexShaderSource, const char* fragmentShaderSource) {
        ShaderProgram program = {0};

        try {
            // Compile vertex shader
            unsigned int vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
            if (vertexShader == 0) {
                LOGE("Failed to compile vertex shader");
                return program;
            }

            // Compile fragment shader
            unsigned int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource);
            if (fragmentShader == 0) {
                LOGE("Failed to compile fragment shader");
                glDeleteShader(vertexShader);
                return program;
            }

            // Create shader program
            program.programId = glCreateProgram();
            glAttachShader(program.programId, vertexShader);
            glAttachShader(program.programId, fragmentShader);
            glLinkProgram(program.programId);

            // Check linking status
            int success;
            glGetProgramiv(program.programId, GL_LINK_STATUS, &success);
            if (!success) {
                char infoLog[512];
                glGetProgramInfoLog(program.programId, 512, nullptr, infoLog);
                LOGE("Shader program linking failed: %s", infoLog);
                glDeleteProgram(program.programId);
                program.programId = 0;
            }

            // Clean up individual shaders
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);

            if (program.programId != 0) {
                // Get attribute and uniform locations
                program.positionAttrib = glGetAttribLocation(program.programId, "aPosition");
                program.texCoordAttrib = glGetAttribLocation(program.programId, "aTexCoord");
                program.textureUniform = glGetUniformLocation(program.programId, "uTexture");
                program.mvpMatrixUniform = glGetUniformLocation(program.programId, "uMVPMatrix");

                LOGI("Shader program created successfully (ID: %d)", program.programId);
            }

            checkGLError("createShaderProgram");
            return program;

        } catch (const std::exception& e) {
            LOGE("Exception in createShaderProgram: %s", e.what());
            if (program.programId != 0) {
                glDeleteProgram(program.programId);
                program.programId = 0;
            }
            return program;
        }
    }

    TextureInfo createTexture(int width, int height) {
        TextureInfo texInfo = {0};

        try {
            glGenTextures(1, &texInfo.textureId);
            glBindTexture(GL_TEXTURE_2D, texInfo.textureId);

            // Set texture parameters for optimal performance
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // Allocate texture memory
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

            texInfo.width = width;
            texInfo.height = height;
            texInfo.format = GL_RGBA;

            checkGLError("createTexture");
            LOGI("Texture created successfully (ID: %d, Size: %dx%d)", texInfo.textureId, width, height);

        } catch (const std::exception& e) {
            LOGE("Exception in createTexture: %s", e.what());
            if (texInfo.textureId != 0) {
                glDeleteTextures(1, &texInfo.textureId);
                texInfo.textureId = 0;
            }
        }

        return texInfo;
    }

    void updateTexture(const TextureInfo& textureInfo, const uint8_t* pixelData) {
        try {
            if (textureInfo.textureId == 0 || !pixelData) {
                LOGE("Invalid texture or pixel data");
                return;
            }

            glBindTexture(GL_TEXTURE_2D, textureInfo.textureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                            textureInfo.width, textureInfo.height,
                            textureInfo.format, GL_UNSIGNED_BYTE, pixelData);

            checkGLError("updateTexture");

        } catch (const std::exception& e) {
            LOGE("Exception in updateTexture: %s", e.what());
        }
    }

    void renderTexture(const ShaderProgram& shaderProgram, const TextureInfo& textureInfo) {
        try {
            if (shaderProgram.programId == 0 || textureInfo.textureId == 0) {
                LOGE("Invalid shader program or texture");
                return;
            }

            // Clear the screen
            glClear(GL_COLOR_BUFFER_BIT);

            // Use shader program
            glUseProgram(shaderProgram.programId);

            // Bind texture
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureInfo.textureId);
            glUniform1i(shaderProgram.textureUniform, 0);

            // Set up vertex attributes
            glBindBuffer(GL_ARRAY_BUFFER, VBO);

            // Position attribute
            glVertexAttribPointer(shaderProgram.positionAttrib, 2, GL_FLOAT, GL_FALSE,
                                  4 * sizeof(float), (void*)0);
            glEnableVertexAttribArray(shaderProgram.positionAttrib);

            // Texture coordinate attribute
            glVertexAttribPointer(shaderProgram.texCoordAttrib, 2, GL_FLOAT, GL_FALSE,
                                  4 * sizeof(float), (void*)(2 * sizeof(float)));
            glEnableVertexAttribArray(shaderProgram.texCoordAttrib);

            // Draw quad
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBO);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

            // Disable vertex attributes
            glDisableVertexAttribArray(shaderProgram.positionAttrib);
            glDisableVertexAttribArray(shaderProgram.texCoordAttrib);

            checkGLError("renderTexture");

        } catch (const std::exception& e) {
            LOGE("Exception in renderTexture: %s", e.what());
        }
    }

    void onSurfaceChanged(int newWidth, int newHeight) {
        glViewport(0, 0, newWidth, newHeight);
        LOGI("Surface changed to %dx%d", newWidth, newHeight);
    }

    void cleanupGL(const ShaderProgram& shaderProgram, const TextureInfo& textureInfo) {
        if (shaderProgram.programId != 0) {
            glDeleteProgram(shaderProgram.programId);
            LOGI("Deleted shader program: %d", shaderProgram.programId);
        }

        if (textureInfo.textureId != 0) {
            glDeleteTextures(1, &textureInfo.textureId);
            LOGI("Deleted texture: %d", textureInfo.textureId);
        }

        if (VAO != 0) glDeleteBuffers(1, &VAO);
        if (VBO != 0) glDeleteBuffers(1, &VBO);
        if (EBO != 0) glDeleteBuffers(1, &EBO);

        LOGI("OpenGL cleanup completed");
    }

} // namespace GLRenderer