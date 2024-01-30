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

	public static class ConstantValueDef extends AbstractCompiledValue {
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

		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			public Interpreted(ConstantValueDef definition, ExElement.Interpreted<?> parent) {
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

		static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
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
				return new ConstantValue<>(getModelPath(), initV.getType(), initV.get());
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return value; // Constants are constant--no need to copy
			}
		}

		static class ConstantValue<T> extends AbstractIdentifiable implements SettableValue<T> {
			private final String theModelPath;
			private final TypeToken<T> theType;
			private final T theValue;

			public ConstantValue(String path, TypeToken<T> type, T value) {
				theModelPath = path;
				theType = type;
				theValue = value;
			}

			@Override
			public TypeToken<T> getType() {
				return theType;
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

	public static class SimpleValueDef extends AbstractCompiledValue {
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

		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			public Interpreted(SimpleValueDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SimpleValueDef getDefinition() {
				return (SimpleValueDef) super.getDefinition();
			}

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
				return new Instantiator<>(this, (TypeToken<T>) getType().getType(0), getInit() == null ? null : getInit().instantiate());
			}
		}

		static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
			private final TypeToken<T> theType;
			private final ModelValueInstantiator<SettableValue<T>> theInit;

			Instantiator(SimpleValueDef.Interpreted<T> parent, TypeToken<T> type, ModelValueInstantiator<SettableValue<T>> init)
				throws ModelInstantiationException {
				super(parent);
				theInit = parent.getInit() == null ? null : parent.getInit().instantiate();
				theType = type;
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
					SettableValue.Builder<T> builder = SettableValue.build(theType);
					if (getModelPath() != null)
						builder.withDescription(getModelPath());
					if (theInit != null) {
						SettableValue<T> initV = theInit.get(models);
						builder.withValue(initV.get());
					} else
						builder.withValue(TypeTokens.get().getDefaultValue(theType));
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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = FieldValueDef.FIELD_VALUE,
		interpretation = FieldValueDef.Interpreted.class,
		instance = FieldValueDef.Instantiator.class)
	public static class FieldValueDef extends AbstractCompiledValue {
		public static final String FIELD_VALUE = "field-value";

		private ModelComponentId theSourceAs;
		private CompiledExpression theSource;
		private CompiledExpression theSave;

		public FieldValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("source-as")
		public ModelComponentId getSourceAs() {
			return theSourceAs;
		}

		@QonfigAttributeGetter("source")
		public CompiledExpression getSource() {
			return theSource;
		}

		@QonfigAttributeGetter("save")
		public CompiledExpression getSave() {
			return theSave;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			theSource = getAttributeExpression("source", session);
			theSave = getAttributeExpression("save", session);
			String sourceAsName = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceAs = elModels.getElementValueModelId(sourceAsName);
			elModels.<Interpreted<?>, SettableValue<?>> satisfyElementValueType(theSourceAs, ModelTypes.Value, (interp, env) -> {
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

		static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theSource;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theSave;

			Interpreted(FieldValueDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FieldValueDef getDefinition() {
				return (FieldValueDef) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getSource() {
				return theSource;
			}

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

		public static class Instantiator<T> extends ModelValueElement.Abstract<SettableValue<T>> {
			private final ModelInstantiator theLocalModels;
			private final ModelValueInstantiator<SettableValue<T>> theSource;
			private final ModelValueInstantiator<ObservableAction> theSave;
			private final ModelComponentId theSourceAs;

			Instantiator(FieldValueDef.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theSource = interpreted.getSource().instantiate();
				theSave = interpreted.getSave().instantiate();
				theSourceAs = interpreted.getDefinition().getSourceAs();
			}

			public ModelValueInstantiator<SettableValue<T>> getSource() {
				return theSource;
			}

			public ModelValueInstantiator<ObservableAction> getSave() {
				return theSave;
			}

			public ModelComponentId getSourceAs() {
				return theSourceAs;
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
				SettableValue<T> sourceAs = SettableValue.build(source.getType()).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAs, models, sourceAs);
				return new FieldValue<>(source, save, sourceAs);
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
					SettableValue<T> sourceAs = SettableValue.build(newSource.getType()).build();
					ExFlexibleElementModelAddOn.satisfyElementValue(theSourceAs, newModels, sourceAs);
					return new FieldValue<>(newSource, newSave, sourceAs);
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

	/** ExElement definition for the Expresso &lt;element>. */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "element",
		interpretation = CollectionElement.Interpreted.class,
		instance = CollectionElement.CollectionPopulator.class)
	public static class CollectionElement extends AbstractCompiledValue {
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

		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T> {
			public Interpreted(CollectionElement definition, ExElement.Interpreted<?> parent) {
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

		public interface CollectionPopulator<T> extends ModelValueElement<SettableValue<T>> {
			boolean populateCollection(BetterCollection<? super T> collection, ModelSetInstance models) throws ModelInstantiationException;

			static class Default<T> extends ModelValueElement.Abstract<SettableValue<T>> implements CollectionPopulator<T> {
				private final ErrorReporting theReporting;

				public Default(CollectionElement.Interpreted<T> interpreted) throws ModelInstantiationException {
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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "int-list",
		interpretation = AbstractCollectionDef.Interpreted.class,
		instance = AbstractCollectionDef.Instantiator.class)
	public static abstract class AbstractCollectionDef<C extends ObservableCollection<?>> extends
	ModelValueElement.Def.SingleTyped<C, ModelValueElement<C>> implements ModelValueElement.CompiledSynth<C, ModelValueElement<C>> {
		private QonfigElementOrAddOn theIntListType;
		private final List<CollectionElement> theElements;

		protected AbstractCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.SingleTyped<C> modelType) {
			super(parent, qonfigType, modelType);
			theElements = new ArrayList<>();
		}

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

		protected abstract Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<T, C extends ObservableCollection<T>>
		extends ModelValueElement.Def.SingleTyped.Interpreted<C, C, ModelValueElement<C>>
		implements ModelValueElement.InterpretedSynth<C, C, ModelValueElement<C>> {
			private final List<CollectionElement.Interpreted<T>> theElements;

			protected Interpreted(AbstractCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
				super((AbstractCollectionDef<C>) definition, parent);
				theElements = new ArrayList<>();
			}

			@Override
			public AbstractCollectionDef<C> getDefinition() {
				return (AbstractCollectionDef<C>) super.getDefinition();
			}

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

			protected TypeToken<T> getValueType() {
				return TypeTokens.get().wrap((TypeToken<T>) getTargetType().getType(0));
			}

			protected List<CollectionElement.CollectionPopulator<T>> instantiateElements() throws ModelInstantiationException {
				return BetterList.of2(theElements.stream(), el -> el.create());
			}
		}

		public static abstract class Instantiator<T, C extends ObservableCollection<T>> extends ModelValueElement.Abstract<C> {
			private final TypeToken<T> theType;
			private final List<CollectionElement.CollectionPopulator<T>> theElements;

			protected Instantiator(AbstractCollectionDef.Interpreted<T, C> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted);
				theType = interpreted.getValueType();
				theElements = elements;
			}

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
				ObservableCollectionBuilder<T, ?> builder = create(theType, models);
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

			protected abstract ObservableCollectionBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	public static class PlainCollectionDef extends AbstractCollectionDef<ObservableCollection<?>> {
		public PlainCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Collection);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableCollection<T>> {
			public Interpreted(PlainCollectionDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableCollection<T>> create() throws ModelInstantiationException {
				return new PlainInstantiator<>(this, instantiateElements());
			}
		}

		static class PlainInstantiator<T> extends Instantiator<T, ObservableCollection<T>> {
			public PlainInstantiator(PlainCollectionDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException {
				return ObservableCollection.build(type);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "sorted-model-value",
		interpretation = AbstractSortedCollectionDef.Interpreted.class,
		instance = AbstractSortedCollectionDef.SortedInstantiator.class)
	public static abstract class AbstractSortedCollectionDef<C extends ObservableSortedCollection<?>> extends AbstractCollectionDef<C> {
		private ExSort.ExRootSort theSort;

		protected AbstractSortedCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType,
			ModelType.SingleTyped<C> modelType) {
			super(parent, qonfigType, modelType);
		}

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

		public static abstract class Interpreted<T, C extends ObservableSortedCollection<T>>
		extends AbstractCollectionDef.Interpreted<T, C> {
			private ExSort.ExRootSort.Interpreted<T> theSort;
			private Comparator<? super T> theDefaultSorting;

			protected Interpreted(AbstractSortedCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public AbstractSortedCollectionDef<C> getDefinition() {
				return (AbstractSortedCollectionDef<C>) super.getDefinition();
			}

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

			protected ModelValueInstantiator<Comparator<? super T>> instantiateSort() throws ModelInstantiationException {
				if (theSort != null)
					return theSort.instantiateSort();
				else
					return ModelValueInstantiator.literal(theDefaultSorting, "default");
			}
		}

		public static abstract class SortedInstantiator<T, C extends ObservableSortedCollection<T>> extends Instantiator<T, C> {
			private final ModelValueInstantiator<Comparator<? super T>> theSort;

			protected SortedInstantiator(AbstractSortedCollectionDef.Interpreted<T, C> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
				theSort = interpreted.instantiateSort();
			}

			public ModelValueInstantiator<Comparator<? super T>> getSort() {
				return theSort;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSort.instantiate();
			}

			@Override
			protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super T> sort = theSort.get(models);
				return create(type, sort, models);
			}

			protected abstract SortedBuilder<T, ?> create(TypeToken<T> type, Comparator<? super T> sort, ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	public static class SortedCollectionDef extends AbstractSortedCollectionDef<ObservableSortedCollection<?>> {
		public SortedCollectionDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.SortedCollection);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedCollection<T>> {
			public Interpreted(AbstractSortedCollectionDef<?> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableSortedCollection<T>> create() throws ModelInstantiationException {
				return new SimpleSortedInstantiator<>(this, instantiateElements());
			}
		}

		static class SimpleSortedInstantiator<T> extends SortedInstantiator<T, ObservableSortedCollection<T>> {
			SimpleSortedInstantiator(SortedCollectionDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(TypeToken<T> type, Comparator<? super T> sort,
				ModelSetInstance models) {
				return ObservableCollection.build(type).sortBy(sort);
			}
		}
	}

	public static class SetDef extends AbstractCollectionDef<ObservableSet<?>> {
		public SetDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Set);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableSet<T>> {
			public Interpreted(SetDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableSet<T>> create() throws ModelInstantiationException {
				return new SetInstantiator<>(this, instantiateElements());
			}
		}

		static class SetInstantiator<T> extends Instantiator<T, ObservableSet<T>> {
			public SetInstantiator(SetDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException {
				return ObservableSet.build(type);
			}
		}
	}

	public static class SortedSetDef extends AbstractSortedCollectionDef<ObservableSortedSet<?>> {
		public SortedSetDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.SortedSet);
		}

		@Override
		protected Interpreted<?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedSet<T>> {
			public Interpreted(SortedSetDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableSortedSet<T>> create() throws ModelInstantiationException {
				return new SortedSetInstantiator<>(this, instantiateElements());
			}
		}

		static class SortedSetInstantiator<T> extends SortedInstantiator<T, ObservableSortedSet<T>> {
			SortedSetInstantiator(SortedSetDef.Interpreted<T> interpreted, List<CollectionPopulator<T>> elements)
				throws ModelInstantiationException {
				super(interpreted, elements);
			}

			@Override
			protected ObservableCollectionBuilder.DistinctSortedBuilder<T, ?> create(TypeToken<T> type, Comparator<? super T> sort,
				ModelSetInstance models) {
				return ObservableCollection.build(type).distinctSorted(sort);
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

		public MapEntry(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("key")
		public CompiledExpression getKey() {
			return theKey;
		}

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

		public Interpreted<?, ?> interpret(ModelValueElement.Interpreted<?, ?, ?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<K, V>
		extends ModelValueElement.Interpreted.Abstract<SettableValue<?>, SettableValue<V>, ModelValueElement<SettableValue<V>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<K>> theKey;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<V>> theValue;

			public Interpreted(MapEntry definition, ExElement.Interpreted<?> parent) {
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

			public InterpretedValueSynth<SettableValue<?>, SettableValue<K>> getKey() {
				return theKey;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<V>> getElementValue() {
				return theValue;
			}

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

		public interface MapPopulator<K, V> extends ModelValueElement<SettableValue<V>> {
			@Override
			void instantiate() throws ModelInstantiationException;

			boolean populateMap(BetterMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException;

			boolean populateMultiMap(BetterMultiMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException;
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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "int-map",
		interpretation = AbstractMapDef.Interpreted.class,
		instance = AbstractMapDef.Instantiator.class)
	public static abstract class AbstractMapDef<M extends ObservableMap<?, ?>> extends
	ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M>> implements ModelValueElement.CompiledSynth<M, ModelValueElement<M>> {
		private final List<MapEntry> theEntries;

		protected AbstractMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
			theEntries = new ArrayList<>();
		}

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

		protected abstract Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<K, V, M extends ObservableMap<K, V>>
		extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M>>
		implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M>> {
			private final List<MapEntry.Interpreted<K, V>> theEntries;

			protected Interpreted(AbstractMapDef<?> definition, ExElement.Interpreted<?> parent) {
				super((AbstractMapDef<M>) definition, parent);
				theEntries = new ArrayList<>();
			}

			@Override
			public AbstractMapDef<M> getDefinition() {
				return (AbstractMapDef<M>) super.getDefinition();
			}

			public List<MapEntry.Interpreted<K, V>> getEntries() {
				return Collections.unmodifiableList(theEntries);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList(); // Elements are initialization only, this value is independent (fundamental)
			}

			protected TypeToken<K> getKeyType() {
				return TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
			}

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

			protected List<MapEntry.MapPopulator<K, V>> instantiateEntries() throws ModelInstantiationException {
				return QommonsUtils.filterMapE(theEntries, null, e -> e.create());
			}
		}

		public static abstract class Instantiator<K, V, M extends ObservableMap<K, V>> extends ModelValueElement.Abstract<M> {
			private final TypeToken<K> theKeyType;
			private final TypeToken<V> theValueType;
			private final List<MapEntry.MapPopulator<K, V>> theEntries;

			protected Instantiator(AbstractMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> elements)
				throws ModelInstantiationException {
				super(interpreted);
				ModelInstanceType<?, ?> type = interpreted.getType();
				theKeyType = interpreted.getKeyType();
				theValueType = interpreted.getValueType();
				theEntries = elements;
			}

			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			public TypeToken<V> getValueType() {
				return theValueType;
			}

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
				ObservableMap.Builder<K, V, ?> builder = create(theKeyType, theValueType, models);
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

			protected abstract ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException;
		}
	}

	public static class PlainMapDef extends AbstractMapDef<ObservableMap<?, ?>> {
		public PlainMapDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Map);
		}

		@Override
		protected Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<K, V> extends AbstractMapDef.Interpreted<K, V, ObservableMap<K, V>> {
			public Interpreted(PlainMapDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableMap<K, V>> create() throws ModelInstantiationException {
				return new PlainInstantiator<>(this, instantiateEntries());
			}
		}

		static class PlainInstantiator<K, V> extends Instantiator<K, V, ObservableMap<K, V>> {
			public PlainInstantiator(PlainMapDef.Interpreted<K, V> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
			}

			@Override
			protected ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				return ObservableMap.build(keyType, valueType);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "sorted-map",
		interpretation = SortedMapDef.Interpreted.class,
		instance = SortedMapDef.Instantiator.class)
	public static class SortedMapDef<M extends ObservableSortedMap<?, ?>> extends AbstractMapDef<M> {
		private ExSort.ExRootSort theSort;

		public SortedMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMap);
		}

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

		public static class Interpreted<K, V, M extends ObservableSortedMap<K, V>> extends AbstractMapDef.Interpreted<K, V, M> {
			private ExSort.ExRootSort.Interpreted<K> theSort;
			private Comparator<? super K> theDefaultSorting;

			public Interpreted(SortedMapDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SortedMapDef<M> getDefinition() {
				return (SortedMapDef<M>) super.getDefinition();
			}

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

		public static class SortedInstantiator<K, V, M extends ObservableSortedMap<K, V>> extends Instantiator<K, V, M> {
			private final ModelValueInstantiator<Comparator<? super K>> theSort;

			protected SortedInstantiator(SortedMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
				theSort = interpreted.instantiateSort();
			}

			public ModelValueInstantiator<Comparator<? super K>> getSort() {
				return theSort;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSort.instantiate();
			}

			@Override
			protected ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super K> sort = theSort.get(models);
				return ObservableSortedMap.build(keyType, valueType, sort);
			}
		}
	}

	public static abstract class AbstractMultiMapDef<M extends ObservableMultiMap<?, ?>> extends
	ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M>> implements ModelValueElement.CompiledSynth<M, ModelValueElement<M>> {
		private final List<MapEntry> theEntries;

		protected AbstractMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
			theEntries = new ArrayList<>();
		}

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

		protected abstract Interpreted<?, ?, ?> interpret2(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<K, V, M extends ObservableMultiMap<K, V>>
		extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M>>
		implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M>> {
			private final List<MapEntry.Interpreted<K, V>> theEntries;

			protected Interpreted(AbstractMultiMapDef<?> definition, ExElement.Interpreted<?> parent) {
				super((AbstractMultiMapDef<M>) definition, parent);
				theEntries = new ArrayList<>();
			}

			@Override
			public AbstractMultiMapDef<M> getDefinition() {
				return (AbstractMultiMapDef<M>) super.getDefinition();
			}

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

			protected TypeToken<K> getKeyType() {
				return TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
			}

			protected TypeToken<V> getValueType() {
				return TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
			}

			protected List<MapEntry.MapPopulator<K, V>> instantiateEntries() throws ModelInstantiationException, RuntimeException {
				return QommonsUtils.filterMapE(theEntries, null, e -> e.create());
			}
		}

		protected static abstract class Instantiator<K, V, M extends ObservableMultiMap<K, V>> extends ModelValueElement.Abstract<M> {
			private final TypeToken<K> theKeyType;
			private final TypeToken<V> theValueType;
			private final List<MapEntry.MapPopulator<K, V>> theEntries;

			protected Instantiator(AbstractMultiMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> elements)
				throws ModelInstantiationException {
				super(interpreted);
				theKeyType = interpreted.getKeyType();
				theValueType = interpreted.getValueType();
				theEntries = elements;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				for (MapEntry.MapPopulator<?, ?> entry : theEntries)
					entry.instantiate();
			}

			@Override
			public M get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				ObservableMultiMap.Builder<K, V, ?> builder = create(theKeyType, theValueType, models);
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

			protected abstract ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models) throws ModelInstantiationException;
		}
	}

	public static class PlainMultiMapDef extends AbstractMultiMapDef<ObservableMultiMap<?, ?>> {
		public PlainMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.MultiMap);
		}

		@Override
		protected Interpreted<?, ?> interpret2(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<K, V> extends AbstractMultiMapDef.Interpreted<K, V, ObservableMultiMap<K, V>> {
			public Interpreted(PlainMultiMapDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelValueElement<ObservableMultiMap<K, V>> create() throws ModelInstantiationException {
				return new PlainInstantiator<>(this, instantiateEntries());
			}
		}

		static class PlainInstantiator<K, V> extends Instantiator<K, V, ObservableMultiMap<K, V>> {
			public PlainInstantiator(PlainMultiMapDef.Interpreted<K, V> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
			}

			@Override
			protected ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				return ObservableMultiMap.build(keyType, valueType);
			}
		}
	}

	public static class SortedMultiMapDef<M extends ObservableSortedMultiMap<?, ?>> extends AbstractMultiMapDef<M> {
		private ExSort.ExRootSort theSort;

		public SortedMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMultiMap);
		}

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

		public static class Interpreted<K, V, M extends ObservableSortedMultiMap<K, V>> extends AbstractMultiMapDef.Interpreted<K, V, M> {
			private ExSort.ExRootSort.Interpreted<K> theSort;
			private Comparator<? super K> theDefaultSorting;

			public Interpreted(SortedMultiMapDef definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SortedMultiMapDef<M> getDefinition() {
				return (SortedMultiMapDef<M>) super.getDefinition();
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

		static class SortedInstantiator<K, V, M extends ObservableSortedMultiMap<K, V>> extends Instantiator<K, V, M> {
			private final ModelValueInstantiator<Comparator<? super K>> theSort;

			protected SortedInstantiator(SortedMultiMapDef.Interpreted<K, V, M> interpreted, List<MapEntry.MapPopulator<K, V>> entries)
				throws ModelInstantiationException {
				super(interpreted, entries);
				theSort = interpreted.instantiateSort();
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				super.instantiate();
				theSort.instantiate();
			}

			@Override
			protected ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super K> sort = theSort.get(models);
				return ObservableMultiMap.build(keyType, valueType).sortedBy(sort);
			}
		}
	}

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

		public VariableType getEventType() {
			return getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
		}

		@QonfigAttributeGetter("on")
		public CompiledExpression getEvent() {
			return theEvent;
		}

		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		public CompiledExpression getElementValue() {
			return theAction;
		}

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

		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<ModelValueElement<Observable<T>>>
		implements ModelValueElement.InterpretedSynth<Observable<?>, Observable<T>, ModelValueElement<Observable<T>>> {
			private TypeToken<T> theEventType;
			private InterpretedValueSynth<Observable<?>, Observable<T>> theEvent;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theAction;

			public Interpreted(Hook definition, ExElement.Interpreted<?> parent) {
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

			public TypeToken<T> getEventType() {
				return theEventType;
			}

			@Override
			public ModelValueElement<Observable<T>> create() throws ModelInstantiationException {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T, A> extends ModelValueElement.Abstract<Observable<T>> {
			private final TypeToken<T> theType;
			private final ModelInstantiator theLocalModels;
			private final ModelValueInstantiator<Observable<T>> theEvent;
			private final ModelValueInstantiator<ObservableAction> theAction;
			private final ModelComponentId theEventValue;

			Instantiator(Hook.Interpreted<T> interpreted) throws ModelInstantiationException {
				super(interpreted);
				theType = interpreted.getEventType();
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theEvent = interpreted.getEvent() == null ? null : interpreted.getEvent().instantiate();
				theAction = interpreted.getAction().instantiate();
				theEventValue = interpreted.getDefinition().getEventVariable();
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
				SettableValue<T> event = SettableValue.build(theType)//
					.withValue(TypeTokens.get().getDefaultValue(theType)).build();
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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "action",
		interpretation = Action.Interpreted.class,
		instance = Action.Instantiator.class)
	public static class Action extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<?>> {
		private CompiledExpression theAction;
		private boolean isAsync;

		public Action(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
		}

		public CompiledExpression getAction() {
			return theAction;
		}

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

		public static class Interpreted
		extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
		implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
			public Interpreted(Action definition, ExElement.Interpreted<?> parent) {
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
			public ModelValueElement<ObservableAction> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
			private final boolean isAsync;

			Instantiator(Action.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				isAsync = interpreted.getDefinition().isAsync();
			}

			@Override
			public ModelValueInstantiator<ObservableAction> getElementValue() {
				return (ModelValueInstantiator<ObservableAction>) super.getElementValue();
			}

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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "action-group",
		interpretation = ActionGroup.Interpreted.class,
		instance = ActionGroup.Instantiator.class)
	public static class ActionGroup extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<ObservableAction>> {
		private final List<ModelValueElement.CompiledSynth<ObservableAction, ?>> theActions;

		public ActionGroup(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
			theActions = new ArrayList<>();
		}

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

		static class Interpreted
		extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
		implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
			private final List<Action.Interpreted> theActions;

			public Interpreted(ActionGroup definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theActions = new ArrayList<>();
			}

			@Override
			public ActionGroup getDefinition() {
				return (ActionGroup) super.getDefinition();
			}

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

		static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
			private final List<ModelValueInstantiator<? extends ObservableAction>> theActions;

			Instantiator(ActionGroup.Interpreted interpreted) throws ModelInstantiationException, RuntimeException {
				super(interpreted);
				theActions = QommonsUtils.filterMapE(interpreted.getActions(), null, a -> a.instantiate());
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
				theEnabled = ObservableValue.firstValue(TypeTokens.get().STRING, v -> v != null, null, actionsEnabled);
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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "loop",
		interpretation = Loop.Interpreted.class,
		instance = Loop.Instantiator.class)
	public static class Loop extends ModelValueElement.Def.Abstract<ObservableAction, ModelValueElement<ObservableAction>>
	implements ModelValueElement.CompiledSynth<ObservableAction, ModelValueElement<ObservableAction>> {
		private CompiledExpression theInit;
		private CompiledExpression theBefore;
		private CompiledExpression theWhile;
		private CompiledExpression theBeforeBody;
		private CompiledExpression theAfterBody;
		private CompiledExpression theFinally;
		private final List<ModelValueElement.CompiledSynth<ObservableAction, ?>> theBody;

		public Loop(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
			theBody = new ArrayList<>();
		}

		@QonfigAttributeGetter("init")
		public CompiledExpression getInit() {
			return theInit;
		}

		@QonfigAttributeGetter("before-while")
		public CompiledExpression getBefore() {
			return theBefore;
		}

		@QonfigAttributeGetter("while")
		public CompiledExpression getWhile() {
			return theWhile;
		}

		@QonfigAttributeGetter("before-body")
		public CompiledExpression getBeforeBody() {
			return theBeforeBody;
		}

		@QonfigAttributeGetter("after-body")
		public CompiledExpression getAfterBody() {
			return theAfterBody;
		}

		@QonfigAttributeGetter("finally")
		public CompiledExpression getFinally() {
			return theFinally;
		}

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
			theBeforeBody = getAttributeExpression("before-body", session);
			theAfterBody = getAttributeExpression("after-body", session);
			theFinally = getAttributeExpression("finally", session);
			syncChildren(ModelValueElement.CompiledSynth.class, theBody, session.forChildren("body"));
		}

		@Override
		public void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			List<ExpressoQIS> bodySessions = session.forChildren("body");
			int i = 0;
			for (ModelValueElement.CompiledSynth<ObservableAction, ?> body : theBody) {
				if (body instanceof ModelValueElement.Def)
					((ModelValueElement.Def<?, ?>) body).prepareModelValue(bodySessions.get(i++));
			}
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted
		extends ModelValueElement.Interpreted.Abstract<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>>
		implements ModelValueElement.InterpretedSynth<ObservableAction, ObservableAction, ModelValueElement<ObservableAction>> {
			private InterpretedValueSynth<ObservableAction, ObservableAction> theInit;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theBefore;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theWhile;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theBeforeBody;
			private InterpretedValueSynth<ObservableAction, ObservableAction> theAfterBody;
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

			public InterpretedValueSynth<ObservableAction, ObservableAction> getInit() {
				return theInit;
			}

			public InterpretedValueSynth<ObservableAction, ObservableAction> getBefore() {
				return theBefore;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getWhile() {
				return theWhile;
			}

			public InterpretedValueSynth<ObservableAction, ObservableAction> getBeforeBody() {
				return theBeforeBody;
			}

			public InterpretedValueSynth<ObservableAction, ObservableAction> getAfterBody() {
				return theAfterBody;
			}

			public InterpretedValueSynth<ObservableAction, ObservableAction> getFinally() {
				return theFinally;
			}

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
				theBeforeBody = interpret(getDefinition().getBeforeBody() == null ? null : getDefinition().getBeforeBody(),
					ModelTypes.Action.instance());
				theAfterBody = interpret(getDefinition().getAfterBody() == null ? null : getDefinition().getAfterBody(),
					ModelTypes.Action.instance());
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
					Stream.of(theInit, theBefore, theWhile, theBeforeBody, theAfterBody, theFinally), //
					theBody.stream()).filter(Objects::nonNull));
			}

			@Override
			public ModelValueElement<ObservableAction> create() throws ModelInstantiationException {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends ModelValueElement.Abstract<ObservableAction> {
			private final ModelInstantiator theLocalModels;
			private final ModelValueInstantiator<? extends ObservableAction> theInit;
			private final ModelValueInstantiator<? extends ObservableAction> theBefore;
			private final ModelValueInstantiator<SettableValue<Boolean>> theWhile;
			private final ModelValueInstantiator<? extends ObservableAction> theBeforeBody;
			private final List<? extends ModelValueInstantiator<? extends ObservableAction>> theBody;
			private final ModelValueInstantiator<? extends ObservableAction> theAfterBody;
			private final ModelValueInstantiator<? extends ObservableAction> theFinally;

			Instantiator(Loop.Interpreted interpreted) throws ModelInstantiationException {
				super(interpreted);
				theLocalModels = interpreted.getExpressoEnv().getModels().instantiate();
				theInit = interpreted.getInit() == null ? null : interpreted.getInit().instantiate();
				theBefore = interpreted.getBefore() == null ? null : interpreted.getBefore().instantiate();
				theWhile = interpreted.getWhile().instantiate();
				theBeforeBody = interpreted.getBeforeBody() == null ? null : interpreted.getBeforeBody().instantiate();
				theBody = QommonsUtils.filterMapE(interpreted.getBody(), null, e -> e.instantiate());
				theAfterBody = interpreted.getAfterBody() == null ? null : interpreted.getAfterBody().instantiate();
				theFinally = interpreted.getFinally() == null ? null : interpreted.getFinally().instantiate();
			}

			public ModelInstantiator getLocalModels() {
				return theLocalModels;
			}

			public ModelValueInstantiator<? extends ObservableAction> getInit() {
				return theInit;
			}

			public ModelValueInstantiator<? extends ObservableAction> getBefore() {
				return theBefore;
			}

			public ModelValueInstantiator<SettableValue<Boolean>> getWhile() {
				return theWhile;
			}

			public ModelValueInstantiator<? extends ObservableAction> getBeforeBody() {
				return theBeforeBody;
			}

			public List<? extends ModelValueInstantiator<? extends ObservableAction>> getBody() {
				return theBody;
			}

			public ModelValueInstantiator<? extends ObservableAction> getAfterBody() {
				return theAfterBody;
			}

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
				if (theBeforeBody != null)
					theBeforeBody.instantiate();
				for (ModelValueInstantiator<?> body : theBody)
					body.instantiate();
				if (theAfterBody != null)
					theAfterBody.instantiate();
				if (theFinally != null)
					theFinally.instantiate();
			}

			@Override
			public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = theLocalModels.wrap(models);
				ObservableAction init = theInit == null ? null : theInit.get(models);
				ObservableAction before = theBefore == null ? null : theBefore.get(models);
				SettableValue<Boolean> condition = theWhile.get(models);
				ObservableAction beforeBody = theBeforeBody == null ? null : theBeforeBody.get(models);
				List<ObservableAction> body = new ArrayList<>(theBody.size());
				for (ModelValueInstantiator<? extends ObservableAction> b : theBody)
					body.add(b.get(models));
				ObservableAction afterBody = theAfterBody == null ? null : theAfterBody.get(models);
				ObservableAction last = theFinally == null ? null : theFinally.get(models);
				return new LoopAction(init, before, condition, beforeBody, Collections.unmodifiableList(body), afterBody, last);
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
				ObservableAction beforeBodyS = loop.getBeforeBody();
				ObservableAction beforeBodyA = theBeforeBody == null ? null
					: ((ModelValueInstantiator<ObservableAction>) theBeforeBody).forModelCopy(beforeBodyS, sourceModels, newModels);
				boolean different = initS != initA || beforeS != beforeA || whileS != whileA || beforeBodyS != beforeBodyA;
				List<ObservableAction> execAs = new ArrayList<>(theBody.size());
				for (int i = 0; i < theBody.size(); i++) {
					ObservableAction bodyS = loop.getBody().get(i);
					ObservableAction bodyA = ((ModelValueInstantiator<ObservableAction>) theBody.get(i)).forModelCopy(bodyS, sourceModels,
						newModels);
					different |= bodyS != bodyA;
				}
				ObservableAction afterBodyS = loop.getAfterBody();
				ObservableAction afterBodyA = theAfterBody == null ? null
					: ((ModelValueInstantiator<ObservableAction>) theAfterBody).forModelCopy(afterBodyS, sourceModels, newModels);
				ObservableAction finallyS = loop.getFinally();
				ObservableAction finallyA = theFinally == null ? null
					: ((ModelValueInstantiator<ObservableAction>) theFinally).forModelCopy(finallyS, sourceModels, newModels);
				different |= afterBodyS != afterBodyA || finallyS != finallyA;
				if (different)
					return new LoopAction(initA, beforeA, whileA, beforeBodyA, execAs, afterBodyA, finallyA);
				else
					return value;
			}
		}

		static class LoopAction implements ObservableAction {
			private final ObservableAction theInit;
			private final ObservableAction theBeforeCondition;
			private final ObservableValue<Boolean> theCondition;
			private final ObservableAction theBeforeBody;
			private final List<ObservableAction> theBody;
			private final ObservableAction theAfterBody;
			private final ObservableAction theFinally;

			public LoopAction(ObservableAction init, ObservableAction before, ObservableValue<Boolean> condition,
				ObservableAction beforeBody, List<ObservableAction> body, ObservableAction afterBody, ObservableAction finallly) {
				theInit = init;
				theBeforeCondition = before;
				theCondition = condition;
				theBeforeBody = beforeBody;
				theBody = body;
				theAfterBody = afterBody;
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

			public ObservableAction getBeforeBody() {
				return theBeforeBody;
			}

			public List<ObservableAction> getBody() {
				return theBody;
			}

			public ObservableAction getAfterBody() {
				return theAfterBody;
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
							if (theBeforeBody != null)
								theBeforeBody.act(cause2);
							for (ObservableAction body : theBody)
								body.act(cause2);
							if (theAfterBody != null)
								theAfterBody.act(cause2);
						}
					} finally {
						if (theFinally != null)
							theFinally.act(cause2);
					}
				}
			}
		}
	}

	public static class Event extends ModelValueElement.Def.SingleTyped<Observable<?>, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<Observable<?>, ModelValueElement<?>> {
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

		static class Interpreted<T>
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

		static class Instantiator<T> extends ModelValueElement.Abstract<Observable<T>> {
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

	public static class ValueSet extends ModelValueElement.Def.SingleTyped<ObservableValueSet<?>, ModelValueElement<?>>
	implements ModelValueElement.CompiledSynth<ObservableValueSet<?>, ModelValueElement<?>> {
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

		static class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<T>>>
		implements
		ModelValueElement.InterpretedSynth<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<T>>> {
			public Interpreted(ValueSet definition, ExElement.Interpreted<?> parent) {
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

		static class Instantiator<T> extends ModelValueElement.Abstract<ObservableValueSet<T>> {
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

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = Timer.TIMER,
		interpretation = Timer.Interpreted.class,
		instance = Timer.Instantiator.class)
	public static class Timer extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<Instant>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<Instant>>> {
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

		public Timer(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("active")
		public CompiledExpression isActive() {
			return isActive;
		}

		@QonfigAttributeGetter("frequency")
		public CompiledExpression getFrequency() {
			return theFrequency;
		}

		@QonfigAttributeGetter("strict-timing")
		public boolean isStrictTiming() {
			return isStrictTiming;
		}

		@QonfigAttributeGetter("background")
		public boolean isBackground() {
			return isBackground;
		}

		@QonfigAttributeGetter("remaining-executions")
		public CompiledExpression getRemainingExecutions() {
			return theRemainingExecutions;
		}

		@QonfigAttributeGetter("until")
		public CompiledExpression getUntil() {
			return theUntil;
		}

		@QonfigAttributeGetter("run-next-in")
		public CompiledExpression getRunNextIn() {
			return theRunNextIn;
		}

		@QonfigAttributeGetter("next-execution")
		public CompiledExpression getNextExecution() {
			return theNextExecution;
		}

		@QonfigAttributeGetter("execution-count")
		public CompiledExpression getExecutionCount() {
			return theExecutionCount;
		}

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

		static class Interpreted extends
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

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isActive() {
				return isActive;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> getFrequency() {
				return theFrequency;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getRemainingExecutions() {
				return theRemainingExecutions;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getUntil() {
				return theUntil;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Duration>> getRunNextIn() {
				return theRunNextIn;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getNextExecution() {
				return theNextExecution;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getExecutionCount() {
				return theExecutionCount;
			}

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

		static class Instantiator extends ModelValueElement.Abstract<SettableValue<Instant>> {
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
			private final ModelValueInstantiator<ObservableAction> theAction;
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
				theAction = interpreted.getElementValue() == null ? null
					: (ModelValueInstantiator<ObservableAction>) (ModelValueInstantiator<?>) interpreted.getElementValue().instantiate();
				theActionReporting = theAction == null ? null
					: interpreted.reporting().at(interpreted.getDefinition().getElementValue().getFilePosition());
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
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
				if (theAction != null)
					theAction.instantiate();
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
				ObservableAction action = theAction == null ? null : theAction.get(models);
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
				ObservableAction action = theAction == null ? null : theAction.forModelCopy(timer.theAction, sourceModels, newModels);

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
				super(SettableValue.build(Instant.class).withDescription("timer").build());
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
