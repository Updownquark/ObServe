package org.observe.expresso.qonfig;

import org.observe.expresso.ObservableModelSet;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;

import com.google.common.reflect.TypeToken;

/**
 * An abstract add-on with some utility for augmenting the model view of a tagged element
 *
 * @param <E> The type of element whose model view to augment
 */
public abstract class ExModelAugmentation<E extends ExElement> extends ExAddOn.Abstract<E> {
	/** A model tag to identify a model as augmented by an extension of this add-on */
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

	/**
	 * {@link ExModelAugmentation} definition
	 *
	 * @param <E> The type of element whose model view to augment
	 * @param <AO> The type of add-on to create
	 */
	public static abstract class Def<E extends ExElement, AO extends ExModelAugmentation<? super E>> extends ExAddOn.Def.Abstract<E, AO> {
		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element whose model view to augment
		 */
		protected Def(QonfigAddOn type, ExElement.Def<? extends E> element) {
			super(type, element);
		}

		/**
		 * @param session The expresso interpretation session whose model to augment
		 * @return A model builder to inject model values in to augment the tagged element's model view
		 */
		protected ObservableModelSet.Builder createBuilder(ExpressoQIS session) {
			ObservableModelSet.Builder builder = augmentElementModel(getElement().getExpressoEnv().getModels(), getElement());
			if (builder != getElement().getExpressoEnv().getModels()) {
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(builder));
				session.setExpressoEnv(getElement().getExpressoEnv());
			}
			return builder;
		}
	}

	/** @param element The element whose model to augment */
	protected ExModelAugmentation(E element) {
		super(element);
	}

	/**
	 * @param models The model to augment
	 * @param element The element to augment the model for
	 * @return A model builder to inject model values in to augment the element's model view
	 */
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
