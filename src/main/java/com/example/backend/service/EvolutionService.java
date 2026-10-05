package com.example.backend.service;

import com.example.backend.dto.EvolutionResponse;
import com.example.backend.entity.Analysis;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.Dependency;
import com.example.backend.entity.File;
import com.example.backend.entity.User;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.github.GitHubClient;
import com.example.backend.github.dto.GitHubCommitDetail;
import com.example.backend.github.dto.GitHubCommitSummary;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EvolutionService {

    private static final int PAGE_SIZE = 100;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Pattern SOURCE_FILE = Pattern.compile(".*\\.(java|kt|groovy|js|jsx|ts|tsx|py|go|rs|rb|php|cs|cpp|c|h)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> IGNORED_PATH_PARTS = Set.of("node_modules", "target", "build", "dist", ".git", "vendor");

    private final GitHubClient gitHubClient;
    private final RepositoryRepository repositoryRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final CodeEntityRepository codeEntityRepository;
    private final DependencyRepository dependencyRepository;
    private final AnalysisRepository analysisRepository;
    private final int maxHistoryCommits;
    private final ConcurrentMap<Long, CachedEvolution> cache = new ConcurrentHashMap<>();

    public EvolutionService(GitHubClient gitHubClient, RepositoryRepository repositoryRepository,
            UserRepository userRepository, FileRepository fileRepository,
            CodeEntityRepository codeEntityRepository, DependencyRepository dependencyRepository,
            AnalysisRepository analysisRepository,
            @Value("${github.analysis.max-commits:500}") int maxHistoryCommits) {
        this.gitHubClient = gitHubClient;
        this.repositoryRepository = repositoryRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.codeEntityRepository = codeEntityRepository;
        this.dependencyRepository = dependencyRepository;
        this.analysisRepository = analysisRepository;
        this.maxHistoryCommits = maxHistoryCommits;
    }

    @Transactional(readOnly = true)
    public EvolutionResponse evolution(Long repositoryId, String email) {
        var repository = ownedRepository(repositoryId, email);
        CachedEvolution cached = cache.get(repositoryId);
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(OffsetDateTime.now())) {
            return cached.response();
        }

        List<CommitEvidence> commits = retrieveCommits(repository.getOwner(), repository.getName());
        List<File> currentFiles = fileRepository.findAllByRepositoryIdOrderByPathAsc(repositoryId);
        List<CodeEntity> currentEntities = codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId);
        List<Dependency> currentDependencies = dependencyRepository.findAllBySourceEntityFileRepositoryId(repositoryId);
        String architecture = analysisRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repositoryId)
                .map(Analysis::getArchitecture).orElse("Unknown architecture");
        EvolutionResponse response = buildResponse(commits, currentFiles, currentEntities, currentDependencies, architecture);
        cache.put(repositoryId, new CachedEvolution(OffsetDateTime.now(), response));
        return response;
    }

    @Transactional(readOnly = true)
    public EvolutionResponse.TimelinePhase timeline(Long repositoryId, String email) {
        return evolution(repositoryId, email).timeline().stream().findFirst().orElse(null);
    }

    public List<EvolutionResponse.TimelinePhase> timelinePhases(Long repositoryId, String email) {
        return evolution(repositoryId, email).timeline();
    }

    public List<EvolutionResponse.FeatureEvolution> features(Long repositoryId, String email) {
        return evolution(repositoryId, email).features();
    }

    public List<EvolutionResponse.Milestone> milestones(Long repositoryId, String email) {
        return evolution(repositoryId, email).milestones();
    }

    public List<EvolutionResponse.ArchitectureEvolution> architectureEvolution(Long repositoryId, String email) {
        return evolution(repositoryId, email).architectureEvolution();
    }

    public EvolutionResponse.ActivityStats activity(Long repositoryId, String email) {
        return evolution(repositoryId, email).activity();
    }

    private com.example.backend.entity.Repository ownedRepository(Long repositoryId, String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(InvalidCredentialsException::new);
        return repositoryRepository.findByIdAndUserId(repositoryId, user.getId())
                .orElseThrow(RepositoryNotFoundException::new);
    }

    private List<CommitEvidence> retrieveCommits(String owner, String name) {
        List<GitHubCommitSummary> summaries = new ArrayList<>();
        for (int page = 1; summaries.size() < maxHistoryCommits; page++) {
            List<GitHubCommitSummary> pageItems = gitHubClient.getCommits(owner, name, page, PAGE_SIZE);
            if (pageItems == null || pageItems.isEmpty()) break;
            summaries.addAll(pageItems);
            if (pageItems.size() < PAGE_SIZE) break;
        }
        List<CommitEvidence> result = new ArrayList<>();
        Set<String> seenShas = new HashSet<>();
        for (GitHubCommitSummary summary : summaries.stream().limit(maxHistoryCommits).toList()) {
            if (summary == null || summary.sha() == null || !seenShas.add(summary.sha())) continue;
            GitHubCommitDetail detail = gitHubClient.getCommit(owner, name, summary.sha());
            result.add(toEvidence(summary, detail));
        }
        return result.stream().filter(commit -> commit.date() != null)
                .sorted(Comparator.comparing(CommitEvidence::date)).toList();
    }

    private CommitEvidence toEvidence(GitHubCommitSummary summary, GitHubCommitDetail detail) {
        GitHubCommitSummary.GitHubCommit commit = detail == null || detail.commit() == null ? summary.commit() : detail.commit();
        OffsetDateTime date = commit == null ? null : commitDate(commit);
        String message = commit == null || commit.message() == null ? "(no commit message)" : commit.message().split("\\R", 2)[0].trim();
        String author = commit == null || commit.author() == null ? "Unknown" : commit.author().name();
        List<String> files = detail == null || detail.files() == null ? List.of() : detail.files().stream()
                .map(GitHubCommitDetail.GitHubFileChange::filename).filter(path -> path != null && relevantPath(path)).toList();
        int additions = detail == null || detail.stats() == null || detail.stats().additions() == null ? 0 : detail.stats().additions();
        int deletions = detail == null || detail.stats() == null || detail.stats().deletions() == null ? 0 : detail.stats().deletions();
        return new CommitEvidence(summary.sha(), message, author, date, additions, deletions, files);
    }

    private OffsetDateTime commitDate(GitHubCommitSummary.GitHubCommit commit) {
        if (commit.committer() != null && commit.committer().date() != null) return commit.committer().date();
        return commit.author() == null ? null : commit.author().date();
    }

    private EvolutionResponse buildResponse(List<CommitEvidence> commits, List<File> currentFiles,
            List<CodeEntity> currentEntities, List<Dependency> currentDependencies, String architecture) {
        Map<String, List<CommitEvidence>> grouped = commits.stream().collect(Collectors.groupingBy(
                commit -> phaseFor(commit), LinkedHashMap::new, Collectors.toList()));
        List<EvolutionResponse.TimelinePhase> timeline = grouped.entrySet().stream().map(entry -> phase(entry.getKey(), entry.getValue())).toList();
        List<EvolutionResponse.FeatureEvolution> features = featureEvolution(commits, currentFiles, currentEntities);
        List<EvolutionResponse.Milestone> milestones = milestones(commits, currentFiles);
        List<EvolutionResponse.ArchitectureEvolution> architectureChanges = architectureChanges(commits, currentDependencies, architecture);
        return new EvolutionResponse(timeline, features, milestones, architectureChanges, activity(commits));
    }

    private EvolutionResponse.TimelinePhase phase(String title, List<CommitEvidence> commits) {
        List<CommitEvidence> important = commits.stream().sorted(Comparator.comparingInt(this::importance).reversed()).limit(5).toList();
        Set<String> files = commits.stream().flatMap(commit -> commit.files().stream()).collect(Collectors.toCollection(java.util.TreeSet::new));
        List<String> technologies = technologies(files);
        OffsetDateTime start = commits.get(0).date();
        OffsetDateTime end = commits.get(commits.size() - 1).date();
        return new EvolutionResponse.TimelinePhase(title, start, end, commits.size(), important.stream().map(this::summary).toList(),
                List.copyOf(files), technologies, description(title, technologies, commits.size()));
    }

    private String phaseFor(CommitEvidence commit) {
        String text = (commit.message() + " " + String.join(" ", commit.files())).toLowerCase(Locale.ROOT);
        if (containsAny(text, "deploy", "docker", "kubernetes", "helm", ".yml", ".yaml", "terraform")) return "Deployment & Operations";
        if (containsAny(text, "test", "spec", "jest", "junit", "pytest")) return "Testing & Quality";
        if (containsAny(text, "auth", "login", "jwt", "security", "permission", "oauth")) return "Authentication & Security";
        if (containsAny(text, "controller", "route", "endpoint", "api", "handler", "view")) return "Application Interfaces";
        if (containsAny(text, "repository", "dao", "database", "migration", "schema", "entity", "model")) return "Data & Domain";
        if (containsAny(text, "package.json", "pom.xml", "build.gradle", "requirements.txt", "settings.gradle", ".gitignore")) return "Project Initialization";
        if (containsAny(text, ".tsx", ".jsx", ".vue", ".svelte", "frontend", "components/", "pages/")) return "User Interface";
        return "Core Implementation";
    }

    private List<EvolutionResponse.FeatureEvolution> featureEvolution(List<CommitEvidence> commits, List<File> currentFiles, List<CodeEntity> entities) {
        Map<String, FeatureRule> rules = featureRules();
        Map<String, List<String>> currentByFeature = new HashMap<>();
        for (File file : currentFiles) for (var entry : rules.entrySet()) if (entry.getValue().matches(file.getPath()))
            currentByFeature.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(file.getPath());
        for (CodeEntity entity : entities) for (var entry : rules.entrySet()) if (entry.getValue().matches(entity.getFile().getPath() + " " + entity.getName()))
            currentByFeature.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entity.getFile().getPath());
        List<EvolutionResponse.FeatureEvolution> result = new ArrayList<>();
        for (var entry : rules.entrySet()) {
            List<CommitEvidence> related = commits.stream().filter(commit -> entry.getValue().matches(commit.message() + " " + String.join(" ", commit.files()))).toList();
            if (related.isEmpty() && !currentByFeature.containsKey(entry.getKey())) continue;
            List<String> files = related.stream().flatMap(commit -> commit.files().stream()).filter(entry.getValue()::matches).distinct().toList();
            result.add(new EvolutionResponse.FeatureEvolution(entry.getKey(), related.isEmpty() ? null : related.get(0).date(),
                    related.stream().map(CommitEvidence::sha).toList(), files, currentByFeature.getOrDefault(entry.getKey(), List.of()),
                    related.stream().map(commit -> phaseFor(commit)).distinct().toList()));
        }
        return result;
    }

    private Map<String, FeatureRule> featureRules() {
        return Map.of("Authentication", rule("auth", "login", "jwt", "security", "oauth", "permission"),
                "Persistence", rule("repository", "dao", "database", "migration", "schema", "entity", "model"),
                "Application API", rule("controller", "endpoint", "route", "api", "handler"),
                "User Interface", rule("frontend", "components/", "pages/", ".tsx", ".jsx", ".vue", ".svelte"),
                "Testing", rule("test", "spec", "junit", "pytest", "jest"),
                "Deployment", rule("docker", "kubernetes", "helm", "terraform", "deploy", ".yml", ".yaml"));
    }

    private FeatureRule rule(String... signals) {
        return value -> containsAny(value.toLowerCase(Locale.ROOT), signals);
    }

    private List<EvolutionResponse.Milestone> milestones(List<CommitEvidence> commits, List<File> currentFiles) {
        List<EvolutionResponse.Milestone> result = new ArrayList<>();
        if (!commits.isEmpty()) result.add(milestone("Project initialized", commits.get(0), "The earliest available commit establishes the repository history."));
        Map<String, String> titles = Map.of("Persistence", "Database or domain model introduced", "Authentication", "Authentication introduced",
                "Application API", "Application API introduced", "User Interface", "User interface introduced", "Testing", "Testing introduced", "Deployment", "Deployment configuration added");
        for (var feature : featureRules().entrySet()) {
            commits.stream().filter(commit -> feature.getValue().matches(commit.message() + " " + String.join(" ", commit.files()))).findFirst()
                    .ifPresent(commit -> result.add(milestone(titles.get(feature.getKey()), commit, "The first evidence of " + feature.getKey().toLowerCase(Locale.ROOT) + " appears in changed files and commit context.")));
        }
        return result.stream().filter(milestone -> milestone.title() != null).sorted(Comparator.comparing(EvolutionResponse.Milestone::date)).toList();
    }

    private EvolutionResponse.Milestone milestone(String title, CommitEvidence commit, String description) {
        return new EvolutionResponse.Milestone(title, commit.date(), commit.sha(), description, commit.files());
    }

    private List<EvolutionResponse.ArchitectureEvolution> architectureChanges(List<CommitEvidence> commits,
            List<Dependency> currentDependencies, String architecture) {
        List<String> currentRelationships = currentDependencies.stream()
                .map(dependency -> dependency.getRelationshipType().name()).distinct().sorted().toList();
        return commits.stream().filter(commit -> commit.files().stream().anyMatch(path -> containsAny(path.toLowerCase(Locale.ROOT), "controller", "service", "repository", "domain", "application", "adapter", "infrastructure")))
                .filter(commit -> commit.files().size() >= 2).map(commit -> new EvolutionResponse.ArchitectureEvolution(commit.date(), architecture,
                        relationships(commit.files(), currentRelationships), commit.files(), commit.sha())).distinct().toList();
    }

    private List<String> relationships(List<String> files, List<String> currentRelationships) {
        Set<String> layers = files.stream().map(this::layer).filter(layer -> !layer.isBlank()).collect(Collectors.toCollection(java.util.TreeSet::new));
        if (layers.size() < 2 && currentRelationships.isEmpty()) return List.of();
        String historical = layers.isEmpty() ? "the analyzed dependency graph" : String.join(", ", layers);
        String dependencies = currentRelationships.isEmpty() ? "" : "; current relationships: " + String.join(", ", currentRelationships);
        return List.of("Changed structural boundaries involving " + historical + dependencies);
    }

    private String layer(String path) {
        String value = path.toLowerCase(Locale.ROOT);
        for (String candidate : List.of("controller", "service", "repository", "domain", "application", "adapter", "infrastructure")) if (value.contains(candidate)) return candidate;
        return "";
    }

    private EvolutionResponse.ActivityStats activity(List<CommitEvidence> commits) {
        if (commits.isEmpty()) return new EvolutionResponse.ActivityStats(0, 0, 0, null, null, 0, Map.of(), List.of());
        Map<String, Integer> byMonth = commits.stream().collect(Collectors.groupingBy(commit -> YearMonth.from(commit.date()).toString(), LinkedHashMap::new, Collectors.summingInt(ignored -> 1)));
        int maximum = byMonth.values().stream().max(Integer::compareTo).orElse(0);
        List<String> active = byMonth.entrySet().stream().filter(entry -> entry.getValue() == maximum).map(Map.Entry::getKey).toList();
        long duration = Duration.between(commits.get(0).date(), commits.get(commits.size() - 1).date()).toDays();
        return new EvolutionResponse.ActivityStats(commits.size(), commits.size(), (int) commits.stream().map(CommitEvidence::author).filter(author -> !"Unknown".equals(author)).distinct().count(),
                commits.get(0).date(), commits.get(commits.size() - 1).date(), duration, byMonth, active);
    }

    private EvolutionResponse.CommitSummary summary(CommitEvidence commit) {
        return new EvolutionResponse.CommitSummary(commit.sha(), commit.message(), commit.author(), commit.date(), commit.additions(), commit.deletions(), commit.files());
    }

    private List<String> technologies(Set<String> files) {
        Set<String> technologies = new HashSet<>();
        for (String path : files) {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".java")) technologies.add("Java");
            if (lower.endsWith(".py")) technologies.add("Python");
            if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx")) technologies.add("JavaScript/TypeScript");
            if (lower.endsWith(".go")) technologies.add("Go");
            if (lower.endsWith(".rs")) technologies.add("Rust");
            if (lower.endsWith("dockerfile")) technologies.add("Docker");
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) technologies.add("YAML configuration");
        }
        return technologies.stream().sorted().toList();
    }

    private String description(String title, List<String> technologies, int count) {
        return "This phase contains " + count + " commits" + (technologies.isEmpty() ? "." : " involving " + String.join(", ", technologies) + ".");
    }

    private int importance(CommitEvidence commit) { return commit.files().size() * 2 + commit.additions() + commit.deletions(); }

    private boolean relevantPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        boolean supported = SOURCE_FILE.matcher(path).matches() || lower.endsWith("pom.xml") || lower.endsWith("package.json") || lower.endsWith("build.gradle") || lower.endsWith("dockerfile") || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".sql");
        return supported && IGNORED_PATH_PARTS.stream().noneMatch(lower::contains);
    }

    private boolean containsAny(String value, String... signals) { for (String signal : signals) if (value.contains(signal)) return true; return false; }

    private record CommitEvidence(String sha, String message, String author, OffsetDateTime date, int additions, int deletions, List<String> files) { }
    private interface FeatureRule { boolean matches(String value); }
    private record CachedEvolution(OffsetDateTime createdAt, EvolutionResponse response) { }
}