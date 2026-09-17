package com.example.backend.github.dto;

public record GitHubContentResponse(String path, String content, String encoding, Long size) {
}