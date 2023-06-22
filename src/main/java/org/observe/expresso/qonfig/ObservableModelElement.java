package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public abstract class ObservableModelElement extends ExElement.Abstract {
	public static final ExElement.ChildElementGetter<ObservableModelElement, Interpreted<?>, Def<?>> VALUES = ExElement.ChildElementGetter
		.<ObservableModelElement, Interpreted<?>, Def<?>> of(Def::getValues, Interpreted::getValues, ObservableModelElement::getValues,
			"The values declared in the model");

	public interface ModelValueElement<E extends ExElement> extends ExElement.Def<E> {
		InterpretedValueElement<? extends E> interpret(ObservableModelElement.Interpreted<?> modelElement, InterpretedModelSet models)
			throws ExpressoInterpretationException;
	}

	public interface InterpretedValueElement<E extends ExElement> extends ExElement.Interpreted<E> {
		void update() throws ExpressoInterpretationException;

		E create(ObservableModelElement modelElement, ModelSetInstance models) throws ModelInstantiationException;
	}

	public static abstract class Def<M extends ObservableModelElement> extends ExElement.Def.Abstract<M> {
		private final List<ModelValueElement<?>> theValues;

		protected Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
			theValues = new ArrayList<>();
		}

		public List<? extends ModelValueElement<?>> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
				"abst-model");
			forChild(session.getRole("value"), VALUES);
			super.update(session);

			QonfigChildDef valueRole = session.getRole("value");
			theValues.clear();
			for (ModelComponentNode<?, ?> component : getModels().getComponents().values()) {
				if (component.getThing() instanceof ModelValueElement
					&& ((ModelValueElement<?>) component.getThing()).getElement().getParentRoles().contains(valueRole))
					theValues.add((ModelValueElement<?>) component.getThing());
			}
			Collections.sort(theValues, (v1, v2) -> Integer.compare(v1.getElement().getPositionInFile().getPosition(),
				v2.getElement().getPositionInFile().getPosition()));
		}

		public abstract Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;
	}

	public static abstract class Interpreted<M extends ObservableModelElement> extends ExElement.Interpreted.Abstract<M> {
		private final List<InterpretedValueElement<?>> theValues;

		protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theValues = new ArrayList<>();
		}

		@Override
		public Def<? super M> getDefinition() {
			return (Def<? super M>) super.getDefinition();
		}

		public List<? extends InterpretedValueElement<?>> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		@Override
		protected void update() throws ExpressoInterpretationException {
			super.update();

			CollectionUtils.synchronize(theValues, getDefinition().getValues(), //
				(widget, child) -> widget.getDefinition() == child)//
			.<ExpressoInterpretationException> simpleE(child -> child.interpret(Interpreted.this, getModels()))//
			.rightOrder()//
			.onRightX(element -> element.getLeftValue().update())//
			.onCommonX(element -> element.getLeftValue().update())//
			.adjust();
		}

		public abstract ObservableModelElement create(ExElement parent) throws ModelInstantiationException;
	}

	private final List<ExElement> theValues;

	protected ObservableModelElement(Interpreted<?> interpreted, ExElement parent) {
		super(interpreted, parent);
		theValues = new ArrayList<>();
	}

	public List<? extends ExElement> getValues() {
		return Collections.unmodifiableList(theValues);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

		CollectionUtils.synchronize(theValues, myInterpreted.getValues(), //
			(inst, interp) -> inst.getIdentity() == interp.getDefinition().getIdentity())//
		.<ModelInstantiationException> simpleE(interp -> interp.create(ObservableModelElement.this, myModels))//
		.rightOrder()//
		.onRightX(element -> {
			try {
				element.getLeftValue().update(element.getRightValue(), myModels);
			} catch (RuntimeException | Error e) {
				element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
			}
		})//
		.onCommonX(element -> {
			try {
				element.getLeftValue().update(element.getRightValue(), myModels);
			} catch (RuntimeException | Error e) {
				element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
			}
		})//
		.adjust();
	}

	public static class ModelSetElement extends ExElement.Abstract {
		private static final ExElement.ChildElementGetter<ModelSetElement, Interpreted<?>, Def<?>> MODELS = ExElement.ChildElementGetter
			.<ModelSetElement, Interpreted<?>, Def<?>> of(Def::getSubModels, Interpreted::getSubModels, ModelSetElement::getSubModels,
				"A model defined in the root model set");

		public static class Def<M extends ModelSetElement> extends ExElement.Def.Abstract<M> {
			private final List<ObservableModelElement.Def<?>> theSubModels;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theSubModels = new ArrayList<>();
			}

			protected Class<? extends ObservableModelElement.Def<?>> getModelType() {
				return (Class<? extends ObservableModelElement.Def<?>>) (Class<?>) ObservableModelElement.Def.class;
			}

			// Would rather name this "getModels", but that name's taken in ExElement.Def
			public List<? extends ObservableModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
					"models");
				forChild(session.getRole("model"), MODELS);
				super.update(session);
				ExElement.syncDefs((Class<ObservableModelElement.Def<?>>) getModelType(), theSubModels, session.forChildren("model"));
			}

			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<M extends ModelSetElement> extends ExElement.Interpreted.Abstract<M> {
			private final List<ObservableModelElement.Interpreted<?>> theSubModels;

			public Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theSubModels = new ArrayList<>();
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			public List<ObservableModelElement.Interpreted<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void update() throws ExpressoInterpretationException {
				super.update();
				CollectionUtils.synchronize(theSubModels, getDefinition().getSubModels(), //
					(widget, child) -> widget.getDefinition() == child)//
				.<ExpressoInterpretationException> simpleE(child -> child.interpret(Interpreted.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().update())//
				.onCommonX(element -> element.getLeftValue().update())//
				.adjust();
			}
		}

		private final List<ObservableModelElement> theSubModels;

		public ModelSetElement(ExElement.Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theSubModels = new ArrayList<>();
		}

		public List<? extends ObservableModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.<ModelInstantiationException> simpleE(child -> child.create(ModelSetElement.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommonX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.adjust();
		}
	}

	public static class DefaultModelElement extends ObservableModelElement {
		private static final ExElement.ChildElementGetter<DefaultModelElement, Interpreted<?>, Def<?>> SUB_MODELS = ExElement.ChildElementGetter
			.<DefaultModelElement, Interpreted<?>, Def<?>> of(Def::getSubModels, Interpreted::getSubModels,
				DefaultModelElement::getSubModels, "A sub-model defined under the parent model");

		public static class Def<M extends DefaultModelElement> extends ObservableModelElement.Def<M> {
			private final List<DefaultModelElement.Def<?>> theSubModels;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theSubModels = new ArrayList<>();
			}

			protected Class<? extends DefaultModelElement.Def<?>> getModelType() {
				return (Class<? extends DefaultModelElement.Def<?>>) (Class<?>) DefaultModelElement.Def.class;
			}

			public List<? extends DefaultModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
					"model");
				forChild(session.getRole("sub-model"), SUB_MODELS);
				super.update(session.asElement(session.getFocusType().getSuperElement()));

				ExElement.syncDefs((Class<Def<?>>) getModelType(), theSubModels, session.forChildren("sub-model"));
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<M extends DefaultModelElement> extends ObservableModelElement.Interpreted<M> {
			private final List<Interpreted<?>> theSubModels;

			public Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theSubModels = new ArrayList<>();
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			public List<? extends Interpreted<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void update() throws ExpressoInterpretationException {
				super.update();

				CollectionUtils.synchronize(theSubModels, getDefinition().getSubModels(), //
					(widget, child) -> widget.getDefinition() == child)//
				.<ExpressoInterpretationException> simpleE(child -> child.interpret(Interpreted.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().update())//
				.onCommonX(element -> element.getLeftValue().update())//
				.adjust();
			}

			@Override
			public ObservableModelElement create(ExElement parent) throws ModelInstantiationException {
				return new DefaultModelElement(this, parent);
			}
		}

		private final List<ObservableModelElement> theSubModels;

		public DefaultModelElement(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theSubModels = new ArrayList<>();
		}

		public List<? extends ObservableModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.<ModelInstantiationException> simpleE(child -> child.create(DefaultModelElement.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommonX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.adjust();
		}
	}

	public static class ExtModelElement extends ObservableModelElement {
		public static class Def<M extends ExtModelElement> extends ObservableModelElement.Def<M> {
			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
					"ext-model");
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<M extends ExtModelElement> extends ObservableModelElement.Interpreted<M> {
			public Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			@Override
			public ExtModelElement create(ExElement parent) throws ModelInstantiationException {
				return new ExtModelElement(this, parent);
			}
		}

		public ExtModelElement(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}
	}

	public static class ConfigModelElement extends ObservableModelElement {
		public static class Def<M extends ConfigModelElement> extends ObservableModelElement.Def<M> {
			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), ExpressoConfigV0_1.CONFIG_NAME, ExpressoConfigV0_1.VERSION, "config");
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<M extends ConfigModelElement> extends ObservableModelElement.Interpreted<M> {
			public Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			@Override
			public ObservableModelElement create(ExElement parent) throws ModelInstantiationException {
				return new ConfigModelElement(this, parent);
			}
		}

		public ConfigModelElement(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}
	}
}
