package com.example.backend.github.dto;

import java.util.List;

public record GitHubCommitDetail(
        String sha,
        GitHubCommitSummary.GitHubCommit commit,
        GitHubStats stats,
        List<GitHubFileChange> files) {

    public record GitHubStats(Integer additions, Integer deletions, Integer total) { }

    public record GitHubFileChange(String filename, String status, Integer additions,
                                   Integer deletions, Integer changes) { }
}