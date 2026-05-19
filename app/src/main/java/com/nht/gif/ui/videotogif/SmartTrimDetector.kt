package com.nht.gif.ui.videotogif

/**
 * Detects the optimal trim end point for a seamless loop by finding the frame in the
 * trimmed clip most visually similar to the first frame.
 */
interface SmartTrimDetector {

    /**
     * Analyses the configured clip and returns the timestamp (ms) of the best loop cut
     * point, or `null` if no frame exceeds the similarity threshold or if extraction fails.
     */
    suspend fun detect(): Long?
}
