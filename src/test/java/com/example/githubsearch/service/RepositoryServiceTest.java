package com.example.githubsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.githubsearch.TestData;
import com.example.githubsearch.dto.PagedResponse;
import com.example.githubsearch.dto.RepositoryResponse;
import com.example.githubsearch.entity.RepositoryEntity;
import com.example.githubsearch.exception.BadRequestException;
import com.example.githubsearch.exception.ResourceNotFoundException;
import com.example.githubsearch.mapper.RepositoryMapper;
import com.example.githubsearch.repository.GitHubRepoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * The database repository is mocked, so these tests check ONLY our logic (upsert decisions,
 * sort mapping, not-found handling) and run in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class RepositoryServiceTest {

    @Mock
    private GitHubRepoRepository repository;

    @InjectMocks
    private RepositoryService service;

    private RepositoryEntity existingEntity(long githubId, String fullName, int stars) {
        RepositoryEntity entity = new RepositoryEntity();
        RepositoryMapper.applyGitHubData(entity, TestData.item(githubId, fullName, "Java", stars),
                Instant.parse("2020-01-01T00:00:00Z"));
        return entity;
    }

    // ---------- upsert ----------

    @Test
    void upsertInsertsRepositoriesThatAreNotStoredYet() {
        when(repository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RepositoryService.UpsertResult result = service.upsertAll(List.of(
                TestData.item(1L, "a/one", "Java", 10),
                TestData.item(2L, "a/two", "Go", 20)));

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.repositories()).extracting(RepositoryResponse::fullName)
                .containsExactly("a/one", "a/two");
    }

    @Test
    void upsertUpdatesTheExistingEntityInsteadOfCreatingADuplicate() {
        RepositoryEntity stored = existingEntity(1L, "a/one", 10);
        when(repository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of(stored));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RepositoryService.UpsertResult result = service.upsertAll(List.of(TestData.item(1L, "a/one", "Java", 500)));

        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        // the SAME entity object was modified, not replaced by a new one
        assertThat(stored.getStargazersCount()).isEqualTo(500);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RepositoryEntity>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).containsExactly(stored);
    }

    @Test
    void upsertHandlesAMixOfNewAndExistingAndIgnoresDuplicateIdsInTheSameBatch() {
        RepositoryEntity stored = existingEntity(1L, "a/one", 10);
        when(repository.findAllByGithubIdIn(anyCollection())).thenReturn(List.of(stored));
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RepositoryService.UpsertResult result = service.upsertAll(List.of(
                TestData.item(1L, "a/one", "Java", 11),
                TestData.item(2L, "a/two", "Java", 22),
                TestData.item(2L, "a/two", "Java", 23)));   // same GitHub id twice

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.repositories()).hasSize(2);
    }

    @Test
    void upsertWithNoItemsDoesNotTouchTheDatabase() {
        RepositoryService.UpsertResult result = service.upsertAll(List.of());

        assertThat(result.repositories()).isEmpty();
        verify(repository, never()).saveAll(any());
    }

    // ---------- list with filters / sorting / paging ----------

    @Test
    void findAllMapsPublicSortNameToEntityFieldAndAppliesPaging() {
        when(repository.findAll(ArgumentMatchers.<Specification<RepositoryEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingEntity(1L, "a/one", 10)), PageRequest.of(2, 5), 11));

        PagedResponse<RepositoryResponse> result = service.findAll("Java", 100, "stars", "desc", 2, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(ArgumentMatchers.<Specification<RepositoryEntity>>any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("stargazersCount").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id")).isNotNull(); // stable tie-breaker
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void findAllSupportsAscendingDirectionAndOtherSortFields() {
        when(repository.findAll(ArgumentMatchers.<Specification<RepositoryEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findAll(null, null, "UPDATED", "ASC", 0, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(ArgumentMatchers.<Specification<RepositoryEntity>>any(), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("updatedAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findAllRejectsUnknownSortField() {
        assertThatThrownBy(() -> service.findAll(null, null, "password", "asc", 0, 10))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).findAll(ArgumentMatchers.<Specification<RepositoryEntity>>any(), any(Pageable.class));
    }

    // ---------- get / delete ----------

    @Test
    void getByIdReturnsTheStoredRepository() {
        when(repository.findById(7L)).thenReturn(Optional.of(existingEntity(1L, "a/one", 10)));

        assertThat(service.getById(7L).fullName()).isEqualTo("a/one");
    }

    @Test
    void getByIdThrowsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Repository with id 99 not found");
    }

    @Test
    void deleteRemovesAnExistingRepository() {
        when(repository.existsById(7L)).thenReturn(true);

        service.deleteById(7L);

        verify(repository).deleteById(7L);
    }

    @Test
    void deleteThrowsNotFoundAndDeletesNothingForUnknownId() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteById(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
