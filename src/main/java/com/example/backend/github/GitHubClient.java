package com.example.backend.github;

import com.example.backend.github.dto.GitHubReadmeResponse;
import com.example.backend.github.dto.GitHubContentResponse;
import com.example.backend.github.dto.GitHubRepositoryResponse;
import com.example.backend.github.dto.GitHubTreeResponse;
import com.example.backend.github.exception.GitHubApiException;
import com.example.backend.github.exception.GitHubNetworkException;
import com.example.backend.github.exception.GitHubRateLimitException;
import com.example.backend.github.exception.GitHubRepositoryNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

@Component
public class GitHubClient {

	private final RestClient restClient;

	public GitHubClient(
			@Value("${github.api-base-url}") String apiBaseUrl,
			@Value("${github.api-version}") String apiVersion) {
		this.restClient = RestClient.builder()
				.baseUrl(apiBaseUrl)
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.defaultHeader("X-GitHub-Api-Version", apiVersion)
				.defaultHeader(HttpHeaders.USER_AGENT, "RepoLens")
				.build();
	}

	public GitHubRepositoryResponse getRepository(String owner, String name) {
		return get("/repos/{owner}/{name}", GitHubRepositoryResponse.class, Map.of("owner", owner, "name", name));
	}

	public Map<String, Long> getLanguages(String owner, String name) {
		try {
			return restClient.get()
					.uri("/repos/{owner}/{name}/languages", Map.of("owner", owner, "name", name))
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
		} catch (HttpClientErrorException exception) {
			throw translateClientError(exception);
		} catch (ResourceAccessException exception) {
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
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

	public GitHubContentResponse getFileContent(String owner, String name, String path) {
		try {
			return restClient.get()
					.uri(builder -> builder.path("/repos/{owner}/{name}/contents/").path(path)
							.build(owner, name))
					.retrieve()
					.body(GitHubContentResponse.class);
		} catch (HttpClientErrorException exception) {
			throw translateClientError(exception);
		} catch (ResourceAccessException exception) {
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
	}

	private <T> T get(String path, Class<T> responseType, Map<String, ?> variables) {
		try {
			return restClient.get().uri(path, variables).retrieve().body(responseType);
		} catch (HttpClientErrorException exception) {
			throw translateClientError(exception);
		} catch (ResourceAccessException exception) {
			throw new GitHubNetworkException();
		} catch (RestClientResponseException exception) {
			throw new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
		}
	}

	private RuntimeException translateClientError(HttpClientErrorException exception) {
		if (exception.getStatusCode().value() == 404) {
			return new GitHubRepositoryNotFoundException();
		}
		if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 429) {
			return new GitHubRateLimitException();
		}
		return new GitHubApiException("GitHub API request failed", exception.getStatusCode().value());
	}
}