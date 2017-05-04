package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.DefaultObservable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection.FilterMapDef;
import org.observe.collect.ObservableCollection.FilterMapResult;
import org.observe.collect.ObservableCollection.ModFilterDef;
import org.observe.collect.ObservableCollection.StdMsg;
import org.observe.util.ObservableUtils;
import org.qommons.Equalizer;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.MultiMap.MultiEntry;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableCollection} methods */
public final class ObservableCollectionImpl {
	private ObservableCollectionImpl() {}

	public static boolean contains(ObservableCollection<?> coll, Object value) {
		try (Transaction t = coll.lock(false, null)) {
			ElementSpliterator<?> iter = coll.spliterator();
			boolean[] found = new boolean[1];
			while (!found[0] && iter.tryAdvance(v -> {
				if (Objects.equals(v, value))
					found[0] = true;
			})) {
			}
			return found[0];
		}
	}

	public static boolean containsAny(ObservableCollection<?> coll, Collection<?> values) {
		try (Transaction t = coll.lock(false, null)) {
			for (Object o : values)
				if (coll.contains(o))
					return true;
		}
		return false;
	}

	public static boolean containsAll(ObservableCollection<?> coll, Collection<?> values) {
		if (values.isEmpty())
			return true;
		ArrayList<Object> copy = new ArrayList<>(values);
		BitSet found = new BitSet(copy.size());
		try (Transaction t = coll.lock(false, null)) {
			ElementSpliterator<?> iter = coll.spliterator();
			boolean[] foundOne = new boolean[1];
			while (iter.tryAdvance(next -> {
				int stop = found.previousClearBit(copy.size());
				for (int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
					if (Objects.equals(next, copy.get(i))) {
						found.set(i);
						foundOne[0] = true;
					}
			})) {
				if (foundOne[0] && found.cardinality() == copy.size()) {
					break;
				}
				foundOne[0] = false;
			}
			return found.cardinality() == copy.size();
		}
	}

	public static <E> boolean addAll(ObservableCollection<E> coll, Collection<? extends E> values) {
		boolean mod = false;
		try (Transaction t = coll.lock(true, null)) {
			for (E o : values)
				mod |= coll.add(o);
		}
		return mod;
	}

	public static boolean removeAll(ObservableCollection<?> coll, Collection<?> values) {
		if (values.isEmpty())
			return false;
		try (Transaction t = coll.lock(true, null)) {
			boolean modified = false;
			Iterator<?> it = coll.iterator();
			while (it.hasNext()) {
				if (values.contains(it.next())) {
					it.remove();
					modified = true;
				}
			}
			return modified;
		}
	}

	public static boolean retainAll(ObservableCollection<?> coll, Collection<?> values) {
		if (values.isEmpty()) {
			coll.clear();
			return false;
		}
		try (Transaction t = coll.lock(true, null)) {
			boolean modified = false;
			Iterator<?> it = coll.iterator();
			while (it.hasNext()) {
				if (!values.contains(it.next())) {
					it.remove();
					modified = true;
				}
			}
			return modified;
		}
	}

	private static class ValueCount<E> {
		final E value;
		int left;
		int right;

		ValueCount(E val) {
			value = val;
		}

		boolean modify(boolean add, boolean lft) {
			boolean modified;
			if (add) {
				if (lft) {
					modified = left == 0;
					left++;
				} else {
					modified = right == 0;
					right++;
				}
			} else {
				if (lft) {
					modified = left == 1;
					left--;
				} else {
					modified = right == 1;
					right--;
				}
			}
			return modified;
		}

		boolean isEmpty() {
			return left == 0 && right == 0;
		}

		@Override
		public String toString() {
			return value + " (" + left + "/" + right + ")";
		}
	}

	public static abstract class ValueCounts<E, X> {
		final Equivalence<? super E> leftEquiv;
		final Equivalence<? super X> rightEquiv;
		final Map<E, ValueCount<E>> leftCounts;
		final Map<X, ValueCount<X>> rightCounts;
		int leftCount;
		int commonCount;
		int rightCount;

		ValueCounts(Equivalence<? super E> leftEquiv, Equivalence<? super X> rightEquiv) {
			this.leftEquiv = leftEquiv;
			this.rightEquiv = rightEquiv;
			leftCounts = leftEquiv.createMap();
			rightCounts = rightEquiv == null ? null : rightEquiv.createMap();
		}

		abstract void check(boolean initial, Object cause);

		public int getLeftCount() {
			return leftCount;
		}

		public int getRightCount() {
			return rightCount;
		}

		public int getCommonCount() {
			return commonCount;
		}
	}

	private static <E, X> Subscription maintainValueCount(ValueCounts<E, X> counts, ObservableCollection<E> left,
		ObservableCollection<X> right) {
		final ReentrantLock lock = new ReentrantLock();
		boolean[] initialized = new boolean[1];
		abstract class ValueCountModifier {
			final void doNotify(Object cause) {
				if (initialized[0] && left.getSession().get() == null && right.getSession().get() == null)
					counts.check(false, cause);
			}
		}
		class ValueCountElModifier extends ValueCountModifier implements Observer<ObservableValueEvent<?>> {
			final boolean onLeft;

			ValueCountElModifier(boolean lft) {
				onLeft = lft;
			}

			@Override
			public <V extends ObservableValueEvent<?>> void onNext(V event) {
				if (event.isInitial() || !Objects.equals(event.getOldValue(), event.getValue())) {
					if (!initialized[0])
						lock.lock();
					try {
						if (event.isInitial())
							modify(event.getValue(), true);
						else if (!Objects.equals(event.getOldValue(), event.getValue())) {
							modify(event.getOldValue(), false);
							modify(event.getValue(), true);
						}
						doNotify(event);
					} finally {
						if (!initialized[0])
							lock.unlock();
					}
				}
			}

			@Override
			public <V extends ObservableValueEvent<?>> void onCompleted(V event) {
				lock.lock();
				try {
					modify(event.getValue(), false);
					doNotify(event);
				} finally {
					lock.unlock();
				}
			}

			private <V> void modify(V value, boolean add) {
				Map<V, ValueCount<V>> countMap;
				boolean leftMap = true;
				if (!onLeft) {
					if (counts.leftEquiv.isElement(value))
						countMap = (Map<V, ValueCount<V>>) (Map<?, ?>) counts.leftCounts;
					else if (counts.rightCounts == null)
						return;
					else {
						countMap = (Map<V, ValueCount<V>>) (Map<?, ?>) counts.rightCounts;
						leftMap = false;
					}
				} else
					countMap = (Map<V, ValueCount<V>>) (Map<?, ?>) counts.leftCounts;
				ValueCount<V> count;
				if (add) {
					boolean[] added = new boolean[1];
					count = countMap.computeIfAbsent(value, v -> {
						added[0] = true;
						return new ValueCount<>(v);
					});
					if (added[0]) {
						count.modify(true, onLeft);
						if (onLeft)
							counts.leftCount++;
						else
							counts.rightCount++;
						return;
					}
				} else {
					count = countMap.get(value);
					if (count == null)
						return;
				}
				if (count.modify(add, onLeft)) {
					if (add) {
						if (onLeft)
							counts.rightCount--;
						else
							counts.leftCount--;
						counts.commonCount++;
					} else if (count.isEmpty()) {
						countMap.remove(value);
						if (onLeft)
							counts.leftCount--;
						else
							counts.rightCount--;
					} else {
						counts.commonCount--;
						if (onLeft)
							counts.rightCount++;
						else
							counts.leftCount++;
					}
				}
			}
		}
		class ValueCountSessModifier extends ValueCountModifier implements Observer<ObservableValueEvent<? extends CollectionSession>> {
			@Override
			public <V extends ObservableValueEvent<? extends CollectionSession>> void onNext(V event) {
				if (initialized[0] && event.getOldValue() != null) {
					if (initialized[0])
						lock.lock();
					try {
						doNotify(event);
					} finally {
						lock.unlock();
					}
				}
			}
		}
		Subscription thisElSub;
		Subscription collElSub;
		Subscription thisSessSub;
		Subscription collSessSub;
		lock.lock();
		try {
			thisElSub = left.onElement(el -> {
				el.subscribe(new ValueCountElModifier(true));
			});
			collElSub = right.onElement(el -> {
				el.subscribe(new ValueCountElModifier(false));
			});

			thisSessSub = left.getSession().subscribe(new ValueCountSessModifier());
			collSessSub = right.getSession().subscribe(new ValueCountSessModifier());

			counts.check(true, null);
		} finally {
			initialized[0] = true;
			lock.unlock();
		}
		return Subscription.forAll(thisElSub, collElSub, thisSessSub, collSessSub);
	}

	public abstract static class IntersectionValue<E, X> implements ObservableValue<Boolean> {
		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final boolean isTrackingRight;
		private final Predicate<ValueCounts<E, X>> theSatisfiedCheck;

		public IntersectionValue(ObservableCollection<E> left, ObservableCollection<X> right, boolean trackRight,
			Predicate<ValueCounts<E, X>> satisfied) {
			theLeft = left;
			theRight = right;
			isTrackingRight = trackRight;
			theSatisfiedCheck = satisfied;
		}

		protected ObservableCollection<E> getLeft() {
			return theLeft;
		}

		protected ObservableCollection<X> getRight() {
			return theRight;
		}

		@Override
		public TypeToken<Boolean> getType() {
			return TypeToken.of(Boolean.TYPE);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
			ValueCounts<E, X> counts = new ValueCounts<E, X>(theLeft.equivalence(), isTrackingRight ? theRight.equivalence() : null) {
				private boolean isSatisfied;

				@Override
				void check(boolean initial, Object cause) {
					boolean satisfied = theSatisfiedCheck.test(this);
					if (initial)
						Observer.onNextAndFinish(observer, createInitialEvent(satisfied, null));
					else if (satisfied != isSatisfied)
						Observer.onNextAndFinish(observer, createChangeEvent(isSatisfied, satisfied, cause));
					isSatisfied = satisfied;
				}
			};
			return maintainValueCount(counts, theLeft, theRight);
		}
	}

	public static class ContainsValue<E, X> extends IntersectionValue<E, X> {
		private final ObservableValue<X> theValue;

		public ContainsValue(ObservableCollection<E> collection, ObservableValue<X> value) {
			super(collection, toCollection(value), false, counts -> counts.getCommonCount() > 0);
			theValue = value;
		}

		private static <T> ObservableCollection<T> toCollection(ObservableValue<T> value) {
			ObservableValue<ObservableCollection<T>> cv = value.mapV(v -> ObservableCollection.constant(value.getType(), v));
			return ObservableCollection.flattenValue(cv);
		}

		@Override
		public Boolean get() {
			return getLeft().contains(theValue.get());
		}
	}

	public static class ContainsAllValue<E, X> extends IntersectionValue<E, X> {
		public ContainsAllValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			super(left, right, true, counts -> counts.getRightCount() == 0);
		}

		@Override
		public Boolean get() {
			return getLeft().containsAll(getRight());
		}
	}

	public static class ContainsAnyValue<E, X> extends IntersectionValue<E, X> {
		public ContainsAnyValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			super(left, right, false, counts -> counts.getCommonCount() > 0);
		}

		@Override
		public Boolean get() {
			return getLeft().containsAny(getRight());
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterMap(FilterMapDef, boolean)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	public static class FilterMappedObservableCollection<E, T> implements ObservableCollection<T> {
		private final ObservableCollection<E> theWrapped;
		private final FilterMapDef<E, ?, T> theDef;
		private final boolean isDynamic;

		protected FilterMappedObservableCollection(ObservableCollection<E> wrap, FilterMapDef<E, ?, T> filterMapDef, boolean dynamic) {
			theWrapped = wrap;
			theDef = filterMapDef;
			isDynamic = dynamic;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected FilterMapDef<E, ?, T> getDef() {
			return theDef;
		}

		@Override
		public TypeToken<T> getType() {
			return theDef.destType;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public int size() {
			if (!theDef.isFiltered())
				return theWrapped.size();

			int[] size = new int[1];
			FilterMapResult<E, T> result = new FilterMapResult<>();
			theWrapped.spliterator().forEachRemaining(v -> {
				result.source = v;
				theDef.checkSourceValue(result);
				if (result.error == null)
					size[0]++;
			});
			return size[0];
		}

		@Override
		public boolean isEmpty() {
			if (theWrapped.isEmpty())
				return true;
			else if (!theDef.isFiltered())
				return false;
			FilterMapResult<E, T> result = new FilterMapResult<>();
			boolean[] contained = new boolean[1];
			while (!contained[0] && theWrapped.spliterator().tryAdvance(v -> {
				result.source = v;
				theDef.checkSourceValue(result);
				if (result.error == null)
					contained[0] = true;
			})) {
			}
			return !contained[0];
		}

		@Override
		public ElementSpliterator<T> spliterator() {
			return map(theWrapped.spliterator());
		}

		@Override
		public boolean contains(Object o) {
			if (!theDef.checkDestType(o))
				return false;
			if (!theDef.isReversible())
				return ObservableCollectionImpl.contains(this, o);
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) o));
			if (reversed.error != null)
				return false;
			return theWrapped.contains(reversed.result);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (!theDef.isReversible() || size() < c.size()) // Try to map the fewest elements
				return ObservableCollectionImpl.containsAll(this, c);

			return theWrapped.containsAll(reverse(c));
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (!theDef.isReversible() || size() < c.size()) // Try to map the fewest elements
				return ObservableCollectionImpl.containsAll(this, c);

			return theWrapped.containsAny(reverse(c));
		}

		protected List<E> reverse(Collection<?> input) {
			FilterMapResult<T, E> reversed = new FilterMapResult<>();
			return input.stream().<E> flatMap(v -> {
				if (!theDef.checkDestType(v))
					return Stream.empty();
				reversed.source = (T) v;
				theDef.reverse(reversed);
				if (reversed.error == null)
					return Stream.of(reversed.result);
				else
					return Stream.empty();
			}).collect(Collectors.toList());
		}

		@Override
		public String canAdd(T value) {
			if (!theDef.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!theDef.checkDestType(value))
				return StdMsg.BAD_TYPE;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
			if (reversed.error != null)
				return reversed.error;
			return theWrapped.canAdd(reversed.result);
		}

		@Override
		public boolean add(T e) {
			if (!theDef.isReversible() || !theDef.checkDestType(e))
				return false;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(e));
			if (reversed.error != null)
				return false;
			return theWrapped.add(reversed.result);
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if (!theDef.isReversible())
				return false;
			return theWrapped.addAll(reverse(c));
		}

		@Override
		public String canRemove(Object value) {
			if (!theDef.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!theDef.checkDestType(value))
				return StdMsg.BAD_TYPE;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) value));
			if (reversed.error != null)
				return reversed.error;
			return theWrapped.canRemove(reversed.result);
		}

		@Override
		public boolean remove(Object o) {
			if (o != null && !theDef.checkDestType(o))
				return false;
			boolean[] found = new boolean[1];
			if (theDef.isReversible()) {
				FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) o));
				if (reversed.error != null)
					return false;
				try (Transaction t = lock(true, null)) {
					while (!found[0] && theWrapped.spliterator().tryAdvanceElement(el -> {
						if (Objects.equals(el.get(), reversed.result)) {
							found[0] = true;
							el.remove();
						}
					})) {
					}
				}
			} else {
				try (Transaction t = lock(true, null)) {
					FilterMapResult<E, T> result = new FilterMapResult<>();
					while (!found[0] && theWrapped.spliterator().tryAdvanceElement(el -> {
						result.source = el.get();
						theDef.map(result);
						if (result.error == null && Objects.equals(result.result, o)) {
							found[0] = true;
							el.remove();
						}
					})) {
					}
				}
			}
			return found[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean[] removed = new boolean[1];
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.map(result);
					if (result.error == null && c.contains(result.result)) {
						el.remove();
						removed[0] = true;
					}
				});
			}
			return removed[0];
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean[] removed = new boolean[1];
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.map(result);
					if (result.error == null && !c.contains(result.result)) {
						el.remove();
						removed[0] = true;
					}
				});
			}
			return removed[0];
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.checkSourceValue(result);
					if (result.error == null)
						el.remove();
				});
			}
		}

		protected ElementSpliterator<T> map(ElementSpliterator<E> iter) {
			return new ElementSpliterator.WrappingSpliterator<>(iter, getType(), () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				FilterMapResult<E, T> mapped = new FilterMapResult<>();
				ElementSpliterator.WrappingElement<E, T> wrapperEl = new ElementSpliterator.WrappingElement<E, T>(getType(), container) {
					@Override
					public T get() {
						return mapped.result;
					}

					@Override
					public <V extends T> String isAcceptable(V value) {
						if (!theDef.isReversible())
							return StdMsg.UNSUPPORTED_OPERATION;
						else if (!theDef.checkDestType(value))
							return StdMsg.BAD_TYPE;
						FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
						if (reversed.error != null)
							return reversed.error;
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reversed.result);
					}

					@Override
					public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
						if (!theDef.isReversible())
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						else if (!theDef.checkDestType(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
						if (reversed.error != null)
							throw new IllegalArgumentException(reversed.error);
						((CollectionElement<E>) getWrapped()).set(reversed.result, cause);
						T old = mapped.result;
						mapped.source = reversed.result;
						mapped.result = value;
						return old;
					}
				};
				return el -> {
					mapped.source = el.get();
					theDef.map(mapped);
					if (mapped.error != null)
						return null;
					container[0] = el;
					return wrapperEl;
				};
			});
		}

		protected FilteredElement<E, T> filter(ObservableElement<E> element) {
			if (isDynamic && theDef.isFiltered()) {
				FilterMapResult<E, T> mapped = getDef().map(new FilterMapResult<>(element.get()));
				return mapped.error == null ? new StaticFilteredElement<>(element, theDef) : null;
			} else {
				return new DynamicFilteredElement<>(element, theDef);
			}
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription elSub = getWrapped().onElement(element -> {
				FilteredElement<E, T> filtered = filter(element);
				if (filtered == null)
					return;
				filtered.isIncluded().value().unsubscribeOn(unSubObs).filter(included -> included)
				.act(included -> onElement.accept(filtered));
			});
			return () -> {
				unSubObs.onNext(null);
				elSub.unsubscribe();
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	public static abstract class FilteredElement<E, T> implements ObservableElement<T> {
		private final ObservableElement<E> theWrappedElement;
		private final FilterMapDef<E, ?, T> theDef;

		/**
		 * @param wrapped The element to wrap
		 * @param def The mapping definition to filter on
		 */
		protected FilteredElement(ObservableElement<E> wrapped, FilterMapDef<E, ?, T> def) {
			theWrappedElement = wrapped;
			theDef = def;
		}

		@Override
		public TypeToken<T> getType() {
			return theDef.destType;
		}

		@Override
		public T get() {
			return theDef.map(new FilterMapResult<>(theWrappedElement.get())).result;
		}

		/** @return The element that this filtered element wraps */
		protected ObservableElement<E> getWrapped() {
			return theWrappedElement;
		}

		/** @return The mapping definition used by this element */
		protected FilterMapDef<E, ?, T> getDef() {
			return theDef;
		}

		@Override
		public String canRemove() {
			return theWrappedElement.canRemove();
		}

		@Override
		public void remove() throws IllegalArgumentException {
			theWrappedElement.remove();
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
			if (!theDef.isReversible())
				throw new IllegalArgumentException(ObservableCollection.StdMsg.UNSUPPORTED_OPERATION);
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>(value));
			if (reversed.error != null)
				throw new IllegalArgumentException(reversed.error);
			E old = getWrapped().set(reversed.result, cause);
			FilterMapResult<E, T> oldRes = getDef().map(new FilterMapResult<>(old));
			if (oldRes.error != null)
				return null;
			return oldRes.result;
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			if (!theDef.isReversible())
				return ObservableCollection.StdMsg.UNSUPPORTED_OPERATION;
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>(value));
			if (reversed.error != null)
				return reversed.error;
			return getWrapped().isAcceptable(reversed.result);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			if (!theDef.isReversible())
				return ObservableValue.constant(ObservableCollection.StdMsg.UNSUPPORTED_OPERATION);
			return theWrappedElement.isEnabled().takeUntil(completed());
		}

		protected abstract ObservableValue<Boolean> isIncluded();

		@Override
		public String toString() {
			return "filter(" + theWrappedElement + ")";
		}

		@Override
		public boolean isSafe() {
			return theWrappedElement.isSafe();
		}
	}

	public static class StaticFilteredElement<E, T> extends FilteredElement<E, T> {
		/**
		 * @param wrapped The element to wrap
		 * @param def The mapping definition to filter on
		 */
		protected StaticFilteredElement(ObservableElement<E> wrapped, FilterMapDef<E, ?, T> def) {
			super(wrapped, def);
		}

		@Override
		protected ObservableValue<Boolean> isIncluded() {
			return ObservableValue.constant(true);
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			return getWrapped().subscribe(new Observer<ObservableValueEvent<E>>() {
				private T oldValue;

				@Override
				public <V2 extends ObservableValueEvent<E>> void onNext(V2 elEvent) {
					FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(elEvent.getValue()));
					if (elEvent.isInitial())
						Observer.onNextAndFinish(observer, createInitialEvent(res.result, elEvent));
					else
						Observer.onNextAndFinish(observer, createChangeEvent(oldValue, res.result, elEvent));
				}

				@Override
				public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elEvent) {
					FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(elEvent.getValue()));
					// Don't fire an event for a result that doesn't belong in the mapped collection
					T value = res.error == null ? res.result : oldValue;
					Observer.onCompletedAndFinish(observer, createChangeEvent(value, value, elEvent));
				}
			});
		}
	}

	/**
	 * The type of elements returned from {@link ObservableCollection#filterMap(Function)}
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element being wrapped
	 */
	public static class DynamicFilteredElement<E, T> extends FilteredElement<E, T> {
		/**
		 * @param wrapped The element to wrap
		 * @param def The mapping definition to filter on
		 */
		protected DynamicFilteredElement(ObservableElement<E> wrapped, FilterMapDef<E, ?, T> def) {
			super(wrapped, def);
		}

		@Override
		protected ObservableValue<Boolean> isIncluded() {
			return getWrapped().mapV(v -> getDef().map(new FilterMapResult<>(v)).error == null, true);
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
			return getWrapped().subscribe(new Observer<ObservableValueEvent<E>>() {
				private T oldValue;

				@Override
				public <V2 extends ObservableValueEvent<E>> void onNext(V2 elEvent) {
					FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(elEvent.getValue()));
					if (res.error != null) { // No longer included
						Observer.onCompletedAndFinish(observer2, createChangeEvent(oldValue, oldValue, elEvent));
						oldValue = null;
					} else{
						if (elEvent.isInitial())
							Observer.onNextAndFinish(observer2, createInitialEvent(res.result, elEvent));
						else
							Observer.onNextAndFinish(observer2, createChangeEvent(oldValue, res.result, elEvent));
						oldValue = res.result;
					}
				}

				@Override
				public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elEvent) {
					FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(elEvent.getValue()));
					// Don't fire an event for a result that doesn't belong in the mapped collection
					T value = res.error == null ? res.result : oldValue;
					Observer.onCompletedAndFinish(observer2, createChangeEvent(value, value, elEvent));
				}
			});
		}
	}

	/**
	 * Implements {@link ObservableCollection#combine(ObservableValue, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	public static class CombinedObservableCollection<E, V> implements ObservableCollection<V> {
		private final ObservableCollection<E> theWrapped;
		private final CombinedCollectionDef<E, V> theDef;

		private final SubCollectionTransactionManager theTransactionManager;

		protected CombinedObservableCollection(ObservableCollection<E> wrap, CombinedCollectionDef<E, V> def) {
			theWrapped = wrap;
			theDef = def;

			theTransactionManager = new SubCollectionTransactionManager(theWrapped,
				Observable.or(def.getArgs().stream().map(arg -> arg.noInit()).collect(Collectors.toList()).toArray(new Observable[0])));
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected CombinedCollectionDef<E, V> getDef() {
			return theDef;
		}

		protected SubCollectionTransactionManager getManager() {
			return theTransactionManager;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theTransactionManager.getSession();
		}

		@Override
		public TypeToken<V> getType() {
			return theDef.targetType;
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			if (o!=null && !theDef.targetType.getRawType().isInstance(o))
				return false;
			if (theDef.getReverse() != null && (o != null || theDef.areNullsReversed()))
				return theWrapped.contains(theDef.getReverse().apply(combineDynamic((V) o)));
			else
				return ObservableCollectionImpl.contains(this, o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (theDef.getReverse() == null || c.size() > size())
				return ObservableCollectionImpl.containsAll(this, c);
			else {
				T value = theValue.get();
				for (Object o : c)
					if (!theDef.targetType.getRawType().isInstance(o))
						return false;
				return theWrapped.containsAll(c.stream().map(o -> theReverse.apply((V) o, value)).collect(Collectors.toList()));
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (theDef.getReverse() == null || c.size() > size())
				return ObservableCollectionImpl.containsAny(this, c);
			else {
				T value = theValue.get();
				for (Object o : c)
					if (!theDef.targetType.getRawType().isInstance(o))
						return false;
				return theWrapped.containsAny(c.stream().map(o -> theReverse.apply((V) o, value)).collect(Collectors.toList()));
			}
		}

		@Override
		public String canAdd(V value) {
			if (theDef.getReverse() != null)
				return theWrapped.canAdd(theReverse.apply(value, theValue.get()));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean add(V e) {
			if (theDef.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else
				return theWrapped.add(theReverse.apply(e, theValue.get()));
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			if (theDef.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else {
				T combineValue = theValue.get();
				return theWrapped.addAll(c.stream().map(o -> theReverse.apply(o, combineValue)).collect(Collectors.toList()));
			}
		}

		@Override
		public ObservableCollection<V> addValues(V... values) {
			if (theDef.getReverse() == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else {
				T combineValue = theValue.get();
				theWrapped.addAll(Arrays.stream(values).map(o -> theReverse.apply(o, combineValue)).collect(Collectors.toList()));
				return this;
			}
		}

		@Override
		public String canRemove(Object value) {
			if (theDef.getReverse() != null && (value == null || theDef.targetType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((V) value, theValue.get()));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean remove(Object o) {
			if (theDef.getReverse() == null) {
				try (Transaction t = lock(true, null)) {
					T combineValue = theValue.get();
					Iterator<E> iter = theWrapped.iterator();
					while (iter.hasNext()) {
						E el = iter.next();
						if (Objects.equals(getMap().apply(el, combineValue), o)) {
							iter.remove();
							return true;
						}
					}
				}
				return false;
			} else if (theDef.targetType.getRawType().isInstance(o)) {
				E reversed = theReverse.apply((V) o, theValue.get());
				return getWrapped().remove(reversed);
			} else
				return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (theDef.getReverse() == null) {
				boolean ret = false;
				try (Transaction t = lock(true, null)) {
					T combineValue = theValue.get();
					Iterator<E> iter = theWrapped.iterator();
					while (iter.hasNext()) {
						E el = iter.next();
						if (c.contains(getMap().apply(el, combineValue))) {
							iter.remove();
							ret = true;
						}
					}
				}
				return ret;
			} else {
				T combined = theValue.get();
				return theWrapped.removeAll(c.stream().filter(o -> theDef.targetType.getRawType().isInstance(o))
					.map(o -> theReverse.apply((V) o, combined)).collect(Collectors.toList()));
			}
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (theDef.getReverse() == null) {
				boolean ret = false;
				try (Transaction t = lock(true, null)) {
					T combineValue = theValue.get();
					Iterator<E> iter = theWrapped.iterator();
					while (iter.hasNext()) {
						E el = iter.next();
						if (!c.contains(getMap().apply(el, combineValue))) {
							iter.remove();
							ret = true;
						}
					}
				}
				return ret;
			} else {
				T combined = theValue.get();
				return theWrapped.retainAll(c.stream().filter(o -> theDef.targetType.getRawType().isInstance(o))
					.map(o -> theReverse.apply((V) o, combined)).collect(Collectors.toList()));
			}
		}

		@Override
		public void clear() {
			getWrapped().clear();
		}

		@Override
		public ElementSpliterator<V> spliterator() {
			return combine(theWrapped.spliterator());
		}

		protected V combine(E value) {
			return theMap.apply(value, theValue.get());
		}

		protected ElementSpliterator<V> combine(ElementSpliterator<E> source) {
			Supplier<Function<CollectionElement<? extends E>, CollectionElement<V>>> elementMap = () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				ElementSpliterator.WrappingElement<E, V> wrapper = new ElementSpliterator.WrappingElement<E, V>(getType(), container) {
					@Override
					public V get() {
						return combine(getWrapped().get());
					}

					@Override
					public <V2 extends V> String isAcceptable(V2 value) {
						if (theDef.getReverse() == null)
							return StdMsg.UNSUPPORTED_OPERATION;
						E reverse = theReverse.apply(value, theValue.get());
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reverse);
					}

					@Override
					public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
						if (theDef.getReverse() == null)
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						E reverse = theReverse.apply(value, theValue.get());
						return theMap.apply(((CollectionElement<E>) getWrapped()).set(reverse, cause), theValue.get());
					}
				};
				return el -> {
					container[0] = el;
					return wrapper;
				};
			};
			return new ElementSpliterator.WrappingSpliterator<>(source, getType(), elementMap);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<V>> onElement) {
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = theTransactionManager.onElement(theWrapped,
				element -> onElement.accept(element.combineV(theMap, theValue).unsubscribeOn(unSubObs)), true);
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}

		protected <T> CombinedValues<T> combineDynamic(T value) {
			return new DynamicCombinedValues<>(value);
		}

		private class DynamicCombinedValues<T> implements CombinedValues<T> {
			private final T theElement;

			DynamicCombinedValues(T element) {
				theElement = element;
			}

			@Override
			public T getElement() {
				return theElement;
			}

			@Override
			public <T> T get(ObservableValue<T> arg) {
				if (!theDef.getArgs().contains(arg))
					throw new IllegalArgumentException("Unrecognized argument value: " + arg);
				return arg.get();
			}
		}
	}

	/**
	 * Implements {@link ObservableCollection#groupBy(Function)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	public static class GroupedMultiMap<K, E> implements ObservableMultiMap<K, E> {
		private final ObservableCollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;
		private final Equalizer theEqualizer;

		private final ObservableSet<K> theKeySet;

		GroupedMultiMap(ObservableCollection<E> wrap, Function<E, K> keyMap, TypeToken<K> keyType, Equalizer equalizer) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType
				: (TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theEqualizer = equalizer;

			ObservableCollection<K> mapped;
			if (theKeyMap != null)
				mapped = theWrapped.map(theKeyMap);
			else
				mapped = (ObservableCollection<K>) theWrapped;
			theKeySet = unique(mapped);
		}

		protected Equalizer getEqualizer() {
			return theEqualizer;
		}

		protected ObservableSet<K> unique(ObservableCollection<K> keyCollection) {
			return ObservableSet.unique(keyCollection, theEqualizer);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<E> get(Object key) {
			return theWrapped.filter(el -> theEqualizer.equals(theKeyMap.apply(el), key) ? null : StdMsg.WRONG_GROUP);
		}

		@Override
		public ObservableSet<? extends ObservableMultiEntry<K, E>> entrySet() {
			return ObservableMultiMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * An entry in a {@link ObservableCollection.GroupedMultiMap}
	 *
	 * @param <K> The key type of the entry
	 * @param <E> The value type of the entry
	 */
	public static class GroupedMultiEntry<K, E> implements ObservableMultiMap.ObservableMultiEntry<K, E> {
		private final K theKey;

		private final Function<E, K> theKeyMap;

		private final ObservableCollection<E> theElements;

		private final Equalizer theEqualizer;

		GroupedMultiEntry(K key, ObservableCollection<E> wrap, Function<E, K> keyMap, Equalizer equalizer) {
			theKey = key;
			theKeyMap = keyMap;
			theEqualizer = equalizer;
			theElements = wrap.filter(el -> equalizer.equals(theKey, theKeyMap.apply(el)) ? null : StdMsg.WRONG_GROUP);
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public TypeToken<E> getType() {
			return theElements.getType();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theElements.onElement(onElement);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theElements.getSession();
		}

		@Override
		public boolean isLockSupported() {
			return theElements.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theElements.lock(write, cause);
		}

		@Override
		public int size() {
			return theElements.size();
		}

		@Override
		public boolean isEmpty() {
			return theElements.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theElements.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theElements.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theElements.containsAny(c);
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return theElements.spliterator();
		}

		@Override
		public String canAdd(E value) {
			return theElements.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theElements.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theElements.remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theElements.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theElements.canRemove(value);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theElements.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theElements.retainAll(c);
		}

		@Override
		public void clear() {
			theElements.clear();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			return o instanceof MultiEntry && theEqualizer.equals(theKey, ((MultiEntry<?, ?>) o).getKey());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public String toString() {
			return getKey() + "=" + ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#groupBy(Function, Comparator)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	public static class GroupedSortedMultiMap<K, E> implements ObservableSortedMultiMap<K, E> {
		private final ObservableCollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;
		private final Comparator<? super K> theCompare;

		private final ObservableSortedSet<K> theKeySet;

		GroupedSortedMultiMap(ObservableCollection<E> wrap, Function<E, K> keyMap, TypeToken<K> keyType, Comparator<? super K> compare) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType
				: (TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theCompare = compare;

			ObservableCollection<K> mapped;
			if (theKeyMap != null)
				mapped = theWrapped.map(theKeyMap);
			else
				mapped = (ObservableCollection<K>) theWrapped;
			theKeySet = unique(mapped);
		}

		@Override
		public Comparator<? super K> comparator() {
			return theCompare;
		}

		protected ObservableSortedSet<K> unique(ObservableCollection<K> keyCollection) {
			return ObservableSortedSet.unique(keyCollection, theCompare);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<E> get(Object key) {
			if (!theKeyType.getRawType().isInstance(key))
				return ObservableList.constant(getValueType());
			return theWrapped.filter(el -> theCompare.compare(theKeyMap.apply(el), (K) key) == 0 ? null : StdMsg.WRONG_GROUP);
		}

		@Override
		public ObservableSortedSet<? extends ObservableSortedMultiEntry<K, E>> entrySet() {
			return ObservableSortedMultiMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#refresh(Observable)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class RefreshingCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theRefresh;

		private final SubCollectionTransactionManager theTransactionManager;

		protected RefreshingCollection(ObservableCollection<E> wrap, Observable<?> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;

			theTransactionManager = new SubCollectionTransactionManager(theWrapped, refresh);
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Observable<?> getRefresh() {
			return theRefresh;
		}

		protected SubCollectionTransactionManager getManager() {
			return theTransactionManager;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theTransactionManager.getSession();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			theTransactionManager.startTransaction(cause);
			return () -> theTransactionManager.endTransaction();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = theTransactionManager.onElement(theWrapped,
				element -> onElement.accept(element.refresh(theRefresh).unsubscribeOn(unSubObs)), true);
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#refreshEach(Function)}
	 *
	 * @param <E> The type of the collection to refresh
	 */
	public static class ElementRefreshingCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;

		private final Function<? super E, Observable<?>> theRefresh;

		protected ElementRefreshingCollection(ObservableCollection<E> wrap, Function<? super E, Observable<?>> refresh) {
			theWrapped = wrap;
			theRefresh = refresh;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, Observable<?>> getRefresh() {
			return theRefresh;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			DefaultObservable<Void> unSubObs = new DefaultObservable<>();
			Observer<Void> unSubControl = unSubObs.control(null);
			Subscription collSub = theWrapped.onElement(element -> onElement.accept(element.refreshForValue(theRefresh, unSubObs)));
			return () -> {
				unSubControl.onCompleted(null);
				collSub.unsubscribe();
			};
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of the collection to control
	 */
	public static class ModFilteredCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final org.observe.collect.ObservableCollection.ModFilterDef<E> theDef;

		public ModFilteredCollection(ObservableCollection<E> wrapped, ModFilterDef<E> def) {
			theWrapped = wrapped;
			theDef = def;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected ModFilterDef<E> getDef() {
			return theDef;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return modFilter(theWrapped.spliterator());
		}

		protected ElementSpliterator<E> modFilter(ElementSpliterator<E> source) {
			return new ElementSpliterator.WrappingSpliterator<>(source, getType(), () -> {
				CollectionElement<E>[] container = new CollectionElement[1];
				ElementSpliterator.WrappingElement<E, E> wrapperEl = new ElementSpliterator.WrappingElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						String s = theDef.attemptRemove(get());
						if (s == null)
							s = theDef.attemptAdd(value);
						if (s == null)
							s = ((CollectionElement<E>) getWrapped()).isAcceptable(value);
						return s;
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						String s = theDef.attemptRemove(get());
						if (s == null)
							s = theDef.attemptAdd(value);
						if (s != null)
							throw new IllegalArgumentException(s);
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
					}

					@Override
					public String canRemove() {
						String s = theDef.attemptRemove(get());
						if (s == null)
							s = getWrapped().canRemove();
						return s;
					}

					@Override
					public void remove() {
						String s = theDef.attemptRemove(get());
						if (s != null)
							throw new IllegalArgumentException(s);
						getWrapped().remove();
					}
				};
				return el -> {
					container[0] = (CollectionElement<E>) el;
					return wrapperEl;
				};
			});
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theWrapped.onElement(element -> onElement.accept(new ModFilteredElement<>(element, theDef)));
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			String s = theDef.attemptAdd(value);
			if (s == null)
				s = theWrapped.canAdd(value);
			return s;
		}

		@Override
		public boolean add(E value) {
			if (theDef.attemptAdd(value) == null)
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theDef.isAddFiltered())
				return theWrapped.addAll(values.stream().filter(v -> theDef.attemptAdd(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.addAll(values);
		}

		@Override
		public ObservableCollection<E> addValues(E... values) {
			if (theDef.isAddFiltered())
				theWrapped.addAll(Arrays.stream(values).filter(v -> theDef.attemptAdd(v) == null).collect(Collectors.toList()));
			else
				theWrapped.addValues(values);
			return this;
		}

		@Override
		public String canRemove(Object value) {
			String s = theDef.attemptRemove(value);
			if (s == null)
				s = theWrapped.canRemove(value);
			return s;
		}

		@Override
		public boolean remove(Object value) {
			if (theDef.attemptRemove(value) == null)
				return theWrapped.remove(value);
			else
				return false;
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			if (theDef.isRemoveFiltered())
				return theWrapped.removeAll(values.stream().filter(v -> theDef.attemptRemove(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.removeAll(values);
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if (!theDef.isRemoveFiltered())
				return theWrapped.retainAll(values);

			boolean[] removed = new boolean[1];
			theWrapped.spliterator().forEachElement(el -> {
				E v = el.get();
				if (!values.contains(v) && theDef.attemptRemove(v) == null) {
					el.remove();
					removed[0] = true;
				}
			});
			return removed[0];
		}

		@Override
		public void clear() {
			if (!theDef.isRemoveFiltered()) {
				theWrapped.clear();
				return;
			}

			theWrapped.spliterator().forEachElement(el -> {
				if (theDef.attemptRemove(el.get()) == null)
					el.remove();
			});
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class ModFilteredElement<E> implements ObservableElement<E> {
		private final ObservableElement<E> theWrapped;
		private final ModFilterDef<E> theDef;

		public ModFilteredElement(ObservableElement<E> wrapped, ModFilterDef<E> def) {
			theWrapped = wrapped;
			theDef = def;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theWrapped.isEnabled();
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			String s = theDef.attemptRemove(get());
			if (s == null)
				s = theDef.attemptAdd(value);
			if (s == null)
				s = theWrapped.isAcceptable(value);
			return s;
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
			String s = theDef.attemptRemove(get());
			if (s == null)
				s = theDef.attemptAdd(value);
			if (s != null)
				throw new IllegalArgumentException(s);
			return theWrapped.set(value, cause);
		}

		@Override
		public String canRemove() {
			String s = theDef.attemptRemove(get());
			if (s == null)
				s = theWrapped.canRemove();
			return s;
		}

		@Override
		public void remove() {
			String s = theDef.attemptRemove(get());
			if (s != null)
				throw new IllegalArgumentException(s);
			theWrapped.remove();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return ObservableUtils.wrap(theWrapped, this, observer);
		}
	}

	public static class CachedObservableCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theUntil;
		private final ReentrantReadWriteLock theLock;
		private final Collection<E> theCache;

		public CachedObservableCollection(ObservableCollection<E> wrapped, Observable<?> until) {
			theWrapped = wrapped;
			theUntil = until;
			theLock = new ReentrantReadWriteLock();
			theCache = createCache();
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Observable<?> getUntil() {
			return theUntil;
		}

		protected Collection<E> createCache() {
			return new ArrayList<>();
		}

		protected Collection<E> getCache() {
			return theCache;
		}

		protected void beginCache() {
			int todo = todo;// TODO
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theCache.size();
		}

		@Override
		public boolean isEmpty() {
			return theCache.isEmpty();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean contains(Object o) {
			Equivalence<? super E> equiv = equivalence();
			if (equiv.equals(Equivalence.DEFAULT))
				return theCache.contains(o);
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				for (E v : theCache) {
					if (equiv.elementEquals(v, o))
						return true;
				}
				return false;
			} finally {
				lock.unlock();
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			Equivalence<? super E> equiv = equivalence();
			if (equiv.equals(Equivalence.DEFAULT))
				return theCache.containsAll(c);
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				for (Object o : c) {
					boolean found = false;
					for (E v : theCache) {
						if (equiv.elementEquals(v, o)) {
							found = true;
							break;
						}
					}
					if (!found)
						return false;
				}
				return true;
			} finally {
				lock.unlock();
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			Equivalence<? super E> equiv = equivalence();
			if (theCache instanceof BetterCollection && equiv.equals(Equivalence.DEFAULT))
				return ((BetterCollection<E>) theCache).containsAny(c);
			Lock lock = theLock.readLock();
			lock.lock();
			try {
				for (Object o : c) {
					for (E v : theCache) {
						if (equiv.elementEquals(v, o)) {
							return true;
						}
					}
				}
				return false;
			} finally {
				lock.unlock();
			}
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Backs {@link ObservableCollection#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class TakenUntilObservableCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<E> theWrapped;
		private final Observable<?> theUntil;
		private final boolean isTerminating;

		public TakenUntilObservableCollection(ObservableCollection<E> wrap, Observable<?> until, boolean terminate) {
			theWrapped = wrap;
			theUntil = until;
			isTerminating = terminate;
		}

		/** @return The collection that this taken until collection wraps */
		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		/** @return The observable that ends this collection */
		protected Observable<?> getUntil() {
			return theUntil;
		}

		/** @return Whether this collection's elements will be removed when the {@link #getUntil() until} observable fires */
		protected boolean isTerminating() {
			return isTerminating;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
		}

		@Override
		public boolean contains(Object o) {
			return theWrapped.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			return theWrapped.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theWrapped.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theWrapped.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theWrapped.retainAll(c);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			// Don't remember now why I'm doing all this bookkeeping instead of just calling onElement(element.takeUntil())
			final Map<ObservableElement<E>, TakenUntilElement<E>> elements = new HashMap<>();
			Subscription[] collSub = new Subscription[] { theWrapped.onElement(element -> {
				TakenUntilElement<E> untilEl = new TakenUntilElement<>(element, isTerminating);
				elements.put(element, untilEl);
				onElement.accept(untilEl);
			}) };
			Subscription untilSub = theUntil.act(v -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				for (TakenUntilElement<E> el : elements.values())
					el.end();
				elements.clear();
			});
			return () -> {
				if (collSub[0] != null)
					collSub[0].unsubscribe();
				collSub[0] = null;
				untilSub.unsubscribe();
			};
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * An element in a {@link TakenUntilObservableCollection}
	 *
	 * @param <E> The type of value in the element
	 */
	public static class TakenUntilElement<E> implements ObservableElement<E> {
		private final ObservableElement<E> theWrapped;
		private final ObservableElement<E> theEndingElement;
		private final SimpleObservable<Void> theTerminator;
		private final boolean isTerminating;

		public TakenUntilElement(ObservableElement<E> wrap, boolean terminate) {
			theWrapped = wrap;
			isTerminating = terminate;
			theTerminator = new SimpleObservable<>();
			if (isTerminating)
				theEndingElement = theWrapped.takeUntil(theTerminator);
			else
				theEndingElement = theWrapped.unsubscribeOn(theTerminator);
		}

		/** @return The element that this element wraps */
		protected ObservableElement<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return theEndingElement.isEnabled();
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
			return theEndingElement.set(value, cause);
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			return theEndingElement.isAcceptable(value);
		}

		@Override
		public String canRemove() {
			return theEndingElement.canRemove();
		}

		@Override
		public void remove() throws IllegalArgumentException {
			theEndingElement.remove();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return theEndingElement.subscribe(observer);
		}

		protected void end() {
			theTerminator.onNext(null);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link ObservableCollection#constant(TypeToken, Collection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class ConstantObservableCollection<E> implements ObservableCollection<E> {
		private final TypeToken<E> theType;
		private final Collection<E> theCollection;

		public ConstantObservableCollection(TypeToken<E> type, Collection<E> collection) {
			theType = type;
			theCollection = collection;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			for (E value : theCollection)
				onElement.accept(new ObservableElement<E>() {
					@Override
					public TypeToken<E> getType() {
						return theType;
					}

					@Override
					public E get() {
						return value;
					}

					@Override
					public String canRemove() {
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void remove() throws IllegalArgumentException {
						throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public ObservableValue<String> isEnabled() {
						return ObservableValue.constant(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
						Observer.onNextAndFinish(observer, createInitialEvent(value, null));
						return () -> {
						};
					}
				});
			return () -> {
			};
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return new ElementSpliterator.SimpleSpliterator<>(theCollection.spliterator(), theType, () -> v -> new CollectionElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return theType;
				}

				@Override
				public E get() {
					return v;
				}

				@Override
				public Value<String> isEnabled() {
					return Value.constant(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canRemove() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void remove() throws IllegalArgumentException {
					throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
				}
			});
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theCollection.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (theCollection.size() < c.size()) {
				for (E v : theCollection)
					if (c.contains(v))
						return true;
			} else {
				for (Object o : c)
					if (theCollection.contains(o))
						return true;
			}
			return false;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public String canAdd(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean add(E e) {
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return false;
		}

		@Override
		public String canRemove(Object value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean remove(Object o) {
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return false;
		}

		@Override
		public void clear() {
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValuesCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<? extends ObservableValue<? extends E>> theCollection;
		private final TypeToken<E> theType;
		private final boolean canAcceptConst;

		protected FlattenedValuesCollection(ObservableCollection<? extends ObservableValue<? extends E>> collection) {
			theCollection = collection;
			theType = (TypeToken<E>) theCollection.getType().resolveType(ObservableValue.class.getTypeParameters()[0]);
			canAcceptConst = theCollection.getType()
				.isAssignableFrom(new TypeToken<ObservableValue.ConstantObservableValue<E>>() {}.where(new TypeParameter<E>() {}, theType));
		}

		/** @return The collection of values that this collection flattens */
		protected ObservableCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
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
		public Equivalence<? super E> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return new ElementSpliterator.WrappingSpliterator<>(theCollection.spliterator(), theType, () -> {
				CollectionElement<ObservableValue<? extends E>>[] container = new CollectionElement[1];
				ElementSpliterator.WrappingElement<ObservableValue<? extends E>, E> wrapperEl;
				wrapperEl = new ElementSpliterator.WrappingElement<ObservableValue<? extends E>, E>(getType(), container) {
					@Override
					public E get() {
						ObservableValue<? extends E> value = getWrapped().get();
						return value == null ? null : value.get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						ObservableValue<? extends E> obValue = getWrapped().get();
						if (!(obValue instanceof SettableValue))
							return StdMsg.UNSUPPORTED_OPERATION;
						if (value != null && !obValue.getType().getRawType().isInstance(value))
							return StdMsg.BAD_TYPE;
						return ((SettableValue<E>) obValue).isAcceptable(value);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						ObservableValue<? extends E> obValue = getWrapped().get();
						if (!(obValue instanceof SettableValue))
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						if (value != null && !obValue.getType().getRawType().isInstance(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						return ((SettableValue<E>) obValue).set(value, cause);
					}
				};
				return el -> {
					container[0] = (CollectionElement<ObservableValue<? extends E>>) el;
					return wrapperEl;
				};
			});
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.stream().map(v -> v == null ? null : v.get()).anyMatch(v -> Objects.equals(v, o));
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			try (Transaction t = lock(false, null)) {
				return c.stream().allMatch(this::contains);
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			return theCollection.stream().map(v -> v == null ? null : v.get()).anyMatch(c::contains);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return theCollection.onElement(element -> observer.accept(createFlattenedElement(element)));
		}

		protected FlattenedValueElement<E> createFlattenedElement(ObservableElement<? extends ObservableValue<? extends E>> element) {
			return new FlattenedValueElement<>(element, theType);
		}

		@Override
		public String canAdd(E value) {
			if (!canAcceptConst)
				return StdMsg.UNSUPPORTED_OPERATION;
			if (value != null && !theType.getRawType().isInstance(value))
				return StdMsg.BAD_TYPE;
			return ((ObservableCollection<ObservableValue<E>>) theCollection).canAdd(ObservableValue.constant(theType, value));
		}

		@Override
		public boolean add(E e) {
			if (!canAcceptConst)
				return false;
			if (e != null && !theType.getRawType().isInstance(e))
				return false;
			return ((ObservableCollection<ObservableValue<E>>) theCollection).add(ObservableValue.constant(theType, e));
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (!canAcceptConst)
				return false;
			return ((ObservableCollection<ObservableValue<E>>) theCollection)
				.addAll(c.stream().filter(v -> v == null || theType.getRawType().isInstance(v))
					.map(v -> ObservableValue.constant(theType, v)).collect(Collectors.toList()));
		}

		@Override
		public String canRemove(Object value) {
			for (ObservableValue<? extends E> v : theCollection)
				if (Objects.equals(v.get(), value))
					return theCollection.canRemove(v);
			return StdMsg.NOT_FOUND;
		}

		@Override
		public boolean remove(Object o) {
			boolean[] removed = new boolean[1];
			theCollection.spliterator().forEachElement(el -> {
				if (Objects.equals(el.get() == null ? null : el.get().get(), o)) {
					el.remove();
					removed[0] = true;
				}
			});
			return removed[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return ObservableCollectionImpl.removeAll(theCollection, c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return ObservableCollectionImpl.retainAll(theCollection, c);
		}

		@Override
		public void clear() {
			theCollection.clear();
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements elements for {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The type value in the element
	 */
	public static class FlattenedValueElement<E> implements ObservableElement<E> {
		private final ObservableElement<? extends ObservableValue<? extends E>> theWrapped;
		private final TypeToken<E> theType;

		protected FlattenedValueElement(ObservableElement<? extends ObservableValue<? extends E>> wrap, TypeToken<E> type) {
			theWrapped = wrap;
			theType = type;
		}

		protected ObservableElement<? extends ObservableValue<? extends E>> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public E get() {
			return get(theWrapped.get());
		}

		private E get(ObservableValue<? extends E> value) {
			return value == null ? null : value.get();
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.flatten(theWrapped.mapV(value -> value instanceof SettableValue ? ((SettableValue) value).isEnabled()
				: ObservableValue.constant(StdMsg.UNSUPPORTED_OPERATION)));
		}

		@Override
		public <V extends E> String isAcceptable(V value) {
			ObservableValue<? extends E> v = theWrapped.get();
			if (!(v instanceof SettableValue))
				return StdMsg.UNSUPPORTED_OPERATION;
			if (value != null && !v.getType().getRawType().isInstance(value))
				return StdMsg.BAD_TYPE;
			return ((SettableValue<E>) v).isAcceptable(value);
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
			ObservableValue<? extends E> v = theWrapped.get();
			if (!(v instanceof SettableValue))
				throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
			if (value != null && !v.getType().getRawType().isInstance(value))
				throw new IllegalArgumentException(StdMsg.BAD_TYPE);
			return ((SettableValue<E>) v).set(value, cause);
		}

		@Override
		public String canRemove() {
			return theWrapped.canRemove();
		}

		@Override
		public void remove() throws IllegalArgumentException {
			theWrapped.remove();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer2) {
			ObservableElement<E> retObs = this;
			return theWrapped.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends E>>>() {
				@Override
				public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends E>>> void onNext(V2 value) {
					if (value.getValue() != null) {
						Observable<?> until = ObservableUtils.makeUntil(theWrapped, value);
						value.getValue().takeUntil(until).act(innerEvent -> {
							Observer.onNextAndFinish(observer2, ObservableUtils.wrap(innerEvent, retObs));
						});
					} else if (value.isInitial())
						Observer.onNextAndFinish(observer2, retObs.createInitialEvent(null, value.getCause()));
					else
						Observer.onNextAndFinish(observer2, retObs.createChangeEvent(get(value.getOldValue()), null, value.getCause()));
				}

				@Override
				public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends E>>> void onCompleted(V2 value) {
					if (value.isInitial())
						Observer.onCompletedAndFinish(observer2, retObs.createInitialEvent(get(value.getValue()), value.getCause()));
					else
						Observer.onCompletedAndFinish(observer2,
							retObs.createChangeEvent(get(value.getOldValue()), get(value.getValue()), value.getCause()));
				}
			});
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValueCollection<E> implements ObservableCollection<E> {
		private final ObservableValue<? extends ObservableCollection<E>> theCollectionObservable;
		private final TypeToken<E> theType;

		protected FlattenedValueCollection(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
			theCollectionObservable = collectionObservable;
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
		}

		protected ObservableValue<? extends ObservableCollection<E>> getWrapped() {
			return theCollectionObservable;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Transaction.NONE : coll.lock(write, cause);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return ObservableValue.flatten(theCollectionObservable.mapV(coll -> coll.getSession()));
		}

		@Override
		public Equivalence<? super E> equivalence() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Equivalence.DEFAULT : (Equivalence<? super E>) coll.equivalence();
		}

		@Override
		public int size() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? 0 : coll.size();
		}

		@Override
		public boolean isEmpty() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? true : coll.isEmpty();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? ElementSpliterator.empty(theType) : coll.spliterator();
		}

		@Override
		public boolean contains(Object o) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.containsAny(c);
		}

		@Override
		public String canAdd(E value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canAdd(value);
		}

		@Override
		public boolean add(E e) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.addAll(c);
		}

		@Override
		public String canRemove(Object value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			ObservableCollection<E> coll = theCollectionObservable.get();
			return coll == null ? false : coll.retainAll(c);
		}

		@Override
		public void clear() {
			ObservableCollection<E> coll = theCollectionObservable.get();
			if (coll != null)
				coll.clear();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = theCollectionObservable
				.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<? extends E>>>() {
					@Override
					public <V extends ObservableValueEvent<? extends ObservableCollection<? extends E>>> void onNext(V event) {
						if (event.getValue() != null) {
							Observable<?> until = ObservableUtils.makeUntil(theCollectionObservable, event);
							((ObservableCollection<E>) event.getValue().takeUntil(until).unsubscribeOn(unSubObs)).onElement(onElement);
						}
					}
				});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#flatten(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedObservableCollection<E> implements ObservableCollection<E> {
		private final ObservableCollection<? extends ObservableCollection<? extends E>> theOuter;
		private final TypeToken<E> theType;
		private final CombinedCollectionSessionObservable theSession;

		protected FlattenedObservableCollection(ObservableCollection<? extends ObservableCollection<? extends E>> collection) {
			theOuter = collection;
			theType = (TypeToken<E>) theOuter.getType().resolveType(ObservableCollection.class.getTypeParameters()[0]);
			theSession = new CombinedCollectionSessionObservable(theOuter);
		}

		protected ObservableCollection<? extends ObservableCollection<? extends E>> getOuter() {
			return theOuter;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theSession;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return CombinedCollectionSessionObservable.lock(theOuter, write, cause);
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public int size() {
			int ret = 0;
			for (ObservableCollection<? extends E> subColl : theOuter)
				ret += subColl.size();
			return ret;
		}

		@Override
		public boolean isEmpty() {
			for (ObservableCollection<? extends E> subColl : theOuter)
				if (!subColl.isEmpty())
					return false;
			return true;
		}

		@Override
		public boolean contains(Object o) {
			for (ObservableCollection<? extends E> subColl : theOuter)
				if (subColl.contains(o))
					return true;
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return ObservableCollectionImpl.containsAll(this, c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			for (ObservableCollection<? extends E> subColl : theOuter)
				if (subColl.containsAny(c))
					return true;
			return false;
		}

		@Override
		public String canAdd(E value) {
			String msg = null;
			for (ObservableCollection<? extends E> coll : theOuter) {
				if (value != null && !coll.getType().getRawType().isInstance(value))
					continue;
				String collMsg = ((ObservableCollection<E>) coll).canAdd(value);
				if (collMsg == null)
					return null;
				if (msg == null)
					msg = collMsg;
			}
			return msg;
		}

		@Override
		public boolean add(E e) {
			for (ObservableCollection<? extends E> coll : theOuter)
				if ((e == null || coll.getType().getRawType().isInstance(e)) && ((ObservableCollection<E>) coll).add(e))
					return true;
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return ObservableCollectionImpl.addAll(this, c);
		}

		@Override
		public String canRemove(Object value) {
			String msg = null;
			for (ObservableCollection<? extends E> coll : theOuter) {
				String collMsg = ((ObservableCollection<E>) coll).canRemove(value);
				if (collMsg == null)
					return null;
				if (msg == null)
					msg = collMsg;
			}
			if (msg == null)
				return StdMsg.NOT_FOUND;
			return msg;
		}

		@Override
		public boolean remove(Object o) {
			for (ObservableCollection<? extends E> coll : theOuter)
				if (coll.remove(o))
					return true;
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean removed = false;
			for (ObservableCollection<? extends E> coll : theOuter)
				removed |= coll.removeAll(c);
			return removed;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean removed = false;
			for (ObservableCollection<? extends E> coll : theOuter)
				removed |= coll.retainAll(c);
			return removed;
		}

		@Override
		public void clear() {
			for (ObservableCollection<? extends E> coll : theOuter)
				coll.clear();
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return wrap(theOuter.spliterator(), ObservableCollection::spliterator);
		}

		protected ElementSpliterator<E> wrap(ElementSpliterator<? extends ObservableCollection<? extends E>> outer,
			Function<ObservableCollection<? extends E>, ElementSpliterator<? extends E>> innerSplit) {
			return new ElementSpliterator<E>() {
				private WrappingSpliterator<E, E> theInnerator;
				private Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> theElementMap;
				private boolean isSplit;

				{
					theElementMap = () -> {
						CollectionElement<? extends E>[] container = new CollectionElement[1];
						WrappingElement<E, E> wrapper = new WrappingElement<E, E>(getType(), container) {
							@Override
							public E get() {
								return getWrapped().get();
							}

							@Override
							public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
								if (!getWrapped().getType().getRawType().isInstance(value))
									throw new IllegalArgumentException(StdMsg.BAD_TYPE);
								return ((CollectionElement<E>) getWrapped()).set(value, cause);
							}

							@Override
							public <V extends E> String isAcceptable(V value) {
								if (!getWrapped().getType().getRawType().isInstance(value))
									return StdMsg.BAD_TYPE;
								return ((CollectionElement<E>) getWrapped()).isAcceptable(value);
							}
						};
						return el -> {
							container[0] = el;
							return wrapper;
						};
					};
				}

				@Override
				public TypeToken<E> getType() {
					return theType;
				}

				@Override
				public long estimateSize() {
					return size();
				}

				@Override
				public int characteristics() {
					return Spliterator.SIZED;
				}

				@Override
				public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
					if (theInnerator == null && !outer
						.tryAdvance(coll -> theInnerator = new WrappingSpliterator<>(innerSplit.apply(coll), theType, theElementMap)))
						return false;
					while (!theInnerator.tryAdvanceElement(action)) {
						if (!outer
							.tryAdvance(coll -> theInnerator = new WrappingSpliterator<>(innerSplit.apply(coll), theType, theElementMap)))
							return false;
					}
					return true;
				}

				@Override
				public void forEachElement(Consumer<? super CollectionElement<E>> action) {
					try (Transaction t = isSplit ? Transaction.NONE : theOuter.lock(false, null)) { // Won't modify the outer
						outer.forEachRemaining(coll -> {
							new WrappingSpliterator<>(innerSplit.apply(coll), theType, theElementMap).forEachElement(action);
						});
					}
				}

				@Override
				public ElementSpliterator<E> trySplit() {
					ElementSpliterator<E>[] ret = new ElementSpliterator[1];
					isSplit |= outer.tryAdvance(coll -> {
						ret[0] = new WrappingSpliterator<>(innerSplit.apply(coll), theType, theElementMap);
					});
					return ret[0];
				}
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = theOuter.onElement(element -> ObservableCollection
				.flattenValue((ObservableValue<ObservableCollection<E>>) element).unsubscribeOn(unSubObs).onElement(observer));
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}
}
