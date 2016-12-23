package org.observe.collect;

import static org.observe.ObservableDebug.d;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.qommons.Equalizer;
import org.qommons.Equalizer.EqualizerNode;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, TransactableSet<E> {
	/** @return The equalizer that governs uniqueness of this set
	 * TODO Remove this and just make Object.equals the standard */
	Equalizer getEqualizer();

	@Override
	default boolean isEmpty() {
		return ObservableCollection.super.isEmpty();
	}

	@Override
	default boolean contains(Object o) {
		return ObservableCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(java.util.Collection<?> coll) {
		return ObservableCollection.super.containsAll(coll);
	}

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value
	 */
	default ObservableValue<E> equivalent(Object o) {
		return new ObservableSetEquivalentFinder<>(this, o);
	}

	@Override
	default E [] toArray() {
		return ObservableCollection.super.toArray();
	}

	@Override
	default <T> T [] toArray(T [] a) {
		return ObservableCollection.super.toArray(a);
	}

	@Override
	default ObservableSet<E> safe() {
		if (isSafe())
			return this;
		else
			return d().debug(new SafeObservableSet<>(this)).from("safe", this).get();
	}

	/**
	 * @param filter The filter function
	 * @return A set containing all elements passing the given test
	 */
	@Override
	default ObservableSet<E> filter(Predicate<? super E> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filter(filter);
	}

	@Override
	default ObservableSet<E> filter(Predicate<? super E> filter, boolean staticFilter) {
		if(staticFilter)
			return filterStatic(filter);
		else
			return filterDynamic(filter);
	}

	@Override
	default ObservableSet<E> filterDynamic(Predicate<? super E> filter) {
		return d().debug(new DynamicFilteredSet<>(this, getType(), filter)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default ObservableSet<E> filterStatic(Predicate<? super E> filter) {
		return d().debug(new StaticFilteredSet<>(this, getType(), filter)).from("filter", this).using("filter", filter).get();
	}

	@Override
	default <T> ObservableSet<T> filter(Class<T> type) {
		Predicate<E> filter = value -> type.isInstance(value);
		return d().debug(new StaticFilteredSet<>(this, TypeToken.of(type), filter)).from("filterMap", this).using("filter", filter)
			.tag("filterType", type).get();
	}

	/**
	 * Creates a mapped set, relying on the caller to guarantee that values returned by the function are equivalent to the argument
	 *
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> ObservableSet<T> mapEquivalent(Function<? super E, T> map, Function<? super T, E> reverse) {
		return mapEquivalent((TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, reverse);
	}

	/**
	 * Creates a mapped set, relying on the caller to guarantee that values returned by the function are equivalent to the argument
	 *
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> ObservableSet<T> mapEquivalent(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return d().debug(new MappedObservableSet<>(this, type, map, reverse)).from("map", this).using("map", map).using("reverse", reverse)
			.get();
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A set whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refresh(Observable<?> refresh) {
		return d().debug(new RefreshingSet<>(this, refresh)).from("refresh", this).from("on", refresh).get();
	}

	/**
	 * @param refresh A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refreshEach(Function<? super E, Observable<?>> refresh) {
		return d().debug(new ElementRefreshingSet<>(this, refresh)).from("refreshEach", this).using("on", refresh).get();
	}

	@Override
	default ObservableSet<E> immutable() {
		return d().debug(new ImmutableObservableSet<>(this)).from("immutable", this).get();
	}

	@Override
	default ObservableSet<E> filterRemove(Predicate<? super E> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableSet<E> noRemove() {
		return (ObservableSet<E>) ObservableCollection.super.noRemove();
	}

	@Override
	default ObservableSet<E> filterAdd(Predicate<? super E> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableSet<E> noAdd() {
		return (ObservableSet<E>) ObservableCollection.super.noAdd();
	}

	@Override
	default ObservableSet<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredSet<>(this, removeFilter, addFilter);
	}

	/**
	 * Creates a collection with the same elements as this collection, but cached, such that the
	 *
	 * @return The cached collection
	 */
	@Override
	default ObservableSet<E> cached() {
		return d().debug(new SafeCachedObservableSet<>(this)).from("cached", this).get();
	}

	/**
	 * @param until The observable to end the set on
	 * @return A set that mirrors this set's values until the given observable fires a value, upon which the returned set's elements will be
	 *         removed and set subscriptions unsubscribed
	 */
	@Override
	default ObservableSet<E> takeUntil(Observable<?> until) {
		return d().debug(new TakenUntilObservableSet<>(this, until, true)).from("take", this).from("until", until).get();
	}

	/**
	 * @param until The observable to unsubscribe the set on
	 * @return A set that mirrors this set's values until the given observable fires a value, upon which the returned set's subscriptions
	 *         will be removed. Unlike {@link #takeUntil(Observable)} however, the returned set's elements will not be removed when the
	 *         observable fires.
	 */
	@Override
	default ObservableSet<E> unsubscribeOn(Observable<?> until) {
		return d().debug(new TakenUntilObservableSet<>(this, until, true)).from("take", this).from("until", until).get();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
		return d().debug(new FlattenedValueSet<>(collectionObservable)).from("flatten", collectionObservable).get();
	}

	/*public static <E> ObservableSet<E> intersect(TypeToken<E> type, Collection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer) {
		TypeToken<ObservableSet<? extends E>> setType = new TypeToken<ObservableSet<? extends E>>() {}.where(new TypeParameter<E>() {},
			type);
		ObservableCollection<ObservableSet<? extends E>> obSets = ObservableCollection.constant(setType,
			(Collection<ObservableSet<? extends E>>) sets);
		return combine(obSets, equalizer, sets.size(), Integer.MAX_VALUE);
	}

	public static <E> ObservableSet<E> union(ObservableCollection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer) {
		return combine(sets, equalizer, 1, Integer.MAX_VALUE);
	}

	public static <E> ObservableSet<E> combine(ObservableCollection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer,
		int minCount, int maxCount) {
		return new SetCombination<>(sets, equalizer, minCount, maxCount);
	}*/

	/**
	 * A default toString() method for set implementations to use
	 *
	 * @param set The set to print
	 * @return The string representation of the set
	 */
	public static String toString(ObservableSet<?> set) {
		StringBuilder ret = new StringBuilder("{");
		boolean first = true;
		try (Transaction t = set.lock(false, null)) {
			for(Object value : set) {
				if(!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append('}');
		return ret.toString();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(TypeToken<T> type, java.util.Collection<T> coll) {
		Set<T> modSet = new java.util.LinkedHashSet<>(coll);
		Set<T> constSet = java.util.Collections.unmodifiableSet(modSet);
		java.util.List<ObservableElement<T>> els = new java.util.ArrayList<>();
		class ConstantObservableSet implements PartialSetImpl<T> {
			@Override
			public Equalizer getEqualizer() {
				return Objects::equals;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
				for(ObservableElement<T> el : els)
					observer.accept(el);
				return () -> {
				};
			}

			@Override
			public int size() {
				return constSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return constSet.iterator();
			}
			@Override
			public boolean canRemove(Object value) {
				return false;
			}

			@Override
			public boolean canAdd(T value) {
				return false;
			}
		}
		ConstantObservableSet ret = d().debug(new ConstantObservableSet()).tag("constant", coll).tag("type", type).get();
		for(T value : constSet)
			els.add(d().debug(new ObservableElement<T>() {
				@Override
				public TypeToken<T> getType() {
					return type;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					observer.onNext(createInitialEvent(value));
					return () -> {
					};
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public ObservableValue<T> persistent() {
					return this;
				}
			}).from("element", ret).tag("value", value).get());
		return ret;
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(TypeToken<T> type, T... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @param equalizer The equalizer to determine uniqueness between elements
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> ObservableSet<T> unique(ObservableCollection<T> coll, Equalizer equalizer) {
		return d().debug(new CollectionWrappingSet<>(coll, equalizer)).from("unique", coll).get();
	}

	/**
	 * An extension of ObservableSet that implements some of the redundant methods and throws UnsupportedOperationExceptions for
	 * modifications.
	 *
	 * @param <E> The type of element in the set
	 */
	interface PartialSetImpl<E> extends PartialCollectionImpl<E>, ObservableSet<E> {
		@Override
		default boolean remove(Object o) {
			return PartialCollectionImpl.super.remove(o);
		}

		@Override
		default boolean removeAll(Collection<?> c) {
			return PartialCollectionImpl.super.removeAll(c);
		}

		@Override
		default boolean retainAll(Collection<?> c) {
			return PartialCollectionImpl.super.retainAll(c);
		}

		@Override
		default void clear() {
			PartialCollectionImpl.super.clear();
		}

		@Override
		default boolean add(E value) {
			return PartialCollectionImpl.super.add(value);
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			return PartialCollectionImpl.super.addAll(c);
		}
	}

	/**
	 * Implements {@link ObservableSet#equivalent(Object)}
	 *
	 * @param <E> The type of the set to find the value in
	 */
	class ObservableSetEquivalentFinder<E> implements ObservableValue<E> {
		private final ObservableSet<E> theSet;

		private final Object theKey;

		protected ObservableSetEquivalentFinder(ObservableSet<E> set, Object key) {
			theSet = set;
			theKey = key;
		}

		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}

		@Override
		public E get() {
			for(E value : theSet) {
				if(Objects.equals(value, theKey))
					return value;
			}
			return null;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			boolean [] isMatch = new boolean[1];
			boolean [] initialized = new boolean[1];
			Subscription ret = theSet.onElement(new Consumer<ObservableElement<E>>() {
				private E theCurrentMatch;

				@Override
				public void accept(ObservableElement<E> element) {
					element.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V event) {
							isMatch[0] = Objects.equals(event.getValue(), theKey);
							if(isMatch[0]) {
								E old = theCurrentMatch;
								theCurrentMatch = event.getValue();
								if (!initialized[0])
									observer.onNext(createInitialEvent(event.getValue()));
								else
									observer.onNext(createChangeEvent(old, event.getValue(), event));
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
							if(isMatch[0] && theCurrentMatch == event.getValue()) {
								theCurrentMatch = null;
								observer.onNext(createChangeEvent(event.getValue(), null, event));
							}
						}
					});
				}
			});
			initialized[0] = true;
			if(!isMatch[0])
				observer.onNext(createInitialEvent(null));
			return ret;
		}

		@Override
		public boolean isSafe() {
			return theSet.isSafe();
		}
	}

	/**
	 * Implements {@link ObservableSet#safe()}
	 *
	 * @param <E> The type of elements in the set
	 */
	class SafeObservableSet<E> extends SafeObservableCollection<E> implements ObservableSet<E> {
		public SafeObservableSet(ObservableSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#filter(Predicate)} and {@link ObservableSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class StaticFilteredSet<E, T> extends StaticFilteredCollection<E, T> implements PartialSetImpl<T> {
		protected StaticFilteredSet(ObservableSet<E> wrap, TypeToken<T> type, Predicate<? super E> filter) {
			super(wrap, type, value -> {
				boolean pass = filter.test(value);
				return new FilterMapResult<>(pass ? (T) value : null, pass);
			} , value -> (E) value);
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#filter(Predicate)} and {@link ObservableSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class DynamicFilteredSet<E, T> extends DynamicFilteredCollection<E, T> implements PartialSetImpl<T> {
		protected DynamicFilteredSet(ObservableSet<E> wrap, TypeToken<T> type, Predicate<? super E> filter) {
			super(wrap, type, value -> {
				boolean pass = filter.test(value);
				return new FilterMapResult<>(pass ? (T) value : null, pass);
			} , value -> (E) value);
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#mapEquivalent(TypeToken, Function, Function)}
	 *
	 * @param <E> The type of the set to map
	 * @param <T> The type of the mapped set
	 */
	class MappedObservableSet<E, T> extends MappedObservableCollection<E, T> implements ObservableSet<T> {
		protected MappedObservableSet(ObservableSet<E> wrap, TypeToken<T> type, Function<? super E, T> map,
			Function<? super T, E> reverse) {
			super(wrap, type, map, reverse);
		}

		@Override
		public Equalizer getEqualizer() {
			Function<? super T, ?> eqMap;
			if (getReverse() != null)
				eqMap = getReverse();
			else
				eqMap = o -> o;
				return (o1, o2) -> getWrapped().getEqualizer().equals(eqMap.apply((T) o1), eqMap.apply((T) o2));
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#refresh(Observable)}
	 *
	 * @param <E> The type of the set
	 */
	class RefreshingSet<E> extends RefreshingCollection<E> implements PartialSetImpl<E> {
		protected RefreshingSet(ObservableSet<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#refreshEach(Function)}
	 *
	 * @param <E> The type of the set
	 */
	class ElementRefreshingSet<E> extends ElementRefreshingCollection<E> implements PartialSetImpl<E> {
		protected ElementRefreshingSet(ObservableSet<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	class ImmutableObservableSet<E> extends ImmutableObservableCollection<E> implements PartialSetImpl<E> {
		protected ImmutableObservableSet(ObservableSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public ImmutableObservableSet<E> immutable() {
			return this;
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredSet<E> extends ModFilteredCollection<E> implements PartialSetImpl<E> {
		public ModFilteredSet(ObservableSet<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#cached()}
	 *
	 * @param <E> The type of elements in the set
	 */
	class SafeCachedObservableSet<E> extends SafeCachedObservableCollection<E> implements PartialSetImpl<E> {
		protected SafeCachedObservableSet(ObservableSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public ObservableSet<E> cached() {
			return this;
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Backs {@link ObservableSet#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class TakenUntilObservableSet<E> extends TakenUntilObservableCollection<E> implements PartialSetImpl<E> {
		public TakenUntilObservableSet(ObservableSet<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			return getWrapped().getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#combine(ObservableCollection, Equalizer, Supplier, Supplier)}
	 *
	 * @param <E> The super type of elements in the collection / class SetCombination<E> implements PartialSetImpl<E> { private final
	 *        ObservableCollection<? extends ObservableSet<? extends E>> theSets; private final TypeToken<E> theType; private final
	 *        Equalizer theEqualizer; private final int theMinCount; private final int theMaxCount; private final
	 *        CombinedCollectionSessionObservable theSession;
	 *
	 *        public SetCombination(ObservableCollection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer, int minCount, int
	 *        maxCount) { theSets = sets; theType = (TypeToken<E>)
	 *        theSets.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]); theEqualizer = equalizer; theMinCount =
	 *        minCount; theMaxCount = maxCount; theSession = new CombinedCollectionSessionObservable(theSets); }
	 *
	 * @Override public TypeToken<E> getType() { return theType; }
	 *
	 * @Override public ObservableValue<CollectionSession> getSession() { return theSession; }
	 *
	 * @Override public boolean isSafe() { return false; }
	 *
	 * @Override public boolean canRemove(Object value) { int count = 0; for (ObservableSet<? extends E> setEl : theSets) { if
	 *           (!setEl.canRemove(value) && setEl.contains(value)) count++; } return count < theMinCount; }
	 *
	 * @Override public boolean canAdd(E value) { int count = 0; int addableCount = 0; int removableCount = 0; for (ObservableSet<? extends
	 *           E> setEl : theSets) { if (setEl.contains(value)) { count++; if (setEl.canRemove(value)) removableCount++; } else if
	 *           (canAdd(setEl, value)) addableCount++; } return count + addableCount >= theMinCount && count - removableCount <=
	 *           theMaxCount; }
	 *
	 *           private <T extends E> boolean canAdd(ObservableSet<T> setEl, E value) { if (value == null ||
	 *           setEl.getType().getRawType().isInstance(value)) return setEl.canAdd((T) value); else return false; }
	 *
	 * @Override public Transaction lock(boolean write, Object cause) { return CombinedCollectionSessionObservable.lock(theSets, write,
	 *           cause); }
	 *
	 * @Override public int size() { Map<Equalizer.EqualizerNode<E>, int[]> elementCounts = new LinkedHashMap<>(); for (ObservableSet<?
	 *           extends E> setEl : theSets) for (E el : setEl) elementCounts.computeIfAbsent(theEqualizer.nodeFor(el), n -> new
	 *           int[1])[0]++; return (int) elementCounts.entrySet().stream().filter(e -> e.getValue()[0] >= theMinCount && e.getValue()[0]
	 *           <= theMaxCount) .count(); }
	 *
	 * @Override public Iterator<E> iterator() { if (theMaxCount >= theSets.size()) { return new Iterator<E>() { private final Iterator<?
	 *           extends ObservableSet<? extends E>> outerIter = theSets.iterator(); private Iterator<? extends E> innerIter; private
	 *           Map<Equalizer.EqualizerNode<E>, int[]> elementCounts = new LinkedHashMap<>(); private boolean isReady; private E
	 *           theNextValue; private boolean hadGoodValue;
	 *
	 * @Override public boolean hasNext() { if (isReady) return true; while (!isReady) { if (innerIter == null || !innerIter.hasNext()) { if
	 *           (outerIter.hasNext()) { hadGoodValue = false; // Don't remove from the new iterator innerIter =
	 *           outerIter.next().iterator(); } else break; } while (innerIter.hasNext()) { E value = innerIter.next(); int[] count =
	 *           elementCounts.computeIfAbsent(theEqualizer.nodeFor(value), n -> new int[1]); count[0]++; if (count[0] == theMinCount) {
	 *           theNextValue = value; isReady = true; break; } } } return isReady; }
	 *
	 * @Override public E next() { if (!isReady && !hasNext()) throw new java.util.NoSuchElementException(); isReady = false; hadGoodValue =
	 *           true; return theNextValue; }
	 *
	 * @Override public void remove() { if (hadGoodValue) { hadGoodValue = false; innerIter.remove(); } else throw new
	 *           IllegalStateException("Call remove() right after next()"); } }; } else { Map<Equalizer.EqualizerNode<E>, int[]>
	 *           elementCounts = new LinkedHashMap<>(); for (ObservableSet<? extends E> setEl : theSets) for (E el : setEl)
	 *           elementCounts.computeIfAbsent(theEqualizer.nodeFor(el), n -> new int[1])[0]++; return new Iterator<E>() { private final
	 *           Iterator<Map.Entry<Equalizer.EqualizerNode<E>, int[]>> entryIter = elementCounts.entrySet().iterator(); private
	 *           Map.Entry<Equalizer.EqualizerNode<E>, int[]> theLastEntry; private boolean isReady; private boolean hadGoodValue;
	 *
	 * @Override public boolean hasNext() { if (isReady) return true; hadGoodValue = false; while (entryIter.hasNext()) { theLastEntry =
	 *           entryIter.next(); if (theLastEntry.getValue()[0] >= theMinCount && theLastEntry.getValue()[0] <= theMaxCount) break; }
	 *           return isReady; }
	 *
	 * @Override public E next() { if (!isReady && !hasNext()) throw new java.util.NoSuchElementException(); isReady = false; hadGoodValue =
	 *           true; return theLastEntry.getKey().get(); }
	 *
	 * @Override public void remove() { if (!hadGoodValue) throw new IllegalStateException("Call remove() right after next()"); hadGoodValue
	 *           = false; entryIter.remove(); for (ObservableSet<? extends E> setEl : theSets) { if
	 *           (setEl.canRemove(theLastEntry.getKey().get()) && setEl.remove(theLastEntry.getKey().get())) { theLastEntry.getValue()[0]--;
	 *           if (theLastEntry.getValue()[0] < theMinCount) break; } } } }; } }
	 *
	 * @Override public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) { class CountedElement{ final AtomicInteger
	 *           count=new AtomicInteger(); final java.util.concurrent.ConcurrentLinkedQueue<ObservableElement<? ex> }
	 *           ConcurrentHashMap<Equalizer.EqualizerNode<E>, AtomicInteger> elementCounts=new ConcurrentHashMap<>(); return
	 *           theSets.onElement(outerEl->{ outerEl.get().takeUntil(outerEl).onElement(innerEl->{ innerEl.subscribe(new
	 *           Observer<ObservableValueEvent<? extends E>>(){
	 * @Override public <V extends ObservableValueEvent<? extends E>> void onNext(V event) { // TODO Auto-generated method stub
	 *
	 *           }
	 *
	 * @Override public <V extends ObservableValueEvent<? extends E>> void onCompleted(V event) { int
	 *           newCount=elementCounts.get(theEqualizer.nodeFor(event.getValue())).decrementAndGet(); if(newCount<theMinCount) // TODO
	 *           Auto-generated method stub
	 *
	 *           } }); }); }); }
	 *
	 * @Override public Equalizer getEqualizer() { return theEqualizer; } }
	 */

	/**
	 * Implements {@link ObservableSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class FlattenedValueSet<E> extends FlattenedValueCollection<E> implements PartialSetImpl<E> {
		public FlattenedValueSet(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}

		@Override
		public Equalizer getEqualizer() {
			ObservableSet<? extends E> wrapped = getWrapped().get();
			return wrapped == null ? Objects::equals : wrapped.getEqualizer();
		}
	}

	/**
	 * Implements {@link ObservableSet#unique(ObservableCollection, Equalizer)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class CollectionWrappingSet<E> implements PartialSetImpl<E> {
		private final ObservableCollection<E> theCollection;
		private final Equalizer theEqualizer;

		public CollectionWrappingSet(ObservableCollection<E> collection, Equalizer equalizer) {
			theCollection = collection;
			theEqualizer = equalizer;
		}

		protected ObservableCollection<E> getWrapped() {
			return theCollection;
		}

		@Override
		public Equalizer getEqualizer() {
			return theEqualizer;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theCollection.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return theCollection.isSafe();
		}

		@Override
		public int size() {
			HashSet<EqualizerNode<E>> set = new HashSet<>();
			for (E value : theCollection)
				set.add(new EqualizerNode<>(theEqualizer, value));
			return set.size();
		}

		@Override
		public Iterator<E> iterator() {
			return unique(theCollection.iterator());
		}

		@Override
		public boolean canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			if (!theCollection.canAdd(value))
				return false;
			HashSet<EqualizerNode<E>> set = new HashSet<>();
			for (E v : theCollection)
				set.add(new EqualizerNode<>(theEqualizer, v));
			return !set.contains(new EqualizerNode<>(theEqualizer, value));
		}

		protected Iterator<E> unique(Iterator<E> backing) {
			return new Iterator<E>() {
				private final HashSet<EqualizerNode<E>> set = new HashSet<>();

				private E nextVal;

				@Override
				public boolean hasNext() {
					while (nextVal == null && backing.hasNext()) {
						nextVal = backing.next();
						if (!set.add(new EqualizerNode<>(theEqualizer, nextVal)))
							nextVal = null;
					}
					return nextVal != null;
				}

				@Override
				public E next() {
					if (nextVal == null && !hasNext())
						throw new java.util.NoSuchElementException();
					E ret = nextVal;
					nextVal = null;
					return ret;
				}

				@Override
				public void remove() {
					backing.remove();
				}
			};
		}

		protected class UniqueElementTracking {
			protected Map<EqualizerNode<E>, UniqueElement<E>> elements = new LinkedHashMap<>();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return onElement(onElement, ObservableCollection::onElement);
		}

		protected Subscription onElement(Consumer<? super ObservableElement<E>> onElement,
			BiFunction<ObservableCollection<E>, Consumer<? super ObservableElement<E>>, Subscription> subscriber) {
			final UniqueElementTracking tracking = createElementTracking();
			return subscriber.apply(theCollection, element -> {
				element.subscribe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <EV extends ObservableValueEvent<E>> void onNext(EV event) {
						EqualizerNode<E> newNode = new EqualizerNode<>(theEqualizer, event.getValue());
						UniqueElement<E> newUnique = tracking.elements.get(newNode);
						if (newUnique == null)
							newUnique = addUniqueElement(tracking, newNode);
						boolean addElement = newUnique.isEmpty();
						boolean reAdd;
						if (event.isInitial()) {
							reAdd = newUnique.addElement(element, event);
						} else {
							EqualizerNode<E> oldNode = new EqualizerNode<>(theEqualizer, event.getOldValue());
							UniqueElement<E> oldUnique = tracking.elements.get(oldNode);
							if (oldUnique == newUnique) {
								reAdd = newUnique.changed(element);
							} else {
								if (oldUnique != null)
									removeFromOld(oldUnique, oldNode, event);
								reAdd = newUnique.addElement(element, event);
							}
						}
						if (addElement)
							onElement.accept(newUnique);
						else if (reAdd) {
							newUnique.reset(event);
							onElement.accept(newUnique);
						}
					}

					@Override
					public <EV extends ObservableValueEvent<E>> void onCompleted(EV event) {
						EqualizerNode<E> node = new EqualizerNode<>(theEqualizer, event.getValue());
						UniqueElement<E> unique = tracking.elements.get(node);
						if (unique != null)
							removeFromOld(unique, node, event);
					}

					void removeFromOld(UniqueElement<E> unique, EqualizerNode<E> node, Object cause) {
						boolean reAdd = unique.removeElement(element, cause);
						if (unique.isEmpty())
							tracking.elements.remove(node);
						else if (reAdd) {
							unique.reset(cause);
							onElement.accept(unique);
						}
					}
				});
			});
		}

		protected UniqueElementTracking createElementTracking() {
			return new UniqueElementTracking();
		}

		protected UniqueElement<E> addUniqueElement(UniqueElementTracking tracking, EqualizerNode<E> node) {
			UniqueElement<E> unique = new UniqueElement<>(this, false);
			tracking.elements.put(node, unique);
			return unique;
		}

		@Override
		public String toString() {
			return ObservableSet.toString(this);
		}
	}

	/**
	 * Implements elements for {@link ObservableSet#unique(ObservableCollection, Equalizer)}
	 *
	 * @param <E> The type of value in the element
	 */
	class UniqueElement<E> implements ObservableElement<E>{
		private final CollectionWrappingSet<E> theSet;
		private final boolean isAlwaysUsingFirst;
		private final Collection<ObservableElement<E>> theElements;
		private final SimpleSettableValue<ObservableElement<E>> theCurrentElement;

		UniqueElement(CollectionWrappingSet<E> set, boolean alwaysUseFirst) {
			theSet = set;
			isAlwaysUsingFirst = alwaysUseFirst;
			theElements = createElements();
			theCurrentElement = new SimpleSettableValue<>(
				new TypeToken<ObservableElement<E>>() {}.where(new TypeParameter<E>() {}, theSet.getType()), true);
		}

		protected Collection<ObservableElement<E>> createElements() {
			return new ArrayDeque<>();
		}

		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}

		@Override
		public E get() {
			return theElements.isEmpty() ? null : theElements.iterator().next().get();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return ObservableElement.flatten(theCurrentElement).subscribe(observer);
		}

		@Override
		public boolean isSafe() {
			return theSet.isSafe();
		}

		@Override
		public ObservableValue<E> persistent() {
			return theElements.isEmpty() ? ObservableValue.constant(theSet.getType(), null) : theElements.iterator().next().persistent();
		}

		protected ObservableElement<E> getCurrentElement() {
			return theCurrentElement.get();
		}

		protected boolean addElement(ObservableElement<E> element, Object cause) {
			theElements.add(element);
			boolean newBest;
			if (isAlwaysUsingFirst)
				newBest = theElements.iterator().next() == element;
			else
				newBest = theCurrentElement.get() == null;
			if (newBest)
				return setCurrentElement(element, cause);
			else
				return false;
		}

		protected boolean removeElement(ObservableElement<E> element, Object cause) {
			theElements.remove(element);
			if (theCurrentElement.get() == element)
				return setCurrentElement(theElements.isEmpty() ? null : theElements.iterator().next(), cause);
			else
				return false;
		}

		protected boolean changed(ObservableElement<E> element) {
			return false;
		}

		protected void reset(Object cause) {
			theCurrentElement.set(theElements.iterator().next(), cause);
		}

		protected boolean setCurrentElement(ObservableElement<E> element, Object cause) {
			theCurrentElement.set(element, cause);
			return false;
		}

		protected boolean isEmpty() {
			return theElements.isEmpty();
		}
	}
}
