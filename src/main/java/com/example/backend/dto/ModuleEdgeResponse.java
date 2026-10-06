package com.example.backend.dto;

public record ModuleEdgeResponse(String source, String target, int dependencyCount) {
}
