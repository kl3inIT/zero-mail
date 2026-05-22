/**
 * Admin spend-dashboard HTTP wire DTOs. AdminPathBodyBanTest gates this package against any field
 * name matching the forbidden body/payload/prompt/completion/content regex.
 * AdminSpendPromptAccessorBanTest forbids spend code from calling
 * get{Prompt,Completion,Request,Response,Body,Content}* accessors. The DTO contract + the SQL
 * aggregation layer + the table schema (no prompt/completion columns) form the privacy gate
 * (OPS-SPEND-02 + ARCH-11 + T-08-50).
 */
@org.springframework.modulith.NamedInterface("admin.spend")
package com.zeromail.api.dto.admin.spend;
