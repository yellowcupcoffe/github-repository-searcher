package com.example.githubsearch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at the top of Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("GitHub Repository Searcher API")
                .version("1.0.0")
                .description("Search GitHub repositories, store them in PostgreSQL (upsert by GitHub id) "
                        + "and query the stored data with filters, sorting and pagination."));
    }
}
