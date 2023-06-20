package org.observe.quick.base;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressionValueType;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.LocatedExpression;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.EvaluatedExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.ExtValueRef;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickApp;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Colors;
import org.qommons.SelfDescribed;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.DequeList;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
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
		if (theDocumentLocation == null) {
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

			renderInterpreted(theRoot, theDocumentInterpreted);

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

	private int theHovered;

	public void hover(int position) {
		if (theRoot == null || position == theHovered)
			return;
		theHovered = position;
		try {
			DocumentComponent hovered = getRenderComponent(theRoot, position);
			String tt = null;
			while (hovered != null) {
				tt = hovered.getTooltip(position - hovered.renderedStart);
				if (tt != null)
					break;
				hovered = hovered.parent;
			}
			theInternalTooltip.set(tt, null);
		} catch (RuntimeException | Error e) {
			e.printStackTrace();
		}
	}

	public void clicked(int position, int clickCount) {
		if (theRoot == null)
			return;
		try {
			DocumentComponent clicked = getRenderComponent(theRoot, position);
			String print = "" + clicked.start;
			String tt = clicked.getTooltip(position - clicked.renderedStart);
			if (tt != null)
				print += tt;
			System.out.println(print);
		} catch (RuntimeException | Error e) {
			e.printStackTrace();
		}
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
		if (parent.start.getPosition() == sourcePosition)
			return parent;
		if (parent.children != null) {
			int childIdx = ArrayUtils.binarySearch(parent.children, child -> Integer.compare(sourcePosition, child.start.getPosition()));
			if (childIdx < 0) {
				childIdx = -childIdx - 2;
				if (childIdx < 0 || sourcePosition >= parent.children.get(childIdx).getEnd())
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

	private void renderDef(DocumentComponent component, ExElement.Def<?> def, String descrip) {
		DocumentComponent elComponent = getSourceComponent(component, def.getElement().getPositionInFile().getPosition()).parent;
		if (descrip != null)
			elComponent.typeTooltip(descrip, true);
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = getSourceComponent(elComponent, position.getPosition(0).getPosition());
			DocumentComponent attrComp = attrValueComp.parent;
			QonfigValueType type = attr.getKey().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object defValue = def.getAttribute(attr.getKey());
					if (defValue instanceof LocatedExpression)
						renderCompiledExpression(((LocatedExpression) defValue).getExpression(),
							((LocatedExpression) defValue).getFilePosition(), attrValueComp);
				}
			}
			String attrDescrip = def.getAttributeDescription(attr.getKey());
			if (attrDescrip != null)
				attrComp.typeTooltip(attrDescrip + "<br><br>" + renderValueType(type, 1, attr.getKey()), true);
			else
				attrComp.typeTooltip(renderValueType(type, 1, attr.getKey()), true);
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
			String attrDescrip = def.getElementValueDescription();
			if (attrDescrip != null)
				attrValueComp.typeTooltip(attrDescrip + "<br><br>" + renderValueType(type, 1, def.getElement().getType().getValue()), true);
			else
				attrValueComp.typeTooltip(renderValueType(type, 1, def.getElement().getType().getValue()), true);
		}
		for (ExElement.Def<?> child : def.getAllChildren()) {
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
			renderDef(elComponent, child, childDescrip);
		}
	}

	private void renderCompiledExpression(ObservableExpression ex, LocatedPositionedContent content, DocumentComponent component) {
		component.color(getExpressionColor(ex));
		int c = 0;
		for (ObservableExpression child : ex.getComponents()) {
			int childOffset = ex.getComponentOffset(c);
			int length = child.getExpressionLength();
			LocatedFilePosition startPos = content.getPosition(childOffset);
			int end = content.getPosition(childOffset + length).getPosition();
			renderCompiledExpression(child, content.subSequence(childOffset, childOffset + length), //
				component.addChild(startPos).end(end));
			c++;
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

	private <E extends ExElement> void renderInterpreted(DocumentComponent component, ExElement.Interpreted<E> interpreted) {
		ExElement.Def<? super E> def = interpreted.getDefinition();
		DocumentComponent elComponent = getSourceComponent(component, def.getElement().getPositionInFile().getPosition()).parent;
		for (Map.Entry<QonfigAttributeDef.Declared, QonfigValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = getSourceComponent(elComponent, position.getPosition(0).getPosition());
			DocumentComponent attrComp = attrValueComp.parent;
			QonfigValueType type = attr.getKey().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object interpValue = def.getAttribute(interpreted, attr.getKey());
					if (interpValue instanceof InterpretedValueSynth) {
						EvaluatedExpression<?, ?> expression = getEvaluatedExpression((InterpretedValueSynth<?, ?>) interpValue);
						if (expression != null)
							renderInterpretedExpression(expression, attrValueComp);
					}
				}
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
							renderInterpretedExpression(expression, attrValueComp);
					}
				}
			}
			// TODO describe interpreted value?
		}
		for (ExElement.Interpreted<?> child : def.getAllChildren(interpreted))
			renderInterpreted(elComponent, child);
	}

	private static EvaluatedExpression<?, ?> getEvaluatedExpression(InterpretedValueSynth<?, ?> value) {
		while (!(value instanceof EvaluatedExpression) && value.getComponents().size() == 1)
			value = value.getComponents().get(0);
		return value instanceof EvaluatedExpression ? (EvaluatedExpression<?, ?>) value : null;
	}

	private void renderInterpretedExpression(EvaluatedExpression<?, ?> expression, DocumentComponent component) {
		component.interpretedTooltip(pos -> renderInterpretedDescriptor(expression.getDescriptor(pos)), true);
		List<? extends EvaluatedExpression<?, ?>> components = expression.getComponents();
		if (component.children != null && components.size() == component.children.size()) {
			int c = 0;
			for (EvaluatedExpression<?, ?> child : components) {
				renderInterpretedExpression(child, component.children.get(c));
				c++;
			}
		}
	}

	protected String renderInterpretedDescriptor(Object descriptor) {
		if (descriptor == null)
			return null;
		System.out.println("Descriptor: " + descriptor.getClass().getName());
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
			str.append("): ").append(renderType(m.getGenericReturnType())).append(')');
			return str.toString();
		} else if (descriptor instanceof ModelComponentNode) {
			ModelComponentNode<?, ?> node = (ModelComponentNode<?, ?>) descriptor;
			Object id = node.getValueIdentity() != null ? node.getValueIdentity() : node.getIdentity();
			Object thing = node.getThing();
			if (thing instanceof ObservableModelSet)
				return "Model " + id.toString();
			else if (thing instanceof CompiledModelValue) {
				String descrip;
				if (id instanceof DynamicModelValue.Identity)
					descrip = "Element model value ";
				else
					descrip = "Model value ";
				try {
					return descrip + id.toString() + ": " + renderType(node.getType());
				} catch (ExpressoInterpretationException e) {
					e.printStackTrace();
					return "Error retrieving type: " + e;
				}
			} else if (thing instanceof ExtValueRef)
				return "External value " + id.toString() + ": " + renderType(((ExtValueRef<?, ?>) thing).getType());
			else if (thing instanceof RuntimeValuePlaceholder) {
				try {
					return "Runtime value " + id.toString() + ": "
						+ renderType(((RuntimeValuePlaceholder<?, ?>) thing).getType());
				} catch (ExpressoInterpretationException e) {
					e.printStackTrace();
					return "Error retrieving type: " + e;
				}
			} else
				return "Model component " + id.toString();
		} else if (descriptor instanceof DynamicModelValue) {
			DynamicModelValue<?, ?> dmv = (DynamicModelValue<?, ?>) descriptor;
			return "Element value " + dmv.getDeclaration().toString();
		} else if (descriptor instanceof SelfDescribed)
			return ((SelfDescribed) descriptor).getDescription();
		else
			return "Literal " + descriptor;
	}

	static String renderType(Object type) {
		if (type instanceof Type)
			type = TypeTokens.get().of((Type) type);
		return type.toString().replace("<", "&lt;");
	}

	private void renderText() {
		DocumentRenderer renderer = new DocumentRenderer(theDocumentContent);
		renderer.render(theRoot);
		theInternalDocumentDisplay.set(renderer.toString(), null);
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

		DocumentRenderer(CharSequence content) {
			theContent = content;
			theRenderedText = new StringBuilder();
			int lines = 0;
			for (int c = 0; c < theContent.length(); c++) {
				if (theContent.charAt(c) == '\n')
					lines++;
			}
			theMaxLineDigits = getDigits(lines);
			theLineNumber = 1;
			renderLineBreak();
		}

		int render(DocumentComponent comp) {
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
					theModelPosition++;
					break;
				case '"':
					theRenderedText.append("&quot;");
					theModelPosition++;
					break;
				case '\'':
					theRenderedText.append("&apos;");
					theModelPosition++;
					break;
				case '&':
					theRenderedText.append("&amp;");
					theModelPosition++;
					break;
				default:
					theRenderedText.append(ch);
					theModelPosition++;
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

		@Override
		public String toString() {
			return theRenderedText.toString();
		}
	}

	static class DocumentComponent {
		final DocumentComponent parent;
		final FilePosition start;
		private int end;
		boolean link;
		boolean bold;
		Color fontColor;
		List<DocumentComponent> children;
		int renderedStart;
		int renderedEnd;

		boolean isTooltipHtml;
		String typeTooltip;
		IntFunction<String> interpretedTooltip;

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

		DocumentComponent end(int e) {
			if (e < start.getPosition())
				throw new IllegalArgumentException("Ending before beginning");
			if (children != null && !children.isEmpty() && e < children.get(children.size() - 1).end)
				throw new IllegalArgumentException("Ending parent before last child");
			if (parent != null && parent.end > 0 && end > parent.end)
				throw new IllegalArgumentException("Ending child after parent");
			this.end = e;
			return this;
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

		String getTooltip(int offset) {
			String intTT = interpretedTooltip == null ? null : interpretedTooltip.apply(offset);
			if (typeTooltip == null && intTT == null)
				return null;
			StringBuilder str = new StringBuilder();
			if (isTooltipHtml)
				str.append("<html>");
			if (typeTooltip != null) {
				str.append(typeTooltip);
				if (interpretedTooltip != null)
					str.append("<br><br>").append(intTT);
			} else
				str.append(intTT);
			return str.toString();
		}

		DocumentComponent typeTooltip(String tt, boolean html) {
			typeTooltip = tt;
			if (html)
				isTooltipHtml = true;
			return this;
		}

		DocumentComponent interpretedTooltip(IntFunction<String> tt, boolean html) {
			interpretedTooltip = tt;
			if (html)
				isTooltipHtml = true;
			return this;
		}

		public void followLink() {
		}

		@Override
		public String toString() {
			return start.toString();
		}
	}
}
