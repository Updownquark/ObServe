package org.observe.quick.base;

import java.util.function.Function;

import org.observe.SettableValue;
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
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickSlider extends QuickValueWidget.Abstract<Double> {
	public static final String SLIDER = "slider";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SLIDER,
		interpretation = Interpreted.class,
		instance = QuickSlider.class)
	public static class Def extends QuickValueWidget.Def.Abstract<QuickSlider> {
		private CompiledExpression theMin;
		private CompiledExpression theMax;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("min")
		public CompiledExpression getMin() {
			return theMin;
		}

		@QonfigAttributeGetter("max")
		public CompiledExpression getMax() {
			return theMax;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theMin = getAttributeExpression("min", session);
			theMax = getAttributeExpression("max", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<Double, QuickSlider> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMin;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theMax;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMin() {
			return theMin;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getMax() {
			return theMax;
		}

		@Override
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getOrInitValue() throws ExpressoInterpretationException {
			InterpretedValueSynth<SettableValue<?>, SettableValue<Number>> numberValue = interpret(getDefinition().getValue(),
				ModelTypes.Value.forType(Number.class));
			Class<?> numberType = TypeTokens.get().unwrap(TypeTokens.getRawType(numberValue.getType().getType(0)));
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
			return numberValue.mapValue(ModelTypes.Value.DOUBLE, numberColl -> numberColl.transformReversible(double.class, tx -> tx//
				.cache(false)//
				.map(Number::doubleValue).replaceSource(reverse, rev -> rev.allowInexactReverse(inexact))));
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theMin = interpret(getDefinition().getMin(), ModelTypes.Value.DOUBLE);
			theMax = interpret(getDefinition().getMax(), ModelTypes.Value.DOUBLE);
		}

		@Override
		public QuickSlider create() {
			return new QuickSlider(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<Double>> theMinInstantiator;
	private ModelValueInstantiator<SettableValue<Double>> theMaxInstantiator;

	private SettableValue<SettableValue<Double>> theMin;
	private SettableValue<SettableValue<Double>> theMax;

	public QuickSlider(Object id) {
		super(id);
		theMin = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class))
			.build();
		theMax = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Double>> parameterized(double.class))
			.build();
	}

	public SettableValue<Double> getMin() {
		return SettableValue.flatten(theMin, () -> 0.0);
	}

	public SettableValue<Double> getMax() {
		return SettableValue.flatten(theMax, () -> 0.0);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theMinInstantiator = myInterpreted.getMin().instantiate();
		theMaxInstantiator = myInterpreted.getMax().instantiate();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theMinInstantiator.instantiate();
		theMaxInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

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
	}

	@Override
	public QuickSlider copy(ExElement parent) {
		QuickSlider copy = (QuickSlider) super.copy(parent);

		copy.theMin = SettableValue.build(theMin.getType()).build();
		copy.theMax = SettableValue.build(theMax.getType()).build();

		return copy;
	}
}
