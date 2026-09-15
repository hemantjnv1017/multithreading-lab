package com.interview.multithreading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring async / scheduling thread pool configuration.
 *
 * Interview tip: never rely on Spring's SimpleAsyncTaskExecutor (creates a new thread every time).
 * Always define a bounded ThreadPoolTaskExecutor bean.
 */
@Configuration
public class AsyncConfig {

    public static final String APP_EXECUTOR = "appTaskExecutor";
    public static final String VIRTUAL_EXECUTOR = "virtualTaskExecutor";

    @Bean(name = APP_EXECUTOR)
    public Executor appTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("app-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * Java 21 virtual-thread-per-task executor — excellent for blocking I/O style work.
     */
    @Bean(name = VIRTUAL_EXECUTOR)
    public Executor virtualTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        // Used by Spring MVC for async request handling when configured
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        return executor;
    }
}
