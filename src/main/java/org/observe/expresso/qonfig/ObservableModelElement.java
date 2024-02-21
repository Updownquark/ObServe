package org.observe.expresso.qonfig;

import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Window;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.observe.SettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigPersistence;
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
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ModelValueElement.CompiledSynth;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.WindowPopulation;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.BetterFile;
import org.qommons.io.ErrorReporting;
import org.qommons.io.FileBackups;
import org.qommons.io.Format;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;
import org.qommons.io.TextParseException;
import org.qommons.threading.QommonsTimer;

/**
 * <p>
 * This class is the model component of the unification of {@link ObservableModelSet} with {@link ExElement}. {@link ModelValueElement} is
 * the value component of the unification.
 * </p>
 * <p>
 * This class is an {@link ExElement} that represents an Observable model. Specifically, this class represents an
 * {@link org.observe.expresso.ObservableModelSet.ModelInstantiator ObservableModelSet.ModelInstantiator}, but it does not implement that
 * class.
 * </p>
 * <p>
 * The {@link ObservableModelElement.Def ObservableModelElement.Def} class represents an {@link ObservableModelSet}, and
 * {@link ObservableModelElement.Interpreted ObservableModelElement.Interpreted} represents an
 * {@link org.observe.expresso.ObservableModelSet.InterpretedModelSet ObservableModelSet.InterpretedModelSet}.
 * </p>
 * <p>
 * Due to the fact that models are needed before {@link ExElement} implementations can be initialized, it's not feasible that the element
 * representations of model set structures can implement the model set interfaces. Rather, this class searches for
 * {@link ModelValueElement}-typed elements in the Observable model structures it receives and incorporates them as the model element's
 * children.
 * </p>
 */
public abstract class ObservableModelElement extends ExElement.Abstract {
	/**
	 * Definition of an {@link ObservableModelElement}
	 *
	 * @param <M> The sub-type of {@link ObservableModelElement} to create
	 * @param <V> The sub-type of {@link ModelValueElement} for this element's values
	 */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
		qonfigType = "abst-model",
		interpretation = Interpreted.class,
		instance = ObservableModelElement.class)
	public static abstract class Def<M extends ObservableModelElement, V extends ModelValueElement.Def<?, ?>>
	extends ExElement.Def.Abstract<M> {
		private String theModelPath;
		private final List<V> theValues;

		/**
		 * @param parent The parent element of this model element
		 * @param qonfigType The Qonfig type of this model element
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			theValues = new ArrayList<>();
		}

		/** @return The path of this model in its parent (or empty ("") if it is the root model) */
		public String getModelPath() {
			return theModelPath;
		}

		/** @return The name of this model */
		public String getName() {
			return getAddOnValue(ExNamed.Def.class, ExNamed.Def::getName);
		}

		/** @return This model's values */
		@QonfigChildGetter("value")
		public List<V> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		/** @return The sub-type of {@link ModelValueElement} for this element's values */
		protected abstract Class<V> getValueType();

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
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
			session.put(ModelValueElement.PATH_KEY, theModelPath);

			BetterList<ExpressoQIS> valueSessions = session.forChildren("value");
			syncChildren(getValueType(), theValues, valueSessions);

			int i = 0;
			ObservableModelSet.Builder builder;
			if (getExpressoEnv().getModels() instanceof ObservableModelSet.Builder)
				builder = (ObservableModelSet.Builder) getExpressoEnv().getModels();
			else {
				builder = getExpressoEnv().getModels().wrap(name);
				setExpressoEnv(getExpressoEnv().with(builder));
			}
			for (ModelValueElement.Def<?, ?> value : theValues) {
				value.populate(builder, valueSessions.get(i));
				i++;
			}
			i = 0;
			for (ExpressoQIS vs : valueSessions) {
				V value = theValues.get(i);
				value.prepareModelValue(vs.asElement(value.getQonfigType()));
				i++;
			}
		}

		/**
		 * @param parent The interpreted parent for the interpreted model
		 * @return The interpreted model element
		 * @throws ExpressoInterpretationException If the model element could not be interpreted
		 */
		public abstract Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;
	}

	/**
	 * Interpretation of an {@link ObservableModelElement}
	 *
	 * @param <M> The sub-type of model element to create
	 */
	public static abstract class Interpreted<M extends ObservableModelElement> extends ExElement.Interpreted.Abstract<M> {
		private final List<ModelValueElement.Interpreted<?, ?, ?>> theValues;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this model element
		 */
		protected Interpreted(Def<? super M, ?> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theValues = new ArrayList<>();
		}

		@Override
		public Def<? super M, ?> getDefinition() {
			return (Def<? super M, ?>) super.getDefinition();
		}

		/** @return This model's sub-models */
		protected abstract List<? extends ObservableModelElement.Interpreted<?>> getSubModels();

		/** @return This model's values */
		public List<? extends ModelValueElement.Interpreted<?, ?, ?>> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		/**
		 * Initializes or updates this model element
		 *
		 * @param env The expresso environment to use to interpret expressions
		 * @throws ExpressoInterpretationException If this model element could not be interpreted
		 */
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
			try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
				for (String name : env.getModels().getComponentNames()) {
					InterpretedModelComponentNode<?, ?> mv = env.getModels().getLocalComponent(name).interpret(env);
					ModelValueElement.Interpreted<?, ?, ?> modelValue = findModelValue(mv.getValue());
					if (modelValue != null && modelValue.getDefinition().getParentElement() == getDefinition())
						theValues.add(modelValue);
				}
				Collections.sort(theValues, (mv1, mv2) -> Integer.compare(mv1.reporting().getPosition().getPosition(),
					mv2.reporting().getPosition().getPosition()));

				super.doUpdate(env);
			}
		}

		/**
		 * @param modelPath The path of the model element to get
		 * @return This model element or one of its {@link #getSubModels() sub-models} with the given model path, or null if no such model
		 *         element exists
		 */
		public Interpreted<?> getInterpretingModel(String modelPath) {
			if (modelPath.startsWith(getDefinition().getModelPath())) {
				if (modelPath.length() == getDefinition().getModelPath().length())
					return this;
				for (ObservableModelElement.Interpreted<?> subModel : getSubModels()) {
					if (modelPath.startsWith(subModel.getDefinition().getModelPath()))
						return subModel.getInterpretingModel(modelPath);
				}
			} else {
				ExElement.Interpreted<?> parent = getParentElement();
				while (parent != null && !(parent instanceof ModelSetElement.Interpreted))
					parent = parent.getParentElement();
				if (parent != null)
					return ((ModelSetElement.Interpreted<?>) parent).getInterpretingModel(modelPath);
			}
			return null;
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

		/**
		 * @param parent The parent element for the model element
		 * @return The model element
		 */
		public abstract ObservableModelElement create(ExElement parent);
	}

	private final List<ModelValueElement<?>> theValues;

	/** @param id The element identifier for this model element */
	protected ObservableModelElement(Object id) {
		super(id);
		theValues = new ArrayList<>();
	}

	/** @return This model's values */
	public List<? extends ExElement> getValues() {
		return Collections.unmodifiableList(theValues);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		// Find all the model value instances and initialize them with this as their parent before they are initialized properly
		for (ModelComponentId name : getModels().getComponents()) {
			ModelValueInstantiator<?> component = getModels().getComponent(name);
			if (component instanceof ModelValueElement)
				theValues.add((ModelValueElement<?>) component);
		}

		ObservableModelElement.Interpreted<?> myInterpreted = (ObservableModelElement.Interpreted<?>) interpreted;

		Collections.sort(theValues,
			(mv1, mv2) -> Integer.compare(mv1.reporting().getPosition().getPosition(), mv2.reporting().getPosition().getPosition()));
		for (int v = 0; v < theValues.size(); v++)
			theValues.get(v).update(myInterpreted.getValues().get(v), this);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		for (ModelValueElement<?> value : theValues)
			value.instantiated();
	}

	/** Represents a &lt;models> element consisting of some number of models */
	public static class ModelSetElement extends ExElement.Abstract {
		/** The XML name of this element */
		public static final String MODELS = "models";

		/**
		 * Definition of a {@link ModelSetElement}
		 *
		 * @param <M> The sub-type of {@link ModelSetElement} to create
		 */
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
			qonfigType = "models",
			interpretation = Interpreted.class,
			instance = ModelSetElement.class)
		public static class Def<M extends ModelSetElement> extends ExElement.Def.Abstract<M> {
			private final List<ObservableModelElement.Def<?, ?>> theSubModels;

			/**
			 * @param parent The parent element for this models element
			 * @param qonfigType The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theSubModels = new ArrayList<>();
			}

			/** @return The {@link ObservableModelElement.Def ObservableModelElement.Def} sub-type for this model set's models */
			protected Class<? extends ObservableModelElement.Def<?, ?>> getModelType() {
				return (Class<? extends ObservableModelElement.Def<?, ?>>) (Class<?>) ObservableModelElement.Def.class;
			}

			/** @return The models in this &lt;models> element */
			// Would rather name this "getModels", but that name's taken in ExElement.Def
			@QonfigChildGetter("model")
			public List<? extends ObservableModelElement.Def<?, ?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				session.put(ModelValueElement.PATH_KEY, session.getElement().getType().getName());
				ObservableModelSet.Builder builder;
				if (getExpressoEnv().getModels() instanceof ObservableModelSet.Builder)
					builder = (ObservableModelSet.Builder) getExpressoEnv().getModels();
				else {
					builder = ObservableModelSet.build(getQonfigType().getName(), ObservableModelSet.JAVA_NAME_CHECKER);
					if (nonTrivial(getExpressoEnv().getModels()))
						builder.withAll(getExpressoEnv().getModels());
					session.setExpressoEnv(session.getExpressoEnv().with(builder));
					setExpressoEnv(session.getExpressoEnv());
				}
				BiConsumer<ObservableModelElement.Def<?, ?>, ExpressoQIS> sessionUpdater = (m, s) -> {
					s.setExpressoEnv(s.getExpressoEnv().with(//
						builder.createSubModel(s.attributes().get("named", "name").getText(), s.getElement().getPositionInFile())));
				};
				CollectionUtils
				.synchronize(theSubModels, session.forChildren("model"),
					(me, ms) -> ExElement.typesEqual(me.getElement(), ms.getElement()))//
				.<QonfigInterpretationException> simpleX(ms -> {
					ObservableModelElement.Def<?, ?> subModelEl = ms.interpret(ObservableModelElement.Def.class);
					sessionUpdater.accept(subModelEl, ms);
					subModelEl.update(ms);
					return subModelEl;
				})//
				.rightOrder()//
				.onCommonX(el -> {
					sessionUpdater.accept(el.getLeftValue(), el.getRightValue());
					el.getLeftValue().update(el.getRightValue());
				})//
				.adjust();
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

			/**
			 * @param parent The parent element for the interpreted model set
			 * @return The interpreted model set
			 */
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * Interpretation of a {@link ModelSetElement}
		 *
		 * @param <M> The sub-type of {@link ModelSetElement} to create
		 */
		public static class Interpreted<M extends ModelSetElement> extends ExElement.Interpreted.Abstract<M> {
			private final List<ObservableModelElement.Interpreted<?>> theSubModels;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this model set element
			 */
			public Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theSubModels = new ArrayList<>();
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			/** @return The models in this &lt;models> element */
			public List<ObservableModelElement.Interpreted<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				// First create the models so all the linkages can happen
				syncChildren(getDefinition().getSubModels(), theSubModels, def -> def.interpret(this), (i, mEnv) -> {
				});
				super.doUpdate(env);
				// Now call the update method
				syncChildren(getDefinition().getSubModels(), theSubModels, def -> def.interpret(this), (i, mEnv) -> i.updateSubModel(mEnv));
			}

			/**
			 * @param modelPath The path of the model element to get
			 * @return One of this model set's {@link #getSubModels() sub-models} with the given model path, or null if no such model
			 *         element exists
			 */
			public ObservableModelElement.Interpreted<?> getInterpretingModel(String modelPath) {
				for (ObservableModelElement.Interpreted<?> subModel : getSubModels()) {
					if (modelPath.startsWith(subModel.getDefinition().getModelPath()))
						return subModel.getInterpretingModel(modelPath);
				}
				throw new IllegalStateException("Could not find model for path " + modelPath);
			}
		}

		private final List<ObservableModelElement> theSubModels;

		/** @param id The element identifier for this model set element */
		protected ModelSetElement(Object id) {
			super(id);
			theSubModels = new ArrayList<>();
		}

		/** @return The models in this &lt;models> element */
		@QonfigChildGetter("model")
		public List<? extends ObservableModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getIdentity())//
			.<ModelInstantiationException> simpleX(child -> child.create(ModelSetElement.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ModelSetElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.onCommonX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ModelSetElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(), e);
				}
			})//
			.adjust();
		}

		@Override
		public void instantiated() throws ModelInstantiationException {
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

	/** Default {@link ObservableModelElement} implementation for the &lt;model> element type */
	public static class DefaultModelElement extends ObservableModelElement {
		/**
		 * Definition for a {@link ObservableModelElement.DefaultModelElement}
		 *
		 * @param <M> The sub-type of {@link ObservableModelElement.DefaultModelElement} to create
		 */
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
			qonfigType = "model",
			interpretation = Interpreted.class,
			instance = DefaultModelElement.class)
		public static class Def<M extends DefaultModelElement>
		extends ObservableModelElement.Def<M, ModelValueElement.CompiledSynth<?, ?>> {
			private final List<DefaultModelElement.Def<?>> theSubModels;

			/**
			 * @param parent The parent element of this model element
			 * @param qonfigType The Qonfig type of this model element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theSubModels = new ArrayList<>();
			}

			@Override
			protected Class<CompiledSynth<?, ?>> getValueType() {
				return (Class<ModelValueElement.CompiledSynth<?, ?>>) (Class<?>) ModelValueElement.CompiledSynth.class;
			}

			/**
			 * @return The {@link ObservableModelElement.DefaultModelElement.Def DefaultModelElement.Def} sub-type for this model's
			 *         sub-models
			 */
			protected Class<? extends DefaultModelElement.Def<?>> getModelType() {
				return (Class<? extends DefaultModelElement.Def<?>>) (Class<?>) DefaultModelElement.Def.class;
			}

			/** @return This model's sub-models */
			@QonfigChildGetter("sub-model")
			public List<? extends DefaultModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				syncChildren(DefaultModelElement.Def.class, theSubModels, session.forChildren("sub-model"), (sub, subS) -> {
					String name = ((ExNamed.Def) sub.getAddOn(ExNamed.Def.class)).getName();
					ObservableModelSet subModel = subS.getExpressoEnv().getModels().getSubModelIfExists(name);
					if (subModel != null)
						subS = subS.setExpressoEnv(subS.getExpressoEnv().with(subModel));
					sub.update(subS);
				});
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * Interpretation for a {@link ObservableModelElement.DefaultModelElement}
		 *
		 * @param <M> The sub-type of {@link ObservableModelElement.DefaultModelElement} to create
		 */
		public static class Interpreted<M extends DefaultModelElement> extends ObservableModelElement.Interpreted<M> {
			private final List<Interpreted<?>> theSubModels;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this model element
			 */
			protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theSubModels = new ArrayList<>();
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			@Override
			public List<? extends Interpreted<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				// First, initialize the children so all the linkages can happen
				syncChildren(getDefinition().getSubModels(), theSubModels, def -> def.interpret(this), (i, mEnv) -> {
				});
				super.doUpdate(env);

				// Now call the update methods
				syncChildren(getDefinition().getSubModels(), theSubModels, def -> def.interpret(this), (i, mEnv) -> i.updateSubModel(mEnv));
			}

			@Override
			public DefaultModelElement create(ExElement parent) {
				return new DefaultModelElement(getIdentity());
			}
		}

		private final List<DefaultModelElement> theSubModels;

		/** @param id The element identifier for this model element */
		protected DefaultModelElement(Object id) {
			super(id);
			theSubModels = new ArrayList<>();
		}

		/** @return This model's sub-models */
		public List<? extends DefaultModelElement> getSubModels() {
			return Collections.unmodifiableList(theSubModels);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.<ModelInstantiationException> simpleX(child -> child.create(DefaultModelElement.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), DefaultModelElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommonX(element -> {
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
		public void instantiated() throws ModelInstantiationException {
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

	/** {@link ObservableModelElement.DefaultModelElement} sub-class for local &lt;model> declaration under an element */
	public static class LocalModelElementDef extends DefaultModelElement.Def<DefaultModelElement> {
		/**
		 * @param parent The parent element for this element
		 * @param qonfigType The Qonfig type of this element
		 */
		public LocalModelElementDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}
	}

	/** Default {@link ObservableModelElement} implementation for the &lt;ext-model> element */
	public static class ExtModelElement extends ObservableModelElement {
		/**
		 * Definition for a {@link ObservableModelElement.ExtModelElement}
		 *
		 * @param <M> The sub-type of {@link ObservableModelElement.ExtModelElement} to create
		 */
		@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE,
			qonfigType = "ext-model",
			interpretation = Interpreted.class,
			instance = ExtModelElement.class)
		public static class Def<M extends ExtModelElement> extends ObservableModelElement.Def<M, ExtModelValueElement.Def<?>> {
			private final List<ExtModelElement.Def<?>> theSubModels;

			/**
			 * @param parent The parent element of this model element
			 * @param qonfigType The Qonfig type of this model element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theSubModels = new ArrayList<>();
			}

			@Override
			protected Class<ExtModelValueElement.Def<?>> getValueType() {
				return (Class<ExtModelValueElement.Def<?>>) (Class<?>) ExtModelValueElement.Def.class;
			}

			/** @return The {@link ObservableModelElement.ExtModelElement.Def ExtModelElement.Def} sub-type for this model's sub-models */
			protected Class<? extends ExtModelElement.Def<?>> getModelType() {
				return (Class<? extends ExtModelElement.Def<?>>) (Class<?>) ExtModelElement.Def.class;
			}

			/** @return This model's sub-models */
			@QonfigChildGetter("sub-model")
			public List<? extends ExtModelElement.Def<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				syncChildren(ExtModelElement.Def.class, theSubModels, session.forChildren("sub-model"));
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}
		}

		/**
		 * Interpretation for a {@link ObservableModelElement.ExtModelElement}
		 *
		 * @param <M> The sub-type of {@link ObservableModelElement.ExtModelElement} to create
		 */
		public static class Interpreted<M extends ExtModelElement> extends ObservableModelElement.Interpreted<M> {
			private final List<Interpreted<?>> theSubModels;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this model element
			 */
			protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
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

			@Override
			public List<? extends Interpreted<?>> getSubModels() {
				return Collections.unmodifiableList(theSubModels);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				// First, initialize the children so all the linkages can happen
				syncChildren(getDefinition().getSubModels(), theSubModels, def -> def.interpret(this), (i, mEnv) -> {
				});
				super.doUpdate(env);

				// Now call the update methods
				syncChildren(getDefinition().getSubModels(), theSubModels, def -> def.interpret(this), (i, mEnv) -> i.updateSubModel(mEnv));
			}

			@Override
			public ExtModelElement create(ExElement parent) {
				return new ExtModelElement(getIdentity());
			}
		}

		private final List<ExtModelElement> theSubModels;

		/** @param id The element ID for this model element */
		protected ExtModelElement(Object id) {
			super(id);
			theSubModels = new ArrayList<>();
		}

		/** @return This element's sub-models */
		public List<ExtModelElement> getSubModels() {
			return theSubModels;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;

			CollectionUtils.synchronize(theSubModels, myInterpreted.getSubModels(), //
				(widget, child) -> widget.getIdentity() == child.getDefinition().getIdentity())//
			.<ModelInstantiationException> simpleX(child -> child.create(ExtModelElement.this))//
			.rightOrder()//
			.onRightX(element -> {
				try {
					element.getLeftValue().update(element.getRightValue(), ExtModelElement.this);
				} catch (RuntimeException | Error e) {
					element.getRightValue().getDefinition().reporting().error(e.getMessage() == null ? e.toString() : e.getMessage(),
						e);
				}
			})//
			.onCommonX(element -> {
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
		public void instantiated() throws ModelInstantiationException {
			super.instantiated();
			for (ExtModelElement subModel : theSubModels)
				subModel.instantiated();
		}
	}

	/** Default {@link ObservableModelElement} implementation for the &lt;config> element */
	public static class ConfigModelElement extends ObservableModelElement {
		/**
		 * Definition for a {@link ObservableModelElement.ConfigModelElement}
		 *
		 * @param <M> The sub-type of {@link ObservableModelElement.ConfigModelElement} to create
		 */
		@ExElementTraceable(toolkit = ExpressoConfigV0_1.CONFIG,
			qonfigType = "config",
			interpretation = Interpreted.class,
			instance = ConfigModelElement.class)
		public static class Def<M extends ConfigModelElement> extends ObservableModelElement.Def<M, ConfigModelValue.Def<M>> {
			private String theConfigName;
			private CompiledExpression theConfigDir;
			private final List<OldConfigName> theOldConfigNames;
			private boolean isBackup;

			/**
			 * @param parent The parent element for this model element
			 * @param qonfigType The Qonfig type of this model element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theOldConfigNames = new ArrayList<>();
			}

			/** @return The config name for this model, determines where it attempts to load from */
			@QonfigAttributeGetter("config-name")
			public String getConfigName() {
				return theConfigName;
			}

			/** @return The config directory for this model, determines where it attempts to load from */
			@QonfigAttributeGetter("config-dir")
			public CompiledExpression getConfigDir() {
				return theConfigDir;
			}

			/** @return Old config names that this model may have had in the past */
			@QonfigChildGetter("old-config-name")
			public List<OldConfigName> getOldConfigNames() {
				return Collections.unmodifiableList(theOldConfigNames);
			}

			/**
			 * @return Whether this model attempts to back up its data in case of load failure or if the user desires to revert to a
			 *         previous version of the data
			 */
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

				syncChildren(OldConfigName.class, theOldConfigNames, session.forChildren("old-config-name"));

				isBackup = session.getAttribute("backup", boolean.class);

				ConfigValueMaker configValueMaker = new ConfigValueMaker(theConfigDir, theConfigName, isBackup(), //
					QommonsUtils.map(getOldConfigNames(), ocn -> ocn.getOldConfigName(), true), reporting());
				((ObservableModelSet.Builder) session.getExpressoEnv().getModels()).withMaker(ExpressoConfigV0_1.CONFIG_NAME,
					configValueMaker, session.getElement().getPositionInFile());

				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted<? extends M> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				return new Interpreted<>(this, parent);
			}

			private static class ConfigValueMaker implements CompiledModelValue<SettableValue<?>> {
				private final CompiledExpression theConfigDir;
				private final String theConfigName;
				private final boolean isBackup;
				private final List<String> theOldConfigNames;
				private final ErrorReporting theReporting;

				ConfigValueMaker(CompiledExpression configDir, String configName, boolean backup, List<String> oldConfigNames,
					ErrorReporting reporting) {
					theConfigDir = configDir;
					isBackup = backup;
					theConfigName = configName;
					theOldConfigNames = oldConfigNames;
					theReporting = reporting;
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
					public ModelValueInstantiator<SettableValue<ObservableConfig>> instantiate() throws ModelInstantiationException {
						return new Instantiator(theInterpretedConfigDir == null ? null : theInterpretedConfigDir.instantiate(), //
							theConfigName, isBackup, theOldConfigNames, theReporting);
					}
				}

				static class Instantiator
				implements ModelValueInstantiator<SettableValue<ObservableConfig>>, AppEnvironment.EnvironmentConfigurable {
					private final ModelValueInstantiator<SettableValue<BetterFile>> theInterpretedConfigDir;
					private final String theConfigName;
					private final boolean isBackup;
					private final List<String> theOldConfigNames;
					private final ErrorReporting theReporting;
					private AppEnvironment theAppEnv;

					public Instantiator(ModelValueInstantiator<SettableValue<BetterFile>> interpretedConfigDir, String configName,
						boolean backup, List<String> oldConfigNames, ErrorReporting reporting) {
						theInterpretedConfigDir = interpretedConfigDir;
						theConfigName = configName;
						isBackup = backup;
						theOldConfigNames = oldConfigNames;
						theReporting = reporting;
					}

					@Override
					public void setAppEnvironment(AppEnvironment env) {
						theAppEnv = env;
					}

					@Override
					public void instantiate() throws ModelInstantiationException {
						if (theInterpretedConfigDir != null)
							theInterpretedConfigDir.instantiate();
						if (theAppEnv != null)
							theAppEnv.instantiated();
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
								// Warning here so the loader doesn't throw an exception and abort.
								// This may not be fatal, as the user can choose a backup, or we can start from scratch.
								theReporting.warn("Could not read config file " + configFile.getPath(), e);
							}
						}
						boolean[] closingWithoutSave = new boolean[1];
						if (loaded)
							installConfigPersistence(config, configFile, backups, closingWithoutSave);
						else if (backups != null && !backups.getBackups().isEmpty()) {
							restoreBackup(true, config, backups, () -> {
								config.setName(theConfigName);
								installConfigPersistence(config, configFile, backups, closingWithoutSave);
							}, () -> {
								config.setName(theConfigName);
								installConfigPersistence(config, configFile, backups, closingWithoutSave);
							}, theAppEnv, closingWithoutSave, models, theReporting);
						} else {
							config.setName(theConfigName);
							installConfigPersistence(config, configFile, backups, closingWithoutSave);
						}
						return SettableValue.of(config, "Not Settable");
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
				Runnable onNoBackup, AppEnvironment appEnv, boolean[] closingWithoutSave, ModelSetInstance msi, ErrorReporting reporting)
					throws ModelInstantiationException {
				BetterSortedSet<Instant> backupTimes = backups == null ? null : backups.getBackups();
				if (backupTimes == null || backupTimes.isEmpty()) {
					if (onNoBackup != null)
						onNoBackup.run();
					return;
				}
				SettableValue<Instant> selectedBackup = SettableValue.<Instant> build().build();
				Format<Instant> PAST_DATE_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", opts -> opts
					.withMaxResolution(TimeUtils.DateElementType.Second).withEvaluationType(TimeUtils.RelativeInstantEvaluation.Past));
				boolean[] backedUp = new boolean[1];
				String titleV;
				try {
					titleV = appEnv == null ? null : appEnv.getApplicationTitle();
				} catch (ModelInstantiationException e) {
					reporting.warn("Could not interpret application title", e);
					titleV = null;
				}
				Image iconV;
				try {
					iconV = appEnv == null ? null : appEnv.getApplicationIcon();
				} catch (ModelInstantiationException e) {
					reporting.warn("Could not interpret application icon", e);
					iconV = null;
				}
				String fTitle = titleV == null ? "Application" : titleV;
				Image fIcon = iconV;
				Window[] window = new Window[1];
				boolean isEdt = EventQueue.isDispatchThread();
				Runnable buildWindow = () -> {
					PanelPopulation.WindowBuilder<?, ?> builder;
					if (isEdt)
						builder = WindowPopulation.populateDialog(null, null, true);
					else
						builder = WindowPopulation.populateWindow(null, null, true, false);
					window[0] = builder.withTitle(fTitle + " Backup")//
						.withIcon(fIcon)//
						.withVContent(content -> {
							if (fromError)
								content.addLabel(null, "Your configuration has been corrupted", null);
							TimeUtils.RelativeTimeFormat durationFormat = TimeUtils.relativeFormat()
								.withMaxPrecision(TimeUtils.DurationComponentType.Second).withMaxElements(2).withMonthsAndYears();
							content.addLabel(null, "Please choose a backup to restore", null)//
							.addTable(ObservableCollection.of(backupTimes.reverse()), table -> {
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
								builder.getWindow().setVisible(false);
							}, btn -> btn.disableWith(selectedBackup.map(t -> t == null ? "Select a Backup" : null)));
						}).run(null).getWindow();
				};
				if (isEdt)
					buildWindow.run();
				else {
					try {
						EventQueue.invokeAndWait(buildWindow);
						while (window[0].isVisible()) {
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
							}
						}
					} catch (InvocationTargetException | InterruptedException e) {
						reporting.error("Could not display backup dialog", e);
					}
				}
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

		/**
		 * Interpretation for a {@link ObservableModelElement.ConfigModelElement}
		 *
		 * @param <M> The sub-type of {@link ObservableModelElement.ConfigModelElement} to create
		 */
		public static class Interpreted<M extends ConfigModelElement> extends ObservableModelElement.Interpreted<M> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this model element
			 */
			protected Interpreted(Def<? super M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super M> getDefinition() {
				return (Def<? super M>) super.getDefinition();
			}

			@Override
			protected List<? extends Interpreted<?>> getSubModels() {
				return Collections.emptyList();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (env.get(ConfigModelValue.FORMAT_SET_KEY, ObservableConfigFormatSet.class) == null)
					env.put(ConfigModelValue.FORMAT_SET_KEY, new ObservableConfigFormatSet());
				super.doUpdate(env);
			}

			@Override
			public ConfigModelElement create(ExElement parent) {
				return new ConfigModelElement(getIdentity());
			}
		}

		/** @param id The element id for this model element */
		protected ConfigModelElement(Object id) {
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
