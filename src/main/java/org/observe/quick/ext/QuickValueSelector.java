package org.observe.quick.ext;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickWidget;
import org.observe.quick.base.QuickButton;
import org.observe.quick.base.QuickTable;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/**
 * A value selector, a widget presenting 2 tables to the user: one whose rows represent available values, and another whose rows represent
 * values from the available set that are selected for use in some operation. Components are available for the user to include or exclude
 * available values.
 *
 * @param <A> The type of available values
 * @param <I> The type of included values
 */
public class QuickValueSelector<A, I> extends QuickWidget.Abstract {
	/** The XML name of this widget */
	public static final String VALUE_SELECTOR = "value-selector";

	/** Definition for a {@link QuickValueSelector} */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = VALUE_SELECTOR,
		interpretation = Interpreted.class,
		instance = QuickValueSelector.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickValueSelector<?, ?>> {
		private CompiledExpression theAvailableValues;
		private CompiledExpression theIncludedValues;
		private ModelComponentId theAvailableValueName;
		private ModelComponentId theAvailableRowsName;
		private ModelComponentId theIncludedRowsName;
		private CompiledExpression theInclude;
		private String theItemName;
		private QuickSuperTable.Def<?> theAvailable;
		private QuickTable.Def<?> theIncluded;
		private QuickButton.Def<?> theIncludeAllConfig;
		private QuickButton.Def<?> theIncludeConfig;
		private QuickButton.Def<?> theExcludeConfig;
		private QuickButton.Def<?> theExcludeAllConfig;

		/**
		 * @param parent The parent of this widget
		 * @param qonfigType The Qonfig type of this widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The collection of available values for the user to select from */
		@QonfigAttributeGetter("available")
		public CompiledExpression getAvailableValues() {
			return theAvailableValues;
		}

		/** @return The collection of values included for the operation */
		@QonfigAttributeGetter("included")
		public CompiledExpression getIncludedValues() {
			return theIncludedValues;
		}

		/** @return The available table */
		@QonfigChildGetter("available")
		public QuickSuperTable.Def<?> getAvailable() {
			return theAvailable;
		}

		/** @return The included table */
		@QonfigChildGetter("included")
		public QuickTable.Def<?> getIncluded() {
			return theIncluded;
		}

		/** @return The name of the variable in which the currently active available value will be available */
		@QonfigAttributeGetter("available-value-name")
		public ModelComponentId getAvailableValueName() {
			return theAvailableValueName;
		}

		ModelComponentId getAvailableRowsName() {
			return theAvailableRowsName;
		}

		ModelComponentId getIncludedRowsName() {
			return theIncludedRowsName;
		}

		/** @return The name for items in the table--may affect the text in some UI elements */
		@QonfigAttributeGetter("item-name")
		public String getItemName() {
			return theItemName;
		}

		/** @return Produces an included value from an available one when the user includes it */
		@QonfigAttributeGetter("include")
		public CompiledExpression getInclude() {
			return theInclude;
		}

		/** @return Configuration for the button that causes all values in the available table to be transferred to the included table */
		@QonfigChildGetter("include-all-button")
		public QuickButton.Def<?> getIncludeAllConfig() {
			return theIncludeAllConfig;
		}

		/**
		 * @return Configuration for the button that causes all selected values in the available table to be transferred to the included
		 *         table
		 */
		@QonfigChildGetter("include-button")
		public QuickButton.Def<?> getIncludeConfig() {
			return theIncludeConfig;
		}

		/** @return Configuration for the button that causes all selected values in the included table to be removed */
		@QonfigChildGetter("exclude-button")
		public QuickButton.Def<?> getExcludeConfig() {
			return theExcludeConfig;
		}

		/** @return Configuration for the button that causes all values in the included table to be removed */
		@QonfigChildGetter("exclude-all-button")
		public QuickButton.Def<?> getExcludeAllConfig() {
			return theExcludeAllConfig;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theAvailableValues = getAttributeExpression("available", session);
			theIncludedValues = getAttributeExpression("included", session);

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			String availableValueName = session.getAttributeText("available-value-name");
			theAvailableValueName = elModels.getElementValueModelId(availableValueName);
			theAvailableRowsName = elModels.getElementValueModelId("$AVAILABLE_ROWS$");
			theIncludedRowsName = elModels.getElementValueModelId("$INCLUDED_ROWS$");
			theInclude = getAttributeExpression("include", session);
			theItemName = session.getAttributeText("item-name");

			theAvailable = syncChild(QuickSuperTable.Def.class, theAvailable, session, "available");
			theIncluded = syncChild(QuickTable.Def.class, theIncluded, session, "included");

			theIncludeAllConfig = syncChild(QuickButton.Def.class, theIncludeAllConfig, session, "include-all-button");
			theIncludeConfig = syncChild(QuickButton.Def.class, theIncludeConfig, session, "include-button");
			theExcludeConfig = syncChild(QuickButton.Def.class, theExcludeConfig, session, "exclude-button");
			theExcludeAllConfig = syncChild(QuickButton.Def.class, theExcludeAllConfig, session, "exclude-all-button");

			if (theIncludeAllConfig != null && theIncludeAllConfig.getClass() != QuickButton.Def.class)
				theIncludeAllConfig.reporting()
				.warn("<button> sub-type is not respected here. This handle is only used for configuration.");
			if (theIncludeConfig != null && theIncludeConfig.getClass() != QuickButton.Def.class)
				theIncludeConfig.reporting().warn("<button> sub-type is not respected here. This handle is only used for configuration.");
			if (theExcludeConfig != null && theExcludeConfig.getClass() != QuickButton.Def.class)
				theExcludeConfig.reporting().warn("<button> sub-type is not respected here. This handle is only used for configuration.");
			if (theExcludeAllConfig != null && theExcludeAllConfig.getClass() != QuickButton.Def.class)
				theExcludeAllConfig.reporting()
				.warn("<button> sub-type is not respected here. This handle is only used for configuration.");

			elModels.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theAvailableValueName, ModelTypes.Value, //
				Interpreted::getAvailableValueType);
			elModels.<Interpreted<?, ?>, ObservableCollection<?>> satisfyElementSingleValueType(theAvailableRowsName, ModelTypes.Collection, //
				Interpreted::getAvailableValueType);
			elModels.<Interpreted<?, ?>, ObservableCollection<?>> satisfyElementSingleValueType(theIncludedRowsName, ModelTypes.Collection, //
				Interpreted::getIncludedValueType);
		}

		@Override
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	/**
	 * Interpretation for a {@link QuickValueSelector}
	 *
	 * @param <A> The type of available values
	 * @param <I> The type of included values
	 */
	public static class Interpreted<A, I> extends QuickWidget.Interpreted.Abstract<QuickValueSelector<A, I>> {
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<A>> theAvailableValues;
		private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<I>> theIncludedValues;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<I>> theInclude;
		private QuickSuperTable.Interpreted<A, ?, ?> theAvailable;
		private QuickTable.Interpreted<I, ?, ?> theIncluded;
		private QuickButton.Interpreted<?> theIncludeAllConfig;
		private QuickButton.Interpreted<?> theIncludeConfig;
		private QuickButton.Interpreted<?> theExcludeConfig;
		private QuickButton.Interpreted<?> theExcludeAllConfig;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The collection of available values for the user to select from */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<A>> getAvailableValues() {
			return theAvailableValues;
		}

		/** @return The collection of values included for the operation */
		public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<I>> getIncludedValues() {
			return theIncludedValues;
		}

		/** @return Produces an included value from an available one when the user includes it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<I>> getInclude() {
			return theInclude;
		}

		/** @return The available table */
		public QuickSuperTable.Interpreted<A, ?, ?> getAvailable() {
			return theAvailable;
		}

		/** @return The included table */
		public QuickTable.Interpreted<I, ?, ?> getIncluded() {
			return theIncluded;
		}

		/** @return Configuration for the button that causes all values in the available table to be transferred to the included table */
		public QuickButton.Interpreted<?> getIncludeAllConfig() {
			return theIncludeAllConfig;
		}

		/**
		 * @return Configuration for the button that causes all selected values in the available table to be transferred to the included
		 *         table
		 */
		public QuickButton.Interpreted<?> getIncludeConfig() {
			return theIncludeConfig;
		}

		/** @return Configuration for the button that causes all selected values in the included table to be removed */
		public QuickButton.Interpreted<?> getExcludeConfig() {
			return theExcludeConfig;
		}

		/** @return Configuration for the button that causes all values in the included table to be removed */
		public QuickButton.Interpreted<?> getExcludeAllConfig() {
			return theExcludeAllConfig;
		}

		/** @return The name of the variable in which the currently active available value will be available */
		public TypeToken<A> getAvailableValueType() {
			return (TypeToken<A>) theAvailableValues.getType().getType(0);
		}

		/** @return The type of included values */
		public TypeToken<I> getIncludedValueType() {
			return (TypeToken<I>) theIncludedValues.getType().getType(0);
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			theAvailableValues = interpret(getDefinition().getAvailableValues(), ModelTypes.Collection.anyAsV());
			theIncludedValues = interpret(getDefinition().getIncludedValues(), ModelTypes.Collection.anyAsV());

			super.doUpdate();

			theAvailable = syncChild(getDefinition().getAvailable(), theAvailable,
				t -> (QuickSuperTable.Interpreted<A, ?, ?>) t.interpret(this), t -> t.updateElement());
			theIncluded = syncChild(getDefinition().getIncluded(), theIncluded,
				t -> (QuickTable.Interpreted<I, ?, ?>) t.interpret(this), t -> t.updateElement());

			theInclude = interpret(getDefinition().getInclude(), ModelTypes.Value.forType(theIncluded.getValueType()));

			theIncludeAllConfig = syncChild(getDefinition().getIncludeAllConfig(), theIncludeAllConfig, t -> t.interpret(this),
				QuickButton.Interpreted::updateElement);
			theIncludeConfig = syncChild(getDefinition().getIncludeConfig(), theIncludeConfig, t -> t.interpret(this),
				QuickButton.Interpreted::updateElement);
			theExcludeConfig = syncChild(getDefinition().getExcludeConfig(), theExcludeConfig, t -> t.interpret(this),
				QuickButton.Interpreted::updateElement);
			theExcludeAllConfig = syncChild(getDefinition().getExcludeAllConfig(), theExcludeAllConfig, t -> t.interpret(this),
				QuickButton.Interpreted::updateElement);
		}

		@Override
		public QuickValueSelector<A, I> create() {
			return new QuickValueSelector<>(getIdentity());
		}
	}

	/**
	 * Model context for a {@link QuickValueSelector}
	 *
	 * @param <A> The type of available values
	 * @param <I> The type of included values
	 */
	public interface ValueSelectorContext<A, I> {
		/** @return The available value */
		SettableValue<A> getAvailableValue();

		/**
		 * Default {@link ValueSelectorContext}
		 *
		 * @param <A> The type of available values
		 * @param <I> The type of included values
		 */
		public class Default<A, I> implements ValueSelectorContext<A, I> {
			private final SettableValue<A> theAvailableValue;

			/** @param availableValue The available value */
			public Default(SettableValue<A> availableValue) {
				theAvailableValue = availableValue;
			}

			/** Creates context with a simple value */
			public Default() {
				this(SettableValue.create());
			}

			@Override
			public SettableValue<A> getAvailableValue() {
				return theAvailableValue;
			}
		}
	}

	private ModelValueInstantiator<ObservableCollection<A>> theAvailableInstantiator;
	private ModelValueInstantiator<ObservableCollection<I>> theIncludedInstantiator;
	private ModelValueInstantiator<SettableValue<I>> theIncludeInstantiator;

	private ModelComponentId theAvailableValueName;
	private ModelComponentId theAvailableRowsName;
	private ModelComponentId theIncludedRowsName;
	private String theItemName;

	private SettableValue<ObservableCollection<A>> theAvailableValues;
	private SettableValue<ObservableCollection<I>> theIncludedValues;
	private SettableValue<SettableValue<A>> theAvailableValue;
	private SettableValue<I> theInclude;
	private QuickSuperTable<A, ?> theAvailable;
	private QuickTable<I, ?> theIncluded;
	private QuickButton theIncludeAllConfig;
	private QuickButton theIncludeConfig;
	private QuickButton theExcludeConfig;
	private QuickButton theExcludeAllConfig;

	QuickValueSelector(Object elementId) {
		super(elementId);
		theAvailableValues = SettableValue.create();
		theIncludedValues = SettableValue.create();
		theAvailableValue = SettableValue.create();
	}

	/** @return The name of the variable in which the currently active available value will be available */
	public ModelComponentId getAvailableValueName() {
		return theAvailableValueName;
	}

	/** @return The included value corresponding the the available value in the current context */
	public SettableValue<I> getIncludeValue() {
		return theInclude;
	}

	/** @return The name for items in the table--may affect the text in some UI elements */
	public String getItemName() {
		return theItemName;
	}

	/** @return The available table */
	public QuickSuperTable<A, ?> getAvailable() {
		return theAvailable;
	}

	/** @return The included table */
	public QuickTable<I, ?> getIncluded() {
		return theIncluded;
	}

	/** @return Configuration for the button that causes all values in the available table to be transferred to the included table */
	public QuickButton getIncludeAllConfig() {
		return theIncludeAllConfig;
	}

	/**
	 * @return Configuration for the button that causes all selected values in the available table to be transferred to the included table
	 */
	public QuickButton getIncludeConfig() {
		return theIncludeConfig;
	}

	/** @return Configuration for the button that causes all selected values in the included table to be removed */
	public QuickButton getExcludeConfig() {
		return theExcludeConfig;
	}

	/** @return Configuration for the button that causes all values in the included table to be removed */
	public QuickButton getExcludeAllConfig() {
		return theExcludeAllConfig;
	}

	/** @param context Model context for this value selector */
	public void setValueSelectorContext(ValueSelectorContext<A, I> context) {
		theAvailableValue.set(context.getAvailableValue(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted<A, I> myInterpreted = (Interpreted<A, I>) interpreted;
		theAvailableInstantiator = myInterpreted.getAvailableValues().instantiate();
		theIncludedInstantiator = myInterpreted.getIncludedValues().instantiate();
		theIncludeInstantiator = myInterpreted.getInclude().instantiate();

		theAvailableRowsName = myInterpreted.getDefinition().getAvailableRowsName();
		theIncludedRowsName = myInterpreted.getDefinition().getIncludedRowsName();
		theAvailableValueName = myInterpreted.getDefinition().getAvailableValueName();
		theItemName = myInterpreted.getDefinition().getItemName();

		theAvailable = syncChild(myInterpreted.getAvailable(), theAvailable, QuickSuperTable.Interpreted::create, QuickSuperTable::update);
		theIncluded = syncChild(myInterpreted.getIncluded(), theIncluded, QuickTable.Interpreted::create, QuickTable::update);

		theIncludeAllConfig = syncChild(myInterpreted.getIncludeAllConfig(), theIncludeAllConfig, QuickButton.Interpreted::create,
			QuickButton::update);
		theIncludeConfig = syncChild(myInterpreted.getIncludeConfig(), theIncludeConfig, QuickButton.Interpreted::create,
			QuickButton::update);
		theExcludeConfig = syncChild(myInterpreted.getExcludeConfig(), theExcludeConfig, QuickButton.Interpreted::create,
			QuickButton::update);
		theExcludeAllConfig = syncChild(myInterpreted.getExcludeAllConfig(), theExcludeAllConfig, QuickButton.Interpreted::create,
			QuickButton::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		theAvailableInstantiator.instantiate();
		theIncludedInstantiator.instantiate();
		theIncludeInstantiator.instantiate();
		theAvailable.instantiated();
		theIncluded.instantiated();
		if (theIncludeAllConfig != null)
			theIncludeAllConfig.instantiated();
		if (theIncludeConfig != null)
			theIncludeConfig.instantiated();
		if (theExcludeConfig != null)
			theExcludeConfig.instantiated();
		if (theExcludeAllConfig != null)
			theExcludeAllConfig.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		ExFlexibleElementModelAddOn.satisfyElementValue(theAvailableRowsName, myModels,
			ObservableCollection.flattenValue(theAvailableValues));
		ExFlexibleElementModelAddOn.satisfyElementValue(theIncludedRowsName, myModels,
			ObservableCollection.flattenValue(theIncludedValues));
		ExFlexibleElementModelAddOn.satisfyElementValue(theAvailableValueName, myModels, SettableValue.flatten(theAvailableValue));

		theAvailableValues.set(theAvailableInstantiator.get(myModels), null);
		theIncludedValues.set(theIncludedInstantiator.get(myModels), null);
		theInclude = theIncludeInstantiator.get(myModels);
		theAvailable.instantiate(myModels);
		theIncluded.instantiate(myModels);

		if (theIncludeAllConfig != null)
			theIncludeAllConfig.instantiate(myModels);
		if (theIncludeConfig != null)
			theIncludeConfig.instantiate(myModels);
		if (theExcludeConfig != null)
			theExcludeConfig.instantiate(myModels);
		if (theExcludeAllConfig != null)
			theExcludeAllConfig.instantiate(myModels);
		return myModels;
	}

	@Override
	public QuickValueSelector<A, I> copy(ExElement parent) {
		QuickValueSelector<A, I> copy = (QuickValueSelector<A, I>) super.copy(parent);

		copy.theAvailableValues = SettableValue.create();
		copy.theIncludedValues = SettableValue.create();
		copy.theAvailableValue = SettableValue.create();
		copy.theAvailable = theAvailable.copy(copy);
		copy.theIncluded = theIncluded.copy(copy);
		copy.theIncludeAllConfig = theIncludeAllConfig == null ? null : theIncludeAllConfig.copy(copy);
		copy.theIncludeConfig = theIncludeConfig == null ? null : theIncludeConfig.copy(copy);
		copy.theExcludeConfig = theExcludeConfig == null ? null : theExcludeConfig.copy(copy);
		copy.theExcludeAllConfig = theExcludeAllConfig == null ? null : theExcludeAllConfig.copy(copy);

		return copy;
	}

	@Override
	public void destroy() {
		super.destroy();
		theAvailable.destroy();
		theIncluded.destroy();
		if (theIncludeAllConfig != null)
			theIncludeAllConfig.destroy();
		if (theIncludeConfig != null)
			theIncludeConfig.destroy();
		if (theExcludeConfig != null)
			theExcludeConfig.destroy();
		if (theExcludeAllConfig != null)
			theExcludeAllConfig.destroy();
	}
}
