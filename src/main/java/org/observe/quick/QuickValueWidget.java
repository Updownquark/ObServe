package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickValueWidget<T> extends QuickWidget {
	public static final String VALUE_WIDGET = "value-widget";

	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = VALUE_WIDGET,
		interpretation = Interpreted.class,
		instance = QuickValueWidget.class)
	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		@QonfigAttributeGetter("value-name")
		ModelComponentId getValueVariable();

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
			private ModelComponentId theValueVariable;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public ModelComponentId getValueVariable() {
				return theValueVariable;
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
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				String valueName = session.getAttributeText("value-name");
				theValue = session.getAttributeExpression("value");
				if (theValue.getExpression() == ObservableExpression.EMPTY && getParentElement() instanceof WidgetValueSupplier.Def)
					theValue = null; // Value supplied by parent
				theDisabled = session.getAttributeExpression("disable-with");
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theValueVariable = elModels.getElementValueModelId(valueName);
				elModels.satisfyElementValueType(theValueVariable, ModelTypes.Value,
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
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getValueVariable().getName(), theValue);
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
		private ModelComponentId theValueVariable;
		private ModelValueInstantiator<SettableValue<T>> theValueInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theDisabledInstantiator;
		private SettableValue<SettableValue<T>> theValue;
		private SettableValue<SettableValue<String>> theDisabled;

		protected Abstract(Object id) {
			super(id);
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
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			QuickValueWidget.Interpreted<T, ?> myInterpreted = (QuickValueWidget.Interpreted<T, ?>) interpreted;
			theValueVariable = myInterpreted.getDefinition().getValueVariable();
			theValueInstantiator = myInterpreted.getValue().instantiate();
			theDisabledInstantiator = myInterpreted.getDisabled() == null ? null : myInterpreted.getDisabled().instantiate();
			if (theValue == null)
				theValue = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class)
					.<SettableValue<T>> parameterized((TypeToken<T>) myInterpreted.getValue().getType().getType(0))).build();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			theValueInstantiator.instantiate();
			if (theDisabledInstantiator != null)
				theDisabledInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			theValue.set(theValueInstantiator.get(myModels), null);
			theDisabled.set(theDisabledInstantiator == null ? null : theDisabledInstantiator.get(myModels), null);
		}

		@Override
		public QuickValueWidget.Abstract<T> copy(ExElement parent) {
			QuickValueWidget.Abstract<T> copy = (QuickValueWidget.Abstract<T>) super.copy(parent);

			copy.theValue = SettableValue.build(theValue.getType()).build();
			copy.theDisabled = SettableValue.build(theDisabled.getType()).build();

			return copy;
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
