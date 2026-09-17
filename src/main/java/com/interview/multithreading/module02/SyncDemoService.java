package com.interview.multithreading.module02;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MODULE 02 — Synchronization & memory visibility
 *
 * Interview must-knows:
 * - Monitor / intrinsic lock (every object has one) — see monitorLocks()
 * - CAS (Compare-And-Swap) — see casSimple() — AtomicInteger uses this
 * - Race condition: shared mutable state without synchronization
 * - synchronized method vs synchronized block
 * - wait() / notify() / notifyAll() — must hold the monitor
 * - volatile: visibility + ordering, NOT atomicity for compound actions
 * - happens-before relationships
 * - ThreadLocal: per-thread storage (watch for memory leaks in pools)
 */
@Service
public class SyncDemoService {

    /**
     * THEORY + hands-on: Monitor locks (intrinsic locks).
     *
     * In Java, every Object has an associated monitor. synchronized uses that monitor.
     * This is NOT ReentrantLock — that is an explicit lock (module 03).
     */
    public DemoResult monitorLocks() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        List<String> theory = List.of(
                "1. Monitor (intrinsic lock) = hidden lock built into EVERY Java object.",
                "2. synchronized(obj) / synchronized method → acquire that object's monitor.",
                "3. Only ONE thread holds a given monitor at a time (mutual exclusion).",
                "4. Other threads needing the same monitor enter BLOCKED state (not WAITING).",
                "5. wait()/notify()/notifyAll() work ONLY while holding that same monitor.",
                "6. wait() RELEASES the monitor, then parks; notify wakes a waiter (still must re-acquire).",
                "7. Monitors are REENTRANT: same thread can enter synchronized on same object again.",
                "8. static synchronized → monitor of the Class object (MyClass.class), not 'this'.",
                "9. Prefer private final Object lock = new Object(); — never sync on public/this if avoidable.",
                "10. Monitor lock ≠ ReentrantLock. Monitor = synchronized. Explicit = java.util.concurrent.locks."
        );

        Object monitor = new Object(); // this object's intrinsic lock = our monitor

        // Demo: two threads contend for the SAME monitor → one runs, other BLOCKED
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch holderInside = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            synchronized (monitor) {
                logs.add("HOLDER acquired monitor; state peers may be BLOCKED");
                bothStarted.countDown();
                holderInside.countDown();
                try {
                    Thread.sleep(200); // hold monitor so peer blocks
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logs.add("HOLDER releasing monitor");
            }
        }, "monitor-holder");

        Thread contender = new Thread(() -> {
            try {
                bothStarted.countDown();
                holderInside.await(); // ensure holder already inside synchronized
                Thread.sleep(20);
                logs.add("CONTENDER before sync, holderAlive — will BLOCK until holder exits");
                synchronized (monitor) {
                    logs.add("CONTENDER acquired monitor after holder released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "monitor-contender");

        holder.start();
        contender.start();
        Thread.sleep(80); // mid-hold snapshot
        logs.add("Snapshot while holder inside: contender.state=" + contender.getState()); // expect BLOCKED
        holder.join();
        contender.join();

        // Reentrancy: same thread acquires monitor twice
        synchronized (monitor) {
            logs.add("outer synchronized entered");
            synchronized (monitor) {
                logs.add("inner synchronized entered (REENTRANT — same thread, same monitor)");
            }
        }

        // Different monitors do NOT block each other
        Object monitorA = new Object();
        Object monitorB = new Object();
        CountDownLatch parallel = new CountDownLatch(2);
        Thread tA = new Thread(() -> {
            synchronized (monitorA) {
                logs.add("ThreadA holds monitorA (independent of monitorB)");
                parallel.countDown();
                try {
                    parallel.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "mon-A");
        Thread tB = new Thread(() -> {
            synchronized (monitorB) {
                logs.add("ThreadB holds monitorB (independent of monitorA)");
                parallel.countDown();
                try {
                    parallel.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "mon-B");
        tA.start();
        tB.start();
        tA.join();
        tB.join();

        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("monitor/intrinsic", "synchronized, wait/notify — built into Object");
        compare.put("explicit lock", "ReentrantLock, ReadWriteLock — java.util.concurrent.locks");
        compare.put("BLOCKED vs WAITING", "BLOCKED = waiting to ENTER synchronized; WAITING = called wait()/join()");
        compare.put("visibility", "Exiting synchronized flushes writes; entering sees latest (happens-before)");

        List<String> interviewAnswers = List.of(
                "Q: What is a monitor lock? → Intrinsic lock associated with every object; used by synchronized.",
                "Q: Can two threads hold different objects' monitors? → Yes — locks are per-object.",
                "Q: Is synchronized reentrant? → Yes.",
                "Q: Does wait() keep the lock? → No, it releases the monitor until woken + re-acquired.",
                "Q: Monitor vs Lock interface? → Monitor = language intrinsic; Lock = explicit API (tryLock, fairness...)."
        );

        return DemoResult.of("02-sync", "monitor-locks",
                "Monitor = object's intrinsic lock. synchronized acquires it. BLOCKED = waiting for monitor. Explicit locks are module 03.",
                DemoResult.map(
                        "theory", theory,
                        "compareWithExplicitLocks", compare,
                        "interviewQandA", interviewAnswers,
                        "logs", logs
                ));
    }

    /**
     * CAS in simple language + small demo.
     *
     * CAS = Compare-And-Swap.
     * Plain English: "Agar value abhi bhi wahi hai jo main soch raha hoon, tabhi badalna — warna mat badalna."
     */
    public DemoResult casSimple() throws InterruptedException {
        List<String> simpleTheory = List.of(
                "CAS full form: Compare-And-Swap.",
                "Simple meaning: pehle check karo value expected hai ya nahi; agar HAAN to naya value set karo — ye 3 steps CPU ek saath (atomic) karta hai.",
                "Example: box mein 5 hai. Thread kehta hai: 'agar abhi 5 hai to 6 kar do'. Agar kisi ne pehle hi 7 kar diya, CAS fail → dubara try.",
                "Isliye lock ki zarurat nahi padti counters ke liye — AtomicInteger andar CAS use karta hai.",
                "compareAndSet(expected, newValue) Java ka CAS API hai: true = success, false = fail.",
                "incrementAndGet() = baar-baar CAS try karo jab tak success na ho (optimistic retry loop).",
                "Lock (synchronized) = pehle room band karo, phir kaam. CAS = bina lock ke try; fail pe dobara try.",
                "ABA problem (advanced): value A→B→A ho jaye to CAS sochta hai 'same hai' — kabhi galat success. AtomicStampedReference fix karta hai."
        );

        List<String> logs = new CopyOnWriteArrayList<>();
        AtomicInteger box = new AtomicInteger(5);

        // Success path
        boolean ok = box.compareAndSet(5, 6);
        logs.add("CAS(expect=5, new=6) → " + ok + ", box=" + box.get());

        // Fail path — expected wrong
        boolean fail = box.compareAndSet(5, 99);
        logs.add("CAS(expect=5, new=99) → " + fail + " (fail because box is already 6), box=" + box.get());

        // Manual retry loop = what incrementAndGet roughly does
        AtomicInteger counter = new AtomicInteger(0);
        int threads = 8;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < perThread; j++) {
                        // DIY CAS loop (same idea as incrementAndGet)
                        while (true) {
                            int old = counter.get();
                            int next = old + 1;
                            if (counter.compareAndSet(old, next)) {
                                break; // success
                            }
                            // fail = kisi aur ne change kar diya → loop again
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        int expected = threads * perThread;
        logs.add("DIY CAS increment: expected=" + expected + " got=" + counter.get());

        // Built-in AtomicInteger
        AtomicInteger builtin = new AtomicInteger(0);
        for (int i = 0; i < 100; i++) {
            builtin.incrementAndGet(); // uses CAS inside
        }
        logs.add("AtomicInteger.incrementAndGet x100 → " + builtin.get());

        Map<String, String> lockVsCas = new LinkedHashMap<>();
        lockVsCas.put("synchronized/Lock", "Pehle lock lo, phir update. Doosri thread wait (BLOCKED) karti hai.");
        lockVsCas.put("CAS", "Bina lock try karo. Agar fail → turant dubara try. Zyada tar counters ke liye faster.");
        lockVsCas.put("Kab lock?", "Bada critical section, wait/notify, kai variables saath.");
        lockVsCas.put("Kab CAS?", "Single variable counter/flag, ConcurrentHashMap internals, lock-free ideas.");

        List<String> interviewQandA = List.of(
                "Q: CAS kya hai? → Atomic 'agar abhi expected hai to new value set karo'.",
                "Q: Java mein kahan? → AtomicInteger.compareAndSet / incrementAndGet, ConcurrentHashMap.",
                "Q: Fail hone pe kya? → Usually retry loop.",
                "Q: Lock se fark? → Lock blocks; CAS optimistic + retry.",
                "Q: ABA? → Value wapas same dikhe to CAS dhokha kha sakta hai."
        );

        return DemoResult.of("02-sync", "cas",
                "CAS = Compare-And-Swap: 'agar value abhi bhi X hai to Y kar do' — AtomicInteger isi pe chalta hai, lock nahi.",
                DemoResult.map(
                        "simpleTheory", simpleTheory,
                        "lockVsCas", lockVsCas,
                        "interviewQandA", interviewQandA,
                        "diyCasFinalCount", counter.get(),
                        "diyCasExpected", expected,
                        "logs", logs
                ));
    }

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
                monitorLocks(),
                casSimple(),
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
