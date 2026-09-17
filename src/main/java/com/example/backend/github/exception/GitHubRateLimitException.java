package com.example.backend.github.exception;

public class GitHubRateLimitException extends GitHubApiException {

	public GitHubRateLimitException() {
		super("GitHub API rate limit exceeded", 429);
	}
}