package com.zeromail.core.admin.scheduler.domain;

import java.util.List;

/**
 * Curated catalog of every background scheduler in the system, the operator-facing answer to "what
 * recurring jobs does this app run?". Most live in the WORKER process and cannot be reflected from
 * the API process, so this list is maintained by hand — keep it in sync when a {@code @Scheduled}
 * bean is added or removed. Read-only by design: a scheduler's cadence is infrastructure config,
 * not a runtime operator knob (production tools show + trigger recurring jobs, they do not let you
 * edit cron expressions from the dashboard).
 *
 * <p>Scheduler phase 2 will add live last-run/status (via a {@code scheduler_run} heartbeat table
 * each scheduler writes) and "Trigger now" for idempotent schedulers (via a worker control
 * channel). Both need worker-side plumbing the API process does not have today.
 */
public final class SchedulerCatalog {

    private SchedulerCatalog() {}

    public static final List<SchedulerDescriptor> ALL =
            List.of(
                    new SchedulerDescriptor(
                            "gmail-watch-refresh",
                            "Làm mới Gmail watch",
                            "Mỗi phút",
                            "0 * * * * *",
                            SchedulerProcess.WORKER,
                            "GMAIL",
                            "Gia hạn đăng ký Gmail users.watch trước khi hết hạn."),
                    new SchedulerDescriptor(
                            "gmail-history-processor",
                            "Xử lý Gmail history",
                            "Mỗi 1s (liên tục)",
                            null,
                            SchedulerProcess.WORKER,
                            "GMAIL",
                            "Tiêu thụ hàng đợi Pub/Sub history và phát sự kiện mail-observed."),
                    new SchedulerDescriptor(
                            "processing-job-worker",
                            "Worker hàng đợi job",
                            "Mỗi 2s (liên tục)",
                            null,
                            SchedulerProcess.WORKER,
                            "QUEUE",
                            "Poll processing_job (SKIP LOCKED) và chạy handler theo job_type."),
                    new SchedulerDescriptor(
                            "catalog-sync-drain",
                            "Drain catalog sync",
                            "Mỗi 2s (liên tục)",
                            null,
                            SchedulerProcess.WORKER,
                            "CATALOG",
                            "Lấy danh mục model từ provider cho job CATALOG_SYNC đang chờ."),
                    new SchedulerDescriptor(
                            "processing-job-reaper",
                            "Reaper job kẹt",
                            "Mỗi 60s",
                            null,
                            SchedulerProcess.WORKER,
                            "QUEUE_MAINTENANCE",
                            "Reset job PROCESSING quá hạn heartbeat về PENDING để chạy lại."),
                    new SchedulerDescriptor(
                            "processing-job-purge",
                            "Dọn job đã xong",
                            "03:00 hằng ngày (UTC)",
                            "0 0 3 * * *",
                            SchedulerProcess.WORKER,
                            "QUEUE_MAINTENANCE",
                            "Xoá job COMPLETED/FAILED/DEAD_LETTER cũ hơn 90 ngày."),
                    new SchedulerDescriptor(
                            "digest-dispatch",
                            "Gửi digest",
                            "Mỗi phút",
                            "0 * * * * *",
                            SchedulerProcess.WORKER,
                            "DIGEST",
                            "Tìm tenant tới giờ digest theo timezone và xếp gửi."),
                    new SchedulerDescriptor(
                            "digest-pending-reaper",
                            "Reaper digest treo",
                            "Mỗi 5m",
                            null,
                            SchedulerProcess.WORKER,
                            "DIGEST",
                            "Phục hồi bản ghi gửi digest bị treo."),
                    new SchedulerDescriptor(
                            "triage-event-retry",
                            "Retry triage",
                            "Mỗi 2m",
                            null,
                            SchedulerProcess.WORKER,
                            "TRIAGE",
                            "Thử lại các sự kiện triage thất bại (Spring Modulith outbox)."),
                    new SchedulerDescriptor(
                            "triage-pending-reaper",
                            "Reaper triage treo",
                            "Mỗi 5m",
                            null,
                            SchedulerProcess.WORKER,
                            "TRIAGE",
                            "Phục hồi triage đang treo ở trạng thái pending."),
                    new SchedulerDescriptor(
                            "triage-event-cleanup",
                            "Dọn sự kiện triage",
                            "03:00 hằng ngày",
                            "0 0 3 * * *",
                            SchedulerProcess.WORKER,
                            "TRIAGE",
                            "Xoá sự kiện triage đã xử lý xong."),
                    new SchedulerDescriptor(
                            "triage-audit-purge",
                            "Dọn audit triage",
                            "04:00 hằng ngày",
                            "0 0 4 * * *",
                            SchedulerProcess.WORKER,
                            "TRIAGE",
                            "Xoá bản ghi audit triage quá hạn lưu trữ."),
                    new SchedulerDescriptor(
                            "credit-reserve-watchdog",
                            "Watchdog giữ tín dụng",
                            "Mỗi 60s",
                            null,
                            SchedulerProcess.WORKER,
                            "BILLING",
                            "Giải phóng các khoản tín dụng giữ chỗ quá hạn chưa quyết toán."),
                    new SchedulerDescriptor(
                            "llm-drift-detection",
                            "Phát hiện trôi model LLM",
                            "06:00 hằng ngày",
                            "0 0 6 * * *",
                            SchedulerProcess.WORKER,
                            "LLM",
                            "So sánh danh mục model để phát hiện thay đổi/biến mất."),
                    new SchedulerDescriptor(
                            "admin-read-event-purge",
                            "Dọn admin read event",
                            "03:30 hằng ngày",
                            "0 30 3 * * *",
                            SchedulerProcess.WORKER,
                            "ADMIN_MAINTENANCE",
                            "Xoá log truy cập đọc của admin quá hạn lưu trữ."),
                    new SchedulerDescriptor(
                            "admin-audit-chain-verify",
                            "Kiểm tra chuỗi audit admin",
                            "02:00 hằng ngày",
                            "0 0 2 * * *",
                            SchedulerProcess.WORKER,
                            "ADMIN_MAINTENANCE",
                            "Xác minh chuỗi HMAC của admin_audit_event chưa bị giả mạo."),
                    new SchedulerDescriptor(
                            "healthcheck",
                            "Healthcheck worker",
                            "Mỗi 60s",
                            null,
                            SchedulerProcess.WORKER,
                            "HEALTH",
                            "Nhịp tim nội bộ của tiến trình worker."),
                    new SchedulerDescriptor(
                            "assistant-pending-action-reconciler",
                            "Đối soát hành động chat treo",
                            "Mỗi 5m",
                            null,
                            SchedulerProcess.API,
                            "CHAT",
                            "Đối soát các hành động gửi mail của chat assistant còn treo."));
}
