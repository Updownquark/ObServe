package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickSpinner<T> extends QuickValueWidget.Abstract<T> {
	public static final String SPINNER = "spinner";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SPINNER,
		interpretation = Interpreted.class,
		instance = QuickSpinner.class)
	public static class Def extends QuickValueWidget.Def.Abstract<QuickSpinner<?>> {
		private CompiledExpression theIncrement;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("increment")
		public CompiledExpression getIncrement() {
			return theIncrement;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theIncrement = session.getAttributeExpression("increment");
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends QuickValueWidget.Interpreted.Abstract<T, QuickSpinner<T>> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theIncrement;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getIncrement() {
			return theIncrement;
		}

		@Override
		public TypeToken<QuickSpinner<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickSpinner.class).<QuickSpinner<T>> parameterized(getValueType());
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.doUpdate(env);

			theIncrement = getDefinition().getIncrement() == null ? null
				: getDefinition().getIncrement().interpret(ModelTypes.Value.forType(getValueType()), env);

			Class<T> valueType = TypeTokens.get().unwrap(TypeTokens.getRawType(getValueType()));
			if (valueType.isPrimitive()) {
				if (valueType == boolean.class || valueType == char.class || valueType == void.class)
					throw new ExpressoInterpretationException("Cannot create a spinner for type " + valueType.getName(),
						env.reporting().getPosition(), 0);
			} else if (valueType.isEnum()) {
				if (theIncrement != null)
					throw new ExpressoInterpretationException(
						"The increment attribute is not compatible with a spinner for enum type " + valueType.getName(),
						env.reporting().getPosition(),
						0);
			} else
				throw new ExpressoInterpretationException("Cannot create a spinner for type " + valueType.getName(),
					env.reporting().getPosition(), 0);
		}

		@Override
		public QuickSpinner create() {
			return new QuickSpinner(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<T>> theIncrementInstantiator;

	private SettableValue<SettableValue<T>> theIncrement;

	public QuickSpinner(Object id) {
		super(id);
		theIncrement = SettableValue.build((Class<SettableValue<T>>) (Class<?>) SettableValue.class).build();
	}

	public SettableValue<T> getIncrement() {
		return SettableValue.flatten(theIncrement);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted<T> myInterpreted = (Interpreted<T>) interpreted;
		theIncrementInstantiator = myInterpreted.getIncrement() == null ? null : myInterpreted.getIncrement().instantiate();
	}

	@Override
	public void instantiated() {
		super.instantiated();

		if (theIncrement != null)
			theIncrementInstantiator.instantiate();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);

		theIncrement.set(theIncrementInstantiator == null ? null : theIncrementInstantiator.get(myModels), null);
	}

	@Override
	public QuickSpinner<T> copy(ExElement parent) {
		QuickSpinner<T> copy = (QuickSpinner<T>) super.copy(parent);

		copy.theIncrement = SettableValue.build(theIncrement.getType()).build();

		return copy;
	}
}
