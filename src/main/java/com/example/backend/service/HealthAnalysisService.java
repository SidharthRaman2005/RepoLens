package com.example.backend.service;

import com.example.backend.dto.HealthIssueResponse;
import com.example.backend.dto.HealthResponse;
import com.example.backend.entity.Analysis;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.File;
import com.example.backend.entity.HealthIssue;
import com.example.backend.entity.HealthSeverity;
import com.example.backend.entity.User;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.repository.CodeEntityRepository;
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
			"(?i)(password|passwd|api[_-]?key|secret|access[_-]?token)\\s*[:=]\\s*['\"][^'\"]+['\"]");
	private final AnalysisRepository analysisRepository;
	private final FileRepository fileRepository;
	private final CodeEntityRepository codeEntityRepository;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;

	public HealthAnalysisService(AnalysisRepository analysisRepository, FileRepository fileRepository,
			CodeEntityRepository codeEntityRepository, RepositoryRepository repositoryRepository,
			UserRepository userRepository) {
		this.analysisRepository = analysisRepository;
		this.fileRepository = fileRepository;
		this.codeEntityRepository = codeEntityRepository;
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
		HealthResult result = calculate(repository, analysis, files, entities);
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
			List<File> files, List<CodeEntity> entities) {
		List<HealthIssueData> issues = new ArrayList<>();
		Set<String> recommendations = new LinkedHashSet<>();
		int architecture = clamp((int) Math.round(analysis.getConfidence() * 100));
		int documentation = repository.getReadme() != null && !repository.getReadme().isBlank() ? 75 : 25;
		if (documentation == 25) recommendations.add("Add a README that explains the repository and how to run it.");
		boolean hasTests = files.stream().anyMatch(file -> isTestPath(file.getPath()));
		int testing = hasTests ? 80 : 30;
		if (!hasTests) recommendations.add("Add automated tests and keep them close to the code they verify.");
		int organization = organizationScore(files);
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
		int security = 100;
		for (File file : files) if (SECRET_PATTERN.matcher(file.getContent()).find()) {
			security -= 20;
			issues.add(issue("Security", HealthSeverity.HIGH, file.getPath(), "Possible hardcoded credential detected.", "Move credentials to environment variables or a secret manager."));
			recommendations.add("Review possible hardcoded credentials and externalize them securely.");
		}
		int overall = (architecture + documentation + testing + organization + clamp(maintainability) + security) / 6;
		return new HealthResult(overall, architecture, documentation, testing, organization, clamp(maintainability), security, issues, List.copyOf(recommendations));
	}

	private int organizationScore(List<File> files) {
		if (files.isEmpty()) return 20;
		long structured = files.stream().filter(file -> file.getPath().contains("/")).count();
		return structured == 0 ? 35 : structured * 100 / files.size() >= 70 ? 85 : 65;
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