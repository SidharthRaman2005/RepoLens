package com.example.backend.repository;

import com.example.backend.dto.FileMetadata;
import com.example.backend.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {

	List<File> findAllByRepositoryIdOrderByPathAsc(Long repositoryId);

	@org.springframework.data.jpa.repository.Query("select new com.example.backend.dto.FileMetadata(f.id, f.path, f.name, f.extension, f.language, f.size) from File f where f.repository.id = :repositoryId order by f.path")
	List<FileMetadata> findMetadataByRepositoryId(Long repositoryId);

	Optional<File> findByIdAndRepositoryId(Long id, Long repositoryId);

	void deleteAllByRepositoryId(Long repositoryId);
}