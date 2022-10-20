package org.observe.expresso;

import java.util.Objects;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
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
public class DynamicModelValue<M, MV extends M> implements ValueContainer<M, MV>, Named {
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
	public DynamicModelValue(String name, ModelInstanceType<M, MV> type, QonfigElement declaration) {
		theName = name;
		theType = type;
		theDeclaration = declaration;
	}

	@Override
	public String getName() {
		return theName;
	}

	/** @return The metadata that is the declaration for this dynamic value */
	public QonfigElement getDeclaration() {
		return theDeclaration;
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
		return obj instanceof DynamicModelValue && theDeclaration.equals(((DynamicModelValue<?, ?>) obj).theDeclaration)
			&& theType.equals(((DynamicModelValue<?, ?>) obj).theType);
	}

	@Override
	public String toString() {
		return theDeclaration.toString();
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
}