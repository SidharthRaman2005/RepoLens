package com.example.backend.parser;

import com.example.backend.parser.model.ParseResult;

public interface CodeParser {

	boolean supports(String language);

	ParseResult parse(String source);
}