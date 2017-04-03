package org.observe.collect;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ElementSpliterator.CollectionElement;
import org.observe.collect.ElementSpliterator.WrappingElement;
import org.observe.collect.ElementSpliterator.WrappingQuiterator;
import org.observe.collect.ObservableCollection.FilterMapDef;
import org.observe.collect.ObservableCollection.FilterMapResult;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

public class ObservableCollectionImpl {
	/**
	 * Implements {@link ObservableCollection#buildMap(TypeToken)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	class FilterMappedObservableCollection<E, T> implements ObservableCollection<T> {
		private final ObservableCollection<E> theWrapped;
		private final FilterMapDef<E, ?, T> theDef;
		private final boolean isDynamic;

		FilterMappedObservableCollection(ObservableCollection<E> wrap, FilterMapDef<E, ?, T> filterMapDef, boolean dynamic) {
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
				return DefaultCollectionMethods.contains(this, o);
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) o));
			if (reversed.error != null)
				return false;
			return theWrapped.contains(reversed.result);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (!theDef.isReversible() || size() < c.size()) // Try to map the fewest elements
				return DefaultCollectionMethods.containsAll(this, c);

			return theWrapped.containsAll(reverse(c));
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (!theDef.isReversible() || size() < c.size()) // Try to map the fewest elements
				return DefaultCollectionMethods.containsAll(this, c);

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

		protected Subscription onElement(Consumer<? super ObservableElement<T>> onElement, Object meta) {
			SimpleObservable<Void> unSubObs = new SimpleObservable<>();
			Subscription collSub = getWrapped().onElement(element -> {
				DynamicFilteredElement<E, T> retElement = filter(element, meta);
				element.unsubscribeOn(unSubObs).act(elValue -> {
					if (!retElement.isIncluded()) {
						FilterMapResult<T> mapped = getMap().apply(elValue.getValue());
						if (mapped.passed)
							onElement.accept(retElement);
					}
				});
			});
			return () -> {
				collSub.unsubscribe();
				unSubObs.onNext(null);
			};
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
			return onElement(onElement, null);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}

	/**
	 * The type of elements returned from {@link ObservableCollection#filterMap(Function)}
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element being wrapped
	 */
	class DynamicFilteredElement<E, T> implements ObservableElement<T> {
		private final ObservableElement<E> theWrappedElement;
		private final FilterMapDef<E, ?, T> theDef;

		/**
		 * @param wrapped The element to wrap
		 * @param def The mapping definition to filter on
		 */
		protected DynamicFilteredElement(ObservableElement<E> wrapped, FilterMapDef<E, ?, T> def) {
			theWrappedElement = wrapped;
			theDef = def;
		}

		@Override
		public ObservableValue<T> persistent() {
			return theWrappedElement.mapV(value -> theDef.map(new FilterMapResult<E, T>(value)).result, true);
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
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
			Subscription[] innerSub = new Subscription[1];
			Object [] oldValue=new Object[1];
			boolean [] included=new boolean[1];
			innerSub[0] = theWrappedElement.subscribe(new Observer<ObservableValueEvent<E>>() {
				@Override
				public <V2 extends ObservableValueEvent<E>> void onNext(V2 elValue) {
					FilterMapResult<E, T> res=theDef.map(new FilterMapResult<>(elValue.getValue()));
					if(res.error!=null){
						if(included[0]){
							observer2.onCompleted(createChangeEvent((T)oldValue[0], (T)oldValue[0], elValue));
							included[0]=false;
							oldValue[0]=null;
						}
					} else{
						if(!included[0])
							obsever2.onNe
					}
					T oldValue = theValue == null ? null : theValue.mapped;
					theValue = theMap.apply(elValue.getValue());
					if (!theValue.passed) {
						if (!isIncluded)
							return;
						isIncluded = false;
						Observer.onCompletedAndFinish(observer2, createChangeEvent(oldValue, oldValue, elValue));
						if (innerSub[0] != null) {
							innerSub[0].unsubscribe();
							innerSub[0] = null;
						}
					} else {
						boolean initial = !isIncluded;
						isIncluded = true;
						if (initial)
							Observer.onNextAndFinish(observer2, createInitialEvent(theValue.mapped, elValue));
						else
							Observer.onNextAndFinish(observer2, createChangeEvent(oldValue, theValue.mapped, elValue));
					}
				}

				@Override
				public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elValue) {
					if (!isIncluded)
						return;
					T oldValue = theValue.mapped;
					Observer.onCompletedAndFinish(observer2, createChangeEvent(oldValue, oldValue, elValue));
				}
			});
			if (!isIncluded) {
				return () -> {
				};
			}
			return innerSub[0];
		}

		@Override
		public String toString() {
			return "filter(" + theWrappedElement + ")";
		}

		@Override
		public boolean isSafe() {
			return theWrappedElement.isSafe();
		}
	}

}
