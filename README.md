# Multithreading Lab (Java 21 + Spring Boot 3)

Hands-on Spring Boot project to learn **Java concurrency from scratch → advanced** and become **interview-ready**.

Every concept is a runnable demo with an `interviewTip` in the JSON response. Read the tip aloud like you would in an interview.

---

## Quick start

```bash
# needs JDK 21+
mvn spring-boot:run
```

**Swagger UI (easiest — no Postman):**  
[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)  
Shortcuts: [http://localhost:8080/](http://localhost:8080/) or [http://localhost:8080/docs](http://localhost:8080/docs)

> App restart zaroori hai after adding Swagger (`Ctrl+C` then `mvn spring-boot:run`).

OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Raw catalog: [http://localhost:8080/api/modules](http://localhost:8080/api/modules)

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
| 01 | `01-basics` | Runnable/Callable, start vs run, **join order**, **wait vs sleep**, states |
| 02 | `02-sync` | Monitor, CAS, race, wait/notify, **spurious wakeup**, volatile, ThreadLocal |
| 03 | `03-locks` | ReentrantLock, **lock vs tryLock**, ReadWriteLock, Condition, StampedLock |
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

Suggested daily plan: **1–2 modules/day**. Content is tuned for **~3–4 years Java experience** interviews.

**Full question bank (LinkedIn / mid-level themes):** see [`INTERVIEW-QUESTIONS-3-4YOE.md`](INTERVIEW-QUESTIONS-3-4YOE.md)

---

## Top interview questions (~3 YOE focus)

- Process vs thread? `start()` vs `run()`? Runnable vs Callable?
- Race condition — kaise fix?
- Monitor / synchronized? BLOCKED vs WAITING?
- CAS / AtomicInteger? `lock()` vs `tryLock()`?
- `volatile` vs Atomic? wait bina synchronized? Why `while` not `if`?
- Latch vs Barrier vs Semaphore? CHM vs HashMap?
- ThreadPoolExecutor: core, max, queue, rejection (high level)
- CompletableFuture: thenApply vs thenCompose; allOf
- Deadlock kya / kaise avoid (lock order, tryLock)?
- Virtual threads kab? Pinning kya (basic)?
- `@Async` self-invocation kyun fail?

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
