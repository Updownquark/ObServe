package org.observe.quick.base;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ExpressionValueType;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickApp;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickElement;
import org.qommons.ArrayUtils;
import org.qommons.Colors;
import org.qommons.LambdaUtils;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.DequeList;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigValueType;
import org.qommons.io.CircularCharBuffer;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FilePosition;
import org.qommons.io.FileUtils;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;
import org.qommons.io.SimpleXMLParser;
import org.qommons.io.SimpleXMLParser.XmlAttribute;
import org.qommons.io.SimpleXMLParser.XmlCdata;
import org.qommons.io.SimpleXMLParser.XmlComment;
import org.qommons.io.SimpleXMLParser.XmlDeclaration;
import org.qommons.io.SimpleXMLParser.XmlElementTerminal;
import org.qommons.io.SimpleXMLParser.XmlParseException;
import org.qommons.io.SimpleXMLParser.XmlProcessingInstruction;
import org.qommons.io.TextParseException;

public class Qwysiwyg {
	public static final Color ELEMENT_COLOR = Colors.green;
	public static final Color ATTRIBUTE_NAME_COLOR = Colors.maroon;
	public static final Color ATTRIBUTE_VALUE_COLOR = Colors.blue;
	public static final Color ELEMENT_VALUE_COLOR = Colors.black;
	public static final Color COMMENT_COLOR = Colors.gray;
	public static final Color MODEL_COLOR = Colors.orange;
	public static final Color MODEL_VALUE_COLOR = Colors.purple;
	public static final Color MODEL_METHOD_Color = Colors.red;
	public static final Color OPERATOR_COLOR = Colors.darkCyan;
	public static final Color LITERAL_COLOR = Colors.black;
	public static final Color EXT_LITERAL_COLOR = Colors.dodgerBlue;
	public static final Color TYPE_COLOR = Colors.darkGoldenrod;

	public final SettableValue<String> documentDisplay;
	public final ObservableValue<String> tooltip;

	private final SettableValue<String> theInternalDocumentDisplay;
	private final SettableValue<String> theInternalTooltip;
	private final SimpleObservable<Void> theDocumentReplacement;
	private final SimpleObservable<Void> theApplicationReplacement;
	private String theDocumentLocation;
	private final StringBuilder theDocumentContent;
	private QuickDocument.Def theDocumentDef;
	private QuickDocument.Interpreted theDocumentInterpreted;
	private QuickDocument theDocument;
	private ModelSetInstance theModels;
	private QuickApplication theApplication;

	private DocumentComponent theRoot;

	public Qwysiwyg() {
		theInternalDocumentDisplay = SettableValue.build(String.class).build();
		theInternalTooltip = SettableValue.build(String.class).build();
		documentDisplay = theInternalDocumentDisplay; // .unsettable(); This is settable so the tooltip can display
		tooltip = theInternalTooltip.unsettable();
		theDocumentReplacement = new SimpleObservable<>();
		theApplicationReplacement = new SimpleObservable<>();
		theDocumentContent = new StringBuilder();
	}

	public void init(String documentLocation, List<String> unmatched) {
		boolean sameDoc = Objects.equals(documentLocation, theDocumentLocation);
		theDocumentLocation = documentLocation;
		if (!sameDoc) {
			theInternalDocumentDisplay.set(null, null);
			clearDef();
		}
		if (theDocumentLocation == null)
			return;

		// TODO Replace the reporting?

		String[] clArgs = new String[unmatched.size() + 1];
		clArgs[0] = "--quick-app=" + documentLocation;
		{
			int i = 1;
			for (String clArg : unmatched)
				clArgs[i++] = clArg;
		}
		QuickApp quickApp;
		try {
			quickApp = QuickApp.parseQuickApp(clArgs);
		} catch (IOException | TextParseException | RuntimeException e) {
			logToConsole(e, null);
			clearDef();
			return;
		} catch (QonfigParseException e) {
			for (ErrorReporting.Issue issue : e.getIssues())
				logToConsole(issue);
			clearDef();
			return;
		}

		clearRender();
		try {
			// Read in the Quick file ourselves, so we can render it
			try {
				theDocumentContent.setLength(0);
				URL appFile = quickApp.resolveAppFile();
				try (InputStream in = appFile.openStream()) {
					Reader reader = new SimpleXMLParser().readXmlFile(in);
					CircularCharBuffer buffer = new CircularCharBuffer(-1);
					FileUtils.copy(reader, buffer.asWriter(), null, null);
					theDocumentContent.append(buffer);
				}
			} catch (IOException | RuntimeException e) {
				logToConsole(e, null);
				clear();
				return;
			}

			renderXml(quickApp.resolveAppFile());

			try {
				theDocumentDef = quickApp.parseQuick(theDocumentDef);
			} catch (IOException | RuntimeException e) {
				logToConsole(e, null);
				clearDef();
				renderXml(quickApp.resolveAppFile());
				renderText();
				return;
			} catch (TextParseException e) {
				logToConsole(e, quickApp.getAppFile(), e.getPosition());
				clearDef();
				return;
			} catch (QonfigParseException e) {
				for (ErrorReporting.Issue issue : e.getIssues())
					logToConsole(issue);
				clearDef();
				return;
			}

			renderDef(theRoot, theDocumentDef, null);

			ObservableModelSet.ExternalModelSet extModels = QuickApp.parseExtModels(theDocumentDef.getHead().getModels(),
				quickApp.getCommandLineArgs(), ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER));

			try {
				if (theDocumentInterpreted == null)
					theDocumentInterpreted = theDocumentDef.interpret(null);
				theDocumentInterpreted.update();
			} catch (TextParseException e) {
				logToConsole(e, quickApp.getAppFile(), e.getPosition());
				clearInterpreted();
				return;
			} catch (RuntimeException e) {
				logToConsole(e, null);
				clearInterpreted();
				return;
			}

			renderInterpreted();

			try {
				if (theDocument == null)
					theDocument = theDocumentInterpreted.create();
				theModels = theDocument.update(theDocumentInterpreted, extModels, theDocumentReplacement);
			} catch (TextParseException e) {
				logToConsole(e, quickApp.getAppFile(), e.getPosition());
				clearDocument();
				return;
			} catch (RuntimeException e) {
				logToConsole(e, null);
				clearDocument();
				return;
			}

			try {
				if (theApplication == null) {
					theApplication = quickApp.interpretQuickApplication(theDocumentInterpreted);
					theApplication.runApplication(theDocument, theApplicationReplacement);
				} else
					theApplication.update(theDocument);
			} catch (ExpressoInterpretationException | ModelInstantiationException e) {
				logToConsole(e, quickApp.getAppFile(), e.getPosition());
				clearApp();
			} catch (RuntimeException e) {
				logToConsole(e, null);
				clearApp();
			}
		} finally {
			renderText();
		}
	}

	private DocumentComponent theHovered;

	public void hover(int position) {
		if (theRoot == null)
			return;
		DocumentComponent hovered = getRenderComponent(theRoot, position);
		if (hovered == theHovered)
			return;
		theHovered = hovered;
		while (hovered != null && hovered.tooltip == null)
			hovered = hovered.parent;
		if (hovered != null)
			theInternalTooltip.set(hovered.tooltip.get(), null);
		else
			theInternalTooltip.set(null, null);
	}

	public void clicked(int position, int clickCount) {
		if (theRoot == null)
			return;
		DocumentComponent clicked = getRenderComponent(theRoot, position);
		String print = "" + clicked.start;
		if (clicked.tooltip != null)
			print += clicked.tooltip.get();
		System.out.println(print);
	}

	public void mouseExit() {
	}

	DocumentComponent getRenderComponent(DocumentComponent parent, int renderPosition) {
		if (parent.children != null) {
			int childIdx = ArrayUtils.binarySearch(parent.children, child -> Integer.compare(renderPosition, child.renderedStart));
			if (childIdx < 0) {
				childIdx = -childIdx - 2;
				if (childIdx < 0 || renderPosition >= parent.children.get(childIdx).renderedEnd)
					return parent;
			}
			return getRenderComponent(parent.children.get(childIdx), renderPosition);
		}
		return parent;
	}

	DocumentComponent getSourceComponent(DocumentComponent parent, int sourcePosition) {
		if (parent.children != null) {
			int childIdx = ArrayUtils.binarySearch(parent.children, child -> Integer.compare(sourcePosition, child.start.getPosition()));
			if (childIdx < 0) {
				childIdx = -childIdx - 2;
				if (childIdx < 0 || sourcePosition >= parent.children.get(childIdx).getEnd())
					return parent;
			}
			return getSourceComponent(parent.children.get(childIdx), sourcePosition);
		}
		return parent;
	}

	private void clear() {
		clearDef();
		theDocumentContent.setLength(0);
		clearRender();
		theInternalDocumentDisplay.set(null, null);
	}

	private void clearDef() {
		clearInterpreted();
		theDocumentDef = null;
	}

	private void clearInterpreted() {
		clearDocument();
		theDocumentInterpreted = null;
	}

	private void clearDocument() {
		clearApp();
		theDocumentReplacement.onNext(null);
		theDocument = null;
		theModels = null;
	}

	private void clearApp() {
		theApplicationReplacement.onNext(null);
		theApplication = null;
	}

	private void clearRender() {
		theRoot = DocumentComponent.createRoot();
	}

	private void logToConsole(Throwable e, LocatedPositionedContent position) {
		// TODO
		e.printStackTrace();
	}

	private void logToConsole(Throwable e, String file, FilePosition position) {
		logToConsole(e, LocatedPositionedContent.of(file, new PositionedContent.Fixed(position)));
	}

	private void logToConsole(ErrorReporting.Issue issue) {
		// TODO
		System.err.println(issue.toString());
	}

	private void renderXml(URL quickFile) {
		clearRender();
		DequeList<DocumentComponent> stack = new CircularArrayList<>();
		stack.add(theRoot);
		try (InputStream in = quickFile.openStream()) {
			new SimpleXMLParser().parseXml(in, new SimpleXMLParser.ParseHandler() {
				@Override
				public void handleDeclaration(XmlDeclaration declaration) {
					DocumentComponent declComp = stack.getLast().addChild(declaration.getContent().getPosition(0))
						.color(ELEMENT_COLOR);
					declComp.end(declaration.getContent().getPosition(declaration.getContent().length()).getPosition());
					for (String attr : declaration.getAttributes()) {
						DocumentComponent attrComp = declComp.addChild(declaration.getAttributeNamePosition(attr))
							.color(ATTRIBUTE_NAME_COLOR);
						attrComp.end(declaration.getAttributeValueEnd(attr).getPosition());
						PositionedContent value = declaration.getAttributeValue(attr);
						DocumentComponent valueComp = attrComp.addChild(value.getPosition(0)).color(ATTRIBUTE_VALUE_COLOR);
						valueComp.end(value.getPosition(value.length()).getPosition());
					}
				}

				@Override
				public void handleProcessingInstruction(XmlProcessingInstruction pi) {
					DocumentComponent piComp = stack.getLast().addChild(pi.getContent().getPosition(0)).color(ELEMENT_COLOR);
					piComp.end(pi.getContent().getPosition(pi.getContent().length()).getPosition());
					PositionedContent value = pi.getValueContent();
					if (value != null) {
						DocumentComponent valueComp = piComp.addChild(value.getPosition(0)).color(ELEMENT_VALUE_COLOR);
						valueComp.end(value.getPosition(value.length()).getPosition());
					}
				}

				@Override
				public void handleComment(XmlComment comment) {
					DocumentComponent commentComp = stack.getLast().addChild(comment.getContent().getPosition(0)).color(COMMENT_COLOR);
					commentComp.end(comment.getContent().getPosition(comment.getContent().length()).getPosition());
				}

				@Override
				public void handleElementStart(XmlElementTerminal element) {
					DocumentComponent elementComp = stack.getLast().addChild(element.getContent().getPosition(0));
					stack.add(elementComp);
					DocumentComponent elStartComp = stack.getLast().addChild(element.getContent().getPosition(element.getNameOffset()))
						.color(ELEMENT_COLOR);
					stack.add(elStartComp);
				}

				@Override
				public void handleElementOpen(String elementName, PositionedContent openEnd) {
					stack.removeLast().end(openEnd.getPosition(openEnd.length()).getPosition());
					DocumentComponent contentComponent = stack.getLast().addChild(openEnd.getPosition(openEnd.length()))//
						.color(ELEMENT_VALUE_COLOR);
					stack.add(contentComponent);
				}

				@Override
				public void handleAttribute(XmlAttribute attribute) {
					DocumentComponent attrComp = stack.getLast().addChild(attribute.getContent().getPosition(0))
						.color(ATTRIBUTE_NAME_COLOR);
					attrComp.end(attribute.getContent().getPosition(attribute.getContent().length()).getPosition());
					PositionedContent value = attribute.getValueContent();
					DocumentComponent valueComp = attrComp.addChild(value.getPosition(0)).color(ATTRIBUTE_VALUE_COLOR);
					valueComp.end(value.getPosition(value.length()).getPosition());
				}

				@Override
				public void handleElementContent(String elementName, PositionedContent elementValue) {
				}

				@Override
				public void handleCDataContent(String elementName, XmlCdata cdata) {
					DocumentComponent cdataComp = stack.getLast().addChild(cdata.getContent().getPosition(0)).color(ELEMENT_COLOR);
					PositionedContent value = cdata.getValueContent();
					DocumentComponent valueComp = cdataComp.addChild(value.getPosition(0)).color(ELEMENT_VALUE_COLOR);
					valueComp.end(value.getPosition(value.length()).getPosition());
					cdataComp.end(cdata.getContent().getPosition(cdata.getContent().length()).getPosition());
				}

				@Override
				public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
					// Remove the component either for the element start (if self-closing) or the element's content
					stack.removeLast().end(element.getContent().getPosition(0).getPosition());
					if (!selfClosing) {
						DocumentComponent elComp = stack.getLast().addChild(element.getContent().getPosition(0))
							.color(ELEMENT_COLOR);
						elComp.end(element.getContent().getPosition(element.getContent().length()).getPosition());
					}
					// Remove the component for the element
					stack.removeLast().end(element.getContent().getPosition(element.getContent().length()).getPosition());
				}
			});
		} catch (IOException | XmlParseException e) {
			// Should have been reported elsewhere, nothing to do here
		}
	}

	private void renderDef(DocumentComponent component, QuickElement.Def<?> def, String descrip) {
		if (descrip != null) {
			DocumentComponent elComponent = getSourceComponent(component, def.getElement().getPositionInFile().getPosition());
			elComponent.tooltip(descrip);
		}
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = getSourceComponent(component, position.getPosition(0).getPosition());
			DocumentComponent attrComp = attrValueComp.parent;
			QonfigValueType type = attr.getKey().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					// TODO Color different expression components
				}
			}
			String attrDescrip = def.getAttributeDescription(attr.getKey());
			attrComp.tooltip2(() -> {
				if (attrDescrip != null)
					return "<html>" + attrDescrip + "<br><br>" + renderValueType(type, 1);
				else
					return renderValueType(type, 1);
			});
		}
		if (def.getElement().getValue() != null && def.getElement().getValue().position != null) {
			PositionedContent position = def.getElement().getValue().position;
			DocumentComponent attrValueComp = getSourceComponent(component, position.getPosition(0).getPosition());
			QonfigValueType type = def.getElement().getType().getValue().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					// TODO Color different expression components
				}
			}
			attrValueComp.tooltip2(() -> renderValueType(type, 1));
		}
		for (QuickElement.Def<?> child : def.getAllChildren()) {
			String childDescrip = null;
			for (QonfigChildDef role : child.getElement().getParentRoles()) {
				String roleDescrip = def.getChildDescription(role);
				if (roleDescrip != null) {
					if (childDescrip == null)
						childDescrip = roleDescrip;
					else
						childDescrip += "<br><br>" + roleDescrip;
				}
			}
			renderDef(component, child, childDescrip);
		}
	}

	static String renderValueType(QonfigValueType type, int indent) {
		if (type instanceof QonfigValueType.Custom) {
			CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
			if (customType instanceof ExpressionValueType)
				return "expression";
			else
				return customType.getName() + " (" + customType.getClass().getName() + ")";
		} else if (type instanceof QonfigAddOn) {
			return "Add-on " + type.toString();
		} else if (type instanceof QonfigValueType.Literal)
			return "'" + ((QonfigValueType.Literal) type).getValue() + "'";
		else if (type instanceof QonfigValueType.OneOf) {
			StringBuilder str = new StringBuilder("<html>").append(type.getName()).append(": one of<br>");
			boolean first = true;
			for (QonfigValueType sub : ((QonfigValueType.OneOf) type).getComponents()) {
				if (first)
					first = false;
				else
					str.append("<br>");
				for (int i = 0; i < indent; i++)
					str.append("  ");
				str.append("\u2022 ").append(renderValueType(sub, indent + 1));
			}
			return str.toString();
		} else
			return type.getName();
	}

	private void renderInterpreted() {
		// TODO
	}

	private void renderText() {
		int lines = 0;
		for (int c = 0; c < theDocumentContent.length(); c++) {
			if (theDocumentContent.charAt(c) == '\n')
				lines++;
		}
		int lineDigits = getDigits(lines);
		StringBuilder renderedText = new StringBuilder();
		int[] model = new int[1];
		renderLineBreak(renderedText, model, 1, lineDigits);
		render(theRoot, renderedText, model, new int[1], lineDigits);
		theInternalDocumentDisplay.set(renderedText.toString(), null);
	}

	private static class DocumentRenderer {
		private static final String TAB_HTML = "&nbsp;&nbsp;&nbsp;&nbsp;";

		private final CharSequence theContent;
		private final StringBuilder theRenderedText;
		private final int theMaxLineDigits;

		private int theModelPosition;
		private int theLineNumber;

		private Color theFontColor;
		private boolean isBold;

		public DocumentRenderer(CharSequence content) {
			theContent = content;
			theRenderedText = new StringBuilder();
			int lines = 0;
			for (int c = 0; c < theContent.length(); c++) {
				if (theContent.charAt(c) == '\n')
					lines++;
			}
			theMaxLineDigits = getDigits(lines);
		}

		private int render(DocumentComponent comp) {
			comp.renderedStart = theModelPosition;
			Color preFC = theFontColor;
			boolean color = comp.fontColor != null && !comp.fontColor.equals(theFontColor);
			if (color) {
				theFontColor = comp.fontColor;
				theRenderedText.append("<font color=\"").append(Colors.toHTML(comp.fontColor)).append("\">");
			}
			boolean linkEnded = !comp.link;
			boolean bold = comp.bold && !isBold;
			if (bold) {
				isBold = true;
				theRenderedText.append("<b>");
			}
			if (comp.link)
				theRenderedText.append("<u>");
			int prevPos = comp.start.getPosition();
			int end = comp.getEnd() < 0 ? theContent.length() : comp.getEnd();
			if (comp.children != null) {
				for (DocumentComponent child : comp.children) {
					renderText(prevPos, child.start.getPosition());
					if (!linkEnded) {
						linkEnded = true;
						theRenderedText.append("</u>");
					}
					prevPos = render(child);
				}
			}
			renderText(prevPos, end);
			if (!linkEnded)
				theRenderedText.append("</u>");
			if (bold) {
				theRenderedText.append("</b>");
				isBold = false;
			}
			if (color) {
				theRenderedText.append("</font>");
				theFontColor = preFC;
			}
			comp.renderedEnd = theModelPosition;
			return end;
		}

		private void renderText(int from, int to) {
			for (int c = from; c < to; c++) {
				char ch = theContent.charAt(c);
				switch (ch) {
				case '\n':
					theRenderedText.append("<br>\n");
					theModelPosition++;
					theLineNumber++;
					renderLineBreak();
					break;
				case '\t':
					theRenderedText.append(TAB_HTML);
					theModelPosition += 4;
					break;
				case '<':
					theRenderedText.append("&lt;");
					break;
				case '"':
					theRenderedText.append("&quot;");
					break;
				case '\'':
					theRenderedText.append("&apos;");
					break;
				case '&':
					theRenderedText.append("&amp;");
					break;
				default:
					theRenderedText.append(ch);
					break;
				}
			}
		}

		private void renderLineBreak() {
			theRenderedText.append("Line ").append(theLineNumber).append(':');
			int digs = getDigits(theLineNumber);
			for (int i = theMaxLineDigits; i > digs; i--)
				theRenderedText.append("&nbsp;");
			theModelPosition += 6 + theMaxLineDigits;
		}

		private static int getDigits(int number) {
			int digs = 1;
			for (int num = 10; num >= 0 && num <= number; num *= 10, digs++) {
			}
			return digs;
		}
	}

	private int render(DocumentComponent comp, StringBuilder renderedText, int[] model, int[] line, int lineDigits) {
		comp.renderedStart = model[0];
		if (comp.fontColor != null)
			renderedText.append("<font color=\"").append(Colors.toHTML(comp.fontColor)).append("\">");
		boolean linkEnded = !comp.link;
		if (comp.bold)
			renderedText.append("<b>");
		if (comp.link)
			renderedText.append("<u>");
		int prevPos = comp.start.getPosition();
		int end = comp.getEnd() < 0 ? theDocumentContent.length() : comp.getEnd();
		if (comp.children != null) {
			for (DocumentComponent child : comp.children) {
				renderText(prevPos, child.start.getPosition(), renderedText, model, line, lineDigits);
				if (!linkEnded) {
					linkEnded = true;
					renderedText.append("</u>");
				}
				prevPos = render(child, renderedText, model, line, lineDigits);
			}
		}
		renderText(prevPos, end, renderedText, model, line, lineDigits);
		if (!linkEnded)
			renderedText.append("</u>");
		if (comp.bold)
			renderedText.append("</b>");
		if (comp.fontColor != null)
			renderedText.append("</font>");
		comp.renderedEnd = model[0];
		return end;
	}

	private static final String TAB_HTML = "&nbsp;&nbsp;&nbsp;&nbsp;";

	private void renderText(int from, int to, StringBuilder renderedText, int[] model, int[] line, int lineDigits) {
		for (int c = from; c < to; c++) {
			char ch = theDocumentContent.charAt(c);
			switch (ch) {
			case '\n':
				renderedText.append("<br>\n");
				model[0]++;
				line[0]++;
				renderLineBreak(renderedText, model, line[0] + 1, lineDigits);
				break;
			case '\t':
				renderedText.append(TAB_HTML);
				model[0] += 4;
				break;
			case '<':
				renderedText.append("&lt;");
				model[0]++;
				break;
			case '"':
				renderedText.append("&quot;");
				model[0]++;
				break;
			case '\'':
				renderedText.append("&apos;");
				model[0]++;
				break;
			case '&':
				renderedText.append("&amp;");
				model[0]++;
				break;
			default:
				renderedText.append(ch);
				model[0]++;
				break;
			}
		}
	}

	private static void renderLineBreak(StringBuilder renderedText, int[] model, int lineNo, int lineDigits) {
		renderedText.append("Line ").append(lineNo).append(':');
		int digs = getDigits(lineNo);
		for (int i = lineDigits; i > digs; i--)
			renderedText.append("&nbsp;");
		model[0] += 6 + lineDigits;
	}

	private static int getDigits(int number) {
		int digs = 1;
		for (int num = 10; num >= 0 && num <= number; num *= 10, digs++) {
		}
		return digs;
	}

	static class DocumentComponent {
		final DocumentComponent parent;
		final FilePosition start;
		private int end;
		boolean link;
		boolean bold;
		Color fontColor;
		Supplier<String> tooltip;
		List<DocumentComponent> children;
		int renderedStart;
		int renderedEnd;

		static DocumentComponent createRoot() {
			return new DocumentComponent(null, FilePosition.START);
		}

		DocumentComponent(DocumentComponent parent, FilePosition start) {
			this.parent = parent;
			this.start = start;
			end = -1;
		}

		public int getEnd() {
			return end;
		}

		public ObservableValue<String> getTooltip() {
			return null;
		}

		DocumentComponent addChild(FilePosition childStart) {
			if (start.getPosition() > childStart.getPosition())
				throw new IllegalArgumentException("Adding child before the parent");
			if (children == null)
				children = new ArrayList<>();
			else if (!children.isEmpty() && childStart.getPosition() < children.get(children.size() - 1).end)
				throw new IllegalArgumentException("Adding child before end of the previous one");
			DocumentComponent child = new DocumentComponent(this, childStart);
			children.add(child);
			return child;
		}

		void end(int e) {
			if (e < start.getPosition())
				throw new IllegalArgumentException("Ending before beginning");
			if (children != null && !children.isEmpty() && e < children.get(children.size() - 1).end)
				throw new IllegalArgumentException("Ending parent before last child");
			this.end = e;
		}

		DocumentComponent color(Color color) {
			fontColor = color;
			return this;
		}

		DocumentComponent link() {
			link = true;
			return this;
		}

		DocumentComponent bold() {
			bold = true;
			return this;
		}

		DocumentComponent tooltip(String tt) {
			tooltip = LambdaUtils.constantSupplier(tt, tt, null);
			return this;
		}

		DocumentComponent tooltip2(Supplier<String> tt) {
			tooltip = tt;
			return this;
		}

		public void followLink() {
		}
	}
}
