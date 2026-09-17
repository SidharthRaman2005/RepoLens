package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repositories")
public class Repository {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String owner;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 2048)
	private String url;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	private String defaultBranch;

	@Column(nullable = false)
	private long stars;

	@Column(nullable = false)
	private long forks;

	private String language;

	@Column(columnDefinition = "TEXT")
	private String readme;

	@Column(nullable = false, updatable = false)
	private LocalDateTime analyzedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RepositoryProcessingStatus processingStatus;

	@Column
	private LocalDateTime processingStartedAt;

	@Column
	private LocalDateTime processingCompletedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@OneToMany(mappedBy = "repository", orphanRemoval = true)
	private List<File> files = new ArrayList<>();

	protected Repository() {
	}

	public Repository(
			String owner,
			String name,
			String url,
			String description,
			String defaultBranch,
			long stars,
			long forks,
			String language,
			String readme,
			User user) {
		this.owner = owner;
		this.name = name;
		this.url = url;
		this.description = description;
		this.defaultBranch = defaultBranch;
		this.stars = stars;
		this.forks = forks;
		this.language = language;
		this.readme = readme;
		this.user = user;
		this.processingStatus = RepositoryProcessingStatus.PENDING;
	}

	@PrePersist
	void populateAnalyzedAt() {
		if (analyzedAt == null) {
			analyzedAt = LocalDateTime.now();
		}
		if (processingStatus == null) {
			processingStatus = RepositoryProcessingStatus.PENDING;
		}
	}

	public Long getId() {
		return id;
	}

	public String getOwner() {
		return owner;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public String getDescription() {
		return description;
	}

	public String getDefaultBranch() {
		return defaultBranch;
	}

	public long getStars() {
		return stars;
	}

	public long getForks() {
		return forks;
	}

	public String getLanguage() {
		return language;
	}

	public String getReadme() {
		return readme;
	}

	public LocalDateTime getAnalyzedAt() {
		return analyzedAt;
	}

	public User getUser() {
		return user;
	}

	public List<File> getFiles() {
		return List.copyOf(files);
	}

	public RepositoryProcessingStatus getProcessingStatus() {
		return processingStatus;
	}

	public LocalDateTime getProcessingStartedAt() {
		return processingStartedAt;
	}

	public LocalDateTime getProcessingCompletedAt() {
		return processingCompletedAt;
	}

	public void markProcessing() {
		processingStatus = RepositoryProcessingStatus.PROCESSING;
		processingStartedAt = LocalDateTime.now();
		processingCompletedAt = null;
	}

	public void markCompleted() {
		processingStatus = RepositoryProcessingStatus.COMPLETED;
		processingCompletedAt = LocalDateTime.now();
	}

	public void markFailed() {
		processingStatus = RepositoryProcessingStatus.FAILED;
		processingCompletedAt = LocalDateTime.now();
	}
}