package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.observe.util.swing.MultiRangeSlider;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickMultiSlider extends QuickWidget.Abstract {
	public static final String MULTI_SLIDER = "multi-slider";

	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = MULTI_SLIDER,
		interpretation = Interpreted.class,
		instance = QuickMultiSlider.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickMultiSlider> {
		private CompiledExpression theValues;
		private boolean isVertical;
		private boolean isOrderEnforced;
		private CompiledExpression theMin;
		private CompiledExpression theMax;
		private CompiledExpression theBgRenderer;
		private CompiledExpression theValueRenderer;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("values")
		public CompiledExpression getValues() {
			return theValues;
		}

		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		@QonfigAttributeGetter("enforce-order")
		public boolean isOrderEnforced() {
			return isOrderEnforced;
		}

		@QonfigAttributeGetter("min")
		public CompiledExpression getMin() {
			return theMin;
		}

		@QonfigAttributeGetter("max")
		public CompiledExpression getMax() {
			return theMax;
		}

		@QonfigAttributeGetter("bg-renderer")
		public CompiledExpression getBGRenderer() {
			return theBgRenderer;
		}

		@QonfigAttributeGetter("value-renderer")
		public CompiledExpression getValueRenderer() {
			return theValueRenderer;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theValues = session.getAttributeExpression("values");
			isVertical = session.getAttributeText("orientation").equals("vertical");
			isOrderEnforced = session.getAttribute("enforce-order", boolean.class);
			theMin = session.getAttributeExpression("min");
			theMax = session.getAttributeExpression("max");
			theBgRenderer = session.getAttributeExpression("bg-renderer");
			theValueRenderer = session.getAttributeExpression("value-renderer");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickMultiSlider> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Double>> theValues;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMin;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMax;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.MRSliderRenderer>> theBgRenderer;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.RangeRenderer>> theValueRenderer;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Double>> getValues() {
			return theValues;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMin() {
			return theMin;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMax() {
			return theMax;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.MRSliderRenderer>> getBGRenderer() {
			return theBgRenderer;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<MultiRangeSlider.RangeRenderer>> getValueRenderer() {
			return theValueRenderer;
		}

		@Override
		public TypeToken<? extends QuickMultiSlider> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().of(QuickMultiSlider.class);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theValues = getDefinition().getValues().interpret(ModelTypes.Collection.forType(double.class), env);
			theMin = getDefinition().getMin().interpret(ModelTypes.Value.DOUBLE, env);
			theMax = getDefinition().getMax().interpret(ModelTypes.Value.DOUBLE, env);
			theBgRenderer = getDefinition().getBGRenderer() == null ? null
				: getDefinition().getBGRenderer().interpret(ModelTypes.Value.forType(MultiRangeSlider.MRSliderRenderer.class), env);
			theValueRenderer = getDefinition().getValueRenderer() == null ? null
				: getDefinition().getValueRenderer().interpret(ModelTypes.Value.forType(MultiRangeSlider.RangeRenderer.class), env);
		}

		@Override
		public QuickMultiSlider create() {
			return new QuickMultiSlider(getIdentity());
		}
	}

	private ModelValueInstantiator<ObservableCollection<Double>> theValuesInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMinInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMaxInstantiator;
	private ModelValueInstantiator<SettableValue<MultiRangeSlider.MRSliderRenderer>> theBgRendererInstantiator;
	private ModelValueInstantiator<SettableValue<MultiRangeSlider.RangeRenderer>> theValueRendererInstantiator;

	private boolean isVertical;
	private boolean isOrderEnforced;
	private SettableValue<ObservableCollection<Double>> theValues;
	private SettableValue<SettableValue<Double>> theMin;
	private SettableValue<SettableValue<Double>> theMax;
	private SettableValue<SettableValue<MultiRangeSlider.MRSliderRenderer>> theBgRenderer;
	private SettableValue<SettableValue<MultiRangeSlider.RangeRenderer>> theValueRenderer;

	QuickMultiSlider(Object id) {
		super(id);
		theValues = SettableValue
			.build(TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<Double>> parameterized(double.class)).build();
		theMin = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class))
			.build();
		theMax = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class))
			.build();
		theBgRenderer = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MultiRangeSlider.MRSliderRenderer>> parameterized(
				MultiRangeSlider.MRSliderRenderer.class))
			.build();
		theValueRenderer = SettableValue
			.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MultiRangeSlider.RangeRenderer>> parameterized(
				MultiRangeSlider.RangeRenderer.class))
			.build();
	}

	public ObservableCollection<Double> getValues() {
		return ObservableCollection.flattenValue(theValues);
	}

	public boolean isVertical() {
		return isVertical;
	}

	public boolean isOrderEnforced() {
		return isOrderEnforced;
	}

	public SettableValue<Double> getMin() {
		return SettableValue.flatten(theMin, () -> 0.0);
	}

	public SettableValue<Double> getMax() {
		return SettableValue.flatten(theMax, () -> 0.0);
	}

	public SettableValue<MultiRangeSlider.MRSliderRenderer> getBGRenderer() {
		return SettableValue.flatten(theBgRenderer);
	}

	public SettableValue<MultiRangeSlider.RangeRenderer> getValueRenderer() {
		return SettableValue.flatten(theValueRenderer);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		isOrderEnforced = myInterpreted.getDefinition().isOrderEnforced();
		theValuesInstantiator = myInterpreted.getValues().instantiate();
		theMinInstantiator = myInterpreted.getMin().instantiate();
		theMaxInstantiator = myInterpreted.getMax().instantiate();
		theBgRendererInstantiator = myInterpreted.getBGRenderer() == null ? null : myInterpreted.getBGRenderer().instantiate();
		theValueRendererInstantiator = myInterpreted.getValueRenderer() == null ? null : myInterpreted.getValueRenderer().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		theValuesInstantiator.instantiate();
		theMinInstantiator.instantiate();
		theMaxInstantiator.instantiate();
		if (theBgRendererInstantiator != null)
			theBgRendererInstantiator.instantiate();
		if (theValueRendererInstantiator != null)
			theValueRendererInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

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
		theBgRenderer.set(theBgRendererInstantiator == null ? null : theBgRendererInstantiator.get(myModels), null);
		theValueRenderer.set(theValueRendererInstantiator == null ? null : theValueRendererInstantiator.get(myModels), null);
	}

	@Override
	public QuickMultiSlider copy(ExElement parent) {
		QuickMultiSlider slider = (QuickMultiSlider) super.copy(parent);

		slider.theValues = SettableValue.build(theValues.getType()).build();
		slider.theMin = SettableValue.build(theMin.getType()).build();
		slider.theMax = SettableValue.build(theMax.getType()).build();
		slider.theBgRenderer = SettableValue.build(theBgRenderer.getType()).build();
		slider.theValueRenderer = SettableValue.build(theValueRenderer.getType()).build();

		return slider;
	}
}
