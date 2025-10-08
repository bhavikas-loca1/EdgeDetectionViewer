/**
 * Image display utility class for canvas operations
 * Handles drawing images, edge detection patterns, and canvas manipulations
 */
export class ImageDisplay {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private currentImage: HTMLImageElement | null = null;

    constructor(canvas: HTMLCanvasElement) {
        this.canvas = canvas;
        const context = canvas.getContext('2d');
        if (!context) {
            throw new Error('Unable to get 2D rendering context');
        }
        this.ctx = context;
        this.setupCanvas();
    }

    /**
     * Initialize canvas settings for optimal rendering
     */
    private setupCanvas(): void {
        // Enable high-DPI rendering
        const devicePixelRatio = window.devicePixelRatio || 1;
        const rect = this.canvas.getBoundingClientRect();

        this.canvas.width = rect.width * devicePixelRatio;
        this.canvas.height = rect.height * devicePixelRatio;

        this.ctx.scale(devicePixelRatio, devicePixelRatio);

        // Set canvas style dimensions
        this.canvas.style.width = rect.width + 'px';
        this.canvas.style.height = rect.height + 'px';

        // Set rendering options for crisp edges
        this.ctx.imageSmoothingEnabled = false;
        this.ctx.fillStyle = '#000000';
        this.ctx.strokeStyle = '#ffffff';
    }

    /**
     * Draw an image on the canvas with proper scaling
     * @param image Image element to draw
     */
    public drawImage(image: HTMLImageElement): void {
        this.currentImage = image;
        this.clear();

        // Calculate scaling to fit canvas while maintaining aspect ratio
        const canvasAspect = this.canvas.width / this.canvas.height;
        const imageAspect = image.width / image.height;

        let drawWidth = this.canvas.width;
        let drawHeight = this.canvas.height;
        let offsetX = 0;
        let offsetY = 0;

        if (imageAspect > canvasAspect) {
            // Image is wider than canvas
            drawHeight = this.canvas.width / imageAspect;
            offsetY = (this.canvas.height - drawHeight) / 2;
        } else {
            // Image is taller than canvas
            drawWidth = this.canvas.height * imageAspect;
            offsetX = (this.canvas.width - drawWidth) / 2;
        }

        this.ctx.drawImage(image, offsetX, offsetY, drawWidth, drawHeight);
    }

    /**
     * Draw a sample edge detection pattern for demonstration
     */
    public drawSampleEdgePattern(): void {
        this.clear();

        const width = this.canvas.width;
        const height = this.canvas.height;

        // Create sample edge detection pattern
        const imageData = this.ctx.createImageData(width, height);
        const data = imageData.data;

        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                const index = (y * width + x) * 4;

                // Generate edge-like pattern
                let intensity = 0;

                // Vertical edges
                if (Math.abs(Math.sin(x / 20)) > 0.8) intensity += 100;

                // Horizontal edges
                if (Math.abs(Math.sin(y / 30)) > 0.7) intensity += 100;

                // Circular pattern
                const centerX = width / 2;
                const centerY = height / 2;
                const distance = Math.sqrt((x - centerX) ** 2 + (y - centerY) ** 2);
                if (Math.abs(distance - 100) < 5 || Math.abs(distance - 200) < 5) {
                    intensity += 150;
                }

                // Random noise for realistic effect
                intensity += Math.random() * 30;

                // Clamp intensity
                intensity = Math.min(255, Math.max(0, intensity));

                // Set RGBA values (white edges on black background)
                data[index] = intensity;     // Red
                data[index + 1] = intensity; // Green
                data[index + 2] = intensity; // Blue
                data[index + 3] = 255;       // Alpha
            }
        }

        this.ctx.putImageData(imageData, 0, 0);

        // Add some text overlay
        this.ctx.fillStyle = 'rgba(255, 255, 0, 0.8)';
        this.ctx.font = '16px Arial';
        this.ctx.fillText('Sample Edge Detection Result', 10, 30);
        this.ctx.fillText('Generated Pattern - Real results from Android App', 10, 50);
    }

    /**
     * Draw image data from byte array (RGBA format)
     * @param data Uint8Array containing RGBA pixel data
     * @param width Image width
     * @param height Image height
     */
    public drawImageData(data: Uint8Array, width: number, height: number): void {
        this.clear();

        // Create ImageData object
        const imageData = new ImageData(width, height);
        imageData.data.set(data);

        // Calculate scaling and position
        const scaleX = this.canvas.width / width;
        const scaleY = this.canvas.height / height;
        const scale = Math.min(scaleX, scaleY);

        const scaledWidth = width * scale;
        const scaledHeight = height * scale;
        const offsetX = (this.canvas.width - scaledWidth) / 2;
        const offsetY = (this.canvas.height - scaledHeight) / 2;

        // Create temporary canvas for scaling
        const tempCanvas = document.createElement('canvas');
        tempCanvas.width = width;
        tempCanvas.height = height;
        const tempCtx = tempCanvas.getContext('2d')!;
        tempCtx.putImageData(imageData, 0, 0);

        // Draw scaled image
        this.ctx.drawImage(tempCanvas, offsetX, offsetY, scaledWidth, scaledHeight);
    }

    /**
     * Clear the canvas
     */
    public clear(): void {
        this.ctx.fillStyle = '#000000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }

    /**
     * Handle canvas resize
     */
    public handleResize(): void {
        this.setupCanvas();
        if (this.currentImage) {
            this.drawImage(this.currentImage);
        }
    }

    /**
     * Get canvas as data URL
     * @param format Image format (default: 'image/png')
     * @param quality Image quality for JPEG (0-1)
     * @returns Data URL string
     */
    public getDataURL(format: string = 'image/png', quality?: number): string {
        return this.canvas.toDataURL(format, quality);
    }

    /**
     * Add text overlay to current canvas content
     * @param text Text to display
     * @param x X coordinate
     * @param y Y coordinate
     * @param style Text style options
     */
    public addTextOverlay(
        text: string,
        x: number,
        y: number,
        style: {
            font?: string;
            fillStyle?: string;
            strokeStyle?: string;
            strokeWidth?: number;
        } = {}
    ): void {
        const {
            font = '14px Arial',
            fillStyle = '#ffffff',
            strokeStyle = '#000000',
            strokeWidth = 2
        } = style;

        this.ctx.font = font;
        this.ctx.fillStyle = fillStyle;
        this.ctx.strokeStyle = strokeStyle;
        this.ctx.lineWidth = strokeWidth;

        // Draw text with outline for better visibility
        this.ctx.strokeText(text, x, y);
        this.ctx.fillText(text, x, y);
    }

    /**
     * Apply a simple edge enhancement filter to current canvas
     * This is a client-side demonstration filter
     */
    public applyEdgeEnhancement(): void {
        const imageData = this.ctx.getImageData(0, 0, this.canvas.width, this.canvas.height);
        const data = imageData.data;

        // Simple edge enhancement kernel
        const kernel = [
            0, -1, 0,
            -1, 5, -1,
            0, -1, 0
        ];

        const newImageData = this.ctx.createImageData(this.canvas.width, this.canvas.height);
        const newData = newImageData.data;

        for (let y = 1; y < this.canvas.height - 1; y++) {
            for (let x = 1; x < this.canvas.width - 1; x++) {
                const idx = (y * this.canvas.width + x) * 4;

                for (let c = 0; c < 3; c++) { // RGB channels
                    let sum = 0;
                    for (let ky = -1; ky <= 1; ky++) {
                        for (let kx = -1; kx <= 1; kx++) {
                            const kernelIdx = (ky + 1) * 3 + (kx + 1);
                            const pixelIdx = ((y + ky) * this.canvas.width + (x + kx)) * 4 + c;
                            sum += data[pixelIdx] * kernel[kernelIdx];
                        }
                    }
                    newData[idx + c] = Math.min(255, Math.max(0, sum));
                }
                newData[idx + 3] = data[idx + 3]; // Alpha channel
            }
        }

        this.ctx.putImageData(newImageData, 0, 0);
    }
}