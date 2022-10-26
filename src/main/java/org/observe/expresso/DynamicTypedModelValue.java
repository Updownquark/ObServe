package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;

public class DynamicTypedModelValue<M, MV extends M> implements ValueCreator<M, MV> {
	private final String theName;
	private final ModelInstanceType<M, MV> theDeclaredType;
	private final QonfigElement theDeclaration;

	private ValueCreator<M, MV> theSatisfier;

	public DynamicTypedModelValue(String name, ModelInstanceType<M, MV> declaredType, QonfigElement declaration) {
		theName = name;
		theDeclaredType = declaredType;
		theDeclaration = declaration;
	}

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

	/**
	 * @param name The name of the dynamic model value
	 * @param models The model set that the dynamic model value is installed in
	 * @return The component node containing the {@link DynamicModelValue} with the given name
	 */
	public static ObservableModelSet.ModelComponentNode<?, ?> getDynamicValueComponent(String name, ObservableModelSet models) {
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = models.getComponentIfExists(name);
		if (modelValue == null)
			throw new IllegalArgumentException("No such dynamic model value: " + models.getIdentity() + "." + name);
		else if (!(modelValue.getThing() instanceof DynamicTypedModelValue))
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
	public static <M, MV extends M> void satisfyDynamicValue(String name, ObservableModelSet model,
		ValueCreator<M, MV> satisfier) throws IllegalStateException {
		satisfyDynamicValue(name, model, satisfier, false);
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
		return satisfyDynamicValue(name, model, satisfier, true);
	}

	private static <M, MV extends M> boolean satisfyDynamicValue(String name, ObservableModelSet model,
		ValueCreator<M, MV> satisfier, boolean ignoreIfSatified) {
		ObservableModelSet.ModelComponentNode<?, ?> modelValue = getDynamicValueComponent(name, model);
		DynamicTypedModelValue<M, MV> dmv = (DynamicTypedModelValue<M, MV>) modelValue.getThing();
		if (dmv.theSatisfier == null) {
			dmv.theSatisfier = satisfier;
			return true;
		} else if (ignoreIfSatified || dmv.theSatisfier == satisfier)
			return false;
		else
			throw new IllegalStateException("Dynamic model value " + name + " has already been satisfied");
	}
}
