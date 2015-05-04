package org.observe.util;

import java.util.Iterator;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.OrderedObservableElement;

import prisms.lang.Type;

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
	public static <T> ObservableList<T> flattenListValues(Type type, ObservableList<? extends ObservableValue<T>> list) {
		class FlattenedList extends java.util.AbstractList<T> implements ObservableList<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return list.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return list.onElement(element ->observer.accept(new OrderedObservableElement<T>() {
					@Override
					public Type getType() {
						return type != null ? type : element.get().getType();
					}

					@Override
					public T get() {
						return get(element.get());
					}

					@Override
					public int getIndex() {
						return ((OrderedObservableElement<?>) element).getIndex();
					}

					@Override
					public ObservableValue<T> persistent() {
						return ObservableValue.flatten(getType(), element.persistent());
					}

					private T get(ObservableValue<? extends T> value) {
						return value == null ? null : value.get();
					}

					@Override
					public Runnable observe(Observer<? super ObservableValueEvent<T>> observer2) {
						OrderedObservableElement<T> retObs = this;
						return element.observe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V2 value) {
								if(value.getValue() != null) {
									value
									.getValue()
									.takeUntil(element.noInit())
									.act(
										innerEvent -> {
											observer2.onNext(new ObservableValueEvent<>(retObs, innerEvent.getOldValue(), innerEvent
												.getValue(), innerEvent));
										});
								} else {
									observer2.onNext(new ObservableValueEvent<>(retObs, get(value.getOldValue()), null, value.getCause()));
								}
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onCompleted(V2 value) {
								observer2.onCompleted(new ObservableValueEvent<>(retObs, get(value.getOldValue()), get(value.getValue()),
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
	public static <T> ObservableCollection<T> flattenValues(Type type, ObservableCollection<? extends ObservableValue<T>> collection) {
		class FlattenedCollection extends java.util.AbstractCollection<T> implements org.observe.collect.ObservableCollection<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return collection.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return collection.onElement(element -> observer.accept(new ObservableElement<T>() {
					@Override
					public Type getType() {
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
					public Runnable observe(Observer<? super ObservableValueEvent<T>> observer2) {
						ObservableElement<T> retObs = this;
						return element.observe(new Observer<ObservableValueEvent<? extends ObservableValue<? extends T>>>() {
							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onNext(V2 value) {
								if(value.getValue() != null) {
									value
									.getValue()
									.takeUntil(element.noInit())
									.act(
										innerEvent -> {
											observer2.onNext(new ObservableValueEvent<>(retObs, innerEvent.getOldValue(), innerEvent
												.getValue(), innerEvent));
										});
								} else {
									observer2.onNext(new ObservableValueEvent<>(retObs, get(value.getOldValue()), null, value.getCause()));
								}
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableValue<? extends T>>> void onCompleted(V2 value) {
								observer2.onCompleted(new ObservableValueEvent<>(retObs, get(value.getOldValue()), get(value.getValue()),
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
	 * @param event The event to wrap
	 * @param wrapper The wrapper observable to wrap the event for
	 * @return An event with the same values as the given event, but created by the given observable
	 */
	public static <T> ObservableValueEvent<T> wrap(ObservableValueEvent<? extends T> event, ObservableValue<T> wrapper) {
		return wrapper.createEvent(event.getOldValue(), event.getValue(), event.getCause());
	}
}
