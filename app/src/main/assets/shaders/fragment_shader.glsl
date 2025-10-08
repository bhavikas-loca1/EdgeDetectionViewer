#version 100

// Set precision for fragment shader calculations
precision mediump float;

// Input from vertex shader
varying vec2 vTexCoord;        // Texture coordinates from vertex shader

// Uniform variables
uniform sampler2D uTexture;    // Input texture (processed camera frame)

void main() {
    // Sample the texture at current fragment's texture coordinates
    vec4 textureColor = texture2D(uTexture, vTexCoord);

    // For edge detection visualization:
    // The input texture contains edge data in all RGBA channels
    // We can enhance the visualization or apply color effects

    // Option 1: Direct output (white edges on black background)
    gl_FragColor = textureColor;

    // Option 2: Enhance edge visibility with colored edges
    // float edgeIntensity = textureColor.r; // Use red channel for edge intensity
    // vec3 edgeColor = vec3(0.0, 1.0, 0.0); // Green edges
    // gl_FragColor = vec4(edgeColor * edgeIntensity, textureColor.a);

    // Option 3: Invert for better visibility (black edges on white background)
    // gl_FragColor = vec4(1.0 - textureColor.rgb, textureColor.a);

    // Option 4: Colorize based on edge intensity
    // float intensity = textureColor.r;
    // vec3 hotColor = vec3(1.0, 0.0, 0.0);     // Red for high intensity
    // vec3 coldColor = vec3(0.0, 0.0, 1.0);    // Blue for low intensity
    // vec3 finalColor = mix(coldColor, hotColor, intensity);
    // gl_FragColor = vec4(finalColor, textureColor.a);
}