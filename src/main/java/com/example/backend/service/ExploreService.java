package com.example.backend.service;

import com.example.backend.dto.ExploreResponse;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.Dependency;
import com.example.backend.entity.File;
import com.example.backend.entity.User;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExploreService {

	private final FileRepository fileRepository;
	private final CodeEntityRepository codeEntityRepository;
	private final DependencyRepository dependencyRepository;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;

	public ExploreService(FileRepository fileRepository, CodeEntityRepository codeEntityRepository,
			DependencyRepository dependencyRepository, RepositoryRepository repositoryRepository,
			UserRepository userRepository) {
		this.fileRepository = fileRepository;
		this.codeEntityRepository = codeEntityRepository;
		this.dependencyRepository = dependencyRepository;
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public ExploreResponse explore(Long repositoryId, String email) {
		ownedRepository(repositoryId, email);
		List<File> files = fileRepository.findAllByRepositoryIdOrderByPathAsc(repositoryId);
		List<CodeEntity> entities = codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId);
		List<Dependency> dependencies = dependencyRepository.findAllBySourceEntityFileRepositoryId(repositoryId);
		Map<Long, Integer> incoming = new HashMap<>();
		Map<Long, Integer> outgoing = new HashMap<>();
		for (Dependency dependency : dependencies) {
			outgoing.merge(dependency.getSourceEntity().getId(), 1, Integer::sum);
			incoming.merge(dependency.getTargetEntity().getId(), 1, Integer::sum);
		}
		List<ExploreResponse.EntryPoint> entryPoints = files.stream()
				.flatMap(file -> entities.stream().filter(entity -> entity.getFile().getId().equals(file.getId()))
						.map(entity -> entryPoint(file, entity)))
				.filter(java.util.Objects::nonNull)
				.distinct()
				.limit(30)
				.toList();
		List<ExploreResponse.DependencyHotspot> hotspots = entities.stream()
				.filter(this::isInspectable)
				.map(entity -> hotspot(entity, incoming, outgoing))
				.filter(hotspot -> hotspot.connections() > 0)
				.sorted(Comparator.comparingInt(ExploreResponse.DependencyHotspot::connections).reversed()
						.thenComparing(ExploreResponse.DependencyHotspot::file))
				.limit(20)
				.toList();
		long directories = files.stream().map(file -> directory(file.getPath())).distinct().count();
		long languages = files.stream().map(File::getLanguage).filter(java.util.Objects::nonNull).distinct().count();
		long classes = entities.stream().filter(this::isType).count();
		long methods = entities.stream().filter(entity -> entity.getType() == CodeEntityType.METHOD
				|| entity.getType() == CodeEntityType.CONSTRUCTOR
				|| entity.getType() == CodeEntityType.FUNCTION).count();
		return new ExploreResponse(entryPoints, hotspots,
				new ExploreResponse.Statistics(files.stream().filter(this::isSource).count(), classes, methods,
						languages, directories, dependencies.size()));
	}

	private ExploreResponse.EntryPoint entryPoint(File file, CodeEntity entity) {
		String metadata = value(entity.getMetadata());
		String signature = value(entity.getSignature());
		String path = file.getPath().toLowerCase(Locale.ROOT);
		String name = entity.getName();
		if (metadata.contains("SPRINGBOOTAPPLICATION") || metadata.contains("@SPRINGBOOTAPPLICATION")) {
			return new ExploreResponse.EntryPoint(file.getId(), file.getPath(), name, "Spring Boot application entry point");
		}
		if (metadata.contains("RESTCONTROLLER") || metadata.contains("CONTROLLER")
				|| name.toLowerCase(Locale.ROOT).endsWith("controller")) {
			return new ExploreResponse.EntryPoint(file.getId(), file.getPath(), name, "REST or application controller");
		}
		if (isType(entity) && (path.endsWith("/main.java") || path.endsWith("/app.java")
				|| path.endsWith("/application.java") || signature.contains("static void main"))) {
			return new ExploreResponse.EntryPoint(file.getId(), file.getPath(), name, "Executable application entry point");
		}
		if (path.endsWith("/main.jsx") || path.endsWith("/index.jsx") || path.endsWith("/app.jsx")
				|| path.endsWith("/main.tsx") || path.endsWith("/index.tsx")) {
			return new ExploreResponse.EntryPoint(file.getId(), file.getPath(), file.getName(), "Frontend application entry point");
		}
		return null;
	}

	private ExploreResponse.DependencyHotspot hotspot(CodeEntity entity, Map<Long, Integer> incoming,
			Map<Long, Integer> outgoing) {
		int in = incoming.getOrDefault(entity.getId(), 0);
		int out = outgoing.getOrDefault(entity.getId(), 0);
		return new ExploreResponse.DependencyHotspot(entity.getId(), entity.getName(), entity.getFile().getPath(),
				in, out, in + out);
	}

	private boolean isInspectable(CodeEntity entity) {
		return isType(entity) || entity.getType() == CodeEntityType.FUNCTION
				|| entity.getType() == CodeEntityType.METHOD || entity.getType() == CodeEntityType.CONSTRUCTOR;
	}

	private boolean isType(CodeEntity entity) {
		return entity.getType() == CodeEntityType.CLASS || entity.getType() == CodeEntityType.INTERFACE
				|| entity.getType() == CodeEntityType.ENUM;
	}

	private boolean isSource(File file) {
		return file.getLanguage() != null && !Set.of("Markdown", "JSON", "YAML", "XML", "Text").contains(file.getLanguage());
	}

	private String directory(String path) {
		int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		return separator < 0 ? "(root)" : path.substring(0, separator);
	}

	private String value(String value) {
		return value == null ? "" : value.toUpperCase(Locale.ROOT);
	}

	private void ownedRepository(Long repositoryId, String email) {
		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
		repositoryRepository.findByIdAndUserId(repositoryId, user.getId())
				.orElseThrow(RepositoryNotFoundException::new);
	}
}
