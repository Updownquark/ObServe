package org.observe.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractCombinedCollectionBuilder;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.qommons.Causable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.SimpleCause;
import org.qommons.collect.TransactableList;
import org.qommons.collect.TreeList;
import org.qommons.tree.CountedRedBlackNode.DefaultNode;
import org.qommons.tree.CountedRedBlackNode.DefaultTreeSet;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An enhanced collection.
 *
 * The biggest differences between ObservableCollection and Collection are:
 * <ul>
 * <li><b>Observability</b> The {@link #subscribe(Consumer)} method and the {@link #changes()} and {@link #simpleChanges()} observables
 * allow subscribers to be notified of changes in the collection.</li>
 * <li><b>Dynamic Transformation</b> The stream API allows transforming of the content of one collection into another, but the
 * transformation is done once for all, creating a new collection independent of the source. Sometimes it is desirable to make a transformed
 * collection that does its transformation dynamically, keeping the same data source, so that when the source is modified, the transformed
 * collection is also updated accordingly. #map(Function), #filter(Function), #groupBy(Function), and others allow this.</li>
 * <li><b>Modification Control</b> The {@link #view()} method creates a collection that forbids certain types of modifications to it.
 * Modification control can also be used to intercept and perform actions based on modifications to a collection.</li>
 * <li><b>ElementSpliterator</b> ObservableCollections must implement {@link #spliterator()}, which returns a {@link ElementSpliterator},
 * which is an enhanced {@link Spliterator}. This had potential for the improved performance associated with using {@link Spliterator}
 * instead of {@link Iterator} as well as the utility added by {@link ElementSpliterator}.</li>
 * <li><b>Transactionality</b> ObservableCollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a
 * collection for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> ObservableCollections have a {@link #getType() type} associated with them, allowing them to enforce
 * type-safety at run time. How strictly this type-safety is enforced is implementation-dependent.</li>
 * </ul>
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableCollection<E> extends ReversibleList<E>, TransactableList<E> {
	/** Standard messages returned by this class */
	interface StdMsg {
		static String BAD_TYPE = "Object is the wrong type for this collection";
		static String UNSUPPORTED_OPERATION = "Unsupported Operation";
		static String NULL_DISALLOWED = "Null is not allowed";
		static String ELEMENT_EXISTS = "Element already exists";
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
		static String ILLEGAL_ELEMENT = "Element is not allowed";
	}

	/**
	 * The {@link ObservableCollectionEvent#getCause() cause} for events fired for extant elements in the collection upon
	 * {@link #subscribe(Consumer) subscription}
	 */
	public static class SubscriptionCause extends SimpleCause {}

	// Additional contract methods

	/** @return The type of elements in this collection */
	TypeToken<E> getType();

	default boolean belongs(Object value) {
		return equivalence().isElement(value) && getType().getRawType().isInstance(value);
	}

	@Override
	abstract boolean isLockSupported();

	@Override
	default MutableObservableSpliterator<E> mutableSpliterator() {
		return mutableSpliterator(true);
	}

	@Override
	MutableObservableSpliterator<E> mutableSpliterator(boolean fromStart);

	@Override
	MutableObservableSpliterator<E> mutableSpliterator(int index);

	/**
	 * Registers a listener for changes to this collection
	 *
	 * @param observer The listener to be notified of each element change in the collection
	 * @return The subscription to use to terminate listening
	 */
	Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer);

	@Override
	default ObservableElementSpliterator<E> spliterator() {
		return spliterator(true);
	}

	@Override
	default ObservableElementSpliterator<E> spliterator(boolean fromStart) {
		return mutableSpliterator(fromStart).immutable();
	}

	@Override
	default ObservableElementSpliterator<E> spliterator(int index) {
		return mutableSpliterator(index).immutable();
	}

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return new ObservableCollectionImpl.ObservableListIterator<>(this, mutableSpliterator(index));
	}

	@Override
	default ReversibleList<E> subList(int fromIndex, int toIndex) {
		return new ObservableCollectionImpl.SubList<>(this, fromIndex, toIndex);
	}

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	@Override
	default E get(int index) {
		return ofElementAt(index, el -> el.get());
	}

	int getElementsBefore(ElementId id);

	int getElementsAfter(ElementId id);

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int indexOf(Object value) {
		if (!belongs(value))
			return -1;
		try (Transaction t = lock(false, null)) {
			int[] index = new int[1];
			if (!forObservableElement((E) value, el -> index[0] = getElementsBefore(el.getElementId()), true))
				return -1;
			return index[0];
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int lastIndexOf(Object value) {
		if (!belongs(value))
			return -1;
		try (Transaction t = lock(false, null)) {
			int[] index = new int[1];
			if (!forObservableElement((E) value, el -> index[0] = getElementsBefore(el.getElementId()), false))
				return -1;
			return index[0];
		}
	}

	@Override
	default boolean contains(Object o) {
		if (!belongs(o))
			return false;
		return forObservableElement((E) o, el -> {
		}, true);
	}

	@Override
	default boolean containsAny(Collection<?> c) {
		try (Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			try (Transaction t = lock(false, null)) {
				if (c.size() < size()) {
					for (Object o : c)
						if (contains(o))
							return true;
					return false;
				} else {
					if (c.isEmpty())
						return false;
					Set<E> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
					Spliterator<E> iter = spliterator();
					boolean[] found = new boolean[1];
					while (iter.tryAdvance(next -> {
						found[0] = cSet.contains(next);
					}) && !found[0]) {
					}
					return found[0];
				}
			}
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		try (Transaction ct = Transactable.lock(c, false, null)) {
			if (c.isEmpty())
				return true;
			try (Transaction t = lock(false, null)) {
				if (c.size() < size()) {
					for (Object o : c)
						if (!contains(o))
							return false;
					return true;
				} else {
					if (c.isEmpty())
						return false;
					Set<E> cSet = ObservableCollectionImpl.toSet(equivalence(), c);
					cSet.removeAll(this);
					return cSet.isEmpty();
				}
			}
		}
	}

	/**
	 * Tests the compatibility of an object with this collection. This method exposes a "best guess" on whether an element could be added to
	 * the collection , but does not provide any guarantee. This method should return null for any object for which {@link #add(Object)} is
	 * successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the position
	 * of the element in the collection may be a factor, but is tested for here.
	 *
	 * @param value The value to test compatibility for
	 * @return Null if given value could possibly be added to this collection, or a message why it can't
	 */
	String canAdd(E value);

	/**
	 * Tests the removability of an element from this collection. This method exposes a "best guess" on whether an element in the collection
	 * could be removed, but does not provide any guarantee. This method should return null for any object for which {@link #remove(Object)}
	 * is successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the
	 * position of the element in the collection may be a factor, but may not be tested for here.
	 *
	 * @param value The value to test removability for
	 * @return Null if given value could possibly be removed from this collection, or a message why it can't
	 */
	default String canRemove(Object value) {
		if (!belongs(value))
			return StdMsg.NOT_FOUND;
		String[] msg = new String[1];
		if (!forElement((E) value, el -> msg[0] = el.canRemove(), true))
			return StdMsg.NOT_FOUND;
		return msg[0];
	}

	@Override
	default boolean remove(Object o) {
		return removeFirstOccurrence(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return SimpleCause.doWithF(new SimpleCause(), cause -> {
			try (Transaction ct = Transactable.lock(c, false, null)) {
				if (isEmpty() || c.isEmpty())
					return false;
				return findAll(v -> c.contains(v), el -> el.remove(cause), true) > 0;
			}
		});
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return SimpleCause.doWithF(new SimpleCause(), cause -> {
			try (Transaction t = lock(true, cause); Transaction ct = Transactable.lock(c, false, null)) {
				if (isEmpty())
					return false;
				if (c.isEmpty()) {
					clear();
					return true;
				}
				return findAll(v -> !c.contains(v), el -> el.remove(cause), true) > 0;
			}
		});
	}

	@Override
	default boolean removeLast(Object o) {
		return removeLastOccurrence(o);
	}

	@Override
	default boolean removeIf(Predicate<? super E> filter) {
		boolean[] removed = new boolean[1];
		try (Transaction t = lock(true, null)) {
			MutableObservableSpliterator<E> iter = mutableSpliterator();
			SimpleCause cause = new SimpleCause();
			SimpleCause.doWith(cause, c -> iter.forEachElement(el -> {
				if (filter.test(el.get())) {
					el.remove(cause);
					removed[0] = true;
				}
			}));
			return removed[0];
		}
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		return SimpleCause.doWithF(new SimpleCause(), cause -> {
			boolean mod = false;
			try (Transaction t = lock(true, cause); Transaction t2 = Transactable.lock(c, false, cause)) {
				for (E o : c)
					mod |= add(o);
			}
			return mod;
		});
	}

	@Override
	default E getFirst() {
		try (Transaction t = lock(false, null)) {
			return ReversibleList.super.getFirst();
		}
	}

	@Override
	default E getLast() {
		try (Transaction t = lock(false, null)) {
			return ReversibleList.super.getLast();
		}
	}

	@Override
	default E peekFirst() {
		try (Transaction t = lock(false, null)) {
			Object[] value = new Object[1];
			if (spliterator(true).tryAdvance(v -> value[0] = v))
				return (E) value[0];
			else
				return null;
		}
	}

	@Override
	default E peekLast() {
		try (Transaction t = lock(false, null)) {
			Object[] value = new Object[1];
			if (spliterator(false).tryReverse(v -> value[0] = v))
				return (E) value[0];
			else
				return null;
		}
	}

	@Override
	default E element() {
		try (Transaction t = lock(false, null)) {
			return ReversibleList.super.element();
		}
	}

	@Override
	default E peek() {
		try (Transaction t = lock(false, null)) {
			return ReversibleList.super.peek();
		}
	}

	@Override
	default void addFirst(E e) {
		try (Transaction t = lock(true, null)) {
			String msg = canAdd(e);
			if (msg == null)
				throw new IllegalStateException(msg);
			ReversibleList.super.addFirst(e);
		}
	}

	@Override
	default void addLast(E e) {
		try (Transaction t = lock(true, null)) {
			String msg = canAdd(e);
			if (msg == null)
				throw new IllegalStateException(msg);
			ReversibleList.super.addLast(e);
		}
	}

	@Override
	default boolean offerFirst(E e) {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.offerFirst(e);
		}
	}

	@Override
	default boolean offerLast(E e) {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.offerLast(e);
		}
	}

	@Override
	default E removeFirst() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.removeFirst();
		}
	}

	@Override
	default E removeLast() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.removeLast();
		}
	}

	@Override
	default E pollFirst() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.pollFirst();
		}
	}

	@Override
	default E pollLast() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.pollLast();
		}
	}

	@Override
	default boolean removeFirstOccurrence(Object o) {
		if (!belongs(o))
			return false;
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.removeFirstOccurrence(o);
		}
	}

	@Override
	default boolean removeLastOccurrence(Object o) {
		if (!belongs(o))
			return false;
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.removeLastOccurrence(o);
		}
	}

	@Override
	default boolean offer(E e) {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.offer(e);
		}
	}

	@Override
	default E remove() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.remove();
		}
	}

	@Override
	default E poll() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.poll();
		}
	}

	@Override
	default void push(E e) {
		try (Transaction t = lock(true, null)) {
			ReversibleList.super.push(e);
		}
	}

	@Override
	default E pop() {
		try (Transaction t = lock(true, null)) {
			return ReversibleList.super.pop();
		}
	}

	@Override
	default void removeRange(int fromIndex, int toIndex) {
		try (Transaction t = lock(true, null)) {
			MutableObservableSpliterator<E> spliter = mutableSpliterator(fromIndex);
			for (int i = fromIndex; i < toIndex; i++)
				spliter.tryAdvanceElement(el -> el.remove(null));
		}
	}

	@Override
	default boolean addAll(int index, Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		forMutableElementAt(index, el -> {
			try (Transaction t = Transactable.lock(c, false, null)) {
				Spliterator<? extends E> spliter;
				if (c instanceof ReversedCollection)
					spliter = ((ReversedCollection<? extends E>) c).spliterator(false).reverse();
				else {
					ArrayList<E> list = new ArrayList<>(c);
					Collections.reverse(list);
					spliter = list.spliterator();
				}
				spliter.forEachRemaining(v -> ((MutableObservableElement<E>) el).add(v, true, null));
			}
		});
		return true;
	}

	@Override
	default E set(int index, E element) {
		return ofMutableElementAt(index, el -> ((MutableObservableElement<E>) el).set(element, null));
	}

	@Override
	default void add(int index, E element) {
		forMutableElementAt(index, el -> ((MutableObservableElement<E>) el).add(element, true, null));
	}

	@Override
	default E remove(int index) {
		return ofMutableElementAt(index, el -> {
			E old = el.get();
			el.remove(null);
			return old;
		});
	}

	@Override
	void clear();

	/** @return Whether events given to the {@link #onChange(Consumer)} listener are always {@link IndexedCollectionEvent indexed} */
	boolean isEventIndexed();

	/**
	 * Like {@link #onChange(Consumer)}, but also fires initial {@link CollectionChangeType#add add} events for each element currently in
	 * the collection, and optionally fires final {@link CollectionChangeType#remove remove} events for each element in the collection on
	 * unsubscription.
	 *
	 * @param observer The listener to be notified of each element change in the collection
	 * @return The subscription to use to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		Subscription changeSub;
		boolean indexed = isEventIndexed();
		try (Transaction t = lock(false, null)) {
			// Initial events
			int[] index = new int[1];
			SubscriptionCause.doWith(new SubscriptionCause(), c -> spliterator().forEachObservableElement(el -> {
				ObservableCollectionEvent<E> event;
				if (indexed)
					event = new IndexedCollectionEvent<>(el.getElementId(), index[0]++, CollectionChangeType.add, null, el.get(), c);
				else
					event = new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.add, null, el.get(), c);
				observer.accept(event);
			}));
			// Subscribe changes
			changeSub = onChange(observer);
		}
		return removeAll -> {
			try (Transaction t = lock(false, null)) {
				// Unsubscribe changes
				changeSub.unsubscribe();
				if (removeAll) {
					// Remove events
					int[] index = new int[1];
					SubscriptionCause.doWith(new SubscriptionCause(), c -> spliterator().forEachObservableElement(el -> {
						ObservableCollectionEvent<E> event;
						if (indexed)
							event = new IndexedCollectionEvent<>(el.getElementId(), index[0]++, CollectionChangeType.add, null, el.get(),
								c);
						else
							event = new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.add, null, el.get(), c);
						observer.accept(event);
					}));
				}
			}
		};
	}

	/**
	 * Same as {@link #subscribe(Consumer)}, but the events are indexed by their position in the collection
	 *
	 * @param observer The listener to be notified of each element change in the collection
	 * @return The subscription to use to terminate listening
	 */
	default CollectionSubscription subscribeIndexed(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
		if (isEventIndexed())
			return subscribe(evt -> observer.accept((IndexedCollectionEvent<E>) evt));
		DefaultTreeSet<ElementId> ids = new DefaultTreeSet<>(Comparable::compareTo);
		return subscribe(evt -> {
			ElementId id = evt.getElementId();
			int index = -1;
			switch (evt.getType()) {
			case add:
				index = ids.addGetNode(id).getIndex();
				break;
			case remove:
				DefaultNode<ElementId> node = ids.getNode(id);
				index = node.getIndex();
				ids.removeNode(node);
				break;
			case set:
				index = ids.getNode(id).getIndex();
				break;
			}
			observer.accept(new IndexedCollectionEvent<>(id, index, evt.getType(), evt.getOldValue(), evt.getNewValue(), evt));
		});
	}

	/**
	 * Same as {@link #subscribe(Consumer)}, except that the elements currently present in this collection are given to the observer in
	 * reverse order
	 *
	 * @param observer The listener to be notified of changes to the collection
	 * @return The subscription to call when the calling code is no longer interested in this collection
	 */
	default CollectionSubscription subscribeReverse(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		Subscription changeSub;
		try (Transaction t = lock(false, null)) {
			// Initial events
			SubscriptionCause.doWith(new SubscriptionCause(), c -> spliterator().reverse().forEachObservableElement(
				el -> observer.accept(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.add, null, el.get(), c))));
			// Subscribe changes
			changeSub = onChange(observer);
		}
		return removeAll -> {
			try (Transaction t = lock(false, null)) {
				// Unsubscribe changes
				changeSub.unsubscribe();
				if (removeAll) {
					// Remove events
					SubscriptionCause.doWith(new SubscriptionCause(), c -> spliterator().reverse().forEachObservableElement(el -> observer
						.accept(new ObservableCollectionEvent<>(el.getElementId(), CollectionChangeType.remove, null, el.get(), c))));
				}
			}
		};
	}

	/**
	 * Same as {@link #subscribeIndexed(Consumer)}, except that the elements currently present in this collection are given to the observer
	 * in reverse order
	 *
	 * @param observer The listener to be notified of changes to the collection
	 * @return The subscription to call when the calling code is no longer interested in this collection
	 */
	default CollectionSubscription subscribeReverseIndexed(Consumer<? super IndexedCollectionEvent<? extends E>> observer) {
		DefaultTreeSet<ElementId> ids = new DefaultTreeSet<>(Comparable::compareTo);
		return subscribeReverse(evt -> {
			ElementId id = evt.getElementId();
			int index = -1;
			switch (evt.getType()) {
			case add:
				index = ids.addGetNode(id).getIndex();
				break;
			case remove:
				DefaultNode<ElementId> node = ids.getNode(id);
				index = node.getIndex();
				ids.removeNode(node);
				break;
			case set:
				index = ids.getNode(id).getIndex();
				break;
			}
			observer.accept(new IndexedCollectionEvent<>(id, index, evt.getType(), evt.getOldValue(), evt.getNewValue(), evt));
		});
	}

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ObservableCollection<E> reverse() {
		return new ObservableCollectionImpl.ReversedObservableCollection<>(this);
	}

	// /**
	// * <p>
	// * The session allows listeners to retain state for the duration of a unit of work (controlled by implementation-specific means),
	// * batching events where possible. Not all events on a collection will have a session (the value may be null). In addition, the
	// presence
	// * or absence of a session need not imply anything about the threaded interactions with a session. A transaction may encompass events
	// * fired and received on multiple threads. In short, the only thing guaranteed about sessions is that they will end. Therefore, if a
	// * session is present, observers may assume that they can delay expensive results of collection events until the session completes.
	// * </p>
	// * <p>
	// * In order to use the session for a listening operation, 2 observers must be installed: one for the collection, and one for the
	// * session. If an event that the observer is interested in occurs in the collection, the session value must be checked. If there is
	// * currently a session, then the session must be tagged with information that will allow later reconstruction of the interesting
	// * particulars of the event. When a session event occurs, the observer should check to see if the
	// * {@link ObservableValueEvent#getOldValue() old value} of the event is non null and whether that old session (the one that is now
	// * ending) has any information installed by the collection observer. If it does, the interesting information should be reconstructed
	// and
	// * dealt with at that time.
	// * </p>
	// *
	// * @return The observable value for the current session of this collection
	// */
	// ObservableValue<CollectionSession> getSession();

	/**
	 * Gets access to the equivalence scheme that this collection uses. {@link ObservableCollection}s are permitted to compare their
	 * elements using any consistent scheme. This scheme is exposed here and should affect any operations on the collection that require
	 * element comparison, e.g.:
	 * <ul>
	 * <li>{@link #contains(Object)}, which will return true for an argument <code>arg</code> if and only if there is an
	 * <code>element</code> in the collection for which this <code>elementEquals(element, arg)</code> returns true</li>
	 * <li>{@link #remove(Object)}, which will only remove elements that match according to this method</li>
	 * </ul>
	 *
	 * The equivalence's {@link Equivalence#isElement(Object)} method must return true for any element in this collection or any element for
	 * which {@link #canAdd(Object)} returns null.
	 *
	 * For {@link ObservableSet}s, this method exposes a test of the set's exclusiveness. I.e. a set may not contain any 2 elements for
	 * which this method returns true.
	 *
	 * @return The equivalence that governs this collection
	 */
	Equivalence<? super E> equivalence();

	@Override
	default boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		return forMutableElement(value, onElement, first);
	}

	boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first);

	boolean forMutableElement(E value, Consumer<? super MutableObservableElement<? extends E>> onElement, boolean first);

	default void forElementAt(ElementId elementId, Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
		ofElementAt(elementId, el -> {
			onElement.accept(el);
			return null;
		});
	}

	default void forMutableElementAt(ElementId elementId, Consumer<? super MutableObservableElement<? extends E>> onElement) {
		ofMutableElementAt(elementId, el -> {
			onElement.accept(el);
			return null;
		});
	}

	<T> T ofElementAt(ElementId elementId, Function<? super ObservableCollectionElement<? extends E>, T> onElement);

	<T> T ofMutableElementAt(ElementId elementId, Function<? super MutableObservableElement<? extends E>, T> onElement);

	default void forElementAt(int index, Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
		ofElementAt(index, el -> {
			onElement.accept(el);
			return null;
		});
	}

	default void forMutableElementAt(int index, Consumer<? super MutableObservableElement<? extends E>> onElement) {
		ofMutableElementAt(index, el -> {
			onElement.accept(el);
			return null;
		});
	}

	<T> T ofElementAt(int index, Function<? super ObservableCollectionElement<? extends E>, T> onElement);

	<T> T ofMutableElementAt(int index, Function<? super MutableObservableElement<? extends E>, T> onElement);

	/**
	 * @param search The test to search for elements that pass
	 * @param onElement The action to take on the first passing element in the collection
	 * @param first Whether to find the first or last element
	 * @return Whether an element was found that passed the test
	 * @see #find(Predicate, Consumer, boolean)
	 */
	default boolean findObservableElement(Predicate<? super E> search, Consumer<? super MutableObservableElement<? extends E>> onElement,
		boolean first) {
		return ReversibleList.super.find(search, el -> onElement.accept((MutableObservableElement<E>) el), first);
	}

	/**
	 * @param search The test to search for elements that pass
	 * @param onElement The action to take on all passing elements in the collection
	 * @param fromStart Whether to iterate from the beginning or end of the collection
	 * @return The number of elements found that passed the test
	 * @see #findAll(Predicate, Consumer, boolean)
	 */
	default int findAllObservableElements(Predicate<? super E> search, Consumer<? super MutableObservableElement<? extends E>> onElement,
		boolean fromStart) {
		return ReversibleList.super.findAll(search, el -> onElement.accept((MutableObservableElement<E>) el), fromStart);
	}

	/**
	 * Optionally replaces each value in this collection with a mapped value. For every element, the map will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param map The map to apply to each value in this collection
	 * @param soft If true, this method will attempt to determine whether each differing mapped value is acceptable as a replacement. This
	 *        may, but is not guaranteed to, prevent {@link IllegalArgumentException}s
	 * @return Whether any elements were replaced
	 * @throws IllegalArgumentException If a mapped value is not acceptable as a replacement
	 */
	default boolean replaceAll(Function<? super E, ? extends E> map, boolean soft) {
		boolean[] replaced = new boolean[1];
		MutableObservableSpliterator<E> iter = mutableSpliterator();
		iter.forEachElement(el -> {
			E value = el.get();
			E newValue = map.apply(value);
			if (value != newValue && (!soft || el.isAcceptable(newValue) == null)) {
				el.set(newValue, null);
				replaced[0] = true;
			}
		});
		return replaced[0];
	}

	/**
	 * Replaces each value in this collection with a mapped value. For every element, the operation will be applied. If the result is
	 * identically (==) different from the existing value, that element will be replaced with the mapped value.
	 *
	 * @param op The operation to apply to each value in this collection
	 */
	@Override
	default void replaceAll(UnaryOperator<E> op) {
		replaceAll(v -> op.apply(v), false);
	}

	// Default implementations of redundant Collection methods

	@Override
	default E[] toArray() {
		E[] array;
		try (Transaction t = lock(false, null)) {
			array = (E[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), size());
			int[] i = new int[1];
			spliterator().forEachRemaining(v -> array[i[0]++] = v);
		}
		return array;
	}

	@Override
	default <T> T[] toArray(T[] a) {
		ArrayList<E> ret;
		try (Transaction t = lock(false, null)) {
			ret = new ArrayList<>();
			spliterator().forEachRemaining(v -> ret.add(v));
		}
		return ret.toArray(a);
	}

	// Simple utility methods

	/**
	 * @param values The values to add to the collection
	 * @return This collection
	 */
	default ObservableCollection<E> addValues(E... values) {
		addAll(java.util.Arrays.asList(values));
		return this;
	}

	// Derived observable changes

	/** @return An observable value for the size of this collection */
	default ObservableValue<Integer> observeSize() {
		return reduce(TypeToken.of(Integer.TYPE), 0, (s, v) -> s + 1, (s, v) -> s - 1);
	}

	/**
	 * @return An observable that fires a change event whenever any elements in it are added, removed or changed. These changes are batched
	 *         by transaction when possible.
	 */
	default Observable<? extends CollectionChangeEvent<E>> changes() {
		return new ObservableCollectionImpl.CollectionChangesObservable<>(this);
	}

	/**
	 * @return An observable that fires a value (the {@link Causable#getRootCause() root cause} event of the change) whenever anything in
	 *         this collection changes. Unlike {@link #changes()}, this observable will only fire 1 event per transaction.
	 */
	default Observable<Object> simpleChanges() {
		return new Observable<Object>() {
			@Override
			public Subscription subscribe(Observer<Object> observer) {
				Object key = new Object();
				boolean[] initialized = new boolean[1];
				Subscription sub = ObservableCollection.this.subscribe(evt -> {
					if (initialized[0])
						evt.getRootCausable().onFinish(key, (root, values) -> observer.onNext(root));
				});
				initialized[0] = true;
				return sub;
			}

			@Override
			public boolean isSafe() {
				return true;
			}
		};
	}

	// Observable containment

	/**
	 * @param test The test to find passing elements for
	 * @param def Supplies a default value for the observable result when no elements in this collection pass the test
	 * @param first true to always use the first element passing the test, false to always use the last element
	 * @return An observable value containing a value in this collection passing the given test
	 */
	default ObservableValue<E> observeFind(Predicate<? super E> test, Supplier<? extends E> def, boolean first) {
		return new ObservableCollectionImpl.ObservableCollectionFinder<>(this, test, def, first);
	}

	/**
	 * @param <X> The type of the value to test
	 * @param value The value to test
	 * @return An observable boolean whose value is whether this collection contains the given value, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}
	 */
	default <X> ObservableValue<Boolean> observeContains(ObservableValue<X> value) {
		return new ObservableCollectionImpl.ContainsValue<>(this, value);
	}

	/**
	 * @param <X> The type of the collection to test
	 * @param collection The collection to test
	 * @return An observable boolean whose value is whether this collection contains every element of the given collection, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}
	 */
	default <X> ObservableValue<Boolean> observeContainsAll(ObservableCollection<X> collection) {
		return new ObservableCollectionImpl.ContainsAllValue<>(this, collection);
	}

	/**
	 * @param <X> The type of the collection to test
	 * @param collection The collection to test
	 * @return An observable boolean whose value is whether this collection contains any element of the given collection, according to
	 *         {@link #equivalence()}.{@link Equivalence#elementEquals(Object, Object) elementEquals()}
	 */
	default <X> ObservableValue<Boolean> observeContainsAny(ObservableCollection<X> collection) {
		return new ObservableCollectionImpl.ContainsAnyValue<>(this, collection);
	}

	/** @return A builder that facilitates filtering, mapping, and other operations against this collection */
	default <T> CollectionDataFlow<E, E, E> flow() {
		return new ObservableCollectionDataFlowImpl.BaseCollectionDataFlow<>(this);
	}

	/** @return An observable value containing the only value in this collection while its size==1, otherwise null TODO TEST ME! */
	default ObservableValue<E> only() {
		return new ObservableCollectionImpl.ReducedValue<E, TreeList<E>, E>(this, getType().wrap()) {
			@Override
			public E get() {
				return size() == 1 ? iterator().next() : null;
			}

			@Override
			protected TreeList<E> init() {
				return new TreeList<>();
			}

			@Override
			protected TreeList<E> update(TreeList<E> oldValue, ObservableCollectionEvent<? extends E> change) {
				switch (change.getType()) {
				case add:
					oldValue.add(change.getNewValue());
					break;
				case remove:
					oldValue.remove(change.getOldValue());
					break;
				case set:
					oldValue.findAndReplace(v -> v == change.getOldValue(), v -> change.getNewValue());
					break;
				}
				return oldValue;
			}

			@Override
			protected E getValue(TreeList<E> updated) {
				return updated.size() == 1 ? updated.get(0) : null;
			}
		};
	}

	/**
	 * Equivalent to {@link #reduce(Object, BiFunction, BiFunction)} with null for the remove function
	 *
	 * @param <T> The type of the reduced value
	 * @param init The seed value before the reduction
	 * @param reducer The reducer function to accumulate the values. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(T init, BiFunction<? super T, ? super E, T> reducer) {
		return reduce(init, reducer, null);
	}

	/**
	 * Equivalent to {@link #reduce(TypeToken, Object, BiFunction, BiFunction)} using the type derived from the reducer's return type
	 *
	 * @param <T> The type of the reduced value
	 * @param init The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(T init, BiFunction<? super T, ? super E, T> add, BiFunction<? super T, ? super E, T> remove) {
		return reduce((TypeToken<T>) TypeToken.of(add.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), init, add, remove);
	}

	/**
	 * Reduces all values in this collection to a single value
	 *
	 * @param <T> The compile-time type of the reduced value
	 * @param type The run-time type of the reduced value
	 * @param init The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> ObservableValue<T> reduce(TypeToken<T> type, T init, BiFunction<? super T, ? super E, T> add,
		BiFunction<? super T, ? super E, T> remove) {
		return new ObservableCollectionImpl.ReducedValue<E, T, T>(this, type) {
			private final T RECALC = (T) new Object(); // Placeholder indicating that the value must be recalculated from scratch

			@Override
			public T get() {
				T ret = init;
				for (E element : ObservableCollection.this)
					ret = add.apply(ret, element);
				return ret;
			}

			@Override
			protected T init() {
				return init;
			}

			@Override
			protected T update(T oldValue, ObservableCollectionEvent<? extends E> change) {
				switch (change.getType()) {
				case add:
					oldValue = add.apply(oldValue, change.getNewValue());
					break;
				case remove:
					if (remove != null)
						oldValue = remove.apply(oldValue, change.getOldValue());
					else
						oldValue = RECALC;
					break;
				case set:
					if (remove != null) {
						oldValue = remove.apply(oldValue, change.getOldValue());
						oldValue = add.apply(oldValue, change.getNewValue());
					} else
						oldValue = RECALC;
				}
				return oldValue;
			}

			@Override
			protected T getValue(T updated) {
				if (updated == RECALC)
					return get();
				else
					return updated;
			}

			@Override
			public String toString() {
				return "reduce " + ObservableCollection.this;
			}
		};
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the minimum of the values, by the given comparator
	 */
	default ObservableValue<E> minBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if (v1 == null)
				return v2;
			else if (v2 == null)
				return v1;
			else if (compare.compare(v1, v2) <= 0)
				return v1;
			else
				return v2;
		}, null);
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the maximum of the values, by the given comparator
	 */
	default ObservableValue<E> maxBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if (v1 == null)
				return v2;
			else if (v2 == null)
				return v1;
			else if (compare.compare(v1, v2) >= 0)
				return v1;
			else
				return v2;
		}, null);
	}

	/**
	 * @param <K> The compile-time key type for the multi-map
	 * @param keyType The run-time key type for the multi-map
	 * @param keyMaker The mapping function to group this collection's values by
	 * @param isStatic Whether the key function is to be evaluated statically, that is, once per value and not again as it may change
	 * @return A builder to create a multi-map containing each of this collection's elements, each in the collection of the value mapped by
	 *         the given function applied to the element
	 */
	default <K> GroupingBuilder<E, K> groupBy(TypeToken<K> keyType, Function<? super E, ? extends K> keyMaker, boolean isStatic) {
		return new GroupingBuilder<>(this, keyType, keyMaker, isStatic);
	}

	/**
	 * @param grouping The grouping builder containing the information needed to create the map
	 * @return A sorted multi-map whose keys are key-mapped elements of this collection and whose values are this collection's elements,
	 *         grouped by their mapped keys
	 */
	default <K> ObservableMultiMap<K, E> groupBy(GroupingBuilder<E, K> grouping) {
		return new ObservableCollectionImpl.GroupedMultiMap<>(this, grouping);
	}

	/**
	 * TODO TEST ME!
	 *
	 * @param grouping The grouping builder containing the information needed to create the map
	 * @return A sorted multi-map whose keys are key-mapped elements of this collection and whose values are this collection's elements,
	 *         grouped by their mapped keys
	 */
	default <K> ObservableSortedMultiMap<K, E> groupBy(SortedGroupingBuilder<E, K> grouping) {
		return new ObservableCollectionImpl.GroupedSortedMultiMap<>(this, grouping);
	}

	/**
	 * @param <E> The type for the root collection
	 * @param type The type for the root collection
	 * @param initialValues The values for the collection to contain initially
	 * @return A {@link CollectionDataFlow} that can be used to create a collection with any characteristics supported by the flow API. The
	 *         collection will be mutable unless prevented via the flow API. The flow is not {@link CollectionDataFlow#isLightWeight()
	 *         light-weight}.
	 */
	static <E> CollectionDataFlow<E, E, E> create(TypeToken<E> type, E... initialValues) {
		return create(type, Arrays.asList(initialValues));
	}

	static <E> CollectionDataFlow<E, E, E> create(TypeToken<E> type, Collection<? extends E> initialValues) {
		return ObservableCollectionImpl.create(type, initialValues);
	}

	/**
	 * @param <E> The type for the root collection
	 * @param type The type for the root collection
	 * @return A {@link CollectionDataFlow} that can be used to create an immutable collection with the given values and any characteristics
	 *         supported by the flow API.
	 */
	static <E> CollectionDataFlow<E, E, E> constant(TypeToken<E> type, E... initialValues) {
		return constant(type, Arrays.asList(initialValues));
	}

	static <E> CollectionDataFlow<E, E, E> constant(TypeToken<E> type, Collection<? extends E> initialValues) {
		return create(type, initialValues).filterModification().immutable(StdMsg.UNSUPPORTED_OPERATION, false).build();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableCollection<E> flattenValue(ObservableValue<? extends ObservableCollection<E>> collectionObservable) {
		return new ObservableCollectionImpl.FlattenedValueCollection<>(collectionObservable);
	}

	/**
	 * @param <E> The super-type of elements in the inner collections
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableCollection<E> flatten(ObservableCollection<? extends ObservableCollection<? extends E>> coll) {
		return new ObservableCollectionImpl.FlattenedObservableCollection<>(coll);
	}

	/**
	 * @param <T> An observable collection that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> ObservableCollection<T> flattenCollections(ObservableCollection<? extends T>... colls) {
		return flatten(constant(new TypeToken<ObservableCollection<? extends T>>() {}, colls).collect());
	}

	/**
	 * @param <T> The type of the folded observable
	 * @param coll The collection to fold
	 * @return An observable that is notified for every event on any observable in the collection
	 */
	public static <T> Observable<T> fold(ObservableCollection<? extends Observable<T>> coll) {
		return new Observable<T>() {
			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				HashMap<ElementId, Subscription> subscriptions = new HashMap<>();
				return coll.subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						subscriptions.put(evt.getElementId(), evt.getNewValue().subscribe(observer));
						break;
					case remove:
						subscriptions.remove(evt.getElementId()).unsubscribe();
						break;
					case set:
						if (evt.getOldValue() != evt.getNewValue()) {
							subscriptions.remove(evt.getElementId()).unsubscribe();
							subscriptions.put(evt.getElementId(), evt.getNewValue().subscribe(observer));
						}
						break;
					}
				}).removeAll();
			}

			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public String toString() {
				return "fold(" + coll + ")";
			}
		};
	}

	/**
	 * A typical hashCode implementation for collections
	 *
	 * @param coll The collection to hash
	 * @return The hash code of the collection's contents
	 */
	public static int hashCode(ObservableCollection<?> coll) {
		try (Transaction t = coll.lock(false, null)) {
			int hashCode = 1;
			for (Object e : coll)
				hashCode += e.hashCode();
			return hashCode;
		}
	}

	/**
	 * A typical equals implementation for collections
	 *
	 * @param coll The collection to test
	 * @param o The object to test the collection against
	 * @return Whether the two objects are equal
	 */
	public static <E> boolean equals(ObservableCollection<E> coll, Object o) {
		if (!(o instanceof Collection))
			return false;
		Collection<?> c = (Collection<?>) o;

		try (Transaction t1 = coll.lock(false, null); Transaction t2 = Transactable.lock(c, false, null)) {
			Iterator<E> e1 = coll.iterator();
			Iterator<?> e2 = c.iterator();
			while (e1.hasNext() && e2.hasNext()) {
				E o1 = e1.next();
				Object o2 = e2.next();
				if (!coll.equivalence().elementEquals(o1, o2))
					return false;
			}
			return !(e1.hasNext() || e2.hasNext());
		}
	}

	/**
	 * A simple toString implementation for collections
	 *
	 * @param coll The collection to print
	 * @return The string representation of the collection's contents
	 */
	public static String toString(ObservableCollection<?> coll) {
		StringBuilder ret = new StringBuilder("(");
		boolean first = true;
		try (Transaction t = coll.lock(false, null)) {
			for (Object value : coll) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(')');
		return ret.toString();
	}

	/**
	 * Allows creation of a collection that uses a collection's data as its source, but filters, maps, or otherwise transforms the data
	 *
	 * @param <E> The type of the source collection
	 * @param <I> An intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface CollectionDataFlow<E, I, T> {
		/** @return The type of collection this flow may build */
		TypeToken<T> getTargetType();

		// Flow operations

		CollectionDataFlow<E, T, T> filter(Function<? super T, String> filter);

		CollectionDataFlow<E, T, T> filterStatic(Function<? super T, String> filter);

		/**
		 * @param <X> The type for the new collection
		 * @param type The type to filter this collection by
		 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
		 *         given class
		 */
		default <X> CollectionDataFlow<E, ?, X> filter(Class<X> type) {
			return filterStatic(value -> {
				if (type == null || type.isInstance(value))
					return null;
				else
					return StdMsg.BAD_TYPE;
			}).map(TypeToken.of(type)).map(v -> (X) v);
		}

		CollectionDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence);

		CollectionDataFlow<E, T, T> refresh(Observable<?> refresh);

		CollectionDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		<X> MappedCollectionBuilder<E, T, X> map(TypeToken<X> target);

		default <X> CollectionDataFlow<E, ?, X> flatMap(TypeToken<X> target,
			Function<? super T, ? extends ObservableValue<? extends X>> map) {
			TypeToken<ObservableValue<? extends X>> valueType = new TypeToken<ObservableValue<? extends X>>() {}
			.where(new TypeParameter<X>() {}, target);
			return map(valueType).map(map).refreshEach(v -> v.noInit()).map(target).withElementSetting((ov, newValue, doSet, cause) -> {
				// Allow setting elements via the wrapped settable value
				if (!(ov instanceof SettableValue))
					return StdMsg.UNSUPPORTED_OPERATION;
				else if (newValue != null && !ov.getType().getRawType().isInstance(newValue))
					return StdMsg.BAD_TYPE;
				String msg = ((SettableValue<X>) ov).isAcceptable(newValue);
				if (msg != null)
					return msg;
				if (doSet)
					((SettableValue<X>) ov).set(newValue, cause);
				return null;
			}).map(obs -> obs == null ? null : obs.get());
		}

		<V, X> CombinedCollectionBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target);

		CollectionDataFlow<E, T, T> sorted(Comparator<? super T> compare);

		UniqueDataFlow<E, T, T> unique(boolean alwaysUseFirst);

		UniqueSortedDataFlow<E, T, T> uniqueSorted(Comparator<? super T> compare, boolean alwaysUseFirst);

		ModFilterBuilder<E, T> filterModification();

		// Terminal operations

		/**
		 * @return Whether this data flow is light weight. A light weight data flow will return a {@link #collect() collection} that does
		 *         not need to keep track of its own data, but rather performs light-weight per-access and per-operation transformations
		 *         that delegate to the base collection.
		 */
		boolean isLightWeight();

		CollectionManager<E, ?, T> manageCollection();

		ObservableCollection<T> collectLW();

		default ObservableCollection<T> collect() {
			return collect(Observable.empty);
		}

		ObservableCollection<T> collect(Observable<?> until);
	}

	/**
	 * A data flow that produces a set
	 *
	 * @param <E> The type of the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface UniqueDataFlow<E, I, T> extends CollectionDataFlow<E, I, T> {
		@Override
		UniqueDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		UniqueDataFlow<E, T, T> filterStatic(Function<? super T, String> filter);

		@Override
		default <X> UniqueDataFlow<E, T, X> filter(Class<X> type) {
			return filterStatic(value -> {
				if (type == null || type.isInstance(value))
					return null;
				else
					return StdMsg.BAD_TYPE;
			}).mapEquivalent(TypeToken.of(type)).map(v -> (X) v, v -> (T) v);
		}

		@Override
		UniqueDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		UniqueDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		/**
		 * <p>
		 * Same as {@link #map(TypeToken)}, but with the additional assertion that the produced mapped data will be one-to-one with the
		 * source data, such that the produced collection is unique in a similar way, without a need for an additional
		 * {@link #unique(boolean) uniqueness} check.
		 * </p>
		 * <p>
		 * This assertion cannot be checked (at compile time or run time), and if the assertion is incorrect such that multiple source
		 * values map to equivalent target values, <b>the resulting set will not be unique and data errors, including internal
		 * ObservableCollection errors, are possible</b>. Therefore caution should be used when considering whether to invoke this method.
		 * When in doubt, use {@link #map(TypeToken)} and {@link #unique(boolean)}.
		 * </p>
		 *
		 * @param <X> The type of the mapped values
		 * @param target The type of the mapped values
		 * @return A builder to create a set of values which are this set's values mapped by a function
		 */
		<X> UniqueMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target);

		@Override
		UniqueModFilterBuilder<E, T> filterModification();

		@Override
		ObservableSet<T> collectLW();

		@Override
		default ObservableSet<T> collect() {
			return (ObservableSet<T>) CollectionDataFlow.super.collect();
		}

		@Override
		ObservableSet<T> collect(Observable<?> until);
	}

	/**
	 * A data flow that produces a sorted set
	 *
	 * @param <E> The type of the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of collection this flow may build
	 */
	interface UniqueSortedDataFlow<E, I, T> extends UniqueDataFlow<E, I, T> {
		Comparator<? super T> comparator();

		@Override
		UniqueSortedDataFlow<E, T, T> filter(Function<? super T, String> filter);

		@Override
		UniqueSortedDataFlow<E, T, T> filterStatic(Function<? super T, String> filter);

		/**
		 * <p>
		 * Same as {@link #map(TypeToken)}, but with the additional assertion that the produced mapped data will be one-to-one with the
		 * source data, such that the produced collection is unique in a similar way, without a need for an additional
		 * {@link #uniqueSorted(Comparator, boolean) uniqueness} check.
		 * </p>
		 * <p>
		 * This assertion cannot be checked (at compile time or run time), and if the assertion is incorrect such that multiple source
		 * values map to equivalent target values, <b>the resulting set will not be unique and data errors, including internal
		 * ObservableCollection errors, are possible</b>. Therefore caution should be used when considering whether to invoke this method.
		 * When in doubt, use {@link #map(TypeToken)} and {@link #uniqueSorted(Comparator, boolean)}.
		 * </p>
		 *
		 * @param <X> The type of the mapped values
		 * @param target The type of the mapped values
		 * @return A builder to create a set of values which are this set's values mapped by a function
		 */
		@Override
		<X> UniqueSortedMappedCollectionBuilder<E, T, X> mapEquivalent(TypeToken<X> target);

		@Override
		UniqueSortedDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		UniqueSortedDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		@Override
		UniqueSortedModFilterBuilder<E, T> filterModification();

		@Override
		ObservableSortedSet<T> collectLW();

		@Override
		default ObservableSortedSet<T> collect() {
			return (ObservableSortedSet<T>) UniqueDataFlow.super.collect();
		}

		@Override
		ObservableSortedSet<T> collect(Observable<?> until);
	}

	/**
	 * Builds a filtered and/or mapped collection
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedCollectionBuilder<E, I, T> {
		private final ObservableCollection<E> theSource;
		private final AbstractDataFlow<E, ?, I> theParent;
		private final TypeToken<T> theTargetType;
		private Function<? super T, ? extends I> theReverse;
		private ElementSetter<? super I, ? super T> theElementReverse;
		private boolean reEvalOnUpdate;
		private boolean fireIfUnchanged;
		private boolean isCached;

		protected MappedCollectionBuilder(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> type) {
			theSource = source;
			theParent = parent;
			theTargetType = type;
			reEvalOnUpdate = true;
			fireIfUnchanged = true;
			isCached = true;
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected AbstractDataFlow<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<T> getTargetType() {
			return theTargetType;
		}

		protected Function<? super T, ? extends I> getReverse() {
			return theReverse;
		}

		protected ElementSetter<? super I, ? super T> getElementReverse() {
			return theElementReverse;
		}

		protected boolean isReEvalOnUpdate() {
			return reEvalOnUpdate;
		}

		protected boolean isFireIfUnchanged() {
			return fireIfUnchanged;
		}

		protected boolean isCached() {
			return isCached;
		}

		public MappedCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse) {
			theReverse = reverse;
			return this;
		}

		public MappedCollectionBuilder<E, I, T> withElementSetting(ElementSetter<? super I, ? super T> reverse) {
			theElementReverse = reverse;
			return this;
		}

		public MappedCollectionBuilder<E, I, T> reEvalOnUpdate(boolean reEval) {
			reEvalOnUpdate = reEval;
			return this;
		}

		public MappedCollectionBuilder<E, I, T> fireIfUnchanged(boolean fire) {
			fireIfUnchanged = fire;
			return this;
		}

		public MappedCollectionBuilder<E, I, T> noCache() {
			if (!fireIfUnchanged)
				throw new IllegalStateException(
					"noCache and fireIfUnchanged=false contradict. Can't know if the value is unchanged if it's not cached");
			if (!reEvalOnUpdate)
				throw new IllegalStateException(
					"noCache and reEvalOnUpdate=false contradict. If the value isn't cached, then re-evaluation is done on access.");
			isCached = false;
			return this;
		}

		public CollectionDataFlow<E, I, T> map(Function<? super I, ? extends T> map) {
			return new ObservableCollectionDataFlowImpl.MapOp<>(theSource, theParent, theTargetType, map, theReverse, theElementReverse,
				reEvalOnUpdate, fireIfUnchanged, isCached);
		}
	}

	@FunctionalInterface
	interface ElementSetter<I, T> {
		String setElement(I element, T newValue, boolean replace, Object cause);
	}

	/**
	 * Builds a filtered and/or mapped set, asserted to be similarly unique as the derived set
	 *
	 * @param <E> The type of values in the source set
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped set
	 */
	class UniqueMappedCollectionBuilder<E, I, T> extends MappedCollectionBuilder<E, I, T> {

		protected UniqueMappedCollectionBuilder(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> type) {
			super(source, parent, type);
		}

		@Override
		public UniqueMappedCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse) {
			return (UniqueMappedCollectionBuilder<E, I, T>) super.withReverse(reverse);
		}

		@Override
		public UniqueMappedCollectionBuilder<E, I, T> withElementSetting(ElementSetter<? super I, ? super T> reverse) {
			return (UniqueMappedCollectionBuilder<E, I, T>) super.withElementSetting(reverse);
		}

		@Override
		public UniqueMappedCollectionBuilder<E, I, T> reEvalOnUpdate(boolean reEval) {
			return (UniqueMappedCollectionBuilder<E, I, T>) super.reEvalOnUpdate(reEval);
		}

		@Override
		public UniqueMappedCollectionBuilder<E, I, T> fireIfUnchanged(boolean fire) {
			return (UniqueMappedCollectionBuilder<E, I, T>) super.fireIfUnchanged(fire);
		}

		@Override
		public UniqueMappedCollectionBuilder<E, I, T> noCache() {
			return (UniqueMappedCollectionBuilder<E, I, T>) super.noCache();
		}

		@Override
		public CollectionDataFlow<E, I, T> map(Function<? super I, ? extends T> map) {
			if (getReverse() != null)
				return map(map, getReverse());
			else
				return super.map(map);
		}

		public UniqueDataFlow<E, I, T> map(Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse) {
			if (reverse == null)
				throw new IllegalArgumentException("Reverse must be specified to maintain uniqueness");
			return new ObservableCollectionDataFlowImpl.UniqueMapOp<>(getSource(), getParent(), getTargetType(), map, getReverse(),
				getElementReverse(), isReEvalOnUpdate(), isFireIfUnchanged(), isCached());
		}
	}

	class UniqueSortedMappedCollectionBuilder<E, I, T> extends UniqueMappedCollectionBuilder<E, I, T> {
		protected UniqueSortedMappedCollectionBuilder(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> type) {
			super(source, parent, type);
			if (!(parent instanceof UniqueSortedDataFlow))
				throw new IllegalArgumentException("The parent of a unique-sorted map builder must be unique-sorted");
		}

		@Override
		public UniqueSortedMappedCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse) {
			return (UniqueSortedMappedCollectionBuilder<E, I, T>) super.withReverse(reverse);
		}

		@Override
		public UniqueSortedMappedCollectionBuilder<E, I, T> withElementSetting(ElementSetter<? super I, ? super T> reverse) {
			return (UniqueSortedMappedCollectionBuilder<E, I, T>) super.withElementSetting(reverse);
		}

		@Override
		public UniqueSortedMappedCollectionBuilder<E, I, T> reEvalOnUpdate(boolean reEval) {
			return (UniqueSortedMappedCollectionBuilder<E, I, T>) super.reEvalOnUpdate(reEval);
		}

		@Override
		public UniqueSortedMappedCollectionBuilder<E, I, T> fireIfUnchanged(boolean fire) {
			return (UniqueSortedMappedCollectionBuilder<E, I, T>) super.fireIfUnchanged(fire);
		}

		@Override
		public UniqueSortedMappedCollectionBuilder<E, I, T> noCache() {
			return (UniqueSortedMappedCollectionBuilder<E, I, T>) super.noCache();
		}

		@Override
		public UniqueSortedDataFlow<E, I, T> map(Function<? super I, ? extends T> map, Function<? super T, ? extends I> reverse) {
			if (reverse == null)
				throw new IllegalArgumentException("Reverse must be specified to maintain uniqueness");
			withReverse(reverse);
			return map(map, (t1, t2) -> {
				I i1 = reverse.apply(t1);
				I i2 = reverse.apply(t2);
				return ((UniqueSortedDataFlow<E, ?, I>) getParent()).comparator().compare(i1, i2);
			});
		}

		public UniqueSortedDataFlow<E, I, T> map(Function<? super I, ? extends T> map, Comparator<? super T> compare) {
			if (compare == null)
				throw new IllegalArgumentException("Comparator must be specified to maintain uniqueness");
			return new ObservableCollectionDataFlowImpl.UniqueSortedMapOp<>(getSource(), getParent(), getTargetType(), map, getReverse(),
				getElementReverse(), isReEvalOnUpdate(), isFireIfUnchanged(), isCached(), compare);
		}
	}

	/**
	 * A structure that may be used to define a collection whose elements are those of a single source collection combined with one or more
	 * values
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	interface CombinedCollectionBuilder<E, I, R> {
		<T> CombinedCollectionBuilder<E, I, R> and(ObservableValue<T> arg);

		CombinedCollectionBuilder<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse);

		CombinedCollectionBuilder<E, I, R> noCache();

		CollectionDataFlow<E, I, R> build(Function<? super CombinedValues<? extends I>, ? extends R> combination);
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with a single value. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <V> The type of the combined value
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedCollectionBuilder2<E, I, V, R> extends AbstractCombinedCollectionBuilder<E, I, R> {
		private final ObservableValue<V> theArg2;

		protected CombinedCollectionBuilder2(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<R> targetType,
			ObservableValue<V> arg2) {
			super(source, parent, targetType);
			theArg2 = arg2;
			addArg(arg2);
		}

		protected ObservableValue<V> getArg2() {
			return theArg2;
		}

		public CombinedCollectionBuilder2<E, I, V, R> withReverse(BiFunction<? super R, ? super V, ? extends I> reverse) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2)));
		}

		@Override
		public CombinedCollectionBuilder2<E, I, V, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse) {
			return (CombinedCollectionBuilder2<E, I, V, R>) super.withReverse(reverse);
		}

		@Override
		public CombinedCollectionBuilder2<E, I, V, R> noCache() {
			return (CombinedCollectionBuilder2<E, I, V, R>) super.noCache();
		}

		public CollectionDataFlow<E, I, R> build(BiFunction<? super I, ? super V, ? extends R> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theArg2)));
		}

		@Override
		public <U> CombinedCollectionBuilder3<E, I, V, U, R> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilder3<>(getSource(), getParent(), getTargetType(), theArg2, arg3);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with 2 values. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <V1> The type of the first combined value
	 * @param <V2> The type of the second combined value
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder2#and(ObservableValue)
	 */
	class CombinedCollectionBuilder3<E, I, V1, V2, R> extends AbstractCombinedCollectionBuilder<E, I, R> {
		private final ObservableValue<V1> theArg2;
		private final ObservableValue<V2> theArg3;

		protected CombinedCollectionBuilder3(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<R> targetType,
			ObservableValue<V1> arg2, ObservableValue<V2> arg3) {
			super(source, parent, targetType);
			theArg2 = arg2;
			theArg3 = arg3;
			addArg(arg2);
			addArg(arg3);
		}

		protected ObservableValue<V1> getArg2() {
			return theArg2;
		}

		protected ObservableValue<V2> getArg3() {
			return theArg3;
		}

		public CombinedCollectionBuilder3<E, I, V1, V2, R> withReverse(
			TriFunction<? super R, ? super V1, ? super V2, ? extends I> reverse) {
			return withReverse(cv -> reverse.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)));
		}

		@Override
		public CombinedCollectionBuilder3<E, I, V1, V2, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse) {
			return (CombinedCollectionBuilder3<E, I, V1, V2, R>) super.withReverse(reverse);
		}

		@Override
		public CombinedCollectionBuilder3<E, I, V1, V2, R> noCache() {
			return (CombinedCollectionBuilder3<E, I, V1, V2, R>) super.noCache();
		}

		public CollectionDataFlow<E, I, R> build(TriFunction<? super I, ? super V1, ? super V2, ? extends R> combination) {
			return build(cv -> combination.apply(cv.getElement(), cv.get(theArg2), cv.get(theArg3)));
		}

		@Override
		public <T2> CombinedCollectionBuilderN<E, I, R> and(ObservableValue<T2> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedCollectionBuilderN<>(getSource(), getParent(), getTargetType(), theArg2, theArg3, arg);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} for the combination of a collection with one or more (typically at least 3)
	 * values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <I> Intermediate type
	 * @param <R> The type of elements in the resulting collection
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableCollection.CombinedCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedCollectionBuilderN<E, I, R> extends AbstractCombinedCollectionBuilder<E, I, R> {
		protected CombinedCollectionBuilderN(ObservableCollection<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<R> targetType,
			ObservableValue<?> arg2, ObservableValue<?> arg3, ObservableValue<?> arg4) {
			super(source, parent, targetType);
			addArg(arg2);
			addArg(arg3);
			addArg(arg4);
		}

		@Override
		public CombinedCollectionBuilderN<E, I, R> withReverse(Function<? super CombinedValues<? extends R>, ? extends I> reverse) {
			return (CombinedCollectionBuilderN<E, I, R>) super.withReverse(reverse);
		}

		@Override
		public CombinedCollectionBuilderN<E, I, R> noCache() {
			return (CombinedCollectionBuilderN<E, I, R>) super.noCache();
		}

		@Override
		public <T> CombinedCollectionBuilderN<E, I, R> and(ObservableValue<T> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			addArg(arg);
			return this;
		}
	}

	/**
	 * A structure that is operated on to produce the elements of a combined collection
	 *
	 * @param <E> The type of the source element (or the value to be reversed)
	 * @see ObservableCollection.CollectionDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	interface CombinedValues<E> {
		E getElement();

		<T> T get(ObservableValue<T> arg);
	}

	/**
	 * Allows creation of a collection that reflects a collection's data, but may limit the operations the user can perform on the data or
	 * when the user can observe the data
	 *
	 * @param <E> The type of the source collection
	 * @param <T> The type of the collection to builder modification on
	 */
	class ModFilterBuilder<E, T> {
		private final ObservableCollection<E> theSource;
		private final CollectionDataFlow<E, ?, T> theParent;
		private String theImmutableMsg;
		private boolean allowUpdates;
		private String theAddMsg;
		private String theRemoveMsg;
		private Function<? super T, String> theAddMsgFn;
		private Function<? super T, String> theRemoveMsgFn;

		public ModFilterBuilder(ObservableCollection<E> source, CollectionDataFlow<E, ?, T> parent) {
			theSource = source;
			theParent = parent;
		}

		protected ObservableCollection<E> getSource() {
			return theSource;
		}

		protected CollectionDataFlow<E, ?, T> getParent() {
			return theParent;
		}

		protected String getImmutableMsg() {
			return theImmutableMsg;
		}

		protected boolean areUpdatesAllowed() {
			return allowUpdates;
		}

		protected String getAddMsg() {
			return theAddMsg;
		}

		protected String getRemoveMsg() {
			return theRemoveMsg;
		}

		protected Function<? super T, String> getAddMsgFn() {
			return theAddMsgFn;
		}

		protected Function<? super T, String> getRemoveMsgFn() {
			return theRemoveMsgFn;
		}

		public ModFilterBuilder<E, T> immutable(String modMsg, boolean allowUpdates) {
			theImmutableMsg = modMsg;
			this.allowUpdates = allowUpdates;
			return this;
		}

		public ModFilterBuilder<E, T> noAdd(String modMsg) {
			theAddMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<E, T> noRemove(String modMsg) {
			theRemoveMsg = modMsg;
			return this;
		}

		public ModFilterBuilder<E, T> filterAdd(Function<? super T, String> messageFn) {
			theAddMsgFn = messageFn;
			return this;
		}

		public ModFilterBuilder<E, T> filterRemove(Function<? super T, String> messageFn) {
			theRemoveMsgFn = messageFn;
			return this;
		}

		public CollectionDataFlow<E, T, T> build() {
			return new ObservableCollectionDataFlowImpl.ModFilteredOp<>(theSource, (AbstractDataFlow<E, ?, T>) theParent, theImmutableMsg,
				allowUpdates, theAddMsg, theRemoveMsg, theAddMsgFn, theRemoveMsgFn);
		}
	}

	class UniqueModFilterBuilder<E, T> extends ModFilterBuilder<E, T> {
		public UniqueModFilterBuilder(ObservableCollection<E> source, UniqueDataFlow<E, ?, T> parent) {
			super(source, parent);
		}

		@Override
		public UniqueModFilterBuilder<E, T> immutable(String modMsg, boolean allowUpdates) {
			return (UniqueModFilterBuilder<E, T>) super.immutable(modMsg, allowUpdates);
		}

		@Override
		public UniqueModFilterBuilder<E, T> noAdd(String modMsg) {
			return (UniqueModFilterBuilder<E, T>) super.noAdd(modMsg);
		}

		@Override
		public UniqueModFilterBuilder<E, T> noRemove(String modMsg) {
			return (UniqueModFilterBuilder<E, T>) super.noRemove(modMsg);
		}

		@Override
		public UniqueModFilterBuilder<E, T> filterAdd(Function<? super T, String> messageFn) {
			return (UniqueModFilterBuilder<E, T>) super.filterAdd(messageFn);
		}

		@Override
		public UniqueModFilterBuilder<E, T> filterRemove(Function<? super T, String> messageFn) {
			return (UniqueModFilterBuilder<E, T>) super.filterRemove(messageFn);
		}

		@Override
		public UniqueDataFlow<E, T, T> build() {
			return new ObservableCollectionDataFlowImpl.UniqueModFilteredOp<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent(),
				getImmutableMsg(), areUpdatesAllowed(), getAddMsg(), getRemoveMsg(), getAddMsgFn(), getRemoveMsgFn());
		}
	}

	class UniqueSortedModFilterBuilder<E, T> extends UniqueModFilterBuilder<E, T> {
		public UniqueSortedModFilterBuilder(ObservableCollection<E> source, UniqueSortedDataFlow<E, ?, T> parent) {
			super(source, parent);
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> immutable(String modMsg, boolean allowUpdates) {
			return (UniqueSortedModFilterBuilder<E, T>) super.immutable(modMsg, allowUpdates);
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> noAdd(String modMsg) {
			return (UniqueSortedModFilterBuilder<E, T>) super.noAdd(modMsg);
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> noRemove(String modMsg) {
			return (UniqueSortedModFilterBuilder<E, T>) super.noRemove(modMsg);
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterAdd(Function<? super T, String> messageFn) {
			return (UniqueSortedModFilterBuilder<E, T>) super.filterAdd(messageFn);
		}

		@Override
		public UniqueSortedModFilterBuilder<E, T> filterRemove(Function<? super T, String> messageFn) {
			return (UniqueSortedModFilterBuilder<E, T>) super.filterRemove(messageFn);
		}

		@Override
		public UniqueSortedDataFlow<E, T, T> build() {
			return new ObservableCollectionDataFlowImpl.UniqueSortedModFilteredOp<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent(),
				getImmutableMsg(), areUpdatesAllowed(), getAddMsg(), getRemoveMsg(), getAddMsgFn(), getRemoveMsgFn());
		}
	}

	/**
	 * Builds a grouping definition that can be used to create a collection-backed multi-map
	 *
	 * @param <E> The type of values in the collection, which will be the type of values in the multi-map
	 * @param <K> The key type for the multi-map
	 * @see ObservableCollection#groupBy(TypeToken, Function, boolean)
	 * @see ObservableCollection#groupBy(GroupingBuilder)
	 */
	class GroupingBuilder<E, K> {
		private final ObservableCollection<E> theCollection;
		private final TypeToken<K> theKeyType;
		private final Function<? super E, ? extends K> theKeyMaker;
		private final boolean isStatic;
		private Equivalence<? super K> theEquivalence = Equivalence.DEFAULT;
		private boolean isAlwaysUsingFirst;
		private boolean isBuilt;

		public GroupingBuilder(ObservableCollection<E> collection, TypeToken<K> keyType, Function<? super E, ? extends K> keyMaker,
			boolean isStatic) {
			theCollection = collection;
			theKeyType = keyType;
			theKeyMaker = keyMaker;
			this.isStatic = isStatic;
		}

		public ObservableCollection<E> getCollection() {
			return theCollection;
		}

		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		public Function<? super E, ? extends K> getKeyMaker() {
			return theKeyMaker;
		}

		public boolean isStatic() {
			return isStatic;
		}

		public GroupingBuilder<E, K> withEquivalence(Equivalence<? super K> equivalence) {
			if (isBuilt)
				throw new IllegalStateException("Cannot change the grouping builder's properties after building");
			theEquivalence = equivalence;
			return this;
		}

		public Equivalence<? super K> getEquivalence() {
			return theEquivalence;
		}

		public GroupingBuilder<E, K> alwaysUseFirst() {
			if (isBuilt)
				throw new IllegalStateException("Cannot change the grouping builder's properties after building");
			isAlwaysUsingFirst = true;
			return this;
		}

		public boolean isAlwaysUsingFirst() {
			return isAlwaysUsingFirst;
		}

		public SortedGroupingBuilder<E, K> sorted(Comparator<? super K> compare) {
			return new SortedGroupingBuilder<>(this, compare);
		}

		public ObservableMultiMap<K, E> build() {
			isBuilt = true;
			return theCollection.groupBy(this);
		}
	}

	/**
	 * Builds a sorted grouping definition that can be used to create a collection-backed sorted multi-map
	 *
	 * @param <E> The type of values in the collection, which will be the type of values in the multi-map
	 * @param <K> The key type for the multi-map
	 * @see ObservableCollection#groupBy(TypeToken, Function, boolean)
	 * @see ObservableCollection.GroupingBuilder#sorted(Comparator)
	 * @see ObservableCollection#groupBy(SortedGroupingBuilder)
	 */
	class SortedGroupingBuilder<E, K> extends GroupingBuilder<E, K> {
		private final Comparator<? super K> theCompare;

		public SortedGroupingBuilder(GroupingBuilder<E, K> basicBuilder, Comparator<? super K> compare) {
			super(basicBuilder.getCollection(), basicBuilder.getKeyType(), basicBuilder.getKeyMaker(), basicBuilder.isStatic());
			theCompare = compare;
		}

		@Override
		public SortedGroupingBuilder<E, K> alwaysUseFirst() {
			return (SortedGroupingBuilder<E, K>) super.alwaysUseFirst();
		}

		@Override
		public ObservableSortedMultiMap<K, E> build() {
			return getCollection().groupBy(this);
		}

		public Comparator<? super K> getCompare() {
			return theCompare;
		}
	}
}
