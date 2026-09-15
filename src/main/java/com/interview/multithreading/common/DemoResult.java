package com.interview.multithreading.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard response wrapper so every demo returns consistent JSON.
 */
public record DemoResult(
        String module,
        String demo,
        String interviewTip,
        Map<String, Object> data,
        Instant timestamp
) {
    public static DemoResult of(String module, String demo, String tip, Map<String, Object> data) {
        return new DemoResult(module, demo, tip, data, Instant.now());
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
