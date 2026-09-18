# Multithreading Interview Questions — 3–4 Years Experience

Curated from common LinkedIn / mid-level Java backend interview themes (2024–2025).  
Lab demos are tuned to these — practice aloud in **30–60 seconds** each.

Swagger: http://localhost:8080/swagger-ui/index.html

---

## A. Thread basics (very frequent)

| # | Question | Lab demo |
|---|----------|----------|
| 1 | Process vs Thread? | `01-basics` tips |
| 2 | Ways to create a thread? Prefer Runnable why? | `/api/modules/01-basics/extend-thread` |
| 3 | `Runnable` vs `Callable`? | `/api/modules/01-basics/runnable-vs-callable` |
| 4 | `start()` vs `run()`? | `/api/modules/01-basics/start-vs-run` |
| 5 | Thread states (BLOCKED vs WAITING vs TIMED_WAITING)? | `/api/modules/01-basics/thread-states` |
| 6 | **T1 then T2 then T3 kaise ensure?** | `/api/modules/01-basics/join-ordering` |
| 7 | **`wait()` vs `sleep()`?** | `/api/modules/01-basics/wait-vs-sleep` |
| 8 | What is a daemon thread? | `/api/modules/01-basics/join-interrupt-daemon` |
| 9 | How does `interrupt()` work? Why restore interrupt flag? | same |

---

## B. Sync, visibility, races (core round)

| # | Question | Lab demo |
|---|----------|----------|
| 10 | What is a race condition? How fix? | `/api/modules/02-sync/race-condition` |
| 11 | How does `synchronized` / monitor work? | `/api/modules/02-sync/monitor-locks` |
| 12 | `synchronized` method vs block? Why private lock object? | `/api/modules/02-sync/synchronized-scopes` |
| 13 | What is `volatile`? Does it make `i++` safe? | `/api/modules/02-sync/volatile-visibility` |
| 14 | What is CAS? How does `AtomicInteger` work? | `/api/modules/02-sync/cas` |
| 15 | Can you call `wait`/`notify` without `synchronized`? | `/api/modules/02-sync/wait-notify-needs-sync` |
| 16 | Explain `wait` / `notify` / `notifyAll` | `/api/modules/02-sync/wait-notify` |
| 17 | Why `while (!condition) wait()` not `if`? Spurious wakeup? | `/api/modules/02-sync/spurious-wakeup` |
| 18 | What is `ThreadLocal`? Memory leak in pools? | `/api/modules/02-sync/thread-local` |
| 19 | Concurrency vs Parallelism? | (concept — say in own words) |

---

## C. Locks (3–4 YOE expect tryLock)

| # | Question | Lab demo |
|---|----------|----------|
| 20 | `synchronized` vs `ReentrantLock`? | `/api/modules/03-locks/reentrant-lock` |
| 21 | **`lock()` vs `tryLock()` vs `tryLock(timeout)`?** | `/api/modules/03-locks/lock-vs-trylock` |
| 22 | What is `ReadWriteLock`? When use? | `/api/modules/03-locks/read-write-lock` |
| 23 | What is `Condition` (with Lock)? | `/api/modules/03-locks/condition` |
| 24 | Optimistic vs pessimistic locking (basic)? | `/api/modules/03-locks/stamped-lock` (awareness) |

---

## D. Executors & pools (almost every backend interview)

| # | Question | Lab demo |
|---|----------|----------|
| 25 | Why `ExecutorService` over `new Thread()`? | `/api/modules/04-executors/pool-types` |
| 26 | Explain `ThreadPoolExecutor` params (core, max, queue, rejection) | `/api/modules/04-executors/custom-tpe` |
| 27 | Why avoid `Executors.newFixedThreadPool` in prod? | same (unbounded queue) |
| 28 | `Future.get()`, `cancel()`? | `/api/modules/04-executors/future` |
| 29 | `shutdown()` vs `shutdownNow()`? | tips in module 04 |
| 30 | `invokeAll` vs `invokeAny`? | `/api/modules/04-executors/invoke-all-any` |

---

## E. Concurrent utilities

| # | Question | Lab demo |
|---|----------|----------|
| 31 | `HashMap` vs `ConcurrentHashMap`? | `/api/modules/05-concurrent/concurrent-hash-map` |
| 32 | Producer–consumer — how? Prefer what? | `/api/modules/05-concurrent/blocking-queue` |
| 33 | `CountDownLatch` vs `CyclicBarrier`? | latch + barrier demos |
| 34 | `Semaphore` — types & use cases (pool / rate limit)? | `/api/modules/05-concurrent/semaphore` |
| 35 | `CopyOnWriteArrayList` kab? | (concept — read-heavy, rare writes) |

---

## F. CompletableFuture (Java 8+ — hot topic)

| # | Question | Lab demo |
|---|----------|----------|
| 36 | `Future` vs `CompletableFuture`? | module 06 tip |
| 37 | `thenApply` vs `thenCompose`? | `/api/modules/06-completable/chaining` |
| 38 | `thenCombine` / `allOf` / `anyOf`? | `/api/modules/06-completable/combine-all-any` |
| 39 | How handle errors (`exceptionally`)? | `/api/modules/06-completable/error-handling` |
| 40 | Why pass custom `Executor` (not only commonPool)? | `/api/modules/06-completable/custom-executor` |
| 41 | Fan-out parallel API calls pattern? | `/api/modules/06-completable/fan-out` |

---

## G. Fork/Join & parallel streams

| # | Question | Lab demo |
|---|----------|----------|
| 42 | What is ForkJoin / work-stealing (basic)? | `/api/modules/07-forkjoin/recursive-sum` |
| 43 | When NOT to use `parallelStream`? | `/api/modules/07-forkjoin/parallel-stream` |

---

## H. Problems & production scenarios

| # | Question | Lab demo |
|---|----------|----------|
| 44 | What is deadlock? How prevent? | `/api/modules/08-problems/deadlock` |
| 45 | Livelock / starvation (basic)? | livelock + starvation demos |
| 46 | How debug stuck threads? (thread dump / actuator) | `/actuator/threaddump` |
| 47 | Thread-safe singleton approaches? | (explain: enum / holder / sync) — no separate demo |

---

## I. Spring + Virtual threads (modern stack)

| # | Question | Lab demo |
|---|----------|----------|
| 48 | `@Async` kaise kaam? Self-invocation kyun fail? | `/api/modules/09-spring/async` |
| 49 | Custom `ThreadPoolTaskExecutor` kyun? | `/api/modules/09-spring/executor-info` |
| 50 | Virtual threads vs platform? Kab use? Pinning? | `/api/modules/10-virtual/*` |

---

## Quick verbal answers (cheat)

1. **wait vs sleep:** sleep lock nahi chhodta; wait monitor release karta hai + sync chahiye.  
2. **T1→T2→T3:** `t1.start(); t1.join(); t2.start(); t2.join(); ...`  
3. **Race:** shared mutable + no sync → lost updates; fix sync/lock/atomic.  
4. **tryLock:** fail-fast / timeout; `lock()` forever wait.  
5. **Latch vs Barrier:** Latch one-shot count→0; Barrier parties meet, reusable.  
6. **CHM:** concurrent reads/writes safe; use `merge`/`compute`.  
7. **TPE:** core → queue fill → max threads → reject. Bound the queue.  
8. **thenApply vs thenCompose:** map vs flatMap (nested CF).  
9. **Deadlock fix:** global lock order **or** `tryLock(timeout)`.  
10. **@Async pitfall:** `this.async()` proxy skip → sync run.

---

## How to use this file

1. Pick 10 questions/day from sections A→I.  
2. Hit the linked demo in Swagger.  
3. Close laptop and answer in 1 minute without notes.  
4. Weak topics → re-run demo + read `interviewTip` / short Q&A in JSON.

Depth target: **3–4 years Java backend** — practical clarity over JVM internals.
