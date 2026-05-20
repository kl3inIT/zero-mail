/**
 * Admin queue-health dashboard projections + read services. Aggregates over {@code processing_job}
 * only — never selects {@code payload_json}. AdminPathBodyBanTest gates the projection package.
 */
package com.zeromail.core.admin.queue;
