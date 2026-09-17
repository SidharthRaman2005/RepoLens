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
@Table(name = "health_issues")
public class HealthIssue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "analysis_id", nullable = false)
	private Analysis analysis;

	@Column(nullable = false, length = 40)
	private String category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HealthSeverity severity;

	@Column(length = 2048)
	private String file;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String message;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String recommendation;

	protected HealthIssue() { }

	public HealthIssue(Analysis analysis, String category, HealthSeverity severity, String file,
			String message, String recommendation) {
		this.analysis = analysis;
		this.category = category;
		this.severity = severity;
		this.file = file;
		this.message = message;
		this.recommendation = recommendation;
	}

	public Long getId() { return id; }
	public String getCategory() { return category; }
	public HealthSeverity getSeverity() { return severity; }
	public String getFile() { return file; }
	public String getMessage() { return message; }
	public String getRecommendation() { return recommendation; }
}