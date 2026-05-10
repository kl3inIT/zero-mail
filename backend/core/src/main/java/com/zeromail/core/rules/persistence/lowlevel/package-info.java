/**
 * Allow-listed package for native SQL inside the rules domain.
 *
 * <p>Native updates here are tenant-qualified and intentionally bypass Hibernate optimistic-lock
 * version increments for preview and enablement state transitions.
 */
package com.zeromail.core.rules.persistence.lowlevel;
