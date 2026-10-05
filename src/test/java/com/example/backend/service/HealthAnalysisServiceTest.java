package com.example.backend.service;

import com.example.backend.entity.Analysis;
import com.example.backend.entity.CodeEntity;
import com.example.backend.entity.CodeEntityType;
import com.example.backend.entity.File;
import com.example.backend.entity.Repository;
import com.example.backend.repository.AnalysisRepository;
import com.example.backend.repository.CodeEntityRepository;
import com.example.backend.repository.DependencyRepository;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HealthAnalysisServiceTest {

    @Test
    void reportsMissingDocumentationAndTests() {
        Repository repository = mock(Repository.class);
        Analysis analysis = new Analysis(repository, "MVC", 0.8, "heuristic", List.of());
        File source = new File(repository, "Main.java", "Main.java", ".java", "Java", 100, "class Main {}");
        HealthAnalysisService service = service(repository, analysis, List.of(source), List.of());
        when(repository.getReadme()).thenReturn(null);

        var response = service.analyze(1L);

        assertThat(response.categories().documentation()).isEqualTo(25);
        assertThat(response.categories().testing()).isEqualTo(30);
        assertThat(response.issues()).extracting(issue -> issue.message())
                .contains("README documentation is missing.", "No test files or test directories were detected.");
    }

    @Test
    void detectsLargeStructuresAndSecretWithoutReturningSecretValue() {
        Repository repository = mock(Repository.class);
        Analysis analysis = new Analysis(repository, "MVC", 0.8, "heuristic", List.of());
        File source = new File(repository, "src/Main.java", "Main.java", ".java", "Java", 600_001,
                "class Main { String password = \"super-secret-value\"; }");
        CodeEntity largeClass = new CodeEntity(source, CodeEntityType.CLASS, "Main", 1, 600,
                "public", "class Main", null, null, null);
        CodeEntity largeMethod = new CodeEntity(source, CodeEntityType.METHOD, "run", 1, 81,
                "public", "void run()", null, null, null);
        HealthAnalysisService service = service(repository, analysis, List.of(source), List.of(largeClass, largeMethod));
        when(repository.getReadme()).thenReturn("# Main");

        var response = service.analyze(1L);

        assertThat(response.categories().maintainability()).isEqualTo(70);
        assertThat(response.categories().security()).isEqualTo(80);
        assertThat(response.issues()).extracting(issue -> issue.category())
                .contains("Security", "Maintainability");
        assertThat(response.issues()).allSatisfy(issue -> {
            assertThat(issue.message()).doesNotContain("super-secret-value");
            assertThat(issue.recommendation()).doesNotContain("super-secret-value");
        });
    }

    private HealthAnalysisService service(Repository repository, Analysis analysis, List<File> files,
            List<CodeEntity> entities) {
        AnalysisRepository analyses = mock(AnalysisRepository.class);
        FileRepository fileRepository = mock(FileRepository.class);
        CodeEntityRepository entityRepository = mock(CodeEntityRepository.class);
        DependencyRepository dependencyRepository = mock(DependencyRepository.class);
        RepositoryRepository repositories = mock(RepositoryRepository.class);
        when(repositories.findById(1L)).thenReturn(Optional.of(repository));
        when(analyses.findTopByRepositoryIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(analysis));
        when(fileRepository.findAllByRepositoryIdOrderByPathAsc(1L)).thenReturn(files);
        when(entityRepository.findAllByFileRepositoryIdOrderByIdAsc(1L)).thenReturn(entities);
        when(dependencyRepository.findAllBySourceEntityFileRepositoryId(1L)).thenReturn(List.of());
        when(analyses.save(analysis)).thenReturn(analysis);
        return new HealthAnalysisService(analyses, fileRepository, entityRepository, dependencyRepository,
                repositories, mock(UserRepository.class));
    }
}