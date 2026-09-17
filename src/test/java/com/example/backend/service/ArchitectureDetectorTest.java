package com.example.backend.service;

import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.File;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureDetectorTest {

	private final ArchitectureDetector detector = new ArchitectureDetector();

	@Test
	void detectsControllerServiceRepositoryStructure() {
		ArchitectureDetector.DetectionResult result = detector.detect(List.of(
				file("controller/UserController.java"), file("service/UserService.java"),
				file("repository/UserRepository.java")), List.of(), List.of());

		assertThat(result.architecture()).isEqualTo("Controller-Service-Repository");
		assertThat(result.confidence()).isEqualTo(0.6);
		assertThat(result.evidence()).hasSize(3);
	}

	@Test
	void detectsMvcStructure() {
		ArchitectureDetector.DetectionResult result = detector.detect(List.of(
				file("controller/HomeController.java"), file("model/Home.java"), file("templates/home.html")),
				List.of(), List.of());

		assertThat(result.architecture()).isEqualTo("MVC");
		assertThat(result.confidence()).isEqualTo(1.0);
	}

	@Test
	void detectsFlatStructure() {
		ArchitectureDetector.DetectionResult result = detector.detect(List.of(file("src/Main.java")), List.of(), List.of());

		assertThat(result.architecture()).isEqualTo("Simple/Flat Structure");
		assertThat(result.confidence()).isEqualTo(0.55);
	}

	@Test
	void returnsUnknownWhenThereIsNoEvidence() {
		ArchitectureDetector.DetectionResult result = detector.detect(List.of(), List.of(), List.of());

		assertThat(result.architecture()).isEqualTo("Unknown");
		assertThat(result.confidence()).isEqualTo(0.2);
	}

	private File file(String path) {
		return new File(null, path, path.substring(path.lastIndexOf('/') + 1), ".java", "Java", 10, "");
	}
}