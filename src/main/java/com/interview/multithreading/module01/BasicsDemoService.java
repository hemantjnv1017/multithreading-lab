package com.interview.multithreading.module01;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * MODULE 01 — Thread basics
 *
 * Interview must-knows:
 * - Thread vs Process
 * - Ways to create threads: extend Thread, implement Runnable, Callable + Future
 * - Thread lifecycle: NEW → RUNNABLE → BLOCKED/WAITING/TIMED_WAITING → TERMINATED
 * - start() vs run() (run() does NOT create a new thread)
 * - daemon vs user threads
 * - join(), interrupt(), sleep()
 */
@Service
public class BasicsDemoService {

    public DemoResult extendThread() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();

        // WAY 1 (interview classic): subclass Thread and OVERRIDE run()
        // This is TRUE "extends Thread" — work lives inside run(), not a Runnable lambda.
        Thread t = new Thread() {
            @Override
            public void run() {
                logs.add("extends-Thread run() on " + Thread.currentThread().getName()
                        + " | class=" + getClass().getName());
            }
        };
        t.setName("extend-thread-demo");
        logs.add("State before start: " + t.getState()); // NEW
        t.start(); // JVM calls our overridden run() on a NEW thread
        t.join();  // wait until that thread finishes
        logs.add("State after join: " + t.getState()); // TERMINATED

        // Named class form (same idea, clearer in interviews)
        WorkerThread named = new WorkerThread(logs);
        named.setName("named-extends-Thread");
        named.start();
        named.join();

        return DemoResult.of("01-basics", "extend-thread",
                "extends Thread = override run(). Prefer Runnable (composition): class MyJob implements Runnable, then new Thread(job).",
                DemoResult.map(
                        "logs", logs,
                        "note", "new Thread(() -> {...}) is Runnable, NOT extends. Empty { } after Thread(...) was a fake subclass."
                ));
    }

    /** Clear "extends Thread" example for interviews. */
    static class WorkerThread extends Thread {
        private final List<String> logs;

        WorkerThread(List<String> logs) {
            this.logs = logs;
        }

        @Override
        public void run() {
            logs.add("WorkerThread.run() on " + getName() + " | extends Thread? "
                    + (this instanceof Thread));
        }
    }

    public DemoResult runnableVsCallable() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();

        Runnable runnable = () -> logs.add("Runnable: no return, no checked exceptions");

        Callable<Integer> callable = () -> {
            logs.add("Callable: can return a value and throw checked exceptions");
            return 42;
        };

        Thread rThread = new Thread(runnable, "runnable-worker");
        rThread.start();
        rThread.join();

        // Callable needs an ExecutorService (or FutureTask)
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Integer> future = pool.submit(callable);
            Integer result = future.get(2, TimeUnit.SECONDS);
            logs.add("Callable result = " + result);
        }

        return DemoResult.of("01-basics", "runnable-vs-callable",
                "Runnable → void. Callable → V + Exception. Both run async via threads/pools.",
                DemoResult.map("logs", logs));
    }

    public DemoResult threadStates() throws InterruptedException {
        List<String> timeline = new CopyOnWriteArrayList<>();
        Object lock = new Object();

        Thread blocked = new Thread(() -> {
            synchronized (lock) {
                timeline.add("blocked-thread acquired lock");
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "will-block-peer");

        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                timeline.add("waiter got lock after peer released → was BLOCKED while waiting for monitor");
            }
        }, "waiter-for-monitor");

        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(200);
                timeline.add("sleeper finished TIMED_WAITING");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sleeper");

        Thread parkWaiter = new Thread(() -> {
            synchronized (lock) {
                try {
                    timeline.add("parkWaiter entering WAITING via wait()");
                    lock.wait(500); // WAITING / TIMED_WAITING
                    timeline.add("parkWaiter woke up");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "park-waiter");

        timeline.add("NEW example: just-created state = " + new Thread(() -> {}).getState());

        blocked.start();
        Thread.sleep(50); // let blocked hold the lock
        waiter.start();
        Thread.sleep(20);
        timeline.add("waiter state while peer holds lock: " + waiter.getState()); // BLOCKED

        sleeper.start();
        Thread.sleep(20);
        timeline.add("sleeper state: " + sleeper.getState()); // TIMED_WAITING

        // release blocked so parkWaiter can demo wait
        blocked.join();
        waiter.join();

        parkWaiter.start();
        Thread.sleep(50);
        timeline.add("parkWaiter state during wait: " + parkWaiter.getState());
        parkWaiter.join();
        sleeper.join();

        return DemoResult.of("01-basics", "thread-states",
                "Memorize: NEW, RUNNABLE, BLOCKED (monitor), WAITING, TIMED_WAITING, TERMINATED.",
                DemoResult.map("timeline", timeline));
    }

    public DemoResult startVsRun() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        String main = Thread.currentThread().getName();

        Runnable task = () -> logs.add("executed on thread=" + Thread.currentThread().getName());

        // WRONG for concurrency: run() executes on calling thread
        task.run();
        logs.add("after task.run() — still on main? " + Thread.currentThread().getName().equals(main));

        // CORRECT: start() creates a new OS/platform thread
        Thread t = new Thread(task, "real-new-thread");
        t.start();
        t.join();

        return DemoResult.of("01-basics", "start-vs-run",
                "Calling run() directly = sync on current thread. Always use start() or an Executor.",
                DemoResult.map("mainThread", main, "logs", logs));
    }

    public DemoResult joinInterruptDaemon() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();

        Thread worker = new Thread(() -> {
            try {
                logs.add("worker sleeping...");
                Thread.sleep(5000);
                logs.add("worker done (should NOT appear if interrupted)");
            } catch (InterruptedException e) {
                logs.add("worker caught InterruptedException — restoring interrupt flag");
                Thread.currentThread().interrupt();
            }
        }, "interruptible-worker");

        Thread daemon = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "daemon-helper");
        daemon.setDaemon(true); // JVM exits even if daemon is still running

        worker.start();
        daemon.start();
        Thread.sleep(100);
        worker.interrupt();
        worker.join();
        logs.add("worker alive after join? " + worker.isAlive());
        logs.add("daemon isDaemon=" + daemon.isDaemon());

        return DemoResult.of("01-basics", "join-interrupt-daemon",
                "Always restore interrupt flag after catching InterruptedException. Daemon threads die with JVM.",
                DemoResult.map("logs", logs));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = new ArrayList<>();
        parts.add(extendThread());
        parts.add(runnableVsCallable());
        parts.add(threadStates());
        parts.add(startVsRun());
        parts.add(joinInterruptDaemon());
        return DemoResult.of("01-basics", "all",
                "Complete Module 01 — move to /api/modules/02-sync next.",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(),
                        "results", parts));
    }
}
