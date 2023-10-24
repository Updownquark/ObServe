package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.VariableType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ExTyped<T> extends ExAddOn.Abstract<ExElement> {
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "typed",
		interpretation = Interpreted.class,
		instance = ExTyped.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExTyped<?>> {
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
			super.update(session, element);
			QonfigValue typeV = session.attributes().get("type").get();
			if (typeV != null && !typeV.text.isEmpty()) {
				theValueType = VariableType.parseType(new LocatedPositionedContent.Default(typeV.fileLocation, typeV.position));
				session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, theValueType);
			} else
				theValueType = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY, VariableType.class);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted<>(this, element);
		}
	}

	public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<ExElement, ExTyped<T>> {
		private TypeToken<T> theValueType;

		public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public TypeToken<T> getValueType() {
			return theValueType;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);

			if (getDefinition().getValueType() == null)
				theValueType = null;
			else
				theValueType = (TypeToken<T>) getDefinition().getValueType().getType(getElement().getExpressoEnv());
		}

		@Override
		public Class<ExTyped<T>> getInstanceType() {
			return (Class<ExTyped<T>>) (Class<?>) ExTyped.class;
		}

		@Override
		public ExTyped<T> create(ExElement element) {
			return new ExTyped<>(element);
		}
	}

	private TypeToken<T> theValueType;

	public ExTyped(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}

	public TypeToken<T> getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) {
		super.update(interpreted, element);
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theValueType = myInterpreted.getValueType();
	}
}
