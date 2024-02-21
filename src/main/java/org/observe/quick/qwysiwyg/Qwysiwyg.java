package org.observe.quick.qwysiwyg;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMap;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.*;
import org.observe.expresso.ObservableExpression.EvaluatedExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.ExtValueRef;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementModelValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Interpreted;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressionValueType;
import org.observe.expresso.qonfig.ExpressoDocument;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.expresso.qonfig.ModelValueElement;
import org.observe.quick.QuickApp;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickWindow;
import org.observe.quick.qwysiwyg.StyledQuickDocument.DocumentComponent;
import org.observe.quick.style.InterpretedStyleValue;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyle.ConditionalValue;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleValue;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickStyledElement.QuickInstanceStyle;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.BreakpointHere;
import org.qommons.Causable;
import org.qommons.Colors;
import org.qommons.LambdaUtils;
import org.qommons.SelfDescribed;
import org.qommons.Stamped;
import org.qommons.Transaction;
import org.qommons.Version;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.DequeList;
import org.qommons.collect.ElementId;
import org.qommons.config.CustomValueType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.AttributeValue;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigType;
import org.qommons.config.QonfigValueDef;
import org.qommons.config.QonfigValueType;
import org.qommons.ex.ExceptionHandler;
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
	public static class DebugExpression<T> {
		private final TypeToken<T> theType;
		private String theExpressionText;
		private ObservableExpression theExpression;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theInterpretedValue;
		private SettableValue<T> theValue;
		private String theErrorText;
		private final Runnable theUpdateAction;
		private final Consumer<ObservableValueEvent<T>> theValueUpdate;
		private final SimpleObservable<Void> theRelease;

		DebugExpression(TypeToken<T> type, Runnable updateAction, Consumer<ObservableValueEvent<T>> valueUpdate) {
			theType = type;
			theUpdateAction = updateAction;
			theValueUpdate = valueUpdate;
			theRelease = new SimpleObservable<>();
			theExpressionText = "";
		}

		public String getExpressionText() {
			return theExpressionText;
		}

		public void setExpressionText(String expressionText, ExElement.Interpreted<?> interpretedContext, ExElement context) {
			theRelease.onNext(null);
			theExpressionText = expressionText;
			try {
				theExpression = new JavaExpressoParser().parse(expressionText);
			} catch (ExpressoParseException e) {
				e.printStackTrace();
				theExpression = null;
				theErrorText = "<Parse Error: " + e.getMessage() + ">";
			}
			if (theExpression != null) {
				try {
					LocatedPositionedContent content = LocatedPositionedContent.of("QWYSIWYG Watch Expression",
						new PositionedContent.Simple(FilePosition.START, expressionText));
					theInterpretedValue = theExpression.evaluate(ModelTypes.Value.forType(theType), interpretedContext.getExpressoEnv()//
						.withErrorReporting(new ErrorReporting.Default(content)), 0, ExceptionHandler.thrower2());
				} catch (ExpressoInterpretationException | TypeConversionException e) {
					e.printStackTrace();
					theInterpretedValue = null;
					theErrorText = "<Interpret Error: " + e.getMessage() + ">";
				}
			}
			if (theInterpretedValue != null) {
				try {
					theValue = theInterpretedValue.instantiate().get(context.getUpdatingModels());
				} catch (ModelInstantiationException e) {
					e.printStackTrace();
					theValue = null;
					theErrorText = "<Instantitate Error: " + e.getMessage() + ">";
				}
			}
			if (theUpdateAction != null)
				theUpdateAction.run();
			if (theValue != null && theValueUpdate != null)
				theValue.changes().takeUntil(theRelease).act(theValueUpdate);
		}

		public String getErrorText() {
			return theErrorText;
		}

		public SettableValue<T> getValue() {
			return theValue;
		}

		public void remove() {
			theRelease.onNext(null);
		}
	}

	public static class WatchExpression<T> {
		private final ObservableCollection<? extends WatchExpression<?>> theCollection;
		private ElementId theId;
		private ExElement.Interpreted<?> theInterpretedContext;
		private ExElement theContext;
		private final DebugExpression<T> theExpression;
		private String theValueText;

		WatchExpression(ObservableCollection<? extends WatchExpression<?>> collection, TypeToken<T> type,
			ExElement.Interpreted<?> interpretedContext, ExElement context) {
			theCollection = collection;
			theInterpretedContext = interpretedContext;
			theContext = context;
			theExpression = new DebugExpression<>(type, this::onExpressionUpdate, this::onExpressionValue);
			theValueText = "<No value selected>";
		}

		void setId(ElementId id) {
			theId = id;
		}

		protected DebugExpression<T> getExpression() {
			return theExpression;
		}

		public ExElement.Interpreted<?> getInterpretedContext() {
			return theInterpretedContext;
		}

		public ExElement getContext() {
			return theContext;
		}

		public String getExpressionText() {
			return theExpression.getExpressionText();
		}

		public void setExpressionText(String expressionText) {
			theExpression.setExpressionText(expressionText, theInterpretedContext, theContext);
		}

		protected void onExpressionUpdate() {
			if (theExpression.getValue() != null) { // Handled in the onExpressionValue
			} else if (theExpression.getErrorText() != null) {
				theValueText = theExpression.getErrorText();
				update();
			} else {
				theValueText = "<No value selected>";
				update();
			}
		}

		protected void onExpressionValue(ObservableValueEvent<T> evt) {
			theValueText = String.valueOf(evt.getNewValue());
			update();
		}

		public String getContextString() {
			return "<" + theContext.getTypeName() + "> " + theContext.reporting().getPosition().toShortString();
		}

		public String getValue() {
			return theValueText;
		}

		public void remove() {
			theExpression.remove();
			theCollection.mutableElement(theId).remove();
		}

		protected void update() {
			((ObservableCollection<WatchExpression<T>>) theCollection).mutableElement(theId).set(this);
		}
	}

	public enum WatchActionType {
		Break, Log
	}

	public static class WatchAction extends WatchExpression<Boolean> {
		private WatchActionType theActionType;
		private final DebugExpression<?> theActionConfiguration;
		private final Causable.CausableKey ROOT_FINISH;

		WatchAction(ObservableCollection<WatchAction> collection, Interpreted<?> interpretedContext, ExElement context) {
			super(collection, TypeTokens.get().BOOLEAN, interpretedContext, context);
			theActionType = WatchActionType.Break;
			theActionConfiguration = new DebugExpression<>(TypeTokens.get().WILDCARD, null, null);
			ROOT_FINISH = Causable.key((c, vs) -> {
				if (Boolean.TRUE.equals(getExpression().getValue().get()))
					doWatchAction();
			});
		}

		public WatchActionType getActionType() {
			return theActionType;
		}

		public void setActionType(WatchActionType actionType) {
			theActionType = actionType;
			update();
		}

		public String getActionText() {
			if (isActionConfigurable() == null)
				return theActionType + " " + theActionConfiguration.getExpressionText();
			else
				return theActionType.name();
		}

		public String isActionConfigurable() {
			switch (theActionType) {
			case Log:
				return null;
			default:
				return "Action type " + theActionType + " is not configurable";
			}
		}

		public String getActionConfiguration() {
			return theActionConfiguration.getExpressionText();
		}

		public void setActionConfiguration(String text) {
			theActionConfiguration.setExpressionText(text, getInterpretedContext(), getContext());
		}

		@Override
		protected void onExpressionValue(ObservableValueEvent<Boolean> value) {
			super.onExpressionValue(value);
			if (!value.isInitial() && Boolean.TRUE.equals(value.getNewValue()))
				value.getRootCausable().onFinish(ROOT_FINISH);
		}

		protected void doWatchAction() {
			switch (theActionType) {
			case Break:
				BreakpointHere.breakpoint();
				break;
			case Log:
				if (theActionConfiguration.getValue() != null)
					getContext().reporting().info(String.valueOf(theActionConfiguration.getValue().get()));
			}
		}

		@Override
		public void remove() {
			theActionConfiguration.remove();
			super.remove();
		}
	}

	public class StyleDebugValue<T> {
		private final QuickInterpretedStyle theSource;
		private final QuickStyleValue theStyleValue;
		private final EvaluatedExpression<SettableValue<?>, SettableValue<Boolean>> theConditionX;
		private final EvaluatedExpression<SettableValue<?>, SettableValue<T>> theValueX;
		private final ObservableValue<QuickInterpretedStyle.ConditionalValue<T>> theConditionalValue;
		private boolean isPassing;
		private Object theValue;
		private final StyledQuickDocument theConditionDoc;

		StyleDebugValue(QuickInterpretedStyle source, QuickStyleValue styleValue, QuickStyleAttribute<T> attr,
			ObservableValue<ConditionalValue<T>> conditionalValue, InterpretedExpressoEnv env, ModelSetInstance models)
				throws ExpressoInterpretationException, TypeConversionException {
			theSource = source;
			theStyleValue = styleValue;
			theConditionalValue = conditionalValue;
			ObservableExpression condition;
			switch (styleValue.getApplication().getConditions().size()) {
			case 0:
				condition = null;
				theConditionX = null;
				break;
			case 1:
				condition = styleValue.getApplication().getConditions().get(0).getExpression();
				theConditionX = condition.evaluate(ModelTypes.Value.BOOLEAN, env, 0, ExceptionHandler.thrower2());
				break;
			default:
				LocatedAndExpression and = new LocatedAndExpression(//
					styleValue.getApplication().getConditions().get(0), //
					styleValue.getApplication().getConditions().get(1));
				for (int i = 2; i < styleValue.getApplication().getConditions().size(); i++)
					and = new LocatedAndExpression(and, styleValue.getApplication().getConditions().get(i));
				condition = and.getExpression();
				theConditionX = condition.evaluate(ModelTypes.Value.BOOLEAN, env, 0, ExceptionHandler.thrower2());
			}
			if (theConditionX != null) {
				LocatedPositionedContent conditionContent = new LocatedPositionedContent.Default(styleValue + ".condition",
					new PositionedContent.Simple(FilePosition.START, condition.toString()));
				theConditionDoc = new StyledQuickDocument(conditionContent.getFileLocation(),
					condition.toString(), Qwysiwyg.this::goTo);
				theConditionDoc.getRoot().end(conditionContent.getPosition(conditionContent.length()));
				renderCompiledExpression(condition, conditionContent, theConditionDoc.getRoot());
				renderInterpretedExpression(theConditionX, theConditionDoc.getRoot(), env, models);
			} else
				theConditionDoc = null;
			theValueX = styleValue.getValueExpression().interpret(ModelTypes.Value.forType(attr.getType()), env);
		}

		boolean update() {
			QuickInterpretedStyle.ConditionalValue<T> cv = theConditionalValue.get();
			boolean update;
			if (cv.pass) {
				Object newValue = cv.value.get();
				if (isPassing)
					update = !Objects.equals(theValue, newValue);
				else
					update = true;
				isPassing = true;
				theValue = newValue;
			} else if (isPassing) {
				isPassing = false;
				theValue = null;
				update = true;
			} else
				update = false;
			return update;
		}

		public String getSourceFile() {
			QonfigElement element = theSource.getDefinition().getElement();
			return element.getPositionInFile().getFileName();
		}

		public String getFullSourceFile() {
			QonfigElement element = theSource.getDefinition().getElement();
			return element.getPositionInFile().getFileLocation();
		}

		public String getSourceElement() {
			QonfigElement element = theSource.getDefinition().getElement();
			return element.getType().getName() + " " + element.getPositionInFile().printPosition();
		}

		public boolean isSourceElementLink() {
			LocatedFilePosition pos = theSource.getDefinition().getElement().getPositionInFile();
			if (pos == null)
				return false;
			String file = pos.getFileLocation();
			return theDocumentURL.endsWith(file);
		}

		public void followSourceElementLink() {
			LocatedFilePosition pos = theSource.getDefinition().getElement().getPositionInFile();
			if (pos == null)
				return;
			String file = pos.getFileLocation();
			if (!theDocumentURL.endsWith(file))
				return;
			DocumentComponent target = StyledQuickDocument.getSourceComponent(document.get().getRoot(), pos.getPosition());
			if (target != null && target.parent != null)
				goTo(target.parent);
		}

		public DocumentComponent getCondition() {
			return theConditionDoc == null ? null : theConditionDoc.getRoot();
		}

		public String getConditionTooltip(int offset) {
			// if (theConditionX == null)
			System.out.println("Offset=" + offset);
			return null;
			// EvaluatedExpression<?, ?> hoveredX = getHoveredDivision(theConditionX, offset);
			// return renderInterpretedDescriptor(hoveredX.getDescriptor());
		}

		private EvaluatedExpression<?, ?> getHoveredDivision(EvaluatedExpression<?, ?> ex, int expressionOffset) {
			for (EvaluatedExpression<?, ?> div : ex.getComponents()) {
				if (div.getExpressionOffset() > expressionOffset)
					return ex;
				if (expressionOffset < div.getExpressionOffset() + div.getExpressionLength())
					return getHoveredDivision(div, expressionOffset);
			}
			return ex;
		}

		public String getValueExpression() {
			return theStyleValue.getValueExpression().toString();
		}

		public boolean isActive() {
			return isPassing;
		}

		public String getCurrentValue() {
			if (!isPassing)
				return "(not active)";
			else if (theValue == null)
				return "null";
			else if (theValue instanceof Color)
				return Colors.toString((Color) theValue);
			else
				return theValue.toString();
		}

		@Override
		public String toString() {
			return theStyleValue.toString();
		}
	}

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

	public final SettableValue<String> title;
	public final SettableValue<StyledQuickDocument> document;
	public final SettableValue<DocumentComponent> selectedNode;
	public final SettableValue<DocumentComponent> selectedEndNode;
	public final SettableValue<Integer> selectedStartIndex;
	public final SettableValue<Integer> selectedEndIndex;
	public final SettableValue<String> lineNumbers;
	public final ObservableCollection<WatchExpression<?>> watchExpressions;
	public final ObservableCollection<WatchAction> watchActions;
	public final ObservableCollection<QuickStyleAttribute<?>> availableStyles;
	public final SettableValue<QuickStyleAttribute<?>> selectedStyle;
	public final ObservableCollection<StyleDebugValue<?>> styleDebugValues;

	private final ObservableMap<QonfigToolkit, StyledQonfigToolkit> theToolkits;
	public final ObservableCollection<StyledQonfigToolkit> toolkits;
	public final SettableValue<StyledQonfigToolkit> selectedToolkit;

	private final SimpleObservable<Void> theDocumentReplacement;
	private final SimpleObservable<Void> theApplicationReplacement;
	private String theAppLocation;
	private String theDocumentURL;
	private final StringBuilder theDocumentContent;
	private CompiledExpressoEnv theCompiledEnv;
	private QuickDocument.Def theDocumentDef;
	private QuickDocument.Interpreted theDocumentInterpreted;
	private QuickDocument theDocument;
	private ModelSetInstance theModels;
	private QuickApplication theApplication;

	private DocumentComponent theStyledNode;
	private final ObservableValue<QuickElementStyleAttribute<?>> theDebuggingStyle;

	// private DocumentComponent theRoot;

	public Qwysiwyg() {
		title = SettableValue.<String> build().withValue("QWYSIWYG").build();
		document = SettableValue.<StyledQuickDocument> build().build();
		theDocumentReplacement = new SimpleObservable<>();
		theApplicationReplacement = new SimpleObservable<>();
		selectedNode = SettableValue.<DocumentComponent> build().build();
		selectedEndNode = SettableValue.<DocumentComponent> build().build();
		selectedStartIndex = SettableValue.<Integer> build().withValue(0).build();
		selectedEndIndex = SettableValue.<Integer> build().withValue(0).build();
		theDocumentContent = new StringBuilder();
		lineNumbers = SettableValue.<String> build().build();
		availableStyles = ObservableCollection.<QuickStyleAttribute<?>> build().build();
		selectedStyle = SettableValue.<QuickStyleAttribute<?>> build().build();
		watchExpressions = ObservableCollection.<WatchExpression<?>> build().build();
		watchExpressions.onChange(evt -> {
			if (evt.getType() == CollectionChangeType.add)
				evt.getNewValue().setId(evt.getElementId());
		});
		watchActions = ObservableCollection.<WatchAction> build().build();
		watchActions.onChange(evt -> {
			if (evt.getType() == CollectionChangeType.add)
				evt.getNewValue().setId(evt.getElementId());
		});
		styleDebugValues = ObservableCollection.<StyleDebugValue<?>> build().build();
		theDebuggingStyle = selectedNode
			.transform(tx -> tx.cache(true).fireIfUnchanged(false).combineWith(selectedStyle).combine((node, style) -> {
			if (theStyledNode == null || style == null)
				return null;
			return ((QuickStyledElement.Interpreted<?>) theStyledNode.interpreted).getStyle().get(style);
		}));
		theToolkits = ObservableMap.<QonfigToolkit, StyledQonfigToolkit> build().buildMap();
		toolkits = theToolkits.values().flow().unmodifiable(false).collect();
		selectedToolkit = SettableValue.<StyledQonfigToolkit> build().build();

		selectedNode.changes().act(evt -> {
			if (evt.getOldValue() == evt.getNewValue())
				return;
			if (evt.getOldValue() != null) {
				evt.getOldValue().bold = false;
				evt.getOldValue().update();
				if (evt.getOldValue().opposite != null) {
					evt.getOldValue().opposite.bold = false;
					evt.getOldValue().opposite.update();
				}
			}
			theStyledNode = evt.getNewValue();
			while (theStyledNode != null && !(theStyledNode.element instanceof QuickStyledElement))
				theStyledNode = theStyledNode.parent;
			if (evt.getNewValue() != null) {
				evt.getNewValue().bold = true;
				evt.getNewValue().update();
				if (evt.getNewValue().opposite != null) {
					evt.getNewValue().opposite.bold = true;
					evt.getNewValue().opposite.update();
				}
				if (theStyledNode != null) {
					QuickStyledElement styled = (QuickStyledElement) theStyledNode.element;
					try (Transaction t = availableStyles.lock(true, evt)) {
						CollectionUtils.synchronize(availableStyles, new ArrayList<>(styled.getStyle().getApplicableAttributes()))//
						.simple(LambdaUtils.identity())//
						.adjust();
					}

				} else {
					availableStyles.clear();
					selectedStyle.set(null, null);
				}
			} else {
				availableStyles.clear();
				selectedStyle.set(null, null);
			}
		});

		theDebuggingStyle.changes().act(evt -> {
			styleDebugValues.clear();
			if (evt.getNewValue() != null)
				populateDebugStyle(evt.getNewValue(), ((QuickStyledElement) theStyledNode.element).getStyle());
		});
	}

	private <T> void populateDebugStyle(QuickElementStyleAttribute<T> attr, QuickInstanceStyle style) {
		List<ObservableValue<QuickInterpretedStyle.ConditionalValue<T>>> values;
		try {
			values = attr.instantiate().getConditionalValues(theStyledNode.element.getUpdatingModels());
		} catch (ModelInstantiationException e) {
			System.err.println("Could not debug style");
			e.printStackTrace();
			return;
		}
		List<BiTuple<QuickInterpretedStyle, InterpretedStyleValue<T>>> interpretedValues = attr.getAllValues();
		for (int v = 0; v < values.size(); v++) {
			try {
				styleDebugValues.add(new StyleDebugValue<>(interpretedValues.get(v).getValue1(),
					interpretedValues.get(v).getValue2().getStyleValue(), attr.getAttribute(), values.get(v),
					theStyledNode.interpreted.getExpressoEnv().withErrorReporting(new ErrorReporting.Default(null)),
					theStyledNode.element.getUpdatingModels()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		updateDebugStyle();
		Observable<?> changes = Observable
			.onRootFinish(style.getApplicableAttribute(attr.getAttribute()).noInitChanges().takeUntil(theDebuggingStyle.noInitChanges()));
		changes.act(__ -> {
			System.out.println("Debug style changed");
			updateDebugStyle();
		});
	}

	private void updateDebugStyle() {
		boolean found = false;
		for (CollectionElement<StyleDebugValue<?>> dv : styleDebugValues.elements()) {
			if (!found) {
				if (dv.get().update())
					styleDebugValues.mutableElement(dv.getElementId()).set(dv.get()); // Update
				found = dv.get().isPassing;
				if (found)
					System.out.println("Active=" + dv.get().getCondition());
			} else if (dv.get().isPassing) {
				dv.get().isPassing = false;
				dv.get().theValue = null;
				styleDebugValues.mutableElement(dv.getElementId()).set(dv.get()); // Update
			}
		}
	}

	public String getDocumentName() {
		if (theDocumentURL == null)
			return "?";
		int lastSlash = theDocumentURL.lastIndexOf('/');
		if (lastSlash < 0)
			return theDocumentURL;
		else
			return theDocumentURL.substring(lastSlash + 1);
	}

	public void init(String documentLocation, List<String> unmatched) {
		title.set("QWYSIWYG: " + documentLocation, null);
		boolean sameDoc = Objects.equals(documentLocation, theAppLocation);
		theAppLocation = documentLocation;
		if (!sameDoc) {
			document.set(null, null);
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
					Reader reader = new SimpleXMLParser().readXmlFile(appFile.toString(), in);
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

				theToolkits.clear();
				for (QonfigToolkit toolkit : quickApp.getToolkits())
					theToolkits.put(toolkit, new StyledQonfigToolkit(toolkit));

				theCompiledEnv = theDocumentDef.getExpressoEnv();
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

			renderDef(document.get().getRoot(), theDocumentDef, null);

			ObservableModelSet.ExternalModelSet extModels = QuickApp.parseExtModels(
				theDocumentDef.getAddOn(ExpressoDocument.Def.class).getHead().getExpressoEnv().getBuiltModels(),
				quickApp.getCommandLineArgs(), ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER),
				InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA);

			try {
				if (theDocumentInterpreted == null)
					theDocumentInterpreted = theDocumentDef.interpret(null);

				InterpretedExpressoEnv env = InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA//
					.withExt(extModels);
				theDocumentInterpreted.updateDocument(env);
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
				theDocument.update(theDocumentInterpreted);
				theDocument.instantiated();
				theModels = theDocument.instantiate(theDocumentReplacement);
				QuickWindow window = theDocument.getAddOn(QuickWindow.class);
				if (window != null && window.getTitle() != null) {
					window.getTitle().changes().takeUntil(theDocumentReplacement).act(evt -> {
						title.set("QWYSIWYG: " + evt.getNewValue(), evt);
					});
				} else
					title.set("QWYSIWYG: " + documentLocation, null);
			} catch (TextParseException e) {
				logToConsole(e, quickApp.getAppFile(), e.getPosition());
				return;
			} catch (RuntimeException e) {
				logToConsole(e, null);
				return;
			} finally {
				// Render interpretation and instances (if available)
				renderInterpreted(document.get().getRoot(), theDocumentInterpreted, theDocument, theModels);
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

	public void goToToolkitReference(Object reference) {
		if (reference == null)
			return;
		StyledQonfigToolkit tk;
		if (reference instanceof QonfigToolkit) {
			tk = theToolkits.get(reference);
			if (tk != null)
				selectedToolkit.set(tk, null);
		} else if (reference instanceof QonfigType) {
			tk = theToolkits.get(((QonfigType) reference).getDeclarer());
			if (tk != null) {
				selectedToolkit.set(tk, null);
				tk.goToReference((QonfigType) reference);
			} else
				System.err.println("Unrecognized toolkit: " + ((QonfigType) reference).getDeclarer());
		} else
			System.err.println("Unrecognized reference: " + reference.getClass().getSimpleName() + " " + reference);
	}

	public void controlPressed(boolean ctrl) {
		StyledQuickDocument doc = document.get();
		if (doc != null)
			doc.setCtrlPressed(ctrl);
	}

	public void hover(DocumentComponent hoveredComp, boolean ctrl) {
		StyledQuickDocument doc = document.get();
		if (doc != null)
			doc.setHoveredComponent(hoveredComp, ctrl);
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
		StyledQuickDocument doc = document.get();
		if (doc != null)
			doc.setHoveredComponent(null, false);
	}

	public String canAddWatchExpression(DocumentComponent selected) {
		if (selected == null)
			return "No context selected";
		while (selected != null && selected.element == null)
			selected = selected.parent;
		if (selected == null)
			return selected + "is not a valid context";
		return null;
	}

	public void addWatchExpression(DocumentComponent selected) {
		DocumentComponent context = selected;
		while (context != null && context.element == null)
			context = context.parent;
		if (context == null) {
			System.err.println("Cannot add watch expression at " + selected + "--no valid context");
			return;
		}
		WatchExpression<?> watch = new WatchExpression<>(watchExpressions, TypeTokens.get().WILDCARD, context.interpreted, context.element);
		Integer start = selectedStartIndex.get();
		Integer end = selectedEndIndex.get();
		if (start != null && end != null && !start.equals(end)) {
			int absStart = selectedNode.get().start.getPosition() + start.intValue();
			int absEnd = selectedEndNode.get().start.getPosition() + end.intValue();
			String expression = theDocumentContent.substring(absStart, absEnd);
			watch.setExpressionText(expression);
		}
		watchExpressions.add(watch);
	}

	public void addWatchAction(DocumentComponent selected) {
		DocumentComponent context = selected;
		while (context != null && context.element == null)
			context = context.parent;
		if (context == null) {
			System.err.println("Cannot add watch action at " + selected + "--no valid context");
			return;
		}
		WatchAction watch = new WatchAction(watchActions, context.interpreted, context.element);
		Integer start = selectedStartIndex.get();
		Integer end = selectedEndIndex.get();
		if (start != null && end != null && !start.equals(end)) {
			int absStart = selectedNode.get().start.getPosition() + start.intValue();
			int absEnd = selectedEndNode.get().start.getPosition() + end.intValue();
			String expression = theDocumentContent.substring(absStart, absEnd);
			watch.setExpressionText(expression);
		}
		watchActions.add(watch);
	}

	private void clear() {
		clearDef();
		theDocumentContent.setLength(0);
		clearRender();
		document.set(null, null);
	}

	private void clearDef() {
		clearInterpreted();
		theDocumentDef = null;
		theQwysiwygEdAddOn = null;
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
		document.set(new StyledQuickDocument(theDocumentURL, theDocumentContent.toString(), this::goTo), null);
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

	void goTo(DocumentComponent target) {
		try (Causable.CausableInUse cause = Causable.cause(); //
			Transaction vt = selectedNode.lock(true, cause);
			Transaction vt2 = selectedEndNode.lock(true, cause); //
			Transaction sit = selectedStartIndex.lock(true, cause);
			Transaction eit = selectedEndIndex.lock(true, cause)) {
			selectedNode.set(target, cause);
			selectedEndNode.set(target, cause);
			selectedStartIndex.set(0, cause);
			selectedEndIndex.set(target.getTextLength(), cause);
		}
	}

	private void renderXml(URL quickFile) {
		clearRender();
		DequeList<DocumentComponent> stack = new CircularArrayList<>();
		stack.add(document.get().getRoot());
		try (InputStream in = quickFile.openStream()) {
			new SimpleXMLParser().parseXml(quickFile.toString(), in, new SimpleXMLParser.ParseHandler() {
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
					elStartComp.addChild(element.getContent().getPosition(element.getNameOffset()))//
					.end(element.getContent().getPosition(element.getNameOffset() + element.getName().length()));
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
				public void handleElementContent(String elementName, PositionedContent elementValue) {}

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
					DocumentComponent endComp;
					if (!selfClosing) {
						endComp = stack.getLast().addChild(element.getContent().getPosition(0)).color(ELEMENT_COLOR);
						endComp.end(element.getContent().getPosition(element.getContent().length()));
					} else
						endComp = null;
					// Remove the component for the element
					DocumentComponent open = stack.removeLast().end(element.getContent().getPosition(element.getContent().length()));
					if (endComp != null)
						open.children.getFirst().children.getFirst().setOpposite(endComp);
				}
			});
		} catch (IOException | XmlParseException e) {
			// Should have been reported elsewhere, nothing to do here
		}
	}

	private void renderDef(DocumentComponent component, ExElement.Def<?> def, String roleDescrip) {
		DocumentComponent elComponent = StyledQuickDocument.getSourceComponent(component,
			def.getElement().getPositionInFile().getPosition()).parent;
		String typeDescrip = def.getElement().getType().getDescription();
		if (roleDescrip != null) {
			if (typeDescrip != null)
				elComponent.typeTooltip(typeDescrip + "<br><br>" + roleDescrip);
			else
				elComponent.typeTooltip(roleDescrip);
		} else if (typeDescrip != null)
			elComponent.typeTooltip(typeDescrip);
		for (Map.Entry<QonfigAttributeDef.Declared, AttributeValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = StyledQuickDocument.getSourceComponent(elComponent, position.getPosition(0).getPosition());
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
			DocumentComponent attrValueComp = StyledQuickDocument.getSourceComponent(elComponent, position.getPosition(0).getPosition());
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
		Map<QonfigElement, ExElement.Def<?>> children = def.getAllDefChildren().stream()
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
				StyledQuickDocument.getSourceComponent(elComponent, child.getPositionInFile().getPosition()).parent
				.typeTooltip(childDescrip);
		}
	}

	private void renderCompiledExpression(ObservableExpression ex, LocatedPositionedContent content, DocumentComponent component) {
		if (!content.getFileLocation().equals(theDocumentURL))
			return;
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

	private static final String TOOLKIT_NAME = "QWYSIWYG";
	private static final Version TOOLKIT_VERSION = new Version(0, 1, 0);
	private static final String QWYSIWYG_ED_NAME = "qwysiwyg-ed";
	private static final String QWYSIWYG_HOVERED = "qwysiwygHovered";

	private QonfigAddOn theQwysiwygEdAddOn;

	static QonfigAddOn findQwysiwygEdAddOn(QonfigToolkit toolkit) {
		if (toolkit.getName().equals(TOOLKIT_NAME) && toolkit.getMajorVersion() == TOOLKIT_VERSION.major
			&& toolkit.getMinorVersion() == TOOLKIT_VERSION.minor) {
			for (QonfigAddOn addOn : toolkit.getDeclaredAddOns().values()) {
				if (addOn.getName().equals(QWYSIWYG_ED_NAME))
					return addOn;
			}
		} else {
			for (QonfigToolkit dep : toolkit.getDependencies().values()) {
				QonfigAddOn found = findQwysiwygEdAddOn(dep);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	private <E extends ExElement> void renderInterpreted(DocumentComponent component, ExElement.Interpreted<E> interpreted, E element,
		ModelSetInstance models) {
		if (interpreted.getParentElement() == null && theQwysiwygEdAddOn == null) {
			theQwysiwygEdAddOn = findQwysiwygEdAddOn(interpreted.getDefinition().getElement().getDocument().getDocToolkit());
		}
		InterpretedExpressoEnv env = interpreted == null ? null : interpreted.getExpressoEnv();
		ExElement.Def<? super E> def = interpreted.getDefinition();
		DocumentComponent elComponent = StyledQuickDocument.getSourceComponent(component,
			def.getElement().getPositionInFile().getPosition()).parent;
		if (models != null && theQwysiwygEdAddOn != null && interpreted.getDefinition().getElement().isInstance(theQwysiwygEdAddOn)) {
			try {
				ExWithElementModel.Interpreted elModels = interpreted.getAddOn(ExWithElementModel.Interpreted.class);
				elComponent.elementHovered = SettableValue.<Boolean> build().withValue(false).build();
				elModels.satisfyElementValue(QWYSIWYG_HOVERED, models, elComponent.elementHovered);
			} catch (ModelInstantiationException e) {
				System.err.println("Could not install " + TOOLKIT_NAME + " toolkit values");
				e.printStackTrace();
			}
		}

		elComponent.interpreted = interpreted;
		elComponent.element = element;
		if (models != null && interpreted instanceof ModelValueElement.InterpretedSynth)
			renderInstance(elComponent, (ModelValueElement.InterpretedSynth<?, ?, ?>) interpreted, env, models);
		for (Map.Entry<QonfigAttributeDef.Declared, AttributeValue> attr : def.getElement().getAttributes().entrySet()) {
			PositionedContent position = attr.getValue().position;
			if (position == null)
				continue;
			DocumentComponent attrValueComp = StyledQuickDocument.getSourceComponent(elComponent, position.getPosition(0).getPosition());
			QonfigValueType type = attr.getKey().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object interpValue = def.getAttribute(interpreted, attr.getKey());
					if (interpValue instanceof InterpretedValueSynth) {
						EvaluatedExpression<?, ?> expression = getEvaluatedExpression((InterpretedValueSynth<?, ?>) interpValue);
						if (expression != null)
							renderInterpretedExpression(expression, attrValueComp, env, models);
					}
				}
			} else {
				Object interpValue = def.getAttribute(interpreted, attr.getKey());
				if (interpValue instanceof SelfDescribed)
					attrValueComp.interpretedTooltip(((SelfDescribed) interpValue)::getDescription);
			}
		}
		if (def.getElement().getValue() != null && def.getElement().getValue().position != null) {
			PositionedContent position = def.getElement().getValue().position;
			DocumentComponent attrValueComp = StyledQuickDocument.getSourceComponent(elComponent, position.getPosition(0).getPosition());
			QonfigValueType type = def.getElement().getType().getValue().getType();
			if (type instanceof QonfigValueType.Custom) {
				CustomValueType customType = ((QonfigValueType.Custom) type).getCustomType();
				if (customType instanceof ExpressionValueType) {
					Object interpValue = def.getElementValue(interpreted);
					if (interpValue instanceof InterpretedValueSynth) {
						EvaluatedExpression<?, ?> expression = getEvaluatedExpression((InterpretedValueSynth<?, ?>) interpValue);
						if (expression != null)
							renderInterpretedExpression(expression, attrValueComp, env, models);
					}
				}
			} else {
				Object interpValue = def.getElementValue(interpreted);
				if (interpValue instanceof SelfDescribed)
					attrValueComp.interpretedTooltip(((SelfDescribed) interpValue)::getDescription);
			}
		}
		List<? extends ExElement.Interpreted<?>> interpretedChildren = def.getAllInterpretedChildren(interpreted);
		List<? extends ExElement> tempChildren = element == null ? Collections.emptyList() : def.getAllElementChildren(element);
		Map<Object, ? extends ExElement> instanceChildren = tempChildren.stream()
			.collect(Collectors.toMap(ch -> ch.getIdentity(), ch -> ch));
		for (ExElement.Interpreted<?> child : interpretedChildren) {
			ExElement instance = instanceChildren.get(child.getIdentity());
			this.renderInterpreted(elComponent, (ExElement.Interpreted<ExElement>) child, instance,
				instance != null ? instance.getUpdatingModels() : models);
		}
	}

	private static EvaluatedExpression<?, ?> getEvaluatedExpression(InterpretedValueSynth<?, ?> value) {
		while (!(value instanceof EvaluatedExpression) && value.getComponents().size() == 1)
			value = value.getComponents().get(0);
		return value instanceof EvaluatedExpression ? (EvaluatedExpression<?, ?>) value : null;
	}

	private void renderInterpretedExpression(EvaluatedExpression<?, ?> expression, DocumentComponent component, InterpretedExpressoEnv env,
		ModelSetInstance models) {
		component.descriptor = expression.getDescriptor();
		component.target = getLinkTarget(component.descriptor);
		component.interpretedTooltip(() -> renderInterpretedDescriptor(component.descriptor));
		if (models != null && isFundamentalValue(expression))
			renderInstance(component, expression, env, models);
		List<? extends EvaluatedExpression<?, ?>> components = expression.getComponents();
		List<? extends EvaluatedExpression<?, ?>> divisions = expression.getDivisions();
		if (component.children.size() == components.size() + divisions.size()) {
			int c = 0;
			for (EvaluatedExpression<?, ?> child : components)
				renderInterpretedExpression(child, component.children.get(c++), env, models);
			for (EvaluatedExpression<?, ?> div : divisions)
				renderInterpretedExpression(div, component.children.get(c++), env, models);
		}
	}

	private void renderInstance(DocumentComponent component, InterpretedValueSynth<?, ?> expression, InterpretedExpressoEnv env,
		ModelSetInstance models) {
		Object value;
		Observable<?> update;
		try {
			value = expression.instantiate().get(models);
			if (value instanceof SettableValue)
				component.instanceTooltip(((SettableValue<?>) value).map(String::valueOf));
			else {
				try {
					update = expression.as(ModelTypes.Event.any(), env, ExceptionHandler.thrower()).instantiate().get(models);
				} catch (TypeConversionException e) {
					update = null;
				}
				if (value instanceof Stamped)
					component.instanceTooltip(
						ObservableValue.of(() -> String.valueOf(value), ((Stamped) value)::getStamp, update));
				else {
					long[] stamp = new long[1];
					if (update != null) {
						update.takeUntil(models.getUntil()).act(__ -> stamp[0]++);
						component.instanceTooltip(ObservableValue.of(() -> String.valueOf(value), () -> stamp[0], update));
					} else {
						component.instanceTooltip(
							ObservableValue.of(() -> String.valueOf(value), () -> stamp[0]++, Observable.empty()));
					}
				}
			}
		} catch (ModelInstantiationException | RuntimeException e) {
			logToConsole(e, new LocatedPositionedContent.SimpleLine(new LocatedFilePosition(theAppLocation, component.start), ""));
		}
	}

	private boolean isFundamentalValue(EvaluatedExpression<?, ?> expression) {
		if (!expression.getComponents().isEmpty())
			return false;
		Object descriptor = expression.getDescriptor();
		if (descriptor instanceof ModelComponentNode) {
			ModelType<?> modelType;
			try {
				modelType = ((ModelComponentNode<?>) descriptor).getModelType(theCompiledEnv);
			} catch (ExpressoCompilationException e) {
				e.printStackTrace();
				return false;
			}
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
			return ((ModelComponentNode<?>) descriptor).getSourceLocation();
		else if (descriptor instanceof ElementModelValue) {
			ElementModelValue<?> dmv = (ElementModelValue<?>) descriptor;
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
			ModelComponentNode<?> node = (ModelComponentNode<?>) descriptor;
			Object id = node.getValueIdentity() != null ? node.getValueIdentity() : node.getIdentity();
			if (id instanceof ElementModelValue.Identity) {
				ElementModelValue.Identity dmv = (ElementModelValue.Identity) id;
				if (dmv.getDeclaration().getDescription() != null)
					return dmv.getDeclaration().getDescription();
				return "Element value " + dmv.toString();
			}
			StringBuilder str = new StringBuilder();
			Object thing = node.getThing();
			if (thing instanceof ObservableModelSet)
				str.append("Model ").append(id);
			else if (thing instanceof CompiledModelValue) {
				if (id instanceof ElementModelValue.Identity)
					str.append("Element model value ");
				else
					str.append("Model value ");
				str.append(id.toString()).append(": ");
			} else if (thing instanceof ExtValueRef)
				str.append("External value ").append(id).append(": ");
			else
				str.append("Model component ").append(id);
			if (node instanceof InterpretedValueSynth)
				str.append(renderType(((InterpretedValueSynth<?, ?>) node).getType()));
			else {
				try {
					str.append(renderType(node.getModelType(theCompiledEnv).any()));
				} catch (ExpressoCompilationException e) {}
			}
			if (node.getSourceLocation() != null) {
				str.append("<br>");
				if (node.getSourceLocation().getFileLocation() != null
					&& !node.getSourceLocation().getFileLocation().equals(theDocumentURL))
					str.append(node.getSourceLocation().getFileLocation()).append(' ');
				str.append('L').append(node.getSourceLocation().getLineNumber() + 1).append(" C")
				.append(node.getSourceLocation().getCharNumber() + 1);
			}
			return str.toString();
		} else if (descriptor instanceof ElementModelValue) {
			ElementModelValue<?> dmv = (ElementModelValue<?>) descriptor;
			if (dmv.getDeclaration().getDeclaration().getDescription() != null)
				return dmv.getDeclaration().getDeclaration().getDescription();
			return "Element value " + dmv.getDeclaration().toString();
		} else if (descriptor instanceof SelfDescribed)
			return ((SelfDescribed) descriptor).getDescription();
		else if (descriptor instanceof ObservableExpression.LiteralExpression) {
			ObservableExpression.LiteralExpression<?> ex = (ObservableExpression.LiteralExpression<?>) descriptor;
			if (ex.getValue() == null)
				return "null literal";
			return TypeTokens.getSimpleName(TypeTokens.get().unwrap(ex.getValue().getClass())) + " literal '" + ex.getValue() + "'";
		} else
			return "Unrecognized value descriptor: " + descriptor.getClass().getName() + ": " + descriptor;
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
		document.set(document.get(), null);
	}
}
