package com.example.backend.github.dto;

import java.util.List;

public record GitHubTreeResponse(List<GitHubTreeItem> tree, boolean truncated) {

	public record GitHubTreeItem(String path, String type, String sha, Long size) {

		public GitHubTreeItem(String path, String type, String sha) {
			this(path, type, sha, null);
		}
	}
}