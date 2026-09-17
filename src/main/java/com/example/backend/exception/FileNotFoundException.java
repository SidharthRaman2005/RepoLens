package com.example.backend.exception;

public class FileNotFoundException extends RuntimeException {

	public FileNotFoundException() {
		super("File was not found");
	}
}