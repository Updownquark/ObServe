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
 * Annotation on model values that are maps, and so may require a key type
 *
 * @param <K> The key type of the map
 */
public class ExMapModelValue<K> extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this add-on */
	public static final String MAP_MODEL_VALUE = "map-model-value";

	/**
	 * Definition for {@link ExMapModelValue}
	 *
	 * @param <AO> The sub-type of add-on to create
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = MAP_MODEL_VALUE,
		interpretation = Interpreted.class,
		instance = ExMapModelValue.class)
	public static class Def<AO extends ExMapModelValue<?>> extends ExAddOn.Def.Abstract<ExElement, AO> {
		private VariableType theKeyType;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The map-type model value element
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The key type of the map */
		@QonfigAttributeGetter("key-type")
		public VariableType getKeyType() {
			return theKeyType;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			QonfigValue keyTypeV = session.attributes().get("key-type").get();
			if (keyTypeV != null && !keyTypeV.text.isEmpty()) {
				theKeyType = VariableType.parseType(new LocatedPositionedContent.Default(keyTypeV.fileLocation, keyTypeV.position));
				session.put(ExMapModelValue.KEY_TYPE_KEY, theKeyType);
			} else
				theKeyType = session.get(ExMapModelValue.KEY_TYPE_KEY, VariableType.class);
		}

		@Override
		public Interpreted<?, ? extends AO> interpret(ExElement.Interpreted<? extends ExElement> element) {
			return (Interpreted<?, ? extends AO>) new Interpreted<>((Def<ExMapModelValue<Object>>) this, element);
		}
	}

	/**
	 * Interpretation for {@link ExMapModelValue}
	 *
	 * @param <K> The key type of the map
	 * @param <AO> The sub-type of add-on to create
	 */
	public static class Interpreted<K, AO extends ExMapModelValue<K>> extends ExAddOn.Interpreted.Abstract<ExElement, AO> {
		private TypeToken<K> theKeyType;

		Interpreted(Def<? super AO> definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def<? super AO> getDefinition() {
			return (Def<? super AO>) super.getDefinition();
		}

		/** @return The key type of the map */
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);

			if (getDefinition().getKeyType() == null)
				theKeyType = null;
			else
				theKeyType = (TypeToken<K>) getDefinition().getKeyType().getType(getElement().getExpressoEnv());
		}

		@Override
		public Class<AO> getInstanceType() {
			return (Class<AO>) ExMapModelValue.class;
		}

		@Override
		public AO create(ExElement element) {
			return (AO) new ExMapModelValue<>(element);
		}
	}

	private TypeToken<?> theKeyType;
	/**
	 * Session key containing a model value's key-type, if applicable and known. This is typically a {@link VariableType}, but may be a
	 * {@link ModelInstanceType} depending on the API of the thing being parsed
	 */
	public static final String KEY_TYPE_KEY = "key-type";

	ExMapModelValue(ExElement element) {
		super(element);
	}

	@Override
	public Class<? extends Interpreted<?, ?>> getInterpretationType() {
		return (Class<Interpreted<?, ?>>) (Class<?>) Interpreted.class;
	}

	/** @return The key type of the map */
	public TypeToken<?> getKeyType() {
		return theKeyType;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
		Interpreted<K, ?> myInterpreted = (Interpreted<K, ?>) interpreted;
		theKeyType = myInterpreted.getKeyType();
	}
}
