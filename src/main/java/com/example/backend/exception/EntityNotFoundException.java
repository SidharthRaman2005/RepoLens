package com.example.backend.exception;

public class EntityNotFoundException extends RuntimeException {

	public EntityNotFoundException() {
		super("Code entity was not found");
	}
}