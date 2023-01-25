package org.observe.expresso;

import org.observe.config.ObservableConfig;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelTag;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueCreator;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;

import com.google.common.reflect.TypeToken;

/** A simple structure consisting of a class view and models, the definition for a set of models for an application */
public class Expresso {
	/**
	 * {@link ObservableModelSet#getTagValue(ModelTag) Model tag} where the {@link QonfigElement} is stored by
	 * {@link ElementModelAugmentation}s
	 */
	public static final ModelTag<QonfigElement> QONFIG_ELEMENT_TAG = ModelTag.of(QonfigElement.class.getSimpleName(),
		TypeTokens.get().of(QonfigElement.class));

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
		 * @throws QonfigEvaluationException If the type cannot be evaluated
		 */
		ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigEvaluationException;

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
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigEvaluationException {
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
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigEvaluationException {
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

	/**
	 * A {@link QonfigValueModifier modifier} that is capable of adding values into the model set that an element sees (in its
	 * {@link ExpressoQIS})'s {@link ExpressoQIS#getExpressoEnv() expresso environment}
	 *
	 * @param <T> The type to modify
	 */
	public interface ElementModelAugmentation<T> extends QonfigInterpreterCore.QonfigValueModifier<T> {
		@Override
		default Object prepareSession(CoreSession session) throws QonfigInterpretationException {
			if (session.get(getClass().getName()) != null)
				return null;
			session.putLocal(getClass().getName(), true);
			ObservableModelSet.Builder builder;
			boolean createdBuilder;
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			ObservableModelSet model = exS.getExpressoEnv().getModels();
			if (model instanceof ObservableModelSet.Builder && model.getTagValue(QONFIG_ELEMENT_TAG) == session.getElement()) {
				builder = (ObservableModelSet.Builder) model;
				createdBuilder = false;
			} else {
				builder = model.wrap("element-model:" + session.getElement()).withTagValue(QONFIG_ELEMENT_TAG, session.getElement());
				exS.setModels(builder, null);
				createdBuilder = true;
			}
			// Clear out any inherited types
			Object oldValueType = session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY);
			if (oldValueType != null)
				session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, null);
			Object oldKeyType = session.get(ExpressoBaseV0_1.KEY_TYPE_KEY);
			if (oldKeyType != null)
				session.put(ExpressoBaseV0_1.KEY_TYPE_KEY, null);

			augmentElementModel(exS, builder);

			session.put(ExpressoBaseV0_1.VALUE_TYPE_KEY, oldValueType);
			session.put(ExpressoBaseV0_1.KEY_TYPE_KEY, oldKeyType);
			return createdBuilder;
		}

		@Override
		default Object postPrepare(CoreSession session, Object prepared) throws QonfigInterpretationException {
			if (Boolean.TRUE.equals(prepared)) {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				if (exS.getExpressoEnv().getModels() instanceof ObservableModelSet.Builder) {
					ObservableModelSet wrapped = ((ObservableModelSet.Builder) exS.getExpressoEnv().getModels()).build();
					exS.setModels(wrapped, null);
					session.putLocal(ExpressoQIS.ELEMENT_MODEL_KEY, wrapped);
				}
			}
			return prepared;
		}

		@Override
		default T modifyValue(T value, CoreSession session, Object prepared) throws QonfigInterpretationException {
			return value;
		}

		/**
		 * @param session The expresso session to use to augment the model
		 * @param builder The model builder to augment
		 * @throws QonfigInterpretationException If an error occurs augmenting the model
		 */
		void augmentElementModel(ExpressoQIS session, ObservableModelSet.Builder builder) throws QonfigInterpretationException;
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
