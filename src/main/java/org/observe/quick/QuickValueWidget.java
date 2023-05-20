package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickValueWidget<T> extends QuickWidget {
	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		String getValueName();

		CompiledExpression getValue();

		CompiledExpression getDisabled();

		@Override
		Interpreted<?, ? extends W> interpret(QuickElement.Interpreted<?> parent);

		public abstract class Abstract<T, W extends QuickValueWidget<T>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private String theValueName;
			private CompiledExpression theValue;
			private CompiledExpression theDisabled;

			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public String getValueName() {
				return theValueName;
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
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				theValueName = session.getAttributeText("value-name");
				theValue = session.getAttributeExpression("value");
				if (theValue.getExpression() == ObservableExpression.EMPTY && getParentElement() instanceof WidgetValueSupplier.Def)
					theValue = null; // Value supplied by parent
				theDisabled = session.getAttributeExpression("disable-with");
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

			protected Abstract(QuickValueWidget.Def<? super W> definition, QuickElement.Interpreted<?> parent) {
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
			public void update(QuickStyledElement.QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value;
				if (getDefinition().getValue() != null)
					value = getDefinition().getValue().evaluate(ModelTypes.Value.<T> anyAsV()).interpret();
				else
					value = ((WidgetValueSupplier.Interpreted<T, ?>) getParentElement()).getValue();
				InterpretedValueSynth<SettableValue<?>, SettableValue<String>> disabled = getDefinition().getDisabled() == null ? null
					: getDefinition().getDisabled().evaluate(ModelTypes.Value.STRING).interpret();
				theValue = ModelValueSynth.of(value.getType(), msi -> {
					SettableValue<T> valueInst = value.get(msi);
					if (disabled == null)
						return valueInst;
					SettableValue<String> disabledInst = disabled.get(msi);
					return valueInst.disableWith(disabledInst);
				}).interpret();
				DynamicModelValue.satisfyDynamicValue(getDefinition().getValueName(), getDefinition().getModels(),
					CompiledModelValue.constant(theValue));
				super.update(cache);
			}
		}
	}

	SettableValue<T> getValue();

	public abstract class Abstract<T> extends QuickWidget.Abstract implements QuickValueWidget<T> {
		private final SettableValue<SettableValue<T>> theValue;

		protected Abstract(QuickValueWidget.Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
				.<SettableValue<T>> parameterized((TypeToken<T>) interpreted.getValue().getType().getType(0))).build();
		}

		@Override
		public SettableValue<T> getValue() {
			return SettableValue.flatten(theValue);
		}

		@Override
		public ModelSetInstance update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models)
			throws ModelInstantiationException {
			ModelSetInstance myModels = super.update(interpreted, models);
			QuickValueWidget.Interpreted<T, ?> myInterpreted = (QuickValueWidget.Interpreted<T, ?>) interpreted;
			theValue.set(myInterpreted.getValue().get(myModels), null);
			return myModels;
		}
	}

	public interface WidgetValueSupplier<T> extends QuickElement {
		public interface Def<VS extends WidgetValueSupplier<?>> extends QuickElement.Def<VS> {
		}

		public interface Interpreted<T, VS extends WidgetValueSupplier<T>> extends QuickElement.Interpreted<VS> {
			@Override
			Def<? super VS> getDefinition();

			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() throws ExpressoInterpretationException;
		}
	}
}
