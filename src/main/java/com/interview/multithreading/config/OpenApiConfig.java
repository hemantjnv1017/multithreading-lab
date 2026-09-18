package com.interview.multithreading.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * springdoc-openapi / Swagger UI configuration.
 * UI:  http://localhost:8080/swagger-ui/index.html
 *      http://localhost:8080/docs  (redirect)
 * JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI multithreadingLabOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multithreading Lab API")
                        .description("""
                                Interview-focused Java concurrency demos.

                                **How to use**
                                1. Open `GET /api/modules` — full learning catalog
                                2. Pick a module id (e.g. `02-sync`) → `GET /api/modules/{moduleId}`
                                3. Or run one demo → `GET /api/modules/{moduleId}/{demo}`

                                Each response has an `interviewTip` — read it aloud for revision.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Interview Prep Lab")))
                .tags(List.of(
                        new Tag().name("Learning modules")
                                .description("Catalog + run multithreading demos")
                ));
    }
}
