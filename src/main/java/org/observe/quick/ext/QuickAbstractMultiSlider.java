package org.observe.quick.ext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
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
import org.observe.quick.QuickWidget;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A slider with multiple thumbs and a great deal of control over rendering
 *
 * @param <T> The type of values to represent with the slider's handles
 */
public abstract class QuickAbstractMultiSlider<T> extends QuickWidget.Abstract implements MultiValueRenderable<T> {
	/** The XML name of this element */
	public static final String ABSTRACT_MULTI_SLIDER = "abstract-multi-slider";
	/** The XML name of the &lt;slider-handle-renderer> element */
	public static final String SLIDER_HANDLE_RENDERER = "slider-handle-renderer";
	/** The XML name of the &lt;slider-bg-renderer> element */
	public static final String SLIDER_BG_RENDERER = "slider-bg-renderer";

	/**
	 * Renders a handle for a {@link QuickAbstractMultiSlider}
	 *
	 * @param <T> The type of values the slider's handles represent
	 */
	public static class SliderHandleRenderer<T> extends QuickWithBackground.Abstract {
		/** {@link SliderHandleRenderer} definition */
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = SLIDER_HANDLE_RENDERER,
			interpretation = Interpreted.class,
			instance = SliderHandleRenderer.class)
		public static class Def extends QuickWithBackground.Def.Abstract<SliderHandleRenderer<?>> {
			private CompiledExpression theTooltip;

			/**
			 * @param parent The parent element of the renderer
			 * @param type The Qonfig type of the renderer
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return The tooltip to display for the handle */
			@QonfigAttributeGetter("tooltip")
			public CompiledExpression getTooltip() {
				return theTooltip;
			}

			@Override
			public SliderHandleStyle.Def getStyle() {
				return (SliderHandleStyle.Def) super.getStyle();
			}

			@Override
			public SliderHandleStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new SliderHandleStyle.Def(parentStyle, this, style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				theTooltip = getAttributeExpression("tooltip", session);
			}

			/**
			 * @param parent The parent element for the interpreted renderer
			 * @return The interpreted renderer
			 */
			public <T> Interpreted<T> interpret(QuickAbstractMultiSlider.Interpreted<T, ?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * {@link SliderHandleRenderer} interpretation
		 *
		 * @param <T> The type of values the slider's handles represent
		 */
		public static class Interpreted<T> extends QuickWithBackground.Interpreted.Abstract<SliderHandleRenderer<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for the renderer
			 */
			protected Interpreted(SliderHandleRenderer.Def definition, QuickAbstractMultiSlider.Interpreted<T, ?> parent) {
				super(definition, parent);
			}

			@Override
			public SliderHandleRenderer.Def getDefinition() {
				return (SliderHandleRenderer.Def) super.getDefinition();
			}

			@Override
			public QuickAbstractMultiSlider.Interpreted<T, ?> getParentElement() {
				return (QuickAbstractMultiSlider.Interpreted<T, ?>) super.getParentElement();
			}

			@Override
			public SliderHandleStyle.Interpreted getStyle() {
				return (SliderHandleStyle.Interpreted) super.getStyle();
			}

			TypeToken<T> getValueType() throws IllegalStateException {
				return (TypeToken<T>) getParentElement().getValues().getType().getType(0);
			}

			/** @return The tooltip to display for the handle */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip() {
				return theTooltip;
			}

			/**
			 * Initializes or updates this renderer
			 *
			 * @throws ExpressoInterpretationException If the renderer could not be interpreted
			 */
			public void updateRenderer() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theTooltip = interpret(getDefinition().getTooltip(), ModelTypes.Value.STRING);
			}

			/** @return The handle renderer */
			public SliderHandleRenderer<T> create() {
				return new SliderHandleRenderer<>(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<String>> theTooltipInstantiator;

		private SettableValue<SettableValue<T>> theHandleValue;
		private SettableValue<SettableValue<Integer>> theHandleIndex;
		private SettableValue<String> theTooltip;

		/** @param id The element ID for this renderer */
		protected SliderHandleRenderer(Object id) {
			super(id);
			theHandleValue = SettableValue.create();
			theHandleIndex = SettableValue.create();
		}

		@Override
		public QuickAbstractMultiSlider<T> getParentElement() {
			return (QuickAbstractMultiSlider<T>) super.getParentElement();
		}

		@Override
		public SliderHandleStyle getStyle() {
			return (SliderHandleStyle) super.getStyle();
		}

		/** @return The tooltip to display for the handle */
		public SettableValue<String> getTooltip() {
			return theTooltip;
		}

		/** @param context Model context for this renderer */
		public void setHandleContext(MultiSliderContext<T> context) {
			theHandleValue.set(context.getHandleValue(), null);
			theHandleIndex.set(context.getHandleIndex(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
			theTooltipInstantiator = myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theTooltipInstantiator != null)
				theTooltipInstantiator.instantiate();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels.copy().build());

			ExFlexibleElementModelAddOn.satisfyElementValue(getParentElement().getActiveValueVariable(), myModels,
				SettableValue.flatten(theHandleValue));
			ExFlexibleElementModelAddOn.satisfyElementValue(getParentElement().getActiveIndexVariable(), myModels,
				SettableValue.flatten(theHandleIndex));
			theTooltip = theTooltipInstantiator == null ? null : theTooltipInstantiator.get(myModels);
			return myModels;
		}

		@Override
		public SliderHandleRenderer<T> copy(ExElement parent) {
			SliderHandleRenderer<T> copy = (SliderHandleRenderer<T>) super.copy(parent);

			copy.theHandleValue = SettableValue.create();
			copy.theHandleIndex = SettableValue.create();

			return copy;
		}

		/** Style for a {@link SliderHandleRenderer} */
		public static class SliderHandleStyle extends QuickBackgroundStyle.Default {
			/** {@link SliderHandleRenderer} style definition */
			public static class Def extends QuickBackgroundStyle.Def.Default {
				private QuickStyleAttributeDef theLineColor;
				private QuickStyleAttributeDef theLineThickness;

				Def(QuickInstanceStyle.Def parent, SliderHandleRenderer.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickXInterpretation.NAME, QuickXInterpretation.VERSION, SLIDER_HANDLE_RENDERER);
					theLineColor = addApplicableAttribute(typeStyle.getAttribute("line-color"));
					theLineThickness = addApplicableAttribute(typeStyle.getAttribute("line-thickness"));
				}

				/** @return The attribute representing the color of the slider's line */
				public QuickStyleAttributeDef getLineColor() {
					return theLineColor;
				}

				/** @return The attribute representing the thickness of the slider's line */
				public QuickStyleAttributeDef getLineThickness() {
					return theLineThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new Interpreted(this, (SliderHandleRenderer.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent));
				}
			}

			/** {@link SliderHandleRenderer} style interpretation */
			public static class Interpreted extends QuickBackgroundStyle.Interpreted.Default {
				private QuickElementStyleAttribute<Color> theLineColor;
				private QuickElementStyleAttribute<Integer> theLineThickness;

				/**
				 * @param definition The definition to interpret
				 * @param styledElement The handle renderer to style
				 * @param parent The Quick style to inherit from
				 * @param wrapped The interpreted style to wrap
				 */
				protected Interpreted(SliderHandleStyle.Def definition, SliderHandleRenderer.Interpreted<?> styledElement,
					QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public SliderHandleStyle.Def getDefinition() {
					return (SliderHandleStyle.Def) super.getDefinition();
				}

				/** @return The attribute representing the color of the slider's line */
				public QuickElementStyleAttribute<Color> getLineColor() {
					return theLineColor;
				}

				/** @return The attribute representing the thickness of the slider's line */
				public QuickElementStyleAttribute<Integer> getLineThickness() {
					return theLineThickness;
				}

				@Override
				public void update(ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theLineColor = get(cache.getAttribute(getDefinition().getLineColor(), Color.class, env));
					theLineThickness = get(cache.getAttribute(getDefinition().getLineThickness(), Integer.class, env));
				}

				@Override
				public SliderHandleStyle create(QuickStyled styled) {
					return new SliderHandleStyle();
				}
			}

			private QuickStyleAttribute<Color> theLineColorAttr;
			private QuickStyleAttribute<Integer> theLineThicknessAttr;
			private ObservableValue<Color> theLineColor;
			private ObservableValue<Integer> theLineThickness;

			/** @return The color for the slider's line */
			public ObservableValue<Color> getLineColor() {
				return theLineColor;
			}

			/** @return The thickness for the slider's line */
			public ObservableValue<Integer> getLineThickness() {
				return theLineThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled)
				throws ModelInstantiationException {
				super.update(interpreted, styled);

				SliderHandleStyle.Interpreted myInterpreted = (SliderHandleStyle.Interpreted) interpreted;

				theLineColorAttr = myInterpreted.getLineColor().getAttribute();
				theLineThicknessAttr = myInterpreted.getLineThickness().getAttribute();

				theLineColor = getApplicableAttribute(theLineColorAttr);
				theLineThickness = getApplicableAttribute(theLineThicknessAttr);
			}

			@Override
			public SliderHandleStyle copy(QuickStyled styled) {
				SliderHandleStyle copy = (SliderHandleStyle) super.copy(styled);

				copy.theLineColor = copy.getApplicableAttribute(theLineColorAttr);
				copy.theLineThickness = copy.getApplicableAttribute(theLineThicknessAttr);

				return copy;
			}
		}
	}

	/** Renders a portion of the line for a {@link QuickAbstractMultiSlider} */
	public static class SliderBgRenderer extends QuickWithBackground.Abstract {
		/** {@link SliderBgRenderer} definition */
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = SLIDER_BG_RENDERER,
			interpretation = Interpreted.class,
			instance = SliderBgRenderer.class)
		public static class Def extends QuickWithBackground.Def.Abstract<SliderBgRenderer> {
			private CompiledExpression theMaxValue;

			/**
			 * @param parent The parent element of the renderer
			 * @param type The Qonfig type of the renderer
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/**
			 * @return The maximum value of this renderer's domain. This renderer will be used to render the portion of the slider below
			 *         this value and above those of any other renderers on the slider
			 */
			@QonfigAttributeGetter("max-value")
			public CompiledExpression getMaxValue() {
				return theMaxValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theMaxValue = getAttributeExpression("max-value", session);
			}

			/**
			 * @param parent The parent element for the interpreted renderer
			 * @return The interpreted renderer
			 */
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** {@link SliderBgRenderer} interpretation */
		public static class Interpreted extends QuickWithBackground.Interpreted.Abstract<SliderBgRenderer> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMaxValue;

			/**
			 * @param definition The definition to renderer
			 * @param parent The parent element for the renderer
			 */
			protected Interpreted(SliderBgRenderer.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SliderBgRenderer.Def getDefinition() {
				return (SliderBgRenderer.Def) super.getDefinition();
			}

			/**
			 * @return The maximum value of this renderer's domain. This renderer will be used to render the portion of the slider below
			 *         this value and above those of any other renderers on the slider
			 */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMaxValue() {
				return theMaxValue;
			}

			/**
			 * Initializes or updates this renderer
			 *
			 * @throws ExpressoInterpretationException If the renderer could not be interpreted
			 */
			public void updateRenderer() throws ExpressoInterpretationException {
				update();
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();

				theMaxValue = interpret(getDefinition().getMaxValue(), ModelTypes.Value.DOUBLE);
			}

			/** @return The background renderer */
			public SliderBgRenderer create() {
				return new SliderBgRenderer(getIdentity());
			}
		}

		private ModelValueInstantiator<SettableValue<Double>> theMaxValueInstantiator;

		private SettableValue<Double> theMaxValue;

		/** @param id The element ID for this renderer */
		protected SliderBgRenderer(Object id) {
			super(id);
		}

		/**
		 * @return The maximum value of this renderer's domain. This renderer will be used to render the portion of the slider below this
		 *         value and above those of any other renderers on the slider
		 */
		public SettableValue<Double> getMaxValue() {
			return theMaxValue;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			Interpreted myInterpreted = (Interpreted) interpreted;
			theMaxValueInstantiator = myInterpreted.getMaxValue() == null ? null : myInterpreted.getMaxValue().instantiate();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();

			if (theMaxValueInstantiator != null)
				theMaxValueInstantiator.instantiate();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			theMaxValue = theMaxValueInstantiator == null ? null : theMaxValueInstantiator.get(myModels);
			return myModels;
		}

		@Override
		public SliderBgRenderer copy(ExElement parent) {
			return (SliderBgRenderer) super.copy(parent);
		}
	}

	/**
	 * {@link QuickAbstractMultiSlider} definition
	 *
	 * @param <S> The sub-type of slider created by this definition
	 */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = ABSTRACT_MULTI_SLIDER,
		interpretation = Interpreted.class,
		instance = QuickAbstractMultiSlider.class)
	public static abstract class Def<S extends QuickAbstractMultiSlider<?>> extends QuickWidget.Def.Abstract<S>
	implements MultiValueRenderable.Def<S> {
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theActiveIndexVariable;
		private ModelComponentId theSelectedVariable;
		private CompiledExpression theValues;
		private boolean isVertical;
		private boolean isOrderEnforced;
		private CompiledExpression theMin;
		private CompiledExpression theMax;
		private SliderHandleRenderer.Def theHandleRenderer;
		private final List<SliderBgRenderer.Def> theBgRenderers;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theBgRenderers = new ArrayList<>();
		}

		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		/** @return The variable in which the active index will be stored */
		@QonfigAttributeGetter("active-index-as")
		public ModelComponentId getActiveIndexVariable() {
			return theActiveIndexVariable;
		}

		/** @return The selection variable, which is not used (always false) for sliders */
		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		/** @return The values for the slider's handles */
		@QonfigAttributeGetter("values")
		public CompiledExpression getValues() {
			return theValues;
		}

		/** @return Whether the slider should be vertical or horizontal */
		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		/** @return Whether to prevent the user from re-ordering values by dragging handles */
		@QonfigAttributeGetter("enforce-order")
		public boolean isOrderEnforced() {
			return isOrderEnforced;
		}

		/** @return The value for the leading edge of the slider */
		@QonfigAttributeGetter("min")
		public CompiledExpression getMin() {
			return theMin;
		}

		/** @return The value for the trailing edge of the slider */
		@QonfigAttributeGetter("max")
		public CompiledExpression getMax() {
			return theMax;
		}

		/** @return The renderer to control the appearance of the slider's handles */
		@QonfigChildGetter("handle-renderer")
		public SliderHandleRenderer.Def getHandleRenderer() {
			return theHandleRenderer;
		}

		/** @return Renderers to control the appearance of the slider's line */
		@QonfigChildGetter("bg-renderer")
		public List<SliderBgRenderer.Def> getBgRenderers() {
			return Collections.unmodifiableList(theBgRenderers);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String activeValueName = session.getAttributeText(ACTIVE_VALUE_NAME);
			String activeIndexName = session.getAttributeText("active-index-as");
			theActiveValueVariable = elModels.getElementValueModelId(activeValueName);
			elModels.satisfyElementSingleValueType(theActiveValueVariable, ModelTypes.Value,
				interp -> ((Interpreted<?, ?>) interp).getValueType());
			theActiveIndexVariable = elModels.getElementValueModelId(activeIndexName);
			theSelectedVariable = elModels.getElementValueModelId("selected");

			theValues = getAttributeExpression("values", session);
			isVertical = session.getAttributeText("orientation").equals("vertical");
			isOrderEnforced = session.getAttribute("enforce-order", boolean.class);
			theMin = getAttributeExpression("min", session);
			theMax = getAttributeExpression("max", session);
			theHandleRenderer = syncChild(SliderHandleRenderer.Def.class, theHandleRenderer, session, "handle-renderer");
			syncChildren(SliderBgRenderer.Def.class, theBgRenderers, session.forChildren("bg-renderer"));
		}

		@Override
		public abstract Interpreted<?, ? extends S> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * {@link QuickAbstractMultiSlider} interpretation
	 *
	 * @param <T> The type of values to represent with the slider's handles
	 * @param <S> The sub-type of slider created by this interpretation
	 */
	public static abstract class Interpreted<T, S extends QuickAbstractMultiSlider<T>> extends QuickWidget.Interpreted.Abstract<S> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMin;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMax;
		private SliderHandleRenderer.Interpreted<T> theHandleRenderer;
		private final List<SliderBgRenderer.Interpreted> theBgRenderers;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super S> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theBgRenderers = new ArrayList<>();
		}

		@Override
		public Def<? super S> getDefinition() {
			return (Def<? super S>) super.getDefinition();
		}

		/** @return The values for the slider's handles */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		/** @return The value for the leading edge of the slider */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMin() {
			return theMin;
		}

		/** @return The value for the trailing edge of the slider */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMax() {
			return theMax;
		}

		/** @return The renderer to control the appearance of the slider's handles */
		public SliderHandleRenderer.Interpreted<T> getHandleRenderer() {
			return theHandleRenderer;
		}

		/** @return Renderers to control the appearance of the slider's line */
		public List<SliderBgRenderer.Interpreted> getBgRenderers() {
			return Collections.unmodifiableList(theBgRenderers);
		}

		/**
		 * @return The type of values rendered by this slider's handles
		 * @throws ExpressoInterpretationException If the values were not previously intepreted and interpretation fails
		 */
		public TypeToken<T> getValueType() throws ExpressoInterpretationException {
			if (theValues == null)
				theValues = interpretValues(getDefinition().getValues());
			return (TypeToken<T>) theValues.getType().getType(0);
		}

		/**
		 * @param values The compiled expression to interpret the values from
		 * @return The interpreted values for this slider's handles
		 * @throws ExpressoInterpretationException If the expression could not be interpreted
		 */
		protected abstract InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> interpretValues(
			CompiledExpression values) throws ExpressoInterpretationException;

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			theValues = null;
			super.doUpdate();
			getValueType();

			theMin = interpret(getDefinition().getMin(), ModelTypes.Value.DOUBLE);
			theMax = interpret(getDefinition().getMax(), ModelTypes.Value.DOUBLE);

			theHandleRenderer = syncChild(getDefinition().getHandleRenderer(), theHandleRenderer, def -> def.interpret(this),
				r -> r.updateElement());

			syncChildren(getDefinition().getBgRenderers(), theBgRenderers, def -> def.interpret(this),
				SliderBgRenderer.Interpreted::updateRenderer);
		}

		/**
		 * @param numberType The type of the number specified for a slider value
		 * @param tx The transformation precursor
		 * @return A transformation to allow the source value to be used as a double in a slider
		 * @throws ExpressoInterpretationException If the transformation could not be performed on the given number type
		 */
		protected Transformation.ReversibleTransformation<Number, Double> reverseSliderValue(Class<?> numberType,
			Transformation.ReversibleTransformationPrecursor<Number, Double, ?> tx) throws ExpressoInterpretationException {
			Function<Double, Number> reverse;
			boolean inexact;
			if (numberType == double.class) {
				reverse = Double::valueOf;
				inexact = false;
			} else {
				inexact = true;
				if (numberType == float.class) {
					reverse = d -> Float.valueOf(d.floatValue());
				} else if (numberType == long.class) {
					reverse = d -> Long.valueOf(Math.round(d));
				} else if (numberType == int.class) {
					reverse = d -> Integer.valueOf(Math.round(d.floatValue()));
				} else if (numberType == short.class) {
					reverse = d -> {
						int i = Math.round(d.floatValue());
						if (i > Short.MAX_VALUE)
							return Short.MAX_VALUE;
						else if (i < Short.MIN_VALUE)
							return Short.MIN_VALUE;
						else
							return Short.valueOf((short) i);
					};
				} else if (numberType == byte.class) {
					reverse = d -> {
						int i = Math.round(d.floatValue());
						if (i > Short.MAX_VALUE)
							return Short.MAX_VALUE;
						else if (i < Short.MIN_VALUE)
							return Short.MIN_VALUE;
						else
							return Short.valueOf((short) i);
					};
				} else
					throw new ExpressoInterpretationException("Cannot create a multi-slider for number type " + numberType.getName(),
						getDefinition().getElement().getPositionInFile(), 0);
			}
			return tx//
				.cache(false)//
				.map(Number::doubleValue)//
				.replaceSource(reverse, rev -> rev.allowInexactReverse(inexact));
		}

		@Override
		public abstract S create();
	}

	/**
	 * Model context for a multi-slider
	 *
	 * @param <T> The type of values the slider's handles represent
	 */
	public interface MultiSliderContext<T> {
		/** @return The value of the handle being rendered */
		SettableValue<T> getHandleValue();

		/** @return The index of the handle being rendered */
		SettableValue<Integer> getHandleIndex();

		/**
		 * Default {@link MultiSliderContext} implementation
		 *
		 * @param <T> The type of values the slider's handles represent
		 */
		public class Default<T> implements MultiSliderContext<T> {
			private final SettableValue<T> theHandleValue;
			private final SettableValue<Integer> theHandleIndex;

			/**
			 * @param handleValue The value of the handle being rendered
			 * @param handleIndex The indexof the handle being rendered
			 */
			public Default(SettableValue<T> handleValue, SettableValue<Integer> handleIndex) {
				theHandleValue = handleValue;
				theHandleIndex = handleIndex;
			}

			/** Creates default context */
			public Default() {
				this(SettableValue.<T> build().build(), //
					SettableValue.<Integer> build().withValue(0).build());
			}

			@Override
			public SettableValue<T> getHandleValue() {
				return theHandleValue;
			}

			@Override
			public SettableValue<Integer> getHandleIndex() {
				return theHandleIndex;
			}
		}
	}

	private ModelComponentId theActiveValueVariable;
	private ModelComponentId theActiveIndexVariable;
	private ModelComponentId theSelectedVariable;
	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMinInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMaxInstantiator;

	private boolean isVertical;
	private boolean isOrderEnforced;
	private SettableValue<ObservableCollection<T>> theValues;
	private SettableValue<SettableValue<Double>> theMin;
	private SettableValue<SettableValue<Double>> theMax;
	private SliderHandleRenderer<T> theHandleRenderer;
	private List<SliderBgRenderer> theBgRenderers;

	private SettableValue<SettableValue<T>> theActiveValue;
	private SettableValue<SettableValue<Integer>> theActiveValueIndex;

	/** @param id The element ID for this widget */
	protected QuickAbstractMultiSlider(Object id) {
		super(id);
		theValues = SettableValue.create();
		theMin = SettableValue.create();
		theMax = SettableValue.create();
		theBgRenderers = new ArrayList<>();

		theActiveValue = SettableValue.create();
		theActiveValueIndex = SettableValue.create();
	}

	/** @return The values for the slider's handles */
	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	/** @return The variable to publish the index of the currently active handle value to */
	public ModelComponentId getActiveIndexVariable() {
		return theActiveIndexVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return theSelectedVariable;
	}

	@Override
	public SettableValue<T> getActiveValue() {
		return SettableValue.flatten(theActiveValue);
	}

	@Override
	public SettableValue<Boolean> isSelected() {
		return SettableValue.of(false, "Not settable");
	}

	/** @return Whether the slider should be vertical or horizontal */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return Whether to prevent the user from re-ordering values by dragging handles */
	public boolean isOrderEnforced() {
		return isOrderEnforced;
	}

	/** @return The value for the leading edge of the slider */
	public SettableValue<Double> getMin() {
		return SettableValue.flatten(theMin, () -> 0.0);
	}

	/** @return The value for the trailing edge of the slider */
	public SettableValue<Double> getMax() {
		return SettableValue.flatten(theMax, () -> 0.0);
	}

	/** @return The renderer to control the appearance of the slider's handles */
	public SliderHandleRenderer<T> getHandleRenderer() {
		return theHandleRenderer;
	}

	/** @return Renderers to control the appearance of the slider's line */
	public List<SliderBgRenderer> getBgRenderers() {
		return Collections.unmodifiableList(theBgRenderers);
	}

	/** @param context Model context for this slider */
	public void setSliderContext(MultiSliderContext<T> context) {
		theActiveValue.set(context.getHandleValue(), null);
		theActiveValueIndex.set(context.getHandleIndex(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T, ?> myInterpreted = (Interpreted<T, ?>) interpreted;

		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
		theActiveIndexVariable = myInterpreted.getDefinition().getActiveIndexVariable();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();
		isVertical = myInterpreted.getDefinition().isVertical();
		isOrderEnforced = myInterpreted.getDefinition().isOrderEnforced();
		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theMinInstantiator = myInterpreted.getMin().instantiate();
		theMaxInstantiator = myInterpreted.getMax().instantiate();

		if (theHandleRenderer != null && (myInterpreted.getHandleRenderer() == null
			|| theHandleRenderer.getIdentity() != myInterpreted.getHandleRenderer().getIdentity())) {
			theHandleRenderer.destroy();
			theHandleRenderer = null;
		}
		if (theHandleRenderer == null && myInterpreted.getHandleRenderer() != null)
			theHandleRenderer = myInterpreted.getHandleRenderer().create();
		if (theHandleRenderer != null)
			theHandleRenderer.update(myInterpreted.getHandleRenderer(), this);

		CollectionUtils
		.synchronize(theBgRenderers, myInterpreted.getBgRenderers(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.<ModelInstantiationException> simpleX(interp -> interp.create())//
		.onLeft(el -> el.getLeftValue().destroy())//
		.onRightX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), this))//
		.rightOrder()//
		.adjust();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theValuesInstantiator.instantiate();
		theMinInstantiator.instantiate();
		theMaxInstantiator.instantiate();
		if (theHandleRenderer != null)
			theHandleRenderer.instantiated();
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			bgRenderer.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, getActiveValue());
		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveIndexVariable, myModels,
			SettableValue.flatten(theActiveValueIndex, () -> 0));
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, isSelected());
		theValues.set(theValuesInstantiator.get(myModels), null);
		SettableValue<Double> min = theMinInstantiator.get(myModels);
		SettableValue<Double> max = theMaxInstantiator.get(myModels);
		// Don't cross the ranges
		if (max.get() >= getMin().get()) {
			theMax.set(max, null);
			theMin.set(min, null);
		} else {
			theMin.set(min, null);
			theMax.set(max, null);
		}
		if (theHandleRenderer != null)
			theHandleRenderer.instantiate(myModels);
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			bgRenderer.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickAbstractMultiSlider<T> copy(ExElement parent) {
		QuickAbstractMultiSlider<T> copy = (QuickAbstractMultiSlider<T>) super.copy(parent);

		copy.theValues = SettableValue.create();
		copy.theMin = SettableValue.create();
		copy.theMax = SettableValue.create();
		if (theHandleRenderer != null)
			copy.theHandleRenderer = theHandleRenderer.copy(copy);
		copy.theBgRenderers = new ArrayList<>();
		for (SliderBgRenderer bgRenderer : theBgRenderers)
			copy.theBgRenderers.add(bgRenderer.copy(copy));

		copy.theActiveValue = SettableValue.create();
		copy.theActiveValueIndex = SettableValue.create();

		return copy;
	}
}
