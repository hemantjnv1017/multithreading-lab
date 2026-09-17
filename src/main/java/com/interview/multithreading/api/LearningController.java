package com.interview.multithreading.api;

import com.interview.multithreading.common.DemoResult;
import com.interview.multithreading.module01.BasicsDemoService;
import com.interview.multithreading.module02.SyncDemoService;
import com.interview.multithreading.module03.LocksDemoService;
import com.interview.multithreading.module04.ExecutorsDemoService;
import com.interview.multithreading.module05.ConcurrentUtilsDemoService;
import com.interview.multithreading.module06.CompletableFutureDemoService;
import com.interview.multithreading.module07.ForkJoinDemoService;
import com.interview.multithreading.module08.ProblemsDemoService;
import com.interview.multithreading.module09.SpringAsyncDemoService;
import com.interview.multithreading.module10.VirtualThreadsDemoService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST catalog for every multithreading demo.
 *
 * Workflow:
 *  1. GET  /api/modules              → learning path
 *  2. GET  /api/modules/{id}         → run all demos in a module
 *  3. GET  /api/modules/{id}/{demo}  → run one demo
 */
@RestController
@RequestMapping("/api")
public class LearningController {

    private final BasicsDemoService basics;
    private final SyncDemoService sync;
    private final LocksDemoService locks;
    private final ExecutorsDemoService executors;
    private final ConcurrentUtilsDemoService concurrent;
    private final CompletableFutureDemoService completable;
    private final ForkJoinDemoService forkJoin;
    private final ProblemsDemoService problems;
    private final SpringAsyncDemoService springAsync;
    private final VirtualThreadsDemoService virtualThreads;

    public LearningController(
            BasicsDemoService basics,
            SyncDemoService sync,
            LocksDemoService locks,
            ExecutorsDemoService executors,
            ConcurrentUtilsDemoService concurrent,
            CompletableFutureDemoService completable,
            ForkJoinDemoService forkJoin,
            ProblemsDemoService problems,
            SpringAsyncDemoService springAsync,
            VirtualThreadsDemoService virtualThreads) {
        this.basics = basics;
        this.sync = sync;
        this.locks = locks;
        this.executors = executors;
        this.concurrent = concurrent;
        this.completable = completable;
        this.forkJoin = forkJoin;
        this.problems = problems;
        this.springAsync = springAsync;
        this.virtualThreads = virtualThreads;
    }

    @GetMapping("/modules")
    public Map<String, Object> catalog() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Java Multithreading Lab — Interview Path");
        body.put("howToLearn", List.of(
                "Start from module 01 and go in order",
                "Call GET /api/modules/{id} to run all demos",
                "Read the interviewTip field in every response aloud (explain like an interview)",
                "Open the matching *DemoService.java and read the comments",
                "Change the code (break it, fix it) — that builds real muscle memory"
        ));
        body.put("modules", List.of(
                module("01-basics", "Thread, Runnable, Callable, states, start vs run, join/interrupt/daemon",
                        List.of("extend-thread", "runnable-vs-callable", "thread-states", "start-vs-run", "join-interrupt-daemon", "all")),
                module("02-sync", "Monitor, CAS, race, wait/notify MUST use synchronized, volatile, ThreadLocal",
                        List.of("monitor-locks", "cas", "race-condition", "wait-notify-needs-sync", "wait-notify", "volatile-visibility", "thread-local", "synchronized-scopes", "all")),
                module("03-locks", "ReentrantLock, ReadWriteLock, Condition, StampedLock",
                        List.of("reentrant-lock", "read-write-lock", "condition", "stamped-lock", "all")),
                module("04-executors", "Thread pools, Future, ScheduledExecutor, custom TPE",
                        List.of("pool-types", "custom-tpe", "future", "scheduled", "invoke-all-any", "all")),
                module("05-concurrent", "CHM, BlockingQueue, Latch, Barrier, Semaphore, Phaser",
                        List.of("concurrent-hash-map", "blocking-queue", "count-down-latch", "cyclic-barrier", "semaphore", "phaser-exchanger", "all")),
                module("06-completable", "CompletableFuture chaining, combine, errors, fan-out",
                        List.of("chaining", "combine-all-any", "error-handling", "custom-executor", "fan-out", "all")),
                module("07-forkjoin", "ForkJoinPool, RecursiveTask, parallel streams, work-stealing",
                        List.of("recursive-sum", "parallel-stream", "work-stealing", "all")),
                module("08-problems", "Race, deadlock, livelock, starvation + fixes",
                        List.of("race-condition", "deadlock", "livelock", "starvation", "all")),
                module("09-spring", "Spring @Async, proxy pitfall, TaskExecutor, @Scheduled",
                        List.of("async", "executor-info", "all")),
                module("10-virtual", "Virtual threads, scale, pinning, Spring virtual executor",
                        List.of("platform-vs-virtual", "scale-smoke", "pinning", "spring-executor", "all"))
        ));
        return body;
    }

    @GetMapping("/modules/{moduleId}")
    public DemoResult runModule(@PathVariable String moduleId) throws Exception {
        return switch (moduleId) {
            case "01-basics" -> basics.all();
            case "02-sync" -> sync.all();
            case "03-locks" -> locks.all();
            case "04-executors" -> executors.all();
            case "05-concurrent" -> concurrent.all();
            case "06-completable" -> completable.all();
            case "07-forkjoin" -> forkJoin.all();
            case "08-problems" -> problems.all();
            case "09-spring" -> springAsync.all();
            case "10-virtual" -> virtualThreads.all();
            default -> DemoResult.of(moduleId, "unknown", "Unknown module. GET /api/modules", Map.of());
        };
    }

    @GetMapping("/modules/{moduleId}/{demo}")
    public DemoResult runDemo(@PathVariable String moduleId, @PathVariable String demo) throws Exception {
        return switch (moduleId) {
            case "01-basics" -> switch (demo) {
                case "extend-thread" -> basics.extendThread();
                case "runnable-vs-callable" -> basics.runnableVsCallable();
                case "thread-states" -> basics.threadStates();
                case "start-vs-run" -> basics.startVsRun();
                case "join-interrupt-daemon" -> basics.joinInterruptDaemon();
                case "all" -> basics.all();
                default -> unknown(moduleId, demo);
            };
            case "02-sync" -> switch (demo) {
                case "monitor-locks" -> sync.monitorLocks();
                case "cas" -> sync.casSimple();
                case "race-condition" -> sync.raceCondition();
                case "wait-notify-needs-sync" -> sync.waitNotifyNeedsSynchronized();
                case "wait-notify" -> sync.waitNotifyProducerConsumer();
                case "volatile-visibility" -> sync.volatileVisibility();
                case "thread-local" -> sync.threadLocalDemo();
                case "synchronized-scopes" -> sync.synchronizedMethodVsBlock();
                case "all" -> sync.all();
                default -> unknown(moduleId, demo);
            };
            case "03-locks" -> switch (demo) {
                case "reentrant-lock" -> locks.reentrantLockFeatures();
                case "read-write-lock" -> locks.readWriteLockDemo();
                case "condition" -> locks.conditionDemo();
                case "stamped-lock" -> locks.stampedLockOptimistic();
                case "all" -> locks.all();
                default -> unknown(moduleId, demo);
            };
            case "04-executors" -> switch (demo) {
                case "pool-types" -> executors.poolTypes();
                case "custom-tpe" -> executors.customThreadPoolExecutor();
                case "future" -> executors.futureBasics();
                case "scheduled" -> executors.scheduledExecutor();
                case "invoke-all-any" -> executors.invokeAllAny();
                case "all" -> executors.all();
                default -> unknown(moduleId, demo);
            };
            case "05-concurrent" -> switch (demo) {
                case "concurrent-hash-map" -> concurrent.concurrentHashMapDemo();
                case "blocking-queue" -> concurrent.blockingQueueProducerConsumer();
                case "count-down-latch" -> concurrent.countDownLatchDemo();
                case "cyclic-barrier" -> concurrent.cyclicBarrierDemo();
                case "semaphore" -> concurrent.semaphoreDemo();
                case "phaser-exchanger" -> concurrent.phaserAndExchanger();
                case "all" -> concurrent.all();
                default -> unknown(moduleId, demo);
            };
            case "06-completable" -> switch (demo) {
                case "chaining" -> completable.chaining();
                case "combine-all-any" -> completable.combineAllAny();
                case "error-handling" -> completable.errorHandling();
                case "custom-executor" -> completable.customExecutor();
                case "fan-out" -> completable.parallelFanOut();
                case "all" -> completable.all();
                default -> unknown(moduleId, demo);
            };
            case "07-forkjoin" -> switch (demo) {
                case "recursive-sum" -> forkJoin.recursiveSum();
                case "parallel-stream" -> forkJoin.parallelVsSequentialStream();
                case "work-stealing" -> forkJoin.workStealingIdea();
                case "all" -> forkJoin.all();
                default -> unknown(moduleId, demo);
            };
            case "08-problems" -> switch (demo) {
                case "race-condition" -> problems.raceAndFix();
                case "deadlock" -> problems.deadlockDemo();
                case "livelock" -> problems.livelockDemo();
                case "starvation" -> problems.starvationDemo();
                case "all" -> problems.all();
                default -> unknown(moduleId, demo);
            };
            case "09-spring" -> switch (demo) {
                case "async" -> springAsync.asyncDemo();
                case "executor-info" -> springAsync.executorInfo();
                case "all" -> springAsync.all();
                default -> unknown(moduleId, demo);
            };
            case "10-virtual" -> switch (demo) {
                case "platform-vs-virtual" -> virtualThreads.platformVsVirtual();
                case "scale-smoke" -> virtualThreads.millionThreadsSmoke();
                case "pinning" -> virtualThreads.pinningWarning();
                case "spring-executor" -> virtualThreads.springVirtualExecutor();
                case "all" -> virtualThreads.all();
                default -> unknown(moduleId, demo);
            };
            default -> unknown(moduleId, demo);
        };
    }

    private static Map<String, Object> module(String id, String summary, List<String> demos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("summary", summary);
        m.put("runAll", "/api/modules/" + id);
        m.put("demos", demos.stream().map(d -> "/api/modules/" + id + "/" + d).toList());
        return m;
    }

    private static DemoResult unknown(String moduleId, String demo) {
        return DemoResult.of(moduleId, demo, "Unknown demo. GET /api/modules", Map.of());
    }
}
