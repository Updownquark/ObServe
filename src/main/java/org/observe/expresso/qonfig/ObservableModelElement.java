package org.observe.expresso.qonfig;

import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigPersistence;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigPath;
import org.observe.config.SyncValueSet;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.ExpressoBaseV0_1.AppEnvironment;
import org.observe.util.TypeTokens;
import org.observe.util.swing.WindowPopulation;
import org.qommons.ArrayUtils;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.BetterFile;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FileBackups;
import org.qommons.io.Format;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;
import org.qommons.io.TextParseException;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

public abstract class ObservableModelElement extends ExElement.Abstract {
	public static final ExElement.ChildElementGetter<ObservableModelElement, Interpreted<?>, Def<?>> VALUES = ExElement.ChildElementGetter
		.<ObservableModelElement, Interpreted<?>, Def<?>> of(Def::getValues, Interpreted::getValues, ObservableModelElement::getValues,
			"A value declared in the model");

	public static abstract class Def<M extends ObservableModelElement> extends ExElement.Def.Abstract<M> {
		private final List<ModelValueElement.Def<?, ?>> theValues;

		private QonfigChildDef theValueRole;

		protected Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
			theValues = new ArrayList<>();
		}

		public List<? extends ModelValueElement.Def<?, ?>> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
				"abst-model");
			theValueRole = session.getRole("value");
			forChild(theValueRole, VALUES);

			ObservableModelSet.Builder builder = (ObservableModelSet.Builder) session.getExpressoEnv().getModels();
			CollectionUtils
			.synchronize(getAllChildren(), session.forChildren(), (me, ms) -> ExElement.typesEqual(me.getElement(), ms.getElement()))
			.adjust(new CollectionUtils.CollectionSynchronizerE<ExElement.Def<?>, ExpressoQIS, QonfigInterpretationException>() {
				@Override
				public boolean getOrder(ElementSyncInput<ExElement.Def<?>, ExpressoQIS> element) throws QonfigInterpretationException {
					return true;
				}

				@Override
				public ElementSyncAction leftOnly(ElementSyncInput<ExElement.Def<?>, ExpressoQIS> element)
					throws QonfigInterpretationException {
					List<? extends ExElement.Def<?>> roleChildren = getRoleChildren(element.getLeftValue().getElement(),
						Def.this.reporting());
					if (roleChildren != null) {
						int index = ArrayUtils.binarySearch(roleChildren,
							v -> Integer.compare(element.getLeftValue().getElement().getPositionInFile().getPosition(),
								v.getElement().getPositionInFile().getPosition()));
						if (index >= 0)
							roleChildren.remove(index);
					}
					return element.preserve();
				}

				@Override
				public ElementSyncAction rightOnly(ElementSyncInput<ExElement.Def<?>, ExpressoQIS> element)
					throws QonfigInterpretationException {
					List<? extends ExElement.Def<?>> roleChildren = getRoleChildren(element.getRightValue().getElement(),
						element.getRightValue().reporting());
					if (roleChildren != null) {
						ExElement.Def<?> newChild = elementAdded(element.getRightValue(), builder);
						if (newChild != null) {
							newChild.update(element.getRightValue());
							int index = ArrayUtils.binarySearch(roleChildren,
								v -> Integer.compare(newChild.getElement().getPositionInFile().getPosition(),
									v.getElement().getPositionInFile().getPosition()));
							if (index >= 0)
								((List<ExElement.Def<?>>) roleChildren).add(index + 1, newChild);
								else
									((List<ExElement.Def<?>>) roleChildren).add(-index - 1, newChild);
						}
					}
					return element.preserve();
				}

				@Override
				public ElementSyncAction common(ElementSyncInput<ExElement.Def<?>, ExpressoQIS> element)
					throws QonfigInterpretationException {
					elementUpdated(element.getLeftValue(), element.getRightValue(), builder);
					element.getLeftValue().update(element.getRightValue());
					return element.preserve();
				}
			}, CollectionUtils.AdjustmentOrder.RightOrder);

			super.update(session);
		}

		protected List<? extends ExElement.Def<?>> getRoleChildren(QonfigElement child, ErrorReporting reporting)
			throws QonfigInterpretationException {
			if (child.getDeclaredRoles().contains(theValueRole.getDeclared()))
				return theValues;
			else {
				reporting.error("Unhandled " + getElement().getType().getName() + " element");
				return null;
			}
		}

		protected ExElement.Def<?> elementAdded(ExpressoQIS session, ObservableModelSet.Builder builder)
			throws QonfigInterpretationException {
			if (session.fulfills(theValueRole)) {
				return interpretValue(session.getAttributeText("name"), session, null, builder);
			} else {
				session.reporting().error("Unhandled " + getElement().getType().getName() + " element");
				return null;
			}
		}

		protected void elementUpdated(ExElement.Def<?> element, ExpressoQIS session, ObservableModelSet.Builder builder)
			throws QonfigInterpretationException {
			if (!session.fulfills(theValueRole))
				interpretValue(session.getAttributeText("name"), session, (ModelValueElement.Def<?, ?>) element, builder);
			else
				session.reporting().error("Unhandled " + getElement().getType().getName() + " element");
		}

		protected abstract ModelValueElement.Def<?, ?> interpretValue(String name, ExpressoQIS value, ModelValueElement.Def<?, ?> previous,
			ObservableModelSet.Builder builder) throws QonfigInterpretationException;

		public abstract Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;
	}

	public static abstract class Interpreted<M extends ObservableModelElement> extends ExElement.Interpreted.Abstract<M> {
		private final List<ModelValueElement.Interpreted<?, ?, ?>> theValues;

		protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theValues = new ArrayList<>();
		}

		@Override
		public Def<? super M> getDefinition() {
			return (Def<? super M>) super.getDefinition();
		}

		public List<? extends ModelValueElement.Interpreted<?, ?, ?>> getValues() {
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

	private final List<ModelValueElement<?, ?>> theValues;

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

				ObservableModelSet.Builder builder = ObservableModelSet.build("models", ObservableModelSet.JAVA_NAME_CHECKER);
				if (nonTrivial(session.getExpressoEnv().getModels()))
					builder.withAll(session.getExpressoEnv().getModels());
				CollectionUtils
				.synchronize(theSubModels, session.forChildren("model"),
					(me, ms) -> ExElement.typesEqual(me.getElement(), ms.getElement()))//
				.simpleE(ms -> {
					ObservableModelSet.Builder subModel = builder.createSubModel(ms.getAttributeText("named", "name"),
						ms.getElement().getPositionInFile());
					return ms.setModels(subModel, null).interpret(ObservableModelElement.Def.class);
				})//
				.rightOrder()//
				.onRightX(el -> el.getLeftValue().update(el.getRightValue()))//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue()))//
				.adjust();

				session.setModels(builder.build(), null);
				super.update(session);
			}

			private static boolean nonTrivial(ObservableModelSet models) {
				if (!models.getComponents().isEmpty())
					return true;
				for (ObservableModelSet inh : models.getInheritance().values()) {
					if (nonTrivial(inh))
						return true;
				}
				return false;
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
			public void update() throws ExpressoInterpretationException {
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

			private QonfigChildDef theSubModelRole;

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
				theSubModelRole = session.getRole("sub-model");
				forChild(theSubModelRole, SUB_MODELS);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			protected List<? extends ExElement.Def<?>> getRoleChildren(QonfigElement child, ErrorReporting reporting)
				throws QonfigInterpretationException {
				if (child.getDeclaredRoles().contains(theSubModelRole.getDeclared()))
					return theSubModels;
				else
					return super.getRoleChildren(child, reporting);
			}

			@Override
			protected ModelValueElement.Def<?, ?> interpretValue(String name, ExpressoQIS value, ModelValueElement.Def<?, ?> previous,
				ObservableModelSet.Builder builder) throws QonfigInterpretationException {
				CompiledModelValue<?, ?> container = previous == null ? value.setModels(builder, null).interpret(CompiledModelValue.class)
					: (CompiledModelValue<?, ?>) previous;
				builder.withMaker(name, container, value.getElement().getPositionInFile());
				return container instanceof ModelValueElement.Def ? (ModelValueElement.Def<?, ?>) container : null;
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
			public DefaultModelElement create(ExElement parent) throws ModelInstantiationException {
				return new DefaultModelElement(this, parent);
			}
		}

		private final List<DefaultModelElement> theSubModels;

		public DefaultModelElement(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theSubModels = new ArrayList<>();
		}

		public List<? extends DefaultModelElement> getSubModels() {
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
		private static final ExElement.ChildElementGetter<ExtModelElement, Interpreted<?>, Def<?>> SUB_MODELS = ExElement.ChildElementGetter
			.<ExtModelElement, Interpreted<?>, Def<?>> of(Def::getSubModels, Interpreted::getSubModels, ExtModelElement::getSubModels,
				"A sub-model defined under the parent model");

		public static class Def<M extends ExtModelElement> extends ObservableModelElement.Def<M> {
			private final List<ExtModelElement.Def<?>> theSubModels;

			private QonfigChildDef theSubModelRole;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
				theSubModels = new ArrayList<>();
			}

			protected Class<? extends ExtModelElement.Def<?>> getModelType() {
				return (Class<? extends ExtModelElement.Def<?>>) (Class<?>) ExtModelElement.Def.class;
			}

			public List<? extends ExtModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION,
					"ext-model");
				theSubModelRole = session.getRole("sub-model");
				forChild(theSubModelRole, SUB_MODELS);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			protected List<? extends ExElement.Def<?>> getRoleChildren(QonfigElement child, ErrorReporting reporting)
				throws QonfigInterpretationException {
				if (child.getDeclaredRoles().contains(theSubModelRole.getDeclared()))
					return theSubModels;
				else
					return super.getRoleChildren(child, reporting);
			}

			@Override
			protected ModelValueElement.Def<?, ?> interpretValue(String name, ExpressoQIS valueEl, ModelValueElement.Def<?, ?> previous,
				ObservableModelSet.Builder builder) throws QonfigInterpretationException {
				ExtModelValue<?> container = valueEl.interpret(ExtModelValue.class);
				ModelInstanceType<Object, Object> childType;
				ObservableModelSet valueModel = valueEl.getExpressoEnv().getModels();
				try {
					childType = (ModelInstanceType<Object, Object>) container.getType(valueModel);
				} catch (ExpressoInterpretationException e) {
					throw new QonfigInterpretationException("Could not interpret type", e.getPosition(), e.getErrorLength(), e);
				}
				CompiledExpression defaultX = valueEl.getAttributeExpression("default");
				String childPath = builder.getIdentity().getPath() + "." + name;
				builder.withExternal(name, childType, valueEl.getElement().getPositionInFile(), extModels -> {
					try {
						return extModels.getValue(childPath, childType);
					} catch (IllegalArgumentException | ModelException | TypeConversionException e) {
						if (defaultX == null)
							throw e;
					}
					return null;
				}, models -> {
					if (defaultX == null)
						return null;
					ModelValueSynth<Object, Object> defaultV;
					try {
						defaultV = defaultX.evaluate(childType);
					} catch (ExpressoInterpretationException e) {
						throw new ModelInstantiationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
					}
					return defaultV.get(models);
				});
				return container instanceof ModelValueElement.Def ? (ModelValueElement.Def<?, ?>) container : null;
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}
		}

		public static class Interpreted<M extends ExtModelElement> extends ObservableModelElement.Interpreted<M> {
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
			public ExtModelElement create(ExElement parent) throws ModelInstantiationException {
				return new ExtModelElement(this, parent);
			}
		}

		private final List<ExtModelElement> theSubModels;

		public ExtModelElement(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theSubModels = new ArrayList<>();
		}

		public List<ExtModelElement> getSubModels() {
			return theSubModels;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.<ModelInstantiationException> simpleE(child -> child.create(ExtModelElement.this))//
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

	public static class ConfigModelElement extends ObservableModelElement {
		public static class Def<M extends ConfigModelElement> extends ObservableModelElement.Def<M> {
			private String theConfigName;
			private CompiledExpression theConfigDir;
			private List<String> theOldConfigNames;
			private boolean isBackup;
			private AppEnvironment theApplicationEnvironment;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			public String getConfigName() {
				return theConfigName;
			}

			public CompiledExpression getConfigDir() {
				return theConfigDir;
			}

			public List<String> getOldConfigNames() {
				return Collections.unmodifiableList(theOldConfigNames);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), ExpressoConfigV0_1.CONFIG_NAME, ExpressoConfigV0_1.VERSION, "config");

				theConfigName = session.getAttributeText("config-name");
				theConfigDir = session.getAttributeExpression("config-dir");

				theOldConfigNames.clear();
				for (QonfigElement ch : session.getChildren("old-config-name"))
					theOldConfigNames.add(ch.getValueText());

				isBackup = session.getAttribute("backup", boolean.class);
				theApplicationEnvironment = session.get(ExpressoBaseV0_1.APP_ENVIRONMENT_KEY, AppEnvironment.class);

				((ObservableModelSet.Builder) session.getExpressoEnv().getModels()).withMaker(ExpressoConfigV0_1.CONFIG_NAME,
					new ConfigValueMaker(), session.getElement().getPositionInFile());

				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			protected ModelValueElement.Def<?, ?> interpretValue(String name, ExpressoQIS child, ModelValueElement.Def<?, ?> previous,
				ObservableModelSet.Builder builder) throws QonfigInterpretationException {
				ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> configV;
				try {
					configV = (ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>>) builder
						.getComponent(ExpressoConfigV0_1.CONFIG_NAME);
				} catch (ModelException e) {
					throw new QonfigInterpretationException("But we just installed it!", child.getElement().getPositionInFile(), 0, e);
				}
				String path = child.getAttributeText("config-path");

				if (path == null)
					path = name;
				ConfigModelValue<?, ?, ?> mv = child.interpret(ConfigModelValue.class);
				builder.withMaker(name, createConfigValue(mv, configV, path, child), child.getElement().getPositionInFile());
				return mv instanceof ModelValueElement.Def ? (ModelValueElement.Def<?, ?>) mv : null;
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}

			private <T, M2, MV extends M2> CompiledModelValue<M2, MV> createConfigValue(ConfigModelValue<T, M2, MV> configValue,
				ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> configV, String path, ExpressoQIS session)
					throws QonfigInterpretationException {
				ExpressoQIS formatSession = session.forChildren("format").peekFirst();
				CompiledModelValue<?, ?> formatCreator = formatSession == null ? null : formatSession.interpret(CompiledModelValue.class);
				return CompiledModelValue.of("value", configValue.getType().getModelType(), () -> {
					configValue.init();
					InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> iConfigV = configV.interpret();
					TypeToken<T> formatType = (TypeToken<T>) configValue.getType()
						.getType(configValue.getType().getModelType().getTypeCount() - 1);
					ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> formatContainer;
					ObservableConfigFormat<T> defaultFormat;
					if (formatCreator != null) {
						try {
							formatContainer = formatCreator.createSynthesizer().as(ModelTypes.Value.forType(TypeTokens.get()
								.keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<T>> parameterized(formatType)));
						} catch (TypeConversionException e) {
							LocatedFilePosition position;
							if (formatSession != null)
								position = formatSession.getElement().getPositionInFile();
							else
								position = session.getElement().getPositionInFile();
							throw new ExpressoInterpretationException("Could not evaluate " + formatCreator + " as a config format",
								position, 0, e);
						}
						defaultFormat = null;
					} else {
						formatContainer = null;
						defaultFormat = getDefaultConfigFormat(formatType);
						if (defaultFormat == null)
							throw new ExpressoInterpretationException("No default config format available for type " + formatType,
								session.getElement().getPositionInFile(), 0);
					}
					return new ModelValueSynth<M2, MV>() {
						@Override
						public ModelType<M2> getModelType() {
							return configValue.getType().getModelType();
						}

						@Override
						public ModelInstanceType<M2, MV> getType() throws ExpressoInterpretationException {
							return configValue.getType();
						}

						@Override
						public MV get(ModelSetInstance msi) throws ModelInstantiationException, IllegalStateException {
							ObservableConfig config = iConfigV.get(msi).get();
							ObservableConfig.ObservableConfigValueBuilder<T> builder = config//
								.asValue(formatType).at(path)//
								.until(msi.getUntil());
							if (formatContainer != null)
								builder.withFormat(formatContainer.get(msi).get());
							else
								builder.withFormat(defaultFormat);
							return configValue.create(builder, msi);
						}

						@Override
						public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
							// Should be the same thing, since the config hasn't changed
							return value;
						}

						@Override
						public List<? extends ModelValueSynth<?, ?>> getComponents() {
							return Collections.emptyList();
						}
					};
				});
			}

			private void build2(ObservableConfig config, BetterFile configFile, FileBackups backups, boolean[] closingWithoutSave) {
				if (configFile != null) {
					ObservableConfigPersistence<IOException> actuallyPersist = ObservableConfig.toFile(configFile,
						ObservableConfig.XmlEncoding.DEFAULT);
					boolean[] persistenceQueued = new boolean[1];
					ObservableConfigPersistence<IOException> persist = new ObservableConfig.ObservableConfigPersistence<IOException>() {
						@Override
						public void persist(ObservableConfig config2) throws IOException {
							try {
								if (persistenceQueued[0] && !closingWithoutSave[0]) {
									actuallyPersist.persist(config2);
									if (backups != null)
										backups.fileChanged();
								}
							} finally {
								persistenceQueued[0] = false;
							}
						}
					};
					config.persistOnShutdown(persist, ex -> {
						System.err.println("Could not persist UI config");
						ex.printStackTrace();
					});
					QommonsTimer timer = QommonsTimer.getCommonInstance();
					Object key = new Object() {
						@Override
						public String toString() {
							return config.getName() + " persistence";
						}
					};
					Duration persistDelay = Duration.ofSeconds(2);
					config.watch(ObservableConfigPath.buildPath(ObservableConfigPath.ANY_NAME).multi(true).build()).act(evt -> {
						if (evt.changeType == CollectionChangeType.add && evt.getChangeTarget().isTrivial())
							return;
						persistenceQueued[0] = true;
						timer.doAfterInactivity(key, () -> {
							try {
								persist.persist(config);
							} catch (IOException ex) {
								System.err.println("Could not persist UI config");
								ex.printStackTrace();
							}
						}, persistDelay);
					});
				}
			}

			private static <T> ObservableConfigFormat<T> getDefaultConfigFormat(TypeToken<T> valueType) {
				Format<T> f;
				Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(valueType));
				if (type == String.class)
					f = (Format<T>) SpinnerFormat.NUMERICAL_TEXT;
				else if (type == int.class)
					f = (Format<T>) SpinnerFormat.INT;
				else if (type == long.class)
					f = (Format<T>) SpinnerFormat.LONG;
				else if (type == double.class)
					f = (Format<T>) Format.doubleFormat(4).build();
				else if (type == float.class)
					f = (Format<T>) Format.doubleFormat(4).buildFloat();
				else if (type == boolean.class)
					f = (Format<T>) Format.BOOLEAN;
				else if (Enum.class.isAssignableFrom(type))
					f = (Format<T>) Format.enumFormat((Class<Enum<?>>) type);
				else if (type == Instant.class)
					f = (Format<T>) SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
				else if (type == Duration.class)
					f = (Format<T>) SpinnerFormat.flexDuration(false);
				else
					return null;
				T defaultValue = TypeTokens.get().getDefaultValue(valueType);
				return ObservableConfigFormat.ofQommonFormat(f, () -> defaultValue);
			}

			private class ConfigValueMaker implements CompiledModelValue<SettableValue<?>, SettableValue<ObservableConfig>> {
				@Override
				public ModelType<SettableValue<?>> getModelType() {
					return ModelTypes.Value;
				}

				@Override
				public ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> createSynthesizer()
					throws ExpressoInterpretationException {
					ModelValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir;
					if (theConfigDir != null)
						configDir = theConfigDir.evaluate(ModelTypes.Value.forType(BetterFile.class));
					else {
						configDir = ModelValueSynth.of(ModelTypes.Value.forType(BetterFile.class), msi -> {
							String prop = System.getProperty(theConfigName + ".config");
							if (prop != null)
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), prop), prop);
							else
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), "./" + theConfigName),
									"./" + theConfigName);
						});
					}

					return ModelValueSynth.of(ModelTypes.Value.forType(ObservableConfig.class), msi -> {
						BetterFile configDirFile = configDir == null ? null : configDir.get(msi).get();
						if (configDirFile == null) {
							String configProp = System.getProperty(theConfigName + ".config");
							if (configProp != null)
								configDirFile = BetterFile.at(new NativeFileSource(), configProp);
							else
								configDirFile = BetterFile.at(new NativeFileSource(), "./" + theConfigName);
						}
						if (!configDirFile.exists()) {
							try {
								configDirFile.create(true);
							} catch (IOException e) {
								throw new IllegalStateException("Could not create config directory " + configDirFile.getPath(), e);
							}
						} else if (!configDirFile.isDirectory())
							throw new IllegalStateException("Not a directory: " + configDirFile.getPath());

						BetterFile configFile = configDirFile.at(theConfigName + ".xml");
						if (!configFile.exists()) {
							BetterFile oldConfigFile = configDirFile.getParent().at(theConfigName + ".config");
							if (oldConfigFile.exists()) {
								try {
									oldConfigFile.move(configFile);
								} catch (IOException e) {
									System.err.println(
										"Could not move old configuration " + oldConfigFile.getPath() + " to " + configFile.getPath());
									e.printStackTrace();
								}
							}
						}

						FileBackups backups = isBackup ? new FileBackups(configFile) : null;

						if (!configFile.exists() && theOldConfigNames != null) {
							boolean found = false;
							for (String oldConfigName : theOldConfigNames) {
								BetterFile oldConfigFile = configDirFile.at(oldConfigName);
								if (oldConfigFile.exists()) {
									try {
										oldConfigFile.move(configFile);
									} catch (IOException e) {
										System.err.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
										e.printStackTrace();
									}
									if (backups != null)
										backups.renamedFrom(oldConfigFile);
									found = true;
									break;
								}
								if (!found) {
									oldConfigFile = configDirFile.getParent().at(oldConfigName + "/" + oldConfigName + ".xml");
									if (oldConfigFile.exists()) {
										try {
											oldConfigFile.move(configFile);
										} catch (IOException e) {
											System.err
											.println("Could not rename " + oldConfigFile.getPath() + " to " + configFile.getPath());
											e.printStackTrace();
										}
										if (backups != null)
											backups.renamedFrom(oldConfigFile);
										found = true;
										break;
									}
								}
							}
						}
						ObservableConfig config = ObservableConfig.createRoot(theConfigName, ThreadConstraint.EDT);
						ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
						boolean loaded = false;
						if (configFile.exists()) {
							try {
								try (InputStream configStream = new BufferedInputStream(configFile.read())) {
									ObservableConfig.readXml(config, configStream, encoding);
								}
								config.setName(theConfigName);
								loaded = true;
							} catch (IOException | TextParseException e) {
								System.out.println("Could not read config file " + configFile.getPath());
								e.printStackTrace(System.out);
							}
						}
						boolean[] closingWithoutSave = new boolean[1];
						AppEnvironment app = theApplicationEnvironment != null ? theApplicationEnvironment : new AppEnvironment() {
							@Override
							public ModelValueSynth<SettableValue<?>, ? extends ObservableValue<String>> getTitle() {
								return ModelValueSynth.literal(TypeTokens.get().STRING, "Unspecified Application",
									"Unspecified Application");
							}

							@Override
							public ModelValueSynth<SettableValue<?>, ? extends ObservableValue<Image>> getIcon() {
								return ModelValueSynth.literal(TypeTokens.get().of(Image.class), null, "No Image");
							}
						};
						if (loaded)
							build2(config, configFile, backups, closingWithoutSave);
						else if (backups != null && !backups.getBackups().isEmpty()) {
							restoreBackup(true, config, backups, () -> {
								config.setName(theConfigName);
								build2(config, configFile, backups, closingWithoutSave);
							}, () -> {
								config.setName(theConfigName);
								build2(config, configFile, backups, closingWithoutSave);
							}, app, closingWithoutSave, msi);
						} else {
							config.setName(theConfigName);
							build2(config, configFile, backups, closingWithoutSave);
						}
						return SettableValue.of(ObservableConfig.class, config, "Not Settable");
					});
				}
			}

			static void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup,
				Runnable onNoBackup, AppEnvironment app, boolean[] closingWithoutSave, ModelSetInstance msi)
					throws ModelInstantiationException {
				BetterSortedSet<Instant> backupTimes = backups == null ? null : backups.getBackups();
				if (backupTimes == null || backupTimes.isEmpty()) {
					if (onNoBackup != null)
						onNoBackup.run();
					return;
				}
				SettableValue<Instant> selectedBackup = SettableValue.build(Instant.class).build();
				Format<Instant> PAST_DATE_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", opts -> opts
					.withMaxResolution(TimeUtils.DateElementType.Second).withEvaluationType(TimeUtils.RelativeInstantEvaluation.Past));
				JFrame[] frame = new JFrame[1];
				boolean[] backedUp = new boolean[1];
				ObservableValue<String> title = (app == null || app.getTitle() == null) ? ObservableValue.of("Unnamed Application")
					: app.getTitle().get(msi);
				ObservableValue<Image> icon = (app == null || app.getIcon() == null) ? ObservableValue.of(Image.class, null)
					: app.getIcon().get(msi);
				frame[0] = WindowPopulation.populateWindow(null, null, false, false)//
					.withTitle((app == null || title.get() == null) ? "Backup" : title.get() + " Backup")//
					.withIcon(app == null ? ObservableValue.of(Image.class, null) : icon)//
					.withVContent(content -> {
						if (fromError)
							content.addLabel(null, "Your configuration is missing or has been corrupted", null);
						TimeUtils.RelativeTimeFormat durationFormat = TimeUtils.relativeFormat()
							.withMaxPrecision(TimeUtils.DurationComponentType.Second).withMaxElements(2).withMonthsAndYears();
						content.addLabel(null, "Please choose a backup to restore", null)//
						.addTable(ObservableCollection.of(TypeTokens.get().of(Instant.class), backupTimes.reverse()), table -> {
							table.fill()
							.withColumn("Date", Instant.class, t -> t,
								col -> col.formatText(PAST_DATE_FORMAT::format).withWidths(80, 160, 500))//
							.withColumn("Age", Instant.class, t -> t,
								col -> col.formatText(t -> durationFormat.printAsDuration(t, Instant.now())).withWidths(50, 90,
									500))//
							.withSelection(selectedBackup, true);
						}).addButton("Backup", __ -> {
							closingWithoutSave[0] = true;
							try {
								backups.restore(selectedBackup.get());
								if (config != null)
									populate(config, QommonsConfig
										.fromXml(QommonsConfig.getRootElement(backups.getBackup(selectedBackup.get()).read())));
								backedUp[0] = true;
							} catch (IOException e) {
								e.printStackTrace();
							} finally {
								closingWithoutSave[0] = false;
							}
							frame[0].setVisible(false);
						}, btn -> btn.disableWith(selectedBackup.map(t -> t == null ? "Select a Backup" : null)));
					}).run(null).getWindow();
				frame[0].addComponentListener(new ComponentAdapter() {
					@Override
					public void componentHidden(ComponentEvent e) {
						if (backedUp[0]) {
							if (onBackup != null)
								onBackup.run();
						} else {
							if (onNoBackup != null)
								onNoBackup.run();
						}
					}
				});
			}

			static void populate(ObservableConfig config, QommonsConfig initConfig) {
				config.setName(initConfig.getName());
				config.setValue(initConfig.getValue());
				SyncValueSet<? extends ObservableConfig> subConfigs = config.getAllContent();
				int configIdx = 0;
				for (QommonsConfig initSubConfig : initConfig.subConfigs()) {
					if (configIdx < subConfigs.getValues().size())
						populate(subConfigs.getValues().get(configIdx), initSubConfig);
					else
						populate(config.addChild(initSubConfig.getName()), initSubConfig);
					configIdx++;
				}
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
			public ConfigModelElement create(ExElement parent) throws ModelInstantiationException {
				return new ConfigModelElement(this, parent);
			}
		}

		public ConfigModelElement(Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}
	}
}
