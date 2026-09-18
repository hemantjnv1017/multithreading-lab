package com.interview.multithreading.module07;

import com.interview.multithreading.common.DemoResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.LongStream;

/**
 * MODULE 07 — Fork/Join & parallel streams (~3 YOE)
 *
 * Must-knows: ForkJoin divide-and-conquer idea, RecursiveTask,
 * parallelStream uses commonPool — chhote data / blocking I/O pe avoid
 */
@Service
public class ForkJoinDemoService {

    public DemoResult recursiveSum() {
        long[] numbers = LongStream.rangeClosed(1, 200_000).toArray();
        long expected = (200_000L * 200_001L) / 2;

        ForkJoinPool pool = new ForkJoinPool();
        long result = pool.invoke(new SumTask(numbers, 0, numbers.length));
        pool.shutdown();

        return DemoResult.of("07-forkjoin", "recursive-sum",
                "ForkJoin: badi problem todh ke parallel. Interview: CPU-bound recursive work.",
                DemoResult.map("result", result, "expected", expected, "match", result == expected));
    }

    public DemoResult parallelVsSequentialStream() {
        List<Integer> data = new ArrayList<>();
        for (int i = 0; i < 500_000; i++) {
            data.add(i);
        }

        long t1 = System.currentTimeMillis();
        long seq = data.stream().mapToLong(i -> i * 2L).sum();
        long seqMs = System.currentTimeMillis() - t1;

        long t2 = System.currentTimeMillis();
        long par = data.parallelStream().mapToLong(i -> i * 2L).sum();
        long parMs = System.currentTimeMillis() - t2;

        return DemoResult.of("07-forkjoin", "parallel-stream",
                "parallelStream → commonPool. CPU-bound OK; blocking I/O / chhota data pe avoid.",
                DemoResult.map(
                        "sequentialSum", seq,
                        "parallelSum", par,
                        "sequentialMs", seqMs,
                        "parallelMs", parMs
                ));
    }

    public DemoResult workStealingIdea() throws Exception {
        List<String> logs = new CopyOnWriteArrayList<>();
        try (ExecutorService pool = Executors.newWorkStealingPool(4)) {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int id = i;
                tasks.add(() -> {
                    // uneven work — idle workers steal from busy queues
                    Thread.sleep(id % 2 == 0 ? 30 : 120);
                    String msg = "task-" + id + " on " + Thread.currentThread().getName();
                    logs.add(msg);
                    return msg;
                });
            }
            pool.invokeAll(tasks);
        }

        return DemoResult.of("07-forkjoin", "work-stealing",
                "Work-stealing: idle threads steal tasks from busy threads' deques → better load balance.",
                DemoResult.map("logs", logs));
    }

    public DemoResult all() throws Exception {
        List<DemoResult> parts = List.of(
                recursiveSum(),
                parallelVsSequentialStream(),
                workStealingIdea()
        );
        return DemoResult.of("07-forkjoin", "all",
                "Complete Module 07 — next: /api/modules/08-problems",
                DemoResult.map("demos", parts.stream().map(DemoResult::demo).toList(), "results", parts));
    }

    static class SumTask extends RecursiveTask<Long> {
        static final int THRESHOLD = 20_000;
        private final long[] arr;
        private final int start;
        private final int end;

        SumTask(long[] arr, int start, int end) {
            this.arr = arr;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            int length = end - start;
            if (length <= THRESHOLD) {
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += arr[i];
                }
                return sum;
            }
            int mid = start + length / 2;
            SumTask left = new SumTask(arr, start, mid);
            SumTask right = new SumTask(arr, mid, end);
            left.fork();                 // async
            long rightResult = right.compute(); // current thread does right
            long leftResult = left.join();      // wait for forked left
            return leftResult + rightResult;
        }
    }
}
