package org.observe.collect;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.XformOptions;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractMappingManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionOperation;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionDataFlowImpl.MapWithParent;
import org.observe.collect.ObservableCollectionDataFlowImpl.ModFilterer;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable;
import org.qommons.Stamped;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Contains implementations of {@link PassiveCollectionManager} and its dependencies */
public class ObservableCollectionPassiveManagers {
	private ObservableCollectionPassiveManagers() {}

	/**
	 * A manager for a {@link ObservableCollection.CollectionDataFlow#supportsPassive() passively-}derived collection
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of the derived collection that this manager can power
	 */
	public static interface PassiveCollectionManager<E, I, T> extends CollectionOperation<E, I, T> {
		/** @return Whether this manager is the result of an odd number of {@link CollectionDataFlow#reverse() reverse} operations */
		boolean isReversed();

		/** @return The observable value of this manager's mapping function that produces values from source values */
		ObservableValue<? extends Function<? super E, ? extends T>> map();

		/**
		 * @return Whether this manager has the ability to convert its values to source values for at least some sub-set of possible values
		 */
		String canReverse();

		/**
		 * @param dest The filter-map structure whose source is the value to convert
		 * @param forAdd Whether this operation is a precursor to inserting the value into the collection
		 * @return The filter-mapped result (typically the same instance as <code>dest</code>)
		 */
		FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd);

		/**
		 * A shortcut for reversing a value
		 *
		 * @param dest The value to convert
		 * @param forAdd Whether this operation is a precursor to inserting the value into the collection
		 * @return The filter-mapped result
		 */
		default FilterMapResult<T, E> reverse(T dest, boolean forAdd) {
			return reverse(new FilterMapResult<>(dest), forAdd);
		}

		/** @return Whether this manager may disallow some remove operations */
		boolean isRemoveFiltered();

		/** @return null if this manager allows derived collections to move elements around, or a reason otherwise */
		default String canMove() {
			return null;
		}

		/**
		 * @param element The source element to map
		 * @param map The mapping function to apply to the element's value
		 * @return The element for the derived collection
		 */
		MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map);

		/**
		 * Maps the old and new source values for an event from the source collection to this manager's value type
		 *
		 * @param oldSource The old value from the source event
		 * @param newSource The new value from the source event
		 * @param map The mapping function to apply to the values
		 * @return The old and new values for the event to propagate from the manager's derived collection
		 */
		BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map);

		/**
		 * @return Whether this manager may produce a single mapped value for many different source values. Affects searching by value in
		 *         the derived collection.
		 */
		boolean isManyToOne();

		/**
		 * @param elements The elements to set the value for en masse
		 * @param value The value to set for the elements
		 */
		void setValue(Collection<MutableCollectionElement<T>> elements, T value);
	}

	/**
	 * Derives a function type from its parameter types
	 *
	 * @param <E> The compiler type of the function's source parameter
	 * @param <T> The compiler type of the function's product parameter
	 * @param srcType The run-time type of the function's source parameter
	 * @param destType The run-time type of the function's product parameter
	 * @return The type of a function with the given parameter types
	 */
	static <E, T> TypeToken<Function<? super E, T>> functionType(TypeToken<E> srcType, TypeToken<T> destType) {
		return new TypeToken<Function<? super E, T>>() {}.where(new TypeParameter<E>() {}, srcType.wrap()).where(new TypeParameter<T>() {},
			destType.wrap());
	}

	/**
	 * Supports passive collection of a base collection flow
	 *
	 * @param <E> The type of the collection and therefore the flow
	 */
	public static class BaseCollectionPassThrough<E> implements PassiveCollectionManager<E, E, E> {
		private static final ConcurrentHashMap<TypeToken<?>, TypeToken<? extends Function<?, ?>>> thePassThroughFunctionTypes = new ConcurrentHashMap<>();

		private final ObservableCollection<E> theSource;
		private final ObservableValue<Function<? super E, E>> theFunctionValue;

		/** @param source The source collection */
		public BaseCollectionPassThrough(ObservableCollection<E> source) {
			theSource = source;

			TypeToken<E> srcType = theSource.getType();
			TypeToken<Function<? super E, E>> functionType = (TypeToken<Function<? super E, E>>) thePassThroughFunctionTypes
				.computeIfAbsent(srcType, st -> functionType(st, st));
			theFunctionValue = ObservableValue.of(functionType, LambdaUtils.identity());
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theSource.getIdentity(), "passiveManager");
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSource.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theSource.tryLock(write, cause);
		}

		@Override
		public TypeToken<E> getTargetType() {
			return theSource.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theSource.equivalence();
		}

		@Override
		public boolean isContentControlled() {
			return theSource.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return false;
		}

		@Override
		public ObservableValue<Function<? super E, E>> map() {
			return theFunctionValue;
		}

		@Override
		public String canReverse() {
			return null;
		}

		@Override
		public FilterMapResult<E, E> reverse(FilterMapResult<E, E> dest, boolean forAdd) {
			dest.result = dest.source;
			return dest;
		}

		@Override
		public boolean isRemoveFiltered() {
			return false;
		}

		@Override
		public MutableCollectionElement<E> map(MutableCollectionElement<E> mapped, Function<? super E, ? extends E> map) {
			return mapped;
		}

		@Override
		public BiTuple<E, E> map(E oldSource, E newSource, Function<? super E, ? extends E> map) {
			return new BiTuple<>(oldSource, newSource);
		}

		@Override
		public boolean isManyToOne() {
			return false;
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<E>> elements, E value) {
			theSource.setValue(//
				elements.stream().map(el -> el.getElementId()).collect(Collectors.toList()), value);
		}
	}

	static class PassiveReversedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;

		PassiveReversedManager(PassiveCollectionManager<E, ?, T> parent) {
			theParent = parent;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "reverse");
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return !theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map); // Don't reverse here--the passive collection takes care of it
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(//
				elements, value);
			// elements.stream().map(el -> el.reverse()).collect(Collectors.toList()), value);
		}
	}

	static class PassiveEquivalenceSwitchedManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Equivalence<? super T> theEquivalence;

		PassiveEquivalenceSwitchedManager(PassiveCollectionManager<E, ?, T> parent, Equivalence<? super T> equivalence) {
			theParent = parent;
			theEquivalence = equivalence;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "withEquivalence", theEquivalence);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> mapped, Function<? super E, ? extends T> map) {
			return theParent.map(mapped, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements, value);
		}
	}

	private static abstract class AbstractPassiveMappingManager<E, I, T> extends AbstractMappingManager<E, I, T>
	implements PassiveCollectionManager<E, I, T> {
		AbstractPassiveMappingManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, XformOptions.XformDef options) {
			super(parent, targetType, options);
		}

		@Override
		protected PassiveCollectionManager<E, ?, I> getParent() {
			return (PassiveCollectionManager<E, ?, I>) super.getParent();
		}

		@Override
		public boolean isReversed() {
			return getParent().isReversed();
		}

		@Override
		protected void doParentMultiSet(Collection<AbstractMappedElement> elements, I newValue) {
			getParent().setValue(elements.stream().map(el -> ((PassiveMappedElement) el).getParentEl()).collect(Collectors.toList()),
				newValue);
		}

		@Override
		public String canReverse() {
			if (!isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			return getParent().canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (!isReversible())
				return dest.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			T value = dest.source;
			FilterMapResult<I, T> test=(FilterMapResult<I, T>) dest;
			test.result=value;
			test.source=null;
			test=canReverse(test);
			FilterMapResult<I, E> intermediate = (FilterMapResult<I, E>) test;
			intermediate.result=null;
			if (intermediate.isAccepted() && !equivalence().elementEquals(map(intermediate.source, null), value))
				return dest.reject(StdMsg.ILLEGAL_ELEMENT, true);
			return (FilterMapResult<T, E>) getParent().reverse(intermediate, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return getParent().isRemoveFiltered();
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			if (oldSource == newSource) {
				if (!getOptions().isFireIfUnchanged())
					return null;
				if (!getOptions().isReEvalOnUpdate()) {
					T newDest = map.apply(newSource);
					return new BiTuple<>(newDest, newDest);
				}
			}
			MapWithParent<E, I, T> mwp = (MapWithParent<E, I, T>) map;
			BiTuple<I, I> interm = getParent().map(oldSource, newSource, mwp.getParentMap());
			if (interm == null)
				return null;
			if (interm.getValue1() == interm.getValue2()) {
				if (!getOptions().isFireIfUnchanged())
					return null;
				if (!getOptions().isReEvalOnUpdate()) {
					T newDest = mwp.mapIntermediate(interm.getValue2());
					return new BiTuple<>(newDest, newDest);
				}
			}
			T v1 = mwp.mapIntermediate(interm.getValue1());
			T v2 = mwp.mapIntermediate(interm.getValue2());
			return new BiTuple<>(v1, v2);
		}

		@Override
		public boolean isManyToOne() {
			return getOptions().isManyToOne() || getParent().isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			setElementsValue(elements, value);
		}

		@Override
		public abstract PassiveMappedElement map(MutableCollectionElement<E> source, Function<? super E, ? extends T> map);

		protected abstract class PassiveMappedElement extends AbstractMappedElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<I> theParentEl;

			protected PassiveMappedElement(MutableCollectionElement<I> parentEl) {
				theParentEl = parentEl;
			}

			protected MutableCollectionElement<I> getParentEl() {
				return theParentEl;
			}

			@Override
			public ElementId getElementId() {
				return theParentEl.getElementId();
			}

			@Override
			public T get() {
				return mapForElement(theParentEl.get(), null);
			}

			@Override
			public BetterCollection<T> getCollection() {
				return null;
			}

			@Override
			public String isEnabled() {
				return isEnabledLocal();
			}

			@Override
			public String isAcceptable(T value) {
				return super.isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				super.set(value);
			}

			@Override
			public String canRemove() {
				return theParentEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theParentEl.remove();
			}

			@Override
			T getValue() {
				return mapForElement(theParentEl.get(), null);
			}

			@Override
			I getParentValue() {
				return theParentEl.get();
			}

			@Override
			I getCachedSource() {
				throw new IllegalStateException();
			}

			@Override
			protected String isParentAcceptable(I value) {
				return theParentEl.isAcceptable(value);
			}

			@Override
			void setParent(I parentValue) {
				theParentEl.set(parentValue);
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = canReverse();
				if (msg != null)
					return msg;
				I intermVal = reverseForElement(value);
				return theParentEl.canAdd(intermVal, before);
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				String msg = canReverse();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				I intermVal = reverseForElement(value);
				return theParentEl.add(intermVal, before);
			}

			@Override
			public String toString() {
				return theParentEl.toString();
			}
		}
	}

	static class PassiveMappedCollectionManager<E, I, T> extends AbstractPassiveMappingManager<E, I, T> {
		private final Function<? super I, ? extends T> theMap;
		private final Equivalence<? super T> theEquivalence;

		PassiveMappedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType,
			Function<? super I, ? extends T> map, Equivalence<? super T> equivalence, MapDef<I, T> options) {
			super(parent, targetType, options);
			theMap = map;
			theEquivalence = equivalence;
		}

		@Override
		protected MapDef<I, T> getOptions() {
			return (MapDef<I, T>) super.getOptions();
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "map", theMap, getOptions());
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theEquivalence;
		}

		@Override
		public ObservableValue<Function<? super E, T>> map() {
			return getParent().map().map(parentMap -> new MapWithParent<>(parentMap, theMap));
		}

		@Override
		protected boolean isReversible() {
			return getOptions().getReverse() != null;
		}

		@Override
		protected T map(I value, T previousValue) {
			return theMap.apply(value);
		}

		@Override
		protected FilterMapResult<I, T> canReverse(FilterMapResult<I, T> sourceAndResult) {
			return getOptions().getReverse().canReverse(sourceAndResult);
		}

		@Override
		protected I reverse(I preSource, T value) {
			return getOptions().getReverse().reverse(preSource, value);
		}

		@Override
		protected boolean isReverseStateful() {
			return false;
		}

		@Override
		public MappedElement map(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
			return new MappedElement(source, map);
		}

		class MappedElement extends PassiveMappedElement {
			MappedElement(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
				super(getParent().map(source, ((MapWithParent<E, I, I>) map).getParentMap()));
			}
		}
	}

	static class PassiveCombinedCollectionManager<E, I, T> extends AbstractPassiveMappingManager<E, I, T> {
		PassiveCombinedCollectionManager(PassiveCollectionManager<E, ?, I> parent, TypeToken<T> targetType, CombinedFlowDef<I, T> def) {
			super(parent, targetType, def);
		}

		@Override
		protected CombinedFlowDef<I, T> getOptions() {
			return (CombinedFlowDef<I, T>) super.getOptions();
		}

		@Override
		protected boolean isReversible() {
			return getOptions().getReverse() != null;
		}

		@Override
		protected T map(I value, T previousValue) {
			return getOptions().getCombination().apply(new Combination.CombinedValues<I>() {
				@Override
				public I getElement() {
					return value;
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					return arg.get();
				}
			}, previousValue);
		}

		@Override
		protected FilterMapResult<I, T> canReverse(FilterMapResult<I, T> sourceAndResult) {
			sourceAndResult.source = reverse(sourceAndResult.source, sourceAndResult.result);
			return sourceAndResult;
		}

		@Override
		protected I reverse(I preSource, T value) {
			return getOptions().getReverse().apply(new Combination.CombinedValues<T>() {
				@Override
				public T getElement() {
					return value;
				}

				@Override
				public <X> X get(ObservableValue<X> arg) {
					return arg.get();
				}
			});
		}

		@Override
		protected boolean isReverseStateful() {
			return false;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(getParent().getIdentity(), "map", getOptions());
		}

		@Override
		public Equivalence<? super T> equivalence() {
			if (getTargetType().equals(getParent().getTargetType()))
				return (Equivalence<? super T>) getParent().equivalence();
			else
				return Equivalence.DEFAULT;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(getParent(), write, cause), getOptions().getArgs());
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(getParent(), write, cause), getOptions().getArgs());
		}

		private Transaction lockArgs() {
			return Lockable.lockAll(getOptions().getArgs());
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			ObservableValue<? extends Function<? super E, ? extends I>> parentMap = getParent().map();
			return new ObservableValue<Function<? super E, T>>() {
				/** Can't imagine why this would ever be used, but we'll fill it out I guess */
				private TypeToken<Function<? super E, T>> theType;

				@Override
				public Object getIdentity() {
					return Identifiable.wrap(parentMap.getIdentity(), "combine", getOptions().getIdentity());
				}

				@Override
				public long getStamp() {
					return parentMap.getStamp() ^ Stamped.compositeStamp(getOptions().getArgs(), Stamped::getStamp);
				}

				@Override
				public TypeToken<Function<? super E, T>> getType() {
					if (theType == null)
						theType = functionType((TypeToken<E>) parentMap.getType().resolveType(Function.class.getTypeParameters()[0]),
							getTargetType());
					return theType;
				}

				@Override
				public Transaction lock() {
					Transaction parentLock = parentMap.lock();
					Transaction valueLock = lockArgs();
					return () -> {
						valueLock.close();
						parentLock.close();
					};
				}

				@Override
				public Function<? super E, T> get() {
					Function<? super E, ? extends I> parentMapVal = parentMap.get();
					Map<ObservableValue<?>, ObservableValue<?>> values = new IdentityHashMap<>();
					for (ObservableValue<?> v : getOptions().getArgs())
						values.put(v, v);

					return new CombinedMap(parentMapVal, null, values);
				}

				@Override
				public Observable<ObservableValueEvent<Function<? super E, T>>> changes() {
					Observable<? extends ObservableValueEvent<? extends Function<? super E, ? extends I>>> parentChanges = parentMap
						.changes();
					return new Observable<ObservableValueEvent<Function<? super E, T>>>() {
						@Override
						public Object getIdentity() {
							return Identifiable.wrap(parentMap.changes().getIdentity(), "combine", getOptions().getIdentity());
						}

						@Override
						public boolean isSafe() {
							return false;
						}

						@Override
						public Subscription subscribe(Observer<? super ObservableValueEvent<Function<? super E, T>>> observer) {
							CombinedMap[] currentMap = new PassiveCombinedCollectionManager.CombinedMap[1];
							Subscription parentSub = parentChanges
								.act(new Consumer<ObservableValueEvent<? extends Function<? super E, ? extends I>>>() {
									@Override
									public void accept(ObservableValueEvent<? extends Function<? super E, ? extends I>> parentEvt) {
										if (parentEvt.isInitial()) {
											currentMap[0] = new CombinedMap(parentEvt.getNewValue(), null, new IdentityHashMap<>());
											ObservableValueEvent<Function<? super E, T>> evt = createInitialEvent(currentMap[0], null);
											try (Transaction t = Causable.use(evt)) {
												observer.onNext(evt);
											}
											return;
										}
										try (Transaction valueLock = lockArgs()) {
											CombinedMap oldMap = currentMap[0];
											currentMap[0] = new CombinedMap(parentEvt.getNewValue(), null, oldMap.theValues);
											ObservableValueEvent<Function<? super E, T>> evt2 = createChangeEvent(oldMap, currentMap[0],
												parentEvt);
											try (Transaction evtT = Causable.use(evt2)) {
												observer.onNext(evt2);
											}
										}
									}
								});
							Subscription[] argSubs = new Subscription[getOptions().getArgs().size()];
							int a = 0;
							for (ObservableValue<?> arg : getOptions().getArgs()) {
								int argIndex = a++;
								argSubs[argIndex] = arg.changes().act(new Consumer<ObservableValueEvent<?>>() {
									@Override
									public void accept(ObservableValueEvent<?> argEvent) {
										if (argEvent.isInitial()) {
											((Map<ObservableValue<?>, SimpleSupplier>) currentMap[0].theValues).put(arg,
												new SimpleSupplier(argEvent.getNewValue()));
											return;
										}
										try (Transaction t = lock()) {
											CombinedMap oldMap = currentMap[0];
											Map<ObservableValue<?>, SimpleSupplier> newValues = new IdentityHashMap<>(
												(Map<ObservableValue<?>, SimpleSupplier>) oldMap.theValues);
											newValues.put(arg, new SimpleSupplier(argEvent.getNewValue()));
											currentMap[0] = new CombinedMap(oldMap.getParentMap(), null, newValues);
											ObservableValueEvent<Function<? super E, T>> evt = createChangeEvent(oldMap, currentMap[0],
												argEvent);
											try (Transaction evtT = Causable.use(evt)) {
												observer.onNext(evt);
											}
										}
									}
								});
							}
							return () -> {
								try (Transaction t = lock()) {
									for (int i = 0; i < argSubs.length; i++)
										argSubs[i].unsubscribe();
									parentSub.unsubscribe();
								}
							};
						}

						@Override
						public Transaction lock() {
							return Lockable.lockAll(getOptions().getArgs());
						}

						@Override
						public Transaction tryLock() {
							return Lockable.tryLockAll(getOptions().getArgs());
						}
					};
				}

				@Override
				public Observable<ObservableValueEvent<Function<? super E, T>>> noInitChanges() {
					return changes().noInit();
				}
			};
		}

		@Override
		public boolean isRemoveFiltered() {
			return false;
		}

		@Override
		public CombinedElement map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return new CombinedElement(element, map);
		}

		class SimpleSupplier implements Supplier<Object> {
			final Object value;

			SimpleSupplier(Object value) {
				this.value = value;
			}

			@Override
			public Object get() {
				return value;
			}
		}

		class CombinedElement extends PassiveMappedElement {
			private final CombinedMap theCombinedMap;

			CombinedElement(MutableCollectionElement<E> source, Function<? super E, ? extends T> map) {
				super(getParent().map(source, ((CombinedMap) map).getParentMap()));
				theCombinedMap = (CombinedMap) map;
			}

			@Override
			public T mapForElement(I source, T value) {
				return theCombinedMap.mapIntermediate(source);
			}

			@Override
			public I reverseForElement(T source) {
				return theCombinedMap.reverse(source);
			}
		}

		class CombinedMap extends MapWithParent<E, I, T> {
			final Map<ObservableValue<?>, ? extends Supplier<?>> theValues;

			CombinedMap(Function<? super E, ? extends I> parentMap, Function<? super I, ? extends T> map,
				Map<ObservableValue<?>, ? extends Supplier<?>> values) {
				super(parentMap, map);
				theValues = values;
			}

			@Override
			public T mapIntermediate(I interm) {
				return getOptions().getCombination().apply(new Combination.CombinedValues<I>() {
					@Override
					public I getElement() {
						return interm;
					}

					@Override
					public <X> X get(ObservableValue<X> arg) {
						Supplier<?> holder = theValues.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return (X) holder.get();
					}
				}, null);
			}

			I reverse(T dest) {
				return getOptions().getReverse().apply(new Combination.CombinedValues<T>() {
					@Override
					public T getElement() {
						return dest;
					}

					@Override
					public <X> X get(ObservableValue<X> arg) {
						Supplier<?> holder = theValues.get(arg);
						if (holder == null)
							throw new IllegalArgumentException("Unrecognized value: " + arg);
						return (X) holder.get();
					}
				});
			}
		}
	}

	static class PassiveRefreshingCollectionManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final Observable<?> theRefresh;

		PassiveRefreshingCollectionManager(PassiveCollectionManager<E, ?, T> parent, Observable<?> refresh) {
			theParent = parent;
			theRefresh = refresh;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "refresh", theRefresh.getIdentity());
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported() && theRefresh.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(Lockable.lockable(theParent, write, cause), theRefresh);
		}

		@Override
		public boolean isContentControlled() {
			return theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map().refresh(theRefresh);
		}

		@Override
		public String canReverse() {
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theParent.isRemoveFiltered();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return theParent.map(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements, value);
		}
	}

	static class PassiveModFilteredManager<E, T> implements PassiveCollectionManager<E, T, T> {
		private final PassiveCollectionManager<E, ?, T> theParent;
		private final ModFilterer<T> theFilter;

		PassiveModFilteredManager(PassiveCollectionManager<E, ?, T> parent, ModFilterer<T> filter) {
			theParent = parent;
			theFilter = filter;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.wrap(theParent.getIdentity(), "filterMod", theFilter);
		}

		@Override
		public TypeToken<T> getTargetType() {
			return theParent.getTargetType();
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return theParent.equivalence();
		}

		@Override
		public boolean isLockSupported() {
			return theParent.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theParent.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theParent.tryLock(write, cause);
		}

		@Override
		public boolean isContentControlled() {
			return !theFilter.isEmpty() || theParent.isContentControlled();
		}

		@Override
		public boolean isReversed() {
			return theParent.isReversed();
		}

		@Override
		public ObservableValue<? extends Function<? super E, ? extends T>> map() {
			return theParent.map();
		}

		@Override
		public String canReverse() {
			String msg = theFilter.canAdd();
			if (msg != null)
				return msg;
			return theParent.canReverse();
		}

		@Override
		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> dest, boolean forAdd) {
			if (forAdd) {
				dest.maybeReject(theFilter.canAdd(dest.source), true);
				if (!dest.isAccepted())
					return dest;
			}
			return theParent.reverse(dest, forAdd);
		}

		@Override
		public boolean isRemoveFiltered() {
			return theFilter.isRemoveFiltered() || theFilter.getUnmodifiableMessage() != null || theParent.isRemoveFiltered();
		}

		@Override
		public String canMove() {
			return theFilter.canMove();
		}

		@Override
		public MutableCollectionElement<T> map(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
			return new ModFilteredElement(element, map);
		}

		@Override
		public BiTuple<T, T> map(E oldSource, E newSource, Function<? super E, ? extends T> map) {
			return theParent.map(oldSource, newSource, map);
		}

		@Override
		public boolean isManyToOne() {
			return theParent.isManyToOne();
		}

		@Override
		public void setValue(Collection<MutableCollectionElement<T>> elements, T value) {
			theParent.setValue(elements.stream().map(el -> ((ModFilteredElement) el).theParentMapped).collect(Collectors.toList()), value);
		}

		private class ModFilteredElement implements MutableCollectionElement<T> {
			private final MutableCollectionElement<T> theParentMapped;

			ModFilteredElement(MutableCollectionElement<E> element, Function<? super E, ? extends T> map) {
				theParentMapped = theParent.map(element, map);
			}

			@Override
			public BetterCollection<T> getCollection() {
				return null;
			}

			@Override
			public ElementId getElementId() {
				return theParentMapped.getElementId();
			}

			@Override
			public T get() {
				return theParentMapped.get();
			}

			@Override
			public String isEnabled() {
				String msg = theFilter.isEnabled();
				if (msg == null)
					msg = theParentMapped.isEnabled();
				return msg;
			}

			@Override
			public String isAcceptable(T value) {
				String msg = theFilter.isAcceptable(value, //
					this::get);
				if (msg == null)
					msg = theParentMapped.isAcceptable(value);
				return msg;
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertSet(value, //
					this::get);
				theParentMapped.set(value);
			}

			@Override
			public String canRemove() {
				String msg = theFilter.canRemove(//
					this::get);
				if (msg == null)
					msg = theParentMapped.canRemove();
				return msg;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theFilter.assertRemove(//
					this::get);
				theParentMapped.remove();
			}

			@Override
			public String canAdd(T value, boolean before) {
				String msg = theFilter.canAdd(value);
				if (msg == null)
					msg = theParentMapped.canAdd(value, before);
				return msg;
			}

			@Override
			public ElementId add(T value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				theFilter.assertAdd(value);
				return theParentMapped.add(value, before);
			}

			@Override
			public String toString() {
				return theParentMapped.toString();
			}
		}
	}
}
