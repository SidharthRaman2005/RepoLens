package com.example.backend.exception;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String error, String message, String path,
		String code, Instant retryAt, Long remaining) {

	public ApiError(Instant timestamp, int status, String error, String message, String path) {
		this(timestamp, status, error, message, path, null, null, null);
	}
}