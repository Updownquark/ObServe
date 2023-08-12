package org.observe.quick.base;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickTextElement;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class StyledTextArea<T> extends QuickTextWidget.Abstract<T> {
	public static final String STYLED_TEXT_AREA = "styled-text-area";
	private static final SingleTypeTraceability<StyledTextArea<?>, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, STYLED_TEXT_AREA, Def.class,
			Interpreted.class, StyledTextArea.class);
	public static final String TEXT_STYLE = "text-style";

	public static class Def<T> extends QuickTextWidget.Def.Abstract<T, StyledTextArea<? extends T>> {
		private CompiledExpression theChildren;
		private CompiledExpression thePostText;
		private CompiledExpression theRows;
		private CompiledExpression theSelectionStartValue;
		private CompiledExpression theSelectionStartOffset;
		private CompiledExpression theSelectionEndValue;
		private CompiledExpression theSelectionEndOffset;
		private TextStyleElement.Def theTextStyle;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return false;
		}

		@QonfigAttributeGetter("children")
		public CompiledExpression getChildren() {
			return theChildren;
		}

		@QonfigAttributeGetter("post-text")
		public CompiledExpression getPostText() {
			return thePostText;
		}

		@QonfigAttributeGetter("rows")
		public CompiledExpression getRows() {
			return theRows;
		}

		@QonfigChildGetter("text-style")
		public TextStyleElement.Def getTextStyle() {
			return theTextStyle;
		}

		@QonfigAttributeGetter("selection-start-value")
		public CompiledExpression getSelectionStartValue() {
			return theSelectionStartValue;
		}

		@QonfigAttributeGetter("selection-start-offset")
		public CompiledExpression getSelectionStartOffset() {
			return theSelectionStartOffset;
		}

		@QonfigAttributeGetter("selection-end-value")
		public CompiledExpression getSelectionEndValue() {
			return theSelectionEndValue;
		}

		@QonfigAttributeGetter("selection-end-offset")
		public CompiledExpression getSelectionEndOffset() {
			return theSelectionEndOffset;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theChildren = session.getAttributeExpression("children");
			thePostText = session.getAttributeExpression("post-text");
			theRows = session.getAttributeExpression("rows");
			theSelectionStartValue = session.getAttributeExpression("selection-start-value");
			theSelectionStartOffset = session.getAttributeExpression("selection-start-offset");
			theSelectionEndValue = session.getAttributeExpression("selection-end-value");
			theSelectionEndOffset = session.getAttributeExpression("selection-end-offset");
			theTextStyle = ExElement.useOrReplace(TextStyleElement.Def.class, theTextStyle, session, "text-style");
			getAddOn(ExWithElementModel.Def.class).satisfyElementValueType("node", ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getValueType()));
		}

		@Override
		public Interpreted<? extends T> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickTextWidget.Interpreted.Abstract<T, StyledTextArea<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theChildren;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePostText;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRows;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectionStartValue;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSelectionStartOffset;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectionEndValue;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSelectionEndOffset;
		private TextStyleElement.Interpreted theTextStyle;

		public Interpreted(Def<? super T> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			/* TODO
			 * It's really unfortunate that we have to do this, but in order to generate an independent, listenable collection for each node,
			 * we have to keep the model instance around to create copies.
			 *
			 * In other widgets I've created so far, the values needed from elements in a collection could be transient, where the value is
			 * inspected and then the observable value could be repurposed for the next child.  But we need to keep the child collection
			 * around for each node independently so we can be notified of changes deep in the hierarchy.
			 *
			 * This is a problem because the ModelSetInstance API currently keeps and exposes its source models.
			 * This means that the structures used to create the models, including expressions, text, Qonfig elements, etc.
			 * must be kept around in memory.  Up to now I'd been able to get away with releasing these after the widget instances were built.
			 *
			 * We also need a reference to the InterpretedValueSynth to create the collection from model copies.  That structure is typically
			 * composed of references to other model values in the form of InterpretedModelComponentNodes, which have references to the models.
			 *
			 * I think the solution to this is to divorce ModelSetInstance and InterpretedValueSynth from the source models.
			 * Once that's done, I think we can delete this comment and accept the way this is done.
			 */
			persistModelInstances(true);
		}

		@Override
		public Def<? super T> getDefinition() {
			return (Def<? super T>) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getChildren() {
			return theChildren;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getPostText() {
			return thePostText;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRows() {
			return theRows;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectionStartValue() {
			return theSelectionStartValue;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSelectionStartOffset() {
			return theSelectionStartOffset;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectionEndValue() {
			return theSelectionEndValue;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSelectionEndOffset() {
			return theSelectionEndOffset;
		}

		public TextStyleElement.Interpreted getTextStyle() {
			return theTextStyle;
		}

		@Override
		public TypeToken<StyledTextArea<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(StyledTextArea.class).<StyledTextArea<T>> parameterized(getValueType());
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theChildren = getDefinition().getChildren().interpret(ModelTypes.Collection.forType(getValueType()), getExpressoEnv());
			thePostText = getDefinition().getPostText() == null ? null
				: getDefinition().getPostText().interpret(ModelTypes.Value.STRING, getExpressoEnv());
			theRows = getDefinition().getRows() == null ? null
				: getDefinition().getRows().interpret(ModelTypes.Value.INT, getExpressoEnv());
			theSelectionStartValue = getDefinition().getSelectionStartValue() == null ? null
				: getDefinition().getSelectionStartValue().interpret(ModelTypes.Value.forType(getValueType()), getExpressoEnv());
			theSelectionStartOffset = getDefinition().getSelectionStartOffset() == null ? null
				: getDefinition().getSelectionStartOffset().interpret(ModelTypes.Value.forType(int.class), getExpressoEnv());
			theSelectionEndValue = getDefinition().getSelectionEndValue() == null ? null
				: getDefinition().getSelectionEndValue().interpret(ModelTypes.Value.forType(getValueType()), getExpressoEnv());
			theSelectionEndOffset = getDefinition().getSelectionEndOffset() == null ? null
				: getDefinition().getSelectionEndOffset().interpret(ModelTypes.Value.forType(int.class), getExpressoEnv());
			if (theTextStyle == null || theTextStyle.getDefinition() != getDefinition().getTextStyle())
				theTextStyle = getDefinition().getTextStyle().interpret(this);
			theTextStyle.updateElement(getExpressoEnv());
		}

		@Override
		public StyledTextArea<T> create(ExElement parent) {
			return new StyledTextArea<>(this, parent);
		}
	}

	public interface StyledTextAreaContext<T> {
		SettableValue<T> getNodeValue();

		public class Default<T> implements StyledTextAreaContext<T> {
			private final SettableValue<T> theNodeValue;

			public Default(SettableValue<T> nodeValue) {
				theNodeValue = nodeValue;
			}

			public Default(TypeToken<T> type) {
				this(SettableValue.build(type).build());
			}

			@Override
			public SettableValue<T> getNodeValue() {
				return theNodeValue;
			}
		}
	}

	private SettableValue<Integer> theRows;
	private TextStyleElement theTextStyle;
	private final SettableValue<SettableValue<T>> theNodeValue;
	private final SettableValue<SettableValue<T>> theSelectionStartValue;
	private final SettableValue<SettableValue<Integer>> theSelectionStartOffset;
	private final SettableValue<SettableValue<T>> theSelectionEndValue;
	private final SettableValue<SettableValue<Integer>> theSelectionEndOffset;

	private ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> theValueType;
	private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theChildrenSynth;
	private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePostTextSynth;

	public StyledTextArea(Interpreted<T> interpreted, ExElement parent) {
		super(interpreted, parent);
		TypeToken<T> valueType;
		try {
			valueType = interpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not interpreted?", e);
		}
		theNodeValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(valueType))
			.build();
		theSelectionStartValue = SettableValue.build(theNodeValue.getType()).build();
		theSelectionStartOffset = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
		theSelectionEndValue = SettableValue.build(theSelectionStartValue.getType()).build();
		theSelectionEndOffset = SettableValue.build(theSelectionStartOffset.getType()).build();
	}

	public ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> getValueType() {
		return theValueType;
	}

	public boolean hasPostText() {
		return thePostTextSynth != null;
	}

	public SettableValue<Integer> getRows() {
		return theRows;
	}

	public TextStyleElement getTextStyle() {
		return theTextStyle;
	}

	public SettableValue<T> getNodeValue() {
		return SettableValue.flatten(theNodeValue);
	}

	public SettableValue<T> getSelectionStartValue() {
		return SettableValue.flatten(theSelectionStartValue);
	}

	public SettableValue<Integer> getSelectionStartOffset() {
		return SettableValue.flatten(theSelectionStartOffset, () -> 0);
	}

	public SettableValue<T> getSelectionEndValue() {
		return SettableValue.flatten(theSelectionEndValue);
	}

	public SettableValue<Integer> getSelectionEndOffset() {
		return SettableValue.flatten(theSelectionEndOffset, () -> 0);
	}

	public ObservableCollection<? extends T> getChildren(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance modelCopy = getUpdatingModels().copy().build();
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", ctx.getNodeValue(), modelCopy,
			ExWithElementModel.ActionIfSatisfied.Replace);
		// After synthesizing and returning the children for the node, we can discard the model copy
		return theChildrenSynth.get(modelCopy);
	}

	public TextStyle getStyle(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance widgetModelCopy = getUpdatingModels().copy().build();
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", ctx.getNodeValue(), widgetModelCopy,
			ExWithElementModel.ActionIfSatisfied.Replace);
		ModelSetInstance styleElementModelCopy = getTextStyle().getUpdatingModels().copy()//
			.withAll(widgetModelCopy)//
			.build();

		return theTextStyle.getStyle().copy(styleElementModelCopy);
	}

	public SettableValue<String> getPostText(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance modelCopy = getUpdatingModels().copy().build();
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", ctx.getNodeValue(), modelCopy,
			ExWithElementModel.ActionIfSatisfied.Replace);
		// After synthesizing and returning the post text, we can discard the model copy
		return thePostTextSynth.get(modelCopy);
	}

	public void setTextAreaContext(StyledTextAreaContext<T> ctx) {
		theNodeValue.set(ctx.getNodeValue(), null);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		getAddOn(ExWithElementModel.class).satisfyElementValue("node", getNodeValue());
		super.updateModel(interpreted, myModels);
		theRows = myInterpreted.getRows() == null ? null : myInterpreted.getRows().get(myModels);
		theSelectionStartValue
		.set(myInterpreted.getSelectionStartValue() == null ? null : myInterpreted.getSelectionStartValue().get(myModels), null);
		theSelectionStartOffset
		.set(myInterpreted.getSelectionStartOffset() == null ? null : myInterpreted.getSelectionStartOffset().get(myModels), null);
		theSelectionEndValue.set(myInterpreted.getSelectionEndValue() == null ? null : myInterpreted.getSelectionEndValue().get(myModels),
			null);
		theSelectionEndOffset
		.set(myInterpreted.getSelectionEndOffset() == null ? null : myInterpreted.getSelectionEndOffset().get(myModels), null);
		if (theTextStyle == null || theTextStyle.getIdentity() != myInterpreted.getTextStyle().getDefinition().getIdentity())
			theTextStyle = myInterpreted.getTextStyle().create(this);
		theTextStyle.update(myInterpreted.getTextStyle(), myModels);

		TypeToken<T> valueType;
		try {
			valueType = myInterpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new ModelInstantiationException("Not evaluated yet??!!", e.getPosition(), e.getErrorLength(), e);
		}
		theValueType = ModelTypes.Value.forType(valueType);
		theChildrenSynth = myInterpreted.getChildren();
		thePostTextSynth = myInterpreted.getPostText();
	}

	public static class TextStyleElement extends QuickStyledElement.Abstract implements QuickTextElement {
		private static final SingleTypeTraceability<TextStyleElement, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TEXT_STYLE, Def.class, Interpreted.class,
				TextStyleElement.class);

		public static class Def extends QuickStyledElement.Def.Abstract<TextStyleElement>
		implements QuickTextElement.Def<TextStyleElement> {
			public Def(StyledTextArea.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public StyledTextArea.Def<?> getParentElement() {
				return (StyledTextArea.Def<?>) super.getParentElement();
			}

			@Override
			public TextStyle.Def getStyle() {
				return (TextStyle.Def) super.getStyle();
			}

			@Override
			protected TextStyle.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new TextStyle.Def(parentStyle, this, style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement("styled")); // No super element
			}

			public Interpreted interpret(StyledTextArea.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickStyledElement.Interpreted.Abstract<TextStyleElement>
		implements QuickTextElement.Interpreted<TextStyleElement> {
			public Interpreted(TextStyleElement.Def definition, StyledTextArea.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public StyledTextArea.Interpreted<?> getParentElement() {
				return (StyledTextArea.Interpreted<?>) super.getParentElement();
			}

			@Override
			public TextStyle.Interpreted getStyle() {
				return (TextStyle.Interpreted) super.getStyle();
			}

			public TextStyleElement create(StyledTextArea<?> parent) {
				return new TextStyleElement(this, parent);
			}
		}

		public TextStyleElement(Interpreted interpreted, StyledTextArea<?> parent) {
			super(interpreted, parent);
		}

		@Override
		public StyledTextArea<?> getParentElement() {
			return (StyledTextArea<?>) super.getParentElement();
		}

		@Override
		public TextStyle getStyle() {
			return (TextStyle) super.getStyle();
		}
	}

	public static class TextStyle extends QuickTextElement.QuickTextStyle.Abstract {
		public static class Def extends QuickTextElement.QuickTextStyle.Def.Abstract {
			private QuickStyleAttributeDef theBackground;

			public Def(QuickInstanceStyle.Def parent, TextStyleElement.Def styledElement, QuickCompiledStyle wrapped) {
				super(parent, styledElement, wrapped);
				QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(),
					QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TEXT_STYLE);
				theBackground = addApplicableAttribute(typeStyle.getAttribute("bg-color"));
			}

			public QuickStyleAttributeDef getBackground() {
				return theBackground;
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				return new Interpreted(this, (TextStyleElement.Interpreted) parentEl, (QuickInstanceStyle.Interpreted) parent,
					getWrapped().interpret(parentEl, parent, env));
			}
		}

		public static class Interpreted extends QuickTextElement.QuickTextStyle.Interpreted.Abstract {
			private QuickElementStyleAttribute<Color> theBackground;

			public Interpreted(Def definition, TextStyleElement.Interpreted styledElement, QuickInstanceStyle.Interpreted parent,
				QuickInterpretedStyle wrapped) {
				super(definition, styledElement, parent, wrapped);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public QuickElementStyleAttribute<Color> getBackground() {
				return theBackground;
			}

			@Override
			public void update(InterpretedExpressoEnv env, Applications appCache) throws ExpressoInterpretationException {
				super.update(env, appCache);
				QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
				theBackground = get(cache.getAttribute(getDefinition().getBackground(), Color.class, env));
			}

			@Override
			public TextStyle create(QuickStyledElement styledElement) {
				return new TextStyle(this, (TextStyleElement) styledElement);
			}
		}

		private final ObservableValue<Color> theBackground;

		// We need to keep this around to make copies of ourself
		private final Interpreted theInterpreted;

		public TextStyle(Interpreted interpreted, TextStyleElement styledElement) {
			super(interpreted, styledElement);
			theInterpreted = interpreted;
			theBackground = getApplicableAttribute(interpreted.getBackground().getAttribute());
		}

		@Override
		public TextStyleElement getStyledElement() {
			return (TextStyleElement) super.getStyledElement();
		}

		public ObservableValue<Color> getBackground() {
			return theBackground;
		}

		public TextStyle copy(ModelSetInstance styleElementModels) throws ModelInstantiationException {
			TextStyle copy = new TextStyle(theInterpreted, getStyledElement());
			copy.update(theInterpreted, styleElementModels);
			return copy;
		}
	}
}
