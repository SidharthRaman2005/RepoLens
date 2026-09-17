package com.example.backend.service;

import com.example.backend.dto.FileResponse;
import com.example.backend.dto.FileTreeResponse;
import com.example.backend.entity.File;
import com.example.backend.entity.User;
import com.example.backend.exception.FileNotFoundException;
import com.example.backend.exception.InvalidCredentialsException;
import com.example.backend.exception.RepositoryNotFoundException;
import com.example.backend.repository.FileRepository;
import com.example.backend.repository.RepositoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class FileService {

	private final FileRepository fileRepository;
	private final RepositoryRepository repositoryRepository;
	private final UserRepository userRepository;

	public FileService(FileRepository fileRepository, RepositoryRepository repositoryRepository,
			UserRepository userRepository) {
		this.fileRepository = fileRepository;
		this.repositoryRepository = repositoryRepository;
		this.userRepository = userRepository;
	}

	public List<FileResponse> findAll(Long repositoryId, String email) {
		com.example.backend.entity.Repository repository = ownedRepository(repositoryId, email);
		return fileRepository.findAllByRepositoryIdOrderByPathAsc(repository.getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	public FileResponse findById(Long repositoryId, Long fileId, String email) {
		com.example.backend.entity.Repository repository = ownedRepository(repositoryId, email);
		return fileRepository.findByIdAndRepositoryId(fileId, repository.getId())
				.map(this::toResponse)
				.orElseThrow(FileNotFoundException::new);
	}

	public List<FileTreeResponse> tree(Long repositoryId, String email) {
		com.example.backend.entity.Repository repository = ownedRepository(repositoryId, email);
		return fileRepository.findMetadataByRepositoryId(repository.getId()).stream()
				.map(file -> new FileTreeResponse(file.id(), file.path(), file.name(), file.extension(), file.language(), file.size()))
				.toList();
	}

	private com.example.backend.entity.Repository ownedRepository(Long repositoryId, String email) {
		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
				.orElseThrow(InvalidCredentialsException::new);
		return repositoryRepository.findByIdAndUserId(repositoryId, user.getId())
				.orElseThrow(RepositoryNotFoundException::new);
	}

	private FileResponse toResponse(File file) {
		return new FileResponse(file.getId(), file.getRepository().getId(), file.getPath(), file.getName(),
				file.getExtension(), file.getLanguage(), file.getSize(), file.getContent());
	}
}