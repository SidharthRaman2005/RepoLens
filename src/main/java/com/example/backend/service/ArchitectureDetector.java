package com.example.backend.service;

import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.Dependency;
import com.example.backend.entity.DependencyRelationshipType;
import com.example.backend.entity.File;
import com.example.backend.parser.model.ParsedEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ArchitectureDetector {

	public DetectionResult detect(List<File> files, List<CodeEntity> entities, List<Dependency> dependencies) {
		Set<String> paths = files.stream().map(File::getPath).map(this::normalize).collect(java.util.stream.Collectors.toSet());
		Set<String> directories = paths.stream().flatMap(path -> directories(path).stream()).collect(java.util.stream.Collectors.toSet());
		Set<String> names = entities.stream().map(CodeEntity::getName).map(this::normalize).collect(java.util.stream.Collectors.toSet());
		List<Score> scores = List.of(
				controllerServiceRepository(directories, names, dependencies),
				mvc(directories, paths),
				clean(directories),
				hexagonal(directories),
				modular(directories),
				flat(directories));
		Score selected = scores.stream().max(Comparator.comparingDouble(Score::confidence)).orElseGet(this::unknown);
		if (selected.confidence() < 0.4) selected = unknown();
		return new DetectionResult(selected.architecture(), round(selected.confidence()), selected.evidence(), selected.explanation());
	}

	private Score controllerServiceRepository(Set<String> directories, Set<String> names, List<Dependency> dependencies) {
		List<String> evidence = new ArrayList<>();
		boolean controller = hasAny(directories, "controller") || hasSuffix(names, "controller");
		boolean service = hasAny(directories, "service") || hasSuffix(names, "service");
		boolean repository = hasAny(directories, "repository") || hasSuffix(names, "repository");
		if (controller) evidence.add("controller package or directory detected");
		if (service) evidence.add("service package or directory detected");
		if (repository) evidence.add("repository package or directory detected");
		long controllerToService = countDirection(dependencies, "controller", "service");
		long serviceToRepository = countDirection(dependencies, "service", "repository");
		if (controllerToService > 0) evidence.add("controllers depend on services");
		if (serviceToRepository > 0) evidence.add("services depend on repositories");
		double confidence = (controller ? 0.2 : 0) + (service ? 0.2 : 0) + (repository ? 0.2 : 0)
				+ (controllerToService > 0 ? 0.2 : 0) + (serviceToRepository > 0 ? 0.2 : 0);
		return score("Controller-Service-Repository", confidence, evidence,
				"The repository appears to use a controller-service-repository structure because its layers and dependency direction were detected.");
	}

	private Score mvc(Set<String> directories, Set<String> paths) {
		List<String> evidence = new ArrayList<>();
		boolean controller = hasAny(directories, "controller", "controllers");
		boolean model = hasAny(directories, "model", "models", "entity", "entities");
		boolean view = hasAny(directories, "view", "views", "template", "templates", "webapp")
				|| paths.stream().anyMatch(path -> path.matches(".*\\.(html|htm|jsp|thymeleaf)$"));
		if (controller) evidence.add("controller directory detected");
		if (model) evidence.add("model or entity directory detected");
		if (view) evidence.add("view or template files detected");
		return score("MVC", (controller ? 0.3 : 0) + (model ? 0.3 : 0) + (view ? 0.4 : 0), evidence,
				"The repository appears to use MVC because controller, model, and view evidence was detected.");
	}

	private Score clean(Set<String> directories) {
		List<String> evidence = evidenceFor(directories, "domain", "usecase", "application", "infrastructure", "interfaces");
		return score("Clean Architecture", Math.min(1.0, evidence.size() * 0.25), evidence,
				"The repository shows clean-architecture naming across domain, application, interface, or infrastructure boundaries.");
	}

	private Score hexagonal(Set<String> directories) {
		List<String> evidence = evidenceFor(directories, "domain", "application", "ports", "adapters", "infrastructure");
		return score("Hexagonal Architecture", Math.min(1.0, evidence.size() * 0.25), evidence,
				"The repository shows hexagonal-architecture evidence through domain, port, adapter, or infrastructure boundaries.");
	}

	private Score modular(Set<String> directories) {
		long moduleCount = directories.stream().filter(directory -> directory.startsWith("modules/") || directory.startsWith("module/")).count();
		List<String> evidence = moduleCount > 0 ? List.of("module directories detected") : List.of();
		return score("Modular Monolith", moduleCount > 0 ? 0.65 : 0, evidence,
				"The repository contains explicit module boundaries but does not provide stronger architectural evidence.");
	}

	private Score flat(Set<String> directories) {
		boolean structured = directories.stream().anyMatch(directory -> Set.of("controller", "service", "repository", "domain", "application").contains(directory));
		return score("Simple/Flat Structure", !structured && !directories.isEmpty() && directories.size() <= 2 ? 0.55 : 0,
				List.of("No recognized architectural layer boundaries were detected"),
				"The repository appears relatively flat because recognized architectural boundaries were not detected.");
	}

	private Score unknown() {
		return score("Unknown", 0.2, List.of("Insufficient structural evidence"),
				"The available repository structure does not provide enough deterministic evidence for a recognized architecture.");
	}

	private long countDirection(List<Dependency> dependencies, String sourceLayer, String targetLayer) {
		return dependencies.stream().filter(dependency -> dependency.getRelationshipType() == DependencyRelationshipType.IMPORTS
				|| dependency.getRelationshipType() == DependencyRelationshipType.DEPENDS_ON)
				.filter(dependency -> normalize(dependency.getSourceEntity().getFile().getPath()).contains(sourceLayer)
						&& normalize(dependency.getTargetEntity().getFile().getPath()).contains(targetLayer))
				.count();
	}

	private List<String> evidenceFor(Set<String> directories, String... expected) {
		List<String> evidence = new ArrayList<>();
		for (String value : expected) if (hasAny(directories, value)) evidence.add(value + " boundary detected");
		return evidence;
	}

	private Set<String> directories(String path) {
		Set<String> result = new HashSet<>();
		String[] segments = path.split("/");
		for (int index = 0; index < segments.length - 1; index++) result.add(segments[index]);
		return result;
	}

	private boolean hasAny(Set<String> values, String... expected) {
		for (String candidate : expected) if (values.contains(candidate)) return true;
		return false;
	}

	private boolean hasSuffix(Set<String> values, String suffix) {
		return values.stream().anyMatch(value -> value.endsWith(suffix));
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("\\", "/");
	}

	private double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private Score score(String architecture, double confidence, List<String> evidence, String explanation) {
		return new Score(architecture, confidence, evidence, explanation);
	}

	private record Score(String architecture, double confidence, List<String> evidence, String explanation) { }
	public record DetectionResult(String architecture, double confidence, List<String> evidence, String explanation) { }
}