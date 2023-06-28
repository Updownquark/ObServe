package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.DynamicModelValue;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickValueWidget<T> extends QuickWidget {
	public static final String VALUE_WIDGET = "value-widget";

	public static final ExElement.AttributeValueGetter.Expression<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>, SettableValue<?>, SettableValue<?>> VALUE//
	= ExElement.AttributeValueGetter.<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>, SettableValue<?>, SettableValue<?>> ofX(
		Def::getValue, Interpreted::getValue, QuickValueWidget::getValue, "The value to edit with the widget");
	public static final ExElement.AttributeValueGetter.Expression<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>, SettableValue<?>, SettableValue<String>> DISABLE_WITH//
	= ExElement.AttributeValueGetter.<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>, SettableValue<?>, SettableValue<String>> ofX(
		Def::getDisabled, Interpreted::getDisabled, QuickValueWidget::getDisabled,
		"A value to disable this widget.  If the value is non-null, the widget will be disabled and the value will be displayed to the user.");
	public static final ExElement.AttributeValueGetter<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>> VALUE_NAME = ExElement.AttributeValueGetter
		.<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>> of(Def::getValueName, null, null,
			"The name of this widget's value for use in internal expressions");

	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		String getValueName();

		CompiledExpression getValue();

		CompiledExpression getDisabled();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<T, W extends QuickValueWidget<? extends T>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private String theValueName;
			private CompiledExpression theValue;
			private CompiledExpression theDisabled;

			protected Abstract(ExElement.Def<?> parent, QonfigElement element) {
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
				ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, VALUE_WIDGET);
				forAttribute(session.getAttributeDef(null, null, "value"), VALUE);
				forAttribute(session.getAttributeDef(null, null, "value-name"), VALUE_NAME);
				forAttribute(session.getAttributeDef(null, null, "disable-with"), DISABLE_WITH);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
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

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisabled();

		default TypeToken<T> getValueType() {
			return (TypeToken<T>) getValue().getType().getType(0);
		}

		public abstract class Abstract<T, W extends QuickValueWidget<T>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<T, W> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theDisabled;

			protected Abstract(QuickValueWidget.Def<? super W> definition, ExElement.Interpreted<?> parent) {
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
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisabled() {
				return theDisabled;
			}

			@Override
			public void update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value;
				if (getDefinition().getValue() != null)
					theValue = getDefinition().getValue().evaluate(ModelTypes.Value.<T> anyAsV()).interpret();
				else
					theValue = ((WidgetValueSupplier.Interpreted<T, ?>) getParentElement()).getValue();
				theDisabled = getDefinition().getDisabled() == null ? null
					: getDefinition().getDisabled().evaluate(ModelTypes.Value.STRING).interpret();
				DynamicModelValue.satisfyDynamicValue(getDefinition().getValueName(), getDefinition().getModels(),
					CompiledModelValue.constant(theValue));
				valueInterpreted();
				super.update(cache);
			}

			protected void valueInterpreted() {
			}
		}
	}

	SettableValue<T> getValue();

	SettableValue<String> getDisabled();

	public abstract class Abstract<T> extends QuickWidget.Abstract implements QuickValueWidget<T> {
		private final SettableValue<SettableValue<T>> theValue;
		private final SettableValue<SettableValue<String>> theDisabled;

		protected Abstract(QuickValueWidget.Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
				.<SettableValue<T>> parameterized((TypeToken<T>) interpreted.getValue().getType().getType(0))).build();
			theDisabled = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
		}

		@Override
		public SettableValue<T> getValue() {
			return SettableValue.flatten(theValue).disableWith(getDisabled());
		}

		@Override
		public SettableValue<String> getDisabled() {
			return SettableValue.flatten(theDisabled);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickValueWidget.Interpreted<T, ?> myInterpreted = (QuickValueWidget.Interpreted<T, ?>) interpreted;
			theValue.set(myInterpreted.getValue().get(myModels), null);
		}
	}

	public interface WidgetValueSupplier<T> extends ExElement {
		public interface Def<VS extends WidgetValueSupplier<?>> extends ExElement.Def<VS> {}

		public interface Interpreted<T, VS extends WidgetValueSupplier<T>> extends ExElement.Interpreted<VS> {
			@Override
			Def<? super VS> getDefinition();

			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() throws ExpressoInterpretationException;
		}
	}
}
