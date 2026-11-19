package com.example.githubsearch.repository;

import com.example.githubsearch.entity.RepositoryEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * Small reusable WHERE-clause pieces. Each returns null when the filter is not
 * provided, and Spring Data simply ignores null specifications.
 */
public final class RepositorySpecifications {

    private RepositorySpecifications() {
    }

    public static Specification<RepositoryEntity> hasLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String wanted = language.trim().toLowerCase();
        return (root, query, cb) -> cb.equal(cb.lower(root.get("language")), wanted);
    }

    public static Specification<RepositoryEntity> hasMinStars(Integer minStars) {
        if (minStars == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("stargazersCount"), minStars);
    }
}
