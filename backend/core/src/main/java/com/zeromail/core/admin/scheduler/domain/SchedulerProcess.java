package com.zeromail.core.admin.scheduler.domain;

/**
 * Which runnable process hosts a scheduler. The admin console is served by {@code API}, but most
 * background schedulers run in the separate {@code WORKER} process — so the API cannot introspect
 * them via {@code ScheduledTaskHolder}. This is why the scheduler catalog is a curated descriptor
 * list rather than live in-process reflection, and why per-scheduler "Trigger now" needs a
 * cross-process control channel (deferred to scheduler phase 2).
 */
public enum SchedulerProcess {
    API,
    WORKER
}
