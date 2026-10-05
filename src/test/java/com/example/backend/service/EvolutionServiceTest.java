package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.github.GitHubClient;
import com.example.backend.github.dto.GitHubCommitDetail;
import com.example.backend.github.dto.GitHubCommitSummary;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EvolutionServiceTest {

    @Test
    void retrievesPaginatedCommitsBuildsTimelineAndCachesResult() {
        GitHubClient client = mock(GitHubClient.class);
        RepositoryRepository repositories = mock(RepositoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        FileRepository files = mock(FileRepository.class);
        CodeEntityRepository entities = mock(CodeEntityRepository.class);
        DependencyRepository dependencies = mock(DependencyRepository.class);
        AnalysisRepository analyses = mock(AnalysisRepository.class);
        User user = mock(User.class);
        com.example.backend.entity.Repository repository = mock(com.example.backend.entity.Repository.class);
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(7L);
        when(repositories.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(repository));
        when(repository.getOwner()).thenReturn("owner");
        when(repository.getName()).thenReturn("repo");
        when(files.findAllByRepositoryIdOrderByPathAsc(12L)).thenReturn(List.of());
        when(entities.findAllByFileRepositoryIdOrderByIdAsc(12L)).thenReturn(List.of());
        when(dependencies.findAllBySourceEntityFileRepositoryId(12L)).thenReturn(List.of());
        when(analyses.findTopByRepositoryIdOrderByCreatedAtDesc(12L)).thenReturn(Optional.empty());

        OffsetDateTime first = OffsetDateTime.parse("2024-01-01T10:00:00Z");
        OffsetDateTime second = OffsetDateTime.parse("2024-02-01T10:00:00Z");
        GitHubCommitSummary firstSummary = summary("first", "project setup", first, "pom.xml");
        GitHubCommitSummary secondSummary = summary("second", "add login API", second, "src/AuthController.java");
        List<GitHubCommitSummary> firstPage = new ArrayList<>();
        firstPage.add(firstSummary);
        for (int index = 0; index < 99; index++) {
            firstPage.add(summary("setup-" + index, "project setup", first, "pom.xml"));
        }
        when(client.getCommits("owner", "repo", 1, 100)).thenReturn(firstPage);
        when(client.getCommits("owner", "repo", 2, 100)).thenReturn(List.of(secondSummary));
        when(client.getCommit(anyString(), anyString(), anyString())).thenAnswer(invocation ->
            "second".equals(invocation.getArgument(2))
                ? detail(secondSummary, "src/AuthController.java")
                : detail(firstSummary, "pom.xml"));

        EvolutionService service = new EvolutionService(client, repositories, users, files, entities, dependencies, analyses, 500);
        var response = service.evolution(12L, "OWNER@EXAMPLE.COM");
        var cachedResponse = service.evolution(12L, "owner@example.com");

        assertThat(response.activity().totalCommits()).isEqualTo(101);
        assertThat(response.activity().developmentStartDate()).isEqualTo(first);
        assertThat(response.activity().latestDevelopmentDate()).isEqualTo(second);
        assertThat(response.timeline()).extracting(phase -> phase.title())
                .containsExactly("Project Initialization", "Authentication & Security");
        assertThat(response.milestones()).extracting(milestone -> milestone.title())
                .contains("Project initialized", "Authentication introduced");
        assertThat(cachedResponse).isSameAs(response);
        verify(client, times(1)).getCommits("owner", "repo", 1, 100);
        verify(client, times(1)).getCommits("owner", "repo", 2, 100);
    }

    private GitHubCommitSummary summary(String sha, String message, OffsetDateTime date, String path) {
        var person = new GitHubCommitSummary.GitHubPerson("Contributor", "person@example.com", date);
        return new GitHubCommitSummary(sha, new GitHubCommitSummary.GitHubCommit(message, person, person));
    }

    private GitHubCommitDetail detail(GitHubCommitSummary summary, String path) {
        return new GitHubCommitDetail(summary.sha(), summary.commit(), new GitHubCommitDetail.GitHubStats(1, 0, 1),
                List.of(new GitHubCommitDetail.GitHubFileChange(path, "added", 1, 0, 1)));
    }
}