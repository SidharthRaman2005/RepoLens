package com.example.backend.service;

import com.example.backend.dto.HealthIssueResponse;
import com.example.backend.dto.HealthResponse;
import com.example.backend.entity.Analysis;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.Dependency;
import com.example.backend.entity.File;
import com.example.backend.entity.HealthIssue;
import com.example.backend.entity.HealthSeverity;
import com.example.backend.entity.User;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class HealthAnalysisService {

	private static final Pattern SECRET_PATTERN = Pattern.compile(
			"(?i)(password|passwd|api[_-]?key|secret|access[_-]?token|private[_-]?key)\\s*[:=]\\s*(?:['\"][^'\"]+['\"]|[A-Za-z0-9_./+=-]{8,})");
	private static final Pattern DOCUMENTATION_PATTERN = Pattern.compile("(?m)(/\\*\\*|//|#|<!--)");
	private static final Pattern TEST_FRAMEWORK_PATTERN = Pattern.compile(
			"(?i)(junit|mockito|assertj|pytest|unittest|jest|vitest|mocha|chai|xunit|nunit|testing\\.go)");
	private static final Set<String> GENERATED_SEGMENTS = Set.of("generated", "generated-sources", "generated-test-sources", "coverage", "dist", "build", "target");
	private final AnalysisRepository analysisRepository;
	private final FileRepository fileRepository;
	private final CodeEntityRepository codeEntityRepository;
	private final DependencyRepository dependencyRepository;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;

	public HealthAnalysisService(AnalysisRepository analysisRepository, FileRepository fileRepository,
			CodeEntityRepository codeEntityRepository, DependencyRepository dependencyRepository,
			RepositoryRepository repositoryRepository,
			UserRepository userRepository) {
		this.analysisRepository = analysisRepository;
		this.fileRepository = fileRepository;
		this.codeEntityRepository = codeEntityRepository;
		this.dependencyRepository = dependencyRepository;
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public HealthResponse analyze(Long repositoryId) {
		com.example.backend.entity.Repository repository = repositoryRepository.findById(repositoryId)
				.orElseThrow(RepositoryNotFoundException::new);
		Analysis analysis = analysisRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repositoryId)
				.orElseThrow(RepositoryNotFoundException::new);
		List<File> files = fileRepository.findAllByRepositoryIdOrderByPathAsc(repositoryId);
		List<CodeEntity> entities = codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId);
		List<Dependency> dependencies = dependencyRepository.findAllBySourceEntityFileRepositoryId(repositoryId);
		HealthResult result = calculate(repository, analysis, files, entities, dependencies);
		List<HealthIssue> issues = result.issues().stream()
				.map(issue -> new HealthIssue(analysis, issue.category(), HealthSeverity.valueOf(issue.severity()),
						issue.file(), issue.message(), issue.recommendation())).toList();
		analysis.applyHealth(result.overallScore(), result.architectureScore(), result.documentationScore(),
				result.testingScore(), result.organizationScore(), result.maintainabilityScore(), result.securityScore(),
				issues, result.recommendations());
		analysisRepository.save(analysis);
		return toResponse(analysis);
	}

	@Transactional(readOnly = true)
	public HealthResponse findForUser(Long repositoryId, String email) {
		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
		repositoryRepository.findByIdAndUserId(repositoryId, user.getId()).orElseThrow(RepositoryNotFoundException::new);
		return analysisRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repositoryId)
				.filter(analysis -> analysis.getOverallScore() != null)
				.map(this::toResponse).orElseThrow(RepositoryNotFoundException::new);
	}

	private HealthResult calculate(com.example.backend.entity.Repository repository, Analysis analysis,
			List<File> files, List<CodeEntity> entities, List<Dependency> dependencies) {
		List<HealthIssueData> issues = new ArrayList<>();
		Set<String> recommendations = new LinkedHashSet<>();
		int architecture = architectureScore(analysis, files, dependencies, issues, recommendations);
		int organization = organizationScore(files, issues, recommendations);
		int maintainability = 100;
		for (File file : files) {
			if (file.getSize() > 500_000) {
				maintainability -= 10;
				issues.add(issue("Maintainability", HealthSeverity.MEDIUM, file.getPath(), "Large file detected.", "Consider splitting the file into smaller responsibilities."));
				recommendations.add("Split very large source files into focused modules.");
			}
		}
		for (CodeEntity entity : entities) {
			int lines = entity.getEndLine() - entity.getStartLine() + 1;
			if ((entity.getType() == CodeEntityType.CLASS || entity.getType() == CodeEntityType.INTERFACE) && lines > 500) {
				maintainability -= 12;
				issues.add(issue("Maintainability", HealthSeverity.MEDIUM, entity.getFile().getPath(), "Large class detected.", "Consider splitting responsibilities."));
			} else if ((entity.getType() == CodeEntityType.METHOD || entity.getType() == CodeEntityType.FUNCTION) && lines > 80) {
				maintainability -= 8;
				issues.add(issue("Maintainability", HealthSeverity.MEDIUM, entity.getFile().getPath(), "Large method or function detected.", "Extract smaller methods with focused responsibilities."));
			}
		}
		maintainability -= couplingPenalty(dependencies, files, issues, recommendations);
		maintainability = clamp(maintainability);
		int security = securityScore(files, issues, recommendations);
		int testing = testingScore(files, issues, recommendations);
		int documentation = documentationScore(repository, files, issues, recommendations);
		int overall = (architecture + documentation + testing + organization + clamp(maintainability) + security) / 6;
		return new HealthResult(overall, architecture, documentation, testing, organization, clamp(maintainability), security, issues, List.copyOf(recommendations));
	}

	private int architectureScore(Analysis analysis, List<File> files, List<Dependency> dependencies,
			List<HealthIssueData> issues, Set<String> recommendations) {
		int score = clamp((int) Math.round(analysis.getConfidence() * 100));
		long suspicious = dependencies.stream().filter(this::isSuspiciousDependency).count();
		if (suspicious > 0) {
			score -= Math.min(25, (int) suspicious * 5);
			issues.add(issue("Architecture", suspicious > 3 ? HealthSeverity.MEDIUM : HealthSeverity.LOW,
					"", "Suspicious cross-layer dependencies detected.", "Review dependency direction between architectural layers."));
			recommendations.add("Review dependency direction and reduce unnecessary cross-layer coupling.");
		}
		return clamp(score);
	}

	private int securityScore(List<File> files, List<HealthIssueData> issues, Set<String> recommendations) {
		int score = 100;
		for (File file : files) {
			if (!SECRET_PATTERN.matcher(file.getContent()).find()) continue;
			score -= 20;
			issues.add(issue("Security", HealthSeverity.HIGH, file.getPath(), "Possible hardcoded credential detected.", "Move credentials to environment variables or a secret manager."));
			recommendations.add("Review possible hardcoded credentials and externalize them securely.");
		}
		return clamp(score);
	}

	private int couplingPenalty(List<Dependency> dependencies, List<File> files,
			List<HealthIssueData> issues, Set<String> recommendations) {
		if (dependencies.isEmpty() || files.isEmpty()) return 0;
		int average = dependencies.size() / files.size();
		if (average <= 8) return 0;
		int penalty = Math.min(20, (average - 8) * 3);
		issues.add(issue("Maintainability", HealthSeverity.MEDIUM, "", "Excessive dependency coupling detected.", "Reduce unnecessary dependencies between source entities."));
		recommendations.add("Reduce excessive dependency coupling between source entities.");
		return penalty;
	}

	private boolean isSuspiciousDependency(Dependency dependency) {
		String source = dependency.getSourceEntity().getFile().getPath().toLowerCase(Locale.ROOT);
		String target = dependency.getTargetEntity().getFile().getPath().toLowerCase(Locale.ROOT);
		return (source.contains("repository") && target.contains("controller"))
				|| (source.contains("service") && target.contains("controller"))
				|| (source.contains("domain") && target.contains("infrastructure"));
	}

	private boolean isGeneratedPath(String path) {
		for (String segment : path.toLowerCase(Locale.ROOT).split("/")) {
			if (GENERATED_SEGMENTS.contains(segment)) return true;
		}
		return false;
	}

	private int documentationScore(com.example.backend.entity.Repository repository, List<File> files,
			List<HealthIssueData> issues, Set<String> recommendations) {
		boolean hasReadme = repository.getReadme() != null && !repository.getReadme().isBlank();
		long documentedSources = files.stream().filter(file -> DOCUMENTATION_PATTERN.matcher(file.getContent()).find()).count();
		int score = hasReadme ? 75 : 25;
		if (!hasReadme) {
			issues.add(issue("Documentation", HealthSeverity.MEDIUM, "README", "README documentation is missing.", "Add a README that explains the repository and how to run it."));
			recommendations.add("Add a README that explains the repository and how to run it.");
		}
		if (documentedSources > 0) score += Math.min(20, (int) documentedSources * 5);
		else {
			issues.add(issue("Documentation", HealthSeverity.LOW, "", "Source documentation comments were not detected.", "Document public APIs and non-obvious design decisions."));
			recommendations.add("Document public APIs and non-obvious design decisions.");
		}
		return clamp(score);
	}

	private int testingScore(List<File> files, List<HealthIssueData> issues, Set<String> recommendations) {
		boolean hasTests = files.stream().anyMatch(file -> isTestPath(file.getPath()));
		boolean hasFramework = files.stream().anyMatch(file -> TEST_FRAMEWORK_PATTERN.matcher(file.getContent()).find());
		int score = hasTests ? (hasFramework ? 85 : 80) : 30;
		if (!hasTests) {
			issues.add(issue("Testing", HealthSeverity.MEDIUM, "", "No test files or test directories were detected.", "Add automated tests and keep them close to the code they verify."));
			recommendations.add("Add automated tests and keep them close to the code they verify.");
		} else if (!hasFramework) {
			issues.add(issue("Testing", HealthSeverity.LOW, "", "Test files were detected, but no common test framework was identified.", "Verify that tests run in the project build."));
		}
		return score;
	}

	private int organizationScore(List<File> files, List<HealthIssueData> issues, Set<String> recommendations) {
		if (files.isEmpty()) return 20;
		long structured = files.stream().filter(file -> file.getPath().contains("/")).count();
		long generated = files.stream().filter(file -> isGeneratedPath(file.getPath())).count();
		int score = structured == 0 ? 35 : structured * 100 / files.size() >= 70 ? 85 : 65;
		if (generated > 0) {
			score -= 10;
			issues.add(issue("Code Organization", HealthSeverity.LOW, "", "Generated files are mixed with analyzed source files.", "Keep generated output outside the source tree or exclude it from analysis."));
			recommendations.add("Keep generated output outside the source tree.");
		}
		if (structured * 100 / files.size() < 50) {
			issues.add(issue("Code Organization", HealthSeverity.LOW, "", "Many source files are located at the repository root.", "Group source files into clear packages or modules."));
		}
		return clamp(score);
	}

	private boolean isTestPath(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		return lower.contains("/test/") || lower.startsWith("test/") || lower.contains("tests/")
				|| lower.matches(".*(_test|test)\\.(java|js|jsx|ts|tsx|py)$");
	}

	private HealthIssueData issue(String category, HealthSeverity severity, String file, String message, String recommendation) {
		return new HealthIssueData(category, severity.name(), file, message, recommendation);
	}

	private int clamp(int value) { return Math.max(0, Math.min(100, value)); }

	private HealthResponse toResponse(Analysis analysis) {
		return new HealthResponse(analysis.getOverallScore(), new HealthResponse.Categories(analysis.getArchitectureScore(),
				analysis.getDocumentationScore(), analysis.getTestingScore(), analysis.getOrganizationScore(),
				analysis.getMaintainabilityScore(), analysis.getSecurityScore()), analysis.getHealthIssues().stream()
				.map(issue -> new HealthIssueResponse(issue.getCategory(), issue.getSeverity().name(), issue.getFile(), issue.getMessage(), issue.getRecommendation())).toList(),
				analysis.getRecommendations(), analysis.getCreatedAt());
	}

	private record HealthIssueData(String category, String severity, String file, String message, String recommendation) { }
	private record HealthResult(int overallScore, int architectureScore, int documentationScore, int testingScore,
			int organizationScore, int maintainabilityScore, int securityScore, List<HealthIssueData> issues,
			List<String> recommendations) { }
}