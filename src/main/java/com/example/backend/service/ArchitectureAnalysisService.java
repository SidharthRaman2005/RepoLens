package com.example.backend.service;

import com.example.backend.dto.ArchitectureResponse;
import com.example.backend.entity.Analysis;
import com.example.backend.entity.User;
import com.example.backend.exception.ArchitectureAnalysisNotFoundException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.HealthIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ArchitectureAnalysisService {

	private final ArchitectureDetector detector;
	private final AnalysisRepository analysisRepository;
	private final FileRepository fileRepository;
	private final CodeEntityRepository codeEntityRepository;
	private final DependencyRepository dependencyRepository;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;
	private final HealthIssueRepository healthIssueRepository;

	public ArchitectureAnalysisService(ArchitectureDetector detector, AnalysisRepository analysisRepository,
			FileRepository fileRepository, CodeEntityRepository codeEntityRepository,
			DependencyRepository dependencyRepository, RepositoryRepository repositoryRepository,
			UserRepository userRepository, HealthIssueRepository healthIssueRepository) {
		this.detector = detector;
		this.analysisRepository = analysisRepository;
		this.fileRepository = fileRepository;
		this.codeEntityRepository = codeEntityRepository;
		this.dependencyRepository = dependencyRepository;
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
		this.healthIssueRepository = healthIssueRepository;
	}

	@Transactional
	public ArchitectureResponse analyze(Long repositoryId) {
		com.example.backend.entity.Repository repository = repositoryRepository.findById(repositoryId)
				.orElseThrow(RepositoryNotFoundException::new);
		ArchitectureDetector.DetectionResult result = detector.detect(
				fileRepository.findAllByRepositoryIdOrderByPathAsc(repositoryId),
				codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId),
				dependencyRepository.findAllBySourceEntityFileRepositoryId(repositoryId));
		healthIssueRepository.deleteAllByAnalysisRepositoryId(repositoryId);
		analysisRepository.deleteAllByRepositoryId(repositoryId);
		Analysis analysis = analysisRepository.save(new Analysis(repository, result.architecture(), result.confidence(),
				result.explanation(), result.evidence()));
		return toResponse(analysis);
	}

	@Transactional(readOnly = true)
	public ArchitectureResponse findForUser(Long repositoryId, String email) {
		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
		repositoryRepository.findByIdAndUserId(repositoryId, user.getId()).orElseThrow(RepositoryNotFoundException::new);
		return analysisRepository.findTopByRepositoryIdOrderByCreatedAtDesc(repositoryId)
				.map(this::toResponse).orElseThrow(ArchitectureAnalysisNotFoundException::new);
	}

	private ArchitectureResponse toResponse(Analysis analysis) {
		return new ArchitectureResponse(analysis.getArchitecture(), analysis.getConfidence(),
				confidenceLevel(analysis.getConfidence()), analysis.getExplanation(), analysis.getEvidence());
	}

	private String confidenceLevel(double confidence) {
		if (confidence >= 0.7) return "HIGH";
		if (confidence >= 0.4) return "MEDIUM";
		return "LOW";
	}
}