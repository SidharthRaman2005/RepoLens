package com.example.backend.parser;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ParserFactory {

	private final List<CodeParser> parsers;

	public ParserFactory(List<CodeParser> parsers) {
		this.parsers = parsers;
	}

	public Optional<CodeParser> forLanguage(String language) {
		return parsers.stream().filter(parser -> parser.supports(language)).findFirst();
	}
}