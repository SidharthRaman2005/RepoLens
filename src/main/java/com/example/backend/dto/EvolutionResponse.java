package com.example.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record EvolutionResponse(
        List<TimelinePhase> timeline,
        List<FeatureEvolution> features,
        List<Milestone> milestones,
        List<ArchitectureEvolution> architectureEvolution,
        ActivityStats activity) {

    public record CommitSummary(String sha, String message, String author, OffsetDateTime date,
                                int additions, int deletions, List<String> changedFiles) { }

    public record TimelinePhase(String title, OffsetDateTime startDate, OffsetDateTime endDate,
                                int commitCount, List<CommitSummary> importantCommits,
                                List<String> changedFiles, List<String> detectedTechnologies,
                                String description) { }

    public record FeatureEvolution(String featureName, OffsetDateTime firstDetectedAt,
                                   List<String> relatedCommits, List<String> relatedFiles,
                                   List<String> currentFiles, List<String> developmentStages) { }

    public record Milestone(String title, OffsetDateTime date, String commitSha,
                            String description, List<String> affectedFiles) { }

    public record ArchitectureEvolution(OffsetDateTime timestamp, String architectureType,
                                         List<String> changedRelationships, List<String> affectedFiles,
                                         String relatedCommit) { }

    public record ActivityStats(int totalCommits, int analyzedCommits, int contributors,
                                OffsetDateTime developmentStartDate, OffsetDateTime latestDevelopmentDate,
                                long developmentDurationDays, Map<String, Integer> commitsByMonth,
                                List<String> mostActiveDevelopmentPeriods) { }
}
