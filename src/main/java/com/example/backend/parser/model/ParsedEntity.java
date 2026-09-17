package com.example.backend.parser.model;

import com.example.backend.entity.CodeEntityType;

public record ParsedEntity(
		CodeEntityType type,
		String name,
		int startLine,
		int endLine,
		String visibility,
		String signature,
		String relationshipType,
		String relatedEntityName,
		String metadata) {
}