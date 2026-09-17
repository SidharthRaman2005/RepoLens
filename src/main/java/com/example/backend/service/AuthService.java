package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.SignupRequest;
import com.example.backend.dto.UserResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.DuplicateEmailException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public AuthResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new DuplicateEmailException();
		}

		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
		try {
			User savedUser = userRepository.save(user);
			return new AuthResponse(jwtService.generateToken(savedUser), toResponse(savedUser));
		} catch (DataIntegrityViolationException exception) {
			throw new DuplicateEmailException();
		}
	}

	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(normalizeEmail(request.email()))
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
				.orElseThrow(InvalidCredentialsException::new);
		return new AuthResponse(jwtService.generateToken(user), toResponse(user));
	}

	public UserResponse getProfile(String email) {
		return userRepository.findByEmail(normalizeEmail(email))
				.map(this::toResponse)
				.orElseThrow(InvalidCredentialsException::new);
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}