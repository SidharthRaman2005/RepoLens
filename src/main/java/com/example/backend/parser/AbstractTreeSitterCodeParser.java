package com.example.backend.parser;

import com.example.backend.entity.CodeEntityType;
import com.example.backend.parser.model.ParseResult;
import com.example.backend.parser.model.ParsedEntity;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class AbstractTreeSitterCodeParser implements CodeParser {

	private final TSLanguage language;

	protected AbstractTreeSitterCodeParser(TSLanguage language) {
		this.language = language;
	}

	@Override
	public ParseResult parse(String source) {
		if (source == null) {
			throw new IllegalArgumentException("Source cannot be null");
		}
		TSParser parser = new TSParser();
		if (!parser.setLanguage(language)) {
			throw new IllegalStateException("Tree-sitter language could not be configured");
		}
		TSTree tree = parser.parseString(null, source);
		TSNode root = tree.getRootNode();
		if (root.hasError()) {
			throw new IllegalArgumentException("Tree-sitter reported a syntax error");
		}
		List<ParsedEntity> entities = new ArrayList<>();
		byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
		walk(root, source, sourceBytes, entities);
		return new ParseResult(entities);
	}

	private void walk(TSNode node, String source, byte[] sourceBytes, List<ParsedEntity> entities) {
		ParsedEntity entity = mapNode(node, source, sourceBytes);
		if (entity != null) {
			entities.add(entity);
		}
		for (int index = 0; index < node.getNamedChildCount(); index++) {
			walk(node.getNamedChild(index), source, sourceBytes, entities);
		}
	}

	protected abstract ParsedEntity mapNode(TSNode node, String source, byte[] sourceBytes);

	protected ParsedEntity entity(TSNode node, String source, byte[] sourceBytes, CodeEntityType type, String name) {
		String text = text(node, source, sourceBytes).trim();
		String relationshipType = null;
		String relatedName = null;
		if (type == CodeEntityType.METHOD || type == CodeEntityType.CONSTRUCTOR
				|| type == CodeEntityType.FIELD || type == CodeEntityType.VARIABLE) {
			relatedName = containingTypeName(node, source, sourceBytes);
			if (relatedName != null) {
				relationshipType = "CONTAINS";
			}
		}
		if (type == CodeEntityType.CLASS || type == CodeEntityType.INTERFACE || type == CodeEntityType.ENUM) {
			String parent = relationshipName(text, "extends");
			if (parent != null) {
				relationshipType = "EXTENDS";
				relatedName = parent;
			} else {
				parent = relationshipName(text, "implements");
				if (parent != null) {
					relationshipType = "IMPLEMENTS";
					relatedName = parent;
				}
			}
		}
		return new ParsedEntity(type, name == null || name.isBlank() ? text : name,
				node.getStartPoint().getRow() + 1, node.getEndPoint().getRow() + 1,
				visibility(text), text, relationshipType, relatedName, null);
	}

	protected String fieldName(TSNode node, String source, byte[] sourceBytes) {
		TSNode nameNode = node.getChildByFieldName("name");
		return nameNode == null || nameNode.isNull() ? null : text(nameNode, source, sourceBytes).trim();
	}

	protected String text(TSNode node, String source, byte[] sourceBytes) {
		int start = Math.max(0, Math.min(node.getStartByte(), sourceBytes.length));
		int end = Math.max(start, Math.min(node.getEndByte(), sourceBytes.length));
		return new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
	}

	private String containingTypeName(TSNode node, String source, byte[] sourceBytes) {
		TSNode parent = node.getParent();
		while (parent != null && !parent.isNull()) {
			if (parent.getType().equals("class_declaration") || parent.getType().equals("class_definition")
					|| parent.getType().equals("interface_declaration") || parent.getType().equals("enum_declaration")) {
				return fieldName(parent, source, sourceBytes);
			}
			parent = parent.getParent();
		}
		return null;
	}

	private String visibility(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.matches("^(public|export)\\b.*")) return "PUBLIC";
		if (lower.matches("^(private)\\b.*")) return "PRIVATE";
		if (lower.matches("^(protected)\\b.*")) return "PROTECTED";
		return "PACKAGE";
	}

	private String relationshipName(String text, String keyword) {
		String lower = text.toLowerCase(Locale.ROOT);
		int start = lower.indexOf(keyword);
		if (start < 0) return null;
		String remainder = text.substring(start + keyword.length()).trim();
		String[] parts = remainder.split("[\\s{:,]+", 2);
		return parts.length == 0 || parts[0].isBlank() ? null : parts[0];
	}
}