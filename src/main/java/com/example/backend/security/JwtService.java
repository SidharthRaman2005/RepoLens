package com.example.backend.security;

import com.example.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-ms}") long expirationMs) {
		this.signingKey = createSigningKey(secret);
		this.expirationMs = expirationMs;
	}

	public String generateToken(User user) {
		Date issuedAt = new Date();
		Date expiration = new Date(issuedAt.getTime() + expirationMs);
		return Jwts.builder()
				.subject(user.getEmail())
				.claim("userId", user.getId())
				.issuedAt(issuedAt)
				.expiration(expiration)
				.signWith(signingKey)
				.compact();
	}

	public String extractEmail(String token) {
		return extractClaim(token, Claims::getSubject);
	}

	public boolean isTokenValid(String token, String email) {
		return email.equals(extractEmail(token)) && !isTokenExpired(token);
	}

	private boolean isTokenExpired(String token) {
		return extractClaim(token, Claims::getExpiration).before(new Date());
	}

	private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
		Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
		return claimsResolver.apply(claims);
	}

	private SecretKey createSigningKey(String secret) {
		try {
			return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
		} catch (IllegalArgumentException exception) {
			return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		}
	}
}