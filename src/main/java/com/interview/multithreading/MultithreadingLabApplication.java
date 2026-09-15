package com.interview.multithreading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Multithreading Lab.
 *
 * Learning path (in order):
 *  01 Basics        → Thread, Runnable, Callable, Thread states
 *  02 Sync          → synchronized, wait/notify, volatile, ThreadLocal
 *  03 Locks         → ReentrantLock, ReadWriteLock, Condition, StampedLock
 *  04 Executors     → Thread pools, Future, ScheduledExecutor
 *  05 Concurrent    → ConcurrentHashMap, BlockingQueue, Latch, Barrier, Semaphore
 *  06 Completable   → CompletableFuture chaining & composition
 *  07 ForkJoin      → ForkJoinPool, RecursiveTask, parallel streams
 *  08 Problems      → Race, deadlock, livelock, starvation (and fixes)
 *  09 Spring Async  → @Async, custom TaskExecutor
 *  10 Virtual       → Virtual threads (Project Loom / Java 21)
 *
 * Hit GET /api/modules for the catalog, then call each demo endpoint.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MultithreadingLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultithreadingLabApplication.class, args);
    }
}
