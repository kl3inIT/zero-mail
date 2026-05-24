/**
 * Email-sending adapter for the worker — wraps the Resend SDK. {@code ResendEmailGateway} is the
 * single allowed entry point for {@code com.resend.*} imports (enforced by {@code
 * ResendBoundaryArchTest}). Marked as a Spring Modulith named interface so other worker packages
 * (e.g. {@code worker.waitlist}, {@code worker.notification}) can inject the gateway without IDE
 * inspections flagging it as internal access.
 */
@org.springframework.modulith.NamedInterface("email-gateway")
package com.zeromail.worker.notification.email;
