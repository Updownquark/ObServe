package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.VariableType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class ExTyped extends ExAddOn.Abstract<ExElement> {
	private static final ElementTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.buildAddOn(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "typed", Def.class, Interpreted.class,
			ExTyped.class)//
		.reflectAddOnMethods()//
		.build();

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExTyped> {
		private Object theValueType;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("type")
		public Object getValueType() {
			return theValueType;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);

			theValueType = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExTyped> {
		private TypeToken<?> theValueType;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<?> getValueType() {
			return theValueType;
		}

		@Override
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			super.update(models);

			if (getDefinition().getValueType() == null)
				theValueType = null;
			else if (getDefinition().getValueType() instanceof TypeToken)
				theValueType = (TypeToken<?>) getDefinition().getValueType();
			else if (getDefinition().getValueType() instanceof VariableType)
				theValueType = ((VariableType) getDefinition().getValueType()).getType(models);
			else
				throw new ExpressoInterpretationException("Unrecognized value type: " + getDefinition().getValueType().getClass().getName(),
					getElement().getDefinition().getElement().getPositionInFile(), 0);
		}

		@Override
		public ExTyped create(ExElement element) {
			return new ExTyped(this, element);
		}
	}

	private TypeToken<?> theValueType;

	public ExTyped(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
	}

	public Object getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theValueType = myInterpreted.getValueType();
	}
}
