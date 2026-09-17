package com.example.backend.service;

import com.example.backend.dto.GraphEdgeResponse;
import com.example.backend.dto.GraphNodeResponse;
import com.example.backend.dto.GraphResponse;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.Dependency;
import com.example.backend.entity.DependencyRelationshipType;
import com.example.backend.entity.File;
import com.example.backend.entity.User;
import com.example.backend.exception.EntityNotFoundException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DependencyGraphService {

	private static final int MAX_TRAVERSAL_NODES = 500;

	private final DependencyRepository dependencyRepository;
	private final CodeEntityRepository codeEntityRepository;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;

	public DependencyGraphService(DependencyRepository dependencyRepository,
			CodeEntityRepository codeEntityRepository, RepositoryRepository repositoryRepository,
			UserRepository userRepository) {
		this.dependencyRepository = dependencyRepository;
		this.codeEntityRepository = codeEntityRepository;
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
	}

	public void rebuild(Long repositoryId) {
		clear(repositoryId);
		List<CodeEntity> entities = codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId);
		Map<String, CodeEntity> namedTypes = entities.stream()
				.filter(this::isType)
				.collect(Collectors.toMap(this::qualifiedTypeKey, Function.identity(), (first, ignored) -> first));
		Map<String, CodeEntity> simpleTypes = entities.stream()
				.filter(this::isType)
				.collect(Collectors.toMap(CodeEntity::getName, Function.identity(), (first, ignored) -> first));
		Set<String> edges = new HashSet<>();
		List<Dependency> dependencies = new ArrayList<>();

		for (CodeEntity entity : entities) {
			addStructuralRelationship(entity, namedTypes, simpleTypes, dependencies, edges);
		}
		addImportRelationships(entities, namedTypes, simpleTypes, dependencies, edges);
		addContainmentRelationships(entities, dependencies, edges);
		dependencyRepository.saveAll(dependencies);
	}

	public void clear(Long repositoryId) {
		dependencyRepository.deleteAllBySourceEntityFileRepositoryId(repositoryId);
	}

	@Transactional(readOnly = true)
	public GraphResponse graph(Long repositoryId, String email) {
		ownedRepository(repositoryId, email);
		List<CodeEntity> entities = codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId);
		List<Dependency> dependencies = dependenciesForRepository(entities);
		List<GraphNodeResponse> nodes = entities.stream()
				.filter(this::isGraphNode)
				.map(this::node)
				.toList();
		List<GraphEdgeResponse> edges = dependencies.stream().map(this::edge).toList();
		return new GraphResponse(nodes, edges);
	}

	@Transactional(readOnly = true)
	public List<GraphEdgeResponse> dependencies(Long repositoryId, Long entityId, String email) {
		ownedRepository(repositoryId, email);
		assertEntityBelongsToRepository(entityId, repositoryId);
		return dependencyRepository.findAllBySourceEntityId(entityId).stream().map(this::edge).toList();
	}

	@Transactional(readOnly = true)
	public List<GraphEdgeResponse> dependents(Long repositoryId, Long entityId, String email) {
		ownedRepository(repositoryId, email);
		assertEntityBelongsToRepository(entityId, repositoryId);
		return dependencyRepository.findAllByTargetEntityId(entityId).stream().map(this::edge).toList();
	}

	@Transactional(readOnly = true)
	public List<GraphNodeResponse> impact(Long repositoryId, Long entityId, String email) {
		ownedRepository(repositoryId, email);
		assertEntityBelongsToRepository(entityId, repositoryId);
		Map<Long, CodeEntity> entities = codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId).stream()
				.collect(Collectors.toMap(CodeEntity::getId, Function.identity()));
		Set<Long> visited = new HashSet<>();
		ArrayDeque<Long> queue = new ArrayDeque<>();
		queue.add(entityId);
		visited.add(entityId);
		while (!queue.isEmpty() && visited.size() <= MAX_TRAVERSAL_NODES) {
			Long current = queue.remove();
			for (Dependency dependency : dependencyRepository.findAllByTargetEntityId(current)) {
				Long source = dependency.getSourceEntity().getId();
				if (visited.add(source)) queue.add(source);
			}
		}
		return visited.stream().map(entities::get).filter(java.util.Objects::nonNull).map(this::node).toList();
	}

	private void addStructuralRelationship(CodeEntity source, Map<String, CodeEntity> namedTypes,
			Map<String, CodeEntity> simpleTypes, List<Dependency> dependencies, Set<String> edges) {
		if (source.getRelationshipType() == null || source.getRelatedEntityName() == null) return;
		DependencyRelationshipType type;
		if ("EXTENDS".equals(source.getRelationshipType())) type = DependencyRelationshipType.EXTENDS;
		else if ("IMPLEMENTS".equals(source.getRelationshipType())) type = DependencyRelationshipType.IMPLEMENTS;
		else if ("CONTAINS".equals(source.getRelationshipType())) return;
		else return;
		CodeEntity target = resolveType(source.getRelatedEntityName(), source, namedTypes, simpleTypes);
		addDependency(source, target, type, dependencies, edges);
	}

	private void addImportRelationships(List<CodeEntity> entities, Map<String, CodeEntity> namedTypes,
			Map<String, CodeEntity> simpleTypes, List<Dependency> dependencies, Set<String> edges) {
		Map<Long, CodeEntity> sourceTypes = entities.stream()
				.filter(this::isType)
				.collect(Collectors.toMap(entity -> entity.getFile().getId(), Function.identity(), (first, ignored) -> first));
		for (CodeEntity importEntity : entities) {
			if (importEntity.getType() != CodeEntityType.IMPORT) continue;
			CodeEntity source = sourceTypes.get(importEntity.getFile().getId());
			if (source == null) continue;
			String importedName = importEntity.getName().replace("static ", "").replace(".*", "").trim();
			String simpleName = importedName.substring(importedName.lastIndexOf('.') + 1);
			CodeEntity target = resolveType(simpleName, source, namedTypes, simpleTypes);
			addDependency(source, target, DependencyRelationshipType.IMPORTS, dependencies, edges);
		}
	}

	private void addContainmentRelationships(List<CodeEntity> entities, List<Dependency> dependencies, Set<String> edges) {
		for (CodeEntity child : entities) {
			if (child.getType() != CodeEntityType.METHOD && child.getType() != CodeEntityType.CONSTRUCTOR
					&& child.getType() != CodeEntityType.FIELD && child.getType() != CodeEntityType.VARIABLE) continue;
			CodeEntity container = entities.stream()
					.filter(candidate -> isType(candidate) && sameFile(candidate, child)
							&& candidate.getStartLine() <= child.getStartLine()
							&& candidate.getEndLine() >= child.getEndLine())
					.min((left, right) -> Integer.compare(left.getEndLine() - left.getStartLine(), right.getEndLine() - right.getStartLine()))
					.orElse(null);
			addDependency(container, child, DependencyRelationshipType.CONTAINS, dependencies, edges);
		}
	}

	private CodeEntity resolveType(String name, CodeEntity source, Map<String, CodeEntity> namedTypes,
			Map<String, CodeEntity> simpleTypes) {
		String normalized = name.replace(";", "").trim();
		CodeEntity direct = simpleTypes.get(normalized);
		if (direct != null && !direct.getId().equals(source.getId())) return direct;
		return namedTypes.entrySet().stream()
				.filter(entry -> entry.getKey().endsWith("." + normalized) || entry.getKey().equals(normalized))
				.map(Map.Entry::getValue)
				.filter(candidate -> !candidate.getId().equals(source.getId()))
				.findFirst().orElse(null);
	}

	private void addDependency(CodeEntity source, CodeEntity target, DependencyRelationshipType type,
			List<Dependency> dependencies, Set<String> edges) {
		if (source == null || target == null || source.getId().equals(target.getId())) return;
		String key = source.getId() + ":" + target.getId() + ":" + type;
		if (edges.add(key)) dependencies.add(new Dependency(source, target, type));
	}

	private List<Dependency> dependenciesForRepository(List<CodeEntity> entities) {
		if (entities.isEmpty()) return List.of();
		return dependencyRepository.findAllBySourceEntityFileRepositoryId(entities.get(0).getFile().getRepository().getId());
	}

	private void assertEntityBelongsToRepository(Long entityId, Long repositoryId) {
		if (codeEntityRepository.findAllByFileRepositoryIdOrderByIdAsc(repositoryId).stream()
				.noneMatch(entity -> entity.getId().equals(entityId))) throw new EntityNotFoundException();
	}

	private User ownedRepository(Long repositoryId, String email) {
		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
		repositoryRepository.findByIdAndUserId(repositoryId, user.getId()).orElseThrow(RepositoryNotFoundException::new);
		return user;
	}

	private boolean isType(CodeEntity entity) {
		return entity.getType() == CodeEntityType.CLASS || entity.getType() == CodeEntityType.INTERFACE
				|| entity.getType() == CodeEntityType.ENUM;
	}

	private boolean isGraphNode(CodeEntity entity) {
		return isType(entity) || entity.getType() == CodeEntityType.METHOD
				|| entity.getType() == CodeEntityType.CONSTRUCTOR
				|| entity.getType() == CodeEntityType.FUNCTION;
	}

	private boolean sameFile(CodeEntity left, CodeEntity right) {
		return left.getFile().getId().equals(right.getFile().getId());
	}

	private String qualifiedTypeKey(CodeEntity entity) {
		return entity.getFile().getPath() + ":" + entity.getName();
	}

	private GraphNodeResponse node(CodeEntity entity) {
		return new GraphNodeResponse(entity.getId(), entity.getName(), entity.getType().name(), entity.getFile().getName());
	}

	private GraphEdgeResponse edge(Dependency dependency) {
		return new GraphEdgeResponse(dependency.getSourceEntity().getId(), dependency.getTargetEntity().getId(),
				dependency.getRelationshipType().name());
	}
}