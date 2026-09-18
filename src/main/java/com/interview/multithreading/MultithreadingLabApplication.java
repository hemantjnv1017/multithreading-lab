package com.interview.multithreading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Multithreading Lab.
 *
 * Learning path (~3 YOE interview focus):
 *  01 Basics → 02 Sync → 03 Locks → 04 Executors → 05 Concurrent
 *  06 CompletableFuture → 07 ForkJoin → 08 Problems → 09 Spring → 10 Virtual
 *
 * Swagger: /swagger-ui/index.html  |  Catalog: GET /api/modules
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MultithreadingLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultithreadingLabApplication.class, args);
    }
}
