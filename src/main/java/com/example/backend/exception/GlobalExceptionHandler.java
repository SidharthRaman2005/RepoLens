package com.example.backend.exception;

import com.example.backend.github.exception.GitHubApiException;
import com.example.backend.github.exception.GitHubNetworkException;
import com.example.backend.github.exception.GitHubRateLimitException;
import com.example.backend.github.exception.GitHubRepositoryNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ApiError> handleDuplicateEmail(
			DuplicateEmailException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrityViolation(HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiError> handleInvalidCredentials(
			InvalidCredentialsException exception, HttpServletRequest request) {
		return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
	}

	@ExceptionHandler(InvalidGitHubUrlException.class)
	public ResponseEntity<ApiError> handleInvalidGitHubUrl(
			InvalidGitHubUrlException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
	}

	@ExceptionHandler(RepositoryNotFoundException.class)
	public ResponseEntity<ApiError> handleRepositoryNotFound(
			RepositoryNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(FileNotFoundException.class)
	public ResponseEntity<ApiError> handleFileNotFound(
			FileNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ApiError> handleEntityNotFound(
			EntityNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(ArchitectureAnalysisNotFoundException.class)
	public ResponseEntity<ApiError> handleArchitectureAnalysisNotFound(
			ArchitectureAnalysisNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(GitHubRepositoryNotFoundException.class)
	public ResponseEntity<ApiError> handleGitHubRepositoryNotFound(
			GitHubRepositoryNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(GitHubRateLimitException.class)
	public ResponseEntity<ApiError> handleGitHubRateLimit(
			GitHubRateLimitException exception, HttpServletRequest request) {
		return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
	}

	@ExceptionHandler(GitHubNetworkException.class)
	public ResponseEntity<ApiError> handleGitHubNetwork(
			GitHubNetworkException exception, HttpServletRequest request) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
	}

	@ExceptionHandler(GitHubApiException.class)
	public ResponseEntity<ApiError> handleGitHubApi(
			GitHubApiException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
	}

	@ExceptionHandler(EmptyRepositoryException.class)
	public ResponseEntity<ApiError> handleEmptyRepository(
			EmptyRepositoryException exception, HttpServletRequest request) {
		return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return error(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpectedException(HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
	}

	private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
		ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}
}