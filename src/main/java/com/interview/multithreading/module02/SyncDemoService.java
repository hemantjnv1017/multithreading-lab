package com.interview.multithreading.module02;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MODULE 02 — Synchronization & memory visibility
 *
 * Interview must-knows:
 * - Race condition: shared mutable state without synchronization
 * - synchronized method vs synchronized block (monitor / intrinsic lock)
 * - wait() / notify() / notifyAll() — must hold the monitor
 * - volatile: visibility + ordering, NOT atomicity for compound actions
 * - happens-before relationships
 * - ThreadLocal: per-thread storage (watch for memory leaks in pools)
 */
@Service
public class SyncDemoService {
    public DemoResult raceCondition() throws InterruptedException {
        // Broken shared counter
        class UnsafeCounter {
            int value = 0;
            void increment() { value++; } // read-modify-write is NOT atomic
        }
        UnsafeCounter unsafe = new UnsafeCounter();

        // Safe with synchronized
        class SafeCounter {
            int value = 0;
            synchronized void increment() { value++; }
        }
        SafeCounter safe = new SafeCounter();

        // Safe with AtomicInteger
        AtomicInteger atomic = new AtomicInteger();

        int threads = 10;
        int incrementsPerThread = 10_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        unsafe.increment();
                        safe.increment();
                        atomic.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        int expected = threads * incrementsPerThread;
        return DemoResult.of("02-sync", "race-condition",
                "value++ is NOT atomic. Use synchronized, Lock, or Atomic* for shared counters.",
                DemoResult.map(
                        "expected", expected,
                        "unsafeResult", unsafe.value,
                        "lostUpdates", expected - unsafe.value,
                        "synchronizedResult", safe.value,
                        "atomicResult", atomic.get()
                ));
    }

    public DemoResult waitNotifyProducerConsumer() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        BlockingBuffer buffer = new BlockingBuffer(2, logs);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        return DemoResult.of("02-sync", "wait-notify",
                "wait() releases the monitor; notify/notifyAll wakes waiters. Always wait in a while-loop (spurious wakeups).",
                DemoResult.map("logs", logs));
    }

    public DemoResult volatileVisibility() throws InterruptedException {
        // Without volatile, reader may NEVER see the write (cached / reordered)
        class FlagHolder {
            volatile boolean ready = false; // try removing volatile in an interview whiteboard
            int data = 0;
        }
        FlagHolder holder = new FlagHolder();
        List<String> logs = new CopyOnWriteArrayList<>();

        Thread writer = new Thread(() -> {
            holder.data = 42;
            holder.ready = true; // happens-before: write to volatile flushes prior writes
            logs.add("writer set data=42 and ready=true");
        }, "writer");

        Thread reader = new Thread(() -> {
            while (!holder.ready) {
                // busy-wait — only for demo; prefer BlockingQueue / CountDownLatch
                Thread.onSpinWait();
            }
            logs.add("reader saw ready=true, data=" + holder.data);
        }, "reader");

        reader.start();
        Thread.sleep(50);
        writer.start();
        writer.join();
        reader.join(2000);

        return DemoResult.of("02-sync", "volatile-visibility",
                "volatile guarantees visibility & ordering, NOT atomic compound ops (i++ still needs AtomicInteger).",
                DemoResult.map("logs", logs, "finalData", holder.data));
    }

    public DemoResult threadLocalDemo() throws InterruptedException {
        ThreadLocal<String> userContext = new ThreadLocal<>();
        List<String> logs = new CopyOnWriteArrayList<>();

        Runnable task = () -> {
            String name = Thread.currentThread().getName();
            userContext.set("user-" + name);
            try {
                logs.add(name + " context=" + userContext.get());
            } finally {
                userContext.remove(); // CRITICAL when using thread pools — prevent leaks
                logs.add(name + " after remove=" + userContext.get());
            }
        };

        Thread t1 = new Thread(task, "T1");
        Thread t2 = new Thread(task, "T2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        return DemoResult.of("02-sync", "thread-local",
                "ThreadLocal = per-thread copy. Always remove() in finally when threads are pooled (Tomcat/Spring).",
                DemoResult.map("logs", logs));
    }

    public DemoResult synchronizedMethodVsBlock() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        Object fineGrainedLock = new Object();

        class Inventory {
            int stock = 100;

            // locks entire 'this' — coarse
            synchronized void decrementMethod() {
                stock--;
                logs.add(Thread.currentThread().getName() + " method-sync stock=" + stock);
            }

            // locks only the critical section / chosen monitor — finer control
            void decrementBlock() {
                // non-critical work could go here without holding a lock
                synchronized (fineGrainedLock) {
                    stock--;
                    logs.add(Thread.currentThread().getName() + " block-sync stock=" + stock);
                }
            }
        }

        Inventory inv = new Inventory();
        Thread a = new Thread(inv::decrementMethod, "A");
        Thread b = new Thread(inv::decrementBlock, "B");
        a.start();
        b.start();
        a.join();
        b.join();

        return DemoResult.of("02-sync", "synchronized-scopes",
                "Prefer synchronized blocks with a private final lock object over synchronizing on 'this'.",
                DemoResult.map("finalStock", inv.stock, "logs", logs));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                raceCondition(),
                waitNotifyProducerConsumer(),
                volatileVisibility(),
                threadLocalDemo(),
                synchronizedMethodVsBlock()
        );
        return DemoResult.of("02-sync", "all",
                "Complete Module 02 — next: /api/modules/03-locks",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    /** Classic bounded buffer using wait/notify. */
    static class BlockingBuffer {
        private final int[] data;
        private int count = 0;
        private int putIndex = 0;
        private int takeIndex = 0;
        private final List<String> logs;

        BlockingBuffer(int capacity, List<String> logs) {
            this.data = new int[capacity];
            this.logs = logs;
        }

        synchronized void put(int value) throws InterruptedException {
            while (count == data.length) {
                logs.add(Thread.currentThread().getName() + " waiting (buffer full)");
                wait();
            }
            data[putIndex] = value;
            putIndex = (putIndex + 1) % data.length;
            count++;
            logs.add(Thread.currentThread().getName() + " put " + value + " count=" + count);
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (count == 0) {
                logs.add(Thread.currentThread().getName() + " waiting (buffer empty)");
                wait();
            }
            int value = data[takeIndex];
            takeIndex = (takeIndex + 1) % data.length;
            count--;
            logs.add(Thread.currentThread().getName() + " took " + value + " count=" + count);
            notifyAll();
            return value;
        }
    }
}
