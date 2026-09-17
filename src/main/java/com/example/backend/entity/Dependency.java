package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dependencies")
public class Dependency {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "source_entity_id", nullable = false)
	private CodeEntity sourceEntity;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_entity_id", nullable = false)
	private CodeEntity targetEntity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DependencyRelationshipType relationshipType;

	protected Dependency() {
	}

	public Dependency(CodeEntity sourceEntity, CodeEntity targetEntity,
			DependencyRelationshipType relationshipType) {
		this.sourceEntity = sourceEntity;
		this.targetEntity = targetEntity;
		this.relationshipType = relationshipType;
	}

	public Long getId() { return id; }
	public CodeEntity getSourceEntity() { return sourceEntity; }
	public CodeEntity getTargetEntity() { return targetEntity; }
	public DependencyRelationshipType getRelationshipType() { return relationshipType; }
}