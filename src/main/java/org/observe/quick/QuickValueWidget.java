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
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickValueWidget<T> extends QuickWidget {
	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		CompiledExpression getValue();

		CompiledExpression getDisabled();

		@Override
		Interpreted<?, ? extends W> interpret(QuickElement.Interpreted<?> parent);

		public abstract class Abstract<T, W extends QuickValueWidget<T>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private CompiledExpression theValue;
			private CompiledExpression theDisabled;

			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
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
			public Def.Abstract<T, W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theValue = getExpressoSession().getAttributeExpression("value");
				theDisabled = getExpressoSession().getAttributeExpression("disable-with");
				return this;
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

			public Abstract(QuickValueWidget.Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
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
			public Interpreted.Abstract<T, W> update(QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
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
				return this;
			}
		}
	}

	SettableValue<T> getValue();

	public abstract class Abstract<T> extends QuickWidget.Abstract implements QuickValueWidget<T> {
		private final SettableValue<SettableValue<T>> theValue;

		public Abstract(QuickValueWidget.Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
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
		public QuickValueWidget.Abstract<T> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theValue.set(getInterpreted().getValue().get(models), null);
			return this;
		}
	}
}
