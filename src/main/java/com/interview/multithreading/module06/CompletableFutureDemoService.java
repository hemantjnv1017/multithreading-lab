package com.interview.multithreading.module06;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * MODULE 06 — CompletableFuture (~3 YOE)
 *
 * Must-knows: supplyAsync, thenApply vs thenCompose, thenCombine,
 * allOf/anyOf, exceptionally, custom Executor (commonPool pe overload mat daalo)
 */
@Service
public class CompletableFutureDemoService {

    public DemoResult chaining() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();

        String result = CompletableFuture
                .supplyAsync(() -> {
                    logs.add("supplyAsync on " + Thread.currentThread().getName());
                    return "hello";
                })
                .thenApply(s -> {
                    logs.add("thenApply upper");
                    return s.toUpperCase();
                })
                .thenApply(s -> s + " WORLD")
                .thenCompose(s -> CompletableFuture.supplyAsync(() -> {
                    logs.add("thenCompose nested async");
                    return s + "!";
                }))
                .get(2, TimeUnit.SECONDS);

        return DemoResult.of("06-completable", "chaining",
                "thenApply = sync transform. thenCompose = flatten nested CompletableFuture (like flatMap).",
                DemoResult.map("result", result, "logs", logs));
    }

    public DemoResult combineAllAny() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();

        CompletableFuture<String> user = CompletableFuture.supplyAsync(() -> {
            sleep(80);
            logs.add("fetched user");
            return "Hemant";
        });
        CompletableFuture<Integer> score = CompletableFuture.supplyAsync(() -> {
            sleep(120);
            logs.add("fetched score");
            return 95;
        });
        CompletableFuture<String> badge = CompletableFuture.supplyAsync(() -> {
            sleep(40);
            logs.add("fetched badge");
            return "GOLD";
        });

        String combined = user.thenCombine(score, (u, s) -> u + " scored " + s).get();

        CompletableFuture<Void> all = CompletableFuture.allOf(user, score, badge);
        all.join();
        List<Object> allValues = List.of(user.join(), score.join(), badge.join());

        Object first = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> { sleep(200); return "slow"; }),
                CompletableFuture.supplyAsync(() -> { sleep(30); return "fast"; })
        ).get();

        return DemoResult.of("06-completable", "combine-all-any",
                "thenCombine = 2 results. allOf = wait all (Void). anyOf = first completed.",
                DemoResult.map("combined", combined, "allValues", allValues, "anyOf", first, "logs", logs));
    }

    public DemoResult errorHandling() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();

        String recovered = CompletableFuture
                .supplyAsync(() -> {
                    if (true) throw new RuntimeException("boom");
                    return "ok";
                })
                .exceptionally(ex -> {
                    logs.add("exceptionally: " + ex.getCause().getMessage());
                    return "fallback";
                })
                .get();

        String handled = CompletableFuture
                .supplyAsync(() -> "data")
                .handle((val, ex) -> {
                    logs.add("handle val=" + val + " ex=" + ex);
                    return ex == null ? val + "-ok" : "err";
                })
                .get();

        CompletableFuture
                .supplyAsync(() -> 1)
                .whenComplete((v, ex) -> logs.add("whenComplete side-effect v=" + v))
                .join();

        return DemoResult.of("06-completable", "error-handling",
                "exceptionally → recover. handle → both success/failure. whenComplete → side-effects only.",
                DemoResult.map("recovered", recovered, "handled", handled, "logs", logs));
    }

    public DemoResult customExecutor() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        try (ExecutorService dedicated = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "cf-dedicated");
            t.setDaemon(true);
            return t;
        })) {
            String result = CompletableFuture
                    .supplyAsync(() -> {
                        logs.add("ran on " + Thread.currentThread().getName());
                        return 10;
                    }, dedicated)
                    .thenApplyAsync(n -> {
                        logs.add("thenApplyAsync on " + Thread.currentThread().getName());
                        return n * 2;
                    }, dedicated)
                    .thenApply(n -> "answer=" + n)
                    .get();

            return DemoResult.of("06-completable", "custom-executor",
                    "Pass your Executor to supplyAsync/thenApplyAsync — don't starve the common ForkJoinPool.",
                    DemoResult.map("result", result, "logs", logs));
        }
    }

    public DemoResult parallelFanOut() throws Exception {
        List<String> ids = List.of("o1", "o2", "o3", "o4");
        long start = System.currentTimeMillis();

        List<CompletableFuture<String>> futures = ids.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    sleep(100);
                    return "order-" + id + "-processed";
                }))
                .toList();

        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        long elapsed = System.currentTimeMillis() - start;

        return DemoResult.of("06-completable", "fan-out",
                "Fan-out/fan-in with CF is a very common interview + real-world pattern.",
                DemoResult.map("results", results, "elapsedMs", elapsed,
                        "note", "4x100ms sequential≈400ms; parallel≈100ms + overhead"));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                chaining(),
                combineAllAny(),
                errorHandling(),
                customExecutor(),
                parallelFanOut()
        );
        return DemoResult.of("06-completable", "all",
                "Complete Module 06 — next: /api/modules/07-forkjoin",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
