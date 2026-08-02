package com.example.notification.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.java21.instrument.binder.jdk.VirtualThreadMetrics;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Micrometer's {@code VirtualThreadMetrics} binder at startup.
 *
 * <p>The binder uses JDK Flight Recorder Event Streaming to observe
 * {@code jdk.VirtualThreadPinned} and {@code jdk.VirtualThreadSubmitFailed}
 * events and surfaces them as Micrometer meters scraped by Prometheus
 * alongside the rest of the workshop's metrics. This is the only
 * standardized way to observe {@code @RunOnVirtualThread} health.
 *
 * <h2>What you can see</h2>
 *
 * <ul>
 *   <li>{@code jvm.threads.virtual.pinned} — pinning duration histogram /
 *       summary. Pinning is when a virtual thread cannot unmount from its
 *       carrier (a {@code synchronized} block holding a monitor across a
 *       blocking call, or a native call). Long pinning starves the
 *       carrier-thread pool.</li>
 *   <li>{@code jvm.threads.virtual.submit.failed} — count of times the
 *       JVM failed to submit a virtual thread to the carrier pool.
 *       Indicates resource exhaustion.</li>
 * </ul>
 *
 * <p>Module 6 includes a brief callout pointing at these meters; the
 * future-modules notes propose a deeper follow-on covering JFR streaming,
 * JEP 509 (CPU-time profiling), and JEP 506 (scoped values).
 */
@ApplicationScoped
public class VirtualThreadMetricsRegistration {

    private static final Logger log = LoggerFactory.getLogger(
            VirtualThreadMetricsRegistration.class);

    void onStart(@Observes StartupEvent event, MeterRegistry registry) {
        new VirtualThreadMetrics().bindTo(registry);
        log.info("Virtual thread metrics binder registered "
                + "(jvm.threads.virtual.pinned, jvm.threads.virtual.submit.failed)");
    }
}
