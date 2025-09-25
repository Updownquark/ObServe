package org.observe.quick.ext;

import org.observe.Transformation;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;

/** A slider with multiple thumbs and a great deal of control over rendering */
public class QuickMultiSlider extends QuickAbstractMultiSlider<Double> {
	/** The XML name of this element */
	public static final String MULTI_SLIDER = "multi-slider";

	/** {@link QuickMultiSlider} definition */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = MULTI_SLIDER,
		interpretation = Interpreted.class,
		instance = QuickMultiSlider.class)
	public static class Def extends QuickAbstractMultiSlider.Def<QuickMultiSlider> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** {@link QuickMultiSlider} interpretation */
	public static class Interpreted extends QuickAbstractMultiSlider.Interpreted<Double, QuickMultiSlider> {
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

		@Override
		protected InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Double>> interpretValues(CompiledExpression values)
			throws ExpressoInterpretationException {
			InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<Number>> numberValues = interpret(values,
				ModelTypes.Collection.forType(Number.class));
			Transformation.ReversibleTransformation<Number, Double> tx = reverseSliderValue(//
				TypeTokens.get().unwrap(TypeTokens.getRawType(numberValues.getType().getType(0))), //
				new Transformation.ReversibleTransformationPrecursor<>());
			return numberValues.mapValue(ModelTypes.Collection.forType(double.class),
				numberColl -> numberColl.flow().<Double> transform(__ -> tx)//
				.collectPassive());
		}

		@Override
		public QuickMultiSlider create() {
			return new QuickMultiSlider(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickMultiSlider(Object id) {
		super(id);
	}

	@Override
	public QuickMultiSlider copy(ExElement parent) {
		return (QuickMultiSlider) super.copy(parent);
	}
}
