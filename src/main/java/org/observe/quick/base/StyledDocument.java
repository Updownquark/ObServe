package org.observe.quick.base;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.MouseCursor;
import org.observe.quick.QuickTextElement;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.QuickWithBackground.QuickBackgroundStyle;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickStyledElement.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A text document, portions of which may be styled
 *
 * @param <T> The type of values in the document
 */
public abstract class StyledDocument<T> extends ExElement.Abstract {
	/** The XML name of this element */
	public static final String STYLED_DOCUMENT = "styled-document";
	/** The XML name of the text-style element */
	public static final String TEXT_STYLE = "text-style";

	/**
	 * {@link StyledDocument} definition
	 *
	 * @param <D> The sub-type of document to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = STYLED_DOCUMENT,
		interpretation = Interpreted.class,
		instance = StyledDocument.class)
	public static abstract class Def<D extends StyledDocument<?>> extends ExElement.Def.Abstract<D> {
		private CompiledExpression theSelectionStartValue;
		private CompiledExpression theSelectionStartOffset;
		private CompiledExpression theSelectionEndValue;
		private CompiledExpression theSelectionEndOffset;

		/**
		 * @param parent The parent element of the document
		 * @param type The Qonfig type of the document
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return Whether this document may support editing */
		public abstract boolean isTypeEditable();

		/** @return The document value at the start of the user's selection */
		@QonfigAttributeGetter("selection-start-value")
		public CompiledExpression getSelectionStartValue() {
			return theSelectionStartValue;
		}

		/**
		 * @return The offset from the start of the {@link #getSelectionStartValue()}'s representation in the document at the start of the
		 *         user's selection
		 */
		@QonfigAttributeGetter("selection-start-offset")
		public CompiledExpression getSelectionStartOffset() {
			return theSelectionStartOffset;
		}

		/** @return The document value at the end of the user's selection */
		@QonfigAttributeGetter("selection-end-value")
		public CompiledExpression getSelectionEndValue() {
			return theSelectionEndValue;
		}

		/**
		 * @return The offset from the start of the {@link #getSelectionEndValue()}'s representation in the document at the end of the
		 *         user's selection
		 */
		@QonfigAttributeGetter("selection-end-offset")
		public CompiledExpression getSelectionEndOffset() {
			return theSelectionEndOffset;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theSelectionStartValue = getAttributeExpression("selection-start-value", session);
			theSelectionStartOffset = getAttributeExpression("selection-start-offset", session);
			theSelectionEndValue = getAttributeExpression("selection-end-value", session);
			theSelectionEndOffset = getAttributeExpression("selection-end-offset", session);
		}

		/**
		 * @param parent The parent element for the interpreted document
		 * @return The interpreted document
		 */
		public abstract Interpreted<?, ? extends D> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * {@link StyledDocument} interpretation
	 *
	 * @param <T> The type of values in the document
	 * @param <D> The sub-type of document to create
	 */
	public static abstract class Interpreted<T, D extends StyledDocument<T>> extends ExElement.Interpreted.Abstract<D> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectionStartValue;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSelectionStartOffset;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSelectionEndValue;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSelectionEndOffset;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the document
		 */
		protected Interpreted(Def<? super D> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super D> getDefinition() {
			return (Def<? super D>) super.getDefinition();
		}

		/**
		 * @return The type of values in the document
		 * @throws ExpressoInterpretationException If the value type could not be interpreted
		 */
		public abstract TypeToken<T> getValueType() throws ExpressoInterpretationException;

		/** @return The document value at the start of the user's selection */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectionStartValue() {
			return theSelectionStartValue;
		}

		/**
		 * @return The offset from the start of the {@link #getSelectionStartValue()}'s representation in the document at the start of the
		 *         user's selection
		 */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSelectionStartOffset() {
			return theSelectionStartOffset;
		}

		/** @return The document value at the end of the user's selection */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSelectionEndValue() {
			return theSelectionEndValue;
		}

		/**
		 * @return The offset from the start of the {@link #getSelectionEndValue()}'s representation in the document at the end of the
		 *         user's selection
		 */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSelectionEndOffset() {
			return theSelectionEndOffset;
		}

		/**
		 * Initializes or updates this document
		 *
		 * @param env the expresso environment for interpreting expressions
		 * @throws ExpressoInterpretationException If this document could not be interpreted
		 */
		public void updateDocument(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			update(env);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theSelectionStartValue = interpret(getDefinition().getSelectionStartValue(), ModelTypes.Value.forType(getValueType()));
			theSelectionStartOffset = interpret(getDefinition().getSelectionStartOffset(), ModelTypes.Value.forType(int.class));
			theSelectionEndValue = interpret(getDefinition().getSelectionEndValue(), ModelTypes.Value.forType(getValueType()));
			theSelectionEndOffset = interpret(getDefinition().getSelectionEndOffset(), ModelTypes.Value.forType(int.class));
		}

		/** @return The instantiated document */
		public abstract StyledDocument<T> create();
	}

	private ModelValueInstantiator<SettableValue<T>> theSelectionStartValueInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theSelectionStartOffsetInstantiator;
	private ModelValueInstantiator<SettableValue<T>> theSelectionEndValueInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theSelectionEndOffsetInstantiator;

	private SettableValue<SettableValue<T>> theSelectionStartValue;
	private SettableValue<SettableValue<Integer>> theSelectionStartOffset;
	private SettableValue<SettableValue<T>> theSelectionEndValue;
	private SettableValue<SettableValue<Integer>> theSelectionEndOffset;

	private ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> theValueType;

	/** @param id The element ID for this document */
	protected StyledDocument(Object id) {
		super(id);
	}

	/** @return The model type of values in the document */
	public ModelInstanceType.SingleTyped<SettableValue<?>, T, SettableValue<T>> getValueType() {
		return theValueType;
	}

	/** @return The document value at the start of the user's selection */
	public SettableValue<T> getSelectionStartValue() {
		return SettableValue.flatten(theSelectionStartValue);
	}

	/**
	 * @return The offset from the start of the {@link #getSelectionStartValue()}'s representation in the document at the start of the
	 *         user's selection
	 */
	public SettableValue<Integer> getSelectionStartOffset() {
		return SettableValue.flatten(theSelectionStartOffset, () -> 0);
	}

	/** @return The document value at the end of the user's selection */
	public SettableValue<T> getSelectionEndValue() {
		return SettableValue.flatten(theSelectionEndValue);
	}

	/**
	 * @return The offset from the start of the {@link #getSelectionEndValue()}'s representation in the document at the end of the user's
	 *         selection
	 */
	public SettableValue<Integer> getSelectionEndOffset() {
		return SettableValue.flatten(theSelectionEndOffset, () -> 0);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;

		TypeToken<T> valueType;
		try {
			valueType = myInterpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not interpreted?", e);
		}
		if (theSelectionStartValue == null || !theValueType.getType(0).equals(valueType)) {
			theValueType = ModelTypes.Value.forType(valueType);
			TypeToken<SettableValue<T>> containerType = TypeTokens.get().keyFor(SettableValue.class)
				.<SettableValue<T>> parameterized(valueType);
			theSelectionStartValue = SettableValue.build(containerType).build();
			theSelectionStartOffset = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
			theSelectionEndValue = SettableValue.build(containerType).build();
			theSelectionEndOffset = SettableValue.build(theSelectionStartOffset.getType()).build();
		}
		theSelectionStartValueInstantiator = myInterpreted.getSelectionStartValue() == null ? null
			: myInterpreted.getSelectionStartValue().instantiate();
		theSelectionStartOffsetInstantiator = myInterpreted.getSelectionStartOffset() == null ? null
			: myInterpreted.getSelectionStartOffset().instantiate();
		theSelectionEndValueInstantiator = myInterpreted.getSelectionEndValue() == null ? null
			: myInterpreted.getSelectionEndValue().instantiate();
		theSelectionEndOffsetInstantiator = myInterpreted.getSelectionEndOffset() == null ? null
			: myInterpreted.getSelectionEndOffset().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theSelectionStartValueInstantiator != null)
			theSelectionStartValueInstantiator.instantiate();
		if (theSelectionStartOffsetInstantiator != null)
			theSelectionStartOffsetInstantiator.instantiate();
		if (theSelectionEndValueInstantiator != null)
			theSelectionEndValueInstantiator.instantiate();
		if (theSelectionEndOffsetInstantiator != null)
			theSelectionEndOffsetInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		theSelectionStartValue.set(theSelectionStartValueInstantiator == null ? null : theSelectionStartValueInstantiator.get(myModels),
			null);
		theSelectionStartOffset.set(theSelectionStartOffsetInstantiator == null ? null : theSelectionStartOffsetInstantiator.get(myModels),
			null);
		theSelectionEndValue.set(theSelectionEndValueInstantiator == null ? null : theSelectionEndValueInstantiator.get(myModels), null);
		theSelectionEndOffset.set(theSelectionEndOffsetInstantiator == null ? null : theSelectionEndOffsetInstantiator.get(myModels), null);
	}

	@Override
	public StyledDocument<T> copy(ExElement parent) {
		StyledDocument<T> copy = (StyledDocument<T>) super.copy(parent);

		copy.theSelectionStartValue = SettableValue.build(theSelectionStartValue.getType()).build();
		copy.theSelectionStartOffset = SettableValue.build(theSelectionStartOffset.getType()).build();
		copy.theSelectionEndValue = SettableValue.build(theSelectionStartValue.getType()).build();
		copy.theSelectionEndOffset = SettableValue.build(theSelectionStartOffset.getType()).build();

		return copy;
	}

	/** Controls the style of text in a {@link StyledDocument} */
	public static class TextStyleElement extends QuickWithBackground.Abstract implements QuickTextElement {
		/** {@link TextStyleElement} definition */
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
			qonfigType = TEXT_STYLE,
			interpretation = Interpreted.class,
			instance = TextStyleElement.class)
		public static class Def extends QuickWithBackground.Def.Abstract<TextStyleElement>
		implements QuickTextElement.Def<TextStyleElement> {
			/**
			 * @param parent The parent element of the style element
			 * @param type The Qonfig type of the style element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
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
				super.doUpdate(session.asElement("styled")); // No super element
			}

			/**
			 * @param parent The parent element for the interpreted style element
			 * @return The interpreted style element
			 */
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** {@link TextStyleElement} interpretation */
		public static class Interpreted extends QuickWithBackground.Interpreted.Abstract<TextStyleElement>
		implements QuickTextElement.Interpreted<TextStyleElement> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the style element
			 */
			protected Interpreted(TextStyleElement.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public TextStyle.Interpreted getStyle() {
				return (TextStyle.Interpreted) super.getStyle();
			}

			/** @return The instantiated style element */
			public TextStyleElement create() {
				return new TextStyleElement(getIdentity());
			}
		}

		/** @param id The element If for this style element */
		protected TextStyleElement(Object id) {
			super(id);
		}

		@Override
		public StyledDocument<?> getParentElement() {
			return (StyledDocument<?>) super.getParentElement();
		}

		@Override
		public TextStyle getStyle() {
			return (TextStyle) super.getStyle();
		}
	}

	/** Quick style for a {@link StyledDocument} */
	public static class TextStyle extends QuickTextElement.QuickTextStyle.Abstract implements QuickWithBackground.QuickBackgroundStyle {
		/** {@link TextStyle} definition */
		public static class Def extends QuickTextElement.QuickTextStyle.Def.Abstract implements QuickBackgroundStyle.Def {
			private final QuickStyleAttributeDef theColor;
			private final QuickStyleAttributeDef theMouseCursor;

			/**
			 * @param parent The Quick parent style to inherit from
			 * @param styledElement The text style element in the document to style
			 * @param wrapped The compiled style to wrap
			 */
			public Def(QuickInstanceStyle.Def parent, TextStyleElement.Def styledElement, QuickCompiledStyle wrapped) {
				super(parent, styledElement, wrapped);
				QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(),
					QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, TEXT_STYLE);
				theColor = addApplicableAttribute(typeStyle.getAttribute("color"));
				theMouseCursor = addApplicableAttribute(typeStyle.getAttribute("mouse-cursor"));
			}

			@Override
			public QuickStyleAttributeDef getColor() {
				return theColor;
			}

			@Override
			public QuickStyleAttributeDef getMouseCursor() {
				return theMouseCursor;
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				return new Interpreted(this, (TextStyleElement.Interpreted) parentEl, (QuickInstanceStyle.Interpreted) parent,
					getWrapped().interpret(parentEl, parent, env));
			}
		}

		/** {@link TextStyle} interpretation */
		public static class Interpreted extends QuickTextElement.QuickTextStyle.Interpreted.Abstract
		implements QuickBackgroundStyle.Interpreted {
			private QuickElementStyleAttribute<Color> theColor;
			private QuickElementStyleAttribute<MouseCursor> theMouseCursor;

			/**
			 * @param definition The definition to interpret
			 * @param styledElement The text style element in the document to style
			 * @param parent The Quick parent style to inherit from
			 * @param wrapped The interpreted style to wrap
			 */
			public Interpreted(Def definition, TextStyleElement.Interpreted styledElement, QuickInstanceStyle.Interpreted parent,
				QuickInterpretedStyle wrapped) {
				super(definition, styledElement, parent, wrapped);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickElementStyleAttribute<Color> getColor() {
				return theColor;
			}

			@Override
			public QuickElementStyleAttribute<MouseCursor> getMouseCursor() {
				return theMouseCursor;
			}

			@Override
			public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
				throws ExpressoInterpretationException {
				super.update(env, styleSheet, appCache);
				QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
				theColor = get(cache.getAttribute(getDefinition().getColor(), Color.class, env));
				theMouseCursor = get(cache.getAttribute(getDefinition().getMouseCursor(), MouseCursor.class, env));
			}

			@Override
			public TextStyle create(QuickStyledElement styledElement) {
				return new TextStyle();
			}
		}

		private QuickStyleAttribute<Color> theColorAttr;
		private QuickStyleAttribute<MouseCursor> theMouseCursorAttr;

		private ObservableValue<Color> theColor;
		private ObservableValue<MouseCursor> theMouseCursor;

		@Override
		public TextStyleElement getStyledElement() {
			return (TextStyleElement) super.getStyledElement();
		}

		@Override
		public ObservableValue<Color> getColor() {
			return theColor;
		}

		@Override
		public ObservableValue<MouseCursor> getMouseCursor() {
			return theMouseCursor;
		}

		@Override
		public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement)
			throws ModelInstantiationException {
			super.update(interpreted, styledElement);

			TextStyle.Interpreted myInterpreted = (TextStyle.Interpreted) interpreted;

			theColorAttr = myInterpreted.getColor().getAttribute();
			theMouseCursorAttr = myInterpreted.getMouseCursor().getAttribute();

			theColor = getApplicableAttribute(theColorAttr);
			theMouseCursor = getApplicableAttribute(theMouseCursorAttr);
		}

		@Override
		public TextStyle copy(QuickStyledElement styledElement) {
			TextStyle copy = (TextStyle) super.copy(styledElement);

			copy.theColor = copy.getApplicableAttribute(theColorAttr);
			copy.theMouseCursor = copy.getApplicableAttribute(theMouseCursorAttr);

			return copy;
		}
	}
}
