package com.example.backend.parser.model;

import java.util.List;

public record ParseResult(List<ParsedEntity> entities) {
}