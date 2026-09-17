package com.example.backend.auth;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String createdEmail;

	@AfterEach
	void cleanUpCreatedUser() {
		if (createdEmail != null) {
			userRepository.findByEmail(createdEmail).ifPresent(userRepository::delete);
		}
	}

	@Test
	void signupStoresHashedPasswordAndReturnsUserWithoutPassword() throws Exception {
		createdEmail = uniqueEmail();

		mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(signupJson(createdEmail)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.email").value(createdEmail))
				.andExpect(jsonPath("$.user.password").doesNotExist())
				.andExpect(jsonPath("$.token").isNotEmpty());

		User user = userRepository.findByEmail(createdEmail).orElseThrow();
		assertThat(user.getPassword()).isNotEqualTo("password123");
		assertThat(passwordEncoder.matches("password123", user.getPassword())).isTrue();
		assertThat(user.getCreatedAt()).isNotNull();
	}

	@Test
	void duplicateSignupFails() throws Exception {
		createdEmail = uniqueEmail();
		performSignup(createdEmail).andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(signupJson(createdEmail)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("An account with that email already exists"));
	}

	@Test
	void loginWorks() throws Exception {
		createdEmail = uniqueEmail();
		performSignup(createdEmail).andExpect(status().isCreated());

		performLogin(createdEmail, "password123")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value(createdEmail));
	}

	@Test
	void wrongPasswordFails() throws Exception {
		createdEmail = uniqueEmail();
		performSignup(createdEmail).andExpect(status().isCreated());

		performLogin(createdEmail, "wrong-password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void profileRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@Test
	void validJwtAllowsProfileAccess() throws Exception {
		createdEmail = uniqueEmail();
		String token = tokenFor(createdEmail);

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(createdEmail))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void invalidJwtIsRejected() throws Exception {
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions performSignup(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(email)));
	}

	private ResultActions performLogin(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}

	private String tokenFor(String email) throws Exception {
		MvcResult result = performSignup(email).andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return body.get("token").asText();
	}

	private String signupJson(String email) {
		return "{\"name\":\"John\",\"email\":\"" + email + "\",\"password\":\"password123\"}";
	}

	private String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}
}