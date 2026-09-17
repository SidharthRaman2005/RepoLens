package com.example.backend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analyses")
public class Analysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "repository_id", nullable = false)
	private Repository repository;

	@Column(nullable = false)
	private String architecture;

	@Column(nullable = false)
	private double confidence;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String explanation;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@ElementCollection
	@CollectionTable(name = "analysis_evidence", joinColumns = @JoinColumn(name = "analysis_id"))
	@Column(name = "evidence", nullable = false, columnDefinition = "TEXT")
	private List<String> evidence = new ArrayList<>();

	@Column
	private Integer overallScore;
	@Column
	private Integer architectureScore;
	@Column
	private Integer documentationScore;
	@Column
	private Integer testingScore;
	@Column
	private Integer organizationScore;
	@Column
	private Integer maintainabilityScore;
	@Column
	private Integer securityScore;

	@ElementCollection
	@CollectionTable(name = "analysis_recommendations", joinColumns = @JoinColumn(name = "analysis_id"))
	@Column(name = "recommendation", nullable = false, columnDefinition = "TEXT")
	private List<String> recommendations = new ArrayList<>();

	@jakarta.persistence.OneToMany(mappedBy = "analysis", orphanRemoval = true,
			cascade = jakarta.persistence.CascadeType.ALL)
	private List<HealthIssue> healthIssues = new ArrayList<>();

	protected Analysis() {
	}

	public Analysis(Repository repository, String architecture, double confidence,
			String explanation, List<String> evidence) {
		this.repository = repository;
		this.architecture = architecture;
		this.confidence = confidence;
		this.explanation = explanation;
		this.evidence = new ArrayList<>(evidence);
	}

	@PrePersist
	void populateCreatedAt() {
		if (createdAt == null) createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public Repository getRepository() { return repository; }
	public String getArchitecture() { return architecture; }
	public double getConfidence() { return confidence; }
	public String getExplanation() { return explanation; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public List<String> getEvidence() { return List.copyOf(evidence); }
	public Integer getOverallScore() { return overallScore; }
	public Integer getArchitectureScore() { return architectureScore; }
	public Integer getDocumentationScore() { return documentationScore; }
	public Integer getTestingScore() { return testingScore; }
	public Integer getOrganizationScore() { return organizationScore; }
	public Integer getMaintainabilityScore() { return maintainabilityScore; }
	public Integer getSecurityScore() { return securityScore; }
	public List<String> getRecommendations() { return List.copyOf(recommendations); }
	public List<HealthIssue> getHealthIssues() { return List.copyOf(healthIssues); }

	public void applyHealth(int overallScore, int architectureScore, int documentationScore, int testingScore,
			int organizationScore, int maintainabilityScore, int securityScore,
			List<HealthIssue> issues, List<String> recommendations) {
		this.overallScore = overallScore;
		this.architectureScore = architectureScore;
		this.documentationScore = documentationScore;
		this.testingScore = testingScore;
		this.organizationScore = organizationScore;
		this.maintainabilityScore = maintainabilityScore;
		this.securityScore = securityScore;
		this.healthIssues.clear();
		this.healthIssues.addAll(issues);
		this.recommendations = new ArrayList<>(recommendations);
	}
}