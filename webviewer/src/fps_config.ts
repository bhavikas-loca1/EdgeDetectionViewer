/**
 * FPS Counter utility for performance monitoring
 * Tracks frame rate and processing time statistics
 */
export class FPSCounter {
    private frameCount: number = 0;
    private lastTime: number = 0;
    private frameStartTime: number = 0;
    private fps: number = 0;
    private averageFrameTime: number = 0;
    private frameTimes: number[] = [];
    private readonly maxFrameTimesSamples: number = 60; // Keep last 60 frame times

    // Performance statistics
    private totalFrames: number = 0;
    private startTime: number = 0;
    private minFps: number = Infinity;
    private maxFps: number = 0;
    private minFrameTime: number = Infinity;
    private maxFrameTime: number = 0;

    constructor() {
        this.reset();
    }

    /**
     * Reset all performance counters
     */
    public reset(): void {
        this.frameCount = 0;
        this.lastTime = performance.now();
        this.frameStartTime = this.lastTime;
        this.startTime = this.lastTime;
        this.fps = 0;
        this.averageFrameTime = 0;
        this.frameTimes = [];
        this.totalFrames = 0;
        this.minFps = Infinity;
        this.maxFps = 0;
        this.minFrameTime = Infinity;
        this.maxFrameTime = 0;
    }

    /**
     * Call this method at the beginning of each frame
     */
    public startFrame(): void {
        this.frameStartTime = performance.now();
    }

    /**
     * Call this method at the end of each frame to update statistics
     */
    public tick(): void {
        const currentTime = performance.now();

        // Calculate frame time
        const frameTime = currentTime - this.frameStartTime;
        if (frameTime > 0) {
            this.frameTimes.push(frameTime);

            // Keep only the most recent frame times
            if (this.frameTimes.length > this.maxFrameTimesSamples) {
                this.frameTimes.shift();
            }

            // Update min/max frame time
            this.minFrameTime = Math.min(this.minFrameTime, frameTime);
            this.maxFrameTime = Math.max(this.maxFrameTime, frameTime);
        }

        this.frameCount++;
        this.totalFrames++;

        // Calculate FPS every second or so
        const deltaTime = currentTime - this.lastTime;
        if (deltaTime >= 1000) { // Update every 1000ms (1 second)
            this.fps = (this.frameCount * 1000) / deltaTime;
            this.frameCount = 0;
            this.lastTime = currentTime;

            // Update min/max FPS
            if (this.fps > 0) {
                this.minFps = Math.min(this.minFps, this.fps);
                this.maxFps = Math.max(this.maxFps, this.fps);
            }
        }

        // Calculate average frame time
        if (this.frameTimes.length > 0) {
            const sum = this.frameTimes.reduce((a, b) => a + b, 0);
            this.averageFrameTime = sum / this.frameTimes.length;
        }

        // Start next frame timing
        this.frameStartTime = currentTime;
    }

    /**
     * Get current FPS
     * @returns Current frames per second
     */
    public getFPS(): number {
        return this.fps;
    }

    /**
     * Get average frame time in milliseconds
     * @returns Average frame processing time
     */
    public getAverageFrameTime(): number {
        return this.averageFrameTime;
    }

    /**
     * Get minimum FPS recorded since last reset
     * @returns Minimum FPS value
     */
    public getMinFPS(): number {
        return this.minFps === Infinity ? 0 : this.minFps;
    }

    /**
     * Get maximum FPS recorded since last reset
     * @returns Maximum FPS value
     */
    public getMaxFPS(): number {
        return this.maxFps;
    }

    /**
     * Get minimum frame time recorded since last reset
     * @returns Minimum frame time in milliseconds
     */
    public getMinFrameTime(): number {
        return this.minFrameTime === Infinity ? 0 : this.minFrameTime;
    }

    /**
     * Get maximum frame time recorded since last reset
     * @returns Maximum frame time in milliseconds
     */
    public getMaxFrameTime(): number {
        return this.maxFrameTime;
    }

    /**
     * Get total number of frames processed
     * @returns Total frame count
     */
    public getTotalFrames(): number {
        return this.totalFrames;
    }

    /**
     * Get overall average FPS since start
     * @returns Overall average FPS
     */
    public getOverallAverageFPS(): number {
        const currentTime = performance.now();
        const totalTime = currentTime - this.startTime;
        if (totalTime > 0 && this.totalFrames > 0) {
            return (this.totalFrames * 1000) / totalTime;
        }
        return 0;
    }

    /**
     * Get frame time percentiles for performance analysis
     * @returns Object containing various percentile values
     */
    public getFrameTimePercentiles(): {
        p50: number; // Median
        p90: number; // 90th percentile
        p95: number; // 95th percentile
        p99: number; // 99th percentile
    } {
        if (this.frameTimes.length === 0) {
            return { p50: 0, p90: 0, p95: 0, p99: 0 };
        }

        const sorted = [...this.frameTimes].sort((a, b) => a - b);
        const length = sorted.length;

        return {
            p50: sorted[Math.floor(length * 0.50)] || 0,
            p90: sorted[Math.floor(length * 0.90)] || 0,
            p95: sorted[Math.floor(length * 0.95)] || 0,
            p99: sorted[Math.floor(length * 0.99)] || 0
        };
    }

    /**
     * Check if performance is within acceptable ranges
     * @param targetFPS Target FPS (default: 60)
     * @param maxFrameTime Maximum acceptable frame time in ms (default: 33.33ms for 30 FPS)
     * @returns Performance status object
     */
    public getPerformanceStatus(targetFPS: number = 60, maxFrameTime: number = 33.33): {
        isGoodFPS: boolean;
        isGoodFrameTime: boolean;
        status: 'excellent' | 'good' | 'poor' | 'critical';
        warnings: string[];
    } {
        const warnings: string[] = [];
        const isGoodFPS = this.fps >= targetFPS * 0.8; // Within 80% of target
        const isGoodFrameTime = this.averageFrameTime <= maxFrameTime;

        if (this.fps < targetFPS * 0.5) {
            warnings.push(`Low FPS: ${this.fps.toFixed(1)} (target: ${targetFPS})`);
        }

        if (this.averageFrameTime > maxFrameTime * 2) {
            warnings.push(`High frame time: ${this.averageFrameTime.toFixed(1)}ms (max: ${maxFrameTime}ms)`);
        }

        const percentiles = this.getFrameTimePercentiles();
        if (percentiles.p95 > maxFrameTime * 3) {
            warnings.push(`95th percentile frame time too high: ${percentiles.p95.toFixed(1)}ms`);
        }

        let status: 'excellent' | 'good' | 'poor' | 'critical';
        if (isGoodFPS && isGoodFrameTime && warnings.length === 0) {
            status = 'excellent';
        } else if (isGoodFPS && isGoodFrameTime) {
            status = 'good';
        } else if (this.fps > targetFPS * 0.3) {
            status = 'poor';
        } else {
            status = 'critical';
        }

        return {
            isGoodFPS,
            isGoodFrameTime,
            status,
            warnings
        };
    }

    /**
     * Get comprehensive performance report
     * @returns Detailed performance metrics
     */
    public getPerformanceReport(): {
        currentFPS: number;
        averageFrameTime: number;
        minFPS: number;
        maxFPS: number;
        minFrameTime: number;
        maxFrameTime: number;
        totalFrames: number;
        overallAverageFPS: number;
        percentiles: ReturnType<FPSCounter['getFrameTimePercentiles']>;
        performanceStatus: ReturnType<FPSCounter['getPerformanceStatus']>;
    } {
        return {
            currentFPS: this.getFPS(),
            averageFrameTime: this.getAverageFrameTime(),
            minFPS: this.getMinFPS(),
            maxFPS: this.getMaxFPS(),
            minFrameTime: this.getMinFrameTime(),
            maxFrameTime: this.getMaxFrameTime(),
            totalFrames: this.getTotalFrames(),
            overallAverageFPS: this.getOverallAverageFPS(),
            percentiles: this.getFrameTimePercentiles(),
            performanceStatus: this.getPerformanceStatus()
        };
    }

    /**
     * Format performance data as human-readable string
     * @returns Formatted performance summary
     */
    public toString(): string {
        const report = this.getPerformanceReport();
        return [
            `FPS: ${report.currentFPS.toFixed(1)} (min: ${report.minFPS.toFixed(1)}, max: ${report.maxFPS.toFixed(1)})`,
            `Frame Time: ${report.averageFrameTime.toFixed(1)}ms (min: ${report.minFrameTime.toFixed(1)}, max: ${report.maxFrameTime.toFixed(1)})`,
            `Total Frames: ${report.totalFrames}`,
            `Overall Avg FPS: ${report.overallAverageFPS.toFixed(1)}`,
            `Status: ${report.performanceStatus.status}`
        ].join('\n');
    }
}