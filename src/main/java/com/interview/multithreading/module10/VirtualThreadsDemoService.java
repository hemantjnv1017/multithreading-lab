package com.interview.multithreading.module10;

import com.interview.multithreading.common.DemoResult;
import com.interview.multithreading.config.AsyncConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * MODULE 10 — Virtual threads (Project Loom / Java 21)
 *
 * Interview must-knows:
 * - Platform thread vs virtual thread
 * - Virtual threads are cheap → one-per-task is OK for blocking I/O
 * - Still need synchronization for shared mutable state
 * - Don't pool virtual threads; don't pin them with long synchronized/JNI
 * - Spring Boot 3.2+: spring.threads.virtual.enabled=true
 * - Structured concurrency (preview) — mention in interviews
 */
@Service
public class VirtualThreadsDemoService {

    private final Executor virtualExecutor;

    public VirtualThreadsDemoService(@Qualifier(AsyncConfig.VIRTUAL_EXECUTOR) Executor virtualExecutor) {
        this.virtualExecutor = virtualExecutor;
    }

    public DemoResult platformVsVirtual() throws Exception {
        int tasks = 5_000;

        long platformMs = runWithExecutor(Executors.newFixedThreadPool(200), tasks);
        long virtualMs = runWithExecutor(Executors.newVirtualThreadPerTaskExecutor(), tasks);

        Thread v = Thread.ofVirtual().name("demo-virtual").unstarted(() -> {});
        Thread p = Thread.ofPlatform().name("demo-platform").unstarted(() -> {});

        return DemoResult.of("10-virtual", "platform-vs-virtual",
                "Virtual threads shine for lots of blocking I/O. CPU-bound work still needs ~#cores platform threads.",
                DemoResult.map(
                        "tasks", tasks,
                        "platformPool200_ms", platformMs,
                        "virtualPerTask_ms", virtualMs,
                        "virtualThreadIsVirtual", v.isVirtual(),
                        "platformThreadIsVirtual", p.isVirtual()
                ));
    }

    public DemoResult millionThreadsSmoke() throws InterruptedException {
        // Creating 100k virtual threads is fine; 100k platform threads would crush the machine
        int n = 100_000;
        CountDownLatch latch = new CountDownLatch(n);
        long start = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                executor.submit(() -> {
                    try {
                        Thread.sleep(Duration.ofMillis(10));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        return DemoResult.of("10-virtual", "scale-smoke",
                "100k blocking virtual threads is normal. Same with platform threads ≈ death.",
                DemoResult.map("virtualThreads", n, "elapsedMs", System.currentTimeMillis() - start));
    }

    public DemoResult pinningWarning() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        Object monitor = new Object();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> f = executor.submit(() -> {
                synchronized (monitor) {
                    // Holding a synchronized monitor while blocking can PIN the carrier platform thread
                    logs.add("inside synchronized on virtual=" + Thread.currentThread().isVirtual());
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                logs.add("prefer ReentrantLock over synchronized for virtual-thread-heavy code");
            });
            f.get();
        }

        return DemoResult.of("10-virtual", "pinning",
                "Pinning: virtual thread blocked inside synchronized/native → carrier stuck. Prefer java.util.concurrent locks.",
                DemoResult.map("logs", logs));
    }

    public DemoResult springVirtualExecutor() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            int id = i;
            virtualExecutor.execute(() -> {
                logs.add("task-" + id + " virtual=" + Thread.currentThread().isVirtual()
                        + " name=" + Thread.currentThread().getName());
                latch.countDown();
            });
        }
        latch.await(2, TimeUnit.SECONDS);

        return DemoResult.of("10-virtual", "spring-executor",
                "Wire Executors.newVirtualThreadPerTaskExecutor() as a Spring bean for I/O-bound @Async work.",
                DemoResult.map("logs", logs, "bootProperty", "spring.threads.virtual.enabled=true"));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = new ArrayList<>();
        parts.add(platformVsVirtual());
        parts.add(millionThreadsSmoke());
        parts.add(pinningWarning());
        parts.add(springVirtualExecutor());
        return DemoResult.of("10-virtual", "all",
                "You've finished the lab. Re-run demos, read interview tips, then practice explaining each aloud.",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    private long runWithExecutor(ExecutorService executor, int tasks) throws Exception {
        long start = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(tasks);
        for (int i = 0; i < tasks; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(5); // simulate blocking I/O
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        return System.currentTimeMillis() - start;
    }
}
