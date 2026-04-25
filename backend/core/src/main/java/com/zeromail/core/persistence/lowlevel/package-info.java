/**
 * Allow-listed package for native SQL / raw JDBC. ArchUnit rule excludes this package
 * from the "no native SQL" rule. Every class in here MUST manually apply tenant_id = ?.
 */
package com.zeromail.core.persistence.lowlevel;
