package org.observe.expresso;

import org.observe.config.ObservableConfig;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;

import com.google.common.reflect.TypeToken;

/** A simple structure consisting of a class view and models, the definition for a set of models for an appliction */
public class Expresso {
	/**
	 * Provided to
	 * {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(org.qommons.config.QonfigElementOrAddOn, Class, QonfigValueCreator)}
	 * to support using an element type as a child of an &lt;ext-model> element.
	 *
	 * @param <M> The model type of the result
	 */
	public interface ExtModelValue<M> {
		/**
		 * @param session The session to use to evaluate the type
		 * @return The type of this model value
		 * @throws QonfigInterpretationException If the type cannot be evaluated
		 */
		ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException;

		/**
		 * An {@link ExtModelValue} with a single parameter type
		 *
		 * @param <M> The model type
		 */
		public class SingleTyped<M> implements ExtModelValue<M> {
			private final ModelType.SingleTyped<M> theType;

			/** @param type The model type of this value */
			public SingleTyped(ModelType.SingleTyped<M> type) {
				theType = type;
			}

			/** @return The model type of this value */
			public ModelType.SingleTyped<M> getType() {
				return theType;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException {
				return theType.forType((TypeToken<?>) session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY));
			}
		}

		/**
		 * An {@link ExtModelValue} with 2 parameter types
		 *
		 * @param <M> The model type
		 */
		class DoubleTyped<M> implements ExtModelValue<M> {
			private final ModelType.DoubleTyped<M> theType;

			/** @param type The model type of this value */
			public DoubleTyped(ModelType.DoubleTyped<M> type) {
				theType = type;
			}

			/** @return The model type of this value */
			public ModelType.DoubleTyped<M> getType() {
				return theType;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException {
				return theType.forType(//
					(TypeToken<?>) session.get(ExpressoBaseV0_1.KEY_TYPE_KEY), //
					(TypeToken<?>) session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY, TypeToken.class));
			}
		}
	}

	/**
	 * Provided to
	 * {@link org.qommons.config.QonfigInterpreterCore.Builder#createWith(org.qommons.config.QonfigElementOrAddOn, Class, QonfigValueCreator)}
	 * to support using an element type as a child of a &lt;config-model> element.
	 *
	 * @param <M> The model type of the result
	 * @param <MV> The value type of the result
	 */
	public interface ConfigModelValue<M, MV extends M> {
		/** @return The type of this value */
		ModelInstanceType<M, MV> getType();

		/**
		 * Creates the value
		 * 
		 * @param config The config value builder to use to build the structure
		 * @param msi The model set to use to build the structure
		 * @return The created value
		 */
		MV create(ObservableConfig.ObservableConfigValueBuilder<?> config, ModelSetInstance msi);
	}

	private final ClassView theClassView;
	private final ObservableModelSet theModels;

	/**
	 * @param classView The class view for this expresso structure
	 * @param models The models for this expresso structure
	 */
	public Expresso(ClassView classView, ObservableModelSet models) {
		theClassView = classView;
		theModels = models;
	}

	/** @return The class view of this expresso structure */
	public ClassView getClassView() {
		return theClassView;
	}

	/** @return The models of this expresso structure */
	public ObservableModelSet getModels() {
		return theModels;
	}
}
