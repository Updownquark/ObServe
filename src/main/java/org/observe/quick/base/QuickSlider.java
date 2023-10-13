package org.observe.quick.base;

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
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

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

			theMin = session.getAttributeExpression("min");
			theMax = session.getAttributeExpression("max");
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
		protected ModelInstanceType<SettableValue<?>, SettableValue<Double>> getTargetType() {
			return ModelTypes.Value.DOUBLE;
		}

		@Override
		public TypeToken<QuickSlider> getWidgetType() {
			return TypeTokens.get().of(QuickSlider.class);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theMin = getDefinition().getMin().interpret(ModelTypes.Value.DOUBLE, env);
			theMax = getDefinition().getMax().interpret(ModelTypes.Value.DOUBLE, env);
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
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theMinInstantiator = myInterpreted.getMin().instantiate();
		theMaxInstantiator = myInterpreted.getMax().instantiate();
	}

	@Override
	public void instantiated() {
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
