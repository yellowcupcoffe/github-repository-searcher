package com.example.githubsearch.service;

import com.example.githubsearch.client.dto.GitHubRepoItem;
import com.example.githubsearch.dto.PagedResponse;
import com.example.githubsearch.dto.RepositoryResponse;
import com.example.githubsearch.entity.RepositoryEntity;
import com.example.githubsearch.exception.BadRequestException;
import com.example.githubsearch.exception.ResourceNotFoundException;
import com.example.githubsearch.mapper.RepositoryMapper;
import com.example.githubsearch.repository.GitHubRepoRepository;
import com.example.githubsearch.repository.RepositorySpecifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything about repositories stored in OUR database: upsert, list, get, delete. */
@Service
public class RepositoryService {

    /** Public sort names -> entity field names. Only these are allowed (prevents sorting by arbitrary fields). */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "stars", "stargazersCount",
            "forks", "forksCount",
            "name", "name",
            "updated", "updatedAt",
            "fetched", "lastFetchedAt");

    /** Outcome of an upsert: the saved repositories plus how many rows were inserted vs updated. */
    public record UpsertResult(List<RepositoryResponse> repositories, int inserted, int updated) {
    }

    private final GitHubRepoRepository repository;

    public RepositoryService(GitHubRepoRepository repository) {
        this.repository = repository;
    }

    /**
     * Insert-or-update by GitHub id. Runs in ONE database transaction: either every
     * repository of the page is saved, or none is.
     */
    @Transactional
    public UpsertResult upsertAll(List<GitHubRepoItem> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertResult(List.of(), 0, 0);
        }

        // GitHub should not repeat an id inside one page, but if it does we keep the first one.
        Map<Long, GitHubRepoItem> uniqueItems = new LinkedHashMap<>();
        for (GitHubRepoItem item : items) {
            uniqueItems.putIfAbsent(item.id(), item);
        }

        // ONE query finds all rows that already exist (instead of one query per repository).
        Map<Long, RepositoryEntity> existing = repository.findAllByGithubIdIn(uniqueItems.keySet())
                .stream()
                .collect(Collectors.toMap(RepositoryEntity::getGithubId, Function.identity()));

        Instant fetchedAt = Instant.now();
        List<RepositoryEntity> toSave = new ArrayList<>();
        int inserted = 0;
        int updated = 0;

        for (GitHubRepoItem item : uniqueItems.values()) {
            RepositoryEntity entity = existing.get(item.id());
            if (entity == null) {
                entity = new RepositoryEntity();
                inserted++;
            } else {
                updated++;
            }
            RepositoryMapper.applyGitHubData(entity, item, fetchedAt);
            toSave.add(entity);
        }

        List<RepositoryEntity> saved = repository.saveAll(toSave);
        List<RepositoryResponse> responses = saved.stream().map(RepositoryMapper::toResponse).toList();
        return new UpsertResult(responses, inserted, updated);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RepositoryResponse> findAll(String language, Integer minStars,
                                                     String sortBy, String direction, int page, int size) {
        String sortKey = sortBy == null ? "stars" : sortBy.toLowerCase();
        String sortField = SORTABLE_FIELDS.get(sortKey);
        if (sortField == null) {
            throw new BadRequestException("sortBy must be one of: stars, forks, name, updated, fetched");
        }
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

        // Secondary sort by id keeps page boundaries stable when many rows share the same stars.
        Sort sort = Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.ASC, "id"));

        Specification<RepositoryEntity> filters = Specification
                .where(RepositorySpecifications.hasLanguage(language))
                .and(RepositorySpecifications.hasMinStars(minStars));

        Page<RepositoryEntity> result = repository.findAll(filters, PageRequest.of(page, size, sort));
        return PagedResponse.from(result.map(RepositoryMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getById(Long id) {
        return repository.findById(id)
                .map(RepositoryMapper::toResponse)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public void deleteById(Long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Repository with id " + id + " not found");
    }
}
