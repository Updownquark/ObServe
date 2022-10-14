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
		ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException;

		class SingleTyped<M> implements ExtModelValue<M> {
			private final ModelType.SingleTyped<M> theType;

			SingleTyped(ModelType.SingleTyped<M> type) {
				theType = type;
			}

			@Override
			public ModelInstanceType<M, ?> getType(ExpressoQIS session) throws QonfigInterpretationException {
				return theType.forType((TypeToken<?>) session.get(ExpressoBaseV0_1.VALUE_TYPE_KEY));
			}
		}

		class DoubleTyped<M> implements ExtModelValue<M> {
			private final ModelType.DoubleTyped<M> theType;

			DoubleTyped(ModelType.DoubleTyped<M> type) {
				theType = type;
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
		ModelInstanceType<M, MV> getType();

		MV create(ObservableConfig.ObservableConfigValueBuilder<?> config, ModelSetInstance msi);
	}

	private final ClassView theClassView;
	private final ObservableModelSet theModels;

	public Expresso(ClassView classView, ObservableModelSet models) {
		theClassView = classView;
		theModels = models;
	}

	public ClassView getClassView() {
		return theClassView;
	}

	public ObservableModelSet getModels() {
		return theModels;
	}
}
