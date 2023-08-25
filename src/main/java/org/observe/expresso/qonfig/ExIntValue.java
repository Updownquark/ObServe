package org.observe.expresso.qonfig;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExIntValue<T> extends ExAddOn.Abstract<ExElement> {
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "int-value",
		interpretation = Interpreted.class,
		instance = ExIntValue.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExIntValue<?>> {
		private CompiledExpression theInit;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("init")
		public CompiledExpression getInit() {
			return theInit;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theInit = session.getAttributeExpression("init");
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted<>(this, element);
		}
	}

	public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<ExElement, ExIntValue<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theInit;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getInit() {
			return theInit;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);

			ExTyped.Interpreted<T> typed = getElement().getAddOn(ExTyped.Interpreted.class);
			if (getDefinition().getInit() == null)
				theInit = null;
			else if (typed != null && typed.getValueType() != null)
				theInit = getDefinition().getInit().interpret(ModelTypes.Value.forType(typed.getValueType()), env);
			else
				theInit = getDefinition().getInit().interpret(ModelTypes.Value.anyAsV(), env);
		}

		@Override
		public ExIntValue<T> create(ExElement element) {
			return new ExIntValue<>(element);
		}
	}

	private ModelValueInstantiator<SettableValue<T>> theInitInstantiator;
	private SettableValue<T> theInit;

	public ExIntValue(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}

	public SettableValue<?> getInit() {
		return theInit;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theInitInstantiator = myInterpreted.getInit() == null ? null : myInterpreted.getInit().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theInit = theInitInstantiator == null ? null : theInitInstantiator.get(models);
	}
}
