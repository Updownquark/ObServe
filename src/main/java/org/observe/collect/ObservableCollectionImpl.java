package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
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
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ElementSpliterator.WrappingElement;
import org.observe.collect.ElementSpliterator.WrappingQuiterator;
import org.observe.collect.ObservableCollection.FilterMapDef;
import org.observe.collect.ObservableCollection.FilterMapResult;
import org.observe.collect.ObservableCollection.StdMsg;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.ObservableUtils;
import org.qommons.Equalizer;
import org.qommons.IterableUtils;
import org.qommons.ListenerSet;
import org.qommons.Transaction;
import org.qommons.collect.MultiMap.MultiEntry;

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

	/**
	 * An iterator backed by an {@link ElementSpliterator}
	 *
	 * @param <E> The type of elements to iterate over
	 */
	public static class SpliteratorBetterator<E> implements Betterator<E> {
		private final ElementSpliterator<E> theSpliterator;

		private boolean isNextCached;
		private boolean isDone;
		private CollectionElement<? extends E> cachedNext;

		public SpliteratorBetterator(ElementSpliterator<E> spliterator) {
			theSpliterator = spliterator;
		}

		@Override
		public boolean hasNext() {
			cachedNext = null;
			if (!isNextCached && !isDone) {
				if (theSpliterator.tryAdvanceElement(element -> {
					cachedNext = element;
				}))
					isNextCached = true;
				else
					isDone = true;
			}
			return isNextCached;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			return cachedNext.get();
		}

		@Override
		public String canRemove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("canRemove() must be called after next() and before the next call to hasNext()");
			return cachedNext.canRemove();
		}

		@Override
		public void remove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("remove() must be called after next() and before the next call to hasNext()");
			cachedNext.remove();
			cachedNext = null;
		}

		@Override
		public String isAcceptable(E value) {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("isAcceptable() must be called after next() and before the next call to hasNext()");
			if (!cachedNext.getType().getRawType().isInstance(value))
				return StdMsg.BAD_TYPE;
			return ((CollectionElement<E>) cachedNext).isAcceptable(value);
		}

		@Override
		public E set(E value, Object cause) {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("set() must be called after next() and before the next call to hasNext()");
			if (!cachedNext.getType().getRawType().isInstance(value))
				throw new IllegalStateException(StdMsg.BAD_TYPE);
			return ((CollectionElement<E>) cachedNext).set(value, cause);
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			if (isNextCached)
				action.accept(next());
			cachedNext = null;
			isDone = true;
			theSpliterator.forEachRemaining(action);
		}
	}

	public static class IntersectionCollection<E, X> implements ObservableCollection<E> {
		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;

		public IntersectionCollection(ObservableCollection<E> left, ObservableCollection<X> right) {
			theLeft = left;
			theRight = right;
		}

		@Override
		public TypeToken<E> getType() {
			return theLeft.getType();
		}

		@Override
		public int size() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public boolean isEmpty() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean contains(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean add(E e) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean remove(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void clear() {
			// TODO Auto-generated method stub

		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean isSafe() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public String canAdd(E value) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public String canRemove(Object value) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

	}

	public static class ContainsAllValue<E, X> implements ObservableValue<Boolean> {
		static class ValueCount {
			final Object value;
			int left;
			int right;
			boolean satisfied = true;

			ValueCount(Object val) {
				value = val;
			}

			int modify(boolean add, boolean lft, int unsatisfied) {
				if (add) {
					if (lft)
						left++;
					else
						right++;
				} else {
					if (lft)
						left--;
					else
						right--;
				}
				if (satisfied) {
					if (!checkSatisfied()) {
						satisfied = false;
						return unsatisfied + 1;
					}
				} else if (unsatisfied > 0) {
					if (checkSatisfied()) {
						satisfied = true;
						return unsatisfied - 1;
					}
				}
				return unsatisfied;
			}

			private boolean checkSatisfied() {
				return left > 0 || right == 0;
			}

			boolean isEmpty() {
				return left == 0 && right == 0;
			}

			@Override
			public String toString() {
				return value + " (" + left + "/" + right + ")";
			}
		}

		private final ObservableCollection<E> theLeft;
		private final ObservableCollection<X> theRight;

		public ContainsAllValue(ObservableCollection<E> left, ObservableCollection<X> right) {
			theLeft = left;
			theRight = right;
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
		public Boolean get() {
			return theLeft.containsAll(theRight);
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<Boolean>> observer) {
			Map<Object, ValueCount> allValueCounts = new HashMap<>();
			final int[] unsatisfied = new int[1];
			final int[] transUnsatisfied = new int[1];
			final boolean[] init = new boolean[] { true };
			final ReentrantLock lock = new ReentrantLock();
			abstract class ValueCountModifier {
				final void doNotify(Object cause) {
					if (init[0] || (transUnsatisfied[0] > 0) == (unsatisfied[0] > 0))
						return; // Still (un)satisfied, no change
					if (theLeft.getSession().get() == null && theRight.getSession().get() == null) {
						Observer.onNextAndFinish(observer, createChangeEvent(unsatisfied[0] == 0, transUnsatisfied[0] == 0, cause));
						unsatisfied[0] = transUnsatisfied[0];
					}
				}
			}
			class ValueCountElModifier extends ValueCountModifier implements Observer<ObservableValueEvent<?>> {
				final boolean left;

				ValueCountElModifier(boolean lft) {
					left = lft;
				}

				@Override
				public <V extends ObservableValueEvent<?>> void onNext(V event) {
					if (event.isInitial() || !Objects.equals(event.getOldValue(), event.getValue())) {
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

				private void modify(Object value, boolean add) {
					ValueCount count;
					if (add)
						count = allValueCounts.computeIfAbsent(value, v -> new ValueCount(v));
					else {
						count = allValueCounts.get(value);
						if (count == null)
							return;
					}
					transUnsatisfied[0] = count.modify(add, left, transUnsatisfied[0]);
					if (!add && count.isEmpty())
						allValueCounts.remove(value);
				}
			}
			class ValueCountSessModifier extends ValueCountModifier implements Observer<ObservableValueEvent<? extends CollectionSession>> {
				@Override
				public <V extends ObservableValueEvent<? extends CollectionSession>> void onNext(V event) {
					if (event.getOldValue() != null) {
						lock.lock();
						try {
							doNotify(event);
						} finally {
							lock.unlock();
						}
					}
				}
			}
			Subscription thisElSub = theLeft.onElement(el -> {
				el.subscribe(new ValueCountElModifier(true));
			});
			Subscription collElSub = theRight.onElement(el -> {
				el.subscribe(new ValueCountElModifier(false));
			});
			Subscription thisSessSub = theLeft.getSession().subscribe(new ValueCountSessModifier());
			Subscription collSessSub = theRight.getSession().subscribe(new ValueCountSessModifier());
			// Fire initial event
			lock.lock();
			try {
				unsatisfied[0] = transUnsatisfied[0];
				Observer.onNextAndFinish(observer, createInitialEvent(unsatisfied[0] == 0, null));
				init[0] = false;
			} finally {
				lock.unlock();
			}
			return Subscription.forAll(thisElSub, collElSub, thisSessSub, collSessSub);
		}
	};

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
		public boolean isSafe() {
			return theWrapped.isSafe();
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
			return new WrappingQuiterator<>(iter, getType(), () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				FilterMapResult<E, T> mapped = new FilterMapResult<>();
				WrappingElement<E, T> wrapperEl = new WrappingElement<E, T>(getType(), container) {
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
	 * Implements {@link ObservableCollection#safe()}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class SafeObservableCollection<E> extends ObservableCollectionWrapper<E> {
		private final ReentrantLock theLock;

		protected SafeObservableCollection(ObservableCollection<E> wrap) {
			super(wrap);
			theLock = new ReentrantLock();
		}

		protected ReentrantLock getLock() {
			return theLock;
		}

		@Override
		public TypeToken<E> getType() {
			return getWrapped().getType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return new ObservableValue<CollectionSession>() {
				@Override
				public TypeToken<CollectionSession> getType() {
					return getWrapped().getSession().getType();
				}

				@Override
				public CollectionSession get() {
					return getWrapped().getSession().get();
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<CollectionSession>> observer) {
					ObservableValue<CollectionSession> sessionObservable = this;
					return getWrapped().getSession().subscribe(new Observer<ObservableValueEvent<CollectionSession>>() {
						@Override
						public <V extends ObservableValueEvent<CollectionSession>> void onNext(V event) {
							theLock.lock();
							try {
								Observer.onNextAndFinish(observer, ObservableUtils.wrap(event, sessionObservable));
							} finally {
								theLock.unlock();
							}
						}

						@Override
						public <V extends ObservableValueEvent<CollectionSession>> void onCompleted(V event) {
							theLock.lock();
							try {
								Observer.onCompletedAndFinish(observer, ObservableUtils.wrap(event, sessionObservable));
							} finally {
								theLock.unlock();
							}
						}
					});
				}
			};
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getWrapped().lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return true;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return getWrapped().onElement(element -> {
				theLock.lock();
				try {
					onElement.accept(wrapElement(element));
				} finally {
					theLock.unlock();
				}
			});
		}

		protected ObservableElement<E> wrapElement(ObservableElement<E> wrap) {
			return new ObservableElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return wrap.getType();
				}

				@Override
				public E get() {
					return wrap.get();
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					return wrap.set(value, cause);
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					return wrap.isAcceptable(value);
				}

				@Override
				public String canRemove() {
					return wrap.canRemove();
				}

				@Override
				public void remove() throws IllegalArgumentException {
					wrap.remove();
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return wrap.isEnabled().safe();
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
					ObservableElement<E> wrapper = this;
					return wrap.subscribe(new Observer<ObservableValueEvent<E>>() {
						@Override
						public <V extends ObservableValueEvent<E>> void onNext(V event) {
							theLock.lock();
							try {
								Observer.onNextAndFinish(observer, ObservableUtils.wrap(event, wrapper));
							} finally {
								theLock.unlock();
							}
						}

						@Override
						public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
							theLock.lock();
							try {
								Observer.onCompletedAndFinish(observer, ObservableUtils.wrap(event, wrapper));
							} finally {
								theLock.unlock();
							}
						}
					});
				}
			};
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * Implements {@link ObservableCollection#combine(ObservableValue, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	public static class CombinedObservableCollection<E, T, V> implements ObservableCollection<V> {
		private final ObservableCollection<E> theWrapped;

		private final TypeToken<V> theType;
		private final ObservableValue<T> theValue;
		private final BiFunction<? super E, ? super T, V> theMap;
		private final BiFunction<? super V, ? super T, E> theReverse;

		private final SubCollectionTransactionManager theTransactionManager;

		protected CombinedObservableCollection(ObservableCollection<E> wrap, TypeToken<V> type, ObservableValue<T> value,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			theWrapped = wrap;
			theType = type;
			theValue = value;
			theMap = map;
			theReverse = reverse;

			theTransactionManager = new SubCollectionTransactionManager(theWrapped, value.noInit());
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected ObservableValue<T> getValue() {
			return theValue;
		}

		protected BiFunction<? super E, ? super T, V> getMap() {
			return theMap;
		}

		protected BiFunction<? super V, ? super T, E> getReverse() {
			return theReverse;
		}

		protected SubCollectionTransactionManager getManager() {
			return theTransactionManager;
		}

		@Override
		public boolean isSafe() {
			return false;
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
			return theType;
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
			if (!theType.getRawType().isInstance(o))
				return false;
			if (theReverse != null)
				return theWrapped.contains(theReverse.apply((V) o, theValue.get()));
			else
				return ObservableCollectionImpl.contains(this, o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (theReverse == null || c.size() > size())
				return ObservableCollectionImpl.containsAll(this, c);
			else {
				T value = theValue.get();
				for (Object o : c)
					if (!theType.getRawType().isInstance(o))
						return false;
				return theWrapped.containsAll(c.stream().map(o -> theReverse.apply((V) o, value)).collect(Collectors.toList()));
			}
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (theReverse == null || c.size() > size())
				return ObservableCollectionImpl.containsAny(this, c);
			else {
				T value = theValue.get();
				for (Object o : c)
					if (!theType.getRawType().isInstance(o))
						return false;
				return theWrapped.containsAny(c.stream().map(o -> theReverse.apply((V) o, value)).collect(Collectors.toList()));
			}
		}

		@Override
		public String canAdd(V value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value, theValue.get()));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean add(V e) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else
				return theWrapped.add(theReverse.apply(e, theValue.get()));
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else {
				T combineValue = theValue.get();
				return theWrapped.addAll(c.stream().map(o -> theReverse.apply(o, combineValue)).collect(Collectors.toList()));
			}
		}

		@Override
		public ObservableCollection<V> addValues(V... values) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else {
				T combineValue = theValue.get();
				theWrapped.addAll(Arrays.stream(values).map(o -> theReverse.apply(o, combineValue)).collect(Collectors.toList()));
				return this;
			}
		}

		@Override
		public String canRemove(Object value) {
			if (theReverse != null && (value == null || theType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((V) value, theValue.get()));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public boolean remove(Object o) {
			if (theReverse == null) {
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
			} else if (theType.getRawType().isInstance(o)) {
				E reversed = theReverse.apply((V) o, theValue.get());
				return getWrapped().remove(reversed);
			} else
				return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (theReverse == null) {
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
				return theWrapped.removeAll(c.stream().filter(o -> theType.getRawType().isInstance(o))
					.map(o -> theReverse.apply((V) o, combined)).collect(Collectors.toList()));
			}
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (theReverse == null) {
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
				return theWrapped.retainAll(c.stream().filter(o -> theType.getRawType().isInstance(o))
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
				WrappingElement<E, V> wrapper = new WrappingElement<E, V>(getType(), container) {
					@Override
					public V get() {
						return combine(getWrapped().get());
					}

					@Override
					public <V2 extends V> String isAcceptable(V2 value) {
						if (theReverse == null)
							return StdMsg.UNSUPPORTED_OPERATION;
						E reverse = theReverse.apply(value, theValue.get());
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reverse);
					}

					@Override
					public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
						if (theReverse == null)
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
			return new WrappingQuiterator<>(source, getType(), elementMap);
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
		public boolean isSafe() {
			return theWrapped.isSafe();
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
		public boolean isSafe() {
			return theElements.isSafe();
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
		public boolean isSafe() {
			return theWrapped.isSafe();
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
		public Iterator<E> iterator() {
			return theWrapped.iterator();
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
		public boolean isSafe() {
			return false;
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
		public Iterator<E> iterator() {
			return theWrapped.iterator();
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
		public boolean isSafe() {
			return false;
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

		private final Function<? super E, String> theRemoveFilter;
		private final Function<? super E, String> theAddFilter;

		public ModFilteredCollection(ObservableCollection<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			theWrapped = wrapped;
			theRemoveFilter = removeFilter;
			theAddFilter = addFilter;
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, String> getRemoveFilter() {
			return theRemoveFilter;
		}

		protected Function<? super E, String> getAddFilter() {
			return theAddFilter;
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
			return new WrappingQuiterator<>(source, getType(), () -> {
				CollectionElement<E>[] container = new CollectionElement[1];
				WrappingElement<E, E> wrapperEl = new WrappingElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						String s = null;
						if (theAddFilter != null)
							s = theAddFilter.apply(value);
						if (s == null)
							s = ((CollectionElement<E>) getWrapped()).isAcceptable(value);
						return s;
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						if (theAddFilter != null) {
							String s = theAddFilter.apply(value);
							if (s != null)
								throw new IllegalArgumentException(s);
						}
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
					}

					@Override
					public String canRemove() {
						String s = null;
						if (theRemoveFilter != null)
							s = theRemoveFilter.apply(get());
						if (s == null)
							s = getWrapped().canRemove();
						return s;
					}

					@Override
					public void remove() {
						if (theRemoveFilter != null) {
							String s = theRemoveFilter.apply(get());
							if (s != null)
								throw new IllegalArgumentException(s);
						}
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
			return theWrapped.onElement(element -> onElement.accept(new ModFilteredElement<>(element, theRemoveFilter, theAddFilter)));
		}

		@Override
		public boolean isSafe() {
			return theWrapped.isSafe();
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
			String s = null;
			if (theAddFilter != null)
				s = theAddFilter.apply(value);
			if (s == null)
				s = theWrapped.canAdd(value);
			return s;
		}

		@Override
		public boolean add(E value) {
			if (theAddFilter == null || theAddFilter.apply(value) == null)
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theAddFilter != null)
				return theWrapped.addAll(values.stream().filter(v -> theAddFilter.apply(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.addAll(values);
		}

		@Override
		public ObservableCollection<E> addValues(E... values) {
			if (theAddFilter != null)
				theWrapped.addAll(Arrays.stream(values).filter(v -> theAddFilter.apply(v) == null).collect(Collectors.toList()));
			else
				theWrapped.addValues(values);
			return this;
		}

		@Override
		public String canRemove(Object value) {
			String s = null;
			if (theRemoveFilter != null) {
				if (value != null && !theWrapped.getType().getRawType().isInstance(value))
					s = StdMsg.BAD_TYPE;
				if (s == null)
					s = theRemoveFilter.apply((E) value);
			}
			if (s == null)
				s = theWrapped.canRemove(value);
			return s;
		}

		@Override
		public boolean remove(Object value) {
			if (theRemoveFilter == null)
				return theWrapped.remove(value);

			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					if (!Objects.equals(next, value))
						continue;
					if (theRemoveFilter.apply(next) == null) {
						iter.remove();
						return true;
					} else
						return false;
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			if (theRemoveFilter == null)
				return theWrapped.removeAll(values);

			BitSet remove = new BitSet();
			int i = 0;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					if (!values.contains(next))
						continue;
					if (theRemoveFilter.apply(next) == null)
						remove.set(i);
					i++;
				}

				if (!remove.isEmpty()) {
					i = 0;
					iter = theWrapped.iterator();
					while (iter.hasNext()) {
						iter.next();
						if (remove.get(i))
							iter.remove();
						i++;
					}
				}
			}
			return !remove.isEmpty();
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if (theRemoveFilter == null)
				return theWrapped.retainAll(values);

			BitSet remove = new BitSet();
			int i = 0;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					if (values.contains(next))
						continue;
					if (theRemoveFilter.apply(next) == null)
						remove.set(i);
					i++;
				}

				if (!remove.isEmpty()) {
					i = 0;
					iter = theWrapped.iterator();
					while (iter.hasNext()) {
						iter.next();
						if (remove.get(i))
							iter.remove();
						i++;
					}
				}
			}
			return !remove.isEmpty();
		}

		@Override
		public void clear() {
			if (theRemoveFilter == null) {
				theWrapped.clear();
				return;
			}

			BitSet remove = new BitSet();
			int i = 0;
			Iterator<E> iter = theWrapped.iterator();
			while (iter.hasNext()) {
				E next = iter.next();
				if (theRemoveFilter.apply(next) == null)
					remove.set(i);
				i++;
			}

			i = 0;
			iter = theWrapped.iterator();
			while (iter.hasNext()) {
				iter.next();
				if (remove.get(i))
					iter.remove();
				i++;
			}
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class ModFilteredElement<E> implements ObservableElement<E> {
		private final ObservableElement<E> theWrapped;
		private final Function<? super E, String> theRemoveFilter;
		private final Function<? super E, String> theAddFilter;

		public ModFilteredElement(ObservableElement<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			theWrapped = wrapped;
			theRemoveFilter = removeFilter;
			theAddFilter = addFilter;
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
			String s = null;
			if (theAddFilter != null)
				s = theAddFilter.apply(value);
			if (s == null)
				s = theWrapped.isAcceptable(value);
			return s;
		}

		@Override
		public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
			if (theAddFilter != null) {
				String s = theAddFilter.apply(value);
				if (s != null)
					throw new IllegalArgumentException(s);
			}
			return theWrapped.set(value, cause);
		}

		@Override
		public String canRemove() {
			String s = null;
			if (theRemoveFilter != null)
				s = theRemoveFilter.apply(get());
			if (s == null)
				s = theWrapped.canRemove();
			return s;
		}

		@Override
		public void remove() {
			if (theRemoveFilter != null) {
				String s = theRemoveFilter.apply(get());
				if (s != null)
					throw new IllegalArgumentException(s);
			}
			theWrapped.remove();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return ObservableUtils.wrap(theWrapped, this, observer);
		}
	}

	/**
	 * Implements {@link ObservableCollection#cached()} Caches the values in an observable collection. As long as this collection is being
	 * listened to, it will maintain a cache of the values in the given collection. When all observers to the collection have been
	 * unsubscribed, the cache is cleared and not maintained. If the cache is active, all access methods to this cache, including the native
	 * {@link Collection} methods, will use the cached values. If the cache is not active, the {@link Collection} methods will delegate to
	 * the wrapped collection.
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class SafeCachedObservableCollection<E> implements ObservableCollection<E> {
		protected static class CachedElement<E> implements ObservableElement<E> {
			private final ObservableElement<E> theWrapped;
			private final ListenerSet<Observer<? super ObservableValueEvent<E>>> theElementListeners;

			private E theCachedValue;

			protected CachedElement(ObservableElement<E> wrap) {
				theWrapped = wrap;
				theElementListeners = new ListenerSet<>();
			}

			protected ObservableElement<E> getWrapped() {
				return theWrapped;
			}

			@Override
			public TypeToken<E> getType() {
				return theWrapped.getType();
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public E get() {
				return theCachedValue;
			}

			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
				theElementListeners.add(observer);
				Observer.onNextAndFinish(observer, createInitialEvent(theCachedValue, null));
				return () -> theElementListeners.remove(observer);
			}

			@Override
			public ObservableValue<E> persistent() {
				return theWrapped.persistent();
			}

			private void newValue(ObservableValueEvent<E> event) {
				E oldValue = theCachedValue;
				theCachedValue = event.getValue();
				ObservableValueEvent<E> cachedEvent = createChangeEvent(oldValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onNext(cachedEvent));
				cachedEvent.finish();
			}

			private void completed(ObservableValueEvent<E> event) {
				ObservableValueEvent<E> cachedEvent = createChangeEvent(theCachedValue, theCachedValue, event);
				theElementListeners.forEach(observer -> observer.onCompleted(cachedEvent));
				cachedEvent.finish();
			}
		}

		private ObservableCollection<E> theWrapped;
		private final ListenerSet<Consumer<? super ObservableElement<E>>> theListeners;
		private final org.qommons.ConcurrentIdentityHashMap<ObservableElement<E>, CachedElement<E>> theCache;
		private final ReentrantLock theLock;
		private final Consumer<ObservableElement<E>> theWrappedOnElement;

		private Subscription theUnsubscribe;

		/** @param wrap The collection to cache */
		protected SafeCachedObservableCollection(ObservableCollection<E> wrap) {
			theWrapped = wrap;
			theListeners = new ListenerSet<>();
			theCache = new org.qommons.ConcurrentIdentityHashMap<>();
			theLock = new ReentrantLock();
			theWrappedOnElement = element -> {
				CachedElement<E> cached = d().debug(createElement(element)).from("element", this).tag("wrapped", element).get();
				d().debug(cached).from("cached", element).from("element", this);
				theCache.put(element, cached);
				element.subscribe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V extends ObservableValueEvent<E>> void onNext(V event) {
						cached.newValue(event);
					}

					@Override
					public <V extends ObservableValueEvent<E>> void onCompleted(V event) {
						cached.completed(event);
						theCache.remove(element);
					}
				});
				theListeners.forEach(onElement -> onElement.accept(cached));
			};

			theListeners.setUsedListener(this::setUsed);
		}

		protected ObservableCollection<E> getWrapped() {
			return theWrapped;
		}

		protected CachedElement<E> createElement(ObservableElement<E> element) {
			return new CachedElement<>(element);
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		protected Subscription addListener(Consumer<? super ObservableElement<E>> onElement) {
			theListeners.add(onElement);
			return () -> theListeners.remove(onElement);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			Subscription ret = addListener(onElement);
			for (CachedElement<E> cached : theCache.values())
				onElement.accept(cached);
			return ret;
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
		public boolean isSafe() {
			return true;
		}

		@Override
		public Iterator<E> iterator() {
			Collection<E> ret = refresh();
			return new Iterator<E>() {
				private final Iterator<E> backing = ret.iterator();

				private E theLastRet;

				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public E next() {
					return theLastRet = backing.next();
				}

				@Override
				public void remove() {
					backing.remove();
					theWrapped.remove(theLastRet);
				}
			};
		}

		@Override
		public int size() {
			Collection<E> ret = refresh();
			return ret.size();
		}

		@Override
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		private void setUsed(boolean used) {
			if (used && theUnsubscribe == null) {
				theLock.lock();
				try {
					theCache.clear();
					theUnsubscribe = theWrapped.onElement(theWrappedOnElement);
				} finally {
					theLock.unlock();
				}
			} else if (!used && theUnsubscribe != null) {
				theUnsubscribe.unsubscribe();
				theUnsubscribe = null;
			}
		}

		protected Collection<E> refresh() {
			// If we're currently caching, then returned the cached values. Otherwise return the dynamic values.
			if (theUnsubscribe != null)
				return cachedElements().stream().map(CachedElement::get).collect(Collectors.toList());
			else
				return theWrapped;
		}

		protected Collection<? extends CachedElement<E>> cachedElements() {
			return theCache.values();
		}

		@Override
		public ObservableCollection<E> cached() {
			return this;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
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
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theWrapped.iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theWrapped.canAdd(value);
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
		public boolean isSafe() {
			return false;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * An element in a {@link ObservableCollection.TakenUntilObservableCollection}
	 *
	 * @param <E> The type of value in the element
	 */
	public static class TakenUntilElement<E> implements ObservableElement<E> {
		private final ObservableElement<E> theWrapped;
		private final ObservableElement<E> theEndingElement;
		private final Observer<Void> theEndControl;
		private final boolean isTerminating;

		public TakenUntilElement(ObservableElement<E> wrap, boolean terminate) {
			theWrapped = wrap;
			isTerminating = terminate;
			DefaultObservable<Void> end = new DefaultObservable<>();
			if (isTerminating)
				theEndingElement = theWrapped.takeUntil(end);
			else
				theEndingElement = theWrapped.unsubscribeOn(end);
			theEndControl = end.control(null);
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
		public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
			return theEndingElement.subscribe(observer);
		}

		@Override
		public ObservableValue<E> persistent() {
			return theWrapped.persistent();
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		protected void end() {
			theEndControl.onNext(null);
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
		public boolean isSafe() {
			return true;
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
					public Subscription subscribe(Observer<? super ObservableValueEvent<E>> observer) {
						Observer.onNextAndFinish(observer, createInitialEvent(value, null));
						return () -> {
						};
					}

					@Override
					public boolean isSafe() {
						return true;
					}

					@Override
					public ObservableValue<E> persistent() {
						return this;
					}
				});
			return () -> {
			};
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public Iterator<E> iterator() {
			return IterableUtils.immutableIterator(theCollection.iterator());
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}
	}

	/**
	 * Implements {@link ObservableCollection#flattenValues(ObservableCollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	public static class FlattenedValuesCollection<E> implements ObservableCollection<E> {
		private ObservableCollection<? extends ObservableValue<? extends E>> theCollection;
		private final TypeToken<E> theType;

		protected FlattenedValuesCollection(ObservableCollection<? extends ObservableValue<? extends E>> collection) {
			theCollection = collection;
			theType = (TypeToken<E>) theCollection.getType().resolveType(ObservableValue.class.getTypeParameters()[0]);
		}

		/** @return The collection of values that this collection flattens */
		protected ObservableCollection<? extends ObservableValue<? extends E>> getWrapped() {
			return theCollection;
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
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public boolean isSafe() {
			return false;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return theCollection.onElement(element -> observer.accept(createFlattenedElement(element)));
		}

		protected FlattenedValueElement<E> createFlattenedElement(ObservableElement<? extends ObservableValue<? extends E>> element) {
			return new FlattenedValueElement<>(element, theType);
		}

		@Override
		public Iterator<E> iterator() {
			return new Iterator<E>() {
				private final Iterator<? extends ObservableValue<? extends E>> wrapped = theCollection.iterator();

				@Override
				public boolean hasNext() {
					return wrapped.hasNext();
				}

				@Override
				public E next() {
					return wrapped.next().get();
				}

				@Override
				public void remove() {
					wrapped.remove();
				}
			};
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
		}

		@Override
		public int size() {
			return theCollection.size();
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

		@Override
		public ObservableValue<E> persistent() {
			return ObservableValue.flatten(theWrapped.persistent());
		}

		private E get(ObservableValue<? extends E> value) {
			return value == null ? null : value.get();
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
		public ObservableValue<CollectionSession> getSession() {
			return ObservableValue.flatten(theCollectionObservable.mapV(coll -> coll.getSession()));
		}

		@Override
		public int size() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? 0 : coll.size();
		}

		@Override
		public Iterator<E> iterator() {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Collections.EMPTY_LIST.iterator() : (Iterator<E>) coll.iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return false;
			return current.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			ObservableCollection<E> current = theCollectionObservable.get();
			if (current == null)
				return false;
			return current.canAdd(value);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			ObservableCollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Transaction.NONE : coll.lock(write, cause);
		}

		@Override
		public boolean isSafe() {
			return false;
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
		public boolean isSafe() {
			return false;
		}

		@Override
		public int size() {
			int ret = 0;
			for (ObservableCollection<? extends E> subColl : theOuter)
				ret += subColl.size();
			return ret;
		}

		@Override
		public Iterator<E> iterator() {
			return (Iterator<E>) IterableUtils.flatten(theOuter).iterator();
		}

		@Override
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
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
