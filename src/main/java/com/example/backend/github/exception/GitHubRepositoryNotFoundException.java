package com.example.backend.github.exception;

public class GitHubRepositoryNotFoundException extends GitHubApiException {

	public GitHubRepositoryNotFoundException() {
		super("GitHub repository was not found", 404);
	}
}