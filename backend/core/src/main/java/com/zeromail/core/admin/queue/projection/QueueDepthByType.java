package com.zeromail.core.admin.queue.projection;

/**
 * Per-job-type queue depth: count of PENDING vs PROCESSING rows. Aggregates only — never carries a
 * job id or a payload reference.
 */
public record QueueDepthByType(String jobType, int pendingCount, int processingCount) {}
