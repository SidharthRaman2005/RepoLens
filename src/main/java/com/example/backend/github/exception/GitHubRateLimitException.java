package com.example.backend.github.exception;

import java.time.Instant;

public class GitHubRateLimitException extends GitHubApiException {

	private final Long remaining;
	private final Instant retryAt;

	public GitHubRateLimitException(Long remaining, Instant retryAt) {
		super("GitHub API rate limit exceeded", 429);
		this.remaining = remaining;
		this.retryAt = retryAt;
	}

	public Long getRemaining() { return remaining; }

	public Instant getRetryAt() { return retryAt; }
}