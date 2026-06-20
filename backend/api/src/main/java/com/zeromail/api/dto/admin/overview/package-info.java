/**
 * Admin overview dashboard HTTP wire DTOs. This package carries aggregate-only operational metadata
 * for the landing dashboard; it must not expose prompt, completion, raw email body, or queue
 * payload fields.
 */
@org.springframework.modulith.NamedInterface("admin.overview")
package com.zeromail.api.dto.admin.overview;
