package org.observe.quick.base;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickApp;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.qommons.Colors;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.DequeList;
import org.qommons.config.QonfigParseException;
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

	public final ObservableValue<String> documentDisplay;
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
		documentDisplay = theInternalDocumentDisplay.unsettable();
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

			theRoot = new DocumentComponent(new FilePosition(0, 0, 0), false, null);
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

			renderDef();

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

	public void hover(int row, int column) {
	}

	public void clicked(int row, int column, int clickCount) {
	}

	public void mouseExit() {
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
		theRoot = new DocumentComponent(new FilePosition(0, 0, 0), false, null);
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
					DocumentComponent declComp = stack.getLast().addChild(declaration.getContent().getPosition(0), false, ELEMENT_COLOR);
					declComp.end = declaration.getContent().getPosition(declaration.getContent().length());
					for (String attr : declaration.getAttributes()) {
						DocumentComponent attrComp = declComp.addChild(declaration.getAttributeNamePosition(attr), false,
							ATTRIBUTE_NAME_COLOR);
						attrComp.end = declaration.getAttributeValueEnd(attr);
						PositionedContent value = declaration.getAttributeValue(attr);
						DocumentComponent valueComp = attrComp.addChild(value.getPosition(0), false, ATTRIBUTE_VALUE_COLOR);
						valueComp.end = value.getPosition(value.length());
					}
				}

				@Override
				public void handleProcessingInstruction(XmlProcessingInstruction pi) {
					DocumentComponent piComp = stack.getLast().addChild(pi.getContent().getPosition(0), false, ELEMENT_COLOR);
					PositionedContent value = pi.getValueContent();
					if (value != null) {
						DocumentComponent valueComp = piComp.addChild(value.getPosition(0), false, ELEMENT_VALUE_COLOR);
						valueComp.end = value.getPosition(value.length());
					}
				}

				@Override
				public void handleComment(XmlComment comment) {
					DocumentComponent commentComp = stack.getLast().addChild(comment.getContent().getPosition(0), false, COMMENT_COLOR);
					commentComp.end = comment.getContent().getPosition(comment.getContent().length());
				}

				@Override
				public void handleElementStart(XmlElementTerminal element) {
					DocumentComponent elComp = stack.getLast().addChild(element.getContent().getPosition(0), false, ELEMENT_COLOR);
					stack.add(elComp);
				}

				@Override
				public void handleElementOpen(String elementName, PositionedContent openEnd) {
					stack.pop().end = openEnd.getPosition(openEnd.length());
				}

				@Override
				public void handleAttribute(XmlAttribute attribute) {
					DocumentComponent attrComp = stack.getLast().addChild(attribute.getContent().getPosition(0), false,
						ATTRIBUTE_NAME_COLOR);
					attrComp.end = attribute.getContent().getPosition(attribute.getContent().length());
					PositionedContent value = attribute.getValueContent();
					DocumentComponent valueComp = attrComp.addChild(value.getPosition(0), false, ATTRIBUTE_VALUE_COLOR);
					valueComp.end = value.getPosition(value.length());
				}

				@Override
				public void handleElementContent(String elementName, PositionedContent elementValue) {
				}

				@Override
				public void handleCDataContent(String elementName, XmlCdata cdata) {
					DocumentComponent cdataComp = stack.getLast().addChild(cdata.getContent().getPosition(0), false, ELEMENT_COLOR);
					PositionedContent value = cdata.getValueContent();
					DocumentComponent valueComp = cdataComp.addChild(value.getPosition(0), false, ELEMENT_VALUE_COLOR);
					valueComp.end = value.getPosition(value.length());
				}

				@Override
				public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
					DocumentComponent elComp = stack.getLast().addChild(element.getContent().getPosition(0), false, ELEMENT_COLOR);
					elComp.end = element.getContent().getPosition(element.getContent().length());
				}
			});
		} catch (IOException | XmlParseException e) {
			// Should have been reported elsewhere, nothing to do here
		}
	}

	private void renderDef() {
		// TODO
	}

	private void renderInterpreted() {
		// TODO
	}

	private void renderText() {
		StringBuilder renderedText = new StringBuilder();
		render(theRoot, renderedText);
		theInternalDocumentDisplay.set(renderedText.toString(), null);
	}

	private int render(DocumentComponent comp, StringBuilder renderedText) {
		if (comp.fontColor != null)
			renderedText.append("<font color=\"").append(Colors.toHTML(comp.fontColor)).append("\">");
		boolean linkEnded = !comp.link;
		if (comp.link)
			renderedText.append("<u>");
		int prevPos = comp.start.getPosition();
		int end = comp.end == null ? theDocumentContent.length() : comp.end.getPosition();
		if (comp.children != null) {
			for (DocumentComponent child : comp.children) {
				renderText(prevPos, child.start.getPosition(), renderedText);
				if (!linkEnded) {
					linkEnded = true;
					renderedText.append("</u>");
				}
				prevPos = render(child, renderedText);
			}
		}
		renderText(prevPos, end, renderedText);
		if (!linkEnded)
			renderedText.append("</u>");
		if (comp.fontColor != null)
			renderedText.append("</font>");
		return end;
	}

	private static final String TAB_HTML = "&nbsp;&nbsp;&nbsp;&nbsp;";

	private void renderText(int from, int to, StringBuilder renderedText) {
		for (int c = from; c < to; c++) {
			char ch = theDocumentContent.charAt(c);
			switch (ch) {
			case '\n':
				renderedText.append("<br>");
				break;
			case '\t':
				renderedText.append(TAB_HTML);
				break;
			case '<':
				renderedText.append("&lt;");
				break;
			case '"':
				renderedText.append("&quot;");
				break;
			case '\'':
				renderedText.append("&apos;");
				break;
			case '&':
				renderedText.append("&amp;");
				break;
			default:
				renderedText.append(ch);
				break;
			}
		}
	}

	static class DocumentComponent {
		final FilePosition start;
		FilePosition end;
		final boolean link;
		final Color fontColor;
		List<DocumentComponent> children;

		public DocumentComponent(FilePosition start, boolean link, Color fontColor) {
			this.start = start;
			this.link = link;
			this.fontColor = fontColor;
		}

		public ObservableValue<String> getTooltip() {
			return null;
		}

		DocumentComponent addChild(FilePosition start, boolean link, Color fontColor) {
			if (children == null)
				children = new ArrayList<>();
			DocumentComponent child = new DocumentComponent(start, link, fontColor);
			children.add(child);
			return child;
		}

		public void followLink() {
		}
	}
}
