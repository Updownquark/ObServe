package org.observe.expresso.qonfig;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExIntValue extends ExAddOn.Abstract<ExElement> {
	private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.getAddOnTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "int-value", Def.class, Interpreted.class, ExIntValue.class);

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExIntValue> {
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
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);
			theInit = session.getAttributeExpression("init");
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExIntValue> {
		private InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> theInit;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<?>> getInit() {
			return theInit;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);

			ExTyped.Interpreted typed = getElement().getAddOn(ExTyped.Interpreted.class);
			if (getDefinition().getInit() == null)
				theInit = null;
			else if (typed != null && typed.getValueType() != null)
				theInit = getDefinition().getInit().interpret(ModelTypes.Value.forType(typed.getValueType()), env);
			else
				theInit = getDefinition().getInit().interpret(ModelTypes.Value.any(), env);
		}

		@Override
		public ExIntValue create(ExElement element) {
			return new ExIntValue(this, element);
		}
	}

	private SettableValue<?> theInit;

	public ExIntValue(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
	}

	public SettableValue<?> getInit() {
		return theInit;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theInit = myInterpreted.getInit() == null ? null : myInterpreted.getInit().get(models);
	}
}
