package com.example.backend.github.exception;

public class GitHubApiException extends RuntimeException {

	private final int statusCode;

	public GitHubApiException(String message, int statusCode) {
		super(message);
		this.statusCode = statusCode;
	}

	public boolean isNotFound() {
		return statusCode == 404;
	}

	public int getStatusCode() {
		return statusCode;
	}
}