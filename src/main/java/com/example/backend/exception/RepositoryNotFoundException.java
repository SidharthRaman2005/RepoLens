package com.example.backend.exception;

public class RepositoryNotFoundException extends RuntimeException {

	public RepositoryNotFoundException() {
		super("Repository was not found");
	}
}