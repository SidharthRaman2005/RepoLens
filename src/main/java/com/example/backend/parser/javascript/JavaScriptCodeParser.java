package com.example.backend.parser.javascript;

import com.example.backend.entity.CodeEntityType;
import com.example.backend.parser.AbstractTreeSitterCodeParser;
import com.example.backend.parser.model.ParsedEntity;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterJavascript;

@Component
public class JavaScriptCodeParser extends AbstractTreeSitterCodeParser {

	public JavaScriptCodeParser() {
		super(new TreeSitterJavascript());
	}

	@Override
	public boolean supports(String language) {
		return "JavaScript".equalsIgnoreCase(language);
	}

	@Override
	protected ParsedEntity mapNode(TSNode node, String source, byte[] sourceBytes) {
		return switch (node.getType()) {
			case "import_statement" -> entity(node, source, sourceBytes, CodeEntityType.IMPORT,
					text(node, source, sourceBytes).trim());
			case "export_statement" -> entity(node, source, sourceBytes, CodeEntityType.VARIABLE,
					text(node, source, sourceBytes).trim());
			case "function_declaration", "function_expression", "arrow_function" -> entity(node, source,
					sourceBytes, CodeEntityType.FUNCTION, fieldName(node, source, sourceBytes));
			case "class_declaration", "class" -> entity(node, source, sourceBytes, CodeEntityType.CLASS,
					fieldName(node, source, sourceBytes));
			case "method_definition" -> entity(node, source, sourceBytes, CodeEntityType.METHOD,
					fieldName(node, source, sourceBytes));
			case "lexical_declaration", "variable_declaration" -> entity(node, source, sourceBytes,
					CodeEntityType.VARIABLE, fieldName(node, source, sourceBytes));
			default -> null;
		};
	}
}