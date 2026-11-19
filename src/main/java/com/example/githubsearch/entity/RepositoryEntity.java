package com.example.githubsearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * One row of the "repositories" table.
 * The UNIQUE constraint on github_id is what guarantees that the same GitHub
 * repository can never be stored twice, even if two requests race each other.
 */
@Entity
@Table(
        name = "repositories",
        uniqueConstraints = @UniqueConstraint(name = "uk_repositories_github_id", columnNames = "github_id"),
        indexes = {
                @Index(name = "idx_repositories_language", columnList = "language"),
                @Index(name = "idx_repositories_stars", columnList = "stargazers_count")
        })
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name", nullable = false, length = 512)
    private String fullName;

    @Column(length = 1000)
    private String description;

    @Column(name = "html_url", nullable = false, length = 1024)
    private String htmlUrl;

    @Column(length = 100)
    private String language;

    @Column(name = "stargazers_count", nullable = false)
    private int stargazersCount;

    @Column(name = "forks_count", nullable = false)
    private int forksCount;

    @Column(name = "open_issues_count", nullable = false)
    private int openIssuesCount;

    @Column(name = "owner_login", nullable = false)
    private String ownerLogin;

    /** When the repository was created on GitHub. */
    @Column(name = "created_at")
    private Instant createdAt;

    /** When the repository was last updated on GitHub. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** When WE last fetched/saved this repository. */
    @Column(name = "last_fetched_at", nullable = false)
    private Instant lastFetchedAt;

    /** Required by JPA (Hibernate creates entities through this constructor). */
    public RepositoryEntity() {
    }

    public Long getId() { return id; }
    public Long getGithubId() { return githubId; }
    public void setGithubId(Long githubId) { this.githubId = githubId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getStargazersCount() { return stargazersCount; }
    public void setStargazersCount(int stargazersCount) { this.stargazersCount = stargazersCount; }
    public int getForksCount() { return forksCount; }
    public void setForksCount(int forksCount) { this.forksCount = forksCount; }
    public int getOpenIssuesCount() { return openIssuesCount; }
    public void setOpenIssuesCount(int openIssuesCount) { this.openIssuesCount = openIssuesCount; }
    public String getOwnerLogin() { return ownerLogin; }
    public void setOwnerLogin(String ownerLogin) { this.ownerLogin = ownerLogin; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastFetchedAt() { return lastFetchedAt; }
    public void setLastFetchedAt(Instant lastFetchedAt) { this.lastFetchedAt = lastFetchedAt; }
}
