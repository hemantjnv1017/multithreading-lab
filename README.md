# Multithreading Lab (Java 21 + Spring Boot 3)

Hands-on Spring Boot project to learn **Java concurrency from scratch → advanced** and become **interview-ready**.

Every concept is a runnable demo with an `interviewTip` in the JSON response. Read the tip aloud like you would in an interview.

---

## Quick start

```bash
# needs JDK 21+
./mvnw spring-boot:run
# or if you have Maven installed:
mvn spring-boot:run
```

Open: [http://localhost:8080/api/modules](http://localhost:8080/api/modules)

Run a module:

```bash
curl http://localhost:8080/api/modules/01-basics
curl http://localhost:8080/api/modules/02-sync/race-condition
```

Thread dump (bonus interview skill):

```bash
curl http://localhost:8080/actuator/threaddump
```

---

## Learning path (do in order)

| # | Module | What you practice |
|---|--------|-------------------|
| 01 | `01-basics` | Thread, Runnable, Callable, states, `start` vs `run`, join/interrupt/daemon |
| 02 | `02-sync` | Monitor locks, **CAS (simple language)**, race, `synchronized`, wait/notify, `volatile`, ThreadLocal |
| 03 | `03-locks` | ReentrantLock, ReadWriteLock, Condition, StampedLock |
| 04 | `04-executors` | Thread pools, Future, ScheduledExecutor, custom `ThreadPoolExecutor` |
| 05 | `05-concurrent` | ConcurrentHashMap, BlockingQueue, Latch, Barrier, Semaphore, Phaser |
| 06 | `06-completable` | CompletableFuture chaining, combine, errors, fan-out/fan-in |
| 07 | `07-forkjoin` | ForkJoinPool, RecursiveTask, parallel streams, work-stealing |
| 08 | `08-problems` | Deadlock, livelock, starvation — and how to fix them |
| 09 | `09-spring` | `@Async`, proxy pitfall, `ThreadPoolTaskExecutor`, `@Scheduled` |
| 10 | `10-virtual` | Virtual threads (Loom), pinning, Spring virtual executor |

Source lives under:

```
src/main/java/com/interview/multithreading/moduleXX/
```

---

## How to study (recommended)

1. Hit the endpoint for a demo.
2. Read `interviewTip` and explain it in your own words (30–60 seconds).
3. Open the corresponding `*DemoService.java` — comments mark interview must-knows.
4. **Break it on purpose** (remove `volatile`, remove `synchronized`, reverse lock order) and re-run.
5. Fix it again. That is how you build real hands-on memory.

Suggested daily plan: **1–2 modules/day** → interview-ready in ~1 week.

---

## Top interview questions this lab prepares you for

- Difference between process and thread? User vs daemon thread?
- `Runnable` vs `Callable`? `start()` vs `run()`?
- Thread lifecycle states?
- What is a race condition? How do you fix it?
- What is a **monitor / intrinsic lock**? BLOCKED vs WAITING?
- What is **CAS**? How does `AtomicInteger` work without synchronized?
- `synchronized` vs `ReentrantLock`?
- `volatile` vs `AtomicInteger`?
- Why `wait()` must be in a loop?
- `CountDownLatch` vs `CyclicBarrier` vs `Semaphore`?
- How does `ConcurrentHashMap` differ from `HashMap`?
- Explain `ThreadPoolExecutor` parameters (core, max, queue, rejection).
- `thenApply` vs `thenCompose`? `allOf` vs `anyOf`?
- What causes deadlock? Four Coffman conditions? Prevention?
- What are virtual threads? When to use them? What is pinning?
- Why does `@Async` fail on self-invocation?

---

## Project layout

```
multithreading-lab/
├── pom.xml
├── README.md
└── src/main/java/com/interview/multithreading/
    ├── MultithreadingLabApplication.java
    ├── api/LearningController.java
    ├── config/AsyncConfig.java
    ├── common/DemoResult.java
    └── module01 … module10/
```

---

## Requirements

- JDK **21+**
- Maven 3.9+ (or use the wrapper after first generate)

```bash
mvn -v
java -version
```
