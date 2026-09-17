package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "repository_files")
public class File {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "repository_id", nullable = false)
	private Repository repository;

	@Column(nullable = false, length = 2048)
	private String path;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 20)
	private String extension;

	@Column(nullable = false, length = 40)
	private String language;

	@Column(nullable = false)
	private long size;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ParseStatus parseStatus;

	@Column(columnDefinition = "TEXT")
	private String parseError;

	@OneToMany(mappedBy = "file", orphanRemoval = true)
	private List<CodeEntity> codeEntities = new ArrayList<>();

	protected File() {
	}

	public File(Repository repository, String path, String name, String extension,
			String language, long size, String content) {
		this.repository = repository;
		this.path = path;
		this.name = name;
		this.extension = extension;
		this.language = language;
		this.size = size;
		this.content = content;
		this.parseStatus = ParseStatus.PENDING;
	}

	public Long getId() {
		return id;
	}

	public Repository getRepository() {
		return repository;
	}

	public String getPath() {
		return path;
	}

	public String getName() {
		return name;
	}

	public String getExtension() {
		return extension;
	}

	public String getLanguage() {
		return language;
	}

	public long getSize() {
		return size;
	}

	public String getContent() {
		return content;
	}

	public ParseStatus getParseStatus() {
		return parseStatus;
	}

	public String getParseError() {
		return parseError;
	}

	public List<CodeEntity> getCodeEntities() {
		return List.copyOf(codeEntities);
	}

	public void markParseCompleted() {
		parseStatus = ParseStatus.COMPLETED;
		parseError = null;
	}

	public void markParseFailed(String error) {
		parseStatus = ParseStatus.FAILED;
		parseError = error;
	}

	public void markParseSkipped(String reason) {
		parseStatus = ParseStatus.SKIPPED;
		parseError = reason;
	}
}