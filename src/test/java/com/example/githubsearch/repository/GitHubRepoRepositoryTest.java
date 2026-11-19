package com.example.githubsearch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.githubsearch.TestData;
import com.example.githubsearch.entity.RepositoryEntity;
import com.example.githubsearch.mapper.RepositoryMapper;
import com.example.githubsearch.service.RepositoryService;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

/**
 * Talks to a REAL PostgreSQL database (the TEST database, never the development one).
 *
 * - @DataJpaTest starts only the JPA layer (entities, repositories, Hibernate, transactions).
 * - replace = NONE tells Spring NOT to swap our database for an embedded one.
 * - @ActiveProfiles("test") loads src/test/resources/application-test.yml (TEST_DB_* settings).
 * - Every test runs inside a transaction that Spring ROLLS BACK at the end, so nothing is left behind.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RepositoryService.class)
class GitHubRepoRepositoryTest {

    @Autowired
    private GitHubRepoRepository repository;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    /**
     * Safety guard: refuse to run unless we are connected to a database whose name ends with "_test".
     * It runs before anything else, so a wrong TEST_DB_NAME can never lead to tests writing into the
     * development database. The delete below is also rolled back, but it makes each test independent
     * of rows that may already exist in the test database.
     */
    @BeforeEach
    void useOnlyTheTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog())
                    .as("Repository tests must run against a database whose name ends with '_test'")
                    .endsWith("_test");
        }
        repository.deleteAllInBatch();
    }

    private RepositoryEntity entity(long githubId, String fullName, String language, int stars) {
        RepositoryEntity entity = new RepositoryEntity();
        RepositoryMapper.applyGitHubData(entity, TestData.item(githubId, fullName, language, stars), Instant.now());
        return entity;
    }

    // ---------- unique constraint ----------

    @Test
    void postgresRejectsASecondRowWithTheSameGithubId() {
        repository.saveAndFlush(entity(1L, "acme/first", "Java", 10));

        assertThatThrownBy(() -> repository.saveAndFlush(entity(1L, "acme/second", "Java", 5)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- upsert against the real database ----------

    @Test
    void upsertingTheSameGithubIdTwiceKeepsOneRowAndUpdatesIt() {
        RepositoryService.UpsertResult first =
                repositoryService.upsertAll(List.of(TestData.item(42L, "acme/lib", "Java", 10)));
        entityManager.flush();
        entityManager.clear(); // forget cached objects so the next read really hits PostgreSQL

        RepositoryService.UpsertResult second =
                repositoryService.upsertAll(List.of(TestData.item(42L, "acme/lib", "Java", 99)));
        entityManager.flush();
        entityManager.clear();

        assertThat(first.inserted()).isEqualTo(1);
        assertThat(second.updated()).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        RepositoryEntity stored = repository.findAllByGithubIdIn(List.of(42L)).get(0);
        assertThat(stored.getStargazersCount()).isEqualTo(99);
        assertThat(second.repositories().get(0).id()).isEqualTo(first.repositories().get(0).id());
    }

    @Test
    void upsertWithRepeatedIdInOneBatchStoresItOnce() {
        repositoryService.upsertAll(List.of(
                TestData.item(1L, "a/one", "Java", 1),
                TestData.item(1L, "a/one", "Java", 2),
                TestData.item(2L, "a/two", "Go", 3)));
        entityManager.flush();

        assertThat(repository.count()).isEqualTo(2);
    }

    // ---------- queries ----------

    @Test
    void findAllByGithubIdInReturnsOnlyMatchingRows() {
        repository.saveAll(List.of(entity(1L, "a/one", "Java", 1), entity(2L, "a/two", "Go", 2),
                entity(3L, "a/three", "Java", 3)));

        List<RepositoryEntity> found = repository.findAllByGithubIdIn(List.of(1L, 3L, 99L));

        assertThat(found).extracting(RepositoryEntity::getGithubId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void specificationsFilterByLanguageIgnoringCaseAndMinStars() {
        repository.saveAll(List.of(
                entity(1L, "a/low", "Java", 5),
                entity(2L, "a/high", "Java", 500),
                entity(3L, "a/other", "Go", 900)));

        Specification<RepositoryEntity> spec = Specification
                .where(RepositorySpecifications.hasLanguage("java"))
                .and(RepositorySpecifications.hasMinStars(100));
        Page<RepositoryEntity> page =
                repository.findAll(spec, PageRequest.of(0, 10, Sort.by("stargazersCount").descending()));

        assertThat(page.getContent()).extracting(RepositoryEntity::getFullName).containsExactly("a/high");
    }

    @Test
    void nullFiltersAreIgnoredAndSortingAndPagingWork() {
        repository.saveAll(List.of(entity(1L, "a/x", "Java", 1), entity(2L, "a/y", "Go", 2),
                entity(3L, "a/z", "Rust", 3)));

        Specification<RepositoryEntity> spec = Specification
                .where(RepositorySpecifications.hasLanguage(null))
                .and(RepositorySpecifications.hasMinStars(null));
        Page<RepositoryEntity> page =
                repository.findAll(spec, PageRequest.of(0, 2, Sort.by("stargazersCount").descending()));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(RepositoryEntity::getFullName).containsExactly("a/z", "a/y");
    }

    @Test
    void serviceListFiltersSortsAndPagesAgainstPostgres() {
        repository.saveAll(List.of(
                entity(1L, "a/one", "Java", 10),
                entity(2L, "a/two", "Java", 300),
                entity(3L, "a/three", "Java", 200),
                entity(4L, "a/four", "Go", 999)));

        var page = repositoryService.findAll("Java", 100, "stars", "desc", 0, 1);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).extracting(r -> r.fullName()).containsExactly("a/two");
    }
}
