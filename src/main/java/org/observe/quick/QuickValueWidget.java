package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
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

/**
 * A Quick widget whose primary purpose is to display and potentially allow editing of a single, typically scalar, value
 *
 * @param <T> The type of the value to display/edit
 */
public interface QuickValueWidget<T> extends QuickWidget {
	/** The XML name of this type */
	public static final String VALUE_WIDGET = "value-widget";

	/**
	 * The definition of a {@link QuickValueWidget}
	 *
	 * @param <W> The sub-type of value widget
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = VALUE_WIDGET,
		interpretation = Interpreted.class,
		instance = QuickValueWidget.class)
	public interface Def<W extends QuickValueWidget<?>> extends QuickWidget.Def<W> {
		/** @return The model ID of the model value containing the value that the widget will be editing */
		@QonfigAttributeGetter("value-name")
		ModelComponentId getValueVariable();

		/** @return The value that the widget will edit */
		@QonfigAttributeGetter("value")
		CompiledExpression getValue();

		/** @return An expression that will cause the editor to be disabled and not accept user input */
		@QonfigAttributeGetter("disable-with")
		CompiledExpression getDisabled();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		/**
		 * Abstract {@link QuickValueWidget} definition implementation
		 *
		 * @param <W> The sub-type of value widget
		 */
		public abstract class Abstract<W extends QuickValueWidget<?>> extends QuickWidget.Def.Abstract<W> implements Def<W> {
			private CompiledExpression theValue;
			private CompiledExpression theDisabled;
			private ModelComponentId theValueVariable;

			/**
			 * @param parent The parent element for this widget
			 * @param type The Qonfig type of this element
			 */
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
				theValue = getAttributeExpression("value", session);
				if (theValue.getExpression() == ObservableExpression.EMPTY && getParentElement() instanceof WidgetValueSupplier.Def)
					theValue = null; // Value supplied by parent
				theDisabled = getAttributeExpression("disable-with", session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theValueVariable = elModels.getElementValueModelId(valueName);
				elModels.satisfyElementValueType(theValueVariable, ModelTypes.Value,
					(interp, env) -> ((Interpreted<?, ?>) interp).getOrInitValue().getType());
			}
		}
	}

	/**
	 * An interpretation of a {@link QuickValueWidget}
	 *
	 * @param <T> The type of the value to display/edit
	 * @param <W> The sub-type of value widget
	 */
	public interface Interpreted<T, W extends QuickValueWidget<T>> extends QuickWidget.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		/** @return The value that the widget will edit */
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue();

		/**
		 * @return The value that the widget will edit
		 * @throws ExpressoInterpretationException If the value could not be interpreted
		 */
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException;

		/** @return An expression that will cause the editor to be disabled and not accept user input */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getDisabled();

		/**
		 * @return The type of the value to edit
		 * @throws ExpressoInterpretationException If the value could not be interpreted
		 */
		default TypeToken<T> getValueType() throws ExpressoInterpretationException {
			return (TypeToken<T>) getOrInitValue().getType().getType(0);
		}

		/**
		 * Abstract {@link QuickValueWidget} interpretation implementation
		 *
		 * @param <T> The type of the value to display/edit
		 * @param <W> The sub-type of value widget
		 */
		public abstract class Abstract<T, W extends QuickValueWidget<T>> extends QuickWidget.Interpreted.Abstract<W>
		implements Interpreted<T, W> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theDisabled;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this widget
			 */
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

			/** @return The target type to use to attempt to interpret the value */
			protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
				return ModelTypes.Value.<T> anyAsV();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException {
				if (theValue == null) {
					if (getDefinition().getValue() != null)
						theValue = interpret(getDefinition().getValue(), getTargetType());
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
				theValue = getOrInitValue(); // Initialize theValue
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getValueVariable().getName(), theValue);
				theDisabled = interpret(getDefinition().getDisabled(), ModelTypes.Value.STRING);
			}

			@Override
			protected void postUpdate() throws ExpressoInterpretationException {
				super.postUpdate();
				checkValidModel();
			}

			/**
			 * Checks to ensure that the configuration for this widget is correct. E.g. some components may allow an optional value, an
			 * icon, or some static text, but at least one must be specified.
			 *
			 * @throws ExpressoInterpretationException If this widget is not correctly configured
			 */
			protected void checkValidModel() throws ExpressoInterpretationException {
			}
		}
	}

	/** @return The value to edit */
	SettableValue<T> getValue();

	/**
	 * @return A value that, when null, specifies that the widget should accept user input. When not null, this is a human-readable message
	 *         describing why the widget should be disabled.
	 */
	SettableValue<String> getDisabled();

	/**
	 * Abstract {@link QuickValueWidget} implementation
	 *
	 * @param <T> The type of the value to display/edit
	 */
	public abstract class Abstract<T> extends QuickWidget.Abstract implements QuickValueWidget<T> {
		private ModelValueInstantiator<SettableValue<T>> theValueInstantiator;
		private ModelValueInstantiator<SettableValue<String>> theDisabledInstantiator;
		private SettableValue<SettableValue<T>> theValue;
		private SettableValue<SettableValue<String>> theDisabled;

		/** @param id The element identifier for this widget */
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

	/**
	 * When a {@link QuickValueWidget} is a child of an element that implements this interface, it can accept the value to be edited from
	 * the parent, not requiring that it be specified in XML.
	 *
	 * @param <T> The type of the value to edit
	 */
	public interface WidgetValueSupplier<T> extends ExElement {
		/**
		 * Definition of a {@link WidgetValueSupplier}
		 *
		 * @param <VS> The sub-type of the element
		 */
		public interface Def<VS extends WidgetValueSupplier<?>> extends ExElement.Def<VS> {
		}

		/**
		 * Interpretation of a {@link WidgetValueSupplier}
		 *
		 * @param <T> The type of the value to edit
		 * @param <VS> The sub-type of the element
		 */
		public interface Interpreted<T, VS extends WidgetValueSupplier<T>> extends ExElement.Interpreted<VS> {
			@Override
			Def<? super VS> getDefinition();

			/**
			 * @return The value to edit
			 * @throws ExpressoInterpretationException If the value could not be interpreted
			 */
			InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() throws ExpressoInterpretationException;
		}
	}
}
