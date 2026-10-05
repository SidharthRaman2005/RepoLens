package com.example.backend.github.exception;

public class GitHubAuthenticationException extends GitHubApiException {

	public GitHubAuthenticationException() {
		super("GitHub authentication failed. Configure GITHUB_TOKEN on the backend.", 401);
	}
}