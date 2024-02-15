package org.observe.expresso.qonfig;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * Add-on for a &lt;value> element in a &lt;model> element, for which the initial value may be specified
 *
 * @param <T> The type of the model value
 */
public class ExIntValue<T> extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this add-on */
	public static final String INT_VALUE = "int-value";

	/** Definition for {@link ExIntValue} */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = INT_VALUE,
		interpretation = Interpreted.class,
		instance = ExIntValue.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExIntValue<?>> {
		private CompiledExpression theInit;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The model value to configure the initial value for
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
		}

		/** @return The initial value for the model value */
		@QonfigAttributeGetter("init")
		public CompiledExpression getInit() {
			return theInit;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theInit = element.getAttributeExpression("init", session);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted<>(this, element);
		}
	}

	/**
	 * Interpretation for {@link ExIntValue}
	 *
	 * @param <T> The type of the model value
	 */
	public static class Interpreted<T> extends ExAddOn.Interpreted.Abstract<ExElement, ExIntValue<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theInit;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The initial value for the model value */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getInit() {
			return theInit;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);

			ExTyped.Interpreted<T> typed = getElement().getAddOn(ExTyped.Interpreted.class);
			if (getDefinition().getInit() == null)
				theInit = null;
			else if (typed != null && typed.getValueType() != null)
				theInit = getElement().interpret(getDefinition().getInit(), ModelTypes.Value.forType(typed.getValueType()));
			else
				theInit = getElement().interpret(getDefinition().getInit(), ModelTypes.Value.anyAsV());
		}

		@Override
		public Class<ExIntValue<T>> getInstanceType() {
			return (Class<ExIntValue<T>>) (Class<?>) ExIntValue.class;
		}

		@Override
		public ExIntValue<T> create(ExElement element) {
			return new ExIntValue<>(element);
		}
	}

	private ModelValueInstantiator<SettableValue<T>> theInitInstantiator;
	private SettableValue<T> theInit;

	ExIntValue(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted<?>> getInterpretationType() {
		return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
	}

	/** @return The initial value for the model value */
	public SettableValue<T> getInit() {
		return theInit;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theInitInstantiator = myInterpreted.getInit() == null ? null : myInterpreted.getInit().instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theInit = theInitInstantiator == null ? null : theInitInstantiator.get(models);
	}
}
