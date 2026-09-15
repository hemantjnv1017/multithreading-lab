package com.interview.multithreading.module05;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MODULE 05 — java.util.concurrent utilities
 *
 * Interview must-knows:
 * - ConcurrentHashMap (segment/CAS; no ConcurrentModificationException on iteration)
 * - BlockingQueue: Array/Linked/Priority/Synchronous/Delay
 * - CountDownLatch — one-shot gate (threads wait until count → 0)
 * - CyclicBarrier — reusable barrier (parties await each other)
 * - Semaphore — permits (connection pools, rate limiting)
 * - Phaser — flexible multi-phase barrier (advanced)
 * - Exchanger — two threads swap data
 */
@Service
public class ConcurrentUtilsDemoService {

    public DemoResult concurrentHashMapDemo() throws InterruptedException {
        Map<String, Integer> unsafe = new HashMap<>();
        ConcurrentHashMap<String, Integer> safe = new ConcurrentHashMap<>();
        AtomicInteger unsafeErrors = new AtomicInteger();

        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch done = new CountDownLatch(writers);

        for (int w = 0; w < writers; w++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 1000; i++) {
                        safe.merge("hits", 1, Integer::sum);
                        try {
                            // HashMap is NOT thread-safe — may lose updates or throw
                            synchronized (unsafe) {
                                // even with sync for put, shows contrast with CHM atomic merge
                            }
                            unsafe.merge("hits", 1, Integer::sum); // intentionally unsafe
                        } catch (Exception e) {
                            unsafeErrors.incrementAndGet();
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        // Atomic compute helpers — interview favorite
        safe.compute("hits", (k, v) -> v == null ? 1 : v);
        int snapshot = safe.getOrDefault("hits", 0);

        return DemoResult.of("05-concurrent", "concurrent-hash-map",
                "Use ConcurrentHashMap + merge/compute/computeIfAbsent. Never share a plain HashMap across threads.",
                DemoResult.map(
                        "safeHits", snapshot,
                        "unsafeHits", unsafe.getOrDefault("hits", 0),
                        "expectedAround", writers * 1000,
                        "unsafeErrors", unsafeErrors.get()
                ));
    }

    public DemoResult blockingQueueProducerConsumer() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(3);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    queue.put(i); // blocks if full
                    logs.add("produced " + i + " size=" + queue.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "bq-producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 6; i++) {
                    Integer v = queue.take(); // blocks if empty
                    logs.add("consumed " + v);
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "bq-consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        return DemoResult.of("05-concurrent", "blocking-queue",
                "BlockingQueue is the modern replacement for wait/notify producer-consumer.",
                DemoResult.map("logs", logs));
    }

    public DemoResult countDownLatchDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        int workers = 3;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            int id = i;
            new Thread(() -> {
                try {
                    logs.add("worker-" + id + " ready");
                    ready.countDown();
                    start.await(); // wait for main to fire the gun
                    logs.add("worker-" + id + " running");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "latch-worker-" + id).start();
        }

        ready.await();
        logs.add("main: all ready → starting race");
        start.countDown();
        done.await();
        logs.add("main: all finished");

        return DemoResult.of("05-concurrent", "count-down-latch",
                "CountDownLatch is one-shot. Use for start gates / wait-for-N-services-up.",
                DemoResult.map("logs", logs));
    }

    public DemoResult cyclicBarrierDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        int parties = 3;
        CyclicBarrier barrier = new CyclicBarrier(parties,
                () -> logs.add("=== barrier action: all parties arrived, next phase ==="));

        CountDownLatch finished = new CountDownLatch(parties);
        for (int i = 0; i < parties; i++) {
            int id = i;
            new Thread(() -> {
                try {
                    logs.add("party-" + id + " phase1 work");
                    barrier.await();
                    logs.add("party-" + id + " phase2 work");
                    barrier.await(); // reusable!
                    logs.add("party-" + id + " done");
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }, "barrier-" + id).start();
        }
        finished.await();

        return DemoResult.of("05-concurrent", "cyclic-barrier",
                "CyclicBarrier is reusable; CountDownLatch is not. Barrier has an optional barrier-action.",
                DemoResult.map("logs", logs));
    }

    public DemoResult semaphoreDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        Semaphore dbConnections = new Semaphore(2); // max 2 concurrent "connections"
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch done = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    logs.add("client-" + id + " waiting for permit, available=" + dbConnections.availablePermits());
                    dbConnections.acquire();
                    try {
                        logs.add("client-" + id + " GOT connection");
                        Thread.sleep(80);
                    } finally {
                        dbConnections.release();
                        logs.add("client-" + id + " released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        return DemoResult.of("05-concurrent", "semaphore",
                "Semaphore = permits. Fair semaphore prevents starvation. Used for rate limiting & pool caps.",
                DemoResult.map("logs", logs));
    }

    public DemoResult phaserAndExchanger() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();

        // Phaser — dynamic registration
        Phaser phaser = new Phaser(1); // register self (main)
        for (int i = 0; i < 2; i++) {
            int id = i;
            phaser.register();
            new Thread(() -> {
                logs.add("phaser-worker-" + id + " phase " + phaser.getPhase());
                phaser.arriveAndAwaitAdvance();
                logs.add("phaser-worker-" + id + " advanced to phase " + phaser.getPhase());
                phaser.arriveAndDeregister();
            }, "phaser-" + id).start();
        }
        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndDeregister();
        logs.add("phaser terminated? " + phaser.isTerminated());

        // Exchanger
        Exchanger<String> exchanger = new Exchanger<>();
        CountDownLatch exDone = new CountDownLatch(2);
        new Thread(() -> {
            try {
                String got = exchanger.exchange("from-A");
                logs.add("A received: " + got);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exDone.countDown();
            }
        }, "ex-A").start();
        new Thread(() -> {
            try {
                String got = exchanger.exchange("from-B");
                logs.add("B received: " + got);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exDone.countDown();
            }
        }, "ex-B").start();
        exDone.await();

        return DemoResult.of("05-concurrent", "phaser-exchanger",
                "Phaser = multi-phase + dynamic parties. Exchanger = pairwise handoff between 2 threads.",
                DemoResult.map("logs", logs));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                concurrentHashMapDemo(),
                blockingQueueProducerConsumer(),
                countDownLatchDemo(),
                cyclicBarrierDemo(),
                semaphoreDemo(),
                phaserAndExchanger()
        );
        return DemoResult.of("05-concurrent", "all",
                "Complete Module 05 — next: /api/modules/06-completable",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }
}
