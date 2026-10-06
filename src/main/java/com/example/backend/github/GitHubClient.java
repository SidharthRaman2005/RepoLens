package com.example.backend.github;

import com.example.backend.github.dto.GitHubReadmeResponse;
import com.example.backend.github.dto.GitHubContentResponse;
import com.example.backend.github.dto.GitHubRepositoryResponse;
import com.example.backend.github.dto.GitHubTreeResponse;
import com.example.backend.github.dto.GitHubCommitDetail;
import com.example.backend.github.dto.GitHubCommitSummary;
import com.example.backend.github.exception.GitHubApiException;
import com.example.backend.github.exception.GitHubAuthenticationException;
import com.example.backend.github.exception.GitHubNetworkException;
import com.example.backend.github.exception.GitHubRateLimitException;
import com.example.backend.github.exception.GitHubRepositoryNotFoundException;
import com.example.backend.github.exception.GitHubTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.core.ParameterizedTypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.List;

@Component
public class GitHubClient {

	private final RestClient restClient;
	private final String token;
	private final Logger logger = LoggerFactory.getLogger(GitHubClient.class);
	private volatile Long remaining;
	private volatile Instant resetAt;

	@Autowired
	public GitHubClient(
			@Value("${github.api-base-url}") String apiBaseUrl,
			@Value("${github.api-version}") String apiVersion,
			@Value("${github.token:}") String token) {
		this(RestClient.builder(), apiBaseUrl, apiVersion, token);
	}

	GitHubClient(RestClient.Builder builder, String apiBaseUrl, String apiVersion, String token) {
		this.token = token == null ? "" : token.trim();
		this.restClient = builder
				.baseUrl(apiBaseUrl)
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.defaultHeader("X-GitHub-Api-Version", apiVersion)
				.defaultHeader(HttpHeaders.USER_AGENT, "RepoLens")
				.defaultHeaders(headers -> {
					if (!this.token.isBlank()) headers.setBearerAuth(this.token);
				})
				.build();
	}

	public GitHubRepositoryResponse getRepository(String owner, String name) {
		return get("/repos/{owner}/{name}", GitHubRepositoryResponse.class, Map.of("owner", owner, "name", name));
	}

	public Map<String, Long> getLanguages(String owner, String name) {
		return getMap("/repos/{owner}/{name}/languages", Map.of("owner", owner, "name", name));
	}

	public GitHubReadmeResponse getReadme(String owner, String name) {
		try {
			return get("/repos/{owner}/{name}/readme", GitHubReadmeResponse.class,
					Map.of("owner", owner, "name", name));
		} catch (GitHubApiException exception) {
			if (exception.isNotFound()) {
				return null;
			}
			throw exception;
		}
	}

	public GitHubTreeResponse getTree(String owner, String name, String branch) {
		return get("/repos/{owner}/{name}/git/trees/{branch}?recursive=1", GitHubTreeResponse.class,
				Map.of("owner", owner, "name", name, "branch", branch));
	}

	public List<GitHubCommitSummary> getCommits(String owner, String name, int page, int perPage) {
		return getList("/repos/{owner}/{name}/commits?page={page}&per_page={perPage}",
				Map.of("owner", owner, "name", name, "page", page, "perPage", perPage),
				new ParameterizedTypeReference<List<GitHubCommitSummary>>() { });
	}

	public GitHubCommitDetail getCommit(String owner, String name, String sha) {
		return get("/repos/{owner}/{name}/commits/{sha}", GitHubCommitDetail.class,
				Map.of("owner", owner, "name", name, "sha", sha));
	}

	public GitHubContentResponse getFileContent(String owner, String name, String path) {
		try {
			ensureAvailable();
			ResponseEntity<GitHubContentResponse> response = restClient.get()
					.uri(builder -> builder.path("/repos/{owner}/{name}/contents/").path(path)
							.build(owner, name))
					.retrieve().toEntity(GitHubContentResponse.class);
			recordRateLimit("/repos/{owner}/{name}/contents/{path}", response.getHeaders());
			return response.getBody();
		} catch (HttpClientErrorException exception) {
			throw translateClientError("/repos/{owner}/{name}/contents/{path}", exception);
		} catch (ResourceAccessException exception) {
			if (exception.getCause() instanceof java.net.SocketTimeoutException) throw new GitHubTimeoutException();
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
	}

	private <T> T get(String path, Class<T> responseType, Map<String, ?> variables) {
		try {
			ensureAvailable();
			ResponseEntity<T> response = restClient.get().uri(path, variables).retrieve().toEntity(responseType);
			recordRateLimit(path, response.getHeaders());
			return response.getBody();
		} catch (HttpClientErrorException exception) {
			throw translateClientError(path, exception);
		} catch (ResourceAccessException exception) {
			if (exception.getCause() instanceof java.net.SocketTimeoutException) throw new GitHubTimeoutException();
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
	}

	private <T> List<T> getList(String path, Map<String, ?> variables) {
		return getList(path, variables, new ParameterizedTypeReference<List<T>>() { });
	}

	private <T> List<T> getList(String path, Map<String, ?> variables,
			ParameterizedTypeReference<List<T>> responseType) {
		try {
			ensureAvailable();
			ResponseEntity<List<T>> response = restClient.get().uri(path, variables).retrieve()
					.toEntity(responseType);
			recordRateLimit(path, response.getHeaders());
			return response.getBody() == null ? List.of() : response.getBody();
		} catch (HttpClientErrorException exception) {
			throw translateClientError(path, exception);
		} catch (ResourceAccessException exception) {
			if (exception.getCause() instanceof java.net.SocketTimeoutException) throw new GitHubTimeoutException();
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
	}

	private <K, V> Map<K, V> getMap(String path, Map<String, ?> variables) {
		try {
			ensureAvailable();
			ResponseEntity<Map<K, V>> response = restClient.get().uri(path, variables).retrieve()
					.toEntity(new ParameterizedTypeReference<>() { });
			recordRateLimit(path, response.getHeaders());
			return response.getBody() == null ? Map.of() : response.getBody();
		} catch (HttpClientErrorException exception) {
			throw translateClientError(path, exception);
		} catch (ResourceAccessException exception) {
			if (exception.getCause() instanceof java.net.SocketTimeoutException) throw new GitHubTimeoutException();
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
	}

	private void ensureAvailable() {
		if (token.isBlank()) throw new GitHubAuthenticationException();
		if (remaining != null && remaining <= 0 && resetAt != null && resetAt.isAfter(Instant.now())) {
			throw new GitHubRateLimitException(remaining, resetAt);
		}
	}

	private void recordRateLimit(String resource, HttpHeaders headers) {
		remaining = parseLong(headers.getFirst("X-RateLimit-Remaining"));
		Long reset = parseLong(headers.getFirst("X-RateLimit-Reset"));
		resetAt = reset == null ? null : Instant.ofEpochSecond(reset);
		logger.info("GitHub API request: resource={} status=200 remaining={} resetAt={}", resource, remaining, resetAt);
	}

	private RuntimeException translateClientError(String resource, HttpClientErrorException exception) {
		HttpHeaders headers = exception.getResponseHeaders() == null ? HttpHeaders.EMPTY : exception.getResponseHeaders();
		Long responseRemaining = parseLong(headers.getFirst("X-RateLimit-Remaining"));
		Long reset = parseLong(headers.getFirst("X-RateLimit-Reset"));
		Instant responseReset = reset == null ? null : Instant.ofEpochSecond(reset);
		logger.warn("GitHub API request: resource={} status={} remaining={} resetAt={}", resource,
				exception.getStatusCode().value(), responseRemaining, responseReset);
		if (exception.getStatusCode().value() == 401) return new GitHubAuthenticationException();
		if (exception.getStatusCode().value() == 404) {
			return new GitHubRepositoryNotFoundException();
		}
		if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 429) {
			remaining = responseRemaining;
			resetAt = responseReset == null ? Instant.now().plusSeconds(60) : responseReset;
			return new GitHubRateLimitException(responseRemaining, resetAt);
		}
		return new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
	}

	private Long parseLong(String value) {
		try { return value == null ? null : Long.valueOf(value); }
		catch (NumberFormatException ignored) { return null; }
	}
}