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
@Table(name = "code_entities")
public class CodeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private File file;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CodeEntityType type;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int startLine;

	@Column(nullable = false)
	private int endLine;

	@Column(length = 40)
	private String visibility;

	@Column(columnDefinition = "TEXT")
	private String signature;

	@Column(length = 40)
	private String relationshipType;

	@Column
	private String relatedEntityName;

	@Column(columnDefinition = "TEXT")
	private String metadata;

	protected CodeEntity() {
	}

	public CodeEntity(File file, CodeEntityType type, String name, int startLine, int endLine,
			String visibility, String signature, String relationshipType, String relatedEntityName, String metadata) {
		this.file = file;
		this.type = type;
		this.name = name;
		this.startLine = startLine;
		this.endLine = endLine;
		this.visibility = visibility;
		this.signature = signature;
		this.relationshipType = relationshipType;
		this.relatedEntityName = relatedEntityName;
		this.metadata = metadata;
	}

	public Long getId() { return id; }
	public File getFile() { return file; }
	public CodeEntityType getType() { return type; }
	public String getName() { return name; }
	public int getStartLine() { return startLine; }
	public int getEndLine() { return endLine; }
	public String getVisibility() { return visibility; }
	public String getSignature() { return signature; }
	public String getRelationshipType() { return relationshipType; }
	public String getRelatedEntityName() { return relatedEntityName; }
	public String getMetadata() { return metadata; }
}