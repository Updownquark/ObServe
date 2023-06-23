package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;

/**
 * Provided to
 * {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(org.qommons.config.QonfigElementOrAddOn, Class, QonfigValueCreator)}
 * to support using an element type as a child of an &lt;ext-model> element.
 *
 * @param <M> The model type of the result
 */
public interface ExtModelValue<M> {
	/** @return The model type of this model value */
	ModelType<M> getModelType();

	/**
	 * @param models The models to use to evaluate the type
	 * @return The type of this model value
	 * @throws ExpressoInterpretationException If the type cannot be evaluated
	 */
	ModelInstanceType<M, ?> getType(ObservableModelSet models) throws ExpressoInterpretationException;

	/** @return Whether the type of this value specification has been fully specified */
	boolean isTypeSpecified();
}