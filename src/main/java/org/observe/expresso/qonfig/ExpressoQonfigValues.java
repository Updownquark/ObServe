package org.observe.expresso.qonfig;

import java.util.ArrayList;
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
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class ExpressoQonfigValues {
	private ExpressoQonfigValues() {
	}

	public static abstract class AbstractCompiledValue<E extends AbstractCompiledValue.Element<?>>
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, E> implements ModelValueElement.CompiledSynth<SettableValue<?>, E> {
		protected AbstractCompiledValue(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		public static abstract class Interpreted<T, E extends Element<T>>
		extends ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<T>, E>
		implements ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, E> {
			protected Interpreted(AbstractCompiledValue<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Interpreted<T, E> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}
		}

		public static abstract class Element<T> extends ModelValueElement.Default<SettableValue<?>, SettableValue<T>> {
			protected Element(ModelValueElement.Interpreted<SettableValue<?>, SettableValue<T>, ?> interpreted, ExElement parent) {
				super(interpreted, parent);
			}
		}

		public static abstract class VoidElement<T> extends Element<T> {
			private VoidElement() {
				super(null, null);
			}
		}
	}

	public static class ConstantValueDef extends AbstractCompiledValue<AbstractCompiledValue.VoidElement<?>> {
		public ConstantValueDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T, AbstractCompiledValue.VoidElement<T>> {
			private ModelInstanceType<SettableValue<?>, SettableValue<T>> theType;

			public Interpreted(ConstantValueDef definition) {
				super(definition, null);
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
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<T> initV = getElementValue().get(models);
				return new ConstantValue<>(getDefinition().getModelPath(), initV.getType(), initV.get());
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return value; // Constants are constant--no need to copy
			}

			@Override
			public void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getValueType() != null)
					theType = ModelTypes.Value.forType((TypeToken<T>) getDefinition().getValueType().getType(getExpressoEnv()));
				else
					theType = getElementValue().getType();
			}
		}

		static class ConstantValue<T> extends AbstractIdentifiable implements SettableValue<T> {
			private final String theModelPath;
			private final TypeToken<T> theType;
			private final T theValue;

			public ConstantValue(String path, TypeToken<T> type, T value2) {
				theModelPath = path;
				theType = type;
				theValue = value2;
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

	public static class SimpleValueDef extends AbstractCompiledValue<SimpleValueDef.Element<?>> {
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
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T, Element<T>> {
			public Interpreted(SimpleValueDef definition) {
				super(definition, null);
			}

			@Override
			public SimpleValueDef getDefinition() {
				return (SimpleValueDef) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getInit() {
				return (InterpretedValueSynth<SettableValue<?>, SettableValue<T>>) getAddOnValue(ExIntValue.Interpreted.class,
					ExIntValue.Interpreted::getInit);
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
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				InterpretedValueSynth<SettableValue<?>, SettableValue<T>> init = getInit();
				if (getElementValue() != null)
					return getElementValue().get(models);
				else {
					TypeToken<T> valueType = (TypeToken<T>) getType().getType(0);
					SettableValue.Builder<T> builder = SettableValue.build(valueType);
					if (getDefinition().getModelPath() != null)
						builder.withDescription(getDefinition().getModelPath());
					if (init != null) {
						SettableValue<T> initV = init.get(models);
						return builder.withValue(initV.get()).build();
					} else {
						return builder.withValue(TypeTokens.get().getDefaultValue(valueType))//
							.build();
					}
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

			@Override
			public Element<T> create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return new Element<>(this, parent);
			}
		}

		public static class Element<T> extends AbstractCompiledValue.Element<T> {
			public Element(SimpleValueDef.Interpreted<T> interpreted, ExElement parent) {
				super(interpreted, parent);
			}
		}
	}

	/** ExElement definition for the Expresso &lt;element>. */
	public static class CollectionElement extends AbstractCompiledValue<AbstractCompiledValue.VoidElement<?>> {
		private static final SingleTypeTraceability<AbstractCompiledValue.VoidElement<?>, Interpreted<?>, CollectionElement> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "element", CollectionElement.class, Interpreted.class,
				null);

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
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session);
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractCompiledValue.Interpreted<T, AbstractCompiledValue.VoidElement<T>> {
			public Interpreted(CollectionElement definition) {
				super(definition, null);
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
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return getElementValue().get(models);
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return value; // This is for initialization only, no need to make copy code
			}

			public boolean populateCollection(BetterCollection<? super T> collection, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<T> elValue = getElementValue().get(models);
				T value = elValue.get();
				String msg = collection.canAdd(value);
				if (msg != null) {
					reporting().at(getDefinition().getElementValue().getFilePosition()).warn(msg);
					return false;
				} else if (!collection.add(value)) {
					reporting().at(getDefinition().getElementValue().getFilePosition()).warn("Value not added for unspecified reason");
					return false;
				} else
					return true;
			}
		}
	}

	public static abstract class AbstractCollectionDef<C extends ObservableCollection<?>>
	extends ModelValueElement.Def.SingleTyped<C, ModelValueElement<C, C>>
	implements ModelValueElement.CompiledSynth<C, ModelValueElement<C, C>> {
		private static final SingleTypeTraceability<ModelValueElement<?, ?>, Interpreted<?, ?>, AbstractCollectionDef<?>> INT_LIST_TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "int-list", AbstractCollectionDef.class,
				Interpreted.class, null);

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
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			if (session.isInstance("int-list") != null) {
				ExpressoQIS intListSession = session.asElement("int-list");
				withTraceability(INT_LIST_TRACEABILITY.validate(intListSession.getFocusType(), session.reporting()));
				ExElement.syncDefs(CollectionElement.class, theElements, intListSession.forChildren("element"));
			}
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			if (!theElements.isEmpty()) {
				List<ExpressoQIS> elementSessions = session.asElement("int-list").forChildren("element");
				int i = 0;
				for (CollectionElement element : theElements)
					element.prepareModelValue(elementSessions.get(i++));
			}
		}

		@Override
		public Interpreted<?, C> interpret() {
			return (Interpreted<?, C>) interpret2();
		}

		protected abstract Interpreted<?, ?> interpret2();

		public static abstract class Interpreted<T, C extends ObservableCollection<T>>
		extends ModelValueElement.Def.SingleTyped.Interpreted<C, C, ModelValueElement<C, C>>
		implements ModelValueElement.InterpretedSynth<C, C, ModelValueElement<C, C>> {
			private final List<CollectionElement.Interpreted<T>> theElements;

			protected Interpreted(AbstractCollectionDef<?> definition) {
				super((AbstractCollectionDef<C>) definition, null);
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
			public C get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				TypeToken<T> type = TypeTokens.get().wrap((TypeToken<T>) getType().getType(0));
				ObservableCollectionBuilder<T, ?> builder = create(type, models);
				if (getDefinition().getModelPath() != null)
					builder.withDescription(getDefinition().getModelPath());
				C collection = (C) builder.build();
				for (CollectionElement.Interpreted<T> element : theElements)
					element.populateCollection(collection, models);
				return collection;
			}

			protected abstract ObservableCollectionBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException;

			@Override
			public C forModelCopy(C value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Configured elements are merely initialized, not slaved, and the collection may have been modified
				// since it was created. There's no sense to making a re-initialized copy here.
				return value;
			}

			@Override
			public Interpreted<T, C> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				CollectionUtils.synchronize(theElements, getDefinition().getElements(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(d -> (CollectionElement.Interpreted<T>) d.interpret(getExpressoEnv()))//
				.onLeft(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().update(getExpressoEnv()))//
				.onCommonX(el -> el.getLeftValue().update(getExpressoEnv()))//
				.adjust();
			}

			@Override
			public ModelValueElement<C, C> create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return new ModelValueElement.Default<>(this, parent);
			}
		}
	}

	public static class PlainCollectionDef extends AbstractCollectionDef<ObservableCollection<?>> {
		public PlainCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Collection);
		}

		@Override
		protected Interpreted<?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableCollection<T>> {
			public Interpreted(PlainCollectionDef definition) {
				super(definition);
			}

			@Override
			protected ObservableCollectionBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException {
				return ObservableCollection.build(type);
			}
		}
	}

	public static abstract class AbstractSortedCollectionDef<C extends ObservableSortedCollection<?>> extends AbstractCollectionDef<C> {
		private ExSort.ExRootSort theSort;

		protected AbstractSortedCollectionDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType,
			ModelType.SingleTyped<C> modelType) {
			super(parent, qonfigType, modelType);
		}

		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSort = ExElement.useOrReplace(ExSort.ExRootSort.class, theSort, session, "sort");
		}

		@Override
		protected abstract Interpreted<?, ?> interpret2();

		public static abstract class Interpreted<T, C extends ObservableSortedCollection<T>>
		extends AbstractCollectionDef.Interpreted<T, C> {
			private ExSort.ExRootSort.Interpreted<T> theSort;

			protected Interpreted(AbstractSortedCollectionDef<?> definition) {
				super(definition);
			}

			@Override
			public AbstractSortedCollectionDef<C> getDefinition() {
				return (AbstractSortedCollectionDef<C>) super.getDefinition();
			}

			@Override
			public void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getDefinition() != getDefinition().getSort())
					theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
				if (theSort != null) {
					TypeToken<T> valueType = (TypeToken<T>) getType().getType(0);
					theSort.update(valueType, getExpressoEnv());
				}
			}

			@Override
			protected ObservableCollectionBuilder.SortedBuilder<T, ?> create(TypeToken<T> type, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super T> sort = theSort.getSorting(models);
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
		protected Interpreted<?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedCollection<T>> {
			public Interpreted(AbstractSortedCollectionDef<?> definition) {
				super(definition);
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
		protected Interpreted<?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractCollectionDef.Interpreted<T, ObservableSet<T>> {
			public Interpreted(SetDef definition) {
				super(definition);
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
		protected Interpreted<?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends AbstractSortedCollectionDef.Interpreted<T, ObservableSortedSet<T>> {
			public Interpreted(SortedSetDef definition) {
				super(definition);
			}

			@Override
			protected SortedBuilder<T, ?> create(TypeToken<T> type, Comparator<? super T> sort, ModelSetInstance models) {
				return ObservableCollection.build(type).sortBy(sort);
			}
		}
	}

	/** ExElement definition for the Expresso &lt;entry>. */
	public static class MapEntry extends ExElement.Def.Abstract<ExElement> {
		private static final SingleTypeTraceability<ExElement, Interpreted<?, ?>, MapEntry> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "entry", MapEntry.class, Interpreted.class, null);

		private CompiledExpression theKey;
		private CompiledExpression theValue;

		public MapEntry(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
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
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session);
		}

		public Interpreted<?, ?> interpret(ModelValueElement.Interpreted<?, ?, ?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<K, V> extends ExElement.Interpreted.Abstract<ExElement> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<K>> theKey;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<V>> theValue;

			public Interpreted(MapEntry definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapEntry getDefinition() {
				return (MapEntry) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<K>> getKey() {
				return theKey;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<V>> getElementValue() {
				return theValue;
			}

			public void update(InterpretedExpressoEnv env, TypeToken<K> keyType, TypeToken<V> valueType)
				throws ExpressoInterpretationException {
				super.update(env);
				theKey = getDefinition().getKey().interpret(ModelTypes.Value.forType(keyType), env);
				theValue = getDefinition().getElementValue().interpret(ModelTypes.Value.forType(valueType), env);
			}

			public boolean populateMap(BetterMap<? super K, ? super V> map, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<K> keyValue = theKey.get(models);
				SettableValue<V> vValue = theValue.get(models);
				K key = keyValue.get();
				V value = vValue.get();
				String msg = map.canPut(key, value);
				if (msg != null) {
					reporting().at(getDefinition().getElementValue().getFilePosition()).warn(msg);
					return false;
				}
				map.put(key, value);
				return true;
			}

			public boolean populateMultiMap(BetterMultiMap<? super K, ? super V> map, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<K> keyValue = theKey.get(models);
				SettableValue<V> vValue = theValue.get(models);
				K key = keyValue.get();
				V value = vValue.get();
				BetterCollection<? super V> values = map.get(key);
				String msg = values.canAdd(value);
				if (msg != null) {
					reporting().at(getDefinition().getElementValue().getFilePosition()).warn(msg);
					return false;
				} else if (!values.add(value)) {
					reporting().at(getDefinition().getElementValue().getFilePosition()).warn("Value not added for unspecified reason");
					return false;
				} else
					return true;
			}
		}
	}

	public static abstract class AbstractMapDef<M extends ObservableMap<?, ?>>
	extends ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M, M>>
	implements ModelValueElement.CompiledSynth<M, ModelValueElement<M, M>> {
		private static final SingleTypeTraceability<ModelValueElement<?, ?>, Interpreted<?, ?, ?>, AbstractMapDef<?>> INT_MAP_TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "int-map", AbstractMapDef.class, Interpreted.class,
				null);
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
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			if (session.isInstance("int-map") != null) {
				ExpressoQIS intMapSession = session.asElement("int-map");
				withTraceability(INT_MAP_TRACEABILITY.validate(intMapSession.getFocusType(), session.reporting()));
				ExElement.syncDefs(MapEntry.class, theEntries, intMapSession.forChildren("entry"));
			}
		}

		@Override
		protected void doPrepare(ExpressoQIS session) {
		}

		@Override
		public Interpreted<?, ?, M> interpret() {
			return (Interpreted<?, ?, M>) interpret2();
		}

		protected abstract Interpreted<?, ?, ?> interpret2();

		public static abstract class Interpreted<K, V, M extends ObservableMap<K, V>>
		extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M, M>>
		implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M, M>> {
			private final List<MapEntry.Interpreted<K, V>> theEntries;

			protected Interpreted(AbstractMapDef<?> definition) {
				super((AbstractMapDef<M>) definition, null);
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

			@Override
			public M get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				TypeToken<K> keyType = TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
				TypeToken<V> valueType = TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
				ObservableMap.Builder<K, V, ?> builder = create(keyType, valueType, models);
				if (getDefinition().getModelPath() != null)
					builder.withDescription(getDefinition().getModelPath());
				M map = (M) builder.build();
				for (MapEntry.Interpreted<K, V> element : theEntries)
					element.populateMap(map, models);
				return map;
			}

			protected abstract ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException;

			@Override
			public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Configured entries are merely initialized, not slaved, and the map may have been modified
				// since it was created. There's no sense to making a re-initialized copy here.
				return value;
			}

			@Override
			public Interpreted<K, V, M> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				ModelInstanceType<M, M> type = getType();
				TypeToken<K> keyType = (TypeToken<K>) type.getType(0);
				TypeToken<V> valueType = (TypeToken<V>) type.getType(1);
				CollectionUtils.synchronize(theEntries, getDefinition().getEntries(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(d -> (MapEntry.Interpreted<K, V>) d.interpret(this))//
				.onLeft(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().update(getExpressoEnv(), keyType, valueType))//
				.onCommonX(el -> el.getLeftValue().update(getExpressoEnv(), keyType, valueType))//
				.adjust();
			}

			@Override
			public ModelValueElement<M, M> create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return new ModelValueElement.Default<>(this, parent);
			}
		}
	}

	public static class PlainMapDef extends AbstractMapDef<ObservableMap<?, ?>> {
		public PlainMapDef(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Map);
		}

		@Override
		protected Interpreted<?, ?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<K, V> extends AbstractMapDef.Interpreted<K, V, ObservableMap<K, V>> {
			public Interpreted(PlainMapDef definition) {
				super(definition);
			}

			@Override
			protected ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				return ObservableMap.build(keyType, valueType);
			}
		}
	}

	public static class SortedMapDef<M extends ObservableSortedMap<?, ?>> extends AbstractMapDef<M> {
		private ExSort.ExRootSort theSort;

		public SortedMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, (ModelType.DoubleTyped<M>) ModelTypes.SortedMap);
		}

		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theSort = ExElement.useOrReplace(ExSort.ExRootSort.class, theSort, session, "sort");
		}

		@Override
		protected Interpreted<?, ?, ?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<K, V, M extends ObservableSortedMap<K, V>> extends AbstractMapDef.Interpreted<K, V, M> {
			private ExSort.ExRootSort.Interpreted<K> theSort;

			public Interpreted(SortedMapDef definition) {
				super(definition);
			}

			@Override
			public SortedMapDef<M> getDefinition() {
				return (SortedMapDef<M>) super.getDefinition();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getDefinition() != getDefinition().getSort())
					theSort = (ExSort.ExRootSort.Interpreted<K>) getDefinition().getSort().interpret(this);
				if (theSort != null) {
					TypeToken<K> keyType = (TypeToken<K>) getType().getType(0);
					theSort.update(keyType, getExpressoEnv());
				}
			}

			@Override
			protected ObservableMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super K> sort = theSort.getSorting(models);
				return ObservableSortedMap.build(keyType, valueType, sort);
			}
		}
	}

	public static abstract class AbstractMultiMapDef<M extends ObservableMultiMap<?, ?>>
	extends ModelValueElement.Def.DoubleTyped<M, ModelValueElement<M, M>>
	implements ModelValueElement.CompiledSynth<M, ModelValueElement<M, M>> {
		private final List<MapEntry> theEntries;

		protected AbstractMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
			theEntries = new ArrayList<>();
		}

		public List<MapEntry> getEntries() {
			return Collections.unmodifiableList(theEntries);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			if (session.isInstance("int-map") != null)
				ExElement.syncDefs(MapEntry.class, theEntries, session.asElement("int-map").forChildren("entry"));
		}

		@Override
		protected void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?, ?, M> interpret() {
			return interpret();
		}

		protected abstract Interpreted<?, ?, ?> interpret2();

		public static abstract class Interpreted<K, V, M extends ObservableMultiMap<K, V>>
		extends ModelValueElement.Def.DoubleTyped.Interpreted<M, M, ModelValueElement<M, M>>
		implements ModelValueElement.InterpretedSynth<M, M, ModelValueElement<M, M>> {
			private final List<MapEntry.Interpreted<K, V>> theEntries;

			protected Interpreted(AbstractMultiMapDef<?> definition) {
				super((AbstractMultiMapDef<M>) definition, null);
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
			public M get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				TypeToken<K> keyType = TypeTokens.get().wrap((TypeToken<K>) getType().getType(0));
				TypeToken<V> valueType = TypeTokens.get().wrap((TypeToken<V>) getType().getType(1));
				ObservableMultiMap.Builder<K, V, ?> builder = create(keyType, valueType, models);
				if (getDefinition().getModelPath() != null)
					builder.withDescription(getDefinition().getModelPath());
				M map = (M) builder.build(models.getUntil());
				for (MapEntry.Interpreted<K, V> element : theEntries)
					element.populateMultiMap(map, models);
				return map;
			}

			protected abstract ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType,
				ModelSetInstance models) throws ModelInstantiationException;

			@Override
			public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Configured entries are merely initialized, not slaved, and the map may have been modified
				// since it was created. There's no sense to making a re-initialized copy here.
				return value;
			}

			@Override
			public Interpreted<K, V, M> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				ModelInstanceType<M, M> type = getType();
				TypeToken<K> keyType = (TypeToken<K>) type.getType(0);
				TypeToken<V> valueType = (TypeToken<V>) type.getType(1);
				CollectionUtils.synchronize(theEntries, getDefinition().getEntries(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(d -> (MapEntry.Interpreted<K, V>) d.interpret(this))//
				.onLeft(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().update(getExpressoEnv(), keyType, valueType))//
				.onCommonX(el -> el.getLeftValue().update(getExpressoEnv(), keyType, valueType))//
				.adjust();
			}

			@Override
			public ModelValueElement<M, M> create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return new ModelValueElement.Default<>(this, parent);
			}
		}
	}

	public static class PlainMultiMapDef extends AbstractMultiMapDef<ObservableMultiMap<?, ?>> {
		public PlainMultiMapDef(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.MultiMap);
		}

		@Override
		protected Interpreted<?, ?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<K, V> extends AbstractMultiMapDef.Interpreted<K, V, ObservableMultiMap<K, V>> {
			public Interpreted(PlainMultiMapDef definition) {
				super(definition);
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
			theSort = ExElement.useOrReplace(ExSort.ExRootSort.class, theSort, session, "sort");
		}

		@Override
		protected Interpreted<?, ?, ?> interpret2() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<K, V, M extends ObservableSortedMultiMap<K, V>> extends AbstractMultiMapDef.Interpreted<K, V, M> {
			private ExSort.ExRootSort.Interpreted<K> theSort;

			public Interpreted(SortedMultiMapDef definition) {
				super(definition);
			}

			@Override
			public SortedMultiMapDef<M> getDefinition() {
				return (SortedMultiMapDef<M>) super.getDefinition();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getDefinition() != getDefinition().getSort())
					theSort = (ExSort.ExRootSort.Interpreted<K>) getDefinition().getSort().interpret(this);
				if (theSort != null) {
					TypeToken<K> keyType = (TypeToken<K>) getType().getType(0);
					theSort.update(keyType, getExpressoEnv());
				}
			}

			@Override
			protected ObservableMultiMap.Builder<K, V, ?> create(TypeToken<K> keyType, TypeToken<V> valueType, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super K> sort = theSort.getSorting(models);
				return ObservableMultiMap.build(keyType, valueType).sortedBy(sort);
			}
		}
	}

	public static class FirstValue extends ModelValueElement.Def.Abstract<SettableValue<?>, ModelValueElement<SettableValue<?>, ?>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ?>> {
		private final List<ModelValueElement.CompiledSynth<SettableValue<?>, ?>> theValues;

		public FirstValue(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theValues = new ArrayList<>();
		}

		public VariableType getValueType() {
			return getAddOnValue(ExTyped.Def.class, ExTyped.Def::getValueType);
		}

		public List<ModelValueElement.CompiledSynth<SettableValue<?>, ?>> getValues() {
			return Collections.unmodifiableList(theValues);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			ExElement.syncDefs(ModelValueElement.Def.class, theValues, session.forChildren("value"));
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			List<ExpressoQIS> valueSessions = session.forChildren("value");
			int i = 0;
			for (ModelValueElement.Def<?, ?> value : theValues) {
				value.prepareModelValue(valueSessions.get(i++));
				if (value.getModelType(getExpressoEnv()) != ModelTypes.Value)
					throw new QonfigInterpretationException(
						"Only value-type components are allowed for a <" + getElement().getType().getName() + "> element",
						value.reporting().getFileLocation().getPosition(0), 0);
			}
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends
		ModelValueElement.Interpreted.Abstract<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<?>, SettableValue<T>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ModelValueElement<SettableValue<?>, SettableValue<T>>> {
			private TypeToken<T> theCommonType;
			private final List<ModelValueElement.InterpretedSynth<SettableValue<?>, ? extends SettableValue<? extends T>, ?>> theValues;

			public Interpreted(FirstValue definition) {
				super(definition, null);
				theValues = new ArrayList<>();
			}

			@Override
			public FirstValue getDefinition() {
				return (FirstValue) super.getDefinition();
			}

			public List<ModelValueElement.InterpretedSynth<SettableValue<?>, ? extends SettableValue<? extends T>, ?>> getValues() {
				return Collections.unmodifiableList(theValues);
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				CollectionUtils.synchronize(theValues, getDefinition().getValues(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(
					d -> (ModelValueElement.InterpretedSynth<SettableValue<?>, ? extends SettableValue<? extends T>, ?>) d
					.interpret(getExpressoEnv()))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onCommonX(el -> el.getLeftValue().updateValue(getExpressoEnv()))//
				.adjust();

				VariableType defType = getDefinition().getValueType();
				if (defType != null)
					theCommonType = (TypeToken<T>) defType.getType(getExpressoEnv());
				else {
					TypeToken<? extends T>[] types = new TypeToken[theValues.size()];
					for (int i = 0; i < types.length; i++)
						types[i] = (TypeToken<? extends T>) theValues.get(i).getType().getType(0);
					theCommonType = TypeTokens.get().getCommonType(types);
				}
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
				return ModelTypes.Value.forType(theCommonType); // Not actually used, since getType() is overridden
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return ModelTypes.Value.forType(theCommonType);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return getValues();
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<? extends T>[] vs = new SettableValue[theValues.size()];
				for (int i = 0; i < vs.length; i++)
					vs[i] = theValues.get(i).get(models);
				return new FirstValueValue<>(theCommonType, vs);
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				FirstValueValue<T> fsv = (FirstValueValue<T>) value;
				SettableValue<? extends T>[] vs = new SettableValue[theValues.size()];
				boolean different = false;
				for (int i = 0; i < vs.length; i++) {
					SettableValue<? extends T> sv = fsv.getValues().get(i);
					SettableValue<? extends T> nv = ((ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, ?>) theValues
						.get(i)).forModelCopy((SettableValue<T>) sv, sourceModels, newModels);
					different |= sv != nv;
					vs[i] = nv;
				}
				if (different)
					return new FirstValueValue<>(theCommonType, vs);
				else
					return value;
			}

		}

		static class FirstValueValue<T> extends SettableValue.FirstSettableValue<T> {
			FirstValueValue(TypeToken<T> type, SettableValue<? extends T>[] values) {
				super(type, values, //
					LambdaUtils.printablePred(v -> v != null, "notNull", null), //
					LambdaUtils.constantSupplier(null, "null", null));
			}

			@Override
			public List<? extends SettableValue<? extends T>> getValues() {
				return super.getValues();
			}
		}
	}

	public static class Hook extends ExElement.Def.Abstract<ModelValueElement<Observable<?>, ?>>
	implements ModelValueElement.CompiledSynth<Observable<?>, ModelValueElement<Observable<?>, ?>> {
		private static SingleTypeTraceability<ModelValueElement<Observable<?>, ?>, Interpreted<?>, Hook> TRACEABILITY = ElementTypeTraceability//
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "hook", Hook.class, Interpreted.class, null);

		private String theModelPath;
		private CompiledExpression theEvent;
		private CompiledExpression theAction;

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

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			withTraceability(ModelValueElement.TRACEABILITY.validate(session.getFocusType().getSuperElement(), session.reporting()));
			super.doUpdate(session);
			theModelPath = session.get(ExpressoBaseV0_1.PATH_KEY, String.class);
			theEvent = session.getAttributeExpression("on");
			theAction = session.getValueExpression();
			getAddOn(ExWithElementModel.Def.class).<Interpreted<?>, SettableValue<?>> satisfyElementValueType("event", ModelTypes.Value,
				(interp, env) -> {
					return ModelTypes.Value.forType(interp.getOrEvalEventType(env));
				});
		}

		@Override
		public void prepareModelValue(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		public static class Interpreted<T> extends ExElement.Interpreted.Abstract<ModelValueElement<Observable<?>, Observable<T>>>
		implements ModelValueElement.InterpretedSynth<Observable<?>, Observable<T>, ModelValueElement<Observable<?>, Observable<T>>> {
			private TypeToken<T> theEventType;
			private InterpretedValueSynth<Observable<?>, Observable<T>> theEvent;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAction;

			public Interpreted(Hook definition) {
				super(definition, null);
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
						theEvent = getDefinition().getEvent() == null ? null
							: getDefinition().getEvent().interpret(ModelTypes.Event.forType(theEventType), getExpressoEnv());
					} else if (getDefinition().getEvent() != null) {
						theEvent = getDefinition().getEvent().interpret(ModelTypes.Event.<Observable<T>> anyAs(), getExpressoEnv());
						theEventType = (TypeToken<T>) theEvent.getType().getType(0);
					} else {
						theEventType = (TypeToken<T>) TypeTokens.get().VOID;
						theEvent = null;
					}
				}
				return theEventType;
			}

			public InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> getAction() {
				return theAction;
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				getOrEvalEventType(getExpressoEnv());
				theAction = getDefinition().getAction().interpret(ModelTypes.Action.any(), getExpressoEnv());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(theEvent, theAction);
			}

			@Override
			public ModelInstanceType<Observable<?>, Observable<T>> getType() {
				return ModelTypes.Event.forType(theEventType);
			}

			@Override
			public Observable<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = getExpressoEnv().wrapLocal(models);
				Observable<T> on = theEvent == null ? null : theEvent.get(models);
				ObservableAction<?> action = theAction.get(models);
				SettableValue<T> event = SettableValue.build(theEventType)//
					.withValue(TypeTokens.get().getDefaultValue(theEventType)).build();
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue("event", models, event);
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
				// TODO Can do this better some day
				return get(newModels);
			}

			@Override
			public ModelValueElement<Observable<?>, Observable<T>> create(ExElement parent, ModelSetInstance models)
				throws ModelInstantiationException {
				return null;
			}
		}
	}

	public static class Action extends ModelValueElement.Def.Abstract<ObservableAction<?>, ModelValueElement<ObservableAction<?>, ?>>
	implements ModelValueElement.CompiledSynth<ObservableAction<?>, ModelValueElement<ObservableAction<?>, ?>> {
		private CompiledExpression theAction;

		public Action(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
		}

		public CompiledExpression getAction() {
			return theAction;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theAction = session.getValueExpression();
		}

		@Override
		public void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public Interpreted<?> interpret() {
			return new Interpreted<>(this);
		}

		protected static class Interpreted<T> extends
		ModelValueElement.Interpreted.Abstract<ObservableAction<?>, ObservableAction<T>, ModelValueElement<ObservableAction<?>, ObservableAction<T>>>
		implements
		ModelValueElement.InterpretedSynth<ObservableAction<?>, ObservableAction<T>, ModelValueElement<ObservableAction<?>, ObservableAction<T>>> {
			public Interpreted(Action definition) {
				super(definition, null);
			}

			@Override
			public Action getDefinition() {
				return (Action) super.getDefinition();
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				return (Interpreted<T>) super.setParentElement(parent);
			}

			@Override
			protected ModelInstanceType<ObservableAction<?>, ObservableAction<T>> getTargetType() {
				return ModelTypes.Action.<ObservableAction<T>> anyAs();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(getElementValue());
			}

			@Override
			public ObservableAction<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				return getElementValue().get(models);
			}

			@Override
			public ObservableAction<T> forModelCopy(ObservableAction<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return getElementValue().forModelCopy(value, sourceModels, newModels);
			}
		}
	}

	public static class ActionGroup
	extends ModelValueElement.Def.Abstract<ObservableAction<?>, ModelValueElement<ObservableAction<?>, ObservableAction<Void>>>
	implements ModelValueElement.CompiledSynth<ObservableAction<?>, ModelValueElement<ObservableAction<?>, ObservableAction<Void>>> {
		private static final SingleTypeTraceability<ModelValueElement<?, ?>, Interpreted, ActionGroup> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "action-group", ActionGroup.class, Interpreted.class,
				null);
		private final List<ModelValueElement.CompiledSynth<ObservableAction<?>, ?>> theActions;

		public ActionGroup(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
			theActions = new ArrayList<>();
		}

		@QonfigChildGetter("action")
		public List<ModelValueElement.CompiledSynth<ObservableAction<?>, ?>> getActions() {
			return Collections.unmodifiableList(theActions);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session);
			ExElement.syncDefs(ModelValueElement.CompiledSynth.class, theActions, session.forChildren("action"));
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			List<ExpressoQIS> actionSessions = session.forChildren("action");
			int i = 0;
			for (ModelValueElement.CompiledSynth<ObservableAction<?>, ?> action : theActions)
				action.prepareModelValue(actionSessions.get(i++));
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Interpreted.Abstract<ObservableAction<?>, ObservableAction<Void>, ModelValueElement<ObservableAction<?>, ObservableAction<Void>>>
		implements
		ModelValueElement.InterpretedSynth<ObservableAction<?>, ObservableAction<Void>, ModelValueElement<ObservableAction<?>, ObservableAction<Void>>> {
			private final List<Action.Interpreted<?>> theActions;

			public Interpreted(ActionGroup definition) {
				super(definition, null);
				theActions = new ArrayList<>();
			}

			@Override
			public ActionGroup getDefinition() {
				return (ActionGroup) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				return (Interpreted) super.setParentElement(parent);
			}

			@Override
			protected ModelInstanceType<ObservableAction<?>, ObservableAction<Void>> getTargetType() {
				return ModelTypes.Action.VOID; // Not actually used, since getType() is overridden
			}

			@Override
			public ModelInstanceType<ObservableAction<?>, ObservableAction<Void>> getType() {
				return ModelTypes.Action.VOID;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				CollectionUtils.synchronize(theActions, getDefinition().getActions(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.simpleE(d -> (Action.Interpreted<?>) d.interpret(getExpressoEnv()))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onCommonX(el -> el.getLeftValue().setParentElement(this).update(getExpressoEnv()))//
				.adjust();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.unmodifiableList(theActions);
			}

			@Override
			public ObservableAction<Void> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				ObservableAction<?>[] actions = new ObservableAction[theActions.size()];
				for (int i = 0; i < actions.length; i++)
					actions[i] = theActions.get(i).get(models);
				return new GroupAction(actions);
			}

			@Override
			public ObservableAction<Void> forModelCopy(ObservableAction<Void> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				GroupAction action = (GroupAction) value;
				ObservableAction<?>[] actionCopies = new ObservableAction[action.getActions().length];
				boolean different = false;
				for (int i = 0; i < theActions.size(); i++) {
					actionCopies[i] = ((Action.Interpreted<Object>) theActions.get(i))
						.forModelCopy((ObservableAction<Object>) action.getActions()[i], sourceModels, newModels);
					if (actionCopies[i] != action.getActions()[i])
						different = true;
				}
				if (different)
					return new GroupAction(actionCopies);
				else
					return value;
			}
		}

		static class GroupAction implements ObservableAction<Void> {
			private final ObservableAction<?>[] theActions;
			private final ObservableValue<String> theEnabled;

			GroupAction(ObservableAction<?>[] actions) {
				theActions = actions;
				ObservableValue<String>[] actionsEnabled = new ObservableValue[actions.length];
				for (int i = 0; i < actions.length; i++)
					actionsEnabled[i] = actions[i].isEnabled();
				theEnabled = ObservableValue.firstValue(TypeTokens.get().STRING, v -> v != null, null, actionsEnabled);
			}

			ObservableAction<?>[] getActions() {
				return theActions;
			}

			@Override
			public TypeToken<Void> getType() {
				return TypeTokens.get().VOID;
			}

			@Override
			public Void act(Object cause) throws IllegalStateException {
				// Don't do any actions if any are disabled
				String msg = theEnabled.get();
				if (msg != null)
					throw new IllegalStateException(msg);
				for (ObservableAction<?> action : theActions)
					action.act(cause);
				return null;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return theEnabled;
			}
		}
	}

	public static class Loop extends ModelValueElement.Def.Abstract<ObservableAction<?>, ModelValueElement<ObservableAction<?>, ?>>
	implements ModelValueElement.CompiledSynth<ObservableAction<?>, ModelValueElement<ObservableAction<?>, ?>> {
		private CompiledExpression theInit;
		private CompiledExpression theBefore;
		private CompiledExpression theWhile;
		private CompiledExpression theBeforeBody;
		private CompiledExpression theAfterBody;
		private CompiledExpression theFinally;
		private final List<ModelValueElement.CompiledSynth<ObservableAction<?>, ?>> theBody;

		public Loop(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Action);
			theBody = new ArrayList<>();
		}

		public CompiledExpression getInit() {
			return theInit;
		}

		public CompiledExpression getBefore() {
			return theBefore;
		}

		public CompiledExpression getWhile() {
			return theWhile;
		}

		public CompiledExpression getBeforeBody() {
			return theBeforeBody;
		}

		public CompiledExpression getAfterBody() {
			return theAfterBody;
		}

		public CompiledExpression getFinally() {
			return theFinally;
		}

		public List<ModelValueElement.CompiledSynth<ObservableAction<?>, ?>> getBody() {
			return Collections.unmodifiableList(theBody);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theInit = session.getAttributeExpression("init");
			theBefore = session.getAttributeExpression("before-while");
			theWhile = session.getAttributeExpression("while");
			theBeforeBody = session.getAttributeExpression("before-body");
			theAfterBody = session.getAttributeExpression("after-body");
			theFinally = session.getAttributeExpression("finally");
			ExElement.syncDefs(ModelValueElement.CompiledSynth.class, theBody, session.forChildren("body"));
		}

		@Override
		public void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			List<ExpressoQIS> bodySessions = session.forChildren("body");
			int i = 0;
			for (CompiledModelValue<ObservableAction<?>> body : theBody) {
				if (body instanceof ModelValueElement.Def)
					((ModelValueElement.Def<?, ?>) body).prepareModelValue(bodySessions.get(i++));
			}
		}

		@Override
		public Interpreted interpret() {
			return new Interpreted(this);
		}

		static class Interpreted extends
		ModelValueElement.Interpreted.Abstract<ObservableAction<?>, ObservableAction<?>, ModelValueElement<ObservableAction<?>, ObservableAction<?>>>
		implements
		ModelValueElement.InterpretedSynth<ObservableAction<?>, ObservableAction<?>, ModelValueElement<ObservableAction<?>, ObservableAction<?>>> {
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theInit;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theBefore;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theWhile;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theBeforeBody;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theAfterBody;
			private InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>> theFinally;
			private final List<InterpretedValueSynth<ObservableAction<?>, ?>> theBody;

			Interpreted(Loop definition) {
				super(definition, null);
				theBody = new ArrayList<>();
			}

			@Override
			public Loop getDefinition() {
				return (Loop) super.getDefinition();
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				return (Interpreted) super.setParentElement(parent);
			}

			@Override
			protected ModelInstanceType<ObservableAction<?>, ObservableAction<?>> getTargetType() {
				return getType(); // Not actually used, since getType() is overridden
			}

			@Override
			public ModelInstanceType<ObservableAction<?>, ObservableAction<?>> getType() {
				if (theBody.isEmpty())
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) (ModelInstanceType<?, ?>) ModelTypes.Action.VOID;
				else
					return (ModelInstanceType<ObservableAction<?>, ObservableAction<?>>) theBody.get(theBody.size() - 1).getType();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theInit = getDefinition().getInit() == null ? null
					: getDefinition().getInit().interpret(ModelTypes.Action.any(), getExpressoEnv());
				theBefore = getDefinition().getBefore() == null ? null
					: getDefinition().getBefore().interpret(ModelTypes.Action.any(), getExpressoEnv());
				theWhile = getDefinition().getWhile().interpret(ModelTypes.Value.forType(boolean.class), getExpressoEnv());
				theBeforeBody = getDefinition().getBeforeBody() == null ? null
					: getDefinition().getBeforeBody().interpret(ModelTypes.Action.any(), getExpressoEnv());
				theAfterBody = getDefinition().getAfterBody() == null ? null
					: getDefinition().getAfterBody().interpret(ModelTypes.Action.any(), getExpressoEnv());
				theFinally = getDefinition().getFinally() == null ? null
					: getDefinition().getFinally().interpret(ModelTypes.Action.any(), getExpressoEnv());
				theBody.clear();
				for (ModelValueElement.CompiledSynth<ObservableAction<?>, ?> body : getDefinition().getBody())
					theBody.add(body.interpret(getExpressoEnv()));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.<InterpretedValueSynth<?, ?>> concat(//
					Stream.of(theInit, theBefore, theWhile, theBeforeBody, theAfterBody, theFinally), //
					theBody.stream()).filter(Objects::nonNull));
			}

			@Override
			public ObservableAction<Object> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = getExpressoEnv().wrapLocal(models);
				ObservableAction<?> init = theInit == null ? null : theInit.get(models);
				ObservableAction<?> before = theBefore == null ? null : theBefore.get(models);
				SettableValue<Boolean> condition = theWhile.get(models);
				ObservableAction<?> beforeBody = theBeforeBody == null ? null : theBeforeBody.get(models);
				List<ObservableAction<?>> body = new ArrayList<>(theBody.size());
				for (InterpretedValueSynth<ObservableAction<?>, ?> b : theBody)
					body.add(b.get(models));
				ObservableAction<?> afterBody = theAfterBody == null ? null : theAfterBody.get(models);
				ObservableAction<?> last = theFinally == null ? null : theFinally.get(models);
				return new LoopAction(init, before, condition, beforeBody, Collections.unmodifiableList(body), afterBody, last);
			}

			@Override
			public ObservableAction<?> forModelCopy(ObservableAction<?> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				LoopAction loop = (LoopAction) value;
				ObservableAction<?> initS = loop.getInit();
				ObservableAction<?> initA = theInit == null ? null : theInit.forModelCopy(initS, sourceModels, newModels);
				ObservableAction<?> beforeS = loop.getBeforeCondition();
				ObservableAction<?> beforeA = theBefore == null ? null : theBefore.forModelCopy(beforeS, sourceModels, newModels);
				SettableValue<Boolean> whileS = (SettableValue<Boolean>) loop.getCondition();
				SettableValue<Boolean> whileA = theWhile.forModelCopy(whileS, sourceModels, newModels);
				ObservableAction<?> beforeBodyS = loop.getBeforeBody();
				ObservableAction<?> beforeBodyA = theBeforeBody == null ? null
					: theBeforeBody.forModelCopy(beforeBodyS, sourceModels, newModels);
				boolean different = initS != initA || beforeS != beforeA || whileS != whileA || beforeBodyS != beforeBodyA;
				List<ObservableAction<?>> execAs = new ArrayList<>(theBody.size());
				for (int i = 0; i < theBody.size(); i++) {
					ObservableAction<?> bodyS = loop.getBody().get(i);
					ObservableAction<?> bodyA = ((InterpretedValueSynth<ObservableAction<?>, ObservableAction<?>>) theBody.get(i))
						.forModelCopy(bodyS, sourceModels, newModels);
					different |= bodyS != bodyA;
				}
				ObservableAction<?> afterBodyS = loop.getAfterBody();
				ObservableAction<?> afterBodyA = theAfterBody == null ? null
					: theAfterBody.forModelCopy(afterBodyS, sourceModels, newModels);
				ObservableAction<?> finallyS = loop.getFinally();
				ObservableAction<?> finallyA = theFinally == null ? null : theFinally.forModelCopy(finallyS, sourceModels, newModels);
				different |= afterBodyS != afterBodyA || finallyS != finallyA;
				if (different)
					return new LoopAction(initA, beforeA, whileA, beforeBodyA, execAs, afterBodyA, finallyA);
				else
					return value;
			}
		}

		static class LoopAction implements ObservableAction<Object> {
			private final ObservableAction<?> theInit;
			private final ObservableAction<?> theBeforeCondition;
			private final ObservableValue<Boolean> theCondition;
			private final ObservableAction<?> theBeforeBody;
			private final List<ObservableAction<?>> theBody;
			private final ObservableAction<?> theAfterBody;
			private final ObservableAction<?> theFinally;

			public LoopAction(ObservableAction<?> init, ObservableAction<?> before, ObservableValue<Boolean> condition,
				ObservableAction<?> beforeBody, List<ObservableAction<?>> body, ObservableAction<?> afterBody,
				ObservableAction<?> finallly) {
				theInit = init;
				theBeforeCondition = before;
				theCondition = condition;
				theBeforeBody = beforeBody;
				theBody = body;
				theAfterBody = afterBody;
				theFinally = finallly;
			}

			public ObservableAction<?> getInit() {
				return theInit;
			}

			public ObservableAction<?> getBeforeCondition() {
				return theBeforeCondition;
			}

			public ObservableValue<Boolean> getCondition() {
				return theCondition;
			}

			public ObservableAction<?> getBeforeBody() {
				return theBeforeBody;
			}

			public List<ObservableAction<?>> getBody() {
				return theBody;
			}

			public ObservableAction<?> getAfterBody() {
				return theAfterBody;
			}

			public ObservableAction<?> getFinally() {
				return theFinally;
			}

			@Override
			public TypeToken<Object> getType() {
				return (TypeToken<Object>) (TypeToken<?>) (theBody.isEmpty() ? TypeTokens.get().VOID
					: theBody.get(theBody.size() - 1).getType());
			}

			@Override
			public ObservableValue<String> isEnabled() {
				// TODO This isn't right, but it seems hard to figure out, so leaving this for now
				return SettableValue.ALWAYS_ENABLED;
			}

			@Override
			public Object act(Object cause) throws IllegalStateException {
				try (Causable.CausableInUse cause2 = Causable.cause(cause)) {
					if (theInit != null)
						theInit.act(cause2);

					try {
						Object result = null;
						// Prevent infinite loops. This structure isn't terribly efficient, so I think this should be sufficient.
						int count = 0;
						while (count < 1_000_000) {
							if (theBeforeCondition != null)
								theBeforeCondition.act(cause2);
							if (!Boolean.TRUE.equals(theCondition.get()))
								break;
							if (theBeforeBody != null)
								theBeforeBody.act(cause2);
							for (ObservableAction<?> body : theBody)
								result = body.act(cause2);
							if (theAfterBody != null)
								theAfterBody.act(cause2);
						}
						return result;
					} finally {
						if (theFinally != null)
							theFinally.act(cause2);
					}
				}
			}
		}
	}

	static class ValueSet extends ModelValueElement.Def.SingleTyped<ObservableValueSet<?>, ModelValueElement<ObservableValueSet<?>, ?>>
	implements ModelValueElement.CompiledSynth<ObservableValueSet<?>, ModelValueElement<ObservableValueSet<?>, ?>> {
		public ValueSet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.ValueSet);
		}

		@Override
		public void doPrepare(ExpressoQIS session) { // Nothing to do
		}

		@Override
		public InterpretedSynth<ObservableValueSet<?>, ?, ? extends ModelValueElement<ObservableValueSet<?>, ?>> interpret() {
			return new Interpreted<>(this);
		}

		static class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<?>, ObservableValueSet<T>>>
		implements
		ModelValueElement.InterpretedSynth<ObservableValueSet<?>, ObservableValueSet<T>, ModelValueElement<ObservableValueSet<?>, ObservableValueSet<T>>> {
			public Interpreted(ValueSet definition) {
				super(definition, null);
			}

			@Override
			public ValueSet getDefinition() {
				return (ValueSet) super.getDefinition();
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ObservableValueSet<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				// Although a purely in-memory value set would be more efficient, I have yet to implement one.
				// Easiest path forward for this right now is to make an unpersisted ObservableConfig and use it to back the value set.
				// TODO At some point I should come back and make an in-memory implementation and use it here.
				ObservableConfig config = ObservableConfig.createRoot("root", null,
					__ -> new FastFailLockingStrategy(ThreadConstraint.ANY));
				return config.asValue((TypeToken<T>) getType().getType(0)).buildEntitySet(null);
			}

			@Override
			public ObservableValueSet<T> forModelCopy(ObservableValueSet<T> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return value;
			}
		}
	}
}
