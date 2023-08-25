package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.VariableType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ExMapModelValue<K> extends ExAddOn.Abstract<ExElement> {
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "map-model-value",
		interpretation = Interpreted.class,
		instance = ExMapModelValue.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExMapModelValue<?>> {
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

	public static class Interpreted<K> extends ExAddOn.Interpreted.Abstract<ExElement, ExMapModelValue<K>> {
		private TypeToken<K> theKeyType;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);

			if (getDefinition().getKeyType() == null)
				theKeyType = null;
			else
				theKeyType = (TypeToken<K>) getDefinition().getKeyType().getType(env);
		}

		@Override
		public ExMapModelValue<K> create(ExElement element) {
			return new ExMapModelValue<>(element);
		}
	}

	private TypeToken<?> theValueType;

	public ExMapModelValue(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}

	public TypeToken<?> getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted) {
		super.update(interpreted);
		Interpreted<K> myInterpreted = (Interpreted<K>) interpreted;
		theValueType = myInterpreted.getKeyType();
	}
}
