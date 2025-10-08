# Sets the minimum version of CMake required to build the native library
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name
project("EdgeDetectionViewer")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable debug symbols for debugging
set(CMAKE_BUILD_TYPE Debug)

# Find required packages
find_package(PkgConfig REQUIRED)

# Set OpenCV path - Update this path to your OpenCV Android SDK location
set(OpenCV_DIR "C:/Users/my lapi/Downloads/opencv-4.8.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)

# Include directories
include_directories(${OpenCV_INCLUDE_DIRS})
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Add the native library
add_library(
        native-lib
        SHARED

        # Core implementation files
        edge_detection.cpp
        gl_renderer.cpp
        jni_bridge.cpp
)

# Link libraries
target_link_libraries(
        native-lib

        # OpenCV libraries
        ${OpenCV_LIBS}

        # Android NDK libraries
        android
        log
        jnigraphics

        # OpenGL ES libraries
        GLESv2
        EGL

        # Native window management
        native_window
)

# Compiler-specific options
target_compile_definitions(native-lib PRIVATE
        GL_GLEXT_PROTOTYPES
        EGL_EGLEXT_PROTOTYPES
)

# Optimization flags for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(native-lib PRIVATE
            -O3
            -ffast-math
            -DNDEBUG
    )
endif()

# Add preprocessor definitions
target_compile_definitions(native-lib PRIVATE
        -DANDROID
        -DHAVE_OPENCV
)

# Set architecture-specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_options(native-lib PRIVATE -march=armv8-a)
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_compile_options(native-lib PRIVATE -march=armv7-a -mfpu=neon)
endif()

# Debug logging
message(STATUS "OpenCV version: ${OpenCV_VERSION}")
message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
message(STATUS "Android ABI: ${ANDROID_ABI}")
message(STATUS "Android API level: ${ANDROID_NATIVE_API_LEVEL}")