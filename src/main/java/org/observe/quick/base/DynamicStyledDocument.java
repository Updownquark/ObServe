package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickTextWidget;
import org.observe.quick.QuickValueWidget.WidgetValueSupplier;
import org.observe.quick.QuickWithBackground;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/**
 * A styled document with a dynamic structure--each node having a dynamic collection of children
 *
 * @param <T> the value of each node in the document
 */
public class DynamicStyledDocument<T> extends StyledDocument<T> {
	/** The XML name of this element */
	public static final String DYNAMIC_STYLED_DOCUMENT = "dynamic-styled-document";

	/**
	 * {@link DynamicStyledDocument} definition
	 *
	 * @param <D> The sub-type of document to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = DYNAMIC_STYLED_DOCUMENT,
		interpretation = Interpreted.class,
		instance = DynamicStyledDocument.class)
	public static class Def<D extends DynamicStyledDocument<?>> extends StyledDocument.Def<D> {
		private CompiledExpression theRoot;
		private CompiledExpression theChildren;
		private CompiledExpression theFormat;
		private CompiledExpression thePostText;
		private TextStyleElement.Def theTextStyle;
		private ModelComponentId theNodeValue;

		/**
		 * @param parent The parent for this element
		 * @param type The Qonfig type of this element
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public boolean isTypeEditable() {
			return false; // Editing not implemented
		}

		/** @return The root of the document */
		@QonfigAttributeGetter("root")
		public CompiledExpression getRoot() {
			return theRoot;
		}

		/** @return The expression to generate children for the document */
		@QonfigAttributeGetter("children")
		public CompiledExpression getChildren() {
			return theChildren;
		}

		/** @return The format to represent values as text */
		@QonfigAttributeGetter("format")
		public CompiledExpression getFormat() {
			return theFormat;
		}

		/** @return The post-text for each node in the document--the text to appear after the node's children */
		@QonfigAttributeGetter("post-text")
		public CompiledExpression getPostText() {
			return thePostText;
		}

		/** @return The text style for the document */
		@QonfigChildGetter("text-style")
		public TextStyleElement.Def getTextStyle() {
			return theTextStyle;
		}

		/** @return The model ID for the variable where the current node will be available to expressions */
		public ModelComponentId getNodeValue() {
			return theNodeValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theRoot = getAttributeExpression("root", session);
			theChildren = getAttributeExpression("children", session);
			theFormat = getAttributeExpression("format", session);
			thePostText = getAttributeExpression("post-text", session);
			theTextStyle = syncChild(TextStyleElement.Def.class, theTextStyle, session, "text-style");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theNodeValue = elModels.getElementValueModelId("node");
			elModels.satisfyElementValueType(theNodeValue, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?, ?>) interp).getValueType()));
		}

		@Override
		public Interpreted<?, ? extends D> interpret(ExElement.Interpreted<?> parent) {
			// I cannot figure out how to pacify the generics here
			return (Interpreted<?, ? extends D>) new Interpreted<>((Def<DynamicStyledDocument<Object>>) this, parent);
		}
	}

	/**
	 * {@link DynamicStyledDocument} interpretation
	 *
	 * @param <T> The type of each node in the document
	 * @param <D> The sub-type of document to create
	 */
	public static class Interpreted<T, D extends DynamicStyledDocument<T>> extends StyledDocument.Interpreted<T, D> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theRoot;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theChildren;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theFormat;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> thePostText;
		private TextStyleElement.Interpreted theTextStyle;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent for this element
		 */
		protected Interpreted(Def<? super D> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			persistModelInstances(true);
		}

		@Override
		public Def<? super D> getDefinition() {
			return (Def<? super D>) super.getDefinition();
		}

		@Override
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			return (TypeToken<T>) getOrInitRoot().getType().getType(0);
		}

		/**
		 * Gets or initializes the root of the document
		 *
		 * @return The root of the document
		 * @throws ExpressoInterpretationException If the root could not be interpreted
		 */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitRoot() throws ExpressoInterpretationException {
			if (theRoot == null) {
				if (getDefinition().getRoot() != null)
					theRoot = interpret(getDefinition().getRoot(), ModelTypes.Value.<T> anyAsV());
				else
					theRoot = ((WidgetValueSupplier.Interpreted<T, ?>) getParentElement()).getValue();
			}
			return theRoot;
		}

		/** @return The root of the document */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getRoot() {
			return theRoot;
		}

		/** @return The expression to generate children for the document */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getChildren() {
			return theChildren;
		}

		/** @return The format to represent values as text */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getFormat() {
			return theFormat;
		}

		/** @return The post-text for each node in the document--the text to appear after the node's children */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getPostText() {
			return thePostText;
		}

		/** @return The text style for the document */
		public TextStyleElement.Interpreted getTextStyle() {
			return theTextStyle;
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			theRoot = null;
			super.doUpdate(env);

			getOrInitRoot(); // Initialize theRoot
			theChildren = interpret(getDefinition().getChildren(), ModelTypes.Collection.forType(getValueType()));
			if (getDefinition().getFormat() != null)
				theFormat = interpret(getDefinition().getFormat(),
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(getValueType())));
			else
				theFormat = QuickTextWidget.getDefaultFormat(getValueType(), false, reporting().getPosition());
			thePostText = interpret(getDefinition().getPostText(), ModelTypes.Value.STRING);
			theTextStyle = syncChild(getDefinition().getTextStyle(), theTextStyle, def -> def.interpret(this),
				(s, sEnv) -> s.updateElement(sEnv));
		}

		@Override
		public DynamicStyledDocument<T> create() {
			return new DynamicStyledDocument<>(getIdentity());
		}
	}

	/**
	 * Context for a {@link DynamicStyledDocument}, defining a node value
	 *
	 * @param <T> The type of the document value
	 */
	public interface StyledTextAreaContext<T> extends QuickWithBackground.BackgroundContext {
		/** @return The node value of the context */
		SettableValue<T> getNodeValue();

		/**
		 * Default {@link StyledTextAreaContext} implementation
		 *
		 * @param <T> The type of the document value
		 */
		public class Default<T> extends QuickWithBackground.BackgroundContext.Default implements StyledTextAreaContext<T> {
			private final SettableValue<T> theNodeValue;

			/**
			 * @param hovered The container for the hovered state of the node
			 * @param focused The container for the focused state of the node
			 * @param pressed The container for the pressed state of the node
			 * @param rightPressed The container for the right-pressed state of the node
			 * @param nodeValue The node value of the context
			 */
			public Default(SettableValue<Boolean> hovered, SettableValue<Boolean> focused, SettableValue<Boolean> pressed,
				SettableValue<Boolean> rightPressed, SettableValue<T> nodeValue) {
				super(hovered, focused, pressed, rightPressed);
				theNodeValue = nodeValue;
			}

			/** @param type The type of the document value */
			public Default(TypeToken<T> type) {
				theNodeValue = SettableValue.build(type).build();
			}

			@Override
			public SettableValue<T> getNodeValue() {
				return theNodeValue;
			}
		}
	}

	private ModelValueInstantiator<SettableValue<T>> theRootInstantiator;
	private ModelValueInstantiator<SettableValue<Format<T>>> theFormatInstantiator;

	private SettableValue<SettableValue<T>> theRoot;
	private TextStyleElement theTextStyle;
	private SettableValue<T> theNodeValue;
	private Format<T> theFormat;

	private ModelValueInstantiator<ObservableCollection<T>> theChildrenSynth;
	private ModelValueInstantiator<SettableValue<String>> thePostTextSynth;
	private ModelComponentId theNodeValueId;

	/** @param id The element identity of the widget */
	protected DynamicStyledDocument(Object id) {
		super(id);
	}

	/** @return The root of the document */
	public SettableValue<T> getRoot() {
		return SettableValue.flatten(theRoot);
	}

	/** @return Whether this document may append text to nodes after the node's children */
	public boolean hasPostText() {
		return thePostTextSynth != null;
	}

	/** @return The text style for the document */
	public TextStyleElement getTextStyle() {
		return theTextStyle;
	}

	/** @return The value of the current node (e.g. the one hovered) */
	public SettableValue<T> getNodeValue() {
		return theNodeValue;
	}

	/**
	 * @param ctx The context to generate the children for
	 * @return The child values for the node in the given context
	 * @throws ModelInstantiationException If the children could not be instantiated
	 */
	public ObservableCollection<? extends T> getChildren(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance modelCopy = getModels().createCopy(getUpdatingModels(), getUpdatingModels().getUntil())//
			.build();
		ExFlexibleElementModelAddOn.satisfyElementValue(theNodeValueId, modelCopy, ctx.getNodeValue());
		// After synthesizing and returning the children for the node, we can discard the model copy
		return theChildrenSynth.get(modelCopy);
	}

	/** @return The format to represent values as text */
	public Format<T> getFormat() {
		return theFormat;
	}

	/**
	 * @param ctx The context to generate the style for
	 * @return The text style for the node in the given context
	 * @throws ModelInstantiationException If the text style could not be instantiated
	 */
	public TextStyle getStyle(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		if (theTextStyle == null)
			return null;
		ModelSetInstance widgetModelCopy = getModels().createCopy(getUpdatingModels(), getUpdatingModels().getUntil())//
			.build();
		ExFlexibleElementModelAddOn.satisfyElementValue(theNodeValueId, widgetModelCopy, ctx.getNodeValue());
		ModelSetInstance styleElementModelCopy = getTextStyle().getUpdatingModels().copy()//
			.withAll(widgetModelCopy)//
			.build();

		TextStyle styleCopy = theTextStyle.getStyle().copy(theTextStyle);
		styleCopy.instantiate(styleElementModelCopy);
		return styleCopy;
	}

	/**
	 * @param ctx The context to generate the post-text for
	 * @return The post-text for the node in the given context
	 * @throws ModelInstantiationException If the post-text could not be instantiated
	 */
	public SettableValue<String> getPostText(StyledTextAreaContext<T> ctx) throws ModelInstantiationException {
		ModelSetInstance modelCopy = getModels().createCopy(getUpdatingModels(), getUpdatingModels().getUntil())//
			.build();
		ExFlexibleElementModelAddOn.satisfyElementValue(theNodeValueId, modelCopy, ctx.getNodeValue());
		// After synthesizing and returning the post text, we can discard the model copy
		return thePostTextSynth.get(modelCopy);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;
		theNodeValueId = myInterpreted.getDefinition().getNodeValue();
		TypeToken<T> valueType;
		try {
			valueType = myInterpreted.getValueType();
		} catch (ExpressoInterpretationException e) {
			throw new IllegalStateException("Not interpreted?", e);
		}
		if (theRoot == null || !valueType.equals(getValueType().getType(0))) {
			theRoot = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<T>> parameterized(valueType)).build();
			theNodeValue = SettableValue.build(valueType).build();
		}

		theRootInstantiator = myInterpreted.getRoot().instantiate();
		theFormatInstantiator = myInterpreted.getFormat().instantiate();
		if (myInterpreted.getTextStyle() == null)
			theTextStyle = null;
		else if (theTextStyle == null || theTextStyle.getIdentity() != myInterpreted.getTextStyle().getDefinition().getIdentity())
			theTextStyle = myInterpreted.getTextStyle().create();
		if (theTextStyle != null)
			theTextStyle.update(myInterpreted.getTextStyle(), this);
		theChildrenSynth = myInterpreted.getChildren().instantiate();
		thePostTextSynth = myInterpreted.getPostText() == null ? null : myInterpreted.getPostText().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theRootInstantiator.instantiate();
		if (theFormatInstantiator != null)
			theFormatInstantiator.instantiate();
		theChildrenSynth.instantiate();
		if (thePostTextSynth != null)
			thePostTextSynth.instantiate();

		if (theTextStyle != null)
			theTextStyle.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		ExFlexibleElementModelAddOn.satisfyElementValue(theNodeValueId, myModels, getNodeValue());
		theRoot.set(theRootInstantiator.get(myModels), null);
		theFormat = theFormatInstantiator.get(myModels).get();

		if (theTextStyle != null)
			theTextStyle.instantiate(myModels);
	}

	@Override
	public DynamicStyledDocument<T> copy(ExElement parent) {
		DynamicStyledDocument<T> copy = (DynamicStyledDocument<T>) super.copy(parent);

		copy.theRoot = SettableValue.build(theRoot.getType()).build();
		copy.theNodeValue = SettableValue.build(theNodeValue.getType()).build();

		return copy;
	}
}
