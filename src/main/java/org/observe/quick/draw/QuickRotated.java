package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.qommons.LambdaUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickRotated extends ExAddOn.Abstract<ExElement> {
	public static final String ROTATED = "rotated";

	/** The definition of a {@link QuickRotated} */
	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = ROTATED,
		interpretation = Interpreted.class,
		instance = QuickRotated.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, QuickRotated> {
		private CompiledExpression theRotation;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("rotation")
		public CompiledExpression getRotation() {
			return theRotation;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theRotation = element.getAttributeExpression("rotation", session);
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** The interpretation of a {@link QuickRotated} */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, QuickRotated> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> theRotation;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Double>> getRotation() {
			return theRotation;
		}

		@Override
		public Class<QuickRotated> getInstanceType() {
			return QuickRotated.class;
		}

		@Override
		public void update(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.update(element);

			theRotation = element.interpret(getDefinition().getRotation(), ModelTypes.Value.DOUBLE);
		}

		@Override
		public QuickRotated create(ExElement element) {
			return new QuickRotated(element);
		}
	}

	private ModelValueInstantiator<SettableValue<Double>> theRotationInstantiator;

	private SettableValue<SettableValue<Double>> theRotation;

	QuickRotated(ExElement element) {
		super(element);
		theRotation = SettableValue.create();
	}

	public SettableValue<Double> getRotation() {
		return SettableValue.flatten(theRotation, LambdaUtils.constantSupplier(0.0));
	}

	@Override
	public Class<? extends ExAddOn.Interpreted<ExElement, ?>> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theRotationInstantiator = ExElement.instantiate(myInterpreted.getRotation());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		theRotation.set(ExElement.get(theRotationInstantiator, models));

		return models;
	}

	@Override
	public QuickRotated copy(ExElement element) {
		QuickRotated copy = (QuickRotated) super.copy(element);

		copy.theRotation = SettableValue.create();

		return copy;
	}
}
