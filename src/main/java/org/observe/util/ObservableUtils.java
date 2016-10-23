package org.observe.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableSet;
import org.qommons.Equalizer;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/** Utility methods for observables */
public class ObservableUtils {
	/**
	 * Wraps an event from an observable value to use a different observable value as the source
	 *
	 * @param <T> The type of the value to wrap an event for
	 * @param event The event to wrap
	 * @param wrapper The wrapper observable to wrap the event for
	 * @return An event with the same values as the given event, but created by the given observable
	 */
	public static <T> ObservableValueEvent<T> wrap(ObservableValueEvent<? extends T> event, ObservableValue<T> wrapper) {
		if (event.isInitial())
			return wrapper.createInitialEvent(event.getValue());
		else
			return wrapper.createChangeEvent(event.getOldValue(), event.getValue(), event.getCause());
	}

	/**
	 * Wraps all events from an observable value to use a different observable value as the source
	 *
	 * @param <T> The type of the value to wrap events for
	 * @param value The observable value whose events to wrap
	 * @param wrapper The wrapper observable to wrap the events for
	 * @param observer The observer interested in the wrapped events
	 * @return The subscription to unsubscribe from the wrapped events
	 */
	public static <T> Subscription wrap(ObservableValue<? extends T> value, ObservableValue<T> wrapper,
		Observer<? super ObservableValueEvent<T>> observer) {
		return value.subscribe(new Observer<ObservableValueEvent<? extends T>>() {
			@Override
			public <V extends ObservableValueEvent<? extends T>> void onNext(V event) {
				observer.onNext(wrap(event, wrapper));
			}

			@Override
			public <V extends ObservableValueEvent<? extends T>> void onCompleted(V event) {
				observer.onCompleted(wrap(event, wrapper));
			}
		});
	}

	/**
	 * A seemingly narrow use case. Makes an observable to be used as the until in
	 * {@link org.observe.collect.ObservableCollection#takeUntil(Observable)} when this will be called as a result of an observable value
	 * containing an observable collection being called
	 *
	 * @param value The collection-containing value
	 * @param cause The event on the value that is the cause of this call
	 * @return The until observable to use
	 */
	public static Observable<?> makeUntil(ObservableValue<?> value, ObservableValueEvent<?> cause) {
		Observable<?> until = value.noInit().fireOnComplete();
		if (!cause.isInitial()) {
			/* If we don't do this, the listener for the until will get added to the end of the queue and will be
			 * called for the same change event we're in now.  So we skip one. */
			until = until.skip(1);
		}
		return until;
	}

	private static class ControllableObservableList<T> extends ObservableListWrapper<T> {
		private volatile boolean isControlled;

		public ControllableObservableList(ObservableList<T> wrap) {
			super(wrap, false);
		}

		protected ObservableList<T> getController() {
			if (isControlled)
				throw new IllegalStateException("This list is already controlled");
			isControlled = true;
			return super.getWrapped();
		}
	}

	/**
	 * A mechanism for passing controllable lists to super constructors
	 *
	 * @param <T> The type of the list
	 * @param list The list to control
	 * @return A list that cannot be modified directly and for which a single call to {@link #getController(ObservableList)} will return a
	 *         modifiable list, changes to which will be reflected in the return value
	 */
	public static <T> ObservableList<T> control(ObservableList<T> list) {
		return new ControllableObservableList<>(list);
	}

	/**
	 * Gets the controller for a list created by {@link #control(ObservableList)}
	 *
	 * @param <T> The type of the list
	 * @param controllableList The controllable list
	 * @return The controller for the list
	 * @throws IllegalArgumentException If the given list was not created by {@link #control(ObservableList)}
	 * @throws IllegalStateException If the given list is already controlled
	 */
	public static <T> ObservableList<T> getController(ObservableList<T> controllableList) {
		if (!(controllableList instanceof ControllableObservableList))
			throw new IllegalArgumentException("This list is not controllable.  Use control(ObservableList) to create a controllable list");
		return ((ControllableObservableList<T>) controllableList).getController();
	}

	public static <V, T> ObservableCollection<List<T>> getPaths(V root, TypeToken<T> type,
		Function<V, ? extends ObservableCollection<? extends T>> target, Function<V, ? extends ObservableValue<? extends V>> parent) {
		return new ObservablePathCollection<>(root, type, target, parent);
	}

	public static <V, T> ObservableSet<List<T>> getPathSet(V root, TypeToken<T> type,
		Function<V, ? extends ObservableSet<? extends T>> target, Function<V, ? extends ObservableValue<? extends V>> parent) {
		return new ObservablePathSet<>(root, type, target, parent);
	}

	static class ObservablePathCollection<V, T> implements ObservableCollection.PartialCollectionImpl<List<T>> {
		private final TypeToken<T> theType;
		private final V theRoot;
		private final Function<V, ? extends ObservableCollection<? extends T>> theTargetFunction;
		private final Function<V, ? extends ObservableValue<? extends V>> theParentFunction;

		ObservablePathCollection(V root, TypeToken<T> type, Function<V, ? extends ObservableCollection<? extends T>> target,
			Function<V, ? extends ObservableValue<? extends V>> parent) {
			theType = type;
			theRoot = root;
			theTargetFunction = target;
			theParentFunction = parent;
		}

		@Override
		public TypeToken<List<T>> getType() {
			return new TypeToken<List<T>>() {}.where(new TypeParameter<T>() {}, theType);
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
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(List<T> value) {
			return false;
		}

		@Override
		public int size() {
			V el = theRoot;
			int size = theTargetFunction.apply(el).size();
			if (size == 0)
				return 0;
			el = theParentFunction.apply(el).get();
			while (el != null) {
				int elSize = theTargetFunction.apply(el).size();
				if (elSize == 0)
					break;
				size *= (elSize + 1);
				el = theParentFunction.apply(el).get();
			}
			return size;
		}

		@Override
		public Iterator<List<T>> iterator() {
			return new PathIterator();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<List<T>>> onElement) {
			// TODO Auto-generated method stub
			return null;
		}

		class PathIterator implements Iterator<List<T>> {
			private V theElement = theRoot;
			private LinkedList<List<T>> theOldPaths = new LinkedList<>();
			private Iterator<? extends T> theElementTargetIter = theTargetFunction.apply(theRoot).iterator();
			private T theNextTarget;
			private Iterator<List<T>> theOldPathIterator;
			private LinkedList<List<T>> theNewPaths = new LinkedList<>();

			{
				if (theElementTargetIter.hasNext()) {
					theNextTarget = theElementTargetIter.next();
					theOldPaths.add(Arrays.asList()); // If there are initial targets, seed old paths with empty list
					theOldPathIterator = theOldPaths.iterator();
				}
			}

			@Override
			public boolean hasNext() {
				return theOldPathIterator.hasNext();
			}

			@Override
			public List<T> next() {
				List<T> oldPath = theOldPathIterator.next();
				List<T> newPath = new ArrayList<>(oldPath.size() + 1);
				newPath.addAll(oldPath);
				newPath.add(theNextTarget);
				theNewPaths.add(newPath);

				// Set up for the next path
				if (!theOldPathIterator.hasNext()) {
					while (!theElementTargetIter.hasNext()) {
						theElement = theParentFunction.apply(theElement).get();
						if (theElement == null)
							break;
						theElementTargetIter = theTargetFunction.apply(theElement).iterator();
					}
					if (theElementTargetIter.hasNext()) { // Will be true iff theElement!=null
						theNextTarget = theElementTargetIter.next();
						LinkedList<List<T>> transfer = theOldPaths;
						theOldPaths = theNewPaths;
						transfer.clear();
						theNewPaths = transfer;
						theOldPathIterator = theOldPaths.iterator();
					}
				}

				return newPath;
			}
		}
	}

	static class ObservablePathSet<V, T> extends ObservablePathCollection<V, T> implements ObservableSet<List<T>> {
		ObservablePathSet(V root, TypeToken<T> type, Function<V, ? extends ObservableSet<? extends T>> target,
			Function<V, ? extends ObservableValue<? extends V>> parent) {
			super(root, type, target, parent);
		}

		@Override
		public void clear() {
			super.clear();
		}

		@Override
		public boolean removeAll(Collection<?> coll) {
			return super.retainAll(coll);
		}

		@Override
		public boolean retainAll(Collection<?> coll) {
			return super.retainAll(coll);
		}

		@Override
		public boolean remove(Object o) {
			return super.remove(o);
		}

		@Override
		public Equalizer getEqualizer() {
			return Object::equals; // We'll just assume. Potentially each collection returned by the function could have a different one.
		}
	}
}
