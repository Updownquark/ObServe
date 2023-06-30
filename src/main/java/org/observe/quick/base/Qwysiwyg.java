package org.observe.quick.base;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.EvaluatedExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.ExtValueRef;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.DynamicModelValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressionValueType;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.quick.QuickApp;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Colors;
import org.qommons.SelfDescribed;
import org.qommons.Stamped;
import org.qommons.Transaction;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.DequeList;
import org.qommons.collect.ElementId;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigValueDef;
import org.qommons.config.QonfigValueType;
import org.qommons.io.CircularCharBuffer;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FilePosition;
import org.qommons.io.FileUtils;
import org.qommons.io.LocatedFilePosition;
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

import com.google.common.reflect.TypeToken;

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

	public final SettableValue<DocumentComponent> documentRoot;
	public final ObservableValue<String> tooltip;
	public final SettableValue<DocumentComponent> hovered;
	public final SettableValue<DocumentComponent> selectedNode;
	public final SettableValue<Integer> selectedStartIndex;
	public final SettableValue<Integer> selectedEndIndex;
	public final SettableValue<String> lineNumbers;

	private final SettableValue<DocumentComponent> theInternalDocumentRoot;
	private final SettableValue<ObservableValue<String>> theInternalTooltip;
	private final SimpleObservable<Void> theDocumentReplacement;
	private final SimpleObservable<Void> theApplicationReplacement;
	private String theAppLocation;
	private String theDocumentURL;
	private final StringBuilder theDocumentContent;
	private QuickDocument.Def theDocumentDef;
	private QuickDocument.Interpreted theDocumentInterpreted;
	private QuickDocument theDocument;
	private ModelSetInstance theModels;
	private QuickApplication theApplication;

	private DocumentComponent theRoot;

	public Qwysiwyg() {
		theInternalDocumentRoot = SettableValue.build(Qwysiwyg.DocumentComponent.class).build();
		theInternalTooltip = SettableValue
			.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<String>> parameterized(String.class)).build();
		documentRoot = theInternalDocumentRoot; // .unsettable(); This is settable so the tooltip can display
		tooltip = ObservableValue.flatten(theInternalTooltip);
		theDocumentReplacement = new SimpleObservable<>();
		theApplicationReplacement = new SimpleObservable<>();
		hovered = SettableValue.build(DocumentComponent.class).build();
		selectedNode = SettableValue.build(DocumentComponent.class).build();
		selectedStartIndex = SettableValue.build(int.class).withValue(0).build();
		selectedEndIndex = SettableValue.build(int.class).withValue(0).build();
		theDocumentContent = new StringBuilder();
		lineNumbers = SettableValue.build(String.class).build();
	}

	public void init(String documentLocation, List<String> unmatched) {
		boolean sameDoc = Objects.equals(documentLocation, theAppLocation);
		theAppLocation = documentLocation;
		if (!sameDoc) {
			theInternalDocumentRoot.set(null, null);
			clearDef();
		}
		if (theAppLocation == null) {
			System.err.println("WARNING: No target document");
			return;
		}

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

			URL docURL = quickApp.resolveAppFile();
			theDocumentURL = docURL.toString();
			renderXml(docURL);

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

			theDocumentInterpreted.persistModelInstances(true);
			theModels = null;
			try {
				if (theDocument == null)
					theDocument = theDocumentInterpreted.create();
				theModels = theDocument.update(theDocumentInterpreted, extModels, theDocumentReplacement);
			} catch (TextParseException e) {
				logToConsole(e, quickApp.getAppFile(), e.getPosition());
				return;
			} catch (RuntimeException e) {
				logToConsole(e, null);
				return;
			} finally {
				// Render interpretation and instances (if available)
				renderInterpreted(theRoot, theDocumentInterpreted, theDocument);
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

	private DocumentComponent theHoveredComponent;
	private boolean isControlPressed;

	public void controlPressed(boolean ctrl) {
		if (ctrl == isControlPressed)
			return;
		isControlPressed = ctrl;
		if (theHoveredComponent != null)
			theHoveredComponent.update();
	}

	public void hover(DocumentComponent hoveredComp, boolean ctrl) {
		if (hoveredComp == null) {
			hovered.set(hoveredComp, null);
			return;
		}
		boolean ctrlChanged = isControlPressed != ctrl;
		isControlPressed = ctrl;
		try {
			ObservableValue<String> tt = null;
			while (hoveredComp != null) {
				tt = hoveredComp.getTooltip();
				if (tt != null)
					break;
				hoveredComp = hoveredComp.parent;
			}
			if (hoveredComp == theHoveredComponent) {
				if (ctrlChanged && theHoveredComponent != null)
					theHoveredComponent.update();
				return;
			}
			theInternalTooltip.set(tt, null);
			theHoveredComponent = hoveredComp;
			if (hoveredComp != null && isControlPressed)
				hoveredComp.update();
			hovered.set(hoveredComp, null);
		} catch (RuntimeException | Error e) {
			e.printStackTrace();
		}
	}

	public void clicked(DocumentComponent clicked, int clickCount, boolean ctrl) {
		if (clicked == null)
			return;
		try {
			String print = clicked.start.toString();
			ObservableValue<String> tt = clicked.getTooltip();
			if (tt != null)
				print += tt.get();
			System.out.println(print);
			if (ctrl)
				clicked.followLink();
		} catch (RuntimeException | Error e) {
			e.printStackTrace();
		}
	}

	public void mouseExit() {
		DocumentComponent oldHovered = theHoveredComponent;
		theHoveredComponent = null;
		if (oldHovered != null && isControlPressed)
			oldHovered.update();
	}

	DocumentComponent getSourceComponent(DocumentComponent parent, int sourcePosition) {
		if (parent.start.getPosition() == sourcePosition)
			return parent;
		if (!parent.children.isEmpty()) {
			int childIdx = ArrayUtils.binarySearch(parent.children, child -> Integer.compare(sourcePosition, child.start.getPosition()));
			if (childIdx < 0) {
				childIdx = -childIdx - 2;
				if (childIdx < 0 || sourcePosition >= parent.children.get(childIdx).getEnd().getPosition())
					return parent;
				return getSourceComponent(parent.children.get(childIdx), sourcePosition);
			} else
				return parent.children.get(childIdx);
		}
		return parent;
	}

	private void clear() {
		clearDef();
		theDocumentContent.setLength(0);
		clearRender();
		theInternalDocumentRoot.set(null, null);
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
		theRoot = createRoot();
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
					DocumentComponent elementComp = stack.getLast().addChild(declaration.getContent().getPosition(0)).color(ELEMENT_COLOR);
					stack.add(elementComp);
					DocumentComponent elStartComp = stack.getLast().addChild(declaration.getContent().getPosition(2));
					stack.add(elStartComp);

					for (String attr : declaration.getAttributes())
						handleAttribute(declaration.getAttribute(attr));
					stack.removeLast().end(declaration.getContent().getPosition(declaration.getContent().length() - 2));
					stack.removeLast().end(declaration.getContent().getPosition(declaration.getContent().length()));
				}

				@Override
				public void handleProcessingInstruction(XmlProcessingInstruction pi) {
					DocumentComponent piComp = stack.getLast().addChild(pi.getContent().getPosition(0)).color(ELEMENT_COLOR);
					PositionedContent value = pi.getValueContent();
					if (value != null) {
						DocumentComponent valueComp = piComp.addChild(value.getPosition(0)).color(ELEMENT_VALUE_COLOR);
						valueComp.end(value.getPosition(value.length()));
					}
					piComp.end(pi.getContent().getPosition(pi.getContent().length()));
				}

				@Override
				public void handleComment(XmlComment comment) {
					DocumentComponent commentComp = stack.getLast().addChild(comment.getContent().getPosition(0)).color(COMMENT_COLOR);
					commentComp.end(comment.getContent().getPosition(comment.getContent().length()));
				}

				@Override
				public void handleElementStart(XmlElementTerminal element) {
					DocumentComponent elementComp = stack.getLast().addChild(element.getContent().getPosition(0)).color(ELEMENT_COLOR);
					stack.add(elementComp);
					DocumentComponent elStartComp = stack.getLast().addChild(element.getContent().getPosition(element.getNameOffset()));
					stack.add(elStartComp);
				}

				@Override
				public void handleElementOpen(String elementName, PositionedContent openEnd) {
					stack.removeLast().end(openEnd.getPosition(openEnd.length()));
					DocumentComponent contentComponent = stack.getLast().addChild(openEnd.getPosition(openEnd.length()))//
						.color(ELEMENT_VALUE_COLOR);
					stack.add(contentComponent);
				}

				@Override
				public void handleAttribute(XmlAttribute attribute) {
					DocumentComponent attrComp = stack.getLast().addChild(attribute.getContent().getPosition(0))
						.color(ATTRIBUTE_NAME_COLOR);
					PositionedContent value = attribute.getValueContent();
					DocumentComponent valueComp = attrComp.addChild(value.getPosition(0)).color(ATTRIBUTE_VALUE_COLOR);
					valueComp.end(value.getPosition(value.length()));
					attrComp.end(attribute.getContent().getPosition(attribute.getContent().length()));
				}

				@Override
				public void handleElementContent(String elementName, PositionedContent elementValue) {
				}

				@Override
				public void handleCDataContent(String elementName, XmlCdata cdata) {
					DocumentComponent cdataComp = stack.getLast().addChild(cdata.getContent().getPosition(0)).color(ELEMENT_COLOR);
					PositionedContent value = cdata.getValueContent();
					DocumentComponent valueComp = cdataComp.addChild(value.getPosition(0)).color(ELEMENT_VALUE_COLOR);
					valueComp.end(value.getPosition(value.length()));
					cdataComp.end(cdata.getContent().getPosition(cdata.getContent().length()));
				}

				@Override
				public void handleElementEnd(XmlElementTerminal element, boolean selfClosing) {
					// Remove the component either for the element start (if self-closing) or the element's content
					stack.removeLast().end(element.getContent().getPosition(0));
					if (!selfClosing) {
						DocumentComponent elComp = stack.getLast().addChild(element.getContent().getPosition(0))
							.color(ELEMENT_COLOR);
						elComp.end(element.getContent().getPosition(element.getContent().length()));
					}
					// Remove the component for the element
					stack.removeLast().end(element.getContent().getPosition(element.getContent().length()));
				}
			});
		} catch (IOException | XmlParseException e) {
			// Should have been reported elsewhere, nothing to do here
		}
	}

	private void renderDef(DocumentComponent component, ExElement.Def<?> def, String roleDescrip) {
		DocumentComponent elComponent = getSourceComponent(component, def.getElement().getPositionInFile().getPosition()).parent;
		String typeDescrip = def.getElement().getType().getDescription();
		if (roleDescrip != null) {
			if (typeDescrip != null)
				elComponent.typeTooltip(typeDescrip + "<br><br>" + roleDescrip);
			else
				elComponent.typeTooltip(roleDescrip);
		} else if (typeDescrip != null)
			elComponent.typeTooltip(typeDescrip);
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = getSourceComponent(elComponent, position.getPosition(0).getPosition());
			DocumentComponent attrComp = attrValueComp.parent;
			QonfigValueType type = attr.getKey().getType();
			String attrValueDescrip = null;
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object defValue = def.getAttribute(attr.getKey());
					if (defValue instanceof LocatedExpression)
						renderCompiledExpression(((LocatedExpression) defValue).getExpression(),
							((LocatedExpression) defValue).getFilePosition(), attrValueComp);
				}
			} else {
				Object value = def.getAttribute(attr.getKey());
				if (!(value instanceof SelfDescribed))
					value = def.getElement().getAttribute(attr.getKey(), Object.class);
				if (value instanceof SelfDescribed)
					attrValueDescrip = ((SelfDescribed) value).getDescription();
			}
			String attrDescrip = attr.getKey().getDescription();
			if (attrDescrip != null) {
				if (attrValueDescrip != null)
					attrComp
					.typeTooltip(attrValueDescrip + "<br><br>" + attrDescrip + "<br><br>" + renderValueType(type, 1, attr.getKey()));
				else
					attrComp.typeTooltip(attrDescrip + "<br><br>" + renderValueType(type, 1, attr.getKey()));
			} else if (attrValueDescrip != null)
				attrComp.typeTooltip(attrValueDescrip + "<br><br>" + renderValueType(type, 1, attr.getKey()));
			else
				attrComp.typeTooltip(renderValueType(type, 1, attr.getKey()));
		}
		if (def.getElement().getValue() != null && def.getElement().getValue().position != null) {
			PositionedContent position = def.getElement().getValue().position;
			DocumentComponent attrValueComp = getSourceComponent(elComponent, position.getPosition(0).getPosition());
			QonfigValueType type = def.getElement().getType().getValue().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object defValue = def.getElementValue();
					if (defValue instanceof LocatedExpression)
						renderCompiledExpression(((LocatedExpression) defValue).getExpression(),
							((LocatedExpression) defValue).getFilePosition(), attrValueComp);
				}
			}
			String attrDescrip = def.getElement().getType().getValue().getDescription();
			if (attrDescrip != null)
				attrValueComp.typeTooltip(attrDescrip + "<br><br>" + renderValueType(type, 1, def.getElement().getType().getValue()));
			else
				attrValueComp.typeTooltip(renderValueType(type, 1, def.getElement().getType().getValue()));
		}
		Map<QonfigElement, ExElement.Def<?>> children = def.getAllChildren().stream()
			.collect(Collectors.toMap(ExElement.Def::getElement, d -> d));
		for (QonfigElement child : def.getElement().getChildren()) {
			String childDescrip = null;
			for (QonfigChildDef role : child.getParentRoles()) {
				String childRoleDescrip = role.getDescription();
				if (childRoleDescrip != null) {
					if (childDescrip == null)
						childDescrip = childRoleDescrip;
					else
						childDescrip += "<br><br>" + childRoleDescrip;
				}
			}
			ExElement.Def<?> childDef = children.get(child);
			if (childDef != null)
				renderDef(elComponent, childDef, childDescrip);
			else if (childDescrip != null)
				getSourceComponent(elComponent, child.getPositionInFile().getPosition()).parent.typeTooltip(childDescrip);
		}
	}

	private void renderCompiledExpression(ObservableExpression ex, LocatedPositionedContent content, DocumentComponent component) {
		component.color(getExpressionColor(ex));
		int c = 0;
		for (ObservableExpression child : ex.getComponents()) {
			int childOffset = ex.getComponentOffset(c);
			int length = child.getExpressionLength();
			LocatedFilePosition startPos = content.getPosition(childOffset);
			FilePosition end = content.getPosition(childOffset + length);
			renderCompiledExpression(child, content.subSequence(childOffset, childOffset + length), //
				component.addChild(startPos).end(end));
			c++;
		}
		// Parse out the divisions for later rendering
		for (int d = 0; d < ex.getDivisionCount(); d++) {
			int divOffset = ex.getDivisionOffset(d);
			int length = ex.getDivisionLength(d);
			LocatedFilePosition startPos = content.getPosition(divOffset);
			FilePosition end = content.getPosition(divOffset + length);
			component.addChild(startPos).end(end);
		}
	}

	protected Color getExpressionColor(ObservableExpression exType) {
		Color color = new Color(exType.getClass().getName().hashCode());
		float dark = Colors.getDarkness(color);
		if (dark < 0.15)
			color = Colors.stain(color, 0.25f);
		return color;
	}

	static String renderValueType(QonfigValueType type, int indent, QonfigValueDef value) {
		String tt;
		if (type instanceof QonfigValueType.Custom) {
			CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
			if (customType instanceof ExpressionValueType)
				tt = "expression";
			else
				tt = customType.getName() + " (" + customType.getClass().getName() + ")";
		} else if (type instanceof QonfigAddOn) {
			tt = "Add-on " + type.toString();
		} else if (type instanceof QonfigValueType.Literal)
			tt = "'" + ((QonfigValueType.Literal) type).getValue() + "'";
		else if (type instanceof QonfigValueType.OneOf) {
			StringBuilder str = new StringBuilder("<html>").append(type.getName()).append(": one of<br>");
			for (QonfigValueType sub : ((QonfigValueType.OneOf) type).getComponents()) {
				for (int i = 0; i < indent; i++)
					str.append("  ");
				str.append("\u2022 ").append(renderValueType(sub, indent + 1, null)).append("<br>");
			}
			tt = str.toString();
		} else
			tt = type.getName();
		if (value != null) {
			tt += " (" + value.getSpecification();
			if (value.getDefaultValue() != null)
				tt += ", default " + value.getDefaultValue();
			tt += ")";
		}
		return tt;
	}

	private <E extends ExElement> void renderInterpreted(DocumentComponent component, ExElement.Interpreted<E> interpreted, E element) {
		ExElement.Def<? super E> def = interpreted.getDefinition();
		DocumentComponent elComponent = getSourceComponent(component, def.getElement().getPositionInFile().getPosition()).parent;
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = getSourceComponent(elComponent, position.getPosition(0).getPosition());
			QonfigValueType type = attr.getKey().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object interpValue = def.getAttribute(interpreted, attr.getKey());
					if (interpValue instanceof InterpretedValueSynth) {
						EvaluatedExpression<?, ?> expression = getEvaluatedExpression((InterpretedValueSynth<?, ?>) interpValue);
						if (expression != null)
							renderInterpretedExpression(expression, attrValueComp, element == null ? null : element.getUpdatingModels());
					}
				}
			} else {
				Object interpValue = def.getAttribute(interpreted, attr.getKey());
				if (interpValue instanceof SelfDescribed)
					attrValueComp.interpretedTooltip(((SelfDescribed) interpValue)::getDescription);
			}
			// TODO describe interpreted value?
		}
		if (def.getElement().getValue() != null && def.getElement().getValue().position != null) {
			PositionedContent position = def.getElement().getValue().position;
			DocumentComponent attrValueComp = getSourceComponent(elComponent, position.getPosition(0).getPosition());
			QonfigValueType type = def.getElement().getType().getValue().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object interpValue = def.getElementValue(interpreted);
					if (interpValue instanceof InterpretedValueSynth) {
						EvaluatedExpression<?, ?> expression = getEvaluatedExpression((InterpretedValueSynth<?, ?>) interpValue);
						if (expression != null)
							renderInterpretedExpression(expression, attrValueComp, element == null ? null : element.getUpdatingModels());
					}
				}
			} else {
				Object interpValue = def.getElementValue(interpreted);
				if (interpValue instanceof SelfDescribed)
					attrValueComp.interpretedTooltip(((SelfDescribed) interpValue)::getDescription);
			}
			// TODO describe interpreted value?
		}
		List<? extends ExElement.Interpreted<?>> interpretedChildren = def.getAllChildren(interpreted);
		List<? extends ExElement> tempChildren = element == null ? Collections.emptyList() : def.getAllChildren(element);
		Map<Integer, ? extends ExElement> instanceChildren = tempChildren.stream()
			.collect(Collectors.toMap(ch -> ch.reporting().getFileLocation().getPosition(0).getPosition(), ch -> ch));
		for (ExElement.Interpreted<?> child : interpretedChildren) {
			ExElement instance = instanceChildren.get(child.getDefinition().reporting().getFileLocation().getPosition(0).getPosition());
			this.renderInterpreted(elComponent, (ExElement.Interpreted<ExElement>) child, instance);
		}
	}

	private static EvaluatedExpression<?, ?> getEvaluatedExpression(InterpretedValueSynth<?, ?> value) {
		while (!(value instanceof EvaluatedExpression) && value.getComponents().size() == 1)
			value = value.getComponents().get(0);
		return value instanceof EvaluatedExpression ? (EvaluatedExpression<?, ?>) value : null;
	}

	private void renderInterpretedExpression(EvaluatedExpression<?, ?> expression, DocumentComponent component, ModelSetInstance models) {
		component.descriptor = expression.getDescriptor();
		component.target = getLinkTarget(component.descriptor);
		component.interpretedTooltip(() -> renderInterpretedDescriptor(component.descriptor));
		if (models != null && isFundamentalValue(expression)) {
			Object value;
			Observable<?> update;
			try {
				value = expression.get(models);
				if (value instanceof SettableValue)
					component.instanceTooltip(((SettableValue<?>) value).map(String::valueOf));
				else {
					try {
						update = expression.as(ModelTypes.Event.any()).get(models);
					} catch (TypeConversionException e) {
						update = null;
					}
					if (value instanceof Stamped)
						component.instanceTooltip(
							ObservableValue.of(String.class, () -> String.valueOf(value), ((Stamped) value)::getStamp, update));
					else {
						long[] stamp = new long[1];
						if (update != null) {
							update.takeUntil(models.getUntil()).act(__ -> stamp[0]++);
							component
							.instanceTooltip(ObservableValue.of(String.class, () -> String.valueOf(value), () -> stamp[0], update));
						} else {
							component.instanceTooltip(
								ObservableValue.of(String.class, () -> String.valueOf(value), () -> stamp[0]++, Observable.empty()));
						}
					}
				}
			} catch (ModelInstantiationException e) {
				logToConsole(e, new LocatedPositionedContent.SimpleLine(new LocatedFilePosition(theAppLocation, component.start), ""));
			}
		}
		List<? extends EvaluatedExpression<?, ?>> components = expression.getComponents();
		List<? extends EvaluatedExpression<?, ?>> divisions = expression.getDivisions();
		if (component.children.size() == components.size() + divisions.size()) {
			int c = 0;
			for (EvaluatedExpression<?, ?> child : components)
				renderInterpretedExpression(child, component.children.get(c++), models);
			for (EvaluatedExpression<?, ?> div : divisions)
				renderInterpretedExpression(div, component.children.get(c++), models);
		}
	}

	private boolean isFundamentalValue(EvaluatedExpression<?, ?> expression) {
		if (!expression.getComponents().isEmpty())
			return false;
		Object descriptor = expression.getDescriptor();
		if (descriptor instanceof ModelComponentNode) {
			ModelType<?> modelType = ((ModelComponentNode<?, ?>) descriptor).getModelType();
			if (modelType == ModelTypes.Model || modelType == ModelTypes.Event || modelType == ModelTypes.Action)
				return false;
			return true;
		} else if (descriptor instanceof Field)
			return true;
		else
			return false;
	}

	private LocatedFilePosition getLinkTarget(Object descriptor) {
		if (descriptor instanceof ModelComponentNode)
			return ((ModelComponentNode<?, ?>) descriptor).getSourceLocation();
		else if (descriptor instanceof DynamicModelValue) {
			DynamicModelValue<?, ?> dmv = (DynamicModelValue<?, ?>) descriptor;
			return dmv.getDeclaration().getDeclaration().getPositionInFile();
		} else
			return null;
	}

	protected String renderInterpretedDescriptor(Object descriptor) {
		if (descriptor == null)
			return null;
		if (descriptor instanceof Class) {
			return "Class " + ((Class<?>) descriptor).getName();
		} else if (descriptor instanceof TypeToken) {
			return "Type " + renderType(descriptor);
		} else if (descriptor instanceof Type) {
			return "Type " + renderType(descriptor);
		} else if (descriptor instanceof Field) {
			Field f = (Field) descriptor;
			StringBuilder str = new StringBuilder();
			if (Modifier.isStatic(f.getModifiers()))
				str.append("Static field ");
			else
				str.append("Field ");
			str.append(f.getDeclaringClass().getName()).append('.').append(f.getName());
			str.append(": ").append(renderType(f.getGenericType()));
			return str.toString();
		} else if (descriptor instanceof Method) {
			Method m = (Method) descriptor;
			StringBuilder str = new StringBuilder();
			if (Modifier.isStatic(m.getModifiers()))
				str.append("Static method ");
			else
				str.append("Method ");
			str.append(m.getDeclaringClass().getName()).append('.').append(m.getName()).append('(');
			boolean first = true;
			for (Type pt : m.getGenericParameterTypes()) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(renderType(pt));
			}
			str.append("): ").append(renderType(m.getGenericReturnType()));
			return str.toString();
		} else if (descriptor instanceof Constructor) {
			Constructor<?> c = (Constructor<?>) descriptor;
			StringBuilder str = new StringBuilder("Constructor ");
			str.append(c.getDeclaringClass().getName()).append('(');
			boolean first = true;
			for (Type pt : c.getGenericParameterTypes()) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(renderType(pt));
			}
			str.append(')');
			return str.toString();
		} else if (descriptor instanceof ModelComponentNode) {
			ModelComponentNode<?, ?> node = (ModelComponentNode<?, ?>) descriptor;
			Object id = node.getValueIdentity() != null ? node.getValueIdentity() : node.getIdentity();
			if (id instanceof DynamicModelValue.Identity) {
				DynamicModelValue.Identity dmv = (DynamicModelValue.Identity) id;
				if (dmv.getDeclaration().getDescription() != null)
					return dmv.getDeclaration().getDescription();
				return "Element value " + dmv.toString();
			}
			StringBuilder str = new StringBuilder();
			Object thing = node.getThing();
			if (thing instanceof ObservableModelSet)
				str.append("Model ").append(id);
			else if (thing instanceof CompiledModelValue) {
				if (id instanceof DynamicModelValue.Identity)
					str.append("Element model value ");
				else
					str.append("Model value ");
				try {
					str.append(id.toString()).append(": ").append(renderType(node.getType()));
				} catch (ExpressoInterpretationException e) {
					e.printStackTrace();
					return "Error retrieving type: " + e;
				}
			} else if (thing instanceof ExtValueRef)
				str.append("External value ").append(id).append(": ").append(renderType(((ExtValueRef<?, ?>) thing).getType()));
			else if (thing instanceof RuntimeValuePlaceholder) {
				try {
					str.append("Runtime value ").append(id).append(": ")
					.append(renderType(((RuntimeValuePlaceholder<?, ?>) thing).getType()));
				} catch (ExpressoInterpretationException e) {
					e.printStackTrace();
					return "Error retrieving type: " + e;
				}
			} else
				str.append("Model component ").append(id);
			if (node.getSourceLocation() != null) {
				str.append("<br>");
				if (node.getSourceLocation().getFileLocation() != null
					&& !node.getSourceLocation().getFileLocation().equals(theDocumentURL))
					str.append(node.getSourceLocation().getFileLocation()).append(' ');
				str.append('L').append(node.getSourceLocation().getLineNumber() + 1).append(" C")
				.append(node.getSourceLocation().getCharNumber() + 1);
			}
			return str.toString();
		} else if (descriptor instanceof DynamicModelValue) {
			DynamicModelValue<?, ?> dmv = (DynamicModelValue<?, ?>) descriptor;
			if (dmv.getDeclaration().getDeclaration().getDescription() != null)
				return dmv.getDeclaration().getDeclaration().getDescription();
			return "Element value " + dmv.getDeclaration().toString();
		} else if (descriptor instanceof SelfDescribed)
			return ((SelfDescribed) descriptor).getDescription();
		else
			return TypeTokens.getSimpleName(TypeTokens.get().unwrap(descriptor.getClass())) + " literal '" + descriptor + "'";
	}

	static String renderType(Object type) {
		if (type instanceof Type)
			type = TypeTokens.get().of((Type) type);
		return type.toString().replace("<", "&lt;");
	}

	private void renderText() {
		StringBuilder str = new StringBuilder();
		int line = 0;
		for (int i = 0; i < theDocumentContent.length(); i++) {
			if (theDocumentContent.charAt(i) == '\n') {
				line++;
				str.append("Line ").append(line).append(":\n");
			}
		}
		str.append("Line ").append(line + 1).append(':');

		lineNumbers.set(str.toString(), null);
		theInternalDocumentRoot.set(theRoot, null);

		// System.out.println("Hierarchy:");
		// System.out.println(theRoot.printHierarchy(new StringBuilder(), 0));
	}

	DocumentComponent createRoot() {
		return new DocumentComponent(null, FilePosition.START);
	}

	boolean isEqual(String fileLocation, String docURL) {
		return docURL.endsWith(fileLocation);
	}

	public class DocumentComponent {
		final DocumentComponent parent;
		ElementId parentChild;
		final FilePosition start;
		private FilePosition end;
		boolean bold;
		Color fontColor;
		public final ObservableCollection<DocumentComponent> children;
		LocatedFilePosition target;
		Object descriptor;

		String typeTooltip;
		Supplier<String> interpretedTooltip;
		ObservableValue<String> instanceTooltip;

		DocumentComponent(DocumentComponent parent, FilePosition start) {
			this.parent = parent;
			this.start = start;
			children = ObservableCollection.build(DocumentComponent.class).build();
			children.onChange(evt -> {
				if (evt.getType() == CollectionChangeType.add)
					evt.getNewValue().parentChild = evt.getElementId();
			});
			end = null;
		}

		public FilePosition getEnd() {
			return end;
		}

		public boolean isBold() {
			return bold;
		}

		public boolean isActiveLink() {
			return target != null && theHoveredComponent == this && isControlPressed && isEqual(target.getFileLocation(), theDocumentURL);
		}

		public Color getFontColor() {
			return fontColor;
		}

		public String getPostText() {
			int domainEnd;
			if (parent == null)
				domainEnd = theDocumentContent.length();
			else {
				DocumentComponent nextSib = CollectionElement.get(parent.children.getAdjacentElement(parentChild, true));
				if (nextSib != null)
					domainEnd = nextSib.start.getPosition();
				else if (parent != null && parent.end != null)
					domainEnd = parent.end.getPosition();
				else
					domainEnd = theDocumentContent.length();
			}

			int endPos;
			if (end != null)
				endPos = end.getPosition();
			else
				return null;
			if (endPos == domainEnd)
				return null;
			return theDocumentContent.substring(endPos, domainEnd);
		}

		void update() {
			if (parentChild != null)
				parent.children.mutableElement(parentChild).set(this);
		}

		DocumentComponent addChild(FilePosition childStart) {
			if (childStart instanceof LocatedFilePosition)
				childStart = new FilePosition(childStart.getPosition(), childStart.getLineNumber(), childStart.getCharNumber());
			if (start.getPosition() > childStart.getPosition())
				throw new IllegalArgumentException("Adding child before the parent");
			else if (!children.isEmpty() && childStart.getPosition() < children.getLast().end.getPosition())
				throw new IllegalArgumentException("Adding child before end of the previous one");

			DocumentComponent child = new DocumentComponent(this, childStart);
			children.add(child);
			return child;
		}

		DocumentComponent end(FilePosition e) {
			if (e instanceof LocatedFilePosition)
				e = new FilePosition(e.getPosition(), e.getLineNumber(), e.getCharNumber());
			if (e.getPosition() < start.getPosition())
				throw new IllegalArgumentException("Ending before beginning");
			if (children != null && !children.isEmpty() && e.getPosition() < children.get(children.size() - 1).end.getPosition())
				throw new IllegalArgumentException("Ending parent before last child");
			if (parent != null && parent.end != null && parent.end.getPosition() > 0 && e.getPosition() > parent.end.getPosition())
				throw new IllegalArgumentException("Ending child after parent");
			this.end = e;
			return this;
		}

		DocumentComponent color(Color color) {
			fontColor = color;
			return this;
		}

		DocumentComponent bold() {
			bold = true;
			return this;
		}

		ObservableValue<String> getTooltip() {
			String intTT = interpretedTooltip == null ? null : interpretedTooltip.get();
			if (typeTooltip == null && intTT == null && instanceTooltip == null)
				return null;
			StringBuilder str = new StringBuilder("<html>");
			if (typeTooltip != null) {
				str.append(typeTooltip);
				if (interpretedTooltip != null)
					str.append("<br><br>").append(intTT);
			} else
				str.append(intTT);
			if (instanceTooltip == null)
				return ObservableValue.of(str.toString());
			else {
				int preLen = str.length();
				return instanceTooltip.map(instTT -> {
					str.setLength(preLen);
					if (instTT != null)
						str.append("<br><br>Current Value: ").append(instTT);
					return str.toString();
				});
			}
		}

		DocumentComponent typeTooltip(String tt) {
			typeTooltip = tt;
			return this;
		}

		DocumentComponent interpretedTooltip(Supplier<String> tt) {
			interpretedTooltip = tt;
			return this;
		}

		DocumentComponent instanceTooltip(ObservableValue<String> tt) {
			instanceTooltip = tt;
			return this;
		}

		public void followLink() {
			System.out.println("Go to " + target);
			DocumentComponent targetComponent = getSourceComponent(theRoot, target.getPosition());
			try (Causable.CausableInUse cause = Causable.cause(); //
				Transaction vt = selectedNode.lock(true, cause);
				Transaction sit = selectedStartIndex.lock(true, cause);
				Transaction eit = selectedEndIndex.lock(true, cause)) {
				selectedNode.set(targetComponent, cause);
				selectedStartIndex.set(0, cause);
				selectedEndIndex.set(targetComponent.getTextLength(), cause);
			}
		}

		public StringBuilder printHierarchy(StringBuilder str, int indent) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(start).append("..").append(end).append('(');
			if (descriptor != null)
				str.append("d:").append(renderInterpretedDescriptor(descriptor)).append(' ');
			if (fontColor != null)
				str.append("fc:").append(Colors.toString(fontColor)).append(' ');
			str.append("): ");

			str.append(toString().replace("\t", "\\t").replace("\n", "\\n")).append('\n');

			for (DocumentComponent child : children)
				child.printHierarchy(str, indent + 1);

			String post = getPostText();
			if (post != null) {
				for (int i = 0; i < indent; i++)
					str.append('\t');
				str.append(post.replace("\t", "\\t").replace("\n", "\\n")).append('\n');
			}

			return str;
		}

		int getTextLength() {
			int textEnd;
			if (!children.isEmpty())
				textEnd = children.getFirst().start.getPosition();
			else if (end != null)
				textEnd = end.getPosition();
			else
				textEnd = theDocumentContent.length();
			return textEnd - start.getPosition();
		}

		@Override
		public String toString() {
			int textEnd;
			if (!children.isEmpty())
				textEnd = children.getFirst().start.getPosition();
			else if (end != null)
				textEnd = end.getPosition();
			else
				textEnd = theDocumentContent.length();
			return theDocumentContent.substring(start.getPosition(), textEnd);
		}
	}
}
