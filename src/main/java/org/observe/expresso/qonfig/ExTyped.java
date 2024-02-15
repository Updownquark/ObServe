package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.VariableType;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

/**
 * A model value for which the type may be specified
 *
 * @param <T> The type of the model value
 */
public class ExTyped<T> extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this add-on */
	public static final String TYPED = "typed";

	/** Definition for {@link ExTyped} */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = TYPED,
		interpretation = Interpreted.class,
		instance = ExTyped.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExTyped<?>> {
		private VariableType theValueType;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The model value whose type this add-on configures
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The type of the model value */
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
				session.put(ExTyped.VALUE_TYPE_KEY, theValueType);
			} else
				theValueType = session.get(ExTyped.VALUE_TYPE_KEY, VariableType.class);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted<>(this, element);
		}
	}

	/**
	 * Interpretation for {@link ExTyped}
	 *
	 * @param <T> The type of the model value
	 */
	public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<ExElement, ExTyped<T>> {
		private TypeToken<T> theValueType;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The type of the model value */
		public TypeToken<T> getValueType() {
			return theValueType;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);

			if (getDefinition().getValueType() == null)
				theValueType = element.getExpressoEnv().get(VALUE_TYPE_KEY, TypeToken.class);
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
	/**
	 * Session key containing a model value's type, if known. This is typically a {@link VariableType}, but may be a
	 * {@link ModelInstanceType} depending on the API of the thing being parsed
	 */
	public static final String VALUE_TYPE_KEY = "value-type";

	ExTyped(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}

	/** @return The type of the model value */
	public TypeToken<T> getValueType() {
		return theValueType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theValueType = myInterpreted.getValueType();
	}
}
