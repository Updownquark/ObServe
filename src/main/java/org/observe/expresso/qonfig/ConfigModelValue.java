package org.observe.expresso.qonfig;

import org.observe.config.ObservableConfig;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;

/**
 * Provided to
 * {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(org.qommons.config.QonfigElementOrAddOn, Class, QonfigValueCreator)}
 * to support using an element type as a child of a &lt;config-model> element.
 *
 * @param <T> The element type of the result
 * @param <M> The model type of the result
 * @param <MV> The value type of the result
 */
public interface ConfigModelValue<T, M, MV extends M> {
	/**
	 * Called to interpret any expressions needed for the value
	 *
	 * @throws ExpressoInterpretationException If an error occurs parsing the the value
	 */
	void init() throws ExpressoInterpretationException;

	/** @return The type of this value */
	ModelInstanceType<M, MV> getType();

	/**
	 * Creates the value
	 *
	 * @param config The config value builder to use to build the structure
	 * @param msi The model set to use to build the structure
	 * @return The created value
	 * @throws ModelInstantiationException If the value could not be instantiated
	 */
	MV create(ObservableConfig.ObservableConfigValueBuilder<T> config, ModelSetInstance msi) throws ModelInstantiationException;
}