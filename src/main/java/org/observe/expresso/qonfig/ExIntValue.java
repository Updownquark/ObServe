package org.observe.expresso.qonfig;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class ExIntValue extends ExAddOn.Abstract<ExElement> {
	private static final ExAddOn.AddOnAttributeGetter.Expression<ExElement, ExIntValue, Interpreted, Def, SettableValue<?>, SettableValue<?>> INIT = ExAddOn.AddOnAttributeGetter
		.<ExElement, ExIntValue, Interpreted, Def, SettableValue<?>, SettableValue<?>> ofX(Def.class, Def::getInit, Interpreted.class,
			Interpreted::getInit, ExIntValue.class, ExIntValue::getInit, "The value with which to initialize this variable");

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExIntValue> {
		private CompiledExpression theInit;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		public CompiledExpression getInit() {
			return theInit;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.forAttribute(session.getAttributeDef(null, null, "init"), INIT);
			super.update(session, element);
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
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			super.update(models);

			ExTyped.Interpreted typed = getElement().getAddOn(ExTyped.Interpreted.class);
			if (getDefinition().getInit() == null)
				theInit = null;
			else if (typed != null && typed.getValueType() != null)
				theInit = getDefinition().getInit().evaluate(ModelTypes.Value.forType(typed.getValueType())).interpret();
			else
				theInit = getDefinition().getInit().evaluate(ModelTypes.Value.any()).interpret();
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
