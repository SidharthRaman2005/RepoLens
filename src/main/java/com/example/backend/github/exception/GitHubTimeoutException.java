package com.example.backend.github.exception;

public class GitHubTimeoutException extends GitHubApiException {

	public GitHubTimeoutException() {
		super("GitHub request timed out", 408);
	}
}