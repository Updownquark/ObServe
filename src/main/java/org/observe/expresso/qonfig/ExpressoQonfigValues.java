package org.observe.expresso.qonfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SettableValue.WrappingSettableValue;
import org.observe.SimpleObservable;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionBuilder.SortedBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoQonfigValues.CollectionElement.CollectionPopulator;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.ErrorReporting;
import org.qommons.threading.QommonsTimer;

import com.google.common.reflect.TypeToken;

/** A collection of Expresso Qonfig model value types */
public class ExpressoQonfigValues {
	private ExpressoQonfigValues() {
	}

	/** Abstract scalar model value definition */
	public static abstract class AbstractCompiledValue extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<?>> {
		/**
		 * @param parent The parent element of this model value
		 * @param qonfigType The Qonfig type of this model value
		 */
		protected AbstractCompiledValue(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		/**
		 * Interpretation of a {@link AbstractCompiledValue}
		 *
		 * @param <T> The type of the model value
		 */
		public static abstract class Interpreted<T>
		extends ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<T>>>
		implements ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<T>>> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent for this element
			 */
			protected Interpreted(AbstractCompiledValue definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}
		}
	}

	/** &lt;constant> element */
	public static class ConstantValueDef extends AbstractCompiledValue {
		/**
		 * @param parent The parent element for this element
		 * @param qonfigType The Qonfig type of this element
		 */
		public ConstantValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link ConstantValueDef} implementation
		 *
		 * @param <T> The type of the value
		 */
		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			Interpreted(ConstantValueDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ConstantValueDef getDefinition() {
				return (ConstantValueDef) super.getDefinition();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
			}

			@Override
			public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		/**
		 * {@link ConstantValueDef} instantiator
		 *
		 * @param <T> The type of the value
		 */
		public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
			Instantiator(ConstantValueDef.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> getElementValue() {
				return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<T> initV = getElementValue().get(models);
				return new ConstantValue<>(getModelPath(), initV.get());
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return value; // Constants are constant--no need to copy
			}
		}

		static class ConstantValue<T> extends AbstractIdentifiable implements SettableValue<T> {
			private final String theModelPath;
			private final T theValue;

			public ConstantValue(String path, T value) {
				theModelPath = path;
				theValue = value;
			}

			@Override
			public long getStamp() {
				return 0;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			public Observable<ObservableValueEvent<T>> noInitChanges() {
				return Observable.empty();
			}

			@Override
			public boolean isLockSupported() {
				return true;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Collection<Cause> getCurrentCauses() {
				return Collections.emptyList();
			}

			@Override
			public <V extends T> T set(V value2, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException("Constant value");
			}

			@Override
			public <V extends T> String isAcceptable(V value2) {
				return "Constant value";
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.of("Constant value");
			}

			@Override
			protected Object createIdentity() {
				return theModelPath;
			}
		}
	}

	/** &lt;value> element */
	public static class SimpleValueDef extends AbstractCompiledValue {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public SimpleValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			ExIntValue.Def intValue = getAddOn(ExIntValue.Def.class);
			if (intValue == null) {
				if (getElementValue() == null || getValueType() != null)
					reporting()
					.warn("This interpretation is intended to be used with internal values (children of a <model> element) only.");
				else
					throw new QonfigInterpretationException(
						"This interpretation is intended to be used with internal values (children of a <model> element) only.\n"
							+ "When this is not true, either a type or a value MUST be specified",
							reporting().getFileLocation().getPosition(0), 0);
			} else {
				if (getElementValue() != null) {
					if (intValue.getInit() != null)
						session.reporting()
						.warn("Either a value or an init value may be specified, but not both.  Initial value will be ignored.");
				} else if (intValue.getInit() == null && getValueType() == null)
					throw new QonfigInterpretationException("One of a type, a value, or an initial value MUST be specified",
						reporting().getFileLocation().getPosition(0), 0);
			}
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SimpleValueDef} interpretation
		 *
		 * @param <T> The type of the value
		 */
		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			Interpreted(SimpleValueDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SimpleValueDef getDefinition() {
				return (SimpleValueDef) super.getDefinition();
			}

			/** @return The initialization for the value */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getInit() {
				return getAddOnValue(ExIntValue.Interpreted.class, ExIntValue.Interpreted::getInit);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				if (getElementValue() != null)
					return Collections.singletonList(getElementValue());
				else
					return Collections.emptyList(); // Independent (fundamental) value
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				if (getDefinition().getValueType() != null || getElementValue() != null)
					return super.getType();
				return getInit().getType();
			}

			@Override
			public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this, getInit() == null ? null : getInit().instantiate());
			}
		}

		/**
		 * {@link SimpleValueDef} instantiator
		 *
		 * @param <T> The type of the value
		 */
		public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
			private final ModelValueInstantiator<SettableValue<T>> theInit;
			private final T theDefaultValue;

			Instantiator(SimpleValueDef.Interpreted<T> parent, ModelValueInstantiator<SettableValue<T>> init)
				throws ModelInstantiationException {
				super(parent);
				theInit = parent.getInit() == null ? null : parent.getInit().instantiate();
				theDefaultValue = (T) TypeTokens.get().getDefaultValue(parent.getType().getType(0));
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> getElementValue() {
				return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				if (theInit != null)
					theInit.instantiate();
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				if (getElementValue() != null)
					return getElementValue().get(models);
				else {
					SettableValue.Builder<T> builder = SettableValue.build();
					if (getModelPath() != null)
						builder.withDescription(getModelPath());
					if (theInit != null) {
						SettableValue<T> initV = theInit.get(models);
						builder.withValue(initV.get());
					} else
						builder.withValue(theDefaultValue);
					return builder.build();
				}
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				if (getElementValue() != null)
					return getElementValue().forModelCopy(value, sourceModels, newModels);
				else
					return value; // Independent (fundamental) value
			}
		}
	}

	/** &lt;field-value> element */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = FieldValueDef.FIELD_VALUE,
		interpretation = FieldValueDef.Interpreted.class,
		instance = FieldValueDef.Instantiator.class)
	public static class FieldValueDef extends AbstractCompiledValue {
		/** The XML name of this element */
		public static final String FIELD_VALUE = "field-value";

		private ModelComponentId theTargetAs;
		private CompiledExpression theSource;
		private CompiledExpression theSave;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public FieldValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The model ID of the model value in which this value will store the value being set when the save action is called */
		@QonfigAttributeGetter("target-as")
		public ModelComponentId getTargetAs() {
			return theTargetAs;
		}

		/** @return The expression to get this value from */
		@QonfigAttributeGetter("source")
		public CompiledExpression getSource() {
			return theSource;
		}

		/**
		 * @return The action that will change the effective value of the {@link #getSource()} expression using the value in the
		 *         {@link #getTargetAs()} model value
		 */
		@QonfigAttributeGetter("save")
		public CompiledExpression getSave() {
			return theSave;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theSource = getAttributeExpression("source", session);
			theSave = getAttributeExpression("save", session);
			String targetAsName = session.getAttributeText("target-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theTargetAs = elModels.getElementValueModelId(targetAsName);
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementValueType(theTargetAs, ModelTypes.Value, (interp, env) -> {
				return ModelTypes.Value.forType(interp.getOrEvalSourceType(env));
			});
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<?>> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link FieldValueDef} interpretation
		 *
		 * @param <T> The type of the value
		 */
		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSource;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theSave;

			Interpreted(FieldValueDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FieldValueDef getDefinition() {
				return (FieldValueDef) super.getDefinition();
			}

			/** @return The expression to get this value from */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSource() {
				return theSource;
			}

			/**
			 * @return The action that will change the effective value of the {@link #getSource()} expression using the value in the
			 *         {@link #getTargetAs()} model value
			 */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getSave() {
				return theSave;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Arrays.asList(theSource, theSave);
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
				return theSource.getType();
			}

			/**
			 * @param env The expresso environment to use to interpret the expression
			 * @return The type of this value
			 * @throws ExpressoInterpretationException If the value type could not be determined
			 */
			protected TypeToken<T> getOrEvalSourceType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				return (TypeToken<T>) theSource.getType().getType(0);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theSource = getDefinition().getSource().interpret(ModelTypes.Value.anyAsV(), env);

				super.doUpdate(env);

				getOrEvalSourceType(env);
				theSave = getDefinition().getSave().interpret(ModelTypes.Action.instance(), env);
			}

			@Override
			public ModelValueElement<SettableValue<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		/**
		 * {@link FieldValueDef} instantiator
		 *
		 * @param <T> The type of the value
		 */
		public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
			private final ModelInstantiator theLocalModels;
			private final ModelValueInstantiator<SettableValue<T>> theSource;
			private final ModelValueInstantiator<ObservableAction> theSave;
			private final ModelComponentId theTargetAs;

			Instantiator(FieldValueDef.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theSource = interpreted.getSource().instantiate();
				theSave = interpreted.getSave().instantiate();
				theTargetAs = interpreted.getDefinition().getTargetAs();
			}

			/** @return The expression to get this value from */
			public ModelValueInstantiator<SettableValue<T>> getSource() {
				return theSource;
			}

			/**
			 * @return The action that will change the effective value of the {@link #getSource()} expression using the value in the
			 *         {@link #getTargetAs()} model value
			 */
			public ModelValueInstantiator<ObservableAction> getSave() {
				return theSave;
			}

			/** @return The model ID of the model value in which this value will store the value being set when the save action is called */
			public ModelComponentId getTargetAs() {
				return theTargetAs;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModels.instantiate();
				theSource.instantiate();
				theSave.instantiate();
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = theLocalModels.wrap(models);
				SettableValue<T> source = theSource.get(models);
				ObservableAction save = theSave.get(models);
				SettableValue<T> targetAs = SettableValue.<T> build().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theTargetAs, models, targetAs);
				return new FieldValue<>(source, save, targetAs);
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				sourceModels = theLocalModels.wrap(sourceModels);
				newModels = theLocalModels.wrap(newModels);
				SettableValue<T> sourceSource = ((FieldValue<T>) value).getSource();
				ObservableAction sourceSave = ((FieldValue<T>) value).getSave();
				SettableValue<T> newSource = theSource.forModelCopy(sourceSource, sourceModels, newModels);
				ObservableAction newSave = theSave.forModelCopy(sourceSave, sourceModels, newModels);
				if (sourceSource == newSource && sourceSave == newSave)
					return value;
				else {
					SettableValue<T> targetAs = SettableValue.<T> build().build();
					ExFlexibleElementModelAddOn.satisfyElementValue(theTargetAs, newModels, targetAs);
					return new FieldValue<>(newSource, newSave, targetAs);
				}
			}
		}

		static class FieldValue<T> extends SettableValue.RefreshingSettableValue<T> {
			private final SettableValue<T> theSource;
			private final ObservableAction theSave;
			private final SettableValue<T> theSourceAs;

			FieldValue(SettableValue<T> source, ObservableAction save, SettableValue<T> sourceAs) {
				super(source, new SimpleObservable<>());
				theSource = source;
				theSave = save;
				theSourceAs = sourceAs;
			}

			SettableValue<T> getSource() {
				return theSource;
			}

			ObservableAction getSave() {
				return theSave;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				// We need a value to determine
				return SettableValue.ALWAYS_ENABLED;
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				theSourceAs.set(value, null);
				return isEnabled().get();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				T old = theSource.get();
				theSourceAs.set(value, cause);
				theSave.act(cause);
				((SimpleObservable<Void>) getRefresh()).onNext(null);
				return old;
			}

			@Override
			protected Object createIdentity() {
				return theSource.getIdentity();
			}
		}
	}

	/** ExElement definition for the Expresso &lt;element> element. */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "element",
		interpretation = CollectionElement.Interpreted.class,
		instance = CollectionElement.CollectionPopulator.class)
	public static class CollectionElement extends AbstractCompiledValue {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public CollectionElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		@QonfigAttributeGetter
		public CompiledExpression getElementValue() {
			return super.getElementValue();
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link CollectionElement} interpretation
		 *
		 * @param <T> The type of the element
		 */
		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this element
			 */
			protected Interpreted(CollectionElement definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public CollectionElement getDefinition() {
				return (CollectionElement) super.getDefinition();
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return getElementValue().getType();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.singletonList(getElementValue());
			}

			@Override
			public CollectionPopulator<T> create() throws ModelInstantiationException {
				return new CollectionPopulator.Default<>(this);
			}
		}

		/**
		 * {@link CollectionElement} instantiator
		 *
		 * @param <T> The type of the element
		 */
		public interface CollectionPopulator<T> extends ModelValueElement<SettableValue<T>> {
			/**
			 * Installs this populator's content into the collection
			 *
			 * @param collection The collection to populate
			 * @param models The model instance to get model values from
			 * @return Whether the collection's content was changed as a result of the call
			 * @throws ModelInstantiationException If any of this populator's content could not be instantiated
			 */
			boolean populateCollection(BetterCollection<? super T> collection, ModelSetInstance models) throws ModelInstantiationException;

			/**
			 * Default {@link CollectionPopulator} implementation for a single value
			 *
			 * @param <T> The type of the value
			 */
			static class Default<T> extends ModelValueElement.Abstract<SettableValue<T>> implements CollectionPopulator<T> {
				private final ErrorReporting theReporting;

				Default(CollectionElement.Interpreted<T> interpreted) throws ModelInstantiationException {
					super(interpreted);
					theReporting = interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
				}

				@Override
				public ModelValueInstantiator<SettableValue<T>> getElementValue() {
					return (ModelValueInstantiator<SettableValue<T>>) super.getElementValue();
				}

				@Override
				public void instantiate() throws ModelInstantiationException {
					super.instantiate();
				}

				@Override
				public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
					return getElementValue().get(models);
				}

				@Override
				public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
					return getElementValue().forModelCopy(value, sourceModels, newModels);
				}

				@Override
				public boolean populateCollection(BetterCollection<? super T> collection, ModelSetInstance models)
					throws ModelInstantiationException {
					SettableValue<T> elValue = get(models);
					T value = elValue.get();
					String msg = collection.canAdd(value);
					if (msg != null) {
						theReporting.warn(msg);
						return false;
					} else if (!collection.add(value)) {
						theReporting.warn("Value not added for unspecified reason");
						return false;
					} else
						return true;
				}
			}
		}
	}

	/**
	 * Abstract ExElement definition for the Expresso &lt;int-list> element.
	 *
	 * @param <C> The sub-type of {@link ObservableCollection} that this element creates
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "int-list",
		interpretation = AbstractCollectionDef.Interpreted.class,
		instance = AbstractCollectionDef.Instantiator.class)
	public static abstract class AbstractCollectionDef<C extends ObservableCollection<?>> extends
	ModelValueElement.Def.SingleTyped<C, ModelValueElement<C>> implements ModelValueElement.CompiledSynth<C, ModelValueElement<C>> {
		private QonfigElementOrAddOn theIntListType;
		private final List<CollectionElement> theElements;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 * @param modelType The collection model type for the value
		 */
		protected AbstractCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<C> modelType) {
			super(parent, qonfigType, modelType);
			theElements = new ArrayList<>();
		}

		/** @return Elements defined to initialize this collection */
		@QonfigChildGetter("element")
		public List<CollectionElement> getElements() {
			return Collections.unmodifiableList(theElements);
		}

		@Override
		protected boolean useWrapperType() {
			return true;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theIntListType = session.isInstance("int-list");
			if (theIntListType != null) {
				ExpressoQIS intListSession = session.asElement("int-list");
				syncChildren(CollectionElement.class, theElements, intListSession.forChildren("element"));
			}
			if ((getElementValue() != null && getElementValue().getExpression() != ObservableExpression.EMPTY) && !theElements.isEmpty())
				reporting().error("Both a list value and elements specified");
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			if (!theElements.isEmpty()) {
				List<ExpressoQIS> elementSessions = session.asElement(theIntListType).forChildren("element");
				int i = 0;
				for (CollectionElement element : theElements)
					element.prepareModelValue(elementSessions.get(i++));
			}
		}

		@Override
		public Interpreted<?, C> interpretValue(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, C>) interpret2(parent);
		}

		/**
		 * @param parent The parent element for the interpreted collection
		 * @return The interpreted collection
		 */
		protected abstract Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent);

		/**
		 * {@link AbstractCollectionDef} implementation
		 *
		 * @param <T> The type of values in the collection
		 * @param <C> The type of the collection
		 */
		public static abstract class Interpreted<T, C extends ObservableCollection<T>>
		extends ModelValueElement.Def.SingleTyped.Interpreted<C, C, ModelValueElement<C>>
		implements ModelValueElement.InterpretedSynth<C, C, ModelValueElement<C>> {
			private final List<CollectionElement.Interpreted<T>> theElements;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this collection
			 */
			protected Interpreted(AbstractCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
				super((AbstractCollectionDef<C>) definition, parent);
				theElements = new ArrayList<>();
			}

			@Override
			public AbstractCollectionDef<C> getDefinition() {
				return (AbstractCollectionDef<C>) super.getDefinition();
			}

			/** @return Elements defined to initialize this collection */
			public List<CollectionElement.Interpreted<T>> getElements() {
				return Collections.unmodifiableList(theElements);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
			}

			@Override
			public void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
					syncChildren(getDefinition().getElements(), theElements,
						(d, elEnv) -> (CollectionElement.Interpreted<T>) d.interpret(elEnv), CollectionElement.Interpreted::update);
				}
			}

			/** @return The type of elements in this collection */
			protected TypeToken<T> getValueType() {
				return TypeTokens.get().wrap((TypeToken<T>) getTargetType().getType(0));
			}

			/**
			 * @return Instantiators for this collection's initial elements
			 * @throws ModelInstantiationException If any initial elements could not be instantiated
			 */
			protected List<CollectionElement.CollectionPopulator<T>> instantiateElements() throws ModelInstantiationException {
				return BetterList.of2(theElements.stream(), el -> el.create());
			}
		}

		/**
		 * {@link AbstractCollectionDef} instantiator
		 *
		 * @param <T> The type of values in the collection
		 * @param <C> The type of the collection
		 */
		public static abstract class Instantiator<T, C extends ObservableCollection<T>> extends ModelValueElement.Abstract<C> {
			private final List<CollectionElement.CollectionPopulator<T>> theElements;

			/**
			 * @param interpreted The interpretation to instantiate
			 * @param elements The element populators for the collection
			 * @throws ModelInstantiationException If the collection could not be instantiated
			 */
			protected Instantiator(AbstractCollectionDef.Interpreted<T, C> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted);
				theElements = elements;
			}

			/** @return Elements defined to initialize this collection */
			public List<CollectionElement.CollectionPopulator<T>> getElements() {
				return Collections.unmodifiableList(theElements);
			}

			@Override
			public ModelValueInstantiator<C> getElementValue() {
				return (ModelValueInstantiator<C>) super.getElementValue();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				for (CollectionElement.CollectionPopulator<T> element : theElements)
					element.instantiate();
			}

			@Override
			public C get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				if (getElementValue() != null)
					return getElementValue().get(models);
				ObservableCollectionBuilder<T, ?> builder = create(models);
				if (getModelPath() != null)
					builder.withDescription(getModelPath());
				C collection = (C) builder.build();
				for (CollectionElement.CollectionPopulator<T> element : theElements)
					element.populateCollection(collection, models);
				return collection;
			}

			@Override
			public C forModelCopy(C value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (getElementValue() != null)
					return getElementValue().forModelCopy(value, sourceModels, newModels);
				// Configured elements are merely initialized, not slaved, and the collection may have been modified
				// since it was created. There's no sense to making a re-initialized copy here.
				return value;
			}

			/**
			 * Creates the collection
			 *
			 * @param models The model instance
			 * @return The collection builder
			 * @throws ModelInstantiationException If the collection could not be instantiated
			 */
			protected abstract ObservableCollectionBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException;
		}
	}

	/** ExElement definition for the Expresso &lt;list> element. */
	public static class PlainCollectionDef extends AbstractCollectionDef<ObservableCollection<?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public PlainCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Collection);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link PlainCollectionDef} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableCollection<T>> {
			Interpreted(PlainCollectionDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableCollection<T>> create() throws ModelInstantiationException {
				return new PlainInstantiator<>(this, instantiateElements());
			}
		}

		/**
		 * {@link PlainCollectionDef} instantiator
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class PlainInstantiator<T> extends Instantiator<T, ObservableCollection<T>> {
			PlainInstantiator(PlainCollectionDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				return ObservableCollection.build();
			}
		}
	}

	/**
	 * Abstract ExElement definition for the Expresso &lt;sorted-list> element.
	 *
	 * @param <C> The sub-type of {@link ObservableSortedCollection} that this element creates
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "sorted-model-value",
		interpretation = AbstractSortedCollectionDef.Interpreted.class,
		instance = AbstractSortedCollectionDef.SortedInstantiator.class)
	public static abstract class AbstractSortedCollectionDef<C extends ObservableSortedCollection<?>> extends AbstractCollectionDef<C> {
		private ExSort.ExRootSort theSort;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 * @param modelType The sorted collection model type for the value
		 */
		protected AbstractSortedCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType,
			ModelType.SingleTyped<C> modelType) {
			super(parent, qonfigType, modelType);
		}

		/** @return The sorting specified for the collection */
		@QonfigChildGetter("sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			if (getElementValue() != null && theSort != null)
				reporting().warn("Sorting will not be used if the value of the collection is specified");
		}

		@Override
		protected abstract Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent);

		/**
		 * {@link AbstractSortedCollectionDef} interpretation
		 *
		 * @param <T> The type of values in the collection
		 * @param <C> The type of the collection
		 */
		public static abstract class Interpreted<T, C extends ObservableSortedCollection<T>>
		extends AbstractCollectionDef.Interpreted<T, C> {
			private ExSort.ExRootSort.Interpreted<T> theSort;
			private Comparator<? super T> theDefaultSorting;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this collection element
			 */
			protected Interpreted(AbstractSortedCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public AbstractSortedCollectionDef<C> getDefinition() {
				return (AbstractSortedCollectionDef<C>) super.getDefinition();
			}

			/** @return The sorting specified for the collection */
			public ExSort.ExRootSort.Interpreted<T> getSort() {
				return theSort;
			}

			@Override
			public void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getElementValue() != null) { // No sorting needed
				} else
					theSort = syncChild(getDefinition().getSort(), theSort, def -> (ExSort.ExRootSort.Interpreted<T>) def.interpret(this),
						(s, sEnv) -> s.update((TypeToken<T>) getType().getType(0), sEnv));
				if (theSort == null) {
					theDefaultSorting = ExSort.getDefaultSorting(TypeTokens.getRawType(getValueType()));
					if (theDefaultSorting == null)
						throw new ExpressoInterpretationException(
							"No default sorting available for type " + getValueType() + ". Specify sorting", reporting().getPosition(), 0);
				}
			}

			/**
			 * @return The instantiator for this collection's sorting
			 * @throws ModelInstantiationException If the sorting could not be instantiated
			 */
			protected ModelValueInstantiator<Comparator<? super T>> instantiateSort() throws ModelInstantiationException {
				if (theSort != null)
					return theSort.instantiateSort();
				else
					return ModelValueInstantiator.literal(theDefaultSorting, "default");
			}
		}

		/**
		 * {@link AbstractSortedCollectionDef} instantiator
		 *
		 * @param <T> The type of values in the collection
		 * @param <C> The type of the collection
		 */
		public static abstract class SortedInstantiator<T, C extends ObservableSortedCollection<T>> extends Instantiator<T, C> {
			private final ModelValueInstantiator<Comparator<? super T>> theSort;

			/**
			 * @param interpreted The interpretation to instantiate
			 * @param elements Instantiators for the initial elements to populate the collection
			 * @throws ModelInstantiationException If the collection or any of its initial content could not be instantiated
			 */
			protected SortedInstantiator(AbstractSortedCollectionDef.Interpreted<T, C> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
				theSort = interpreted.instantiateSort();
			}

			/** @return The sorting specified for the collection */
			public ModelValueInstantiator<Comparator<? super T>> getSort() {
				return theSort;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSort.instantiate();
			}

			@Override
			protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				Comparator<? super T> sort = theSort.get(models);
				return create(sort, models);
			}

			/**
			 *
			 * Creates the collection
			 *
			 * @param sort The sorting for the collection
			 * @param models The model instance
			 * @return The sorted collection builder
			 * @throws ModelInstantiationException If the collection cannot be instantiated
			 */
			protected abstract SortedBuilder<T, ?> create(Comparator<? super T> sort, ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	/** ExElement definition for the Expresso &lt;sorted-list> element. */
	public static class SortedCollectionDef extends AbstractSortedCollectionDef<ObservableSortedCollection<?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public SortedCollectionDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.SortedCollection);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SortedCollectionDef} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedCollection<T>> {
			Interpreted(AbstractSortedCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableSortedCollection<T>> create() throws ModelInstantiationException {
				return new SimpleSortedInstantiator<>(this, instantiateElements());
			}
		}

		/**
		 * {@link SortedCollectionDef} instantiator
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class SimpleSortedInstantiator<T> extends SortedInstantiator<T, ObservableSortedCollection<T>> {
			SimpleSortedInstantiator(SortedCollectionDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(Comparator<? super T> sort, ModelSetInstance models) {
				return ObservableCollection.<T> build().sortBy(sort);
			}
		}
	}

	/** ExElement definition for the Expresso &lt;set> element. */
	public static class SetDef extends AbstractCollectionDef<ObservableSet<?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public SetDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Set);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SetDef} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableSet<T>> {
			Interpreted(SetDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableSet<T>> create() throws ModelInstantiationException {
				return new SetInstantiator<>(this, instantiateElements());
			}
		}

		/**
		 * {@link SetDef} instantiator
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class SetInstantiator<T> extends Instantiator<T, ObservableSet<T>> {
			SetInstantiator(SetDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements) throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder<T, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				return ObservableSet.build();
			}
		}
	}

	/** ExElement definition for the Expresso &lt;sorted-set> element. */
	public static class SortedSetDef extends AbstractSortedCollectionDef<ObservableSortedSet<?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public SortedSetDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.SortedSet);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SortedSetDef} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedSet<T>> {
			Interpreted(SortedSetDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableSortedSet<T>> create() throws ModelInstantiationException {
				return new SortedSetInstantiator<>(this, instantiateElements());
			}
		}

		/**
		 * {@link SortedSetDef} instantiator
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class SortedSetInstantiator<T> extends SortedInstantiator<T, ObservableSortedSet<T>> {
			SortedSetInstantiator(SortedSetDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder.DistinctSortedBuilder<T, ?> create(Comparator<? super T> sort, ModelSetInstance models) {
				return ObservableCollection.<T> build().distinctSorted(sort);
			}
		}
	}

	/** ExElement definition for the Expresso &lt;entry>. */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "entry",
		interpretation = MapEntry.Interpreted.class,
		instance = MapEntry.MapPopulator.class)
	public static class MapEntry extends ModelValueElement.Def.Abstract<SettableValue<?>, ModelValueElement<?>> {
		private CompiledExpression theKey;
		private CompiledExpression theValue;

		/**
		 * @param parent The parent element for this map entry
		 * @param qonfigType The Qonfig type of this map entry
		 */
		public MapEntry(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		/** @return The key of the entry */
		@QonfigAttributeGetter("key")
		public CompiledExpression getKey() {
			return theKey;
		}

		/** @return The value of the entry */
		@Override
		@QonfigAttributeGetter
		public CompiledExpression getElementValue() {
			return theValue;
		}

		@Override
		public void populate(ObservableModelSet.Builder builder, ExpressoQIS session) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("<entry> cannot be used this way", reporting().getFileLocation());
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
		}

		/**
		 * @param parent The parent element for the interpreted entry
		 * @return The interpreted entry
		 */
		public Interpreted<?, ?> interpret(ModelValueElement.Interpreted<?, ?, ?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link MapEntry} interpretation
		 *
		 * @param <K> The key type for the entry
		 * @param <V> The value type for the entry
		 */
		public static class Interpreted<K, V>
		extends ModelValueElement.Interpreted.Abstract<SettableValue<?>, SettableValue<V>, ModelValueElement<SettableValue<V>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<K>> theKey;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<V>> theValue;

			Interpreted(MapEntry definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapEntry getDefinition() {
				return (MapEntry) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<V>> getTargetType() {
				AbstractMapDef.Interpreted<K, V, ?> parent = (AbstractMapDef.Interpreted<K, V, ?>) getParentElement();
				return ModelTypes.Value.forType(parent.getValueType());
			}

			/** @return The key of the entry */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<K>> getKey() {
				return theKey;
			}

			/** @return The value of the entry */
			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<V>> getElementValue() {
				return theValue;
			}

			/**
			 * Initializes or updates this entry
			 *
			 * @param env The expresso environment to use to interpret expressions
			 * @param keyType The key type for the entry
			 * @param valueType The value type for the entry
			 * @throws ExpressoInterpretationException If this entry cannot be interpreted
			 */
			public void update(InterpretedExpressoEnv env, TypeToken<K> keyType, TypeToken<V> valueType)
				throws ExpressoInterpretationException {
				super.update(env);
				theKey = interpret(getDefinition().getKey(), ModelTypes.Value.forType(keyType));
				theValue = interpret(getDefinition().getElementValue(), ModelTypes.Value.forType(valueType));
			}

			@Override
			public MapPopulator<K, V> create() throws ModelInstantiationException {
				return new MapPopulator.Default<>(this);
			}
		}

		/**
		 * {@link MapEntry} instantiator
		 *
		 * @param <K> The key type of the entry
		 * @param <V> The value type of the entry
		 */
		public interface MapPopulator<K, V> extends ModelValueElement<SettableValue<V>> {
			@Override
			void instantiate() throws ModelInstantiationException;

			/**
			 * Installs this entry's content into a map
			 *
			 * @param map The map to populate
			 * @param models The model instance to use to instantiate this entry's content
			 * @return If the map was changed as a result of this call
			 * @throws ModelInstantiationException If this entry's content cannot be instantiated
			 */
			boolean populateMap(BetterMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException;

			/**
			 * Installs this entry's content into a multi-map
			 *
			 * @param map The map to populate
			 * @param models The model instance to use to instantiate this entry's content
			 * @return If the map was changed as a result of this call
			 * @throws ModelInstantiationException If this entry's content cannot be instantiated
			 */
			boolean populateMultiMap(BetterMultiMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException;

			/**
			 * Default {@link MapEntry} implementation for the &lt;entry> element
			 *
			 * @param <K> The key type of the entry
			 * @param <V> The value type of the entry
			 */
			static class Default<K, V> extends ModelValueElement.Abstract<SettableValue<V>> implements MapPopulator<K, V> {
				private final ModelValueInstantiator<SettableValue<K>> theKey;
				private final ErrorReporting theReporting;

				public Default(MapEntry.Interpreted<K, V> interpreted) throws ModelInstantiationException {
					super(interpreted);
					theKey = interpreted.getKey().instantiate();
					theReporting = interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
				}

				public ModelValueInstantiator<SettableValue<K>> getKey() {
					return theKey;
				}

				@Override
				public ModelValueInstantiator<SettableValue<V>> getElementValue() {
					return (ModelValueInstantiator<SettableValue<V>>) super.getElementValue();
				}

				@Override
				public void instantiate() throws ModelInstantiationException {
					super.instantiate();
					theKey.instantiate();
				}

				@Override
				public SettableValue<V> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
					return getElementValue().get(models);
				}

				@Override
				public SettableValue<V> forModelCopy(SettableValue<V> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
					return getElementValue().forModelCopy(value, sourceModels, newModels);
				}

				@Override
				public boolean populateMap(BetterMap<? super K, ? super V> map, ModelSetInstance models)
					throws ModelInstantiationException {
					SettableValue<K> keyValue = theKey.get(models);
					SettableValue<V> vValue = getElementValue().get(models);
					K key = keyValue.get();
					V value = vValue.get();
					String msg = map.canPut(key, value);
					if (msg != null) {
						theReporting.warn(msg);
						return false;
					}
					map.put(key, value);
					return true;
				}

				@Override
				public boolean populateMultiMap(BetterMultiMap<? super K, ? super V> map, ModelSetInstance models)
					throws ModelInstantiationException {
					SettableValue<K> keyValue = theKey.get(models);
					SettableValue<V> vValue = getElementValue().get(models);
					K key = keyValue.get();
					V value = vValue.get();
					BetterCollection<? super V> values = map.get(key);
					String msg = values.canAdd(value);
					if (msg != null) {
						theReporting.warn(msg);
						return false;
					} else if (!values.add(value)) {
						theReporting.warn("Entry not added for unspecified reason");
						return false;
					} else
						return true;
				}
			}
		}
	}

	/**
	 * Abstract ExElement definition for the Expresso &lt;int-map> element.
	 *
	 * @param <M> The sub-type of {@link ObservableMap} that this element creates
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "int-map",
		interpretation = AbstractMapDef.Interpreted.class,
		instance = AbstractMapDef.Instantiator.class)
	public static abstract class AbstractMapDef<M extends ObservableMap<?, ?>> extends
	ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M>> implements ModelValueElement.CompiledSynth<M, ModelValueElement<M>> {
		private final List<MapEntry> theEntries;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 * @param modelType The map model type for the value
		 */
		protected AbstractMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
			theEntries = new ArrayList<>();
		}

		/** @return Entries defined to initialize this map */
		@QonfigChildGetter("entry")
		public List<MapEntry> getEntries() {
			return Collections.unmodifiableList(theEntries);
		}

		@Override
		protected boolean useWrapperType() {
			return true;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			if (session.isInstance("int-map") != null) {
				ExpressoQIS intMapSession = session.asElement("int-map");
				syncChildren(MapEntry.class, theEntries, intMapSession.forChildren("entry"));
			}
		}

		@Override
		protected void doPrepare(ExpressoQIS session) {
		}

		@Override
		public Interpreted<?, ?, M> interpretValue(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ?, M>) interpret2(parent);
		}

		/**
		 * @param parent The parent element for the interpreted map
		 * @return The interpreted map element
		 */
		protected abstract Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent);

		/**
		 * {@link AbstractMapDef} interpretation
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <M> The sub-type of {@link ObservableMap} to create
		 */
		public static abstract class Interpreted<K, V, M extends ObservableMap<K, V>>
		extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M>>
		implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M>> {
			private final List<MapEntry.Interpreted<K, V>> theEntries;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this map element
			 */
			protected Interpreted(AbstractMapDef<?> definition, ExElement.Interpreted<?> parent) {
				super((AbstractMapDef<M>) definition, parent);
				theEntries = new ArrayList<>();
			}

			@Override
			public AbstractMapDef<M> getDefinition() {
				return (AbstractMapDef<M>) super.getDefinition();
			}

			/** @return Entries defined to initialize this map */
			public List<MapEntry.Interpreted<K, V>> getEntries() {
				return Collections.unmodifiableList(theEntries);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
			}

			/** @return The key type of the map */
			protected TypeToken<K> getKeyType() {
				return TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
			}

			/** @return The value type of the map */
			protected TypeToken<V> getValueType() {
				return TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				ModelInstanceType<M, M> type = getType();
				TypeToken<K> keyType = (TypeToken<K>) type.getType(0);
				TypeToken<V> valueType = (TypeToken<V>) type.getType(1);
				try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
					syncChildren(getDefinition().getEntries(), theEntries, d -> (MapEntry.Interpreted<K, V>) d.interpret(this),
						(entry, eEnv) -> entry.update(eEnv, keyType, valueType));
				}
			}

			/**
			 * Instantiate's this map's initial content
			 *
			 * @return The instantiators for this map's initial content
			 * @throws ModelInstantiationException If any of this map's initial content could not be instantiated
			 */
			protected List<MapEntry.MapPopulator<K, V>> instantiateEntries() throws ModelInstantiationException {
				return QommonsUtils.filterMapE(theEntries, null, e -> e.create());
			}
		}

		/**
		 * {@link AbstractMapDef} instantiator
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <M> The sub-type of {@link ObservableMap} to create
		 */
		public static abstract class Instantiator<K, V, M extends ObservableMap<K, V>> extends ModelValueElement.Abstract<M> {
			private final List<MapEntry.MapPopulator<K, V>> theEntries;

			/**
			 * @param interpreted The interpretation to instantiate
			 * @param elements The initial content for the map
			 * @throws ModelInstantiationException If any model values fail to instantiate
			 */
			protected Instantiator(AbstractMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> elements)
				throws ModelInstantiationException {
				super(interpreted);
				theEntries = elements;
			}


			/** @return Entries defined to initialize this map */
			public List<MapEntry.MapPopulator<K, V>> getEntries() {
				return Collections.unmodifiableList(theEntries);
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				for (MapEntry.MapPopulator<?, ?> entry : theEntries)
					entry.instantiate();
			}

			@Override
			public M get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				ObservableMap.Builder<K, V, ?> builder = create(models);
				if (getModelPath() != null)
					builder.withDescription(getModelPath());
				M map = (M) builder.build();
				for (MapEntry.MapPopulator<K, V> entry : theEntries)
					entry.populateMap(map, models);
				return map;
			}

			@Override
			public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Configured entries are merely initialized, not slaved, and the map may have been modified
				// since it was created. There's no sense to making a re-initialized copy here.
				return value;
			}

			/**
			 * Creates a builder for the map
			 *
			 * @param models The model instances to use to instantiate the map
			 * @return The map builder
			 * @throws ModelInstantiationException If the map cannot be instantiated
			 */
			protected abstract ObservableMap.Builder<K, V, ?> create(ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	/** ExElement definition for the Expresso &lt;map>. */
	public static class PlainMapDef extends AbstractMapDef<ObservableMap<?, ?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public PlainMapDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Map);
		}

		@Override
		protected Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link PlainMapDef} interpretation
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		public static class Interpreted<K, V> extends AbstractMapDef.Interpreted<K, V, ObservableMap<K, V>> {
			Interpreted(PlainMapDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableMap<K, V>> create() throws ModelInstantiationException {
				return new PlainInstantiator<>(this, instantiateEntries());
			}
		}

		/**
		 * {@link PlainMapDef} instantiator
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		public static class PlainInstantiator<K, V> extends Instantiator<K, V, ObservableMap<K, V>> {
			PlainInstantiator(PlainMapDef.Interpreted<K, V> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
			}

			@Override
			protected ObservableMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				return ObservableMap.build();
			}
		}
	}

	/**
	 * ExElement definition for the Expresso &lt;sorted-map>.
	 *
	 * @param <M> The sub-type of {@link ObservableSortedMap} to create
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "sorted-map",
		interpretation = SortedMapDef.Interpreted.class,
		instance = SortedMapDef.Instantiator.class)
	public static class SortedMapDef<M extends ObservableSortedMap<?, ?>> extends AbstractMapDef<M> {
		private ExSort.ExRootSort theSort;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public SortedMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMap);
		}

		/** @return The sorting specified for the map's keys */
		@QonfigChildGetter("sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
		}

		@Override
		protected Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SortedMapDef} interpretation
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <M> The sub-type of {@link ObservableSortedMap} to create
		 */
		public static class Interpreted<K, V, M extends ObservableSortedMap<K, V>> extends AbstractMapDef.Interpreted<K, V, M> {
			private ExSort.ExRootSort.Interpreted<K> theSort;
			private Comparator<? super K> theDefaultSorting;

			Interpreted(SortedMapDef<?> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SortedMapDef<M> getDefinition() {
				return (SortedMapDef<M>) super.getDefinition();
			}

			/** @return The sorting specified for the map's keys */
			public ExSort.ExRootSort.Interpreted<K> getSort() {
				return theSort;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theSort = syncChild(getDefinition().getSort(), theSort, def -> (ExSort.ExRootSort.Interpreted<K>) def.interpret(this),
					(s, sEnv) -> s.update((TypeToken<K>) getType().getType(0), sEnv));
				if (theSort == null) {
					theDefaultSorting = ExSort.getDefaultSorting(TypeTokens.getRawType(getKeyType()));
					if (theDefaultSorting == null)
						throw new ExpressoInterpretationException(
							"No default sorting available for type " + getKeyType() + ". Specify sorting", reporting().getPosition(), 0);
				}
			}

			/**
			 * @return The instantiated sorting for the map
			 * @throws ModelInstantiationException If the sorting cannot be instantiated
			 */
			protected ModelValueInstantiator<Comparator<? super K>> instantiateSort() throws ModelInstantiationException {
				if (theSort != null)
					return theSort.instantiateSort();
				else
					return ModelValueInstantiator.literal(theDefaultSorting, "default");
			}

			@Override
			public ModelValueElement<M> create() throws ModelInstantiationException {
				return new SortedInstantiator<>(this, instantiateEntries());
			}
		}

		/**
		 * {@link SortedMapDef} instantiator
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <M> The sub-type of {@link ObservableSortedMap} to create
		 */
		public static class SortedInstantiator<K, V, M extends ObservableSortedMap<K, V>> extends Instantiator<K, V, M> {
			private final ModelValueInstantiator<Comparator<? super K>> theSort;

			SortedInstantiator(SortedMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
				theSort = interpreted.instantiateSort();
			}

			/** @return The sorting specified for the map's keys */
			public ModelValueInstantiator<Comparator<? super K>> getSort() {
				return theSort;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSort.instantiate();
			}

			@Override
			protected ObservableMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				Comparator<? super K> sort = theSort.get(models);
				return ObservableSortedMap.build(sort);
			}
		}
	}

	/**
	 * Abstract ExElement definition for the Expresso &lt;multi-map> element tagged with &lt;int-map>.
	 *
	 * @param <M> The sub-type of {@link ObservableMultiMap} that this element creates
	 */
	public static abstract class AbstractMultiMapDef<M extends ObservableMultiMap<?, ?>> extends
	ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M>> implements ModelValueElement.CompiledSynth<M, ModelValueElement<M>> {
		private final List<MapEntry> theEntries;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 * @param modelType The multi-map model type for the value
		 */
		protected AbstractMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
			theEntries = new ArrayList<>();
		}

		/** @return The entries to populate this map initially */
		public List<MapEntry> getEntries() {
			return Collections.unmodifiableList(theEntries);
		}

		@Override
		protected boolean useWrapperType() {
			return true;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			if (session.isInstance("int-map") != null)
				syncChildren(MapEntry.class, theEntries, session.asElement("int-map").forChildren("entry"));
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?, ?, M> interpretValue(ExElement.Interpreted<?> parent) {
			return (Interpreted<?, ?, M>) interpret2(parent);
		}

		/**
		 * @param parent The parent element for the interpreted multi-map
		 * @return The interpreted multi-map
		 */
		protected abstract Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent);

		/**
		 * {@link AbstractMultiMapDef} interpretation
		 *
		 * @param <K> The key type for the multi-map
		 * @param <V> The value type for the multi-map
		 * @param <M> The sub-type of {@link ObservableMultiMap} to create
		 */
		public static abstract class Interpreted<K, V, M extends ObservableMultiMap<K, V>>
		extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M>>
		implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M>> {
			private final List<MapEntry.Interpreted<K, V>> theEntries;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of this multi-map element
			 */
			protected Interpreted(AbstractMultiMapDef<?> definition, ExElement.Interpreted<?> parent) {
				super((AbstractMultiMapDef<M>) definition, parent);
				theEntries = new ArrayList<>();
			}

			@Override
			public AbstractMultiMapDef<M> getDefinition() {
				return (AbstractMultiMapDef<M>) super.getDefinition();
			}

			/** @return The entries to populate this map initially */
			public List<MapEntry.Interpreted<K, V>> getEntries() {
				return Collections.unmodifiableList(theEntries);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				ModelInstanceType<M, M> type = getType();
				TypeToken<K> keyType = (TypeToken<K>) type.getType(0);
				TypeToken<V> valueType = (TypeToken<V>) type.getType(1);
				syncChildren(getDefinition().getEntries(), theEntries, d -> (MapEntry.Interpreted<K, V>) d.interpret(this),
					(entry, eEnv) -> entry.update(eEnv, keyType, valueType));
			}

			/** @return The key type of the map */
			protected TypeToken<K> getKeyType() {
				return TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
			}

			/** @return The value type of the map */
			protected TypeToken<V> getValueType() {
				return TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
			}

			/**
			 * @return The instantiated entries to populate the initial content for the map
			 * @throws ModelInstantiationException If any initial content cannot be instantiated
			 */
			protected List<MapEntry.MapPopulator<K, V>> instantiateEntries() throws ModelInstantiationException {
				return QommonsUtils.filterMapE(theEntries, null, e -> e.create());
			}
		}

		/**
		 * {@link AbstractMultiMapDef} instantiator
		 *
		 * @param <K> The key type for the multi-map
		 * @param <V> The value type for the multi-map
		 * @param <M> The sub-type of {@link ObservableMultiMap} to create
		 */
		public static abstract class Instantiator<K, V, M extends ObservableMultiMap<K, V>> extends ModelValueElement.Abstract<M> {
			private final List<MapEntry.MapPopulator<K, V>> theEntries;

			/**
			 * @param interpreted The interpretation to instantiate
			 * @param entries The entries to populate the map initially
			 * @throws ModelInstantiationException If this multi-map cannot be instantiated
			 */
			protected Instantiator(AbstractMultiMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted);
				theEntries = entries;
			}

			/** @return The entries to populate this map initially */
			public List<MapEntry.MapPopulator<K, V>> getEntries() {
				return theEntries;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				for (MapEntry.MapPopulator<?, ?> entry : theEntries)
					entry.instantiate();
			}

			@Override
			public M get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				ObservableMultiMap.Builder<K, V, ?> builder = create(models);
				if (getModelPath() != null)
					builder.withDescription(getModelPath());
				M map = (M) builder.build(models.getUntil());
				for (MapEntry.MapPopulator<K, V> entry : theEntries)
					entry.populateMultiMap(map, models);
				return map;
			}

			@Override
			public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Configured entries are merely initialized, not slaved, and the map may have been modified
				// since it was created. There's no sense to making a re-initialized copy here.
				return value;
			}

			/**
			 * Creates the multi-map
			 *
			 * @param models The model instance to use to create the map
			 * @return The builder for the new multi-map
			 * @throws ModelInstantiationException If the map cannot be instantiated
			 */
			protected abstract ObservableMultiMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException;
		}
	}

	/** ExElement definition for the Expresso &lt;multi-map>. */
	public static class PlainMultiMapDef extends AbstractMultiMapDef<ObservableMultiMap<?, ?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public PlainMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.MultiMap);
		}

		@Override
		protected Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link PlainMultiMapDef} interpretation
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		public static class Interpreted<K, V> extends AbstractMultiMapDef.Interpreted<K, V, ObservableMultiMap<K, V>> {
			Interpreted(PlainMultiMapDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableMultiMap<K, V>> create() throws ModelInstantiationException {
				return new PlainInstantiator<>(this, instantiateEntries());
			}
		}

		/**
		 * {@link PlainMultiMapDef} instantiator
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		public static class PlainInstantiator<K, V> extends Instantiator<K, V, ObservableMultiMap<K, V>> {
			PlainInstantiator(PlainMultiMapDef.Interpreted<K, V> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
			}

			@Override
			protected ObservableMultiMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				return ObservableMultiMap.build();
			}
		}
	}

	/**
	 * ExElement definition for the Expresso &lt;sorted-multi-map>.
	 *
	 * @param <M> The sub-type of {@link ObservableSortedMultiMap} to create
	 */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "sorted-multi-map",
		interpretation = SortedMultiMapDef.Interpreted.class,
		instance = SortedMultiMapDef.Instantiator.class)
	public static class SortedMultiMapDef<M extends ObservableSortedMultiMap<?, ?>> extends AbstractMultiMapDef<M> {
		private ExSort.ExRootSort theSort;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public SortedMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMultiMap);
		}

		/** @return The sorting specified for the map's keys */
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
		}

		@Override
		protected Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link SortedMultiMapDef} interpretation
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <M> The sub-type of {@link ObservableSortedMultiMap} to create
		 */
		public static class Interpreted<K, V, M extends ObservableSortedMultiMap<K, V>> extends AbstractMultiMapDef.Interpreted<K, V, M> {
			private ExSort.ExRootSort.Interpreted<K> theSort;
			private Comparator<? super K> theDefaultSorting;

			Interpreted(SortedMultiMapDef<?> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SortedMultiMapDef<M> getDefinition() {
				return (SortedMultiMapDef<M>) super.getDefinition();
			}

			/** @return The sorting specified for the map's keys */
			public ExSort.ExRootSort.Interpreted<K> getSort() {
				return theSort;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theSort = syncChild(getDefinition().getSort(), theSort, def -> (ExSort.ExRootSort.Interpreted<K>) def.interpret(this),
					(s, sEnv) -> s.update((TypeToken<K>) getType().getType(0), sEnv));
				if (theSort == null) {
					theDefaultSorting = ExSort.getDefaultSorting(TypeTokens.getRawType(getKeyType()));
					if (theDefaultSorting == null)
						throw new ExpressoInterpretationException(
							"No default sorting available for type " + getKeyType() + ". Specify sorting", reporting().getPosition(), 0);
				}
			}

			/**
			 * @return The instantiator for the sorting for the multi-map's keys
			 * @throws ModelInstantiationException If the sorting could not be instantiated
			 */
			protected ModelValueInstantiator<Comparator<? super K>> instantiateSort() throws ModelInstantiationException {
				if (theSort != null)
					return theSort.instantiateSort();
				else
					return ModelValueInstantiator.literal(theDefaultSorting, "default");
			}

			@Override
			public ModelValueElement<M> create() throws ModelInstantiationException, RuntimeException {
				return new SortedInstantiator<>(this, instantiateEntries());
			}
		}

		/**
		 * {@link SortedMultiMapDef} instantiator
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <M> The sub-type of {@link ObservableSortedMultiMap} to create
		 */
		public static class SortedInstantiator<K, V, M extends ObservableSortedMultiMap<K, V>> extends Instantiator<K, V, M> {
			private final ModelValueInstantiator<Comparator<? super K>> theSort;

			SortedInstantiator(SortedMultiMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
				theSort = interpreted.instantiateSort();
			}

			/** @return The sorting specified for the map's keys */
			public ModelValueInstantiator<Comparator<? super K>> getSort() {
				return theSort;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSort.instantiate();
			}

			@Override
			protected ObservableMultiMap.Builder<K, V, ?> create(ModelSetInstance models) throws ModelInstantiationException {
				Comparator<? super K> sort = theSort.get(models);
				return ObservableMultiMap.<K, V> build().sortedBy(sort);
			}
		}
	}

	/** Performs an action when an event happens */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "hook",
		interpretation = Hook.Interpreted.class,
		instance = Hook.Instantiator.class)
	public static class Hook extends ExElement.Def.Abstract<ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<Observable<?>, ModelValueElement<?>> {
		private String theModelPath;
		private CompiledExpression theEvent;
		private CompiledExpression theAction;
		private ModelComponentId theEventVariable;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public Hook(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public String getModelPath() {
			return theModelPath;
		}

		@Override
		public ModelType<Observable<?>> getModelType(CompiledExpressoEnv env) {
			return ModelTypes.Event;
		}

		/** @return The type of the event */
		public VariableType getEventType() {
			return getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
		}

		/** @return The event to listen to */
		@QonfigAttributeGetter("on")
		public CompiledExpression getEvent() {
			return theEvent;
		}

		/** @return The action to perform */
		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		public CompiledExpression getElementValue() {
			return theAction;
		}

		/** @return The model ID of the variable that will contain the event that fired while its action is executing */
		public ModelComponentId getEventVariable() {
			return theEventVariable;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theModelPath = session.get(ModelValueElement.PATH_KEY, String.class);
			theEvent = getAttributeExpression("on", session);
			theAction = getValueExpression(session);
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theEventVariable = elModels.getElementValueModelId("event");
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementValueType(theEventVariable, ModelTypes.Value, (interp, env) -> {
				return ModelTypes.Value.forType(interp.getOrEvalEventType(env));
			});
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link Hook} interpretation
		 *
		 * @param <T> The type of the event
		 */
		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<ModelValueElement<Observable<T>>>
		implements ModelValueElement.InterpretedSynth<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>> {
			private TypeToken<T> theEventType;
			private InterpretedValueSynth<Observable<?>, Observable<T>> theEvent;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

			Interpreted(Hook definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Hook getDefinition() {
				return (Hook) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return theAction;
			}

			/** @return The event to listen to */
			public InterpretedValueSynth<Observable<?>, Observable<T>> getEvent() {
				return theEvent;
			}

			TypeToken<T> getOrEvalEventType(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				if (theEvent == null) {
					VariableType defType = getDefinition().getEventType();
					if (defType != null) {
						theEventType = (TypeToken<T>) defType.getType(getExpressoEnv());
						theEvent = interpret(getDefinition().getEvent(), ModelTypes.Event.forType(theEventType));
					} else if (getDefinition().getEvent() != null) {
						theEvent = interpret(getDefinition().getEvent(), ModelTypes.Event.<Observable<T>> anyAs());
						theEventType = (TypeToken<T>) theEvent.getType().getType(0);
					} else {
						theEventType = (TypeToken<T>) TypeTokens.get().VOID;
						theEvent = null;
					}
				}
				return theEventType;
			}

			/** @return The action to perform */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getAction() {
				return theAction;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				getOrEvalEventType(getExpressoEnv());
				theAction = interpret(getDefinition().getAction(), ModelTypes.Action.instance());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(theEvent, theAction);
			}

			@Override
			public ModelInstanceType<Observable<?>, Observable<T>> getType() {
				return ModelTypes.Event.forType(theEventType);
			}

			/** @return The type of the event */
			public TypeToken<T> getEventType() {
				return theEventType;
			}

			@Override
			public ModelValueElement<Observable<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		/**
		 * {@link Hook} instantiator
		 *
		 * @param <T> The type of the event
		 */
		public static class Instantiator<T> extends ModelValueElement.Abstract<Observable<T>> {
			private final ModelInstantiator theLocalModels;
			private final ModelValueInstantiator<Observable<T>> theEvent;
			private final ModelValueInstantiator<ObservableAction> theAction;
			private final T theDefaultEventValue;
			private final ModelComponentId theEventValue;

			Instantiator(Hook.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theEvent = interpreted.getEvent() == null ? null : interpreted.getEvent().instantiate();
				theDefaultEventValue = TypeTokens.get().getDefaultValue(interpreted.getEventType());
				theAction = interpreted.getAction().instantiate();
				theEventValue = interpreted.getDefinition().getEventVariable();
			}

			/** @return The event to listen to */
			public ModelValueInstantiator<Observable<T>> getEvent() {
				return theEvent;
			}

			/** @return The action to perform */
			public ModelValueInstantiator<ObservableAction> getAction() {
				return theAction;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModels.instantiate();
				if (theEvent != null)
					theEvent.instantiate();
				theAction.instantiate();
			}

			@Override
			public Observable<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = theLocalModels.wrap(models);
				Observable<T> on = theEvent == null ? null : theEvent.get(models);
				ObservableAction action = theAction.get(models);
				return create(on, action, models);
			}

			Observable<T> create(Observable<T> on, ObservableAction action, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T> event = SettableValue.<T> build()//
					.withValue(theDefaultEventValue).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theEventValue, models, event);
				if (on != null) {
					on.takeUntil(models.getUntil()).act(v -> {
						event.set(v, null);
						action.act(v);
					});
					return on;
				} else {
					action.act(null);
					return Observable.empty();
				}
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				Observable<T> oldEvent = theEvent == null ? null : theEvent.get(sourceModels);
				Observable<T> newEvent = theEvent == null ? null : theEvent.forModelCopy(oldEvent, sourceModels, newModels);
				ObservableAction oldAction = theAction.get(sourceModels);
				ObservableAction newAction = theAction.forModelCopy(oldAction, sourceModels, newModels);
				if (oldEvent == newEvent && oldAction == newAction)
					return value;
				else
					return create(newEvent, newAction, theLocalModels.wrap(newModels));
			}
		}
	}

	/** A simple action to perform */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "action",
		interpretation = Action.Interpreted.class,
		instance = Action.Instantiator.class)
	public static class Action extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<?>> {
		private CompiledExpression theAction;
		private boolean isAsync;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public Action(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
		}

		/** @return The action to perform */
		public CompiledExpression getAction() {
			return theAction;
		}

		/** @return Whether to perform the action asynchronously (off the UI event thread) */
		@QonfigAttributeGetter("async")
		public boolean isAsync() {
			return isAsync;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theAction = getValueExpression(session);
			isAsync = session.getAttribute("async", boolean.class);
		}

		@Override
		public void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		/** {@link Action} interpretation */
		public static class Interpreted
		extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
		implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this action element
			 */
			protected Interpreted(Action definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Action getDefinition() {
				return (Action) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<ObservableAction, ObservableAction> getTargetType() {
				return ModelTypes.Action.<ObservableAction> anyAs();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(getElementValue());
			}

			@Override
			public Instantiator create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		/** {@link Action} instantiator */
		public static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
			private final boolean isAsync;

			Instantiator(Action.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				isAsync = interpreted.getDefinition().isAsync();
			}

			@Override
			public ModelValueInstantiator<ObservableAction> getElementValue() {
				return (ModelValueInstantiator<ObservableAction>) super.getElementValue();
			}

			/** @return Whether to perform the action asynchronously (off the UI event thread) */
			public boolean isAsync() {
				return isAsync;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				getElementValue().instantiate();
			}

			@Override
			public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				ObservableAction action = getElementValue().get(models);
				if (isAsync)
					return new AsyncAction(action);
				else
					return action;
			}

			@Override
			public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				if (isAsync) {
					ObservableAction wrapped = ((AsyncAction) value).theWrapped;
					ObservableAction newWrapped = getElementValue().forModelCopy(wrapped, sourceModels, newModels);
					if (wrapped == newWrapped)
						return wrapped;
					return new AsyncAction(newWrapped);
				} else
					return getElementValue().forModelCopy(value, sourceModels, newModels);
			}
		}

		static class AsyncAction implements ObservableAction {
			final ObservableAction theWrapped;

			AsyncAction(ObservableAction wrapped) {
				theWrapped = wrapped;
			}

			@Override
			public void act(Object cause) throws IllegalStateException {
				QommonsTimer.getCommonInstance().offload(() -> {
					if (cause instanceof Causable)
						theWrapped.act(Causable.broken(cause));
					else
						theWrapped.act(cause);
				});
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return theWrapped.isEnabled();
			}

			@Override
			public int hashCode() {
				return theWrapped.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof AsyncAction))
					return false;
				return theWrapped.equals(((AsyncAction) obj).theWrapped);
			}

			@Override
			public String toString() {
				return theWrapped.toString();
			}
		}
	}

	/** A group of actions to perform in sequence */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "action-group",
		interpretation = ActionGroup.Interpreted.class,
		instance = ActionGroup.Instantiator.class)
	public static class ActionGroup extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<ObservableAction>> {
		private final List<Action> theActions;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public ActionGroup(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
			theActions = new ArrayList<>();
		}

		/** @return The actions to perform */
		@QonfigChildGetter("action")
		public List<ModelValueElement.CompiledSynth<ObservableAction, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			syncChildren(ModelValueElement.CompiledSynth.class, theActions, session.forChildren("action"));
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			List<ExpressoQIS> actionSessions = session.forChildren("action");
			int i = 0;
			for (ModelValueElement.CompiledSynth<ObservableAction, ?> action : theActions)
				action.prepareModelValue(actionSessions.get(i++));
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		/** {@link ActionGroup} interpretation */
		public static class Interpreted
		extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
		implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
			private final List<Action.Interpreted> theActions;

			Interpreted(ActionGroup definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theActions = new ArrayList<>();
			}

			@Override
			public ActionGroup getDefinition() {
				return (ActionGroup) super.getDefinition();
			}

			/** @return The actions to perform */
			public List<Action.Interpreted> getActions() {
				return Collections.unmodifiableList(theActions);
			}

			@Override
			protected ModelInstanceType<ObservableAction, ObservableAction> getTargetType() {
				return ModelTypes.Action.instance(); // Not actually used, since getType() is overridden
			}

			@Override
			public ModelInstanceType<ObservableAction, ObservableAction> getType() {
				return ModelTypes.Action.instance();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
					syncChildren(getDefinition().getActions(), theActions, (d, aEnv) -> (Action.Interpreted) d.interpret(aEnv),
						(i, aEnv) -> i.update(aEnv));
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.unmodifiableList(theActions);
			}

			@Override
			public ModelValueElement<ObservableAction> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		/** {@link ActionGroup} instantiator */
		public static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
			private final List<Action.Instantiator> theActions;

			Instantiator(ActionGroup.Interpreted interpreted) throws ModelInstantiationException, RuntimeException {
				super(interpreted);
				theActions = QommonsUtils.filterMapE(interpreted.getActions(), null, a -> a.create());
			}

			/** @return The actions to perform */
			public List<Action.Instantiator> getActions() {
				return theActions;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				for (ModelValueInstantiator<?> action : theActions)
					action.instantiate();
			}

			@Override
			public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				ObservableAction[] actions = new ObservableAction[theActions.size()];
				for (int i = 0; i < actions.length; i++)
					actions[i] = theActions.get(i).get(models);
				return new GroupAction(actions);
			}

			@Override
			public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				GroupAction action = (GroupAction) value;
				ObservableAction[] actionCopies = new ObservableAction[action.getActions().length];
				boolean different = false;
				for (int i = 0; i < theActions.size(); i++) {
					actionCopies[i] = ((ModelValueInstantiator<ObservableAction>) theActions.get(i)).forModelCopy(action.getActions()[i],
						sourceModels, newModels);
					if (actionCopies[i] != action.getActions()[i])
						different = true;
				}
				if (different)
					return new GroupAction(actionCopies);
				else
					return value;
			}
		}

		static class GroupAction implements ObservableAction {
			private final ObservableAction[] theActions;
			private final ObservableValue<String> theEnabled;

			GroupAction(ObservableAction[] actions) {
				theActions = actions;
				ObservableValue<String>[] actionsEnabled = new ObservableValue[actions.length];
				for (int i = 0; i < actions.length; i++)
					actionsEnabled[i] = actions[i].isEnabled();
				theEnabled = ObservableValue.firstValue(v -> v != null, null, actionsEnabled);
			}

			ObservableAction[] getActions() {
				return theActions;
			}

			@Override
			public void act(Object cause) throws IllegalStateException {
				// Don't do any actions if any are disabled
				String msg = theEnabled.get();
				if (msg != null)
					throw new IllegalStateException(msg);
				for (ObservableAction action : theActions)
					action.act(cause);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return theEnabled;
			}
		}
	}

	/** A set of actions that occur as long as a condition remains true */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "loop",
		interpretation = Loop.Interpreted.class,
		instance = Loop.Instantiator.class)
	public static class Loop extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<ObservableAction>> {
		private CompiledExpression theInit;
		private CompiledExpression theBefore;
		private CompiledExpression theWhile;
		private CompiledExpression theFinally;
		private final List<ModelValueElement.CompiledSynth<ObservableAction, ?>> theBody;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public Loop(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
			theBody = new ArrayList<>();
		}

		/** @return An action to perform as soon as this loop begins execution */
		@QonfigAttributeGetter("init")
		public CompiledExpression getInit() {
			return theInit;
		}

		/** @return An action to perform before each check of the condition */
		@QonfigAttributeGetter("before-while")
		public CompiledExpression getBefore() {
			return theBefore;
		}

		/** @return The condition determining when this loop stops */
		@QonfigAttributeGetter("while")
		public CompiledExpression getWhile() {
			return theWhile;
		}

		/** @return An action to perform when the loop stops */
		@QonfigAttributeGetter("finally")
		public CompiledExpression getFinally() {
			return theFinally;
		}

		/** @return The actions to perform as long as the condition is true */
		@QonfigChildGetter("body")
		public List<ModelValueElement.CompiledSynth<ObservableAction, ?>> getBody() {
			return Collections.unmodifiableList(theBody);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theInit = getAttributeExpression("init", session);
			theBefore = getAttributeExpression("before-while", session);
			theWhile = getAttributeExpression("while", session);
			theFinally = getAttributeExpression("finally", session);
			syncChildren(ModelValueElement.CompiledSynth.class, theBody, session.forChildren("body"));
		}

		@Override
		public void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			List<ExpressoQIS> bodySessions = session.forChildren("body");
			int i = 0;
			for (ModelValueElement.CompiledSynth<ObservableAction, ?> body : theBody)
				((ModelValueElement.Def<?, ?>) body).prepareModelValue(bodySessions.get(i++));
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		/** {@link Loop} interpretation */
		public static class Interpreted
		extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
		implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
			private InterpretedValueSynth<ObservableAction, ObservableAction> theInit;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theBefore;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theWhile;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theFinally;
			private final List<ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ?>> theBody;

			Interpreted(Loop definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theBody = new ArrayList<>();
			}

			@Override
			public Loop getDefinition() {
				return (Loop) super.getDefinition();
			}

			/** @return An action to perform as soon as this loop begins execution */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getInit() {
				return theInit;
			}

			/** @return An action to perform before each check of the condition */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getBefore() {
				return theBefore;
			}

			/** @return The condition determining when this loop stops */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getWhile() {
				return theWhile;
			}

			/** @return An action to perform when the loop stops */
			public InterpretedValueSynth<ObservableAction, ObservableAction> getFinally() {
				return theFinally;
			}

			/** @return The actions to perform as long as the condition is true */
			public List<ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ?>> getBody() {
				return Collections.unmodifiableList(theBody);
			}

			@Override
			protected ModelInstanceType<ObservableAction, ObservableAction> getTargetType() {
				return getType(); // Not actually used, since getType() is overridden
			}

			@Override
			public ModelInstanceType<ObservableAction, ObservableAction> getType() {
				return ModelTypes.Action.instance();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theInit = interpret(getDefinition().getInit(), ModelTypes.Action.instance());
				theBefore = interpret(getDefinition().getBefore(), ModelTypes.Action.instance());
				theWhile = interpret(getDefinition().getWhile(), ModelTypes.Value.forType(boolean.class));
				theFinally = interpret(getDefinition().getFinally() == null ? null : getDefinition().getFinally(),
					ModelTypes.Action.instance());
				try (Transaction t = ModelValueElement.INTERPRETING_PARENTS.installParent(this)) {
					this.syncChildren(getDefinition().getBody(), theBody,
						(def, bEnv) -> (ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ?>) def.interpret(bEnv),
						(b, bEnv) -> b.updateValue(bEnv));
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.<InterpretedValueSynth<?, ?>> concat(//
					Stream.of(theInit, theBefore, theWhile, theFinally), //
					theBody.stream()).filter(Objects::nonNull));
			}

			@Override
			public ModelValueElement<ObservableAction> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		/** {@link Loop} instantiator */
		public static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
			private final ModelInstantiator theLocalModels;
			private final ModelValueInstantiator<? extends ObservableAction> theInit;
			private final ModelValueInstantiator<? extends ObservableAction> theBefore;
			private final ModelValueInstantiator<SettableValue<Boolean>> theWhile;
			private final List<? extends ModelValueInstantiator<? extends ObservableAction>> theBody;
			private final ModelValueInstantiator<? extends ObservableAction> theFinally;

			Instantiator(Loop.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theInit = interpreted.getInit() == null ? null : interpreted.getInit().instantiate();
				theBefore = interpreted.getBefore() == null ? null : interpreted.getBefore().instantiate();
				theWhile = interpreted.getWhile().instantiate();
				theBody = QommonsUtils.filterMapE(interpreted.getBody(), null, e -> e.instantiate());
				theFinally = interpreted.getFinally() == null ? null : interpreted.getFinally().instantiate();
			}

			ModelInstantiator getLocalModels() {
				return theLocalModels;
			}

			/** @return An action to perform as soon as this loop begins execution */
			public ModelValueInstantiator<? extends ObservableAction> getInit() {
				return theInit;
			}

			/** @return An action to perform before each check of the condition */
			public ModelValueInstantiator<? extends ObservableAction> getBefore() {
				return theBefore;
			}

			/** @return The condition determining when this loop stops */
			public ModelValueInstantiator<SettableValue<Boolean>> getWhile() {
				return theWhile;
			}

			/** @return The actions to perform as long as the condition is true */
			public List<? extends ModelValueInstantiator<? extends ObservableAction>> getBody() {
				return theBody;
			}

			/** @return An action to perform when the loop stops */
			public ModelValueInstantiator<? extends ObservableAction> getFinally() {
				return theFinally;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				if (theLocalModels != null)
					theLocalModels.instantiate();
				if (theInit != null)
					theInit.instantiate();
				if (theBefore != null)
					theBefore.instantiate();
				theWhile.instantiate();
				for (ModelValueInstantiator<?> body : theBody)
					body.instantiate();
				if (theFinally != null)
					theFinally.instantiate();
			}

			@Override
			public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = theLocalModels.wrap(models);
				ObservableAction init = theInit == null ? null : theInit.get(models);
				ObservableAction before = theBefore == null ? null : theBefore.get(models);
				SettableValue<Boolean> condition = theWhile.get(models);
				List<ObservableAction> body = new ArrayList<>(theBody.size());
				for (ModelValueInstantiator<? extends ObservableAction> b : theBody)
					body.add(b.get(models));
				ObservableAction last = theFinally == null ? null : theFinally.get(models);
				return new LoopAction(init, before, condition, Collections.unmodifiableList(body), last);
			}

			@Override
			public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				LoopAction loop = (LoopAction) value;
				ObservableAction initS = loop.getInit();
				ObservableAction initA = theInit == null ? null
					: ((ModelValueInstantiator<ObservableAction>) theInit).forModelCopy(initS, sourceModels, newModels);
				ObservableAction beforeS = loop.getBeforeCondition();
				ObservableAction beforeA = theBefore == null ? null
					: ((ModelValueInstantiator<ObservableAction>) theBefore).forModelCopy(beforeS, sourceModels, newModels);
				SettableValue<Boolean> whileS = (SettableValue<Boolean>) loop.getCondition();
				SettableValue<Boolean> whileA = theWhile.forModelCopy(whileS, sourceModels, newModels);
				boolean different = initS != initA || beforeS != beforeA || whileS != whileA;
				List<ObservableAction> execAs = new ArrayList<>(theBody.size());
				for (int i = 0; i < theBody.size(); i++) {
					ObservableAction bodyS = loop.getBody().get(i);
					ObservableAction bodyA = ((ModelValueInstantiator<ObservableAction>) theBody.get(i)).forModelCopy(bodyS, sourceModels,
						newModels);
					different |= bodyS != bodyA;
				}
				ObservableAction finallyS = loop.getFinally();
				ObservableAction finallyA = theFinally == null ? null
					: ((ModelValueInstantiator<ObservableAction>) theFinally).forModelCopy(finallyS, sourceModels, newModels);
				different |= finallyS != finallyA;
				if (different)
					return new LoopAction(initA, beforeA, whileA, execAs, finallyA);
				else
					return value;
			}
		}

		static class LoopAction implements ObservableAction {
			private final ObservableAction theInit;
			private final ObservableAction theBeforeCondition;
			private final ObservableValue<Boolean> theCondition;
			private final List<ObservableAction> theBody;
			private final ObservableAction theFinally;

			public LoopAction(ObservableAction init, ObservableAction before, ObservableValue<Boolean> condition,
				List<ObservableAction> body, ObservableAction finallly) {
				theInit = init;
				theBeforeCondition = before;
				theCondition = condition;
				theBody = body;
				theFinally = finallly;
			}

			public ObservableAction getInit() {
				return theInit;
			}

			public ObservableAction getBeforeCondition() {
				return theBeforeCondition;
			}

			public ObservableValue<Boolean> getCondition() {
				return theCondition;
			}

			public List<ObservableAction> getBody() {
				return theBody;
			}

			public ObservableAction getFinally() {
				return theFinally;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				// TODO This isn't right, but it seems hard to figure out, so leaving this for now
				return SettableValue.ALWAYS_ENABLED;
			}

			@Override
			public void act(Object cause) throws IllegalStateException {
				try (Causable.CausableInUse cause2 = Causable.cause(cause)) {
					if (theInit != null)
						theInit.act(cause2);

					try {
						// Prevent infinite loops. This structure isn't terribly efficient, so I think this should be sufficient.
						int count = 0;
						while (count < 1_000_000) {
							if (theBeforeCondition != null)
								theBeforeCondition.act(cause2);
							if (!Boolean.TRUE.equals(theCondition.get()))
								break;
							for (ObservableAction body : theBody)
								body.act(cause2);
						}
					} finally {
						if (theFinally != null)
							theFinally.act(cause2);
					}
				}
			}
		}
	}

	/** A simple event */
	public static class Event extends ModelValueElement.Def.SingleTyped<Observable<?>, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<Observable<?>, ModelValueElement<?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public Event(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Event);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException { // Nothing to do
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link Event} interpretation
		 *
		 * @param <T> The type of the event
		 */
		public static class Interpreted<T>
		extends ModelValueElement.Def.SingleTyped.Interpreted<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>>
		implements ModelValueElement.InterpretedSynth<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>> {
			Interpreted(Event definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Arrays.asList(getElementValue());
			}

			@Override
			public ModelValueElement<Observable<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		/**
		 * {@link Event} instantiator
		 *
		 * @param <T> The type of the event
		 */
		public static class Instantiator<T> extends ModelValueElement.Abstract<Observable<T>> {
			Instantiator(Event.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
			}

			@Override
			public ModelValueInstantiator<Observable<T>> getElementValue() {
				return (ModelValueInstantiator<Observable<T>>) super.getElementValue();
			}

			@Override
			public Observable<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return getElementValue().get(models);
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return getElementValue().forModelCopy(value, sourceModels, newModels);
			}
		}
	}

	/** A list of values containing additional capablities for creating new elements */
	public static class ValueSet extends ModelValueElement.Def.SingleTyped<ObservableValueSet<?>, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<ObservableValueSet<?>, ModelValueElement<?>> {
		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public ValueSet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.ValueSet);
		}

		@Override
		public void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public InterpretedSynth<ObservableValueSet<?>, ?, ? extends ModelValueElement<?>> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * {@link ValueSet} interpretation
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<T>>>
		implements
		ModelValueElement.InterpretedSynth<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<T>>> {
			Interpreted(ValueSet definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ValueSet getDefinition() {
				return (ValueSet) super.getDefinition();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueElement<ObservableValueSet<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		/**
		 * {@link ValueSet} instantiator
		 *
		 * @param <T> The type of values in the collection
		 */
		public static class Instantiator<T> extends ModelValueElement.Abstract<ObservableValueSet<T>> {
			private final TypeToken<T> theType;

			Instantiator(ValueSet.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theType = (TypeToken<T>) interpreted.getType().getType(0);
			}

			@Override
			public void instantiate() {
			}

			@Override
			public ObservableValueSet<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				// Although a purely in-memory value set would be more efficient, I have yet to implement one.
				// Easiest path forward for this right now is to make an unpersisted ObservableConfig and use it to back the value set.
				// TODO At some point I should come back and make an in-memory implementation and use it here.
				ObservableConfig config = ObservableConfig.createRoot("root", null,
					__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
				return config.asValue(theType).buildEntitySet(null);
			}

			@Override
			public ObservableValueSet<T> forModelCopy(ObservableValueSet<T> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return value;
			}
		}
	}

	/** A customizable task that executes every so often */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = Timer.TIMER,
		interpretation = Timer.Interpreted.class,
		instance = Timer.Instantiator.class)
	public static class Timer extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<Instant>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<Instant>>> {
		/** The XML name of this type */
		public static final String TIMER = "timer";

		private CompiledExpression isActive;
		private CompiledExpression theFrequency;
		private boolean isStrictTiming;
		private boolean isBackground;
		private CompiledExpression theRemainingExecutions;
		private CompiledExpression theUntil;
		private CompiledExpression theRunNextIn;
		private CompiledExpression theNextExecution;
		private CompiledExpression theExecutionCount;
		private CompiledExpression isExecuting;

		/**
		 * @param parent The parent element of this value element
		 * @param qonfigType The Qonfig type of this value element
		 */
		public Timer(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		/** @return Controls when the timer is active */
		@QonfigAttributeGetter("active")
		public CompiledExpression isActive() {
			return isActive;
		}

		/** @return The frequency with which the task runs */
		@QonfigAttributeGetter("frequency")
		public CompiledExpression getFrequency() {
			return theFrequency;
		}

		/**
		 * @return The way in which this timer obeys its {@link #getFrequency() frequency}. If true, the beginnings of the task's execution
		 *         will be spaced by the frequency, meaning that if the task takes a while, there will be a smaller interval between the end
		 *         of one execution and the beginning of another. If false, the timer will wait for its frequency after each execution's end
		 *         before beginning a new execution.
		 */
		@QonfigAttributeGetter("strict-timing")
		public boolean isStrictTiming() {
			return isStrictTiming;
		}

		/** @return Whether this timer executes in the background (not on the UI thread) */
		@QonfigAttributeGetter("background")
		public boolean isBackground() {
			return isBackground;
		}

		/** @return The number of executions left before the timer stops (not set (null) by default) */
		@QonfigAttributeGetter("remaining-executions")
		public CompiledExpression getRemainingExecutions() {
			return theRemainingExecutions;
		}

		/** @return An instant after which the timer will stop (null by default) */
		@QonfigAttributeGetter("until")
		public CompiledExpression getUntil() {
			return theUntil;
		}

		/** @return A duration that, when set, will cause the timer's next execution to be that duration from the current time */
		@QonfigAttributeGetter("run-next-in")
		public CompiledExpression getRunNextIn() {
			return theRunNextIn;
		}

		/** @return The settable next execution of this timer */
		@QonfigAttributeGetter("next-execution")
		public CompiledExpression getNextExecution() {
			return theNextExecution;
		}

		/** @return The number of times this timer has executed */
		@QonfigAttributeGetter("execution-count")
		public CompiledExpression getExecutionCount() {
			return theExecutionCount;
		}

		/** @return Whether this timer is currently executing */
		@QonfigAttributeGetter("executing")
		public CompiledExpression isExecuting() {
			return isExecuting;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			isActive = getAttributeExpression("active", session);
			theFrequency = getAttributeExpression("frequency", session);
			isStrictTiming = session.getAttribute("strict-timing", boolean.class);
			isBackground = session.getAttribute("background", boolean.class);
			theRemainingExecutions = getAttributeExpression("remaining-executions", session);
			theUntil = getAttributeExpression("until", session);
			theRunNextIn = getAttributeExpression("run-next-in", session);
			theNextExecution = getAttributeExpression("next-execution", session);
			theExecutionCount = getAttributeExpression("execution-count", session);
			isExecuting = getAttributeExpression("executing", session);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		/** {@link Timer} interpretation */
		public static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Instant>, ModelValueElement<SettableValue<Instant>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Instant>, ModelValueElement<SettableValue<Instant>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> theFrequency;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theRemainingExecutions;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theUntil;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> theRunNextIn;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theNextExecution;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theExecutionCount;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isExecuting;

			Interpreted(Timer definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Timer getDefinition() {
				return (Timer) super.getDefinition();
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<Instant>> getType() {
				return ModelTypes.Value.forType(Instant.class);
			}

			// A little hacky here because the type of the element value (action) isn't the same as the type of this element
			// (value<Instant>)
			// The super class doesn't expect this situation
			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<Instant>> getTargetType() {
				return (ModelInstanceType<SettableValue<?>, SettableValue<Instant>>) (ModelInstanceType<?, ?>) ModelTypes.Action.instance();
			}

			/** @return Controls when the timer is active */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive() {
				return isActive;
			}

			/** @return The frequency with which the task runs */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> getFrequency() {
				return theFrequency;
			}

			/** @return The number of executions left before the timer stops (not set (null) by default) */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRemainingExecutions() {
				return theRemainingExecutions;
			}

			/** @return An instant after which the timer will stop (null by default) */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getUntil() {
				return theUntil;
			}

			/** @return A duration that, when set, will cause the timer's next execution to be that duration from the current time */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> getRunNextIn() {
				return theRunNextIn;
			}

			/** @return The settable next execution of this timer */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getNextExecution() {
				return theNextExecution;
			}

			/** @return The number of times this timer has executed */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getExecutionCount() {
				return theExecutionCount;
			}

			/** @return Whether this timer is currently executing */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isExecuting() {
				return isExecuting;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				isActive = interpret(getDefinition().isActive(), ModelTypes.Value.BOOLEAN);
				theFrequency = interpret(getDefinition().getFrequency(), ModelTypes.Value.forType(Duration.class));
				theRemainingExecutions = interpret(getDefinition().getRemainingExecutions(), ModelTypes.Value.INT);
				theUntil = interpret(getDefinition().getUntil(), ModelTypes.Value.forType(Instant.class));
				theRunNextIn = interpret(getDefinition().getRunNextIn(), ModelTypes.Value.forType(Duration.class));
				theNextExecution = interpret(getDefinition().getNextExecution(), ModelTypes.Value.forType(Instant.class));
				theExecutionCount = interpret(getDefinition().getExecutionCount(), ModelTypes.Value.INT);
				isExecuting = interpret(getDefinition().isExecuting(), ModelTypes.Value.BOOLEAN);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.of(isActive, theFrequency, theRemainingExecutions, theUntil, theRunNextIn, theNextExecution,
					theExecutionCount, isExecuting, getElementValue()).filter(Objects::nonNull));
			}

			@Override
			public ModelValueElement<SettableValue<Instant>> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		/** {@link Timer} instantiator */
		public static class Instantiator extends ModelValueElement.Abstract<SettableValue<Instant>> {
			private final ModelValueInstantiator<SettableValue<Boolean>> isActive;
			private final ModelValueInstantiator<SettableValue<Duration>> theFrequency;
			private final boolean isStrictTiming;
			private final boolean isBackground;
			private final ModelValueInstantiator<SettableValue<Integer>> theRemainingExecutions;
			private final ModelValueInstantiator<SettableValue<Instant>> theUntil;
			private final ModelValueInstantiator<SettableValue<Duration>> theRunNextIn;
			private final ModelValueInstantiator<SettableValue<Instant>> theNextExecution;
			private final ModelValueInstantiator<SettableValue<Integer>> theExecutionCount;
			private final ModelValueInstantiator<SettableValue<Boolean>> isExecuting;
			private final ErrorReporting theActionReporting;

			Instantiator(Timer.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				isActive = interpreted.isActive().instantiate();
				theFrequency = interpreted.getFrequency().instantiate();
				isStrictTiming = interpreted.getDefinition().isStrictTiming();
				isBackground = interpreted.getDefinition().isBackground();
				theRemainingExecutions = interpreted.getRemainingExecutions() == null ? null
					: interpreted.getRemainingExecutions().instantiate();
				theUntil = interpreted.getUntil() == null ? null : interpreted.getUntil().instantiate();
				theRunNextIn = interpreted.getRunNextIn() == null ? null : interpreted.getRunNextIn().instantiate();
				theNextExecution = interpreted.getNextExecution() == null ? null : interpreted.getNextExecution().instantiate();
				theExecutionCount = interpreted.getExecutionCount() == null ? null : interpreted.getExecutionCount().instantiate();
				isExecuting = interpreted.isExecuting() == null ? null : interpreted.isExecuting().instantiate();
				theActionReporting = getElementValue() == null ? null
					: interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
			}

			/** @return Controls when the timer is active */
			public ModelValueInstantiator<SettableValue<Boolean>> isActive() {
				return isActive;
			}

			/** @return The frequency with which the task runs */
			public ModelValueInstantiator<SettableValue<Duration>> getFrequency() {
				return theFrequency;
			}

			/** @return Whether this timer obeys its frequency strictly */
			public boolean isStrictTiming() {
				return isStrictTiming;
			}

			/** @return Whether this timer executes in the background (not on the UI thread) */
			public boolean isBackground() {
				return isBackground;
			}

			/** @return The number of executions left before the timer stops (not set (null) by default) */
			public ModelValueInstantiator<SettableValue<Integer>> getRemainingExecutions() {
				return theRemainingExecutions;
			}

			/** @return An instant after which the timer will stop (null by default) */
			public ModelValueInstantiator<SettableValue<Instant>> getUntil() {
				return theUntil;
			}

			/** @return A duration that, when set, will cause the timer's next execution to be that duration from the current time */
			public ModelValueInstantiator<SettableValue<Duration>> getRunNextIn() {
				return theRunNextIn;
			}

			/** @return The settable next execution of this timer */
			public ModelValueInstantiator<SettableValue<Instant>> getNextExecution() {
				return theNextExecution;
			}

			/** @return The number of times this timer has executed */
			public ModelValueInstantiator<SettableValue<Integer>> getExecutionCount() {
				return theExecutionCount;
			}

			/** @return Whether this timer is currently executing */
			public ModelValueInstantiator<SettableValue<Boolean>> isExecuting() {
				return isExecuting;
			}

			@Override
			public ModelValueInstantiator<ObservableAction> getElementValue() {
				return (ModelValueInstantiator<ObservableAction>) super.getElementValue();
			}

			ErrorReporting getActionReporting() {
				return theActionReporting;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				isActive.instantiate();
				theFrequency.instantiate();
				if (theRemainingExecutions != null)
					theRemainingExecutions.instantiate();
				if (theUntil != null)
					theUntil.instantiate();
				if (theRunNextIn != null)
					theRunNextIn.instantiate();
				if (theNextExecution != null)
					theNextExecution.instantiate();
				if (theExecutionCount != null)
					theExecutionCount.instantiate();
				if (isExecuting != null)
					isExecuting.instantiate();
			}

			@Override
			public SettableValue<Instant> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<Boolean> active = isActive.get(models);
				SettableValue<Duration> frequency = theFrequency.get(models);
				SettableValue<Integer> remainingExecutions = theRemainingExecutions == null ? null : theRemainingExecutions.get(models);
				SettableValue<Instant> until = theUntil == null ? null : theUntil.get(models);
				SettableValue<Duration> runNextIn = theRunNextIn == null ? null : theRunNextIn.get(models);
				SettableValue<Instant> nextExecution = theNextExecution == null ? null : theNextExecution.get(models);
				SettableValue<Integer> executionCount = theExecutionCount == null ? null : theExecutionCount.get(models);
				SettableValue<Boolean> executing = isExecuting == null ? null : isExecuting.get(models);
				ObservableAction action = getElementValue() == null ? null : getElementValue().get(models);
				return new TimerInstance(active, frequency, isStrictTiming, isBackground, remainingExecutions, until, runNextIn,
					nextExecution, executionCount, executing, action, theActionReporting);
			}

			@Override
			public SettableValue<Instant> forModelCopy(SettableValue<Instant> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				TimerInstance timer = (TimerInstance) value;
				SettableValue<Boolean> active = isActive.forModelCopy(timer.isActive, sourceModels, newModels);
				SettableValue<Duration> frequency = theFrequency.forModelCopy(timer.theFrequency, sourceModels, newModels);
				SettableValue<Integer> remainingExecutions = theRemainingExecutions == null ? null
					: theRemainingExecutions.forModelCopy(timer.theRemainingExecutions, sourceModels, newModels);
				SettableValue<Instant> until = theUntil == null ? null : theUntil.forModelCopy(timer.theUntil, sourceModels, newModels);
				SettableValue<Duration> runNextIn = theRunNextIn == null ? null
					: theRunNextIn.forModelCopy(timer.theRunNextIn, sourceModels, newModels);
				SettableValue<Instant> nextExecution = theNextExecution == null ? null
					: theNextExecution.forModelCopy(timer.theNextExecution, sourceModels, newModels);
				SettableValue<Integer> executionCount = theExecutionCount == null ? null
					: theExecutionCount.forModelCopy(timer.theExecutionCount, sourceModels, newModels);
				SettableValue<Boolean> executing = isExecuting == null ? null
					: isExecuting.forModelCopy(timer.isExecuting, sourceModels, newModels);
				ObservableAction action = getElementValue() == null ? null
					: getElementValue().forModelCopy(timer.theAction, sourceModels, newModels);

				if (active != timer.isActive || frequency != timer.theFrequency || remainingExecutions != timer.theRemainingExecutions
					|| until != timer.theUntil || runNextIn != timer.theRunNextIn || nextExecution != timer.theNextExecution
					|| executionCount != timer.theExecutionCount || executing != timer.isExecuting || action != timer.theAction)
					return new TimerInstance(active, frequency, isStrictTiming, isBackground, remainingExecutions, until, runNextIn,
						nextExecution, executionCount, executing, action, theActionReporting);
				else
					return timer;
			}
		}

		static class TimerInstance extends WrappingSettableValue<Instant> {
			final SettableValue<Boolean> isActive;
			final SettableValue<Duration> theFrequency;
			final boolean isStrictTiming;
			final boolean isBackground;
			final SettableValue<Integer> theRemainingExecutions;
			final SettableValue<Instant> theUntil;
			final SettableValue<Duration> theRunNextIn;
			final SettableValue<Instant> theNextExecution;
			final SettableValue<Integer> theExecutionCount;
			final SettableValue<Boolean> isExecuting;
			final ObservableAction theAction;
			final ErrorReporting theActionReporting;

			final QommonsTimer.TaskHandle theHandle;
			private final Causable.CausableKey theExecuteFinish;

			private volatile boolean theCallbackLock;

			TimerInstance(SettableValue<Boolean> active, SettableValue<Duration> frequency, boolean strictTiming, boolean background,
				SettableValue<Integer> remainingExecutions, SettableValue<Instant> until, SettableValue<Duration> runNextIn,
				SettableValue<Instant> nextExecution, SettableValue<Integer> executionCount, SettableValue<Boolean> executing,
				ObservableAction action, ErrorReporting actionReporting) {
				super(SettableValue.<Instant> build().withDescription("timer").build());
				isActive = active;
				theFrequency = frequency;
				isStrictTiming = strictTiming;
				isBackground = background;
				theRemainingExecutions = remainingExecutions;
				theUntil = until;
				theRunNextIn = runNextIn;
				theNextExecution = nextExecution;
				theExecutionCount = executionCount;
				isExecuting = executing;
				theAction = action;
				theActionReporting = actionReporting;

				QommonsTimer.TaskHandle task = QommonsTimer.getCommonInstance().build(this::executeIfAllowed, theFrequency.get(),
					isStrictTiming);
				if (!isBackground)
					task.onEDT();
				theHandle = task;
				theExecuteFinish = Causable.key((cause, data) -> {
				}, (cause, data) -> {
					if (isExecuting != null && isExecuting.isAcceptable(false) == null)
						isExecuting.set(false, cause);
				});
				theFrequency.noInitChanges().act(evt -> {
					if (evt.getNewValue() != null)
						task.setFrequency(evt.getNewValue(), isStrictTiming);
					else
						deactivate();
				});
				if (theRunNextIn != null) {
					theRunNextIn.changes().act(evt -> {
						if (evt.getNewValue() != null) {
							Instant nextRun = Instant.now().plus(evt.getNewValue());
							activate(nextRun, evt, false);
							task.runNextIn(evt.getNewValue());
						} else
							deactivate();
					});
				}
				if (theNextExecution != null) {
					theNextExecution.changes().act(evt -> {
						if (theCallbackLock)
							return;
						if (evt.getNewValue() != null) {
							activate(evt.getNewValue(), evt, false);
							task.runNextAt(evt.getNewValue());
						} else
							deactivate();
					});
				}
				getWrapped().noInitChanges().act(this::execute);
				// Activate last
				isActive.changes().act(evt -> {
					if (theCallbackLock)
						return;
					task.setActive(Boolean.TRUE.equals(evt.getNewValue()));
				});
			}

			private void executeIfAllowed() {
				Instant time = theHandle.getPreviousExecution();
				if (theUntil != null) {
					Instant until = theUntil.get();
					if (until != null && time.compareTo(until) > 0) {
						deactivate();
						return;
					}
				}
				// First, make sure we're allowed to execute
				if (theRemainingExecutions != null) {
					Integer remaining = theRemainingExecutions.get();
					if (remaining != null && remaining <= 0) {
						deactivate();
						return;
					}
				}
				getWrapped().set(time, null);
			}

			private void execute(ObservableValueEvent<Instant> event) {
				if (isExecuting != null && isExecuting.isAcceptable(true) == null) {
					isExecuting.set(true, event);
					event.onFinish(theExecuteFinish);
				}
				if (theRemainingExecutions != null) {
					Integer remaining = theRemainingExecutions.get();
					if (remaining != null && remaining > 0) {
						remaining = remaining - 1;
						if (theRemainingExecutions.isAcceptable(remaining) == null)
							theRemainingExecutions.set(remaining, event);
					}
				}
				if (theNextExecution != null && theNextExecution.isAcceptable(theHandle.getNextExecution()) == null) {
					theCallbackLock = true;
					try {
						theNextExecution.set(theHandle.getNextExecution(), event);
					} finally {
						theCallbackLock = false;
					}
				}
				if (theExecutionCount != null) {
					Integer count = theExecutionCount.get();
					count = count == null ? 1 : count + 1;
					if (theExecutionCount.isAcceptable(count) == null)
						theExecutionCount.set(count, event);
				}
				if (theAction != null) {
					try {
						theAction.act(event);
					} catch (Throwable e) {
						theActionReporting.error("Timer action throw an exception", e);
					}
				}
			}

			private void activate(Instant nextRun, Object cause, boolean activateTask) {
				if (nextRun == null)
					nextRun = Instant.now();
				if (theUntil != null) {
					Instant until = theUntil.get();
					if (until != null && until.compareTo(nextRun) <= 0)
						theUntil.set(null, null);
				}
				if (activateTask) {
					if (!Boolean.TRUE.equals(isActive.get()) && isActive.isAcceptable(true) == null)
						isActive.set(true, null);
					else
						theHandle.setActive(true);
				} else {
					theCallbackLock = true;
					try {
						if (!Boolean.TRUE.equals(isActive.get()) && isActive.isAcceptable(true) == null)
							isActive.set(true, null);
					} finally {
						theCallbackLock = false;
					}
				}
			}

			private void deactivate() {
				if (!Boolean.FALSE.equals(isActive.get()) && isActive.isAcceptable(false) == null)
					isActive.set(false, null);
				else
					theHandle.setActive(false);
			}

			@Override
			public <V extends Instant> Instant set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public <V extends Instant> String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String toString() {
				return "timer";
			}
		}
	}
}
