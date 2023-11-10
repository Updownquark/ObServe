package org.observe.expresso.qonfig;

import org.observe.expresso.ObservableModelSet;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public abstract class ExModelAugmentation<E extends ExElement> extends ExAddOn.Abstract<E> {
	public static final ObservableModelSet.ModelTag<Object> ELEMENT_MODEL_TAG = new ObservableModelSet.ModelTag<Object>() {
		@Override
		public String getName() {
			return "ElementLocalModelTag";
		}

		@Override
		public TypeToken<Object> getType() {
			return TypeTokens.get().OBJECT;
		}

		@Override
		public String toString() {
			return getName();
		}
	};

	public static abstract class Def<E extends ExElement, AO extends ExModelAugmentation<? super E>> extends ExAddOn.Def.Abstract<E, AO> {
		protected Def(QonfigAddOn type, ExElement.Def<? extends E> element) {
			super(type, element);
		}

		protected ObservableModelSet.Builder createBuilder(ExpressoQIS session) throws QonfigInterpretationException {
			ObservableModelSet.Builder builder = augmentElementModel(getElement().getExpressoEnv().getModels(), getElement());
			if (builder != getElement().getExpressoEnv().getModels()) {
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(builder));
				session.setExpressoEnv(getElement().getExpressoEnv());
			}
			return builder;
		}
	}

	protected ExModelAugmentation(E element) {
		super(element);
	}

	public static ObservableModelSet.Builder augmentElementModel(ObservableModelSet models, ExElement.Def<?> element) {
		ObservableModelSet.Builder builder;
		Object modelTag = models.getTagValue(ELEMENT_MODEL_TAG);
		if (modelTag != element.getIdentity()) {
			builder = ObservableModelSet.build(element.toString() + "(local)", models.getNameChecker());
			builder.withTagValue(ELEMENT_MODEL_TAG, element.getIdentity());
			builder.withAll(models);
		} else
			builder = (ObservableModelSet.Builder) models;
		return builder;
	}
}
