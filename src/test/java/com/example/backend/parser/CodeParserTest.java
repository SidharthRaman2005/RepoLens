package com.example.backend.parser;

import com.example.backend.entity.CodeEntityType;
import com.example.backend.parser.model.ParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CodeParserTest {

	@Autowired
	private ParserFactory parserFactory;

	@Test
	void parsesJavaClassesMethodsConstructorsAndRelationships() {
		ParseResult result = parserFactory.forLanguage("Java").orElseThrow().parse("""
				package demo;
				import java.util.List;
				interface Auditable {}
				class Base {}
				public class UserService extends Base implements Auditable {
				    private String name;
				    public UserService() {}
				    public void createUser() {}
				}
				""");

		assertThat(result.entities()).extracting(entity -> entity.type())
				.contains(CodeEntityType.PACKAGE, CodeEntityType.IMPORT, CodeEntityType.INTERFACE,
						CodeEntityType.CLASS, CodeEntityType.FIELD, CodeEntityType.CONSTRUCTOR, CodeEntityType.METHOD);
		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.CLASS);
			assertThat(entity.name()).isEqualTo("UserService");
			assertThat(entity.relationshipType()).isEqualTo("EXTENDS");
			assertThat(entity.relatedEntityName()).isEqualTo("Base");
		});
		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.METHOD);
			assertThat(entity.name()).isEqualTo("createUser");
			assertThat(entity.startLine()).isEqualTo(8);
		});
	}

	@Test
	void parsesJavaScriptFunctionsAndClasses() {
		ParseResult result = parserFactory.forLanguage("JavaScript").orElseThrow().parse("""
				import lib from 'lib';
				function createUser() { return {}; }
				class UserService {
				  save() {}
				}
				""");

		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.FUNCTION);
			assertThat(entity.name()).isEqualTo("createUser");
		});
		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.CLASS);
			assertThat(entity.name()).isEqualTo("UserService");
		});
		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.METHOD);
			assertThat(entity.name()).isEqualTo("save");
		});
	}

	@Test
	void parsesPythonClassesFunctionsAndImports() {
		ParseResult result = parserFactory.forLanguage("Python").orElseThrow().parse("""
				import os
				class UserService:
				    def create_user(self):
				        return True
				""");

		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.IMPORT);
			assertThat(entity.name()).contains("import os");
		});
		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.CLASS);
			assertThat(entity.name()).isEqualTo("UserService");
		});
		assertThat(result.entities()).anySatisfy(entity -> {
			assertThat(entity.type()).isEqualTo(CodeEntityType.FUNCTION);
			assertThat(entity.name()).isEqualTo("create_user");
		});
	}

	@Test
	void syntaxErrorsAreReportedWithoutReturningEntities() {
		assertThatThrownBy(() -> parserFactory.forLanguage("Java").orElseThrow()
				.parse("class Broken { public void missing( "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}