package com.example.backend.controller;

import com.example.backend.dto.EvolutionResponse;
import com.example.backend.service.EvolutionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repositories/{repositoryId}")
public class EvolutionController {

    private final EvolutionService evolutionService;

    public EvolutionController(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @GetMapping("/evolution")
    public EvolutionResponse evolution(@PathVariable Long repositoryId, Authentication authentication) {
        return evolutionService.evolution(repositoryId, authentication.getName());
    }

    @GetMapping("/timeline")
    public List<EvolutionResponse.TimelinePhase> timeline(@PathVariable Long repositoryId, Authentication authentication) {
        return evolutionService.timelinePhases(repositoryId, authentication.getName());
    }

    @GetMapping("/features")
    public List<EvolutionResponse.FeatureEvolution> features(@PathVariable Long repositoryId, Authentication authentication) {
        return evolutionService.features(repositoryId, authentication.getName());
    }

    @GetMapping("/milestones")
    public List<EvolutionResponse.Milestone> milestones(@PathVariable Long repositoryId, Authentication authentication) {
        return evolutionService.milestones(repositoryId, authentication.getName());
    }

    @GetMapping("/architecture-evolution")
    public List<EvolutionResponse.ArchitectureEvolution> architectureEvolution(@PathVariable Long repositoryId, Authentication authentication) {
        return evolutionService.architectureEvolution(repositoryId, authentication.getName());
    }

    @GetMapping("/activity")
    public EvolutionResponse.ActivityStats activity(@PathVariable Long repositoryId, Authentication authentication) {
        return evolutionService.activity(repositoryId, authentication.getName());
    }
}