package com.example.backend.dto;

import java.util.List;

public record ExploreResponse(
		List<EntryPoint> entryPoints,
		List<DependencyHotspot> dependencyHotspots,
		Statistics statistics) {

	public record EntryPoint(Long fileId, String path, String name, String reason) { }

	public record DependencyHotspot(Long entityId, String name, String file, int incoming, int outgoing,
			int connections) { }

	public record Statistics(long sourceFiles, long classes, long methods, long languages,
			long directories, long dependencyEdges) { }
}
