package com.example.backend.parser.java;

import com.example.backend.entity.CodeEntityType;
import com.example.backend.parser.AbstractTreeSitterCodeParser;
import com.example.backend.parser.model.ParsedEntity;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterJava;

@Component
public class JavaCodeParser extends AbstractTreeSitterCodeParser {

	public JavaCodeParser() {
		super(new TreeSitterJava());
	}

	@Override
	public boolean supports(String language) {
		return "Java".equalsIgnoreCase(language);
	}

	@Override
	protected ParsedEntity mapNode(TSNode node, String source, byte[] sourceBytes) {
		return switch (node.getType()) {
			case "package_declaration" -> entity(node, source, sourceBytes, CodeEntityType.PACKAGE,
					fieldName(node, source, sourceBytes));
			case "import_declaration" -> entity(node, source, sourceBytes, CodeEntityType.IMPORT,
					textName(node, source, sourceBytes));
			case "class_declaration" -> entity(node, source, sourceBytes, CodeEntityType.CLASS,
					fieldName(node, source, sourceBytes));
			case "interface_declaration" -> entity(node, source, sourceBytes, CodeEntityType.INTERFACE,
					fieldName(node, source, sourceBytes));
			case "enum_declaration" -> entity(node, source, sourceBytes, CodeEntityType.ENUM,
					fieldName(node, source, sourceBytes));
			case "method_declaration" -> entity(node, source, sourceBytes, CodeEntityType.METHOD,
					fieldName(node, source, sourceBytes));
			case "constructor_declaration" -> entity(node, source, sourceBytes, CodeEntityType.CONSTRUCTOR,
					fieldName(node, source, sourceBytes));
			case "field_declaration" -> entity(node, source, sourceBytes, CodeEntityType.FIELD,
					fieldName(node, source, sourceBytes));
			case "annotation" -> entity(node, source, sourceBytes, CodeEntityType.ANNOTATION,
					textName(node, source, sourceBytes));
			default -> null;
		};
	}

	private String textName(TSNode node, String source, byte[] sourceBytes) {
		return text(node, source, sourceBytes).replace("import", "").replace("package", "")
				.replace(";", "").trim();
	}
}