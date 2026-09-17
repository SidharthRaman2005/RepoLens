package com.example.backend.parser.python;

import com.example.backend.entity.CodeEntityType;
import com.example.backend.parser.AbstractTreeSitterCodeParser;
import com.example.backend.parser.model.ParsedEntity;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterPython;

@Component
public class PythonCodeParser extends AbstractTreeSitterCodeParser {

	public PythonCodeParser() {
		super(new TreeSitterPython());
	}

	@Override
	public boolean supports(String language) {
		return "Python".equalsIgnoreCase(language);
	}

	@Override
	protected ParsedEntity mapNode(TSNode node, String source, byte[] sourceBytes) {
		return switch (node.getType()) {
			case "import_statement", "import_from_statement" -> entity(node, source, sourceBytes,
					CodeEntityType.IMPORT, text(node, source, sourceBytes).trim());
			case "class_definition" -> entity(node, source, sourceBytes, CodeEntityType.CLASS,
					fieldName(node, source, sourceBytes));
			case "function_definition" -> entity(node, source, sourceBytes, CodeEntityType.FUNCTION,
					fieldName(node, source, sourceBytes));
			case "assignment", "annotated_assignment" -> entity(node, source, sourceBytes,
					CodeEntityType.VARIABLE, fieldName(node, source, sourceBytes));
			default -> null;
		};
	}
}