package org.observe.expresso.qonfig;

import org.observe.expresso.ObservableModelSet;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class ExModelAugmentation<E extends ExElement> extends ExAddOn.Abstract<E> {
	public static final String CREATED_LOCAL_MODEL_COPY = "Expresso.Created.Local.Copy";

	public static abstract class Def<E extends ExElement, AO extends ExModelAugmentation<? super E>> extends ExAddOn.Def.Abstract<E, AO> {
		protected Def(QonfigAddOn type, ExElement.Def<? extends E> element) {
			super(type, element);
		}

		protected ObservableModelSet.Builder createBuilder(ExpressoQIS session) throws QonfigInterpretationException {
			ObservableModelSet models = getElement().getExpressoEnv().getModels();
			ObservableModelSet.Builder builder;
			if (session.get(CREATED_LOCAL_MODEL_COPY) == null) {
				session.putLocal(CREATED_LOCAL_MODEL_COPY, true);
				builder = ObservableModelSet.build(getElement().toString() + ".local",
					models == null ? ObservableModelSet.JAVA_NAME_CHECKER : models.getNameChecker());
				if (getElement().getExpressoEnv().getModels() != null)
					builder.withAll(getElement().getExpressoEnv().getModels());
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(builder));
				session.setExpressoEnv(getElement().getExpressoEnv());
			} else if (models != null)
				builder = (ObservableModelSet.Builder) models;
			else {
				builder = ObservableModelSet.build(getElement().toString() + ".local",
					ObservableModelSet.JAVA_NAME_CHECKER);
				getElement().setExpressoEnv(getElement().getExpressoEnv().with(builder));
				session.setExpressoEnv(getElement().getExpressoEnv());
			}
			return builder;
		}
	}

	protected ExModelAugmentation(E element) {
		super(element);
	}
}
