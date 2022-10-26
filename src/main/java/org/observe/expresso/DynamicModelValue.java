package org.observe.expresso;

import java.util.Objects;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.util.TypeTokens;
import org.qommons.Named;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

/**
 * A dynamic (interpreted-value-specific) model value
 *
 * @param <M> The model type of the value
 * @param <MV> The type of the value
 */
public interface DynamicModelValue<M, MV extends M> extends ValueContainer<M, MV>, Named {
	/** @return The metadata that is the declaration for this dynamic value */
	public QonfigElement getDeclaration();

	public ModelInstanceType<M, MV> getDeclaredType();

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoQIS#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param value The value to use to satisfy the model value
	 */
	public static <M, MV extends M> void satisfyDynamicValue(String name, ModelInstanceType<M, MV> type, ModelSetInstance model, MV value) {
		try { // Check for the value rigorously
			model.getModel().getValue(name, type);
		} catch (QonfigInterpretationException e) {
			throw new IllegalArgumentException("No such dynamic model value: " + model.getModel().getIdentity() + "." + name);
		}
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = model.getModel().getComponentIfExists(name);
		MV dynamicValue = (MV) model.get(modelValue);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Dynamic model value " + model.getModel().getIdentity() + "." + name
				+ " is not a runtime model value. Satisfy with satisfyDynamicTypedValue(...).");
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) dynamicValue;
		hollow.satisfy(value);
	}

	/**
	 * Checks if the given dynamic value is satisfied
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @param model The model instance the value may be satisfied in
	 * @return Whether the given dynamic value has already been satisfied with
	 *         {@link #satisfyDynamicValue(String, ModelInstanceType, ModelSetInstance, Object)}
	 */
	public static <M, MV extends M> boolean isDynamicValueSatisfied(String name, ModelInstanceType<M, MV> type, ModelSetInstance model) {
		try { // Check for the value rigorously
			model.getModel().getValue(name, type);
		} catch (QonfigInterpretationException e) {
			throw new IllegalArgumentException("No such dynamic model value: " + model.getModel().getIdentity() + "." + name);
		}
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = model.getModel().getComponentIfExists(name);
		MV dynamicValue = (MV) model.get(modelValue);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Dynamic model value " + model.getModel().getIdentity() + "." + name
				+ " is not a runtime model value. Satisfy with satisfyDynamicTypedValue(...).");
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) dynamicValue;
		return hollow.isSatisfied();
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value, if it is not already
	 * satisfied.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoQIS#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param value The value to use to satisfy the model value
	 */
	public static <M, MV extends M> void satisfyDynamicValueIfUnsatisfied(String name, ModelInstanceType<M, MV> type,
		ModelSetInstance model, MV value) {
		try { // Check for the value rigorously
			model.getModel().getValue(name, type);
		} catch (QonfigInterpretationException e) {
			throw new IllegalArgumentException("No such dynamic model value: " + model.getModel().getIdentity() + "." + name);
		}
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = model.getModel().getComponentIfExists(name);
		MV dynamicValue = (MV) model.get(modelValue);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("Dynamic model value " + model.getModel().getIdentity() + "." + name
				+ " is not a runtime model value. Satisfy with satisfyDynamicTypedValue(...).");
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) dynamicValue;
		if (!hollow.isSatisfied())
			hollow.satisfy(value);
	}

	/**
	 * @param name The name of the dynamic model value
	 * @param models The model set that the dynamic model value is installed in
	 * @return The component node containing the {@link DynamicModelValue} with the given name
	 */
	public static ObservableModelSet.ModelComponentNode<?, ?> getDynamicValueComponent(String name, ObservableModelSet models) {
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = models.getComponentIfExists(name);
		if (modelValue == null)
			throw new IllegalArgumentException("No such dynamic model value: " + models.getIdentity() + "." + name);
		else if (!(modelValue.getThing() instanceof DynamicTypedModelValueCreator))
			throw new IllegalArgumentException(models.getIdentity() + "." + name + " is not a dynamic model value");
		return modelValue;
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoQIS#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param satisfier The value creator to use to satisfy the model value
	 * @throws IllegalStateException If the value has already been satisfied for the given model instance
	 */
	public static <M, MV extends M> void satisfyDynamicValue(String name, ObservableModelSet model, ValueCreator<M, MV> satisfier)
		throws IllegalStateException {
		DynamicTypedModelValueCreator.satisfyDynamicValue(name, model, satisfier, false);
	}

	/**
	 * Called by some implementation to satisfy a metadata-declared dynamic (interpreted-value-specific) model value, if it is not already
	 * satisfied.
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param model The model instance to satisfy the value in (should be {@link ExpressoQIS#wrapLocal(ModelSetInstance) wrapLocal}'ed)
	 * @param satisfier The value creator to use to satisfy the model value
	 * @return Whether the value was newly satisfied as a result of this call
	 */
	public static <M, MV extends M> boolean satisfyDynamicValueIfUnsatisfied(String name, ObservableModelSet model,
		ValueCreator<M, MV> satisfier) {
		return DynamicTypedModelValueCreator.satisfyDynamicValue(name, model, satisfier, true);
	}

	public class RuntimeModelValue<M, MV extends M> implements DynamicModelValue<M, MV> {
		private final String theName;
		private final ModelInstanceType<M, MV> theType;
		private final QonfigElement theDeclaration;

		/**
		 * Called by the Expresso interpreter to declare a dynamic (interpreted-value-specific) model value
		 *
		 * @param name The name of the value
		 * @param type The type of the value
		 * @param declaration The metadata element that is the declaration for the dynamic value
		 */
		public RuntimeModelValue(String name, ModelInstanceType<M, MV> type, QonfigElement declaration) {
			theName = name;
			theType = type;
			theDeclaration = declaration;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public QonfigElement getDeclaration() {
			return theDeclaration;
		}

		@Override
		public ModelInstanceType<M, MV> getDeclaredType() {
			return theType;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}

		@Override
		public MV get(ModelSetInstance models) {
			return (MV) theType.getModelType().createHollowValue(theName, theType);
		}

		@Override
		public BetterList<ValueContainer<?, ?>> getCores() {
			return BetterList.of(this);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theDeclaration, theType);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof DynamicModelValue && theDeclaration.equals(((DynamicModelValue<?, ?>) obj).getDeclaration())
				&& theType.equals(((DynamicModelValue<?, ?>) obj).getType());
		}

		@Override
		public String toString() {
			return theDeclaration.toString();
		}
	}

	public class DynamicContainerWrapper<M, MV extends M> implements DynamicModelValue<M, MV> {
		private final DynamicTypedModelValueCreator<M, MV> theCreator;
		private ValueContainer<M, MV> theContainer;

		public DynamicContainerWrapper(DynamicTypedModelValueCreator<M, MV> creator) {
			theCreator = creator;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			if (theContainer == null)
				theContainer = theCreator.createDynamicContainer();
			return theContainer.getType();
		}

		@Override
		public MV get(ModelSetInstance models) {
			if (theContainer == null)
				theContainer = theCreator.createDynamicContainer();
			return theContainer.get(models);
		}

		@Override
		public BetterList<ValueContainer<?, ?>> getCores() {
			return BetterList.of(this);
		}

		@Override
		public String getName() {
			return theCreator.getName();
		}

		@Override
		public QonfigElement getDeclaration() {
			return theCreator.getDeclaration();
		}

		@Override
		public ModelInstanceType<M, MV> getDeclaredType() {
			return theCreator.getDeclaredType();
		}

		public DynamicTypedModelValueCreator<M, MV> getCreator() {
			return theCreator;
		}
	}

	/**
	 * A dynamic-typed value whose type is not completely specified and must be
	 * {@link #satisfyDynamicValue(String, ObservableModelSet, ValueCreator) satisfied} before it is {@link ValueCreator#createValue() used}
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value, as far as it is known
	 */
	public class DynamicTypedModelValueCreator<M, MV extends M> implements ValueCreator<M, MV>, Named {
		private final String theName;
		private final ModelInstanceType<M, MV> theDeclaredType;
		private final QonfigElement theDeclaration;

		private ValueCreator<M, MV> theSatisfier;

		/**
		 * @param name The name of the dynamic value
		 * @param declaredType The type of the value, as far as it is known
		 * @param declaration The metadata element that is the declaration for the dynamic value
		 */
		public DynamicTypedModelValueCreator(String name, ModelInstanceType<M, MV> declaredType, QonfigElement declaration) {
			theName = name;
			theDeclaredType = declaredType;
			theDeclaration = declaration;
		}

		@Override
		public String getName() {
			return theName;
		}

		public ModelInstanceType<M, MV> getDeclaredType() {
			return theDeclaredType;
		}

		public QonfigElement getDeclaration() {
			return theDeclaration;
		}

		@Override
		public ValueContainer<M, MV> createValue() {
			return new DynamicContainerWrapper<>(this);
		}

		ValueContainer<M, MV> createDynamicContainer() {
			if (theSatisfier == null)
				throw new IllegalStateException("Dynamic model value " + theName + " requested but not yet satisfied");
			ValueContainer<M, MV> container = theSatisfier.createValue();
			if (!getDeclaredType().getModelType().equals(container.getType().getModelType()))
				throw new IllegalStateException(
					"Dynamic model value " + theName + "(" + getDeclaredType() + ") satisfied with " + container.getType());
			for (int t = 0; t < getDeclaredType().getModelType().getTypeCount(); t++) {
				if (!TypeTokens.get().isAssignable(getDeclaredType().getType(t), container.getType().getType(t)))
					throw new IllegalStateException(
						"Dynamic model value " + theName + "(" + getDeclaredType() + ") satisfied with " + container.getType());
			}
			return container;
		}

		static <M, MV extends M> boolean satisfyDynamicValue(String name, ObservableModelSet model, ValueCreator<M, MV> satisfier,
			boolean ignoreIfSatified) {
			ObservableModelSet.ModelComponentNode<?, ?> modelValue = getDynamicValueComponent(name, model);
			DynamicTypedModelValueCreator<M, MV> dmv = (DynamicTypedModelValueCreator<M, MV>) modelValue.getThing();
			if (dmv.theSatisfier == null) {
				dmv.theSatisfier = satisfier;
				return true;
			} else if (ignoreIfSatified || dmv.theSatisfier == satisfier)
				return false;
			else
				throw new IllegalStateException("Dynamic model value " + name + " has already been satisfied");
		}
	}
}