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
 * MODULE 02 — Synchronization & memory visibility (~3 YOE interview focus)
 *
 * Must-knows:
 * - Monitor / intrinsic lock — monitorLocks()
 * - CAS + AtomicInteger — casSimple()
 * - Race condition, synchronized method vs block
 * - wait/notify need synchronized — waitNotifyNeedsSynchronized()
 * - Spurious wakeup → while (!condition) wait() — spuriousWakeup()
 * - volatile = visibility (not atomic i++); ThreadLocal + remove() in pools
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
                "1. Monitor = har Java object ke saath hidden intrinsic lock.",
                "2. synchronized → usi object ka monitor acquire hota hai.",
                "3. Ek monitor pe ek time pe ek hi thread (mutual exclusion).",
                "4. Doosri thread BLOCKED hoti hai jab tak monitor free na ho.",
                "5. wait/notify bhi usi monitor pe — pehle synchronized zaroori.",
                "6. Prefer private final Object lock = new Object(); (this pe sync avoid)."
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
        Thread.sleep(80);
        logs.add("Snapshot while holder inside: contender.state=" + contender.getState()); // BLOCKED
        holder.join();
        contender.join();

        // reentrant: same thread, same monitor again OK
        synchronized (monitor) {
            synchronized (monitor) {
                logs.add("reentrant: same thread entered synchronized twice");
            }
        }

        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("monitor", "synchronized + wait/notify");
        compare.put("explicit lock", "ReentrantLock — tryLock possible (module 03)");
        compare.put("BLOCKED vs WAITING", "BLOCKED = sync entry wait; WAITING = wait()/join()");

        List<String> interviewAnswers = List.of(
                "Q: Monitor? → Object ka intrinsic lock.",
                "Q: synchronized reentrant? → Haan.",
                "Q: wait lock rakhta hai? → Nahi, release karta hai."
        );

        return DemoResult.of("02-sync", "monitor-locks",
                "Monitor = object's intrinsic lock. synchronized acquires it. BLOCKED = waiting for monitor.",
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
                "CAS = Compare-And-Swap: agar value abhi bhi expected hai to new set karo (atomic).",
                "Example: 5 hai → 'agar 5 hai to 6 karo'. Kisi ne 7 kar diya → fail → retry.",
                "Java: AtomicInteger.compareAndSet / incrementAndGet (andar CAS loop).",
                "Lock = pehle band karo phir update. CAS = try; fail pe dubara try (counters ke liye common)."
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
        lockVsCas.put("Lock/synchronized", "Block karke exclusive update");
        lockVsCas.put("CAS", "Bina lock try + retry — counters/flags ke liye");
        lockVsCas.put("Kab lock", "Bada critical section / kai fields saath");
        lockVsCas.put("Kab CAS", "Simple counter — AtomicInteger");

        List<String> interviewQandA = List.of(
                "Q: CAS? → Atomic 'expected ho to new value set'.",
                "Q: Java kahan? → AtomicInteger, aksar ConcurrentHashMap andar.",
                "Q: Fail pe? → Retry loop (incrementAndGet aisa hi karta hai).",
                "Q: Lock se fark? → Lock blocks; CAS fail-fast + retry."
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

    /**
     * Can you call wait/notify WITHOUT synchronized?
     * Answer: NO — you must already hold that object's monitor.
     */
    public DemoResult waitNotifyNeedsSynchronized() {
        List<String> simpleTheory = List.of(
                "wait/notify bina synchronized? → NAHI.",
                "Pehle usi object pe synchronized lo, phir wait/notify.",
                "Bina monitor → IllegalMonitorStateException.",
                "Same object: synchronized(lock) { lock.wait(); } — alag object pe wait mat karo."
        );

        Object lock = new Object();
        List<String> logs = new ArrayList<>();

        // 1) WITHOUT synchronized → must fail
        try {
            lock.wait(10);
            logs.add("UNEXPECTED: wait() without sync succeeded");
        } catch (IllegalMonitorStateException e) {
            logs.add("WITHOUT synchronized: wait() → IllegalMonitorStateException (expected)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logs.add("interrupted");
        }

        try {
            lock.notify();
            logs.add("UNEXPECTED: notify() without sync succeeded");
        } catch (IllegalMonitorStateException e) {
            logs.add("WITHOUT synchronized: notify() → IllegalMonitorStateException (expected)");
        }

        // 2) WITH synchronized → OK
        synchronized (lock) {
            logs.add("WITH synchronized: holding monitor of 'lock'");
            lock.notifyAll(); // legal — we own the monitor (no waiters, but call is valid)
            logs.add("WITH synchronized: notifyAll() OK");
        }

        // 3) Wrong pattern: synchronized on A, wait on B
        Object a = new Object();
        Object b = new Object();
        try {
            synchronized (a) {
                b.wait(10); // hold A's monitor, but wait on B → still IllegalMonitorStateException
            }
            logs.add("UNEXPECTED: wait on B while sync on A succeeded");
        } catch (IllegalMonitorStateException e) {
            logs.add("sync(A) + B.wait() → IllegalMonitorStateException (must be SAME object)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("correct", "synchronized (lock) { while (!ready) { lock.wait(); } }");
        rules.put("wrong", "lock.wait(); // IllegalMonitorStateException");

        List<String> interviewQandA = List.of(
                "Q: wait bina sync? → No → IllegalMonitorStateException.",
                "Q: Kyun sync? → wait/notify ko monitor chahiye.",
                "Q: wait lock release? → Haan, phir wake ke baad dubara leta hai.",
                "Q: Production tip? → Prefer BlockingQueue over raw wait/notify."
        );

        return DemoResult.of("02-sync", "wait-notify-needs-sync",
                "wait/notify WITHOUT synchronized = IllegalMonitorStateException. Same object ka monitor hold karna zaroori hai.",
                DemoResult.map(
                        "simpleTheory", simpleTheory,
                        "rules", rules,
                        "interviewQandA", interviewQandA,
                        "logs", logs
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
                "wait() releases monitor; notify/notifyAll wakes waiters. Always while (!condition) wait().",
                DemoResult.map(
                        "logs", logs,
                        "waitSteps", WaitNotifyRevision.WAIT_STEPS,
                        "notifySteps", WaitNotifyRevision.NOTIFY_STEPS,
                        "notifyAllSteps", WaitNotifyRevision.NOTIFY_ALL_STEPS,
                        "whyWhile", "Spurious wake + notifyAll → condition dubara check: while (!cond) wait();"
                ));
    }

    /**
     * Spurious wakeup — simple meaning + interview angle.
     * We also show the SAME bug class: wake-up jab condition abhi false hai (notifyAll race).
     */
    public DemoResult spuriousWakeup() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();

        // Demo: 1 item, 2 consumers — notifyAll wakes BOTH; only 1 item exists.
        // with while → second consumer waits again (safe)
        // with if → second consumer would proceed wrongly (unsafe) — we only run the SAFE path live
        Object lock = new Object();
        boolean[] hasItem = {false};
        String[] box = {null};

        Thread producer = new Thread(() -> {
            synchronized (lock) {
                box[0] = "pizza";
                hasItem[0] = true;
                logs.add("producer: put pizza, notifyAll()");
                lock.notifyAll();
            }
        }, "producer");

        Runnable safeConsumer = () -> {
            String name = Thread.currentThread().getName();
            synchronized (lock) {
                // CORRECT: while — re-check after every wake (spurious OR notifyAll)
                while (!hasItem[0]) {
                    logs.add(name + ": condition false → wait()");
                    try {
                        lock.wait();
                        logs.add(name + ": woke up → will RE-CHECK while (!hasItem)");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                logs.add(name + ": took " + box[0]);
                box[0] = null;
                hasItem[0] = false; // second woken consumer must see false and wait again
            }
        };

        Thread c1 = new Thread(safeConsumer, "consumer-1");
        Thread c2 = new Thread(safeConsumer, "consumer-2");
        c1.start();
        c2.start();
        Thread.sleep(80); // both enter wait
        producer.start();
        producer.join();
        c1.join(1000);
        c2.join(200); // one may still be waiting — interrupt to finish demo cleanly
        if (c2.isAlive()) {
            logs.add("consumer-2 still waiting (good — no item left). Interrupting to end demo.");
            c2.interrupt();
            c2.join(200);
        }
        if (c1.isAlive()) {
            c1.interrupt();
            c1.join(200);
        }

        Map<String, String> wrongVsRight = new LinkedHashMap<>();
        wrongVsRight.put("WRONG", "if (!hasItem) { wait(); } take();  // wake ke baad check nahi");
        wrongVsRight.put("RIGHT", "while (!hasItem) { wait(); } take(); // har wake pe dubara check");
        wrongVsRight.put("why", "Spurious wake OR notifyAll ne galat thread uthaya — condition abhi false ho sakti hai");

        return DemoResult.of("02-sync", "spurious-wakeup",
                "Spurious wakeup = wait() se bina notify ke uth jaana. Fix: while (!condition) wait(); never if.",
                DemoResult.map(
                        "simpleDescription", WaitNotifyRevision.SPURIOUS_SIMPLE,
                        "interviewQandA", WaitNotifyRevision.SPURIOUS_Q_AND_A,
                        "wrongVsRight", wrongVsRight,
                        "logs", logs
                ));
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
            holder.ready = true; // visibility: readers see ready + earlier writes
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
                waitNotifyNeedsSynchronized(),
                waitNotifyProducerConsumer(),
                spuriousWakeup(),
                volatileVisibility(),
                threadLocalDemo(),
                synchronizedMethodVsBlock()
        );
        return DemoResult.of("02-sync", "all",
                "Complete Module 02 — next: /api/modules/03-locks",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    /**
     * Short revision steps (~3 YOE) — wait / notify / notifyAll / spurious.
     */
    static final class WaitNotifyRevision {

        static final List<String> WAIT_STEPS = List.of(
                "1. Pehle synchronized se monitor hold karo.",
                "2. wait() → monitor release + WAITING.",
                "3. notify/notifyAll (ya timeout/interrupt) se wake.",
                "4. Dubara monitor lo, tab wait() return — hamesha while (!condition)."
        );

        static final List<String> NOTIFY_STEPS = List.of(
                "1. Monitor hold karke notify().",
                "2. Wait-set se EK thread uthati hai.",
                "3. Wo thread lock ke liye wait karti hai; unsure ho to notifyAll prefer karo."
        );

        static final List<String> NOTIFY_ALL_STEPS = List.of(
                "1. Monitor hold karke notifyAll().",
                "2. Saari waiting threads uthengi aur lock compete karengi.",
                "3. Ek time pe ek jeetegi; baaki while se condition check karke phir wait kar sakti hain."
        );

        static final List<String> SPURIOUS_SIMPLE = List.of(
                "Spurious wakeup = wait() se bina notify ke bhi jag sakna (rare but legal).",
                "Isliye if nahi — while (!condition) { wait(); }",
                "notifyAll ke baad bhi while zaroori — har woken thread ke liye condition true nahi hoti.",
                "Interview line: main hamesha wait loop mein condition re-check karta hoon."
        );

        static final List<String> SPURIOUS_Q_AND_A = List.of(
                "Q: Spurious wakeup? → Bina proper notify ke wait() return.",
                "Q: Fix? → while (!condition) wait();",
                "Q: if kyun galat? → Wake ke baad condition false ho sakti hai.",
                "Q: Sirf spurious? → Nahi — multi-waiter/notifyAll ke liye bhi while."
        );

        private WaitNotifyRevision() {}
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
            // while (not if): after wake, re-check "buffer full?"
            while (count == data.length) {
                logs.add(Thread.currentThread().getName() + " waiting (buffer full)");
                // wait(): release monitor → WAITING → later re-acquire → then continue
                wait();
            }
            data[putIndex] = value;
            putIndex = (putIndex + 1) % data.length;
            count++;
            logs.add(Thread.currentThread().getName() + " put " + value + " count=" + count);
            // notifyAll(): all waiters → leave wait-set → compete for THIS monitor; one wins at a time
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (count == 0) {
                logs.add(Thread.currentThread().getName() + " waiting (buffer empty)");
                // wait(): same steps as above (release → WAITING → re-acquire)
                wait();
            }
            int value = data[takeIndex];
            takeIndex = (takeIndex + 1) % data.length;
            count--;
            logs.add(Thread.currentThread().getName() + " took " + value + " count=" + count);
            // wake producers that may be waiting on "full"
            notifyAll();
            return value;
        }
    }
}
