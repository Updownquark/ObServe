package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollectionImpl.CachedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.ConstantObservableCollection;
import org.observe.collect.ObservableCollectionImpl.ElementRefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.FilterMappedObservableCollection;
import org.observe.collect.ObservableCollectionImpl.FlattenedValueCollection;
import org.observe.collect.ObservableCollectionImpl.ModFilteredCollection;
import org.observe.collect.ObservableCollectionImpl.RefreshingCollection;
import org.observe.collect.ObservableCollectionImpl.TakenUntilObservableCollection;
import org.observe.collect.ObservableElementSpliterator.WrappingObservableElement;
import org.observe.collect.ObservableElementSpliterator.WrappingObservableSpliterator;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashSet;

import com.google.common.reflect.TypeToken;

/** Holds default implementation methods and classes for {@link ObservableSet} methods */
public class ObservableSetImpl {
	private ObservableSetImpl() {}

	/**
	 * Implements {@link ObservableSet#intersect(ObservableCollection)}
	 *
	 * @param <E> The type of the elements in the set
	 * @param <X> The type of elements in the filtering collection
	 */
	public static class IntersectedSet<E, X> implements ObservableSet<E> {
		// Note: Several (E) v casts below are technically incorrect, as the values may not be of type E
		// But they are runtime-safe because of the isElement tests
		private final ObservableSet<E> theLeft;
		private final ObservableCollection<X> theRight;
		private final boolean sameEquivalence;

		/**
		 * @param left The left set whose elements to reflect
		 * @param right The right set to filter this set's elements by
		 */
		protected IntersectedSet(ObservableSet<E> left, ObservableCollection<X> right) {
			theLeft = left;
			theRight = right;
			sameEquivalence = left.equivalence().equals(right.equivalence());
		}

		/** @return The left set whose elements this set reflects */
		protected ObservableSet<E> getLeft() {
			return theLeft;
		}

		/** @return The right collection that this set's elements are filtered by */
		protected ObservableCollection<X> getRight() {
			return theRight;
		}

		@Override
		public TypeToken<E> getType() {
			return theLeft.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theLeft.equivalence();
		}

		@Override
		public boolean order(Object elementId1, Object elementId2) {
			return theLeft.order(elementId1, elementId2);
		}

		@Override
		public boolean isLockSupported() {
			return theLeft.isLockSupported() || theRight.isLockSupported(); // Report whether locking will have any effect
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction lt = theLeft.lock(write, cause);
			Transaction rt = theRight.lock(write, cause);
			return () -> {
				lt.close();
				rt.close();
			};
		}

		/** @return The right collection's elements, in a set using the left set's equivalence */
		protected Set<? super E> getRightSet() {
			return ObservableCollectionImpl.toSet(theLeft.equivalence(), theRight);
		}

		@Override
		public int size() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return 0;
			if (sameEquivalence && theRight instanceof ObservableSet) {
				try (Transaction lt = theLeft.lock(false, null); Transaction rt = theRight.lock(false, null)) {
					return (int) theLeft.stream().filter(theRight::contains).count();
				}
			} else {
				Set<? super E> rightSet = getRightSet();
				try (Transaction t = theLeft.lock(false, null)) {
					return (int) theLeft.stream().filter(rightSet::contains).count();
				}
			}
		}

		@Override
		public boolean isEmpty() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return true;
			try (Transaction t = theRight.lock(false, null)) {
				return !theLeft.containsAny(theRight);
			}
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return ObservableElementSpliterator.empty(theLeft.getType());
			// Create the right set for filtering when the method is called
			Set<? super E> rightSet = getRightSet();
			ObservableElementSpliterator<E> leftSplit = theLeft.spliterator();
			return new WrappingObservableSpliterator<>(leftSplit, leftSplit.getType(), () -> {
				ObservableCollectionElement<E>[] container = new ObservableCollectionElement[1];
				WrappingObservableElement<E, E> wrappingEl = new WrappingObservableElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return ((ObservableCollectionElement<E>) getWrapped()).isAcceptable(value);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						return ((ObservableCollectionElement<E>) getWrapped()).set(value, cause);
					}
				};
				return el -> {
					if (!rightSet.contains(el.get()))
						return null;
					container[0] = (ObservableCollectionElement<E>) el;
					return wrappingEl;
				};
			});
		}

		@Override
		public boolean contains(Object o) {
			if (!theLeft.contains(o))
				return false;
			if (sameEquivalence)
				return theRight.contains(o);
			else {
				try (Transaction t = theRight.lock(false, null)) {
					Equivalence<? super E> equiv = theLeft.equivalence();
					return theRight.stream().anyMatch(v -> equiv.isElement(v) && equiv.elementEquals((E) v, o));
				}
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			if (theRight.isEmpty())
				return false;
			if (!theLeft.containsAll(c))
				return false;
			if (sameEquivalence)
				return theRight.containsAll(c);
			else
				return getRightSet().containsAll(c);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			if (c.isEmpty())
				return false;
			if (theLeft.isEmpty() || theRight.isEmpty())
				return false;
			if (sameEquivalence && theRight instanceof ObservableSet) {
				try (Transaction lt = theLeft.lock(false, null); Transaction rt = theRight.lock(false, null)) {
					for (Object o : c)
						if (theLeft.contains(o) && theRight.contains(o))
							return true;
				}
			} else {
				Set<? super E> rightSet = getRightSet();
				try (Transaction t = theLeft.lock(false, null)) {
					for (Object o : c)
						if (theLeft.contains(o) && rightSet.contains(o))
							return true;
				}
			}
			return false;
		}

		@Override
		public String canAdd(E value) {
			String msg = theLeft.canAdd(value);
			if (msg != null)
				return msg;
			if (value != null && !theRight.getType().getRawType().isInstance(value))
				return ObservableCollection.StdMsg.BAD_TYPE;
			msg = theRight.canAdd((X) value);
			if (msg != null)
				return msg;
			return null;
		}

		@Override
		public boolean add(E e) {
			try (Transaction lt = theLeft.lock(true, null); Transaction rt = theRight.lock(true, null)) {
				boolean addedLeft = false;
				if (!theLeft.contains(e)) {
					if (!theLeft.add(e))
						return false;
					addedLeft = true;
				}
				if (!theRight.contains(e)) {
					try {
						if (!theRight.add((X) e)) {
							if (addedLeft)
								theLeft.remove(e);
							return false;
						}
					} catch (RuntimeException ex) {
						if (addedLeft)
							theLeft.remove(e);
						throw ex;
					}
				}
				return true;
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			boolean addedAny = false;
			try (Transaction lt = theLeft.lock(true, null); Transaction rt = theRight.lock(true, null)) {
				for (E o : c) {
					boolean addedLeft = false;
					if (!theLeft.contains(o)) {
						if (!theLeft.add(o))
							continue;
						addedLeft = true;
					}
					boolean addedRight = false;
					if (!theRight.contains(o)) {
						addedRight = true;
						if (!theRight.add((X) o)) {
							if (addedLeft)
								theLeft.remove(o);
							continue;
						}
					}
					if (addedLeft || addedRight)
						addedAny = true;
				}
			}
			return addedAny;
		}

		@Override
		public String canRemove(Object value) {
			String msg = theLeft.canRemove(value);
			if (msg != null)
				return msg;
			msg = theRight.canRemove(value);
			if (msg != null)
				return msg;
			return null;
		}

		@Override
		public boolean remove(Object o) {
			return theLeft.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theLeft.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theLeft.retainAll(c);
		}

		@Override
		public void clear() {
			if (theLeft.isEmpty() || theRight.isEmpty())
				return;
			Set<? super E> rightSet = getRightSet();
			theLeft.removeAll(rightSet);
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			// TODO Auto-generated method stub
		}
	}

	/**
	 * A set that is a result of a filter-map operation applied to another set
	 *
	 * @param <E> The type of values in the source set
	 * @param <T> The type of values in this set
	 */
	public static class FilterMappedSet<E, T> extends FilterMappedObservableCollection<E, T> implements ObservableSet<T> {
		/**
		 * @param wrap The set whose elements to reflect
		 * @param filterMapDef The filter-mapping definition defining which elements are included and how they are mapped
		 */
		protected FilterMappedSet(ObservableSet<E> wrap, EquivalentFilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		protected EquivalentFilterMapDef<E, ?, T> getDef() {
			return (EquivalentFilterMapDef<E, ?, T>) super.getDef();
		}
	}

	/**
	 * Implements {@link ObservableSet#refresh(Observable)}
	 *
	 * @param <E> The type of the set
	 */
	public static class RefreshingSet<E> extends RefreshingCollection<E> implements ObservableSet<E> {
		/**
		 * @param wrap The set whose values to reflect
		 * @param refresh The observable to refresh this set's elements with
		 */
		protected RefreshingSet(ObservableSet<E> wrap, Observable<?> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#refreshEach(Function)}
	 *
	 * @param <E> The type of the set
	 */
	public static class ElementRefreshingSet<E> extends ElementRefreshingCollection<E> implements ObservableSet<E> {
		/**
		 * @param wrap The set whose values to reflect
		 * @param refresh The function providing observables to refresh individual elements with
		 */
		protected ElementRefreshingSet(ObservableSet<E> wrap, Function<? super E, Observable<?>> refresh) {
			super(wrap, refresh);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#filterModification(ModFilterDef)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ModFilteredSet<E> extends ModFilteredCollection<E> implements ObservableSet<E> {
		/**
		 * @param wrapped The set whose values to reflect
		 * @param def The modification-filter definition to use to restrict modification to this set
		 */
		protected ModFilteredSet(ObservableSet<E> wrapped, ModFilterDef<E> def) {
			super(wrapped, def);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#cached(Observable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class CachedObservableSet<E> extends CachedObservableCollection<E> implements ObservableSet<E> {
		/**
		 * @param wrapped The set whose values to reflect
		 * @param until The observable that will deactivate this set when fired
		 */
		protected CachedObservableSet(ObservableSet<E> wrapped, Observable<?> until) {
			super(wrapped, until);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}

		@Override
		protected BetterCollection<E> createCache() {
			return new BetterHashSet<>();
		}
	}

	/**
	 * Backs {@link ObservableSet#takeUntil(Observable)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class TakenUntilObservableSet<E> extends TakenUntilObservableCollection<E> implements ObservableSet<E> {
		/**
		 * @param wrap The set whose values to reflect
		 * @param until The observable to listen to to terminate listeners to this set
		 * @param terminate Whether the observable's firing will also remove all elements from listeners to the set
		 */
		protected TakenUntilObservableSet(ObservableSet<E> wrap, Observable<?> until, boolean terminate) {
			super(wrap, until, terminate);
		}

		@Override
		protected ObservableSet<E> getWrapped() {
			return (ObservableSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableSet#constant(TypeToken, Equivalence, Collection)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class ConstantObservableSet<E> extends ConstantObservableCollection<E> implements ObservableSet<E> {
		private final Equivalence<? super E> theEquivalence;

		/**
		 * @param type The type of the set
		 * @param equivalence The equivalence set for the set's uniqueness
		 * @param collection The values for the set
		 */
		public ConstantObservableSet(TypeToken<E> type, Equivalence<? super E> equivalence, Collection<E> collection) {
			super(type, ObservableCollectionImpl.toSet(equivalence, collection));
			theEquivalence = equivalence;
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theEquivalence;
		}
	}

	/**
	 * Implements {@link ObservableSet#flattenValue(ObservableValue)}
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class FlattenedValueSet<E> extends FlattenedValueCollection<E> implements ObservableSet<E> {
		/** @param collectionObservable The value containing a set to flatten */
		protected FlattenedValueSet(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected ObservableValue<? extends ObservableSet<E>> getWrapped() {
			return (ObservableValue<? extends ObservableSet<E>>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link ObservableCollection#unique()}.
	 *
	 * While most of this set's methods have similar performance to the underlying collection, the {@link #size()} method must re-check the
	 * entire collection's uniqueness for every operation, resulting in O(n) performance or worse, depending on the performance of the
	 * {@link Equivalence#createSet() equivalence set}. Iteration also will have slower performance. The {@link #cached(Observable)} method
	 * may be used to mitigate this.
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class CollectionWrappingSet<E> implements ObservableSet<E> {
		private final ObservableCollection<E> theCollection;

		/** @param collection The collection whose values to reflect */
		protected CollectionWrappingSet(ObservableCollection<E> collection) {
			theCollection = collection;
		}

		/** @return The collection whose values this set reflects */
		protected ObservableCollection<E> getWrapped() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public Equivalence<? super E> equivalence() {
			return theCollection.equivalence();
		}

		@Override
		public boolean order(Object elementId1, Object elementId2) {
			return theCollection.order(elementId1, elementId2);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public int size() {
			return ObservableCollectionImpl.toSet(equivalence(), theCollection).size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return theCollection.contains(o);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theCollection.containsAny(c);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theCollection.containsAll(c);
		}

		@Override
		public ObservableElementSpliterator<E> spliterator() {
			return unique(theCollection.spliterator(), true);
		}

		/**
		 * @param backing The spliterator from the collection to
		 * @param forwardFromStart Whether the spliterator's position is at the beginning of the collection, moving forward. If this is not
		 *        the case, additional operations will need to be done for each element to ensure that only the first element in the
		 *        collection with a particular value is returned.
		 * @return A spliterator backed by the collection's spliterator that eliminates duplicate elements
		 */
		protected ObservableElementSpliterator<E> unique(ObservableElementSpliterator<E> backing, boolean forwardFromStart) {
			Map<E, Object> uniqueIds = equivalence().createMap();
			return new WrappingObservableSpliterator<>(backing, backing.getType(), () -> {
				ObservableCollectionElement<E>[] container = new ObservableCollectionElement[1];
				WrappingObservableElement<E, E> wrappingEl = new WrappingObservableElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return ((ObservableCollectionElement<E>) getWrapped()).isAcceptable(value);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						return ((ObservableCollectionElement<E>) getWrapped()).set(value, cause);
					}
				};
				return el -> {
					Object elId = uniqueIds.computeIfAbsent(el.get(), v -> {
						if (forwardFromStart)
							return el.getElementId();
						else
							return theCollection.elementFor(el.get()).getElementId();
					});
					if (!elId.equals(el.getElementId()))
						return null;
					container[0] = (ObservableCollectionElement<E>) el;
					return wrappingEl;
				};
			});
		}

		@Override
		public String canAdd(E value) {
			String msg = theCollection.canAdd(value);
			if (msg != null)
				return msg;
			if (theCollection.contains(value))
				return StdMsg.ELEMENT_EXISTS;
			return null;
		}

		@Override
		public boolean add(E e) {
			if (theCollection.contains(e))
				return false;
			return theCollection.add(e);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			Equivalence<? super E> equiv = equivalence();
			Set<E> set = equiv.createSet();
			for (Object o : c) {
				if (!equiv.isElement(o))
					continue;
				if (theCollection.contains(o))
					continue;
				set.add((E) o);
			}
			return theCollection.addAll(set);
		}

		@Override
		public String canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			return theCollection.removeAll(Arrays.asList(o));
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theCollection.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theCollection.retainAll(c);
		}

		@Override
		public void clear() {
			theCollection.clear();
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
			return subscribe(observer, true);
		}

		// TODO This seems weird. Should work, but don't think it will actually support reversibility
		protected CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer, boolean forward) {
			Map<E, Object> elementIds = equivalence().createMap();
			boolean[] initialized = new boolean[1];
			CollectionSubscription sub = theCollection.subscribe(evt -> {
				Object elId = null;
				switch (evt.getType()) {
				case add:
					elId = elementIds.computeIfAbsent(evt.getNewValue(), v -> {
						if (forward || initialized[0])
							return evt.getElementId();
						else
							return theCollection.elementFor(v);
					});
					if (!elId.equals(evt.getElementId())) {
						// This new value is equivalent to that of another element in the collection
						if (!initialized[0] || theCollection.order(elId, evt.getElementId()))
							return; // The element already reported has priority. Don't report this one.
						else {
							// The new element has priority. Remove the old one.
							ObservableCollectionEvent.doWith(
								createEvent(elId, CollectionChangeType.remove, evt.getNewValue(), evt.getNewValue(), evt), observer);
							elementIds.put(evt.getNewValue(), evt.getElementId());
						}
					} else
						elementIds.put(evt.getNewValue(), evt.getElementId());
					break;
				case remove:
					// This element has been removed, but it's possible there's another element in the collection with the same value.
					// Search for it.
					ObservableCollectionElement<E> el = theCollection.elementFor(evt.getOldValue());
					if (el != null) {
						// Need to remove this element before adding the other
						ObservableCollectionEvent.doWith(
							createEvent(evt.getElementId(), CollectionChangeType.remove, evt.getOldValue(), evt.getOldValue(), evt),
							observer);
						ObservableCollectionEvent.doWith(createEvent(el.getElementId(), CollectionChangeType.add, null, el.get(), evt),
							observer);
						elementIds.put(el.get(), el.getElementId());
						return; // Already did all the needed operations
					} else {
						elementIds.remove(evt.getOldValue());
						// If there is no other element with the same value, just remove like default
					}
					break;
				case set:
					if (!equivalence().elementEquals(evt.getOldValue(), evt.getNewValue())) {
						// The two values are not equivalent. This is the most complex case.
						// It could be handled simply by splitting the operation into a remove of the old value, then an add of the new
						// value, but there may be efficiency in firing a simple change event if possible.
						el = theCollection.elementFor(evt.getOldValue());
						if (el == null) {
							// The old value is no longer present
							elId = elementIds.get(evt.getNewValue());
							if (elId != null) {
								// The new value already exists in the collection
								if (theCollection.order(elId, evt.getElementId())) {
									// The element already reported has priority. Report the removed old value and return.
									ObservableCollectionEvent.doWith(createEvent(evt.getElementId(), CollectionChangeType.remove,
										evt.getOldValue(), evt.getOldValue(), evt), observer);
									return;
								} else {
									// The new element has priority.
									// Remove the old element with the new value before reporting the change event.
									ObservableCollectionEvent.doWith(
										createEvent(elId, CollectionChangeType.remove, evt.getNewValue(), evt.getNewValue(), evt),
										observer);
									elementIds.put(evt.getNewValue(), evt.getElementId());
								}
							}
							// else The new value is unique in the collection, so we can just report the change event
						} else {
							// The old value is present in another element in the collection
							elId = elementIds.get(evt.getNewValue());
							if (elId != null) {
								// The new value already exists in the collection
								if (theCollection.order(elId, evt.getElementId())) {
									// The element already reported has priority for the new value. Report the removed old value,
									// the added (already present) old value, and return.
									ObservableCollectionEvent.doWith(createEvent(evt.getElementId(), CollectionChangeType.remove,
										evt.getOldValue(), evt.getOldValue(), evt), observer);
									ObservableCollectionEvent
									.doWith(createEvent(elId, CollectionChangeType.add, null, evt.getNewValue(), evt), observer);
									return;
								} else {
									// The new element has priority.
									// Remove the old element with the new value before reporting the change event.
									ObservableCollectionEvent.doWith(
										createEvent(elId, CollectionChangeType.remove, evt.getNewValue(), evt.getNewValue(), evt),
										observer);
									elementIds.put(evt.getNewValue(), evt.getElementId());
								}
							} else {
								// The new value is unique in the collection
							}
						}
						ObservableCollectionEvent
						.doWith(createEvent(evt.getElementId(), evt.getType(), evt.getOldValue(), evt.getNewValue(), evt), observer);
						// If we need to add a different element that already contained the old value, do it now.
						if (el != null) {
							ObservableCollectionEvent.doWith(
								createEvent(el.getElementId(), CollectionChangeType.add, evt.getOldValue(), evt.getOldValue(), evt),
								observer);
							elementIds.put(evt.getOldValue(), el.getElementId());
						} else
							elementIds.remove(evt.getOldValue());
						return;
					}
					// else If the old and new values are equivalent, then just propagate the change.
					break;
				}
				ObservableCollectionEvent.doWith(createEvent(evt.getElementId(), evt.getType(), evt.getOldValue(), evt.getNewValue(), evt),
					observer);
			});
			initialized[0] = true;
			return sub;
		}

		/**
		 * Creates a change event for this collection's listeners
		 *
		 * @param elementId The ID of the element that the change occurred on
		 * @param type The type of the change
		 * @param oldValue The old value
		 * @param newValue The new value
		 * @param cause The cause of the change
		 * @return The event to fire to the listener
		 */
		protected ObservableCollectionEvent<E> createEvent(Object elementId, CollectionChangeType type, E oldValue, E newValue,
			Object cause) {
			return new ObservableCollectionEvent<>(elementId, type, oldValue, newValue, cause);
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
			return ObservableSet.toString(this);
		}
	}
}
