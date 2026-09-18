package com.interview.multithreading.module01;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MODULE 01 — Thread basics (~3–4 YOE interview focus)
 *
 * High-frequency: Runnable vs Callable, start vs run, join ordering,
 * wait vs sleep, daemon, interrupt, basic states
 */
@Service
public class BasicsDemoService {

    public DemoResult extendThread() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();

        // Prefer Runnable in real code; extends Thread still asked in interviews
        Thread t = new Thread(() -> logs.add("running on " + Thread.currentThread().getName()),
                "demo-thread");
        logs.add("before start: " + t.getState()); // NEW
        t.start();
        t.join();
        logs.add("after join: " + t.getState()); // TERMINATED

        return DemoResult.of("01-basics", "extend-thread",
                "Interview: prefer implements Runnable (composition). extends Thread rare in production.",
                DemoResult.map("logs", logs));
    }

    public DemoResult runnableVsCallable() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();

        Runnable runnable = () -> logs.add("Runnable → no return value");
        Callable<Integer> callable = () -> {
            logs.add("Callable → returns value (and can throw checked Exception)");
            return 42;
        };

        Thread t = new Thread(runnable, "r-worker");
        t.start();
        t.join();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            logs.add("Callable result=" + pool.submit(callable).get(1, TimeUnit.SECONDS));
        }

        return DemoResult.of("01-basics", "runnable-vs-callable",
                "Runnable = void. Callable = returns V + can throw. Submit Callable to ExecutorService.",
                DemoResult.map("logs", logs));
    }

    public DemoResult threadStates() throws InterruptedException {
        List<String> timeline = new CopyOnWriteArrayList<>();
        Object lock = new Object();

        timeline.add("NEW = " + new Thread(() -> {}).getState());

        Thread holder = new Thread(() -> {
            synchronized (lock) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "holder");

        Thread blocked = new Thread(() -> {
            synchronized (lock) {
                timeline.add("blocked thread finally entered sync");
            }
        }, "blocked");

        holder.start();
        Thread.sleep(30);
        blocked.start();
        Thread.sleep(20);
        timeline.add("while holder holds monitor, peer state=" + blocked.getState()); // BLOCKED

        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sleeper");
        sleeper.start();
        Thread.sleep(20);
        timeline.add("sleep → " + sleeper.getState()); // TIMED_WAITING

        holder.join();
        blocked.join();
        sleeper.join();
        timeline.add("done → TERMINATED");

        return DemoResult.of("01-basics", "thread-states",
                "Know: NEW, RUNNABLE, BLOCKED (monitor), WAITING, TIMED_WAITING, TERMINATED.",
                DemoResult.map(
                        "timeline", timeline,
                        "quick", Map.of(
                                "BLOCKED", "waiting to enter synchronized",
                                "WAITING", "wait() / join()",
                                "TIMED_WAITING", "sleep() / wait(timeout)"
                        )
                ));
    }

    public DemoResult startVsRun() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        String main = Thread.currentThread().getName();
        Runnable task = () -> logs.add("on " + Thread.currentThread().getName());

        task.run(); // same thread — NOT concurrent
        Thread t = new Thread(task, "real-thread");
        t.start();  // new thread
        t.join();

        return DemoResult.of("01-basics", "start-vs-run",
                "run() = current thread pe sync call. start() = naya thread. Interview favorite trap.",
                DemoResult.map("main", main, "logs", logs));
    }

    /** Very common: ensure T1 → T2 → T3 order using join(). */
    public DemoResult joinOrdering() throws InterruptedException {
        List<String> order = new CopyOnWriteArrayList<>();

        Thread t1 = new Thread(() -> order.add("T1"), "T1");
        Thread t2 = new Thread(() -> order.add("T2"), "T2");
        Thread t3 = new Thread(() -> order.add("T3"), "T3");

        t1.start();
        t1.join(); // wait until T1 finishes
        t2.start();
        t2.join();
        t3.start();
        t3.join();

        return DemoResult.of("01-basics", "join-ordering",
                "Q: T1 then T2 then T3 kaise? → start + join chain. (CountDownLatch bhi option hai.)",
                DemoResult.map("executionOrder", order));
    }

    /** Extremely common: wait() vs sleep(). */
    public DemoResult waitVsSleep() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();
        Object lock = new Object();

        // sleep: does NOT release monitor
        Thread sleeper = new Thread(() -> {
            synchronized (lock) {
                logs.add("sleeper: holding lock, sleeping 120ms (lock NOT released)");
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logs.add("sleeper: woke, still in synchronized");
            }
        }, "sleeper");

        Thread wantsLock = new Thread(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logs.add("peer: trying synchronized while sleeper sleeps → will BLOCK");
            synchronized (lock) {
                logs.add("peer: got lock after sleeper finished");
            }
        }, "peer");

        sleeper.start();
        wantsLock.start();
        sleeper.join();
        wantsLock.join();

        // wait: releases monitor (needs synchronized)
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                try {
                    logs.add("waiter: wait(80) → releases lock + WAITING");
                    lock.wait(80);
                    logs.add("waiter: back after wait");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "waiter");
        waiter.start();
        waiter.join();

        Map<String, String> diff = new LinkedHashMap<>();
        diff.put("sleep()", "Thread class. Monitor release NAHI. Kisi notify ki zarurat nahi.");
        diff.put("wait()", "Object class. Monitor RELEASE. Synchronized chahiye. notify se wake.");
        diff.put("use_sleep", "Delay / pause — lock free rakhna ho to sync ke bahar sleep.");
        diff.put("use_wait", "Condition ke liye wait (producer-consumer).");

        return DemoResult.of("01-basics", "wait-vs-sleep",
                "sleep = time pass, lock rakho. wait = lock chhodo + condition wait. Top 3–4 YOE question.",
                DemoResult.map("difference", diff, "logs", logs));
    }

    public DemoResult joinInterruptDaemon() throws InterruptedException {
        List<String> logs = new CopyOnWriteArrayList<>();

        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(2000);
                logs.add("should not print if interrupted");
            } catch (InterruptedException e) {
                logs.add("InterruptedException → restore flag");
                Thread.currentThread().interrupt();
            }
        }, "worker");

        Thread daemon = new Thread(() -> {}, "daemon");
        daemon.setDaemon(true);

        worker.start();
        daemon.start();
        Thread.sleep(50);
        worker.interrupt();
        worker.join();
        logs.add("daemon=" + daemon.isDaemon() + " (JVM exit pe daemon ruk sakta hai)");

        return DemoResult.of("01-basics", "join-interrupt-daemon",
                "interrupt → catch InterruptedException aur flag restore. Daemon = background, JVM exit pe die.",
                DemoResult.map("logs", logs));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                extendThread(),
                runnableVsCallable(),
                threadStates(),
                startVsRun(),
                joinOrdering(),
                waitVsSleep(),
                joinInterruptDaemon()
        );
        return DemoResult.of("01-basics", "all",
                "Module 01 done → /api/modules/02-sync",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }
}
