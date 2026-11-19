package com.example.githubsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GitHubSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHubSearchApplication.class, args);
    }
}
