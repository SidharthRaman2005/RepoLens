package com.example.backend.dto;

import com.example.backend.entity.CodeEntityType;

public record CodeEntityResponse(Long id, String type, String name, int startLine,
		int endLine, String visibility, String signature, String file) {

	public CodeEntityResponse(Long id, CodeEntityType type, String name, int startLine,
			int endLine, String visibility, String signature, String file) {
		this(id, type == null ? null : type.name(), name, startLine, endLine, visibility, signature, file);
	}
}