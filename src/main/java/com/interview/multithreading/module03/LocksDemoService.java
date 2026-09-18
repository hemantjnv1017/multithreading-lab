package com.interview.multithreading.module03;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * MODULE 03 — Explicit locks (~3 YOE)
 *
 * Must-knows:
 * - ReentrantLock vs synchronized (tryLock, unlock in finally)
 * - lock() vs tryLock() — lockVsTryLock()
 * - ReadWriteLock — many readers OR one writer
 * - Condition — wait/signal with Lock (basic idea)
 * - StampedLock — name-level awareness (optimistic read)
 */
@Service
public class LocksDemoService {

    public DemoResult reentrantLockFeatures() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        ReentrantLock lock = new ReentrantLock(true); // fair lock — FIFO, avoids starvation

        Runnable worker = () -> {
            String name = Thread.currentThread().getName();
            try {
                if (lock.tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        logs.add(name + " acquired lock, holdCount=" + lock.getHoldCount());
                        // reentrancy: same thread can lock again
                        lock.lock();
                        try {
                            logs.add(name + " re-entered, holdCount=" + lock.getHoldCount());
                        } finally {
                            lock.unlock();
                        }
                    } finally {
                        lock.unlock();
                        logs.add(name + " released, holdCount=" + lock.getHoldCount());
                    }
                } else {
                    logs.add(name + " timed out waiting for lock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logs.add(name + " interrupted");
            }
        };

        Thread t1 = new Thread(worker, "L1");
        Thread t2 = new Thread(worker, "L2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        return DemoResult.of("03-locks", "reentrant-lock",
                "ReentrantLock: tryLock, lockInterruptibly, fairness. Always unlock() in finally.",
                DemoResult.map("fair", lock.isFair(), "logs", logs));
    }

    /**
     * Interview favorite: lock() vs tryLock() on ReentrantLock / Lock API.
     */
    public DemoResult lockVsTryLock() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        ReentrantLock lock = new ReentrantLock();

        // Hold the lock so the other thread must wait / fail
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                logs.add("HOLDER: got lock via lock(), sleeping 300ms");
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
                logs.add("HOLDER: unlocked");
            }
        }, "holder");

        Thread tryLockThread = new Thread(() -> {
            try {
                Thread.sleep(50); // ensure holder has the lock
                // tryLock() — non-blocking: immediate true/false
                boolean got = lock.tryLock();
                logs.add("TRYLOCK: tryLock() immediate → " + got + " (false expected while holder sleeps)");
                if (got) {
                    try {
                        logs.add("TRYLOCK: unexpected acquire");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    logs.add("TRYLOCK: skipped work / fallback path (no waiting)");
                }

                // tryLock(timeout) — wait at most N, then give up
                boolean gotTimed = lock.tryLock(500, TimeUnit.MILLISECONDS);
                logs.add("TRYLOCK: tryLock(500ms) → " + gotTimed);
                if (gotTimed) {
                    try {
                        logs.add("TRYLOCK: acquired after wait (holder finished)");
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logs.add("TRYLOCK: interrupted");
            }
        }, "trylock-worker");

        Thread lockThread = new Thread(() -> {
            try {
                Thread.sleep(50);
                logs.add("LOCK: calling lock() — will BLOCK until holder unlocks (no timeout)");
                lock.lock(); // blocks here
                try {
                    logs.add("LOCK: finally acquired after waiting");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lock-worker");

        holder.start();
        tryLockThread.start();
        lockThread.start();
        holder.join();
        tryLockThread.join();
        lockThread.join();

        Map<String, String> comparison = new LinkedHashMap<>();
        comparison.put("lock()", "Lock milne tak block — wait forever");
        comparison.put("tryLock()", "Turant true/false — fail pe block nahi");
        comparison.put("tryLock(timeout)", "Max N time wait, phir false");
        comparison.put("synced note", "synchronized mein tryLock nahi hota — isliye kabhi ReentrantLock");

        List<String> interviewQandA = List.of(
                "Q: lock vs tryLock? → lock blocks; tryLock boolean + fail-fast.",
                "Q: tryLock fail? → false → skip/fallback/retry later.",
                "Q: Kab tryLock? → Timeout, deadlock avoid, responsive path.",
                "Q: unlock kab? → Sirf acquire success pe, finally mein.",
                "Q: Common bug? → tryLock false pe bhi unlock()."
        );

        return DemoResult.of("03-locks", "lock-vs-trylock",
                "lock() waits forever; tryLock() fails fast; tryLock(timeout) bounds wait. unlock only if acquired.",
                DemoResult.map(
                        "comparison", comparison,
                        "interviewQandA", interviewQandA,
                        "logs", logs
                ));
    }

    public DemoResult readWriteLockDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        ReadWriteLock rw = new ReentrantReadWriteLock();
        Lock readLock = rw.readLock();
        Lock writeLock = rw.writeLock();
        int[] shared = {0};

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            int id = i;
            futures.add(pool.submit(() -> {
                readLock.lock();
                try {
                    logs.add("reader-" + id + " saw value=" + shared[0]);
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    readLock.unlock();
                }
            }));
        }

        futures.add(pool.submit(() -> {
            writeLock.lock();
            try {
                shared[0] = 99;
                logs.add("writer set value=99");
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                writeLock.unlock();
            }
        }));

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();

        return DemoResult.of("03-locks", "read-write-lock",
                "ReadWriteLock: concurrent readers OK; writers exclusive. Great for read-heavy caches.",
                DemoResult.map("finalValue", shared[0], "logs", logs));
    }

    public DemoResult conditionDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        ReentrantLock lock = new ReentrantLock();
        Condition notFull = lock.newCondition();
        Condition notEmpty = lock.newCondition();
        int[] buffer = new int[1];
        boolean[] hasItem = {false};

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                lock.lock();
                try {
                    while (hasItem[0]) {
                        logs.add("producer awaiting notFull");
                        notFull.await();
                    }
                    buffer[0] = i;
                    hasItem[0] = true;
                    logs.add("producer wrote " + i);
                    notEmpty.signal();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            }
        }, "cond-producer");

        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                lock.lock();
                try {
                    while (!hasItem[0]) {
                        logs.add("consumer awaiting notEmpty");
                        notEmpty.await();
                    }
                    logs.add("consumer read " + buffer[0]);
                    hasItem[0] = false;
                    notFull.signal();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            }
        }, "cond-consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        return DemoResult.of("03-locks", "condition",
                "Condition = multiple wait-sets on one Lock (notFull vs notEmpty). Prefer over wait/notify.",
                DemoResult.map("logs", logs));
    }

    public DemoResult stampedLockOptimistic() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        StampedLock stampedLock = new StampedLock();
        double[] point = {1.0, 1.0}; // x, y

        // Writer
        Thread writer = new Thread(() -> {
            long stamp = stampedLock.writeLock();
            try {
                point[0] = 3.0;
                point[1] = 4.0;
                logs.add("writer moved point to (3,4)");
            } finally {
                stampedLock.unlockWrite(stamp);
            }
        }, "stamped-writer");

        // Optimistic reader
        Thread reader = new Thread(() -> {
            long stamp = stampedLock.tryOptimisticRead();
            double x = point[0];
            double y = point[1];
            if (!stampedLock.validate(stamp)) {
                // fallback to read lock if a write happened
                stamp = stampedLock.readLock();
                try {
                    x = point[0];
                    y = point[1];
                    logs.add("optimistic failed → upgraded to readLock, distance=" + Math.hypot(x, y));
                } finally {
                    stampedLock.unlockRead(stamp);
                }
            } else {
                logs.add("optimistic read OK, distance=" + Math.hypot(x, y));
            }
        }, "stamped-reader");

        reader.start();
        Thread.sleep(10);
        writer.start();
        writer.join();
        reader.join();

        // Force a clean optimistic success path
        long stamp = stampedLock.tryOptimisticRead();
        double dist = Math.hypot(point[0], point[1]);
        boolean valid = stampedLock.validate(stamp);
        logs.add("post-write optimistic valid=" + valid + " distance=" + dist);

        return DemoResult.of("03-locks", "stamped-lock",
                "StampedLock: optimistic read + validate(); fail pe readLock. Day-to-day rare — naam/idea jaano.",
                DemoResult.map("logs", logs, "distance", dist));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                reentrantLockFeatures(),
                lockVsTryLock(),
                readWriteLockDemo(),
                conditionDemo(),
                stampedLockOptimistic()
        );
        return DemoResult.of("03-locks", "all",
                "Complete Module 03 — next: /api/modules/04-executors",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }
}
