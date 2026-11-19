package com.example.githubsearch.repository;

import com.example.githubsearch.entity.RepositoryEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring generates the implementation of this interface at startup.
 * JpaRepository gives save/findById/delete/findAll(Pageable);
 * JpaSpecificationExecutor adds findAll(Specification, Pageable) for optional filters.
 */
public interface GitHubRepoRepository
        extends JpaRepository<RepositoryEntity, Long>, JpaSpecificationExecutor<RepositoryEntity> {

    /** One query for a whole page of search results (avoids one query per repository). */
    List<RepositoryEntity> findAllByGithubIdIn(Collection<Long> githubIds);
}
