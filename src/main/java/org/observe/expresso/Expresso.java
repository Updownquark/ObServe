package org.observe.expresso;

import org.observe.config.ObservableConfig;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;

import com.google.common.reflect.TypeToken;

/** A simple structure consisting of a class view and models, the definition for a set of models for an application */
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

		/** @return Whether the type of this value specification has been fully specified */
		boolean isTypeSpecified();

		/**
		 * An {@link ExtModelValue} with a single parameter type
		 *
		 * @param <M> The model type
		 */
		public class SingleTyped<M> implements ExtModelValue<M> {
			private final ModelType.SingleTyped<M> theModelType;
			private final VariableType theValueType;

			/**
			 * @param modelType The model type of this value
			 * @param valueType The value type of this value
			 */
			public SingleTyped(ModelType.SingleTyped<M> modelType, VariableType valueType) {
				theModelType = modelType;
				theValueType = valueType;
			}

			/** @return The model type of this value */
			public ModelType.SingleTyped<M> getModelType() {
				return theModelType;
			}

			/** @return The value type of this value */
			public VariableType getValueType() {
				return theValueType;
			}

			@Override
			public boolean isTypeSpecified() {
				return theValueType != null;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException {
				if (theValueType != null)
					return theModelType.forType((TypeToken<?>) theValueType.getType(session.getExpressoEnv().getModels()));
				else
					return theModelType.any();
			}
		}

		/**
		 * An {@link ExtModelValue} with 2 parameter types
		 *
		 * @param <M> The model type
		 */
		class DoubleTyped<M> implements ExtModelValue<M> {
			private final ModelType.DoubleTyped<M> theModelType;
			private final VariableType theValueType1;
			private final VariableType theValueType2;

			/**
			 * @param type The model type of this value
			 * @param valueType1 The first value type of this value
			 * @param valueType2 The second value type of this value
			 */
			public DoubleTyped(ModelType.DoubleTyped<M> type, VariableType valueType1, VariableType valueType2) {
				theModelType = type;
				theValueType1 = valueType1;
				theValueType2 = valueType2;
			}

			/** @return The model type of this value */
			public ModelType.DoubleTyped<M> getType() {
				return theModelType;
			}

			/** @return The first value type of this value */
			public VariableType getValueType1() {
				return theValueType1;
			}

			/** @return The second value type of this value */
			public VariableType getValueType2() {
				return theValueType2;
			}

			@Override
			public boolean isTypeSpecified() {
				return theValueType1 != null && theValueType2 != null;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException {
				if (theValueType1 != null && theValueType2 != null)
					return theModelType.forType(//
						(TypeToken<?>) theValueType1.getType(session.getExpressoEnv().getModels()), //
						(TypeToken<?>) theValueType2.getType(session.getExpressoEnv().getModels())//
						);
				else if (theValueType1 == null && theValueType2 == null)
					return theModelType.any();
				else
					return theModelType.forType(//
						(TypeToken<?>) (theValueType1 == null ? TypeTokens.get().WILDCARD
							: theValueType1.getType(session.getExpressoEnv().getModels())), //
						(TypeToken<?>) (theValueType2 == null ? TypeTokens.get().WILDCARD
							: theValueType2.getType(session.getExpressoEnv().getModels()))//
						);
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
