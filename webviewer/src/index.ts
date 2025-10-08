import { ImageDisplay } from './image_display';
import { FPSCounter } from './fps_counter';
import './styles.css';

/**
* Main application class for the Edge Detection Web Viewer
*/
class EdgeDetectionViewer {
private imageDisplay: ImageDisplay;
private fpsCounter: FPSCounter;
private isRunning: boolean = false;

// UI Elements
private canvas: HTMLCanvasElement;
private uploadInput: HTMLInputElement;
private statsDisplay: HTMLElement;
private controlsPanel: HTMLElement;

constructor() {
this.initializeDOM();
this.imageDisplay = new ImageDisplay(this.canvas);
this.fpsCounter = new FPSCounter();
this.setupEventListeners();
this.loadSampleImage();
}

/**
* Initialize DOM elements and create the UI structure
*/
private initializeDOM(): void {
// Create main container
const container = document.createElement('div');
container.className = 'app-container';

// Create header
const header = document.createElement('header');
header.innerHTML = `
<h1>Edge Detection Web Viewer</h1>
<p>TypeScript-powered image processing visualization</p>
`;

// Create main content area
const main = document.createElement('main');
main.className = 'main-content';

// Create canvas for image display
this.canvas = document.createElement('canvas');
this.canvas.id = 'image-canvas';
this.canvas.width = 640;
this.canvas.height = 480;

// Create controls panel
this.controlsPanel = document.createElement('div');
this.controlsPanel.className = 'controls-panel';
this.controlsPanel.innerHTML = `
<div class="control-group">
    <label for="file-upload">Load Image:</label>
    <input type="file" id="file-upload" accept="image/*" />
</div>
<div class="control-group">
    <button id="load-sample">Load Sample Edge Result</button>
    <button id="clear-canvas">Clear Canvas</button>
</div>
<div class="control-group">
    <label>Image Info:</label>
    <div id="image-info">No image loaded</div>
</div>
`;

// Create stats display
this.statsDisplay = document.createElement('div');
this.statsDisplay.className = 'stats-display';
this.statsDisplay.innerHTML = `
<h3>Performance Stats</h3>
<div id="fps-display">FPS: 0</div>
<div id="resolution-display">Resolution: N/A</div>
<div id="processing-time">Processing Time: N/A</div>
`;

// Assemble the UI
main.appendChild(this.canvas);
main.appendChild(this.controlsPanel);
main.appendChild(this.statsDisplay);

container.appendChild(header);
container.appendChild(main);

// Add to document body
document.body.appendChild(container);

// Get references to input elements
this.uploadInput = document.getElementById('file-upload') as HTMLInputElement;
}

/**
* Set up event listeners for user interactions
*/
private setupEventListeners(): void {
// File upload handler
this.uploadInput.addEventListener('change', (event) => {
const file = (event.target as HTMLInputElement).files?.[0];
if (file) {
this.loadImageFile(file);
}
});

// Sample image button
const sampleButton = document.getElementById('load-sample');
sampleButton?.addEventListener('click', () => {
this.loadSampleImage();
});

// Clear canvas button
const clearButton = document.getElementById('clear-canvas');
clearButton?.addEventListener('click', () => {
this.clearCanvas();
});

// Window resize handler
window.addEventListener('resize', () => {
this.handleResize();
});

// Keyboard shortcuts
document.addEventListener('keydown', (event) => {
this.handleKeyboard(event);
});
}

/**
* Load an image file and display it
*/
private loadImageFile(file: File): void {
const reader = new FileReader();
reader.onload = (event) => {
const img = new Image();
img.onload = () => {
this.displayImage(img);
this.updateImageInfo(img.width, img.height, file.name);
};
img.src = event.target?.result as string;
};
reader.readAsDataURL(file);
}

/**
* Load a sample edge detection result (base64 encoded)
*/
private loadSampleImage(): void {
// This would typically be a processed frame from the Android app
// For demo purposes, we'll create a sample edge detection pattern
this.imageDisplay.drawSampleEdgePattern();
this.updateImageInfo(640, 480, 'Sample Edge Detection');
this.startPerformanceMonitoring();
}

/**
* Display an image on the canvas
*/
private displayImage(image: HTMLImageElement): void {
this.imageDisplay.drawImage(image);
this.startPerformanceMonitoring();
}

/**
* Clear the canvas
*/
private clearCanvas(): void {
this.imageDisplay.clear();
this.stopPerformanceMonitoring();
this.updateImageInfo(0, 0, 'No image');
}

/**
* Update image information display
*/
private updateImageInfo(width: number, height: number, name: string): void {
const infoElement = document.getElementById('image-info');
if (infoElement) {
infoElement.innerHTML = `
<strong>${name}</strong><br>
Resolution: ${width} × ${height}<br>
Pixels: ${(width * height).toLocaleString()}
`;
}

const resolutionElement = document.getElementById('resolution-display');
if (resolutionElement) {
resolutionElement.textContent = `Resolution: ${width} × ${height}`;
}
}

/**
* Start performance monitoring
*/
private startPerformanceMonitoring(): void {
if (this.isRunning) return;

this.isRunning = true;
this.updatePerformanceStats();
}

/**
* Stop performance monitoring
*/
private stopPerformanceMonitoring(): void {
this.isRunning = false;
this.fpsCounter.reset();
}

/**
* Update performance statistics display
*/
private updatePerformanceStats(): void {
if (!this.isRunning) return;

this.fpsCounter.tick();

const fpsElement = document.getElementById('fps-display');
if (fpsElement) {
fpsElement.textContent = `FPS: ${this.fpsCounter.getFPS().toFixed(1)}`;
}

const processingElement = document.getElementById('processing-time');
if (processingElement) {
processingElement.textContent = `Processing Time: ${this.fpsCounter.getAverageFrameTime().toFixed(1)}ms`;
}

// Continue monitoring
requestAnimationFrame(() => this.updatePerformanceStats());
}

/**
* Handle window resize events
*/
private handleResize(): void {
// Adjust canvas size if needed
const containerWidth = this.canvas.parentElement?.clientWidth || 640;
const maxWidth = Math.min(containerWidth - 40, 800);

if (maxWidth !== this.canvas.width) {
const aspectRatio = this.canvas.height / this.canvas.width;
this.canvas.width = maxWidth;
this.canvas.height = maxWidth * aspectRatio;
this.imageDisplay.handleResize();
}
}

/**
* Handle keyboard shortcuts
*/
private handleKeyboard(event: KeyboardEvent): void {
switch (event.key) {
case 'c':
case 'C':
if (event.ctrlKey || event.metaKey) {
event.preventDefault();
this.clearCanvas();
}
break;
case 's':
case 'S':
if (event.ctrlKey || event.metaKey) {
event.preventDefault();
this.saveCurrentImage();
}
break;
case 'r':
case 'R':
if (event.ctrlKey || event.metaKey) {
event.preventDefault();
this.loadSampleImage();
}
break;
}
}

/**
* Save current canvas content as image
*/
private saveCurrentImage(): void {
const link = document.createElement('a');
link.download = `edge-detection-${Date.now()}.png`;
link.href = this.canvas.toDataURL();
link.click();
}

/**
* Process base64 image data from Android app
* This method would be called when receiving data from the Android application
*/
public processBase64Frame(base64Data: string, width: number, height: number): void {
const img = new Image();
img.onload = () => {
this.displayImage(img);
this.updateImageInfo(width, height, 'Android Frame');
};
img.src = `data:image/png;base64,${base64Data}`;
}

/**
* Update frame statistics from Android app
*/
public updateFrameStats(fps: number, processingTime: number): void {
const fpsElement = document.getElementById('fps-display');
if (fpsElement) {
fpsElement.textContent = `FPS: ${fps.toFixed(1)}`;
}

const processingElement = document.getElementById('processing-time');
if (processingElement) {
processingElement.textContent = `Processing Time: ${processingTime.toFixed(1)}ms`;
}
}
}

// Initialize the application when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
const app = new EdgeDetectionViewer();

// Expose app instance globally for Android communication
(window as any).edgeDetectionViewer = app;

console.log('Edge Detection Web Viewer initialized successfully');
});

// Export for module systems
export { EdgeDetectionViewer };