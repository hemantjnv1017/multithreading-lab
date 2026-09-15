package com.interview.multithreading.module04;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MODULE 04 — Executors & thread pools
 *
 * Interview must-knows:
 * - Executor / ExecutorService / ScheduledExecutorService hierarchy
 * - Fixed, cached, single, work-stealing, custom ThreadPoolExecutor
 * - Core vs max pool size, queue, RejectedExecutionHandler
 * - Future.get(), cancel(), isDone()
 * - shutdown() vs shutdownNow()
 * - Why Executors.newFixedThreadPool can OOM (unbounded queue) — prefer custom TPE
 */
@Service
public class ExecutorsDemoService {

    public DemoResult poolTypes() throws Exception {
        Map<String, Object> results = new LinkedHashMap<>();

        try (ExecutorService fixed = Executors.newFixedThreadPool(3)) {
            results.put("fixedPool", runAndCollect(fixed, 5, "fixed"));
        }
        try (ExecutorService cached = Executors.newCachedThreadPool()) {
            results.put("cachedPool", runAndCollect(cached, 5, "cached"));
        }
        try (ExecutorService single = Executors.newSingleThreadExecutor()) {
            results.put("singlePool", runAndCollect(single, 3, "single"));
        }
        try (ExecutorService workStealing = Executors.newWorkStealingPool()) {
            results.put("workStealingPool", runAndCollect(workStealing, 5, "steal"));
        }

        return DemoResult.of("04-executors", "pool-types",
                "Prefer explicit ThreadPoolExecutor over Executors.* factories in production (bounded queues).",
                results);
    }

    public DemoResult customThreadPoolExecutor() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        AtomicInteger rejected = new AtomicInteger();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,                          // corePoolSize
                4,                          // maximumPoolSize
                30, TimeUnit.SECONDS,       // keepAlive
                new ArrayBlockingQueue<>(2), // bounded queue — important!
                new ThreadFactory() {
                    private final AtomicInteger n = new AtomicInteger();
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "custom-pool-" + n.incrementAndGet());
                        t.setDaemon(false);
                        return t;
                    }
                },
                (r, ex) -> {
                    rejected.incrementAndGet();
                    logs.add("REJECTED task — poolSize=" + ex.getPoolSize()
                            + " queue=" + ex.getQueue().size());
                }
        );

        // Submit more than core+queue+max can take to trigger rejection
        for (int i = 0; i < 10; i++) {
            final int id = i;
            executor.execute(() -> {
                logs.add(Thread.currentThread().getName() + " running task-" + id);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        return DemoResult.of("04-executors", "custom-tpe",
                "Know the formula: new threads created when queue is full (until max). Then RejectedExecutionHandler.",
                DemoResult.map(
                        "rejectedCount", rejected.get(),
                        "corePoolSize", 2,
                        "maxPoolSize", 4,
                        "queueCapacity", 2,
                        "logs", logs
                ));
    }

    public DemoResult futureBasics() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> ok = pool.submit(() -> {
                Thread.sleep(100);
                return "success";
            });

            Future<String> slow = pool.submit(() -> {
                Thread.sleep(5000);
                return "too-slow";
            });

            logs.add("ok.isDone before get? " + ok.isDone());
            logs.add("ok result=" + ok.get());
            logs.add("ok.isDone after get? " + ok.isDone());

            boolean cancelled = slow.cancel(true);
            logs.add("slow cancelled=" + cancelled + " isCancelled=" + slow.isCancelled());

            try {
                slow.get(100, TimeUnit.MILLISECONDS);
            } catch (CancellationException e) {
                logs.add("get() on cancelled future → CancellationException");
            } catch (TimeoutException e) {
                logs.add("TimeoutException");
            }
        }

        return DemoResult.of("04-executors", "future",
                "Future: get() blocks, cancel(true) interrupts if running. Prefer CompletableFuture for composition.",
                DemoResult.map("logs", logs));
    }

    public DemoResult scheduledExecutor() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        CountDownLatch latch = new CountDownLatch(4);

        scheduler.schedule(() -> {
            logs.add("one-shot after 100ms");
            latch.countDown();
        }, 100, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> periodic = scheduler.scheduleAtFixedRate(() -> {
            logs.add("fixedRate tick @ " + System.currentTimeMillis());
            latch.countDown();
        }, 50, 80, TimeUnit.MILLISECONDS);

        latch.await(3, TimeUnit.SECONDS);
        periodic.cancel(false);
        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);

        return DemoResult.of("04-executors", "scheduled",
                "scheduleAtFixedRate vs scheduleWithFixedDelay — know the difference for interviews.",
                DemoResult.map("logs", logs));
    }

    public DemoResult invokeAllAny() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            List<Callable<String>> tasks = List.of(
                    () -> { Thread.sleep(150); return "A"; },
                    () -> { Thread.sleep(80); return "B"; },
                    () -> { Thread.sleep(200); return "C"; }
            );

            List<Future<String>> all = pool.invokeAll(tasks);
            List<String> allResults = new ArrayList<>();
            for (Future<String> f : all) {
                allResults.add(f.get());
            }

            String fastest = pool.invokeAny(List.of(
                    () -> { Thread.sleep(300); return "slow"; },
                    () -> { Thread.sleep(50); return "fast"; }
            ));

            return DemoResult.of("04-executors", "invoke-all-any",
                    "invokeAll waits for every task. invokeAny returns first successful result and cancels the rest.",
                    DemoResult.map("invokeAll", allResults, "invokeAny", fastest));
        }
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                poolTypes(),
                customThreadPoolExecutor(),
                futureBasics(),
                scheduledExecutor(),
                invokeAllAny()
        );
        return DemoResult.of("04-executors", "all",
                "Complete Module 04 — next: /api/modules/05-concurrent",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    private List<String> runAndCollect(ExecutorService pool, int tasks, String label)
            throws InterruptedException, ExecutionException {
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            int id = i;
            futures.add(pool.submit(() -> label + "-" + id + "@" + Thread.currentThread().getName()));
        }
        List<String> out = new ArrayList<>();
        for (Future<String> f : futures) {
            out.add(f.get());
        }
        return out;
    }
}
