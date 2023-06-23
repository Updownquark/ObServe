package org.observe.expresso.qonfig;

import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelTag;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;

/**
 * A {@link QonfigValueModifier modifier} that is capable of adding values into the model set that an element sees (in its
 * {@link ExpressoQIS})'s {@link ExpressoQIS#getExpressoEnv() expresso environment}
 *
 * @param <T> The type to modify
 */
public interface ElementModelAugmentation<T> extends QonfigInterpreterCore.QonfigValueModifier<T> {
	/**
	 * {@link ObservableModelSet#getTagValue(ModelTag) Model tag} where the {@link QonfigElement} is stored by
	 * {@link ElementModelAugmentation}s
	 */
	ModelTag<QonfigElement> QONFIG_ELEMENT_TAG = ModelTag.of(QonfigElement.class.getSimpleName(),
		TypeTokens.get().of(QonfigElement.class));

	@Override
	default Object prepareSession(CoreSession session) throws QonfigInterpretationException {
		if (session.get(getClass().getName(), true) != null)
			return null;
		session.putLocal(getClass().getName(), true);
		ObservableModelSet.Builder builder;
		boolean createdBuilder;
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ObservableModelSet model = exS.getExpressoEnv().getModels();
		if (model instanceof ObservableModelSet.Builder && model.getTagValue(ElementModelAugmentation.QONFIG_ELEMENT_TAG) == session.getElement()) {
			builder = (ObservableModelSet.Builder) model;
			createdBuilder = false;
		} else {
			builder = model.wrap("element-model:" + session.getElement().printLocation()).withTagValue(ElementModelAugmentation.QONFIG_ELEMENT_TAG,
				session.getElement());
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
		if (Boolean.TRUE.equals(prepared))
			session.as(ExpressoQIS.class).getExpressoEnv().saveLocalModel();
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