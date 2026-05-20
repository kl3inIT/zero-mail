/**
 * Admin spend-dashboard projections + aggregate read services. Aggregates over {@code
 * llm_call_audit} using SUM/COUNT ONLY — never selects {@code prompt}, {@code completion}, {@code
 * request_body}, or any other content-shaped column. AdminPathBodyBanTest gates the projection
 * package against body-named fields; AdminSpendPromptAccessorBanTest gates the services against
 * content-shaped accessor calls.
 *
 * <p>Privacy contract (OPS-SPEND-02 + ARCH-11): no per-prompt drill-down endpoint exists.
 * K-anonymity (k ≥ 5) collapses small buckets into rollup rows.
 */
package com.zeromail.core.admin.spend;
