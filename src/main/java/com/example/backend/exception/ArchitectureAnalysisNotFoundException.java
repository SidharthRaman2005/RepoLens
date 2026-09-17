package com.example.backend.exception;

public class ArchitectureAnalysisNotFoundException extends RuntimeException {

	public ArchitectureAnalysisNotFoundException() {
		super("Architecture analysis is not available for this repository");
	}
}