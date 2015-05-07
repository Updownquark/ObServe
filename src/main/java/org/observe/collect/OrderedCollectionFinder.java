package org.observe.collect;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

/**
 * Finds something in an {@link ObservableOrderedCollection}
 *
 * @param <E> The type of value to find
 */
public class OrderedCollectionFinder<E> implements ObservableValue<E> {
	private final ObservableOrderedCollection<E> theCollection;

	private final Type theType;

	private final Predicate<? super E> theFilter;

	private final boolean isForward;

	OrderedCollectionFinder(ObservableOrderedCollection<E> collection, Predicate<? super E> filter, boolean forward) {
		theCollection = collection;
		theType = theCollection.getType().isPrimitive() ? new Type(Type.getWrapperType(theCollection.getType().getBaseType()))
		: theCollection.getType();
		theFilter = filter;
		isForward = forward;
	}

	/** @return The collection that this finder searches */
	public ObservableOrderedCollection<E> getCollection() {
		return theCollection;
	}

	/** @return The function to test elements with */
	public Predicate<? super E> getFilter() {
		return theFilter;
	}

	/** @return Whether this finder searches forward or backward in the collection */
	public boolean isForward() {
		return isForward;
	}

	@Override
	public Type getType() {
		return theType;
	}

	@Override
	public E get() {
		if(isForward) {
			for(E element : theCollection) {
				if(theFilter.test(element))
					return element;
			}
			return null;
		} else {
			E ret = null;
			for(E element : theCollection) {
				if(theFilter.test(element))
					ret = element;
			}
			return ret;
		}
	}

	@Override
	public Runnable observe(Observer<? super ObservableValueEvent<E>> observer) {
		final Object key = new Object();
		int [] index = new int[] {-1};
		Runnable collSub = theCollection.onElement(new Consumer<ObservableElement<E>>() {
			private E theValue;

			@Override
			public void accept(ObservableElement<E> element) {
				element.observe(new Observer<ObservableValueEvent<E>>() {
					@Override
					public <V3 extends ObservableValueEvent<E>> void onNext(V3 value) {
						int listIndex = ((OrderedObservableElement<?>) value.getObservable()).getIndex();
						if(index[0] < 0 || isBetterIndex(listIndex, index[0])) {
							if(theFilter.test(value.getValue()))
								newBest(value.getValue(), listIndex);
							else if(listIndex == index[0])
								findNextBest(listIndex + 1);
						}
					}

					@Override
					public <V3 extends ObservableValueEvent<E>> void onCompleted(V3 value) {
						int listIndex = ((OrderedObservableElement<?>) value.getObservable()).getIndex();
						if(listIndex == index[0]) {
							findNextBest(listIndex + 1);
						} else if(listIndex < index[0])
							index[0]--;
					}

					private boolean isBetterIndex(int test, int current) {
						if(isForward)
							return test <= current;
						else
							return test >= current;
					}

					private void findNextBest(int newIndex) {
						boolean found = false;
						java.util.Iterator<E> iter = theCollection.iterator();
						int idx = 0;
						for(idx = 0; iter.hasNext() && idx < newIndex; idx++)
							iter.next();
						for(; iter.hasNext(); idx++) {
							E val = iter.next();
							if(theFilter.test(val)) {
								found = true;
								newBest(val, idx);
								break;
							}
						}
						if(!found)
							newBest(null, -1);
					}
				});
			}

			void newBest(E value, int newIndex) {
				E oldValue = theValue;
				theValue = value;
				index[0] = newIndex;
				CollectionSession session = theCollection.getSession().get();
				if(session == null)
					observer.onNext(createEvent(oldValue, theValue, null));
				else {
					session.putIfAbsent(key, "oldBest", oldValue);
					session.put(key, "newBest", theValue);
				}
			}
		});
		if(index[0] < 0)
			observer.onNext(createEvent(null, null, null));
		Runnable transSub = theCollection.getSession().observe(new Observer<ObservableValueEvent<CollectionSession>>() {
			@Override
			public <V2 extends ObservableValueEvent<CollectionSession>> void onNext(V2 value) {
				CollectionSession completed = value.getOldValue();
				if(completed == null)
					return;
				E oldBest = (E) completed.get(key, "oldBest");
				E newBest = (E) completed.get(key, "newBest");
				if(oldBest == null && newBest == null)
					return;
				observer.onNext(createEvent(oldBest, newBest, value));
			}
		});
		return () -> {
			collSub.run();
			transSub.run();
		};
	}

	@Override
	public String toString() {
		return "find in " + theCollection;
	}
}