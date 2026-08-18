package com.burakpadr.decorating.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the pollers in {@code quoting.adapter.in.scheduler}: AnalysisPoller, QuoteExpiry,
 * ExpiryReminder, CallbackOverdue, PhotoPurge, DeletionReminder (§8).
 *
 * <p>There is no broker. Work is claimed from PostgreSQL with {@code FOR UPDATE SKIP LOCKED},
 * which is concurrency-safe across instances.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
