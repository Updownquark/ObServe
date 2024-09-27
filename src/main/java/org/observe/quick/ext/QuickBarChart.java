package org.observe.quick.ext;

import java.awt.Color;

import org.observe.ObservableValue;
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
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.MultiValueRenderable;
import org.observe.quick.base.QuickBaseInterpretation;
import org.observe.quick.base.QuickSize;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A bar chart in Quick
 *
 * @param <T> The type of value representing each bar in the chart
 */
public class QuickBarChart<T> extends QuickWidget.Abstract implements MultiValueRenderable<T> {
	/** The name of this element */
	public static final String BAR_CHART = "bar-chart";

	/** {@link QuickBarChart} definition */
	@ExMultiElementTraceable({ //
		@ExElementTraceable(toolkit = QuickXInterpretation.X,
			qonfigType = BAR_CHART,
			interpretation = Interpreted.class,
			instance = QuickBarChart.class),
		@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = MULTI_VALUE_RENDERABLE,
		interpretation = Interpreted.class,
		instance = QuickBarChart.class) })
	public static class Def extends QuickWidget.Def.Abstract<QuickBarChart<?>> implements MultiValueRenderable.Def<QuickBarChart<?>> {
		private ModelComponentId theActiveValueVariable;
		private ModelComponentId theActiveIndexVariable;
		private ModelComponentId theSelectedVariable;

		private CompiledExpression theBars;
		private CompiledExpression theMax;
		private CompiledExpression theBarLength;
		private CompiledExpression theBarTitle;
		private boolean isVertical;
		private CompiledExpression thePadding;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The values for the chart's bars */
		@QonfigAttributeGetter(asType = BAR_CHART, value = "values")
		public CompiledExpression getValues() {
			return theBars;
		}

		/** @return The maximum value for the chart */
		@QonfigAttributeGetter(asType = BAR_CHART, value = "max")
		public CompiledExpression getMax() {
			return theMax;
		}

		/** @return The length for each bar */
		@QonfigAttributeGetter(asType = BAR_CHART, value = "bar-length")
		public CompiledExpression getBarLength() {
			return theBarLength;
		}

		/** @return The title for each bar */
		@QonfigAttributeGetter(asType = BAR_CHART, value = "title")
		public CompiledExpression getBarTitle() {
			return theBarTitle;
		}

		/** @return Whether the chart's bars should be arranged vertically or horizontally */
		@QonfigAttributeGetter(asType = BAR_CHART, value = "orientation")
		public boolean isVertical() {
			return isVertical;
		}

		/** @return The padding between the bars, if any */
		@QonfigAttributeGetter(asType = BAR_CHART, value = "padding")
		public CompiledExpression getPadding() {
			return thePadding;
		}

		@QonfigAttributeGetter(asType = MULTI_VALUE_RENDERABLE, value = "active-value-name")
		@Override
		public ModelComponentId getActiveValueVariable() {
			return theActiveValueVariable;
		}

		/** @return The ID of the model variable containing the index of the current bar */
		public ModelComponentId getActiveIndexVariable() {
			return theActiveIndexVariable;
		}

		/** @return The model ID of the variable containing the selected state of the active tree node */
		public ModelComponentId getSelectedVariable() {
			return theSelectedVariable;
		}

		@Override
		public BarChartStyle.Def getStyle() {
			return (BarChartStyle.Def) super.getStyle();
		}

		@Override
		protected BarChartStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
			return new BarChartStyle.Def(parentStyle, this, style);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String valueName = session.getAttributeText("active-value-name");
			theActiveValueVariable = elModels.getElementValueModelId(valueName);
			elModels.satisfyElementValueType(theActiveValueVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getType()));
			theActiveIndexVariable = elModels.getElementValueModelId("valueIndex");
			theSelectedVariable = elModels.getElementValueModelId("selected");

			theBars = getAttributeExpression("values", session);
			theMax = getAttributeExpression("max", session);
			theBarLength = getAttributeExpression("bar-length", session);
			theBarTitle = getAttributeExpression("title", session);
			isVertical = session.getAttributeText("orientation").equals("vertical");
			thePadding = getAttributeExpression("padding", session);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickBarChart} interpretation
	 *
	 * @param <T> The type of value representing each bar in the chart
	 */
	public static class Interpreted<T> extends QuickWidget.Interpreted.Abstract<QuickBarChart<T>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> theValues;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMax;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theBarLength;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theBarTitle;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> thePadding;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/**
		 * @return The type of value representing each bar in the chart
		 * @throws ExpressoInterpretationException If the bar type can't be evaluated
		 */
		public TypeToken<T> getType() throws ExpressoInterpretationException {
			if (theValues == null)
				theValues = interpret(getDefinition().getValues(), ModelTypes.Collection.<T> anyAsV());
			return (TypeToken<T>) theValues.getType().getType(0);
		}

		/** @return The values for the chart's bars */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> getValues() {
			return theValues;
		}

		/** @return The maximum value for the chart */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMax() {
			return theMax;
		}

		/** @return The length for each bar */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getBarLength() {
			return theBarLength;
		}

		/** @return The title for each bar */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getBarTitle() {
			return theBarTitle;
		}

		/** @return The padding between the bars, if any */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getPadding() {
			return thePadding;
		}

		@Override
		public BarChartStyle.Interpreted getStyle() {
			return (BarChartStyle.Interpreted) super.getStyle();
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			getType();

			theMax = interpret(getDefinition().getMax(), ModelTypes.Value.DOUBLE);
			theBarLength = interpret(getDefinition().getBarLength(), ModelTypes.Value.DOUBLE);
			thePadding = interpret(getDefinition().getPadding(), ModelTypes.Value.forType(QuickSize.class));
		}

		@Override
		public QuickBarChart<T> create() {
			return new QuickBarChart<>(getIdentity());
		}
	}

	/** Style for a {@link QuickBarChart} */
	public static class BarChartStyle extends QuickWidgetStyle.Default {
		/** {@link QuickBarChart} style definition */
		public static class Def extends QuickWidgetStyle.Def.Default {
			private QuickStyleAttributeDef theBarColor;
			private QuickStyleAttributeDef theOutlineColor;
			private QuickStyleAttributeDef theOutlineThickness;

			Def(QuickInstanceStyle.Def parent, QuickBarChart.Def styledElement, QuickCompiledStyle wrapped) {
				super(parent, styledElement, wrapped);
				QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickXInterpretation.NAME,
					QuickXInterpretation.VERSION, BAR_CHART);
				theBarColor = addApplicableAttribute(typeStyle.getAttribute("bar-color"));
				theOutlineColor = addApplicableAttribute(typeStyle.getAttribute("outline-color"));
				theOutlineThickness = addApplicableAttribute(typeStyle.getAttribute("outline-thickness"));
			}

			/** @return The attribute representing the fill color for a bar */
			public QuickStyleAttributeDef getBarColor() {
				return theBarColor;
			}

			/** @return The attribute representing the color of the outline for a bar */
			public QuickStyleAttributeDef getOutlineColor() {
				return theOutlineColor;
			}

			/** @return The attribute representing the thickness of the outline for a bar */
			public QuickStyleAttributeDef getOutlineThickness() {
				return theOutlineThickness;
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				return new Interpreted(this, (QuickBarChart.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
					getWrapped().interpret(parentEl, parent, env));
			}
		}

		/** {@link QuickBarChart} style interpretation */
		public static class Interpreted extends QuickWidgetStyle.Interpreted.Default {
			private QuickElementStyleAttribute<Color> theBarColor;
			private QuickElementStyleAttribute<Color> theOutlineColor;
			private QuickElementStyleAttribute<Integer> theOutlineThickness;

			/**
			 * @param definition The definition to interpret
			 * @param styledElement The bar chart to style
			 * @param parent The Quick style to inherit from
			 * @param wrapped The interpreted style to wrap
			 */
			protected Interpreted(BarChartStyle.Def definition, QuickBarChart.Interpreted<?> styledElement,
				QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
				super(definition, styledElement, parent, wrapped);
			}

			@Override
			public BarChartStyle.Def getDefinition() {
				return (BarChartStyle.Def) super.getDefinition();
			}

			/** @return The attribute representing the fill color for a bar */
			public QuickElementStyleAttribute<Color> getBarColor() {
				return theBarColor;
			}

			/** @return The attribute representing the color of the outline for a bar */
			public QuickElementStyleAttribute<Color> getOutlineColor() {
				return theOutlineColor;
			}

			/** @return The attribute representing the thickness of the outline for a bar */
			public QuickElementStyleAttribute<Integer> getOutlineThickness() {
				return theOutlineThickness;
			}

			@Override
			public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
				throws ExpressoInterpretationException {
				super.update(env, styleSheet, appCache);
				QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
				theBarColor = get(cache.getAttribute(getDefinition().getBarColor(), Color.class, env));
				theOutlineColor = get(cache.getAttribute(getDefinition().getOutlineColor(), Color.class, env));
				theOutlineThickness = get(cache.getAttribute(getDefinition().getOutlineThickness(), Integer.class, env));
			}

			@Override
			public BarChartStyle create(QuickStyledElement styledElement) {
				return new BarChartStyle();
			}
		}

		private QuickStyleAttribute<Color> theBarColorAttr;
		private QuickStyleAttribute<Color> theOutlineColorAttr;
		private QuickStyleAttribute<Integer> theOutlineThicknessAttr;
		private ObservableValue<Color> theBarColor;
		private ObservableValue<Color> theOutlineColor;
		private ObservableValue<Integer> theOutlineThickness;

		/** @return The attribute representing the fill color for a bar */
		public ObservableValue<Color> getBarColor() {
			return theBarColor;
		}

		/** @return The attribute representing the color of the outline for a bar */
		public ObservableValue<Color> getOutlineColor() {
			return theOutlineColor;
		}

		/** @return The attribute representing the thickness of the outline for a bar */
		public ObservableValue<Integer> getOutlineThickness() {
			return theOutlineThickness;
		}

		@Override
		public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement)
			throws ModelInstantiationException {
			super.update(interpreted, styledElement);

			BarChartStyle.Interpreted myInterpreted = (BarChartStyle.Interpreted) interpreted;

			theBarColorAttr = myInterpreted.getBarColor().getAttribute();
			theOutlineColorAttr = myInterpreted.getOutlineColor().getAttribute();
			theOutlineThicknessAttr = myInterpreted.getOutlineThickness().getAttribute();

			theBarColor = getApplicableAttribute(theBarColorAttr);
			theOutlineColor = getApplicableAttribute(theOutlineColorAttr);
			theOutlineThickness = getApplicableAttribute(theOutlineThicknessAttr);
		}

		@Override
		public BarChartStyle copy(QuickStyledElement styledElement) {
			BarChartStyle copy = (BarChartStyle) super.copy(styledElement);

			copy.theBarColor = copy.getApplicableAttribute(theBarColorAttr);
			copy.theOutlineColor = copy.getApplicableAttribute(theOutlineColorAttr);
			copy.theOutlineThickness = copy.getApplicableAttribute(theOutlineThicknessAttr);

			return copy;
		}
	}

	/**
	 * Model context for a {@link QuickBarChart}
	 *
	 * @param <T> The type of values in the chart
	 */
	public interface BarChartContext<T> extends MultiValueRenderContext<T> {
		/** @return The index of the active value for the chart */
		SettableValue<Integer> getActiveIndex();

		/**
		 * Default {@link BarChartContext} implementation
		 *
		 * @param <T> The type of values in the chart
		 */
		public class Default<T> extends MultiValueRenderContext.Default<T> implements BarChartContext<T> {
			private final SettableValue<Integer> theActiveIndex;

			/**
			 * @param activeValue The active value for the chart
			 * @param selected Whether the active value is selected
			 * @param index The index of the active value for the chart
			 */
			public Default(SettableValue<T> activeValue, SettableValue<Boolean> selected, SettableValue<Integer> index) {
				super(activeValue, selected);
				theActiveIndex = index;
			}

			/** Creates the context */
			public Default() {
				this(SettableValue.<T> build().withDescription("activeValue").build(), //
					SettableValue.<Boolean> build().withDescription("selected").withValue(false).build(), //
					SettableValue.<Integer> build().withDescription("valueIndex").withValue(0).build());
			}

			@Override
			public SettableValue<Integer> getActiveIndex() {
				return theActiveIndex;
			}
		}
	}

	private ModelComponentId theActiveValueVariable;
	private ModelComponentId theActiveIndexVariable;
	private ModelComponentId theSelectedVariable;

	private ModelValueInstantiator<ObservableCollection<T>> theValuesInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMaxInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theBarLengthInstantiator;
	private ModelValueInstantiator<SettableValue<String>> theBarTitleInstantiator;
	private ModelValueInstantiator<SettableValue<QuickSize>> thePaddingInstantiator;

	private boolean isVertical;
	private SettableValue<SettableValue<T>> theActiveBar;
	private SettableValue<SettableValue<Integer>> theActiveIndex;
	private SettableValue<ObservableCollection<T>> theValues;
	private SettableValue<SettableValue<Double>> theMax;
	private SettableValue<SettableValue<Double>> theBarLength;
	private SettableValue<SettableValue<String>> theBarTitle;
	private SettableValue<SettableValue<QuickSize>> thePadding;
	private SettableValue<SettableValue<Boolean>> isSelected;

	/** @param id The element ID for this widget */
	protected QuickBarChart(Object id) {
		super(id);
		theActiveBar = SettableValue.<SettableValue<T>> build().build();
		theActiveIndex = SettableValue.<SettableValue<Integer>> build().build();
		isSelected = SettableValue.<SettableValue<Boolean>> build().build();
		theValues = SettableValue.<ObservableCollection<T>> build().build();
		theMax = SettableValue.<SettableValue<Double>> build().build();
		theBarLength = SettableValue.<SettableValue<Double>> build().build();
		theBarTitle = SettableValue.<SettableValue<String>> build().build();
		thePadding = SettableValue.<SettableValue<QuickSize>> build().build();
	}

	/** @return The values for the chart's bars */
	public ObservableCollection<T> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	/** @return Whether the chart's bars should be arranged vertically or horizontally */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return The padding between the bars, if any */
	public SettableValue<QuickSize> getPadding() {
		return SettableValue.flatten(thePadding);
	}

	/** @return The maximum value for the chart */
	public SettableValue<Double> getMax() {
		return SettableValue.flatten(theMax, () -> 0.0);
	}

	/** @return The length for each bar */
	public SettableValue<Double> getBarLength() {
		return SettableValue.flatten(theBarLength, () -> 0.0);
	}

	/** @return Whether This chart specifies titles for each bar */
	public boolean hasBarTitles() {
		return theBarTitleInstantiator != null;
	}

	/** @return The title for each bar */
	public SettableValue<String> getBarTitle() {
		return SettableValue.flatten(theBarTitle);
	}

	@Override
	public BarChartStyle getStyle() {
		return (BarChartStyle) super.getStyle();
	}

	@Override
	public ModelComponentId getActiveValueVariable() {
		return theActiveValueVariable;
	}

	/** @return The ID of the model variable containing the index of the current bar */
	public ModelComponentId getActiveIndexVariable() {
		return theActiveIndexVariable;
	}

	@Override
	public ModelComponentId getSelectedVariable() {
		return theSelectedVariable;
	}

	@Override
	public void setContext(MultiValueRenderContext<T> ctx) {
		theActiveBar.set(ctx.getActiveValue(), null);
	}

	/** @param ctx The bar chart context for the environment */
	public void setContext(BarChartContext<T> ctx) {
		setContext((MultiValueRenderContext<T>) ctx);
		theActiveIndex.set(ctx.getActiveIndex(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;

		theActiveValueVariable = myInterpreted.getDefinition().getActiveValueVariable();
		theActiveIndexVariable = myInterpreted.getDefinition().getActiveIndexVariable();
		theSelectedVariable = myInterpreted.getDefinition().getSelectedVariable();

		isVertical = myInterpreted.getDefinition().isVertical();
		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theMaxInstantiator = myInterpreted.getMax().instantiate();
		theBarLengthInstantiator = myInterpreted.getBarLength().instantiate();
		theBarTitleInstantiator = myInterpreted.getBarTitle() == null ? null : myInterpreted.getBarTitle().instantiate();
		thePaddingInstantiator = myInterpreted.getPadding() == null ? null : myInterpreted.getPadding().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theValuesInstantiator.instantiate();
		if (thePaddingInstantiator != null)
			thePaddingInstantiator.instantiate();
		theMaxInstantiator.instantiate();
		theBarLengthInstantiator.instantiate();
		if (theBarTitleInstantiator != null)
			theBarTitleInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveValueVariable, myModels, SettableValue.flatten(theActiveBar));
		ExFlexibleElementModelAddOn.satisfyElementValue(theActiveIndexVariable, myModels, SettableValue.flatten(theActiveIndex));
		ExFlexibleElementModelAddOn.satisfyElementValue(theSelectedVariable, myModels, SettableValue.flatten(isSelected, () -> false));

		theValues.set(theValuesInstantiator.get(myModels), null);
		theMax.set(theMaxInstantiator.get(myModels), null);
		theBarLength.set(theBarLengthInstantiator.get(myModels), null);
		theBarTitle.set(theBarTitleInstantiator == null ? null : theBarTitleInstantiator.get(myModels), null);
		thePadding.set(thePaddingInstantiator == null ? null : thePaddingInstantiator.get(myModels), null);
	}

	@Override
	public QuickBarChart<T> copy(ExElement parent) {
		QuickBarChart<T> copy = (QuickBarChart<T>) super.copy(parent);

		copy.theActiveBar = SettableValue.<SettableValue<T>> build().build();
		copy.theActiveIndex = SettableValue.<SettableValue<Integer>> build().build();
		copy.isSelected = SettableValue.<SettableValue<Boolean>> build().build();
		copy.theValues = SettableValue.<ObservableCollection<T>> build().build();
		copy.theMax = SettableValue.<SettableValue<Double>> build().build();
		copy.theBarLength = SettableValue.<SettableValue<Double>> build().build();
		copy.theBarTitle = SettableValue.<SettableValue<String>> build().build();
		copy.thePadding = SettableValue.<SettableValue<QuickSize>> build().build();

		return copy;
	}
}
