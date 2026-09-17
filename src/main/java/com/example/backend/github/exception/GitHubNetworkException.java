package com.example.backend.github.exception;

public class GitHubNetworkException extends RuntimeException {

	public GitHubNetworkException() {
		super("GitHub could not be reached");
	}
}