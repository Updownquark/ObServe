package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class ObservableModelElement extends ExElement.Abstract {
	private static final SingleTypeTraceability<ObservableModelElement, Interpreted<?>, Def<?, ?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "abst-model", Def.class,
			Interpreted.class, ObservableModelElement.class);

	public static abstract class Def<M extends ObservableModelElement, V extends ModelValueElement.Def<?, ?>>
	extends ExElement.Def.Abstract<M> {
		private String theModelPath;
		private final List<V> theValues;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theValues = new ArrayList<>();
		}

		public String getModelPath() {
			return theModelPath;
		}

		public String getName() {
			return getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
		}

		@QonfigChildGetter("value")
		public List<V> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		protected abstract Class<V> getValueType();

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session);
			theModelPath = session.get(ExpressoBaseV0_1.PATH_KEY, String.class);
			String name = getName();
			if (theModelPath == null) {
				if (name == null)
					name = "model";
				theModelPath = name;
			} else if (name != null)
				theModelPath += "." + name;
			session.put(ExpressoBaseV0_1.PATH_KEY, theModelPath);

			BetterList<ExpressoQIS> valueSessions = session.forChildren("value");
			ExElement.syncDefs(getValueType(), theValues, valueSessions);
			for (ModelValueElement.Def<?, ?> value : theValues)
				value.populate((ObservableModelSet.Builder) session.getExpressoEnv().getModels());
			int i = 0;
			for (ExpressoQIS vs : valueSessions)
				theValues.get(i++).prepareModelValue(vs);
		}

		public abstract Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;
	}

	public static abstract class Interpreted<M extends ObservableModelElement> extends ExElement.Interpreted.Abstract<M> {
		private final List<ModelValueElement.Interpreted<?, ?, ?>> theValues;

		protected Interpreted(Def<? super M, ?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theValues = new ArrayList<>();
		}

		@Override
		public Def<? super M, ?> getDefinition() {
			return (Def<? super M, ?>) super.getDefinition();
		}

		public List<? extends ModelValueElement.Interpreted<?, ?, ?>> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		public void updateSubModel(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			InterpretedModelSet childModels;
			try {
				childModels = env.getModels().getSubModel(getDefinition().getName());
			} catch (ModelException e) {
				throw new IllegalStateException("Child model not added?", e);
			}
			update(env.with(childModels));
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			// Find all the interpreted model values and initialize them with this as their parent before they are initialized properly
			theValues.clear();
			for (String name : env.getModels().getComponentNames()) {
				InterpretedModelComponentNode<?, ?> mv = env.getModels().getLocalComponent(name).interpreted();
				ModelValueElement.Interpreted<?, ?, ?> modelValue = findModelValue(mv.getValue());
				if (modelValue != null && modelValue.getDefinition().getParentElement() == getDefinition()) {
					modelValue.setParentElement(this);
					theValues.add(modelValue);
				}
			}
			Collections.sort(theValues, (mv1, mv2) -> Integer.compare(mv1.reporting().getPosition().getPosition(),
				mv2.reporting().getPosition().getPosition()));

			super.doUpdate(env);
		}

		private ModelValueElement.Interpreted<?, ?, ?> findModelValue(InterpretedValueSynth<?, ?> value) {
			if (value == null)
				return null;
			else if (value instanceof ModelValueElement.InterpretedSynth)
				return (ModelValueElement.Interpreted<?, ?, ?>) value;
			List<? extends InterpretedValueSynth<?, ?>> components = value.getComponents();
			if (components.size() != 1)
				return null;
			return findModelValue(components.get(0));
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
			(inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.adjust(
			new CollectionUtils.CollectionSynchronizerE<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>, ModelInstantiationException>() {
				@Override
				public boolean getOrder(ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element)
					throws ModelInstantiationException {
					return true;
				}

				@Override
				public ElementSyncAction leftOnly(
					ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element)
						throws ModelInstantiationException {
					return element.remove();
				}

				@Override
				public ElementSyncAction rightOnly(
					ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element)
						throws ModelInstantiationException {
					ModelValueElement<?, ?> instance = element.getRightValue().create(ObservableModelElement.this, myModels);
					if (instance != null) {
						instance.update(element.getRightValue(), myModels);
						return element.useValue(instance);
					} else
						return element.remove();
				}

				@Override
				public ElementSyncAction common(
					ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element)
						throws ModelInstantiationException {
					element.getLeftValue().update(element.getRightValue(), myModels);
					return element.preserve();
				}
			}, CollectionUtils.AdjustmentOrder.RightOrder);
	}

	public static class ModelSetElement extends ExElement.Abstract {
		private static final SingleTypeTraceability<ModelSetElement, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "models", Def.class,
				Interpreted.class, ModelSetElement.class);

		public static class Def<M extends ModelSetElement> extends ExElement.Def.Abstract<M> {
			private final List<ObservableModelElement.Def<?, ?>> theSubModels;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theSubModels = new ArrayList<>();
			}

			protected Class<? extends ObservableModelElement.Def<?, ?>> getModelType() {
				return (Class<? extends ObservableModelElement.Def<?, ?>>) (Class<?>) ObservableModelElement.Def.class;
			}

			// Would rather name this "getModels", but that name's taken in ExElement.Def
			@QonfigChildGetter("model")
			public List<? extends ObservableModelElement.Def<?, ?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session);

				session.put(ExpressoBaseV0_1.PATH_KEY, session.getElement().getType().getName());
				ObservableModelSet.Builder builder;
				if (getExpressoEnv().getModels() instanceof ObservableModelSet.Builder)
					builder = (ObservableModelSet.Builder) getExpressoEnv().getModels();
				else {
					builder = ObservableModelSet.build(getQonfigType().getName(), ObservableModelSet.JAVA_NAME_CHECKER);
					if (nonTrivial(getExpressoEnv().getModels()))
						builder.withAll(session.getExpressoEnv().getModels());
					session.setModels(builder);
					setExpressoEnv(session.getExpressoEnv());
				}
				CollectionUtils
				.synchronize(theSubModels, session.forChildren("model"),
					(me, ms) -> ExElement.typesEqual(me.getElement(), ms.getElement()))//
				.<QonfigInterpretationException> simpleE(ms -> {
					ObservableModelSet.Builder subModel = builder.createSubModel(ms.getAttributeText("named", "name"),
						ms.getElement().getPositionInFile());
					return ms.setModels(subModel).interpret(ObservableModelElement.Def.class);
				})//
				.rightOrder()//
				.onRightX(el -> el.getLeftValue().update(el.getRightValue()))//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue()))//
				.adjust();
				//
				// ObservableModelSet.Built built = builder.build();
				// session.setModels(built);
				// setExpressoEnv(getExpressoEnv().with(built));
			}

			private static boolean nonTrivial(ObservableModelSet models) {
				if (models == null)
					return false;
				else if (!models.getComponentNames().isEmpty())
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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				CollectionUtils.synchronize(theSubModels, getDefinition().getSubModels(), //
					(widget, child) -> widget.getIdentity() == child.getIdentity())//
				.<ExpressoInterpretationException> simpleE(child -> child.interpret(Interpreted.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().updateSubModel(getExpressoEnv()))//
				.onCommonX(element -> element.getLeftValue().updateSubModel(getExpressoEnv()))//
				.adjust();
			}
		}

		private final List<ObservableModelElement> theSubModels;

		public ModelSetElement(ExElement.Interpreted<?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theSubModels = new ArrayList<>();
		}

		@QonfigChildGetter("model")
		public List<? extends ObservableModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getIdentity())//
			.<ModelInstantiationException> simpleE(child -> child.create(ModelSetElement.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.onCommonX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), myModels);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.adjust();
		}
	}

	public static class DefaultModelElement extends ObservableModelElement {
		private static final SingleTypeTraceability<DefaultModelElement, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "model", Def.class,
				Interpreted.class, DefaultModelElement.class);

		public static class Def<M extends DefaultModelElement>
		extends ObservableModelElement.Def<M, ModelValueElement.CompiledSynth<?, ?>> {
			private final List<DefaultModelElement.Def<?>> theSubModels;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theSubModels = new ArrayList<>();
			}

			@Override
			protected Class<CompiledSynth<?, ?>> getValueType() {
				return (Class<ModelValueElement.CompiledSynth<?, ?>>) (Class<?>) ModelValueElement.CompiledSynth.class;
			}

			protected Class<? extends DefaultModelElement.Def<?>> getModelType() {
				return (Class<? extends DefaultModelElement.Def<?>>) (Class<?>) DefaultModelElement.Def.class;
			}

			@QonfigChildGetter("sub-model")
			public List<? extends DefaultModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				ExElement.syncDefs(DefaultModelElement.Def.class, theSubModels, session.forChildren("sub-model"));
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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				CollectionUtils.synchronize(theSubModels, getDefinition().getSubModels(), //
					(widget, child) -> widget.getDefinition() == child)//
				.<ExpressoInterpretationException> simpleE(child -> child.interpret(Interpreted.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().updateSubModel(getExpressoEnv()))//
				.onCommonX(element -> element.getLeftValue().updateSubModel(getExpressoEnv()))//
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

	public static class LocalModelElementDef extends DefaultModelElement.Def<DefaultModelElement> {
		public LocalModelElementDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}
	}

	public static class ExtModelElement extends ObservableModelElement {
		private static final SingleTypeTraceability<ExtModelElement, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "ext-model", Def.class,
				Interpreted.class, ExtModelElement.class);

		public static class Def<M extends ExtModelElement> extends ObservableModelElement.Def<M, ExtModelValueElement.Def<?>> {
			private final List<ExtModelElement.Def<?>> theSubModels;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theSubModels = new ArrayList<>();
			}

			@Override
			protected Class<ExtModelValueElement.Def<?>> getValueType() {
				return (Class<ExtModelValueElement.Def<?>>) (Class<?>) ExtModelValueElement.Def.class;
			}

			protected Class<? extends ExtModelElement.Def<?>> getModelType() {
				return (Class<? extends ExtModelElement.Def<?>>) (Class<?>) ExtModelElement.Def.class;
			}

			@QonfigChildGetter("sub-model")
			public List<? extends ExtModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				ExElement.syncDefs(ExtModelElement.Def.class, theSubModels, session.forChildren("sub-model"));
			}

			// @Override
			// protected ModelValueElement.Def<?, ?> interpretValue(String name, ExpressoQIS valueEl, ModelValueElement.Def<?, ?> previous,
			// ObservableModelSet.Builder builder) throws QonfigInterpretationException {
			// ExtModelValue<?> container = valueEl.interpret(ExtModelValue.class);
			// ModelInstanceType<Object, Object> childType;
			// ObservableModelSet valueModel = valueEl.getExpressoEnv().getModels();
			// try {
			// childType = (ModelInstanceType<Object, Object>) container.getType(valueModel);
			// } catch (ExpressoInterpretationException e) {
			// throw new QonfigInterpretationException("Could not interpret type", e.getPosition(), e.getErrorLength(), e);
			// }
			// CompiledExpression defaultX = valueEl.getAttributeExpression("default");
			// String childPath = builder.getIdentity().getPath() + "." + name;
			// builder.withExternal(name, childType, valueEl.getElement().getPositionInFile(), extModels -> {
			// try {
			// return extModels.getValue(childPath, childType);
			// } catch (IllegalArgumentException | ModelException | TypeConversionException e) {
			// if (defaultX == null)
			// throw e;
			// }
			// return null;
			// }, models -> {
			// if (defaultX == null)
			// return null;
			// ModelValueSynth<Object, Object> defaultV;
			// try {
			// defaultV = defaultX.evaluate(childType);
			// } catch (ExpressoInterpretationException e) {
			// throw new ModelInstantiationException(e.getMessage(), e.getPosition(), e.getErrorLength(), e);
			// }
			// return defaultV.get(models);
			// });
			// return container instanceof ModelValueElement.Def ? (ModelValueElement.Def<?, ?>) container : null;
			// }

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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				CollectionUtils.synchronize(theSubModels, getDefinition().getSubModels(), //
					(widget, child) -> widget.getDefinition() == child)//
				.<ExpressoInterpretationException> simpleE(child -> child.interpret(Interpreted.this))//
				.rightOrder()//
				.onRightX(element -> element.getLeftValue().updateSubModel(getExpressoEnv()))//
				.onCommonX(element -> element.getLeftValue().updateSubModel(getExpressoEnv()))//
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

	/*public static class ConfigModelElement extends ObservableModelElement {
		public static final String APP_ENVIRONMENT_KEY = "EXPRESSO.APP.ENVIRONMENT";
		private static final ElementTypeTraceability<ConfigModelElement, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.<ConfigModelElement, Interpreted<?>, Def<?>> build(ExpressoConfigV0_1.NAME, ExpressoConfigV0_1.VERSION, "config")//
			.reflectMethods(Def.class, Interpreted.class, ConfigModelElement.class)//
			.build();

		public static class Def<M extends ConfigModelElement> extends ObservableModelElement.Def<M> {
			private String theConfigName;
			private CompiledExpression theConfigDir;
			private List<String> theOldConfigNames;
			private boolean isBackup;
			private AppEnvironment theApplicationEnvironment;

			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@QonfigAttributeGetter("config-name")
			public String getConfigName() {
				return theConfigName;
			}

			@QonfigAttributeGetter("config-dir")
			public CompiledExpression getConfigDir() {
				return theConfigDir;
			}

			public List<String> getOldConfigNames() {
				return Collections.unmodifiableList(theOldConfigNames);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));

				theConfigName = session.getAttributeText("config-name");
				theConfigDir = session.getAttributeExpression("config-dir");

				theOldConfigNames.clear();
				for (QonfigElement ch : session.getChildren("old-config-name"))
					theOldConfigNames.add(ch.getValueText());

				isBackup = session.getAttribute("backup", boolean.class);
				theApplicationEnvironment = session.get(APP_ENVIRONMENT_KEY, AppEnvironment.class);

				((ObservableModelSet.Builder) session.getExpressoEnv().getModels()).withMaker(ExpressoConfigV0_1.CONFIG_NAME,
					new ConfigValueMaker(), session.getElement().getPositionInFile());

				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			// @Override
			// protected ModelValueElement.Def<?, ?> interpretValue(String name, ExpressoQIS child, ModelValueElement.Def<?, ?> previous,
			// ObservableModelSet.Builder builder) throws QonfigInterpretationException {
			// ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> configV;
			// try {
			// configV = (ModelValueSynth<SettableValue<?>, SettableValue<ObservableConfig>>) builder
			// .getComponent(ExpressoConfigV0_1.CONFIG_NAME);
			// } catch (ModelException e) {
			// throw new QonfigInterpretationException("But we just installed it!", child.getElement().getPositionInFile(), 0, e);
			// }
			// String path = child.getAttributeText("config-path");
			//
			// if (path == null)
			// path = name;
			// ConfigModelValue<?, ?, ?> mv = child.interpret(ConfigModelValue.class);
			// builder.withMaker(name, createConfigValue(mv, configV, path, child), child.getElement().getPositionInFile());
			// return mv instanceof ModelValueElement.Def ? (ModelValueElement.Def<?, ?>) mv : null;
			// }

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}

			private <T, M2, MV extends M2> CompiledModelValue<M2> createConfigValue(ConfigModelValue<T, M2, MV> configValue,
				InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> configV, String path, ExpressoQIS session)
					throws QonfigInterpretationException {
				ExpressoQIS formatSession = session.forChildren("format").peekFirst();
				CompiledModelValue<?> formatCreator = formatSession == null ? null : formatSession.interpret(CompiledModelValue.class);
				return CompiledModelValue.of("value", configValue.getType().getModelType(), () -> {
					configValue.init();
					TypeToken<T> formatType = (TypeToken<T>) configValue.getType()
						.getType(configValue.getType().getModelType().getTypeCount() - 1);
					InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> formatContainer;
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
					return new InterpretedValueSynth<M2, MV>() {
						@Override
						public ModelType<M2> getModelType() {
							return configValue.getType().getModelType();
						}

						@Override
						public ModelInstanceType<M2, MV> getType() {
							return configValue.getType();
						}

						@Override
						public MV get(ModelSetInstance msi) throws ModelInstantiationException, IllegalStateException {
							ObservableConfig config = configV.get(msi).get();
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
						public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
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

			private class ConfigValueMaker implements CompiledModelValue<SettableValue<?>> {
				@Override
				public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) {
					return ModelTypes.Value;
				}

				@Override
				public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> createSynthesizer(
					InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir;
					if (theConfigDir != null)
						configDir = theConfigDir.evaluate(ModelTypes.Value.forType(BetterFile.class), env);
					else {
						configDir = InterpretedValueSynth.of(ModelTypes.Value.forType(BetterFile.class), msi -> {
							String prop = System.getProperty(theConfigName + ".config");
							if (prop != null)
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), prop), prop);
							else
								return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), "./" + theConfigName),
									"./" + theConfigName);
						});
					}

					return InterpretedValueSynth.of(ModelTypes.Value.forType(ObservableConfig.class), msi -> {
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
							public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
								return InterpretedValueSynth.literal(TypeTokens.get().STRING, "Unspecified Application",
									"Unspecified Application");
							}

							@Override
							public InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getIcon() {
								return InterpretedValueSynth.literal(TypeTokens.get().of(Image.class), null, "No Image");
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
	}*/
}
