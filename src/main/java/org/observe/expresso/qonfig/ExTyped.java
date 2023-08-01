package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ExTyped extends ExAddOn.Abstract<ExElement> {
	private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.getAddOnTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "typed", Def.class, Interpreted.class,
			ExTyped.class);

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExTyped> {
		private VariableType theValueType;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("type")
		public VariableType getValueType() {
			return theValueType;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);
			QonfigValue typeV = session.getAttributeQV("type");
			if (typeV != null && !typeV.text.isEmpty()) {
				theValueType = VariableType.parseType(new LocatedPositionedContent.Default(typeV.fileLocation, typeV.position));
				session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, theValueType);
			} else
				theValueType = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY, VariableType.class);
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
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);

			if (getDefinition().getValueType() == null)
				theValueType = null;
			else
				theValueType = getDefinition().getValueType().getType(env);
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

	public TypeToken<?> getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theValueType = myInterpreted.getValueType();
	}
}
