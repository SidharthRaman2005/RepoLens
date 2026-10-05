package com.example.backend.github;

import com.example.backend.github.exception.GitHubAuthenticationException;
import com.example.backend.github.exception.GitHubRateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

class GitHubClientTest {

	@Test
	void addsBackendAuthorizationHeaderWithoutReturningToken() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubClient client = new GitHubClient(builder, "http://github.test", "2022-11-28", "test-token");
		server.expect(requestTo("http://github.test/repos/octo/repo"))
				.andExpect(header("Authorization", "Bearer test-token"))
				.andRespond(withSuccess("{\"name\":\"repo\",\"default_branch\":\"main\",\"stargazers_count\":0,\"forks_count\":0,\"language\":\"Java\"}", MediaType.APPLICATION_JSON));

		var response = client.getRepository("octo", "repo");

		assertThat(response.name()).isEqualTo("repo");
		server.verify();
	}

	@Test
	void missingTokenFailsBeforeMakingARequest() {
		GitHubClient client = new GitHubClient(RestClient.builder(), "http://github.test", "2022-11-28", "");

		assertThatThrownBy(() -> client.getRepository("octo", "repo"))
				.isInstanceOf(GitHubAuthenticationException.class)
				.hasMessageContaining("GITHUB_TOKEN");
	}

	@Test
	void recordsRateLimitResetAndBlocksDuplicateRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubClient client = new GitHubClient(builder, "http://github.test", "2022-11-28", "test-token");
		long reset = Instant.now().plusSeconds(3600).getEpochSecond();
		server.expect(requestTo("http://github.test/repos/octo/repo"))
				.andRespond(withStatus(TOO_MANY_REQUESTS)
						.header("X-RateLimit-Remaining", "0")
						.header("X-RateLimit-Reset", String.valueOf(reset)));

		GitHubRateLimitException first = (GitHubRateLimitException) catchException(() -> client.getRepository("octo", "repo"));
		GitHubRateLimitException second = (GitHubRateLimitException) catchException(() -> client.getRepository("octo", "repo"));

		assertThat(first.getRemaining()).isZero();
		assertThat(first.getRetryAt()).isNotNull();
		assertThat(second.getRetryAt()).isEqualTo(first.getRetryAt());
		server.verify();
	}

	@Test
	void mapsUnauthorizedResponseToAuthenticationFailure() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GitHubClient client = new GitHubClient(builder, "http://github.test", "2022-11-28", "bad-token");
		server.expect(requestTo("http://github.test/repos/octo/repo"))
				.andRespond(withStatus(UNAUTHORIZED));

		assertThatThrownBy(() -> client.getRepository("octo", "repo"))
				.isInstanceOf(GitHubAuthenticationException.class)
				.hasMessageNotContaining("bad-token");
		server.verify();
	}

	private RuntimeException catchException(Runnable action) {
		try { action.run(); }
		catch (RuntimeException exception) { return exception; }
		throw new AssertionError("Expected request to fail");
	}
}