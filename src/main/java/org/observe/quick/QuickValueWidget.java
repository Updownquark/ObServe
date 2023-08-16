package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickValueWidget<T> extends QuickWidget {
	public static final String VALUE_WIDGET = "value-widget";
	public static final SingleTypeTraceability<QuickValueWidget<?>, Interpreted<?, ?>, Def<?>> VALUE_WIDGET_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, VALUE_WIDGET, Def.class, Interpreted.class,
			QuickValueWidget.class);

	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		@QonfigAttributeGetter("value-name")
		String getValueName();

		@QonfigAttributeGetter("value")
		CompiledExpression getValue();

		@QonfigAttributeGetter("disable-with")
		CompiledExpression getDisabled();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<W extends QuickValueWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private String theValueName;
			private CompiledExpression theValue;
			private CompiledExpression theDisabled;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
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
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(VALUE_WIDGET_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theValueName = session.getAttributeText("value-name");
				theValue = session.getAttributeExpression("value");
				if (theValue.getExpression() == ObservableExpression.EMPTY && getParentElement() instanceof WidgetValueSupplier.Def)
					theValue = null; // Value supplied by parent
				theDisabled = session.getAttributeExpression("disable-with");
				getAddOn(ExWithElementModel.Def.class).satisfyElementValueType(theValueName, ModelTypes.Value,
					(interp, env) -> ((Interpreted<?, ?>) interp).getOrInitValue().getType());
			}
		}
	}

	public interface Interpreted<T, W extends QuickValueWidget<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue();

		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException;

		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisabled();

		default TypeToken<T> getValueType() throws ExpressoInterpretationException {
			return (TypeToken<T>) getOrInitValue().getType().getType(0);
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
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException {
				if (theValue == null) {
					if (getDefinition().getValue() != null)
						theValue = getDefinition().getValue().interpret(ModelTypes.Value.<T> anyAsV(), getExpressoEnv());
					else
						theValue = ((WidgetValueSupplier.Interpreted<T, ?>) getParentElement()).getValue();
				}
				return theValue;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisabled() {
				return theDisabled;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theValue = null;
				super.doUpdate(env);
				getOrInitValue(); // Initialize theValue
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getValueName(), theValue);
				theDisabled = getDefinition().getDisabled() == null ? null
					: getDefinition().getDisabled().interpret(ModelTypes.Value.STRING, env);
			}

			@Override
			protected void postUpdate() throws ExpressoInterpretationException {
				super.postUpdate();
				checkValidModel();
			}

			protected void checkValidModel() throws ExpressoInterpretationException {
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
		public interface Def<VS extends WidgetValueSupplier<?>> extends ExElement.Def<VS> {
		}

		public interface Interpreted<T, VS extends WidgetValueSupplier<T>> extends ExElement.Interpreted<VS> {
			@Override
			Def<? super VS> getDefinition();

			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() throws ExpressoInterpretationException;
		}
	}
}
