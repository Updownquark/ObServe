package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickValueWidget<T> extends QuickWidget {
	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		CompiledExpression getValue();

		CompiledExpression getDisabled();

		@Override
		Interpreted<?, ? extends W> interpret(QuickContainer2.Interpreted<?, ?> parent, QuickInterpretationCache cache)
			throws ExpressoInterpretationException;

		public abstract class Abstract<T, W extends QuickValueWidget<T>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private CompiledExpression theValue;
			private CompiledExpression theDisabled;

			public Abstract(AbstractQIS<?> session) throws QonfigInterpretationException {
				super(session);
			}

			@Override
			public CompiledExpression getValue() {
				return theValue;
			}

			@Override
			public CompiledExpression getDisabled() {
				return theDisabled;
			}

			@Override
			public void update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theValue = getExpressoSession().getAttributeExpression("value");
				theDisabled = getExpressoSession().getAttributeExpression("disable-with");
			}
		}
	}

	public interface Interpreted<T, W extends QuickValueWidget<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue();

		default TypeToken<T> getValueType() {
			return (TypeToken<T>) getValue().getType().getType(0);
		}

		public abstract class Abstract<T, W extends QuickValueWidget<T>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<T, W> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

			public Abstract(QuickValueWidget.Def<? super W> definition, QuickContainer2.Interpreted<?, ?> parent,
				QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super(definition, parent, cache);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
				return theValue;
			}

			@Override
			public void update(QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value = getDefinition().getValue()
					.evaluate(ModelTypes.Value.<T> anyAs()).interpret();
				InterpretedValueSynth<SettableValue<?>, SettableValue<String>> disabled = getDefinition().getDisabled() == null ? null
					: getDefinition().getDisabled().evaluate(ModelTypes.Value.STRING).interpret();
				theValue = ModelValueSynth.of(value.getType(), msi -> {
					SettableValue<T> valueInst = value.get(msi);
					if (disabled == null)
						return valueInst;
					SettableValue<String> disabledInst = disabled.get(msi);
					return valueInst.disableWith(disabledInst);
				}).interpret();
			}
		}
	}

	SettableValue<T> getValue();

	public abstract class Abstract<T> extends QuickWidget.Abstract implements QuickValueWidget<T> {
		private final SettableValue<SettableValue<T>> theValue;

		public Abstract(QuickValueWidget.Interpreted<T, ?> interpreted, QuickContainer2<?> parent, ModelSetInstance models) throws ModelInstantiationException {
			super(interpreted, parent, models);
			theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
				.<SettableValue<T>> parameterized((TypeToken<T>) interpreted.getValue().getType().getType(0))).build();
		}

		@Override
		public QuickValueWidget.Interpreted<T, ?> getInterpreted() {
			return (QuickValueWidget.Interpreted<T, ?>) super.getInterpreted();
		}

		@Override
		public SettableValue<T> getValue() {
			return SettableValue.flatten(theValue);
		}

		@Override
		public void update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theValue.set(getInterpreted().getValue().get(models), null);
		}
	}
}
