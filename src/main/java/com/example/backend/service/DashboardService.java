package com.example.backend.service;

import com.example.backend.dto.ArchitectureResponse;
import com.example.backend.dto.CodeEntityResponse;
import com.example.backend.dto.DashboardResponse;
import com.example.backend.dto.FileMetadata;
import com.example.backend.dto.FileTreeResponse;
import com.example.backend.entity.Analysis;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.Repository;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;
	private final FileRepository fileRepository;
	private final CodeEntityRepository codeEntityRepository;
	private final DependencyRepository dependencyRepository;
	private final AnalysisRepository analysisRepository;

	public DashboardService(RepositoryRepository repositoryRepository, UserRepository userRepository,
			FileRepository fileRepository, CodeEntityRepository codeEntityRepository,
			DependencyRepository dependencyRepository, AnalysisRepository analysisRepository) {
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
		this.fileRepository = fileRepository;
		this.codeEntityRepository = codeEntityRepository;
		this.dependencyRepository = dependencyRepository;
		this.analysisRepository = analysisRepository;
	}

	@Transactional(readOnly = true)
	public DashboardResponse dashboard(Long repositoryId, String email) {
		Repository repository = ownedRepository(repositoryId, email);
		List<FileMetadata> files = fileRepository.findMetadataByRepositoryId(repositoryId);
		Analysis analysis = analysisRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repositoryId).orElse(null);
		return new DashboardResponse(
				new DashboardResponse.RepositorySummary(repository.getName(), repository.getOwner(), repository.getDescription(),
						repository.getStars(), repository.getForks(), repository.getLanguage()),
				new DashboardResponse.Statistics(files.size(), files.size(),
					count(repositoryId, CodeEntityType.CLASS), count(repositoryId, CodeEntityType.INTERFACE),
					count(repositoryId, CodeEntityType.METHOD), count(repositoryId, CodeEntityType.FUNCTION),
					dependencyRepository.countBySourceEntityFileRepositoryId(repositoryId)),
				languages(files),
				analysis == null ? null : new DashboardResponse.ArchitectureSummary(analysis.getArchitecture(), analysis.getConfidence()),
				new DashboardResponse.AnalysisSummary(repository.getProcessingStatus().name(), repository.getAnalyzedAt()),
				summary(repository));
	}

	@Transactional(readOnly = true)
	public List<CodeEntityResponse> entities(Long repositoryId, String type, String file, String name, String email) {
		ownedRepository(repositoryId, email);
		CodeEntityType entityType = type == null || type.isBlank() ? null : CodeEntityType.valueOf(type.toUpperCase(Locale.ROOT));
		return codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId).stream()
				.filter(entity -> entityType == null || entity.getType() == entityType)
				.filter(entity -> file == null || file.isBlank() || entity.getFile().getPath().equals(file))
				.filter(entity -> name == null || name.isBlank() || entity.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
				.map(entity -> new CodeEntityResponse(entity.getId(), entity.getType().name(), entity.getName(),
						entity.getStartLine(), entity.getEndLine(), entity.getVisibility(), entity.getSignature(),
						entity.getFile().getPath()))
				.toList();
	}

	private Repository ownedRepository(Long repositoryId, String email) {
		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
		return repositoryRepository.findByIdAndUserId(repositoryId, user.getId())
				.orElseThrow(RepositoryNotFoundException::new);
	}

	private long count(Long repositoryId, CodeEntityType type) {
		return codeEntityRepository.countByFileRepositoryIdAndType(repositoryId, type);
	}

	private List<DashboardResponse.LanguageStatistics> languages(List<FileMetadata> files) {
		Map<String, Long> counts = files.stream().collect(Collectors.groupingBy(
				file -> file.language() == null || file.language().isBlank() ? "Unknown" : file.language(),
				Collectors.counting()));
		double total = files.size();
		return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.map(entry -> new DashboardResponse.LanguageStatistics(entry.getKey(), entry.getValue(),
						total == 0 ? 0 : Math.round(entry.getValue() * 10000.0 / total) / 100.0))
				.toList();
	}

	private String summary(Repository repository) {
		String language = repository.getLanguage() == null ? "multiple languages" : repository.getLanguage();
		return repository.getOwner() + "/" + repository.getName() + " is a public " + language
				+ " repository with " + repository.getStars() + " stars and " + repository.getForks() + " forks.";
	}
}