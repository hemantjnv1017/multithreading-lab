package com.interview.multithreading.module08;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MODULE 08 — Classic concurrency problems (and how to fix them)
 *
 * Interview must-knows:
 * - Race condition
 * - Deadlock (4 Coffman conditions) + prevention
 * - Livelock
 * - Starvation
 * - How to detect (jstack / thread dump) and fix
 */
@Service
public class ProblemsDemoService {

    public DemoResult raceAndFix() throws InterruptedException {
        // Already covered deeply in module 02 — short recap with bank transfer smell
        class Account {
            int balance;
            Account(int b) { this.balance = b; }
        }
        Account a = new Account(1000);
        Account b = new Account(1000);

        // UNSAFE: two threads mutate shared balances with NO lock → race (lost updates / wrong total)
        Runnable unsafeTransfer = () -> {
            for (int i = 0; i < 1000; i++) {
                a.balance -= 1;
                b.balance += 1;
            }
        };

        Thread u1 = new Thread(unsafeTransfer);
        Thread u2 = new Thread(unsafeTransfer);
        u1.start();
        u2.start();
        u1.join();
        u2.join();
        int unsafeTotal = a.balance + b.balance;

        // SAFE: both threads must take the SAME monitor before touching c/d
        Account c = new Account(1000);
        Account d = new Account(1000);

        // Dedicated lock object — not used for data, only as a monitor for synchronized.
        // Every Java object has exactly one intrinsic (monitor) lock.
        // We create a private Object so:
        //   1) synchronized(lock) can acquire THAT object's monitor
        //   2) we don't sync on 'this' / Account (outsiders could lock those and block us)
        //   3) one shared lock serializes ALL transfers → no race on c.balance / d.balance
        Object lock = new Object();

        Runnable safeTransfer = () -> {
            for (int i = 0; i < 1000; i++) {
                synchronized (lock) { // acquire lock's monitor; other thread waits (BLOCKED) if busy
                    c.balance -= 1;
                    d.balance += 1;
                } // monitor released here
            }
        };
        Thread s1 = new Thread(safeTransfer);
        Thread s2 = new Thread(safeTransfer);
        s1.start();
        s2.start();
        s1.join();
        s2.join();

        return DemoResult.of("08-problems", "race-condition",
                "Race = incorrect result from unsynchronized shared mutation. Fix: sync / locks / atomics / immutability.",
                DemoResult.map(
                        "expectedTotal", 2000,
                        "unsafeTotal", unsafeTotal,
                        "safeTotal", c.balance + d.balance
                ));
    }

    public DemoResult deadlockDemo() throws InterruptedException {
        Object lockA = new Object();
        Object lockB = new Object();
        List<String> logs = new CopyOnWriteArrayList<>();
        CountDownLatch bothStarted = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                logs.add("T1 acquired A");
                bothStarted.countDown();
                try {
                    bothStarted.await();
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logs.add("T1 waiting for B...");
                synchronized (lockB) {
                    logs.add("T1 acquired B"); // won't reach if deadlocked
                }
            }
        }, "deadlock-T1");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                logs.add("T2 acquired B");
                bothStarted.countDown();
                try {
                    bothStarted.await();
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logs.add("T2 waiting for A...");
                synchronized (lockA) {
                    logs.add("T2 acquired A");
                }
            }
        }, "deadlock-T2");

        t1.start();
        t2.start();
        t1.join(800);
        t2.join(200);

        boolean deadlocked = t1.isAlive() && t2.isAlive();
        if (deadlocked) {
            logs.add("DETECTED: both threads still alive after timeout → classic deadlock");
            t1.interrupt();
            t2.interrupt();
            // Force break for demo — in real life fix lock ordering
        }

        // FIX: consistent global lock ordering
        List<String> fixLogs = new CopyOnWriteArrayList<>();
        Object x = new Object();
        Object y = new Object();
        Runnable ordered = () -> {
            Object first = System.identityHashCode(x) < System.identityHashCode(y) ? x : y;
            Object second = first == x ? y : x;
            synchronized (first) {
                synchronized (second) {
                    fixLogs.add(Thread.currentThread().getName() + " acquired both (ordered)");
                }
            }
        };
        Thread f1 = new Thread(ordered, "fix-1");
        Thread f2 = new Thread(ordered, "fix-2");
        f1.start();
        f2.start();
        f1.join();
        f2.join();

        return DemoResult.of("08-problems", "deadlock",
                "Deadlock needs: mutual exclusion, hold-and-wait, no preemption, circular wait. Fix: lock ordering / tryLock timeouts.",
                DemoResult.map("deadlockObserved", deadlocked, "logs", logs, "fixLogs", fixLogs));
    }

    public DemoResult livelockDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        ReentrantLock left = new ReentrantLock();
        ReentrantLock right = new ReentrantLock();
        AtomicFlag done = new AtomicFlag();

        Thread polite1 = new Thread(() -> {
            int attempts = 0;
            while (!done.value && attempts < 20) {
                attempts++;
                if (left.tryLock()) {
                    try {
                        if (right.tryLock()) {
                            try {
                                logs.add("polite1 got both locks");
                                done.value = true;
                                return;
                            } finally {
                                right.unlock();
                            }
                        } else {
                            logs.add("polite1 releasing left (being polite)");
                        }
                    } finally {
                        if (left.isHeldByCurrentThread()) left.unlock();
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            logs.add("polite1 gave up after attempts=" + attempts);
        }, "livelock-1");

        Thread polite2 = new Thread(() -> {
            int attempts = 0;
            while (!done.value && attempts < 20) {
                attempts++;
                if (right.tryLock()) {
                    try {
                        if (left.tryLock()) {
                            try {
                                logs.add("polite2 got both locks");
                                done.value = true;
                                return;
                            } finally {
                                left.unlock();
                            }
                        } else {
                            logs.add("polite2 releasing right (being polite)");
                        }
                    } finally {
                        if (right.isHeldByCurrentThread()) right.unlock();
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            logs.add("polite2 gave up after attempts=" + attempts);
        }, "livelock-2");

        polite1.start();
        polite2.start();
        polite1.join();
        polite2.join();

        return DemoResult.of("08-problems", "livelock",
                "Livelock: threads keep responding to each other but make no progress. Fix: backoff/random retry, or lock ordering.",
                DemoResult.map("resolved", done.value, "logs", logs));
    }

    public DemoResult starvationDemo() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        // Unfair lock can starve a thread under contention
        Lock unfair = new ReentrantLock(false);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, Integer> acquisitions = new ConcurrentHashMap<>();

        Runnable hog = () -> {
            try {
                start.await();
                for (int i = 0; i < 50; i++) {
                    unfair.lock();
                    try {
                        acquisitions.merge(Thread.currentThread().getName(), 1, Integer::sum);
                        Thread.sleep(5);
                    } finally {
                        unfair.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread greedy1 = new Thread(hog, "greedy-1");
        Thread greedy2 = new Thread(hog, "greedy-2");
        Thread maybeStarved = new Thread(hog, "maybe-starved");

        greedy1.start();
        greedy2.start();
        maybeStarved.start();
        start.countDown();
        greedy1.join();
        greedy2.join();
        maybeStarved.join();

        logs.add("acquisitions=" + acquisitions);
        logs.add("Fair ReentrantLock(true) reduces starvation (FIFO entry).");

        return DemoResult.of("08-problems", "starvation",
                "Starvation: a thread never gets CPU/lock. Fair locks, priority management, avoid long critical sections.",
                DemoResult.map("acquisitions", acquisitions, "logs", logs));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                raceAndFix(),
                deadlockDemo(),
                livelockDemo(),
                starvationDemo()
        );
        return DemoResult.of("08-problems", "all",
                "Complete Module 08 — next: /api/modules/09-spring",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    static class AtomicFlag {
        volatile boolean value = false;
    }
}
