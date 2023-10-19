package org.observe.expresso.qonfig;

import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JDialog;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigPersistence;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.ObservableConfigPath;
import org.observe.config.SyncValueSet;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.WindowPopulation;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.BetterFile;
import org.qommons.io.FileBackups;
import org.qommons.io.Format;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;
import org.qommons.io.TextParseException;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

public abstract class ObservableModelElement extends ExElement.Abstract {
	public static final String PREVENT_MODEL_BUILDING = "ObservableModel.Prevent.Model.Building";

	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "abst-model", interpretation = Interpreted.class)
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
			super.doUpdate(session);
			theModelPath = session.get(ExpressoBaseV0_1.PATH_KEY, String.class);
			String name = getName();
			if (theModelPath == null) {
				if (name == null)
					name = "";
				theModelPath = name;
			} else if (name != null) {
				if (theModelPath.isEmpty())
					theModelPath = name;
				else
					theModelPath += "." + name;
			}
			session.put(ExpressoBaseV0_1.PATH_KEY, theModelPath);

			BetterList<ExpressoQIS> valueSessions = session.forChildren("value");
			ExElement.syncDefs(getValueType(), theValues, valueSessions);

			// Installing this control system because sometimes we want to control how the models are interpreted from above
			Object building = session.get(PREVENT_MODEL_BUILDING);
			boolean doBuild;
			if (building == null)
				doBuild = true;
			else if (building instanceof Predicate)
				doBuild = !((Predicate<Object>) building).test(this);
			else if (building instanceof Boolean)
				doBuild = Boolean.TRUE.equals(building);
			else {
				reporting().warn("Unrecognized " + PREVENT_MODEL_BUILDING + " type: " + building.getClass().getName());
				doBuild = true;
			}
			if (doBuild) {
				for (ModelValueElement.Def<?, ?> value : theValues)
					value.populate((ObservableModelSet.Builder) session.getExpressoEnv().getModels());
			}
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
			Collections.sort(theValues,
				(mv1, mv2) -> Integer.compare(mv1.reporting().getPosition().getPosition(), mv2.reporting().getPosition().getPosition()));

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

		public abstract ObservableModelElement create(ExElement parent);
	}

	private final List<ModelValueElement<?, ?>> theValues;

	protected ObservableModelElement(Object id) {
		super(id);
		theValues = new ArrayList<>();
	}

	public List<? extends ExElement> getValues() {
		return Collections.unmodifiableList(theValues);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

		CollectionUtils.synchronize(theValues, myInterpreted.getValues(), //
			(inst, interp) -> inst.getIdentity() == interp.getIdentity())//
		.adjust(new CollectionUtils.CollectionSynchronizer<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>>() {
			@Override
			public boolean getOrder(ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element) {
				return true;
			}

			@Override
			public ElementSyncAction leftOnly(
				ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element) {
				return element.remove();
			}

			@Override
			public ElementSyncAction rightOnly(
				ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element) {
				ModelValueElement<?, ?> instance = element.getRightValue().create();
				if (instance != null) {
					instance.update(element.getRightValue(), ObservableModelElement.this);
					return element.useValue(instance);
				} else
					return element.remove();
			}

			@Override
			public ElementSyncAction common(ElementSyncInput<ModelValueElement<?, ?>, ModelValueElement.Interpreted<?, ?, ?>> element) {
				element.getLeftValue().update(element.getRightValue(), ObservableModelElement.this);
				return element.preserve();
			}
		}, CollectionUtils.AdjustmentOrder.RightOrder);
	}

	@Override
	public void instantiated() {
		super.instantiated();
		for (ModelValueElement<?, ?> value : theValues)
			value.instantiated();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		for (ModelValueElement<?, ?> value : theValues)
			value.instantiate(myModels);
	}

	public static class ModelSetElement extends ExElement.Abstract {
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "models", interpretation = Interpreted.class)
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

		public ModelSetElement(Object id) {
			super(id);
			theSubModels = new ArrayList<>();
		}

		@QonfigChildGetter("model")
		public List<? extends ObservableModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getIdentity())//
			.simple(child -> child.create(ModelSetElement.this))//
			.rightOrder()//
			.onRight(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ModelSetElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.onCommon(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ModelSetElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			for (ObservableModelElement subModel : theSubModels)
				subModel.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			for (ObservableModelElement subModel : theSubModels)
				subModel.instantiate(myModels);
		}
	}

	public static class DefaultModelElement extends ObservableModelElement {
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "model", interpretation = Interpreted.class)
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
			public DefaultModelElement create(ExElement parent) {
				return new DefaultModelElement(getIdentity());
			}
		}

		private final List<DefaultModelElement> theSubModels;

		public DefaultModelElement(Object id) {
			super(id);
			theSubModels = new ArrayList<>();
		}

		public List<? extends DefaultModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.simple(child -> child.create(DefaultModelElement.this))//
			.rightOrder()//
			.onRight(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), DefaultModelElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommon(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), DefaultModelElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			for (DefaultModelElement subModel : theSubModels)
				subModel.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			for (DefaultModelElement subModel : theSubModels)
				subModel.instantiate(myModels);
		}
	}

	public static class LocalModelElementDef extends DefaultModelElement.Def<DefaultModelElement> {
		public LocalModelElementDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}
	}

	public static class ExtModelElement extends ObservableModelElement {
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "ext-model", interpretation = Interpreted.class)
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
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				ExElement.syncDefs(ExtModelElement.Def.class, theSubModels, session.forChildren("sub-model"));
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

			@Override
			public List<? extends ExtModelValueElement.Interpreted<?, ?>> getValues() {
				return (List<? extends ExtModelValueElement.Interpreted<?, ?>>) super.getValues();
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
			public ExtModelElement create(ExElement parent) {
				return new ExtModelElement(getIdentity());
			}
		}

		private final List<ExtModelElement> theSubModels;

		public ExtModelElement(Object id) {
			super(id);
			theSubModels = new ArrayList<>();
		}

		public List<ExtModelElement> getSubModels() {
			return theSubModels;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.simple(child -> child.create(ExtModelElement.this))//
			.rightOrder()//
			.onRight(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ExtModelElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommon(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ExtModelElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.adjust();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			for (ExtModelElement subModel : theSubModels)
				subModel.instantiated();
		}
	}

	public static class ConfigModelElement extends ObservableModelElement {
		public static final String APP_ENVIRONMENT_KEY = "EXPRESSO.APP.ENVIRONMENT";

		@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG, qonfigType = "config", interpretation = Interpreted.class)
		public static class Def<M extends ConfigModelElement> extends ObservableModelElement.Def<M, ConfigModelValue.Def<M>> {
			private String theConfigName;
			private CompiledExpression theConfigDir;
			private final List<OldConfigName> theOldConfigNames;
			private boolean isBackup;
			private AppEnvironment theApplicationEnvironment;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theOldConfigNames = new ArrayList<>();
			}

			@QonfigAttributeGetter("config-name")
			public String getConfigName() {
				return theConfigName;
			}

			@QonfigAttributeGetter("config-dir")
			public CompiledExpression getConfigDir() {
				return theConfigDir;
			}

			@QonfigChildGetter("old-config-name")
			public List<OldConfigName> getOldConfigNames() {
				return Collections.unmodifiableList(theOldConfigNames);
			}

			@QonfigAttributeGetter("backup")
			public boolean isBackup() {
				return isBackup;
			}

			@Override
			protected Class<ConfigModelValue.Def<M>> getValueType() {
				return (Class<ConfigModelValue.Def<M>>) (Class<?>) ConfigModelValue.Def.class;
			}

			@Override
			public void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				theConfigName = session.getAttributeText("config-name");
				theConfigDir = getAttributeExpression("config-dir", session);

				ExElement.syncDefs(OldConfigName.class, theOldConfigNames, session.forChildren("old-config-name"));

				isBackup = session.getAttribute("backup", boolean.class);
				theApplicationEnvironment = session.get(APP_ENVIRONMENT_KEY, AppEnvironment.class);

				ConfigValueMaker configValueMaker = new ConfigValueMaker(theConfigDir, theConfigName, isBackup(), //
					QommonsUtils.map(getOldConfigNames(), ocn -> ocn.getOldConfigName(), true), theApplicationEnvironment);
				((ObservableModelSet.Builder) session.getExpressoEnv().getModels()).withMaker(ExpressoConfigV0_1.CONFIG_NAME,
					configValueMaker, session.getElement().getPositionInFile());

				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
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

			private static class ConfigValueMaker implements CompiledModelValue<SettableValue<?>> {
				private final CompiledExpression theConfigDir;
				private final String theConfigName;
				private final boolean isBackup;
				private final List<String> theOldConfigNames;
				private final AppEnvironment theApplicationEnvironment;

				ConfigValueMaker(CompiledExpression configDir, String configName, boolean backup, List<String> oldConfigNames,
					AppEnvironment applicationEnvironment) {
					theConfigDir = configDir;
					isBackup = backup;
					theConfigName = configName;
					theOldConfigNames = oldConfigNames;
					theApplicationEnvironment = applicationEnvironment;
				}

				@Override
				public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) {
					return ModelTypes.Value;
				}

				@Override
				public InterpretedValueSynth<SettableValue<?>, ?> interpret(InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir;
					if (theConfigDir != null)
						configDir = theConfigDir.interpret(ModelTypes.Value.forType(BetterFile.class), env);
					else {
						configDir = InterpretedValueSynth.simple(ModelTypes.Value.forType(BetterFile.class),
							ModelValueInstantiator.of(msi -> {
								String prop = System.getProperty(theConfigName + ".config");
								if (prop != null)
									return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), prop), prop);
								else
									return ObservableModelSet.literal(BetterFile.at(new NativeFileSource(), "./" + theConfigName),
										"./" + theConfigName);
							}));
					}
					return new Interpreted(configDir);
				}

				class Interpreted implements InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfig>> {
					private final InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> theInterpretedConfigDir;

					Interpreted(InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> configDir) {
						theInterpretedConfigDir = configDir;
					}

					@Override
					public ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfig>> getType() {
						return ModelTypes.Value.forType(ObservableConfig.class);
					}

					@Override
					public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
						return BetterList.of(theInterpretedConfigDir);
					}

					@Override
					public ModelValueInstantiator<SettableValue<ObservableConfig>> instantiate() {
						return new Instantiator(theInterpretedConfigDir == null ? null : theInterpretedConfigDir.instantiate(), //
							theConfigName, isBackup, theOldConfigNames, //
							theApplicationEnvironment == null ? null : theApplicationEnvironment.instantiate());
					}
				}

				static class Instantiator implements ModelValueInstantiator<SettableValue<ObservableConfig>> {
					private final ModelValueInstantiator<SettableValue<BetterFile>> theInterpretedConfigDir;
					private final String theConfigName;
					private final boolean isBackup;
					private final List<String> theOldConfigNames;
					private final AppEnvironment.Instantiator theApplicationEnvironment;

					public Instantiator(ModelValueInstantiator<SettableValue<BetterFile>> interpretedConfigDir, String configName,
						boolean backup, List<String> oldConfigNames,
						org.observe.expresso.qonfig.AppEnvironment.Instantiator applicationEnvironment) {
						theInterpretedConfigDir = interpretedConfigDir;
						theConfigName = configName;
						isBackup = backup;
						theOldConfigNames = oldConfigNames;
						theApplicationEnvironment = applicationEnvironment;
					}

					@Override
					public void instantiate() {
						if (theInterpretedConfigDir != null)
							theInterpretedConfigDir.instantiate();
					}

					@Override
					public SettableValue<ObservableConfig> get(ModelSetInstance models)
						throws ModelInstantiationException, IllegalStateException {
						BetterFile configDirFile = theInterpretedConfigDir == null ? null : theInterpretedConfigDir.get(models).get();
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
						ObservableConfig config = ObservableConfig.createRoot(theConfigName, null,
							owner -> new StampedLockingStrategy(owner, ThreadConstraint.ANY));
						ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
						boolean loaded = false;
						if (configFile.exists()) {
							try {
								try (InputStream configStream = new BufferedInputStream(configFile.read());
									Transaction t = config.lock(true, null)) {
									ObservableConfig.readXml(config, configStream, encoding);
								}
								loaded = true;
							} catch (IOException | TextParseException e) {
								System.out.println("Could not read config file " + configFile.getPath());
								e.printStackTrace(System.out);
							}
						}
						boolean[] closingWithoutSave = new boolean[1];
						AppEnvironment.Instantiator app = theApplicationEnvironment != null ? theApplicationEnvironment
							: new AppEnvironment.Instantiator(
								ModelValueInstantiator.literal(SettableValue.of(String.class, "Unspecified Application", "Not Settable"),
									"Unspecified Application"), //
								ModelValueInstantiator.literal(SettableValue.of(Image.class, null, "Not Settable"), "No Image"));
						if (loaded)
							installConfigPersistence(config, configFile, backups, closingWithoutSave);
						else if (backups != null && !backups.getBackups().isEmpty()) {
							restoreBackup(true, config, backups, () -> {
								config.setName(theConfigName);
								installConfigPersistence(config, configFile, backups, closingWithoutSave);
							}, () -> {
								config.setName(theConfigName);
								installConfigPersistence(config, configFile, backups, closingWithoutSave);
							}, app, closingWithoutSave, models);
						} else {
							config.setName(theConfigName);
							installConfigPersistence(config, configFile, backups, closingWithoutSave);
						}
						return SettableValue.of(ObservableConfig.class, config, "Not Settable");
					}

					@Override
					public SettableValue<ObservableConfig> forModelCopy(SettableValue<ObservableConfig> value,
						ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
						return value; // Config doesn't change between model copies
					}
				}
			}

			static void installConfigPersistence(ObservableConfig config, BetterFile configFile, FileBackups backups,
				boolean[] closingWithoutSave) {
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

			static void restoreBackup(boolean fromError, ObservableConfig config, FileBackups backups, Runnable onBackup,
				Runnable onNoBackup, AppEnvironment.Instantiator app, boolean[] closingWithoutSave, ModelSetInstance msi)
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
				boolean[] backedUp = new boolean[1];
				ObservableValue<String> title = (app == null || app.title == null) ? ObservableValue.of("Unnamed Application")
					: app.title.get(msi);
				ObservableValue<Image> icon = (app == null || app.icon == null) ? ObservableValue.of(Image.class, null) : app.icon.get(msi);
				PanelPopulation.DialogBuilder<JDialog, ?> dialog = WindowPopulation.populateDialog(null, null, false);
				dialog.modal(true)//
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
						dialog.getWindow().setVisible(false);
					}, btn -> btn.disableWith(selectedBackup.map(t -> t == null ? "Select a Backup" : null)));
				}).run(null).getWindow();
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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (env.getProperty(ConfigModelValue.FORMAT_SET_KEY, ObservableConfigFormatSet.class) == null)
					env.setProperty(ConfigModelValue.FORMAT_SET_KEY, new ObservableConfigFormatSet());
				super.doUpdate(env);
			}

			@Override
			public ConfigModelElement create(ExElement parent) {
				return new ConfigModelElement(getIdentity());
			}
		}

		public ConfigModelElement(Object id) {
			super(id);
		}

		@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG, qonfigType = "old-config-name")
		static class OldConfigName extends ExElement.Def.Abstract<ExElement> {
			private String theOldConfigName;

			OldConfigName(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@QonfigAttributeGetter
			public String getOldConfigName() {
				return theOldConfigName;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theOldConfigName = session.getValueText();
			}
		}
	}
}
