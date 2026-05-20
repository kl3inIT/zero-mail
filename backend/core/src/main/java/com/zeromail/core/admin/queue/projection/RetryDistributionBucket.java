package com.zeromail.core.admin.queue.projection;

/**
 * Retry-distribution histogram bucket. {@code attemptsBucket} is {@code LEAST(attempts, 4)} so the
 * tail past 4 collapses into the "4+" bin per UI-SPEC §Queue.
 */
public record RetryDistributionBucket(int attemptsBucket, int rowCount) {}
