package com.example.backend.service;

import com.example.backend.entity.File;
import com.example.backend.entity.Repository;
import com.example.backend.github.GitHubClient;
import com.example.backend.github.dto.GitHubContentResponse;
import com.example.backend.github.dto.GitHubTreeResponse;
import com.example.backend.entity.ParseStatus;
import com.example.backend.parser.CodeParser;
import com.example.backend.parser.ParserFactory;
import com.example.backend.parser.model.ParseResult;
import com.example.backend.parser.model.ParsedEntity;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RepositoryProcessingService {

	private static final long MAX_SOURCE_FILE_SIZE = 1_000_000L;
	private static final Map<String, String> LANGUAGES = Map.of(
			".java", "Java",
			".js", "JavaScript",
			".jsx", "JavaScript",
			".ts", "TypeScript",
			".tsx", "TypeScript",
			".py", "Python");
	private static final Set<String> IGNORED_DIRECTORIES = Set.of(
			".git", "node_modules", "target", "build", "dist", "out", "bin", "coverage", ".idea", ".vscode");
	private static final Set<String> IGNORED_FILES = Set.of("package-lock.json", "yarn.lock", "pnpm-lock.yaml");

	private final GitHubClient gitHubClient;
	private final FileRepository fileRepository;
	private final RepositoryRepository repositoryRepository;
	private final ParserFactory parserFactory;
	private final CodeEntityRepository codeEntityRepository;
	private final DependencyGraphService dependencyGraphService;
	private final ArchitectureAnalysisService architectureAnalysisService;
	private final HealthAnalysisService healthAnalysisService;

	public RepositoryProcessingService(
			GitHubClient gitHubClient,
			FileRepository fileRepository,
			RepositoryRepository repositoryRepository,
			ParserFactory parserFactory,
			CodeEntityRepository codeEntityRepository,
			DependencyGraphService dependencyGraphService,
			ArchitectureAnalysisService architectureAnalysisService,
			HealthAnalysisService healthAnalysisService) {
		this.gitHubClient = gitHubClient;
		this.fileRepository = fileRepository;
		this.repositoryRepository = repositoryRepository;
		this.parserFactory = parserFactory;
		this.codeEntityRepository = codeEntityRepository;
		this.dependencyGraphService = dependencyGraphService;
		this.architectureAnalysisService = architectureAnalysisService;
		this.healthAnalysisService = healthAnalysisService;
	}

	public void process(Repository repository, String owner, String name, GitHubTreeResponse tree) {
		repository.markProcessing();
		repositoryRepository.saveAndFlush(repository);
		try {
			dependencyGraphService.clear(repository.getId());
			fileRepository.deleteAllByRepositoryId(repository.getId());
			for (GitHubTreeResponse.GitHubTreeItem item : tree.tree()) {
				processItem(repository, owner, name, item);
			}
			dependencyGraphService.rebuild(repository.getId());
			architectureAnalysisService.analyze(repository.getId());
			healthAnalysisService.analyze(repository.getId());
			repository.markCompleted();
			repositoryRepository.save(repository);
		} catch (RuntimeException exception) {
			repository.markFailed();
			repositoryRepository.save(repository);
			throw exception;
		}
	}

	private void processItem(Repository repository, String owner, String name,
			GitHubTreeResponse.GitHubTreeItem item) {
		if (item == null || !"blob".equalsIgnoreCase(item.type()) || !isSupportedSourceFile(item.path())) {
			return;
		}
		long size = item.size() == null ? 0L : item.size();
		if (size > MAX_SOURCE_FILE_SIZE || isMinified(item.path())) {
			return;
		}

		GitHubContentResponse source = gitHubClient.getFileContent(owner, name, item.path());
		String content = decodeContent(source);
		long contentSize = content.getBytes(StandardCharsets.UTF_8).length;
		if (contentSize > MAX_SOURCE_FILE_SIZE) {
			return;
		}
		String path = item.path();
		String fileName = path.substring(path.lastIndexOf('/') + 1);
		String extension = extensionOf(fileName);
		File savedFile = fileRepository.save(new File(repository, path, fileName, extension,
				LANGUAGES.get(extension), contentSize, content));
		analyzeFile(savedFile);
	}

	private void analyzeFile(File file) {
		codeEntityRepository.deleteAllByFileId(file.getId());
		CodeParser parser = parserFactory.forLanguage(file.getLanguage()).orElse(null);
		if (parser == null) {
			file.markParseSkipped("No parser available for language: " + file.getLanguage());
			fileRepository.save(file);
			return;
		}
		try {
			ParseResult result = parser.parse(file.getContent());
			for (ParsedEntity parsed : result.entities()) {
				codeEntityRepository.save(new com.example.backend.entity.CodeEntity(file, parsed.type(), parsed.name(),
						parsed.startLine(), parsed.endLine(), parsed.visibility(), parsed.signature(),
						parsed.relationshipType(), parsed.relatedEntityName(), parsed.metadata()));
			}
			file.markParseCompleted();
		} catch (RuntimeException exception) {
			file.markParseFailed(exception.getMessage() == null ? "Unable to parse source" : exception.getMessage());
		}
		fileRepository.save(file);
	}

	private boolean isSupportedSourceFile(String path) {
		if (path == null || path.isBlank() || IGNORED_FILES.contains(path.substring(path.lastIndexOf('/') + 1))) {
			return false;
		}
		for (String segment : path.split("/")) {
			if (IGNORED_DIRECTORIES.contains(segment.toLowerCase(Locale.ROOT))) {
				return false;
			}
		}
		return LANGUAGES.containsKey(extensionOf(path));
	}

	private boolean isMinified(String path) {
		return path.toLowerCase(Locale.ROOT).matches(".*\\.min\\.(js|jsx|ts|tsx)$");
	}

	private String extensionOf(String path) {
		int dot = path.lastIndexOf('.');
		return dot < 0 ? "" : path.substring(dot).toLowerCase(Locale.ROOT);
	}

	private String decodeContent(GitHubContentResponse source) {
		if (source == null || source.content() == null) {
			return "";
		}
		if ("base64".equalsIgnoreCase(source.encoding())) {
			return new String(Base64.getMimeDecoder().decode(source.content()), StandardCharsets.UTF_8);
		}
		return source.content();
	}
}