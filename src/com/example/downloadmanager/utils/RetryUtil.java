package com.example.downloadmanager.utils;

import java.util.function.Supplier;

/**
 * Utility class for implementing retry operations with exponential backoff
 */
public class RetryUtil {

    // Base delay in milliseconds
    private static final long BASE_DELAY = 1000;

    // Maximum delay in milliseconds (1 minute)
    private static final long MAX_DELAY = 60000;

    /**
     * Retry an operation with exponential backoff
     *
     * @param <T> The return type of the operation
     * @param action The operation to retry
     * @param maxRetries Maximum number of retries
     * @return The result of the operation or null if all retries failed
     */
    public <T> T retry(Supplier<T> action, int maxRetries) {
        int attempts = 0;

        while (attempts <= maxRetries) {
            try {
                // Try the action
                return action.get();

            } catch (Exception e) {
                attempts++;

                // If we've exhausted all retries, give up
                if (attempts > maxRetries) {
                    LoggerUtil.logError("All retry attempts failed", e);
                    return null;
                }

                // Calculate backoff delay with exponential increase and jitter
                long delay = calculateBackoff(attempts);

                LoggerUtil.logInfo("Retry attempt " + attempts + " failed. Retrying in " + delay + "ms");

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Calculate backoff time with exponential increase and jitter
     *
     * @param attempt The attempt number (1-based)
     * @return Backoff time in milliseconds
     */
    private long calculateBackoff(int attempt) {
        // Exponential backoff: 2^attempt * BASE_DELAY
        long exponentialDelay = (1L << attempt) * BASE_DELAY;

        // Add jitter: ±20% random variation to prevent thundering herd problem
        double jitterFactor = 0.8 + Math.random() * 0.4; // 0.8 to 1.2
        long delay = (long) (exponentialDelay * jitterFactor);

        // Cap at max delay
        return Math.min(delay, MAX_DELAY);
    }

    /**
     * Retry an operation without return value
     *
     * @param action The operation to retry
     * @param maxRetries Maximum number of retries
     * @return true if successful, false if all retries failed
     */
    public boolean retryVoid(Runnable action, int maxRetries) {
        return retry(() -> {
            action.run();
            return true;
        }, maxRetries) != null;
    }
}