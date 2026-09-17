package com.example.backend.exception;

public class EmptyRepositoryException extends RuntimeException {

	public EmptyRepositoryException() {
		super("The GitHub repository does not contain a browsable file tree");
	}
}