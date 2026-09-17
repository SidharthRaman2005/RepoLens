package com.example.backend.exception;

public class InvalidGitHubUrlException extends RuntimeException {

	public InvalidGitHubUrlException() {
		super("URL must be a public GitHub repository URL");
	}
}