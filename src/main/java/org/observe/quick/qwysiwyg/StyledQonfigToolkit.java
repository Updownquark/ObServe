package org.observe.quick.qwysiwyg;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.ParserCallback;

import org.observe.SettableValue;
import org.qommons.StringUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigType;
import org.qommons.config.QonfigValueType;

public class StyledQonfigToolkit {
	public interface Node {
		public String getNodeClass();

		public String getTooltip();

		public List<? extends Node> getChildren();

		public Object getLinkTarget();
	}

	private static HTMLEditorKit.Parser HTML_PARSER = new HtmlParserAccess().getParser();
	public final QonfigToolkit toolkit;
	public final SettableValue<Node> selected;
	private final GenericNode theRoot;
	private final Map<QonfigType, Node> theLinkTargets;

	public StyledQonfigToolkit(QonfigToolkit toolkit) {
		this.toolkit = toolkit;
		selected = SettableValue.<Node> build().build();
		theRoot = new GenericNode("root");
		theLinkTargets = new HashMap<>();

		theRoot.addChild("toolkit-name").withContent(toolkit + "\n\n");
		if (toolkit.getDescription() != null)
			printHtml(theRoot.addChild("toolkit-descrip"), toolkit.getDescription() + "<br>");
		theRoot.addChild("").withContent("\n");

		if (!toolkit.getDependencies().isEmpty())
			docDependencies(theRoot.addChild("toolkit"));
		if (!toolkit.getDeclaredAttributeTypes().isEmpty())
			docValueTypes(theRoot.addChild("value-type"));
		if (!toolkit.getDeclaredAddOns().isEmpty()) {
			GenericNode addOnsNode = theRoot.addChild("add-on");
			addOnsNode.addChild("h1").withContent("Add-Ons\n\n");
			docElements(addOnsNode, toolkit.getDeclaredAddOns().values());
		}
		if (!toolkit.getDeclaredAttributeTypes().isEmpty()) {
			GenericNode elementsNode = theRoot.addChild("element-def");
			elementsNode.addChild("h1").withContent("Elements\n\n");
			docElements(elementsNode, toolkit.getDeclaredElements().values());
		}
	}

	public Node getRoot() {
		return theRoot;
	}

	public void goToReference(QonfigType reference) {
		Node found = theLinkTargets.get(reference);
		if (found != null)
			selected.set(found, null);
		else
			System.err.println("Link reference not found: " + reference.getClass().getSimpleName() + " " + reference);
	}

	private void docDependencies(GenericNode node) {
		node.addChild("h1").withContent("Toolkit Dependencies\n");
		for (Map.Entry<String, QonfigToolkit> dep : toolkit.getDependencies().entrySet()) {
			node.addChild("").withContent("\u2022 ");
			printReference(node, dep.getValue(), null);
			node.addChild("").withContent(" as " + dep.getKey() + "\n");
		}
		node.addChild("").withContent("\n\n");
	}

	private void docValueTypes(GenericNode node) {
		node.addChild("h1").withContent("Value Types\n\n");
		for (QonfigValueType.Declared valueType : toolkit.getDeclaredAttributeTypes().values()) {
			theLinkTargets.put(valueType, node.addChild("h2").withContent(valueType.getName() + "\n"));
			if (valueType.getDescription() != null)
				printHtml(node, valueType.getDescription() + "<br>");
		}

		node.addChild("").withContent("\n\n");
	}


	private void docElements(GenericNode node, Collection<? extends QonfigElementOrAddOn> elements) {
		for (QonfigElementOrAddOn element : elements) {
			theLinkTargets.put(element, node.addChild("h2").withContent(element.getName() + "\n"));

			if (element.getDescription() != null) {
				printHtml(node, element.getDescription());
			}

			if (element.getSuperElement() != null) {
				node.addChild("b").withContent("Extends: ");
				printReference(node, element.getSuperElement(), null).append("\n");
			}

			if (!element.getInheritance().isEmpty()) {
				node.addChild("b").withContent("Inherits:\n");
				for (QonfigAddOn inh : element.getInheritance()) {
					node.addChild("").withContent(" \u2022 ");
					printReference(node, inh, null).append("\n");
				}
			}

			if (!element.getDeclaredAttributes().isEmpty()) {
				node.addChild("b").withContent("Declared Attributes:\n");
				for (QonfigAttributeDef.Declared attr : element.getDeclaredAttributes().values()) {
					node.addChild("").withContent(" \u2022 " + attr.getName() + " (" + attr.getSpecification() + ")\n");
					if (attr.getDescription() != null)
						printHtml(node.addChild("").withContent("    "), attr.getDescription());
					node.addChild("").withContent("    Type: ");
					printReference(node, attr.getType(), null).append("\n");
					if (attr.getDefaultValue() != null) {
						if (attr.getDefaultValue() instanceof QonfigAddOn)
							printReference(node.addChild("    Default Value: "), attr.getDefaultValue(), null).append("\n");
						else
							node.addChild("    Default Value: " + attr.getDefaultValueContent().toString() + "\n");
					}
				}
			}

			if (element.getValueSpec() != null && (element.getSuperElement() == null || element.getSuperElement().getValueSpec() == null)) {
				node.addChild("b").withContent("Declared Value (" + element.getValueSpec().getSpecification() + "):");
				if (element.getValueSpec().getDescription() != null) {
					printHtml(node.addChild("").withContent("    "), element.getValueSpec().getDescription());
				}
				node.addChild("").withContent("    Type: ");
				printReference(node, element.getValue().getType(), null).append("\n");
				if (element.getValueSpec().getDefaultValue() != null) {
					if (element.getValueSpec().getDefaultValue() instanceof QonfigAddOn)
						printReference(node.addChild("    Default Value: "), element.getValueSpec().getDefaultValue(), null).append("\n");
					else
						node.addChild("    Default Value: " + element.getValueSpec().getDefaultValueContent().toString() + "\n");
				}
			}

			if (!element.getDeclaredChildren().isEmpty()) {
				node.addChild("b").withContent("Declared Children:\n");
				for (QonfigChildDef.Declared child : element.getDeclaredChildren().values()) {
					GenericNode childNode = node.addChild("").withContent(" \u2022 " + child.getName());
					switch (child.getMin()) {
					case 0:
						switch (child.getMax()) {
						case 0:
							childNode.append(" (forbidden)");
							break;
						case 1:
							childNode.append(" (optional)");
							break;
						case Integer.MAX_VALUE:
							childNode.append(" (any number)");
							break;
						default:
							childNode.append(" (up to " + child.getMax() + " times)");
							break;
						}
						break;
					case 1:
						switch (child.getMax()) {
						case 1:
							childNode.append(" (required)");
							break;
						case Integer.MAX_VALUE:
							childNode.append(" (at least once)");
							break;
						default:
							childNode.append(" (1 to " + child.getMax() + " times)");
							break;
						}
						break;
					default:
						if (child.getMax() == child.getMin())
							childNode.append(" (exactly " + child.getMin() + " times)");
						else if (child.getMax() == Integer.MAX_VALUE)
							childNode.append(" (at least " + child.getMin() + " times)");
						else
							childNode.append(" (" + child.getMin() + " to " + child.getMax() + " times)");
					}
					childNode.append("\n");
					if (child.getDescription() != null) {
						childNode.append("    ");
						printHtml(childNode, child.getDescription());
					}
					if (child.getType() != null) {
						childNode.addChild("").withContent("    Type: ");
						printReference(childNode, child.getType(), null).append("\n");
					} else
						childNode.addChild("").withContent("    No type specified\n");
					if (!child.getRequirement().isEmpty()) {
						childNode.addChild("").withContent("    Requires:\n");
						for (QonfigAddOn inh : child.getRequirement()) {
							childNode.addChild("").withContent("     \u2022 ");
							printReference(childNode, inh, null).append("\n");
						}
					}
					if (!child.getInheritance().isEmpty()) {
						childNode.addChild("").withContent("    Inherits:\n");
						for (QonfigAddOn inh : child.getInheritance()) {
							childNode.addChild("").withContent("     \u2022 ");
							printReference(childNode, inh, null).append("\n");
						}
					}
				}
			}

			node.addChild("").withContent("\n");
		}
		node.addChild("").withContent("\n\n");
	}

	private GenericNode printReference(GenericNode node, Object ref, String text) {
		if (ref instanceof QonfigToolkit)
			return node.addChild("toolkit-ref").withContent(text != null ? text : ((QonfigToolkit) ref).toString()).withLinkTarget(ref);
		else if (ref instanceof QonfigValueType.Declared) {
			QonfigValueType.Declared value = (QonfigValueType.Declared) ref;
			if (text == null && value.getDeclarer() != toolkit) {
				printReference(node, value.getDeclarer(), null);
				node.addChild("").withContent(".");
			}
			return node.addChild("value-type-ref").withContent(text != null ? text : value.getName()).withLinkTarget(value);
		} else if (ref instanceof QonfigAddOn) {
			QonfigAddOn addOn = (QonfigAddOn) ref;
			if (addOn.getDeclarer() != toolkit) {
				printReference(node, addOn.getDeclarer(), null);
				node.addChild("").withContent(".");
			}
			return node.addChild("add-on-ref").withContent(text != null ? text : addOn.getName()).withLinkTarget(addOn);
		} else if (ref instanceof QonfigElementDef) {
			QonfigElementDef element = (QonfigElementDef) ref;
			if (element.getDeclarer() != toolkit) {
				printReference(node, element.getDeclarer(), null);
				node.addChild("").withContent(".");
			}
			return node.addChild("element-def-ref").withContent(text != null ? text : element.getName()).withLinkTarget(element);
		} else
			return node.addChild("").withContent(ref.toString());
	}

	private void printHtml(GenericNode node, String descrip) {
		try {
			HTML_PARSER.parse(new StringReader(descrip), new ParserCallback() {
				private final LinkedList<GenericNode> theStack = new LinkedList<>();
				private final LinkedList<Integer> theListNumberStack;

				{
					theStack.add(node);
					theListNumberStack = new LinkedList<>();
				}

				@Override
				public void handleText(char[] data, int pos) {
					FoundReference ref = findReference(data);
					while (ref != null) {
						GenericNode refNode = theStack.getLast().addChild("").withContent(new String(data, 0, ref.start));
						printReference(refNode, ref.reference, new String(data, ref.start, ref.end - ref.start));
						data = Arrays.copyOfRange(data, ref.end, data.length);
						ref = findReference(data);
					}
					if (data.length > 0)
						theStack.getLast().addChild("").withContent(new String(data));
				}

				@Override
				public void handleComment(char[] data, int pos) {
					theStack.getLast().addChild("html-comment").withContent(new String(data));
				}

				@Override
				public void handleStartTag(Tag t, MutableAttributeSet a, int pos) {
					switch (t.toString().toLowerCase()) {
					case "br":
						theStack.getLast().addChild("").withContent("\n");
						break;
					case "li":
						theStack.getLast().addChild("").withContent(nextOrderedListHeader());
						break;
					case "ul":
						theListNumberStack.add(-1);
						break; // No effect
					case "ol":
						theListNumberStack.add(1);
						break;
					case "b":
					case "i":
					case "u":
						theStack.add(theStack.getLast().addChild(t.toString()));
						break;
					}
				}

				@Override
				public void handleEndTag(Tag t, int pos) {
					switch (t.toString().toLowerCase()) {
					case "li":
						theStack.getLast().addChild("").withContent("\n");
						break;
					case "ul":
					case "ol":
						if (!theListNumberStack.isEmpty())
							theListNumberStack.removeLast();
						else
							theStack.getLast().addChild("error").withContent("?");
						break;
					case "b":
					case "i":
					case "u":
						theStack.removeLast();
						break;
					}
				}

				@Override
				public void handleSimpleTag(Tag t, MutableAttributeSet a, int pos) {
					switch (t.toString().toLowerCase()) {
					case "br":
						theStack.getLast().addChild("").withContent("\n");
						break;
					}
				}

				@Override
				public void handleEndOfLineString(String eol) {
					theStack.getLast().addChild("").withContent("\n");
				}

				private String nextOrderedListHeader() {
					if (theListNumberStack.isEmpty())
						return "\u2022";
					Integer num = theListNumberStack.getLast();
					theListNumberStack.removeLast();
					if (num < 0)
						return "\u2022"; // Unordered list

					theListNumberStack.add(num + 1);
					int depth = 0;
					for (Integer ln : theListNumberStack) {
						if (ln >= 0)
							depth++;
					}
					switch (depth % 4) {
					case 1:
						return num.toString();
					case 2:
						return letterListHeader('a', num - 1);
					case 3:
						return StringUtils.toRomanNumeral(num);
					default:
						return letterListHeader('A', num - 1);
					}
				}

				private String letterListHeader(char start, int num) {
					if (num < 26)
						return "" + (char) (start + num);
					StringBuilder s = new StringBuilder();
					while (num > 0) {
						s.insert(0, (char) (start + (num % 26)));
						num /= 26;
					}
					return s.toString();
				}

				private FoundReference findReference(char[] data) {
					int refStart = -1;
					for (int i = 0; i < data.length; i++) {
						if (data[i] == '<')
							refStart = i;
						else if (data[i] == '>') {
							if (refStart >= 0) {
								QonfigType ref = findReference(new String(data, refStart + 1, i - refStart - 1));
								if (ref != null)
									return new FoundReference(refStart, i + 1, ref);
							}
							refStart = -1;
						} else if (data[i] >= 'a' && data[i] <= 'z') {//
						} else if (data[i] >= 'A' && data[i] <= 'Z') {//
						} else if (data[i] >= '0' && data[i] <= '9') {//
						} else if (data[i] == '_' || data[i] == '-' || data[i] == '.' || data[i] == ':') {//
						} else
							refStart = -1;
					}
					return null;
				}

				private QonfigType findReference(String ref) {
					try {
						QonfigType found;
						int dot = ref.indexOf('.');
						if (dot < 0) {
							found = toolkit.getElementOrAddOn(ref);
							if (found == null)
								found = toolkit.getAttributeType(ref);
						} else {
							QonfigToolkit tk = toolkit.getDependency(ref.substring(0, dot));
							if (tk != null) {
								found = tk.getElementOrAddOn(ref.substring(dot + 1));
								if (found == null)
									found = tk.getAttributeType(ref.substring(dot + 1));
							} else
								found = null;
						}
						return found;
					} catch (ParseException | IllegalArgumentException e) {
						System.err.println("Bad reference in docs: " + ref + ": " + e.getMessage());
						return null;
					}
				}
			}, true);
		} catch (IOException e) {
			throw new IllegalStateException("StringReader threw an IOException", e);
		}
	}

	static class FoundReference {
		final int start;
		final int end;
		final QonfigType reference;

		FoundReference(int start, int end, QonfigType reference) {
			this.start = start;
			this.end = end;
			this.reference = reference;
		}
	}

	static class GenericNode implements Node {
		private final String theNodeClass;
		private String theTooltip;
		private String theContent;
		private final List<Node> theChildren;
		private Object theLinkTarget;

		public GenericNode(String nodeClass) {
			theNodeClass = nodeClass;
			theContent = "";
			theChildren = new ArrayList<>();
		}

		@Override
		public String getNodeClass() {
			return theNodeClass;
		}

		@Override
		public String getTooltip() {
			return theTooltip;
		}

		@Override
		public String toString() {
			return theContent;
		}

		@Override
		public Object getLinkTarget() {
			return theLinkTarget;
		}

		@Override
		public List<Node> getChildren() {
			return Collections.unmodifiableList(theChildren);
		}

		public GenericNode withContent(String content) {
			theContent = content;
			return this;
		}

		public GenericNode append(String content) {
			theContent += content;
			return this;
		}

		public GenericNode withTooltip(String tooltip) {
			theTooltip = tooltip;
			return this;
		}

		public GenericNode withLinkTarget(Object linkTarget) {
			theLinkTarget = linkTarget;
			return this;
		}

		public void addChild(Node child) {
			theChildren.add(child);
		}

		public GenericNode addChild(String nodeClass) {
			GenericNode child = new GenericNode(nodeClass);
			addChild(child);
			return child;
		}
	}

	private static class HtmlParserAccess extends HTMLEditorKit {
		@Override
		public HTMLEditorKit.Parser getParser() {
			return super.getParser();
		}
	}
}
