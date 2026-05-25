package com.zeromail.worker.waitlist;

import java.net.URI;
import org.springframework.stereotype.Component;

/**
 * Plain-string template renderer for the waitlist invite mail. Intentionally not Thymeleaf-backed —
 * the v0 invite is a single CTA in one locale (VN), so the extra ceremony is not warranted. Migrate
 * to {@code SpringTemplateEngine} (see {@code ThymeleafDigestRenderer}) once locale fan-out is
 * needed.
 */
@Component
public class WaitlistInviteRenderer {

    private static final String SUBJECT = "Bạn vừa được duyệt vào Zero Mail";

    public String subject() {
        return SUBJECT;
    }

    public String renderHtml(URI loginUrl) {
        String safeUrl = loginUrl.toString();
        return """
                <!DOCTYPE html>
                <html lang="vi">
                  <head>
                    <meta charset="UTF-8">
                    <title>Chào mừng bạn đến với Zero Mail</title>
                  </head>
                  <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; \
                color: #1f2937; max-width: 560px; margin: 0 auto; padding: 32px 24px;">
                    <h1 style="font-size: 24px; margin-bottom: 16px;">Chào mừng bạn đến với Zero Mail!</h1>
                    <p>Bạn vừa được duyệt vào danh sách beta. Click nút dưới để bắt đầu sử dụng:</p>
                    <p style="margin: 32px 0;">
                      <a href="%s" style="background: #4f46e5; color: #ffffff; padding: 12px 24px; \
                text-decoration: none; border-radius: 6px; display: inline-block;">
                        Đăng nhập với Google
                      </a>
                    </p>
                    <p style="color: #6b7280; font-size: 14px;">
                      Nếu nút không hoạt động, copy link sau vào trình duyệt: <br>
                      <span style="font-family: monospace;">%s</span>
                    </p>
                    <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 32px 0;">
                    <p style="color: #9ca3af; font-size: 12px;">
                      Zero Mail — AI giúp bạn đạt inbox zero.
                    </p>
                  </body>
                </html>
                """
                .formatted(safeUrl, safeUrl);
    }

    public String renderText(URI loginUrl) {
        return """
                Chào mừng bạn đến với Zero Mail!

                Bạn vừa được duyệt vào danh sách beta. Truy cập link sau để bắt đầu:

                %s

                ---
                Zero Mail — AI giúp bạn đạt inbox zero.
                """
                .formatted(loginUrl.toString());
    }
}
