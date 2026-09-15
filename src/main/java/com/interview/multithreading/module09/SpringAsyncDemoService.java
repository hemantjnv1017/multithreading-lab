package com.interview.multithreading.module09;

import com.interview.multithreading.common.DemoResult;
import com.interview.multithreading.config.AsyncConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MODULE 09 — Spring @Async & scheduling
 *
 * Interview must-knows:
 * - @EnableAsync + @Async
 * - Self-invocation pitfall (JDK/CGLIB proxy) — this.asyncMethod() runs SYNCHRONOUSLY
 * - Custom executor via @Async("beanName")
 * - Return types: void | Future | CompletableFuture
 * - @Scheduled: fixedRate / fixedDelay / cron
 */
@Service
public class SpringAsyncDemoService {

    private final AtomicInteger scheduledTicks = new AtomicInteger();
    private final Executor appExecutor;
    private final SpringAsyncDemoService self; // proxy injection to demo real @Async

    public SpringAsyncDemoService(
            @Qualifier(AsyncConfig.APP_EXECUTOR) Executor appExecutor,
            @Lazy SpringAsyncDemoService self) {
        this.appExecutor = appExecutor;
        this.self = self;
    }

    @Async(AsyncConfig.APP_EXECUTOR)
    public CompletableFuture<String> asyncFetch(String id) {
        String thread = Thread.currentThread().getName();
        sleep(100);
        return CompletableFuture.completedFuture("fetched-" + id + " on " + thread);
    }

    @Async(AsyncConfig.APP_EXECUTOR)
    public void asyncFireAndForget(String msg, List<String> sink) {
        sink.add("fire-and-forget '" + msg + "' on " + Thread.currentThread().getName());
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 5_000)
    public void heartbeat() {
        scheduledTicks.incrementAndGet();
    }

    public DemoResult asyncDemo() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        String caller = Thread.currentThread().getName();

        // CORRECT: go through Spring proxy
        CompletableFuture<String> f1 = self.asyncFetch("A");
        CompletableFuture<String> f2 = self.asyncFetch("B");
        CompletableFuture.allOf(f1, f2).join();
        logs.add(f1.get());
        logs.add(f2.get());

        // WRONG: self-invocation — runs on caller thread
        CompletableFuture<String> syncMistake = this.asyncFetch("SELF-INVOKE");
        logs.add("self-invoke result=" + syncMistake.get() + " (caller was " + caller + ")");

        self.asyncFireAndForget("ping", logs);
        Thread.sleep(150);

        CompletableFuture<String> manual = new CompletableFuture<>();
        appExecutor.execute(() -> {
            logs.add("manual execute on " + Thread.currentThread().getName());
            manual.complete("manual-ok");
        });

        return DemoResult.of("09-spring", "async",
                "@Async only works through the Spring proxy. this.async() = sync. Inject self (@Lazy) or another bean.",
                DemoResult.map(
                        "logs", logs,
                        "scheduledTicksSoFar", scheduledTicks.get(),
                        "manual", manual.get(1, TimeUnit.SECONDS)
                ));
    }

    public DemoResult executorInfo() {
        ThreadPoolTaskExecutor tpe = (ThreadPoolTaskExecutor) appExecutor;
        return DemoResult.of("09-spring", "executor-info",
                "Size pools with core/max/queue + rejection policy. CallerRunsPolicy = backpressure on caller.",
                DemoResult.map(
                        "corePoolSize", tpe.getCorePoolSize(),
                        "maxPoolSize", tpe.getMaxPoolSize(),
                        "poolSize", tpe.getPoolSize(),
                        "activeCount", tpe.getActiveCount(),
                        "threadNamePrefix", "app-async-"
                ));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(asyncDemo(), executorInfo());
        return DemoResult.of("09-spring", "all",
                "Complete Module 09 — next: /api/modules/10-virtual",
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
