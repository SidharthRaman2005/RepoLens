package com.example.backend.service;

import com.example.backend.dto.RepositoryResponse;
import com.example.backend.entity.Repository;
import com.example.backend.entity.User;
import com.example.backend.exception.EmptyRepositoryException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.InvalidGitHubUrlException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.github.GitHubClient;
import com.example.backend.github.dto.GitHubReadmeResponse;
import com.example.backend.github.dto.GitHubRepositoryResponse;
import com.example.backend.github.dto.GitHubTreeResponse;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class RepositoryService {

	private final GitHubClient gitHubClient;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;
	private final RepositoryProcessingService repositoryProcessingService;

	public RepositoryService(
			GitHubClient gitHubClient,
			RepositoryRepository repositoryRepository,
			UserRepository userRepository,
			RepositoryProcessingService repositoryProcessingService) {
		this.gitHubClient = gitHubClient;
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
		this.repositoryProcessingService = repositoryProcessingService;
	}

	public RepositoryResponse analyze(String submittedUrl, String authenticatedEmail) {
		GitHubCoordinates coordinates = parseRepositoryUrl(submittedUrl);
		User user = findUser(authenticatedEmail);
		GitHubRepositoryResponse metadata = gitHubClient.getRepository(coordinates.owner(), coordinates.name());

		if (metadata.default_branch() == null || metadata.default_branch().isBlank()) {
			throw new EmptyRepositoryException();
		}

		Map<String, Long> languageStats = gitHubClient.getLanguages(coordinates.owner(), coordinates.name());
		String primaryLanguage = selectPrimaryLanguage(metadata.language(), languageStats);
		GitHubTreeResponse tree = gitHubClient.getTree(
				coordinates.owner(), coordinates.name(), metadata.default_branch());
		if (tree == null || tree.tree() == null || tree.tree().isEmpty()) {
			throw new EmptyRepositoryException();
		}

		GitHubReadmeResponse readme = gitHubClient.getReadme(coordinates.owner(), coordinates.name());
		Repository repository = new Repository(
				metadata.owner() == null ? coordinates.owner() : metadata.owner().login(),
				metadata.name() == null ? coordinates.name() : metadata.name(),
				metadata.html_url() == null ? submittedUrl : metadata.html_url(),
				metadata.description(),
				metadata.default_branch(),
				metadata.stargazers_count(),
				metadata.forks_count(),
				primaryLanguage,
				decodeReadme(readme),
				user);
		Repository savedRepository = repositoryRepository.save(repository);
		repositoryProcessingService.process(savedRepository, coordinates.owner(), coordinates.name(), tree);
		return toResponse(savedRepository);
	}

	public List<RepositoryResponse> findAllForUser(String authenticatedEmail) {
		User user = findUser(authenticatedEmail);
		return repositoryRepository.findAllByUserIdOrderByAnalyzedAtDesc(user.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	public RepositoryResponse findForUser(Long repositoryId, String authenticatedEmail) {
		User user = findUser(authenticatedEmail);
		return repositoryRepository.findByIdAndUserId(repositoryId, user.getId())
				.map(this::toResponse)
				.orElseThrow(RepositoryNotFoundException::new);
	}

	private User findUser(String email) {
		return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
	}

	private RepositoryResponse toResponse(Repository repository) {
		return new RepositoryResponse(
				repository.getId(),
				repository.getOwner(),
				repository.getName(),
				repository.getUrl(),
				repository.getDescription(),
				repository.getDefaultBranch(),
				repository.getStars(),
				repository.getForks(),
				repository.getLanguage(),
				repository.getReadme(),
			repository.getAnalyzedAt(),
			repository.getProcessingStatus().name());
	}

	private String decodeReadme(GitHubReadmeResponse readme) {
		if (readme == null || readme.content() == null || readme.content().isBlank()) {
			return null;
		}
		if ("base64".equalsIgnoreCase(readme.encoding())) {
			return new String(Base64.getMimeDecoder().decode(readme.content()), StandardCharsets.UTF_8);
		}
		return readme.content();
	}

	private String selectPrimaryLanguage(String metadataLanguage, Map<String, Long> languageStats) {
		if (metadataLanguage != null && !metadataLanguage.isBlank()) {
			return metadataLanguage;
		}
		if (languageStats == null || languageStats.isEmpty()) {
			return null;
		}
		return languageStats.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);
	}

	private GitHubCoordinates parseRepositoryUrl(String submittedUrl) {
		try {
			URI uri = new URI(submittedUrl.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme())
					|| !"github.com".equalsIgnoreCase(uri.getHost())
					|| uri.getQuery() != null
					|| uri.getFragment() != null) {
				throw new InvalidGitHubUrlException();
			}

			String[] segments = uri.getPath().split("/", -1);
			if (segments.length < 3 || segments.length > 4
					|| segments[1].isBlank() || segments[2].isBlank()
					|| (segments.length == 4 && !segments[3].isBlank())) {
				throw new InvalidGitHubUrlException();
			}

			String repositoryName = segments[2].endsWith(".git")
					? segments[2].substring(0, segments[2].length() - 4)
					: segments[2];
			if (repositoryName.isBlank()) {
				throw new InvalidGitHubUrlException();
			}
			return new GitHubCoordinates(segments[1], repositoryName);
		} catch (URISyntaxException | NullPointerException exception) {
			throw new InvalidGitHubUrlException();
		}
	}

	private record GitHubCoordinates(String owner, String name) {
	}
}