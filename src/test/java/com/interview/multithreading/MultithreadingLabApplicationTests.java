package com.interview.multithreading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultithreadingLabApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void catalogLoads() {
        ResponseEntity<Map> response = rest.getForEntity("/api/modules", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKey("modules");
    }

    @Test
    void raceConditionDemoWorks() {
        ResponseEntity<Map> response =
                rest.getForEntity("/api/modules/02-sync/race-condition", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKeys("module", "demo", "interviewTip", "data");
    }
}
