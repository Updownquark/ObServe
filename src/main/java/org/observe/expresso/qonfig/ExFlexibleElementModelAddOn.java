package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.HollowModelValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretableModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExBiFunction;
import org.qommons.io.LocatedFilePosition;

import com.google.common.reflect.TypeToken;

/**
 * This abstract add-on implementation provides utility for injecting new model data into the model view of the tagged element.
 *
 * @param <E> The type of element this add-on applies to
 */
public abstract class ExFlexibleElementModelAddOn<E extends ExElement> extends ExModelAugmentation<E> {
	/**
	 * Satisfies a dynamic model value in a model instance
	 *
	 * @param elementValueId The model ID of the dynamic model value to satisfy
	 * @param models The model instance to satisfy the value in
	 * @param value The value to satisfy the model value with
	 * @throws ModelInstantiationException If no such element was injected from this add-on, or the injected value was not dynamically-typed
	 */
	public static void satisfyElementValue(ModelComponentId elementValueId, ModelSetInstance models, Object value)
		throws ModelInstantiationException {
		satisfyElementValue(elementValueId, models, value, ActionIfSatisfied.Replace);
	}

	/**
	 * Satisfies a dynamic model value in a model instance
	 *
	 * @param elementValueId The model ID of the dynamic model value to satisfy
	 * @param models The model instance to satisfy the value in
	 * @param value The value to satisfy the model value with
	 * @param ifPreSatisfied The action to take if the value is already satisfied in the model
	 * @throws ModelInstantiationException If no such element was injected from this add-on, the value is not dynamic, or the value is
	 *         already satisfied and <code>ifPreSatisfied=={@link ActionIfSatisfied#Error Error}</code>
	 */
	public static void satisfyElementValue(ModelComponentId elementValueId, ModelSetInstance models, Object value,
		ActionIfSatisfied ifPreSatisfied) throws ModelInstantiationException {
		Object modelValue = models.get(elementValueId);
		if (!(modelValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Element value '" + elementValueId + "' is not dynamic");
		ModelType.HollowModelValue<Object, Object> hollow = (ModelType.HollowModelValue<Object, Object>) modelValue;
		if (hollow.isSatisfied()) {
			switch (ifPreSatisfied) {
			case Error:
				throw new IllegalArgumentException("Element value '" + elementValueId + "' is already satisfied");
			case Ignore:
				return;
			case Replace:
				break;
			}
		}
		hollow.satisfy(value);
	}

	/**
	 * @param elementValueId The model ID of the dynamic model value
	 * @param models The model instance to check the value in
	 * @return Whether the given dynamic value has been satisfied
	 * @throws ModelInstantiationException If no such element was injected from this add-on, or the injected value was not dynamically-typed
	 */
	public static boolean isElementValueSatisfied(ModelComponentId elementValueId, ModelSetInstance models)
		throws ModelInstantiationException {
		Object modelValue = models.get(elementValueId);
		if (!(modelValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Element value '" + elementValueId + "' is not dynamic");
		ModelType.HollowModelValue<Object, Object> hollow = (ModelType.HollowModelValue<Object, Object>) modelValue;
		return hollow.isSatisfied();
	}

	/**
	 * {@link ExFlexibleElementModelAddOn} definition
	 *
	 * @param <E> The type of element this add-on applies to
	 * @param <AO> The type of add-on to create
	 */
	public static abstract class Def<E extends ExElement, AO extends ExFlexibleElementModelAddOn<? super E>>
	extends ExModelAugmentation.Def<E, AO> {
		private Map<String, CompiledModelValue<?>> theElementValues;
		private volatile ExElement.Interpreted<? extends E> theCurrentInterpreting;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element whose model view to enhance
		 */
		protected Def(QonfigAddOn type, ExElement.Def<? extends E> element) {
			super(type, element);
			theElementValues = Collections.emptyMap();
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends E> element) throws QonfigInterpretationException {
			if (!theElementValues.isEmpty())
				theElementValues.clear();
			super.update(session, element);
		}

		/** @return Additional model values injected by this add on, by name */
		public Map<String, ? extends CompiledModelValue<?>> getElementValues() {
			return Collections.unmodifiableMap(theElementValues);
		}

		/**
		 * Injects a model value into the tagged element's model view
		 *
		 * @param <M> The model type of the value to inject
		 * @param name The name of the variable by which the injected model value will be available in expressions on the target element
		 * @param value The model value to inject
		 * @param builder The model builder in which to inject the value
		 * @param position The file position where the value was defined, for tracing
		 * @return The model node of the injected value
		 * @throws QonfigInterpretationException If the model value could not be injected
		 */
		protected <M> ModelComponentNode<M> addElementValue(String name, CompiledModelValue<M> value, ObservableModelSet.Builder builder,
			LocatedFilePosition position) throws QonfigInterpretationException {
			try {
				builder.getNameChecker().checkName(name);
			} catch (IllegalArgumentException e) {
				throw new QonfigInterpretationException("Illegal variable name: '" + name + "'", position, 0, e);
			}
			if (theElementValues.isEmpty())
				theElementValues = new LinkedHashMap<>();
			CompiledModelValue<?> prev = getElementValues().get(name);
			if (prev != null)
				throw new QonfigInterpretationException("Multiple conflicting element values named '" + name + "' declared", position, 0);
			ModelComponentNode<M> component;
			builder.withMaker(name, value, position);
			component = (ModelComponentNode<M>) builder.getLocalComponent(name);
			theElementValues.put(component.getIdentity().getPath(), value);
			return component;
		}

		/**
		 * @param elementValueName The name by which the model value should be available to expressions
		 * @return The
		 *         {@link #addElementValue(String, CompiledModelValue, org.observe.expresso.ObservableModelSet.Builder, LocatedFilePosition)
		 *         injected} model value
		 * @throws QonfigInterpretationException If no such model value is available from this add-on
		 */
		protected CompiledModelValue<?> getElementValue(String elementValueName) throws QonfigInterpretationException {
			CompiledModelValue<?> value = theElementValues.get(elementValueName);
			if (value == null)
				throw new QonfigInterpretationException("No such element value '" + elementValueName + "'",
					getElement().reporting().getPosition(), 0);
			return value;
		}

		/**
		 * Satisfies the type of an injected model value. The model value passed to
		 * {@link #addElementValue(String, CompiledModelValue, org.observe.expresso.ObservableModelSet.Builder, LocatedFilePosition)
		 * addElementValue()} may not have the information it needs to ascertain its type. This method provides implementations a means for
		 * determining type with the interpreted element available.
		 *
		 * @param <I> The type of the interpreted element
		 * @param <M> The model type of the model value
		 * @param elementValueId The model ID of the model value to satisfy the type of
		 * @param modelType The model type of the model value
		 * @param type A function to evaluate the type for the model value given the interpreted element and its expresso environment
		 * @throws QonfigInterpretationException If no such element was injected from this add-on, or the injected value was not
		 *         dynamically-typed
		 */
		protected <I extends ExElement.Interpreted<?>, M> void satisfyElementValueType(ModelComponentId elementValueId,
			ModelType<M> modelType,
			ExBiFunction<I, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> type)
				throws QonfigInterpretationException {
			ModelComponentNode<?> value = getElement().getExpressoEnv().getModels().getComponent(elementValueId);
			if (!(value.getThing() instanceof PlaceholderModelValue))
				throw new QonfigInterpretationException("Element value '" + elementValueId + "' is not dynamically-typed",
					getElement().reporting().getPosition(), 0);
			try {
				if (value.getModelType(null) != modelType)
					throw new QonfigInterpretationException(
						"Element value '" + elementValueId + "' is not a " + value.getModelType(null) + ", not a " + modelType,
						getElement().reporting().getPosition(), 0);
			} catch (ExpressoCompilationException e) {
				throw new QonfigInterpretationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			}
			((PlaceholderModelValue<M>) value.getThing()).satisfyType(this::getCurrentInterpreting,
				(ExBiFunction<ExElement.Interpreted<?>, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException>) type);
		}

		/**
		 * Satisfies the type of an injected model value. The model value passed to
		 * {@link #addElementValue(String, CompiledModelValue, org.observe.expresso.ObservableModelSet.Builder, LocatedFilePosition)
		 * addElementValue()} may not have the information it needs to ascertain its type. This method provides implementations a means for
		 * satisfying the type after the value is declared.
		 *
		 * @param <I> The type of the interpreted element
		 * @param <M> The model type of the model value
		 * @param elementValueId The model ID of the model value to satisfy the type of
		 * @param type The type for the model value
		 * @throws QonfigInterpretationException If no such element was injected from this add-on, or the injected value was not
		 *         dynamically-typed
		 */
		protected <I extends ExElement.Interpreted<?>, M> void satisfyElementValueType(ModelComponentId elementValueId,
			ModelInstanceType<M, ?> type) throws QonfigInterpretationException {
			satisfyElementValueType(elementValueId, type.getModelType(), (interp, env) -> type);
		}

		@Override
		public abstract Interpreted<? extends E, ? extends AO> interpret(ExElement.Interpreted<?> element);

		void setCurrentInterpreting(ExElement.Interpreted<? extends E> interpreting) {
			theCurrentInterpreting = interpreting;
		}

		ExElement.Interpreted<? extends E> getCurrentInterpreting() {
			return theCurrentInterpreting;
		}
	}

	/**
	 * {@link ExFlexibleElementModelAddOn} interpretation
	 *
	 * @param <E> The type of element this add-on applies to
	 * @param <AO> The type of add-on to create
	 */
	public static abstract class Interpreted<E extends ExElement, AO extends ExFlexibleElementModelAddOn<? super E>>
	extends ExAddOn.Interpreted.Abstract<E, AO> {
		private final Map<String, InterpretedModelComponentNode<?, ?>> theElementValues;

		/**
		 * @param definition The definition to interpret
		 * @param element The element whose model view to enhance
		 */
		protected Interpreted(Def<E, ? super AO> definition, ExElement.Interpreted<? extends E> element) {
			super(definition, element);
			if (definition.getElementValues().isEmpty())
				theElementValues = Collections.emptyMap();
			else
				theElementValues = new LinkedHashMap<>();
		}

		@Override
		public Def<E, ? super AO> getDefinition() {
			return (Def<E, ? super AO>) super.getDefinition();
		}

		/** @return Additional model values injected by this add on, by name */
		public Map<String, InterpretedModelComponentNode<?, ?>> getElementValues() {
			return Collections.unmodifiableMap(theElementValues);
		}

		@Override
		public void preUpdate(ExElement.Interpreted<? extends E> element) {
			getDefinition().setCurrentInterpreting(element);
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends E> element) throws ExpressoInterpretationException {
			for (Map.Entry<String, ? extends CompiledModelValue<?>> elementValue : getDefinition().getElementValues().entrySet()) {
				if (theElementValues.containsKey(elementValue.getKey()))
					continue;
				InterpretableModelComponentNode<?> node = getElement().getExpressoEnv().getModels()
					.getComponentIfExists(elementValue.getKey(), false);
				theElementValues.put(elementValue.getKey(), node.interpreted());
			}
			getDefinition().setCurrentInterpreting(null);
		}

		/**
		 * @param elementValueName The name by which the model value should be available to expressions
		 * @return The
		 *         {@link Def#addElementValue(String, CompiledModelValue, org.observe.expresso.ObservableModelSet.Builder, LocatedFilePosition)
		 *         injected} model value
		 * @throws ExpressoInterpretationException If no such model value is available from this add-on
		 */
		protected InterpretedModelComponentNode<?, ?> getElementValue(String elementValueName) throws ExpressoInterpretationException {
			InterpretedModelComponentNode<?, ?> value = theElementValues.get(elementValueName);
			if (value == null) {
				CompiledModelValue<?> defValue = getDefinition().getElementValues().get(elementValueName);
				if (defValue == null)
					throw new ExpressoInterpretationException("No such element value '" + elementValueName + "'",
						getElement().reporting().getPosition(), 0);
				InterpretableModelComponentNode<?> node = getElement().getExpressoEnv().getModels().getComponentIfExists(elementValueName,
					false);
				value = node.interpreted();
				theElementValues.put(elementValueName, value);
			}
			return value;
		}

		/**
		 * Satisfies the value of an injected model value
		 *
		 * @param <M> The model type of the value to satisfy
		 * @param <MV> The instance type of the value to satisfy
		 * @param elementValueName The name of the model value to satisfy the type of
		 * @param satisfier The implementation of the model value
		 * @throws ExpressoInterpretationException If no such model value is available from this add-on, or the value is not dynamic
		 */
		protected <M, MV extends M> void satisfyElementValue(String elementValueName, InterpretedValueSynth<M, MV> satisfier)
			throws ExpressoInterpretationException {
			InterpretedModelComponentNode<?, ?> value = getElementValue(elementValueName);
			if (!(value.getValue() instanceof PlaceholderModelValue.Interpreted))
				throw new ExpressoInterpretationException("Element value '" + elementValueName + "' is not dynamic",
					getElement().reporting().getPosition(), 0);
			((PlaceholderModelValue.Interpreted<M, MV>) value.getValue()).satisfy(satisfier);
		}

		/**
		 * Satisfies the value of an injected model value in an instance of the models
		 *
		 * @param elementValueName The name of the model value to satisfy the type of
		 * @param models The model instance to satisfy the model value in
		 * @param value The value for the model
		 * @throws ModelInstantiationException If no such model value is available from this add on, or the value is not dynamic
		 */
		protected void satisfyElementValue(String elementValueName, ModelSetInstance models, Object value)
			throws ModelInstantiationException {
			satisfyElementValue(elementValueName, models, value, ActionIfSatisfied.Replace);
		}

		/**
		 * Satisfies the value of an injected model value in an instance of the models
		 *
		 * @param elementValueName The name of the model value to satisfy the type of
		 * @param models The model instance to satisfy the model value in
		 * @param value The value for the model
		 * @param ifPreSatisfied The action to take if the value is already satisfied in the model
		 * @throws ModelInstantiationException If no such model value is available from this add on, the value is not dynamic, or the value
		 *         is already satisfied and <code>ifPreSatisfied=={@link ActionIfSatisfied#Error Error}</code>
		 */
		protected void satisfyElementValue(String elementValueName, ModelSetInstance models, Object value, ActionIfSatisfied ifPreSatisfied)
			throws ModelInstantiationException {
			InterpretedModelComponentNode<?, ?> elementValue = theElementValues.get(elementValueName);
			if (elementValue == null)
				throw new ModelInstantiationException("No such element value '" + elementValueName + "'",
					getElement().reporting().getPosition(), 0);
			else if (!(elementValue.getValue() instanceof PlaceholderModelValue.Interpreted))
				throw new ModelInstantiationException("Element value '" + elementValueName + "' is not dynamic",
					getElement().reporting().getPosition(), 0);
			Object modelValue = models.get(elementValue.getIdentity());
			if (!(modelValue instanceof ModelType.HollowModelValue))
				throw new ModelInstantiationException("Element value '" + elementValueName + "' is not dynamic",
					getElement().reporting().getPosition(), 0);
			ModelType.HollowModelValue<Object, Object> hollow = (ModelType.HollowModelValue<Object, Object>) modelValue;
			if (hollow.isSatisfied()) {
				switch (ifPreSatisfied) {
				case Error:
					throw new ModelInstantiationException("Element value '" + elementValueName + "' is already satisfied",
						getElement().reporting().getPosition(), 0);
				case Ignore:
					return;
				case Replace:
					break;
				}
			}
			hollow.satisfy(value);
		}
	}

	/** An action to take when attempting to satisfy a dynamic model value, but it is already satisfied */
	public enum ActionIfSatisfied {
		/** Throw an exception */
		Error,
		/** Leave the pre-satisfied value and return */
		Ignore,
		/** Replace the satisfied value with the new one */
		Replace
	}

	/** @param element The element whose model view to enhance */
	protected ExFlexibleElementModelAddOn(E element) {
		super(element);
	}

	/**
	 * A {@link CompiledModelValue} implementation for dynamic model values--ones that are just placeholders whose implementations must be
	 * supplied by element or add-on implementations.
	 *
	 * @param <M> The model type of the value
	 */
	protected static abstract class PlaceholderModelValue<M> implements CompiledModelValue<M> {
		private final String theName;
		private Supplier<ExElement.Interpreted<?>> theInterpreting;
		private ExBiFunction<ExElement.Interpreted<?>, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> theType;

		/** @param name The name by which this value will be available to expressions */
		protected PlaceholderModelValue(String name) {
			theName = name;
		}

		/** @return The model type of this value */
		public abstract ModelType<M> getModelType();

		@Override
		public ModelType<M> getModelType(CompiledExpressoEnv env) {
			return getModelType();
		}

		void satisfyType(Supplier<ExElement.Interpreted<?>> interpreting,
			ExBiFunction<ExElement.Interpreted<?>, InterpretedExpressoEnv, ? extends ModelInstanceType<M, ?>, ExpressoInterpretationException> type) {
			theInterpreting = interpreting;
			theType = type;
		}

		/**
		 * @param <MV> The instance type of the value
		 * @param env The expresso environment for interpreting expressions
		 * @param defaultType The type to use for this model value if its type has not been satisfied
		 * @return The interpreted model value
		 * @throws ExpressoInterpretationException If the value could not be interpreted, or if it requires a type that was not satisfied
		 */
		protected <MV extends M> InterpretedValueSynth<M, MV> create(InterpretedExpressoEnv env, ModelInstanceType<M, MV> defaultType)
			throws ExpressoInterpretationException {
			ModelInstanceType<M, MV> type;
			if (theType != null) {
				ModelInstanceType<?, ?> targetType = theType.apply(theInterpreting.get(), env);
				for (int t = 0; t < getModelType().getTypeCount(); t++) {
					TypeToken<?> pt = targetType.getType(t);
					if (!TypeTokens.get().isAssignable(defaultType.getType(t), pt))
						throw new ExpressoInterpretationException(
							"Dynamic model value '" + this + "' (" + defaultType + ") satisfied with " + targetType,
							env.reporting().getPosition(), 0);
				}
				type = (ModelInstanceType<M, MV>) targetType;
			} else {
				boolean wildcard = false;
				for (int t = 0; !wildcard && t < getModelType().getTypeCount(); t++)
					wildcard = TypeTokens.get().isTrivialType(defaultType.getType(t).getType());
				if (wildcard)
					throw new ExpressoInterpretationException(
						"Type not specified for dynamic model value '" + this + "' (" + defaultType + ") before being needed",
						env.reporting().getPosition(), 0);
				type = defaultType;
			}
			return interpret(type);
		}

		/**
		 * @param <MV> The instance type of the value
		 * @param type The actual instance type for the value
		 * @return The interpreted model value
		 * @throws ExpressoInterpretationException If the model value could not be interpreted
		 */
		protected <MV extends M> Interpreted<M, MV> interpret(ModelInstanceType<M, MV> type) throws ExpressoInterpretationException {
			return new Interpreted<>(theName, type);
		}

		@Override
		public String toString() {
			return theName;
		}

		/**
		 * An {@link InterpretedValueSynth} implementation for dynamic model values--ones that are just placeholders whose implementations
		 * must be supplied by element or add-on implementations.
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The instance type of the value
		 */
		protected static class Interpreted<M, MV extends M> implements InterpretedValueSynth<M, MV> {
			private final String theName;
			private final ModelInstanceType<M, MV> theType;
			private InterpretedValueSynth<M, MV> theSatisfier;

			/**
			 * @param name The name by which this value will be available to expressions
			 * @param type The instance type of this value
			 */
			protected Interpreted(String name, ModelInstanceType<M, MV> type) {
				theName = name;
				theType = type;
			}

			void satisfy(InterpretedValueSynth<M, MV> satisfier) {
				ModelInstanceType<M, MV> satisfierType = satisfier.getType();
				if (satisfierType.getModelType() != theType.getModelType()) {
					throw new IllegalStateException("Dynamic model value '" + this + "' (" + theType + ") satisfied with " + satisfierType);
				} else {
					for (int t = 0; t < theType.getModelType().getTypeCount(); t++) {
						TypeToken<?> pt = satisfierType.getType(t);
						if (!TypeTokens.get().isAssignable(theType.getType(t), pt))
							throw new IllegalStateException(
								"Dynamic model value '" + this + "' (" + theType + ") satisfied with " + satisfierType);
					}
				}
				theSatisfier = satisfier;
			}

			@Override
			public ModelType<M> getModelType() {
				return theType.getModelType();
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				if (theSatisfier != null)
					return theSatisfier.getType();
				else
					return theType;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				if (theSatisfier != null)
					return Collections.singletonList(theSatisfier);
				else
					return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() throws ModelInstantiationException {
				if (theSatisfier != null)
					return theSatisfier.instantiate();
				else
					return new HollowValueInstantiator<>(theName, theType);
			}

			@Override
			public String toString() {
				return theName;
			}
		}

		/**
		 * A model instantiator for dynamic model values that must be satisfied by element or add-on instance implementations
		 *
		 * @param <M> The model type of the value
		 * @param <MV> The instance type of the value
		 */
		protected static class HollowValueInstantiator<M, MV extends M> implements ModelValueInstantiator<MV> {
			private final String theName;
			private final ModelInstanceType<M, MV> theType;

			/**
			 * @param name The name by which this value will be available to expressions
			 * @param type The instance type of this value
			 */
			protected HollowValueInstantiator(String name, ModelInstanceType<M, MV> type) {
				theName = name;
				theType = type;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public MV get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return (MV) theType.getModelType().createHollowValue(theName, theType);
			}

			@Override
			public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				HollowModelValue<M, MV> hollow = theType.getModelType().createHollowValue(theName, theType);
				hollow.satisfy(value);
				return (MV) hollow;
			}
		}
	}
}
