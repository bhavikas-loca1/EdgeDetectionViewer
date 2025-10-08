#version 100

// Vertex attributes
attribute vec2 aPosition;      // Vertex position in normalized device coordinates
attribute vec2 aTexCoord;      // Texture coordinates (0,0 to 1,1)

// Output to fragment shader
varying vec2 vTexCoord;        // Texture coordinates passed to fragment shader

// Uniform variables
uniform mat4 uMVPMatrix;       // Model-View-Projection matrix (optional for 2D)

void main() {
    // Pass texture coordinates to fragment shader
    vTexCoord = aTexCoord;

    // Transform vertex position
    // For simple 2D rendering, we can use position directly
    gl_Position = vec4(aPosition, 0.0, 1.0);

    // If MVP matrix is provided, apply transformation
    // gl_Position = uMVPMatrix * vec4(aPosition, 0.0, 1.0);
}