package org.observe.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

import org.observe.DefaultObservable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedElement;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/** Utility methods for observables */
public class ObservableUtils {
	/**
	 * Turns a list of observable values into a list composed of those holders' values
	 *
	 * @param <T> The type of elements held in the values
	 * @param type The run-time type of elements held in the values
	 * @param list The list to flatten
	 * @return The flattened list
	 */
	public static <T> ObservableList<T> flattenListValues(TypeToken<T> type, ObservableList<? extends ObservableValue<T>> list) {
		class FlattenedList implements ObservableList.PartialListImpl<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return list.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return list.lock(write, cause);
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> observer) {
				return list.onElement(element ->observer.accept(new ObservableOrderedElement<T>() {
					@Override
					public TypeToken<T> getType() {
						return type != null ? type : element.get().getType();
					}

					@Override
					public T get() {
						return get(element.get());
					}

					@Override
					public int getIndex() {
						return ((ObservableOrderedElement<?>) element).getIndex();
					}

					@Override
					public ObservableValue<T> persistent() {
						return ObservableValue.flatten(getType(), element.persistent());
					}

					private T get(ObservableValue<? extends T> value) {
						return value == null ? null : value.get();
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
						ObservableOrderedElement<T> retObs = this;
						return element.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V2 value) {
								if(value.getValue() != null) {
									value
									.getValue()
									.takeUntil(element.noInit())
									.act(
											innerEvent -> {
												observer2.onNext(ObservableUtils.wrap(innerEvent, retObs));
											});
								} else if(value.isInitial())
									observer2.onNext(retObs.createInitialEvent(null));
								else
									observer2.onNext(retObs.createChangeEvent(get(value.getOldValue()), null, value.getCause()));
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onCompleted(V2 value) {
								if(value.isInitial())
									observer2.onCompleted(retObs.createInitialEvent(get(value.getValue())));
								else
									observer2.onCompleted(retObs.createChangeEvent(get(value.getOldValue()), get(value.getValue()),
											value.getCause()));
							}
						});
					}
				}));
			}

			@Override
			public T get(int index) {
				return list.get(index).get();
			}

			@Override
			public int size() {
				return list.size();
			}

			@Override
			public String toString() {
				return "flatValue(" + list + ")";
			}
		}
		return new FlattenedList();
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <T> The type of elements held in the values
	 * @param type The run-time type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <T> ObservableCollection<T> flattenValues(TypeToken<T> type,
			ObservableCollection<? extends ObservableValue<T>> collection) {
		class FlattenedCollection implements ObservableCollection.PartialCollectionImpl<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return collection.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return collection.lock(write, cause);
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
				return collection.onElement(element -> observer.accept(new ObservableElement<T>() {
					@Override
					public TypeToken<T> getType() {
						return type != null ? type : element.get().getType();
					}

					@Override
					public T get() {
						return get(element.get());
					}

					@Override
					public ObservableValue<T> persistent() {
						return ObservableValue.flatten(getType(), element.persistent());
					}

					private T get(ObservableValue<? extends T> value) {
						return value == null ? null : value.get();
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer2) {
						ObservableElement<T> retObs = this;
						return element.subscribe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V2 value) {
								if(value.getValue() != null) {
									value
									.getValue()
									.takeUntil(element.noInit())
									.act(
											innerEvent -> {
												observer2.onNext(ObservableUtils.wrap(innerEvent, retObs));
											});
								} else if(value.isInitial())
									observer2.onNext(retObs.createInitialEvent(null));
								else
									observer2.onNext(retObs.createChangeEvent(get(value.getOldValue()), null, value.getCause()));
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onCompleted(V2 value) {
								if(value.isInitial())
									observer2.onCompleted(retObs.createInitialEvent(get(value.getValue())));
								else
									observer2.onCompleted(retObs.createChangeEvent(get(value.getOldValue()), get(value.getValue()),
											value.getCause()));
							}
						});
					}
				}));
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<? extends ObservableValue<T>> wrapped = collection.iterator();

					@Override
					public boolean hasNext() {
						return wrapped.hasNext();
					}

					@Override
					public T next() {
						return wrapped.next().get();
					}

					@Override
					public void remove() {
						wrapped.remove();
					}
				};
			}

			@Override
			public int size() {
				return collection.size();
			}
		}
		return new FlattenedCollection();
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 * 
	 * @param type The type of elements in the collection
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <T> ObservableCollection<T> flattenValue(TypeToken<T> type,
			ObservableValue<ObservableCollection<T>> collectionObservable) {
		class FlattenedCollectionObservable implements ObservableCollection.PartialCollectionImpl<T> {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.flatten(new TypeToken<CollectionSession>() {}, collectionObservable.mapV(coll -> coll.getSession()));
			}

			@Override
			public int size() {
				ObservableCollection<T> coll = collectionObservable.get();
				return coll == null ? 0 : coll.size();
			}

			@Override
			public Iterator<T> iterator() {
				ObservableCollection<T> coll = collectionObservable.get();
				return coll == null ? Collections.EMPTY_LIST.iterator() : coll.iterator();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				ObservableCollection<T> coll = collectionObservable.get();
				return coll == null ? () -> {
				} : coll.lock(write, cause);
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<T>> onElement) {
				return collectionObservable.subscribe(new Observer<ObservableValueEvent<ObservableCollection<T>>>() {
					private ObservableCollection<T> theCurrent;
					private DefaultObservable<Void> theEnd;
					private Observer<Void> theEndControl;

					@Override
					public <V extends ObservableValueEvent<ObservableCollection<T>>> void onNext(V event) {
						theEndControl.onNext(null);
						theCurrent = event.getValue();
						if (theCurrent != null) {
							theCurrent.onElement(element -> onElement.accept(element.takeUntil(theEnd)));
						}
					}

					@Override
					public <V extends ObservableValueEvent<ObservableCollection<T>>> void onCompleted(V event) {
						theEndControl.onNext(null);
						theCurrent = null;
					}
				});
			}
		}
		return new FlattenedCollectionObservable();
	}

	/**
	 * Turns an observable value containing an observable list into the contents of the value
	 * 
	 * @param type The type of elements in the list
	 * @param listObservable The observable value
	 * @return A list representing the contents of the value, or a zero-length list when null
	 */
	public static <T> ObservableList<T> flattenListValue(TypeToken<T> type, ObservableValue<ObservableList<T>> listObservable) {
		class FlattenedListObservable implements ObservableList.PartialListImpl<T> {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.flatten(new TypeToken<CollectionSession>() {}, listObservable.mapV(list -> list.getSession()));
			}

			@Override
			public int size() {
				ObservableList<T> list = listObservable.get();
				return list == null ? 0 : list.size();
			}

			@Override
			public T get(int index) {
				ObservableList<T> list = listObservable.get();
				if (list == null)
					throw new IndexOutOfBoundsException(index + " of 0");
				return list.get(index);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				ObservableList<T> list = listObservable.get();
				return list == null ? () -> {
				} : list.lock(write, cause);
			}

			@Override
			public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<T>> onElement) {
				return listObservable.subscribe(new Observer<ObservableValueEvent<ObservableList<T>>>() {
					private ObservableList<T> theCurrent;
					private DefaultObservable<Void> theEnd = new DefaultObservable<>();
					private Observer<Void> theEndControl = theEnd.control(null);

					@Override
					public <V extends ObservableValueEvent<ObservableList<T>>> void onNext(V event) {
						theEndControl.onNext(null);
						theCurrent = event.getValue();
						if (theCurrent != null) {
							theCurrent.onOrderedElement(element -> onElement.accept(element.takeUntil(theEnd)));
						}
					}

					@Override
					public <V extends ObservableValueEvent<ObservableList<T>>> void onCompleted(V event) {
						theEndControl.onNext(null);
						theCurrent = null;
					}
				});
			}
		}
		return new FlattenedListObservable();
	}

	/**
	 * Wraps an event from an observable value to use a different observable value as the source
	 *
	 * @param <T> The type of the value to wrap an event for
	 * @param event The event to wrap
	 * @param wrapper The wrapper observable to wrap the event for
	 * @return An event with the same values as the given event, but created by the given observable
	 */
	public static <T> ObservableValueEvent<T> wrap(ObservableValueEvent<? extends T> event, ObservableValue<T> wrapper) {
		if(event.isInitial())
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

	private static class ControllableObservableList<T> extends ObservableListWrapper<T> {
		private volatile boolean isControlled;

		public ControllableObservableList(ObservableList<T> wrap) {
			super(wrap, false);
		}

		protected ObservableList<T> getController() {
			if(isControlled)
				throw new IllegalStateException("This list is already controlled");
			isControlled=true;
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
		if(!(controllableList instanceof ControllableObservableList))
			throw new IllegalArgumentException("This list is not controllable.  Use control(ObservableList) to create a controllable list");
		return ((ControllableObservableList<T>) controllableList).getController();
	}
}
