package org.observe.quick.ext;

import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
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
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * A slider supporting multiple ranges
 *
 * @param <T> The type of value to represent with each handle
 */
public class QuickMultiRangeSlider<T> extends QuickAbstractMultiSlider<T> {
	/** The XML name of this element */
	public static final String MULTI_RANGE_SLIDER = "multi-range-slider";

	/** {@link QuickMultiRangeSlider} definition */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = MULTI_RANGE_SLIDER,
		interpretation = Interpreted.class,
		instance = QuickMultiRangeSlider.class)
	public static class Def extends QuickAbstractMultiSlider.Def<QuickMultiRangeSlider<?>> {
		private CompiledExpression theRangeMin;
		private CompiledExpression theRangeMax;
		private boolean isSourceModifying;

		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The minimum value for each range represented by a value in this slider */
		@QonfigAttributeGetter("range-min")
		public CompiledExpression getRangeMin() {
			return theRangeMin;
		}

		/** @return The maximum value for each range represented by a value in this slider */
		@QonfigAttributeGetter("range-max")
		public CompiledExpression getRangeMax() {
			return theRangeMax;
		}

		/** @return Whether the source values should be updated whenever the range min/max values change */
		@QonfigAttributeGetter("requires-source-modification")
		public boolean requiresSourceModification() {
			return isSourceModifying;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theRangeMin = getAttributeExpression("range-min", session);
			theRangeMax = getAttributeExpression("range-max", session);
			isSourceModifying = session.getAttribute("requires-source-modification", boolean.class);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * {@link QuickMultiRangeSlider} interpretation
	 *
	 * @param <T> The type of value to represent with each handle
	 */
	public static class Interpreted<T> extends QuickAbstractMultiSlider.Interpreted<T, QuickMultiRangeSlider<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theRangeMin;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theRangeMax;

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

		/** @return The minimum value for each range represented by a value in this slider */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getRangeMin() {
			return theRangeMin;
		}

		/** @return The maximum value for each range represented by a value in this slider */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getRangeMax() {
			return theRangeMax;
		}

		@Override
		protected InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> interpretValues(CompiledExpression values)
			throws ExpressoInterpretationException {
			return interpret(values, ModelTypes.Collection.anyAs());
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			InterpretedValueSynth<SettableValue<?>, SettableValue<Number>> rangeMin = interpret(getDefinition().getRangeMin(),
				ModelTypes.Value.forType(Number.class));
			Transformation.ReversibleTransformation<Number, Double> minTx = reverseSliderValue(//
				TypeTokens.get().unwrap(TypeTokens.getRawType(rangeMin.getType().getType(0))), //
				new Transformation.ReversibleTransformationPrecursor<>());
			theRangeMin = rangeMin.mapValue(ModelTypes.Value.DOUBLE, v -> v.transformReversible(__ -> minTx));
			InterpretedValueSynth<SettableValue<?>, SettableValue<Number>> rangeMax = interpret(getDefinition().getRangeMax(),
				ModelTypes.Value.forType(Number.class));
			Transformation.ReversibleTransformation<Number, Double> maxTx = reverseSliderValue(//
				TypeTokens.get().unwrap(TypeTokens.getRawType(rangeMax.getType().getType(0))), //
				new Transformation.ReversibleTransformationPrecursor<>());
			theRangeMax = rangeMax.mapValue(ModelTypes.Value.DOUBLE, v -> v.transformReversible(__ -> maxTx));
		}

		@Override
		public QuickMultiRangeSlider<T> create() {
			return new QuickMultiRangeSlider<>(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Double>> theRangeMinInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theRangeMaxInstantiator;
	private boolean isSourceModifying;

	private SettableValue<SettableValue<Double>> theRangeMin;
	private SettableValue<SettableValue<Double>> theRangeMax;

	/** @param id The element ID for this widget */
	protected QuickMultiRangeSlider(Object id) {
		super(id);

		theRangeMin = SettableValue.create();
		theRangeMax = SettableValue.create();
	}

	/** @return The minimum value for each range represented by a value in this slider */
	public SettableValue<Double> getRangeMin() {
		return SettableValue.flatten(theRangeMin, () -> 0.0);
	}

	/** @return The maximum value for each range represented by a value in this slider */
	public SettableValue<Double> getRangeMax() {
		return SettableValue.flatten(theRangeMax, () -> 0.0);
	}

	/** @return Whether the source values should be updated whenever the range min/max values change */
	public boolean requiresSourceModification() {
		return isSourceModifying;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theRangeMinInstantiator = myInterpreted.getRangeMin().instantiate();
		theRangeMaxInstantiator = myInterpreted.getRangeMax().instantiate();
		isSourceModifying = myInterpreted.getDefinition().requiresSourceModification();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theRangeMin.set(theRangeMinInstantiator.get(myModels));
		theRangeMax.set(theRangeMaxInstantiator.get(myModels));

		return myModels;
	}

	@Override
	public QuickMultiRangeSlider<T> copy(ExElement parent) {
		QuickMultiRangeSlider<T> copy = (QuickMultiRangeSlider<T>) super.copy(parent);

		copy.theRangeMin = SettableValue.create();
		copy.theRangeMax = SettableValue.create();

		return copy;
	}
}
