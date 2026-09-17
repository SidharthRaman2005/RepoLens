package com.example.backend.repository;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.RepositoryResponse;
import com.example.backend.github.GitHubClient;
import com.example.backend.github.dto.GitHubOwnerResponse;
import com.example.backend.github.dto.GitHubContentResponse;
import com.example.backend.github.dto.GitHubReadmeResponse;
import com.example.backend.github.dto.GitHubRepositoryResponse;
import com.example.backend.github.dto.GitHubTreeResponse;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.Dependency;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.service.AuthService;
import com.example.backend.service.DependencyGraphService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RepositoryIntegrationTest.MockGitHubConfiguration.class)
class RepositoryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RepositoryRepository repositoryRepository;

	@Autowired
	private CodeEntityRepository codeEntityRepository;

	@Autowired
	private FileRepository fileRepository;

	@Autowired
	private DependencyRepository dependencyRepository;

	@Autowired
	private AnalysisRepository analysisRepository;

	@Autowired
	private DependencyGraphService dependencyGraphService;

	@Autowired
	private GitHubClient gitHubClient;

	private final String firstEmail = uniqueEmail();
	private final String secondEmail = uniqueEmail();

	@AfterEach
	void cleanUpUsersAndRepositories() {
		deleteUserData(firstEmail);
		deleteUserData(secondEmail);
		reset(gitHubClient);
	}

	@Test
	void authenticatedUserCanAnalyzeAndRetrieveOwnRepository() throws Exception {
		stubSuccessfulGitHubResponses();
		AuthResponse auth = authService.signup(signupRequest(firstEmail));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.owner").value("owner"))
				.andExpect(jsonPath("$.name").value("repo"))
				.andExpect(jsonPath("$.language").value("Java"))
				.andReturn().getResponse().getContentAsString();

		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(response).get("id").asLong();
		mockMvc.perform(get("/api/repositories/{id}", repositoryId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://github.com/owner/repo"));
	}

	@Test
	void unauthenticatedAnalyzeIsRejected() throws Exception {
		mockMvc.perform(post("/api/repositories/analyze")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void userCannotRetrieveAnotherUsersRepository() throws Exception {
		stubSuccessfulGitHubResponses();
		AuthResponse owner = authService.signup(signupRequest(firstEmail));
		AuthResponse otherUser = authService.signup(signupRequest(secondEmail));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + owner.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{id}", repositoryId)
					.header("Authorization", "Bearer " + otherUser.token()))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidGitHubUrlIsRejected() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));

		mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://google.com/project\"}"))
				.andExpect(status().isBadRequest());
}

	@Test
	void missingGitHubRepositoryReturnsNotFound() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "missing"))
				.thenThrow(new com.example.backend.github.exception.GitHubRepositoryNotFoundException());

		mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/missing\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void GitHubFailureReturnsBadGateway() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository(anyString(), anyString()))
				.thenThrow(new com.example.backend.github.exception.GitHubApiException("GitHub API request failed", 500));

		mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isBadGateway());
	}

	@Test
	void processingStoresSupportedSourceFilesAndIgnoresUnwantedFiles() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "processed")).thenReturn(repositoryMetadata("processed"));
		when(gitHubClient.getLanguages("owner", "processed")).thenReturn(Map.of("Java", 100L));
		when(gitHubClient.getTree("owner", "processed", "main")).thenReturn(new GitHubTreeResponse(List.of(
				item("src/Main.java", 20L),
				item("src/App.tsx", 20L),
				item("scripts/tool.py", 20L),
				item("node_modules/pkg/index.js", 20L),
				item("target/generated.java", 20L),
				item("src/image.png", 20L),
				item("src/bundle.min.js", 20L),
				item("src/Large.java", 1_000_001L)), false));
		when(gitHubClient.getReadme("owner", "processed")).thenReturn(null);
		when(gitHubClient.getFileContent("owner", "processed", "src/Main.java"))
				.thenReturn(encodedContent("class Main {}"));
		when(gitHubClient.getFileContent("owner", "processed", "src/App.tsx"))
				.thenReturn(encodedContent("export default App;"));
		when(gitHubClient.getFileContent("owner", "processed", "scripts/tool.py"))
				.thenReturn(encodedContent("print('ok')"));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/processed\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{repositoryId}/files", repositoryId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
				.andExpect(jsonPath("$[0].path").value("scripts/tool.py"))
				.andExpect(jsonPath("$[0].language").value("Python"))
				.andExpect(jsonPath("$[0].content").value("print('ok')"));
		long fileId = fileRepository.findAllByRepositoryIdOrderByPathAsc(repositoryId).stream()
				.filter(file -> file.getPath().equals("src/Main.java")).findFirst().orElseThrow().getId();
		assertThat(codeEntityRepository.findAllByFileIdOrderByStartLineAsc(fileId)).anySatisfy(entity -> {
			assertThat(entity.getType().name()).isEqualTo("CLASS");
			assertThat(entity.getName()).isEqualTo("Main");
		});
	}

	@Test
	void malformedSourceIsMarkedFailedWithoutFailingRepositoryProcessing() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "malformed")).thenReturn(repositoryMetadata("malformed"));
		when(gitHubClient.getLanguages("owner", "malformed")).thenReturn(Map.of("Java", 100L));
		when(gitHubClient.getTree("owner", "malformed", "main"))
				.thenReturn(new GitHubTreeResponse(List.of(item("Broken.java", 20L)), false));
		when(gitHubClient.getReadme("owner", "malformed")).thenReturn(null);
		when(gitHubClient.getFileContent("owner", "malformed", "Broken.java"))
				.thenReturn(encodedContent("class Broken {"));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/malformed\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
		assertThat(fileRepository.findAllByRepositoryIdOrderByPathAsc(repositoryId).get(0)
				.getParseStatus().name()).isEqualTo("FAILED");
	}

	@Test
	void emptyRepositoryTreeReturnsClientErrorWithoutCrashing() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "empty")).thenReturn(repositoryMetadata("empty"));
		when(gitHubClient.getLanguages("owner", "empty")).thenReturn(Map.of());
		when(gitHubClient.getTree("owner", "empty", "main"))
				.thenReturn(new GitHubTreeResponse(List.of(), false));

		mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/empty\"}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void processingFailureMarksRepositoryFailed() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "failed")).thenReturn(repositoryMetadata("failed"));
		when(gitHubClient.getLanguages("owner", "failed")).thenReturn(Map.of());
		when(gitHubClient.getTree("owner", "failed", "main"))
				.thenReturn(new GitHubTreeResponse(List.of(item("Main.java", 20L)), false));
		when(gitHubClient.getReadme("owner", "failed")).thenReturn(null);
		when(gitHubClient.getFileContent("owner", "failed", "Main.java"))
				.thenThrow(new com.example.backend.github.exception.GitHubNetworkException());

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/failed\"}"))
				.andExpect(status().isServiceUnavailable())
				.andReturn().getResponse().getContentAsString();
		long repositoryId = repositoryRepository.findAllByUserIdOrderByAnalyzedAtDesc(
				userRepository.findByEmail(firstEmail).orElseThrow().getId()).get(0).getId();
		org.assertj.core.api.Assertions.assertThat(repositoryRepository.findById(repositoryId).orElseThrow()
				.getProcessingStatus().name()).isEqualTo("FAILED");
	}

	@Test
	void userCannotAccessAnotherUsersFiles() throws Exception {
		stubSuccessfulGitHubResponses();
		AuthResponse owner = authService.signup(signupRequest(firstEmail));
		AuthResponse otherUser = authService.signup(signupRequest(secondEmail));
		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + owner.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{repositoryId}/files", repositoryId)
					.header("Authorization", "Bearer " + otherUser.token()))
				.andExpect(status().isNotFound());
	}

	@Test
	void graphContainsImportsInheritanceAndContainmentEdges() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "graph")).thenReturn(repositoryMetadata("graph"));
		when(gitHubClient.getLanguages("owner", "graph")).thenReturn(Map.of("Java", 200L));
		when(gitHubClient.getTree("owner", "graph", "main")).thenReturn(new GitHubTreeResponse(List.of(
				item("src/A.java", 80L), item("src/B.java", 30L)), false));
		when(gitHubClient.getReadme("owner", "graph")).thenReturn(null);
		when(gitHubClient.getFileContent("owner", "graph", "src/A.java"))
				.thenReturn(encodedContent("import demo.B;\nclass A extends B {\n  public void run() {}\n}"));
		when(gitHubClient.getFileContent("owner", "graph", "src/B.java"))
				.thenReturn(encodedContent("package demo;\nclass B {}"));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/graph\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		String graph = mockMvc.perform(get("/api/repositories/{id}/graph", repositoryId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nodes", org.hamcrest.Matchers.hasSize(3)))
				.andExpect(jsonPath("$.edges[*].type").value(org.hamcrest.Matchers.hasItems(
						"IMPORTS", "EXTENDS", "CONTAINS")))
				.andReturn().getResponse().getContentAsString();

		com.fasterxml.jackson.databind.JsonNode graphBody = new com.fasterxml.jackson.databind.ObjectMapper().readTree(graph);
		long classAId = findNodeId(graphBody, "A");
		mockMvc.perform(get("/api/repositories/{id}/dependencies/{entityId}", repositoryId, classAId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].type").value(org.hamcrest.Matchers.hasItems("IMPORTS", "EXTENDS")));
	}

	@Test
	void graphOwnershipIsEnforced() throws Exception {
		stubSuccessfulGitHubResponses();
		AuthResponse owner = authService.signup(signupRequest(firstEmail));
		AuthResponse otherUser = authService.signup(signupRequest(secondEmail));
		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + owner.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{id}/graph", repositoryId)
					.header("Authorization", "Bearer " + otherUser.token()))
				.andExpect(status().isNotFound());
	}

	@Test
	void architectureEndpointReturnsAutomaticAnalysisAndEnforcesOwnership() throws Exception {
		stubSuccessfulGitHubResponses();
		AuthResponse owner = authService.signup(signupRequest(firstEmail));
		AuthResponse otherUser = authService.signup(signupRequest(secondEmail));
		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + owner.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/repo\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{id}/architecture", repositoryId)
					.header("Authorization", "Bearer " + owner.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.architecture").value("Unknown"))
				.andExpect(jsonPath("$.confidenceLevel").value("LOW"))
				.andExpect(jsonPath("$.evidence").isArray());

		mockMvc.perform(get("/api/repositories/{id}/architecture", repositoryId)
					.header("Authorization", "Bearer " + otherUser.token()))
				.andExpect(status().isNotFound());
	}

	@Test
	void dashboardReturnsStatisticsLanguagesTreeAndFilteredEntities() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "dashboard")).thenReturn(repositoryMetadata("dashboard"));
		when(gitHubClient.getLanguages("owner", "dashboard")).thenReturn(Map.of("Java", 100L, "JavaScript", 50L));
		when(gitHubClient.getTree("owner", "dashboard", "main")).thenReturn(new GitHubTreeResponse(List.of(
				item("src/Main.java", 80L), item("src/app.js", 40L)), false));
		when(gitHubClient.getReadme("owner", "dashboard")).thenReturn(null);
		when(gitHubClient.getFileContent("owner", "dashboard", "src/Main.java"))
				.thenReturn(encodedContent("class Main { public void run() {} }"));
		when(gitHubClient.getFileContent("owner", "dashboard", "src/app.js"))
				.thenReturn(encodedContent("function start() {}"));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/dashboard\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{id}/dashboard", repositoryId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statistics.fileCount").value(2))
				.andExpect(jsonPath("$.statistics.sourceFileCount").value(2))
				.andExpect(jsonPath("$.statistics.classCount").value(1))
				.andExpect(jsonPath("$.statistics.methodCount").value(1))
				.andExpect(jsonPath("$.languages", org.hamcrest.Matchers.hasSize(2)))
				.andExpect(jsonPath("$.summary").isNotEmpty());

		mockMvc.perform(get("/api/repositories/{id}/files/tree", repositoryId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
				.andExpect(jsonPath("$[0].content").doesNotExist());

		mockMvc.perform(get("/api/repositories/{id}/entities?type=CLASS", repositoryId)
					.header("Authorization", "Bearer " + auth.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
				.andExpect(jsonPath("$[0].name").value("Main"));
	}

	@Test
	void healthReturnsDeterministicCategoriesIssuesAndOwnership() throws Exception {
		AuthResponse owner = authService.signup(signupRequest(firstEmail));
		AuthResponse otherUser = authService.signup(signupRequest(secondEmail));
		when(gitHubClient.getRepository("owner", "health")).thenReturn(repositoryMetadata("health"));
		when(gitHubClient.getLanguages("owner", "health")).thenReturn(Map.of("Java", 100L));
		when(gitHubClient.getTree("owner", "health", "main")).thenReturn(new GitHubTreeResponse(List.of(
				item("src/main/App.java", 100L), item("src/test/java/AppTest.java", 100L)), false));
		when(gitHubClient.getReadme("owner", "health")).thenReturn(new GitHubReadmeResponse(
				Base64.getEncoder().encodeToString("# Health".getBytes(StandardCharsets.UTF_8)), "base64"));
		when(gitHubClient.getFileContent("owner", "health", "src/main/App.java"))
				.thenReturn(encodedContent("class App { String password = \"do-not-expose\"; }"));
		when(gitHubClient.getFileContent("owner", "health", "src/test/java/AppTest.java"))
				.thenReturn(encodedContent("class AppTest {}"));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + owner.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/health\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

		mockMvc.perform(get("/api/repositories/{id}/health", repositoryId)
					.header("Authorization", "Bearer " + owner.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.overallScore").isNumber())
				.andExpect(jsonPath("$.categories.documentation").value(75))
				.andExpect(jsonPath("$.categories.testing").value(80))
				.andExpect(jsonPath("$.issues[0].category").value("Security"))
				.andExpect(jsonPath("$.issues[0].file").value("src/main/App.java"))
				.andExpect(jsonPath("$.issues[0].message").value("Possible hardcoded credential detected."))
				.andExpect(jsonPath("$.issues[0].message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("do-not-expose"))));

		mockMvc.perform(get("/api/repositories/{id}/health", repositoryId)
				.header("Authorization", "Bearer " + otherUser.token()))
				.andExpect(status().isNotFound());
	}

	@Test
	void impactTraversalHandlesCyclesWithVisitedSet() throws Exception {
		AuthResponse auth = authService.signup(signupRequest(firstEmail));
		when(gitHubClient.getRepository("owner", "cycle")).thenReturn(repositoryMetadata("cycle"));
		when(gitHubClient.getLanguages("owner", "cycle")).thenReturn(Map.of("Java", 100L));
		when(gitHubClient.getTree("owner", "cycle", "main")).thenReturn(new GitHubTreeResponse(List.of(
				item("A.java", 20L), item("B.java", 20L), item("C.java", 20L)), false));
		when(gitHubClient.getReadme("owner", "cycle")).thenReturn(null);
		when(gitHubClient.getFileContent("owner", "cycle", "A.java")).thenReturn(encodedContent("class A {}"));
		when(gitHubClient.getFileContent("owner", "cycle", "B.java")).thenReturn(encodedContent("class B {}"));
		when(gitHubClient.getFileContent("owner", "cycle", "C.java")).thenReturn(encodedContent("class C {}"));

		String response = mockMvc.perform(post("/api/repositories/analyze")
					.header("Authorization", "Bearer " + auth.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"https://github.com/owner/cycle\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long repositoryId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
		List<com.example.backend.entity.CodeEntity> entities = codeEntityRepository
				.findAllByFileRepositoryIdOrderByIdAsc(repositoryId).stream()
				.filter(entity -> entity.getType().name().equals("CLASS")).toList();
		Dependency first = new Dependency(entities.get(0), entities.get(1),
				com.example.backend.entity.DependencyRelationshipType.DEPENDS_ON);
		Dependency second = new Dependency(entities.get(1), entities.get(2),
				com.example.backend.entity.DependencyRelationshipType.DEPENDS_ON);
		Dependency third = new Dependency(entities.get(2), entities.get(0),
				com.example.backend.entity.DependencyRelationshipType.DEPENDS_ON);
		dependencyRepository.saveAll(List.of(first, second, third));

		assertThat(dependencyGraphService.impact(repositoryId, entities.get(0).getId(), firstEmail))
				.hasSize(3);
	}

	private long findNodeId(com.fasterxml.jackson.databind.JsonNode graph, String label) {
		for (com.fasterxml.jackson.databind.JsonNode node : graph.get("nodes")) {
			if (label.equals(node.get("label").asText())) return node.get("id").asLong();
		}
		throw new AssertionError("Node not found: " + label);
	}

	private void stubSuccessfulGitHubResponses() {
		when(gitHubClient.getRepository("owner", "repo")).thenReturn(
				new GitHubRepositoryResponse(
						new GitHubOwnerResponse("owner"), "repo", "https://github.com/owner/repo",
						"Repository description", "main", 42, 7, "Java"));
		when(gitHubClient.getLanguages("owner", "repo")).thenReturn(Map.of("Java", 100L));
		when(gitHubClient.getTree("owner", "repo", "main")).thenReturn(
				new GitHubTreeResponse(List.of(new GitHubTreeResponse.GitHubTreeItem("README.md", "blob", "sha")), false));
		String encodedReadme = Base64.getEncoder().encodeToString("# RepoLens".getBytes(StandardCharsets.UTF_8));
		when(gitHubClient.getReadme("owner", "repo"))
				.thenReturn(new GitHubReadmeResponse(encodedReadme, "base64"));
	}

	private GitHubRepositoryResponse repositoryMetadata(String name) {
		return new GitHubRepositoryResponse(new GitHubOwnerResponse("owner"), name,
				"https://github.com/owner/" + name, "Description", "main", 1, 1, "Java");
	}

	private GitHubTreeResponse.GitHubTreeItem item(String path, long size) {
		return new GitHubTreeResponse.GitHubTreeItem(path, "blob", "sha", size);
	}

	private GitHubContentResponse encodedContent(String content) {
		return new GitHubContentResponse("source", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)),
				"base64", (long) content.length());
	}

	private com.example.backend.dto.SignupRequest signupRequest(String email) {
		return new com.example.backend.dto.SignupRequest("Test User", email, "password123");
	}

	private void deleteUserData(String email) {
		userRepository.findByEmail(email).ifPresent(user -> {
			repositoryRepository.findAllByUserIdOrderByAnalyzedAtDesc(user.getId())
					.forEach(repository -> {
						analysisRepository.deleteAllByRepositoryId(repository.getId());
						dependencyRepository.deleteAllBySourceEntityFileRepositoryId(repository.getId());
						repositoryRepository.delete(repository);
					});
			userRepository.delete(user);
		});
	}

	private static String uniqueEmail() {
		return "repository-test-" + UUID.randomUUID() + "@example.com";
	}

	@TestConfiguration
	static class MockGitHubConfiguration {

		@Bean
		@Primary
		GitHubClient gitHubClient() {
			return mock(GitHubClient.class);
		}
	}
}