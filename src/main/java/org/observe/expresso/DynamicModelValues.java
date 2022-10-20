package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigInterpretationException;

/** Supports the satisfaction of interpreted-value-specific model values declared with metadata under a with-element-model implementation */
public class DynamicModelValues {
	/**
	 * Called by the Expresso interpreter to declare a dynamic (interpreted-value-specific) model value
	 *
	 * @param <M> The model type of the value
	 * @param <MV> The type of the value
	 * @param name The name of the value
	 * @param type The type of the value
	 * @return The {@link ValueContainer} to use for {@link ObservableModelSet.WrappedBuilder#with(String, ValueContainer)} to declare the
	 *         dynamic value in the model
	 */
	public static <M, MV extends M> ValueContainer<M, MV> declareDynamicValue(String name, ModelInstanceType<M, MV> type) {
		return new DynamicModelValue<>(name, type);
	}

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
			model.getModel().get(name, type);
		} catch (QonfigInterpretationException e) {
			throw new IllegalArgumentException("No such dynamic model value: " + model.getModel().getPath() + "." + name);
		}
		MV dynamicValue = model.get(name, type);
		if (!(dynamicValue instanceof ModelType.HollowModelValue))
			throw new IllegalArgumentException("No such dynamic model value: " + model.getModel().getPath() + "." + name);
		ModelType.HollowModelValue<M, MV> hollow = (ModelType.HollowModelValue<M, MV>) model.get(name, type);
		hollow.satisfy(value);
	}

	private static class DynamicModelValue<M, MV extends M> implements ValueContainer<M, MV> {
		private final String theName;
		private final ModelInstanceType<M, MV> theType;

		DynamicModelValue(String name, ModelInstanceType<M, MV> type) {
			theName = name;
			theType = type;
		}

		@Override
		public ModelInstanceType<M, MV> getType() {
			return theType;
		}

		@Override
		public MV get(ModelSetInstance models) {
			return (MV) theType.getModelType().createHollowValue(theName, theType);
		}
	}
}
