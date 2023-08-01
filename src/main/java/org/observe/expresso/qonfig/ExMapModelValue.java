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

public class ExMapModelValue extends ExAddOn.Abstract<ExElement> {
	private static final SingleTypeTraceability<ExElement, ExElement.Interpreted<?>, ExElement.Def<?>> TRACEABILITY = ElementTypeTraceability
		.getAddOnTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "map-model-value", Def.class,
			Interpreted.class, ExMapModelValue.class);

	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExMapModelValue> {
		private VariableType theKeyType;

		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("key-type")
		public VariableType getKeyType() {
			return theKeyType;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), element.reporting()));
			super.update(session, element);
			QonfigValue keyTypeV = session.getAttributeQV("key-type");
			if (keyTypeV != null && !keyTypeV.text.isEmpty()) {
				theKeyType = VariableType.parseType(new LocatedPositionedContent.Default(keyTypeV.fileLocation, keyTypeV.position));
				session.put(ExpressoBaseV0_1.KEY_TYPE_KEY, theKeyType);
			} else
				theKeyType = session.get(ExpressoBaseV0_1.KEY_TYPE_KEY, VariableType.class);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExMapModelValue> {
		private TypeToken<?> theKeyType;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<?> getKeyType() {
			return theKeyType;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);

			if (getDefinition().getKeyType() == null)
				theKeyType = null;
			else
				theKeyType = getDefinition().getKeyType().getType(env);
		}

		@Override
		public ExMapModelValue create(ExElement element) {
			return new ExMapModelValue(this, element);
		}
	}

	private TypeToken<?> theValueType;

	public ExMapModelValue(Interpreted interpreted, ExElement element) {
		super(interpreted, element);
	}

	public TypeToken<?> getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theValueType = myInterpreted.getKeyType();
	}
}
