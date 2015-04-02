package org.observe.collect;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.*;

import prisms.lang.Type;

/**
 * A list whose content can be observed. This list is immutable in that none of its methods, including {@link List} methods, can modify its
 * content (List modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s returned by this
 * observable will be instances of {@link OrderedObservableElement}.
 *
 * @param <E> The type of element in the list
 */
public interface ObservableList<E> extends ObservableOrderedCollection<E>, List<E> {
	/**
	 * @param map The mapping function
	 * @return An observable list of a new type backed by this list and the mapping function
	 */
	@Override
	default <T> ObservableList<T> map(Function<? super E, T> map) {
		return map(ComposedObservableValue.getReturnType(map), map);
	}

	/**
	 * @param type The type for the mapped list
	 * @param map The mapping function
	 * @return An observable list of a new type backed by this list and the mapping function
	 */
	@Override
	default <T> ObservableList<T> map(Type type, Function<? super E, T> map) {
		ObservableList<E> outerList = this;
		class MappedObservableList extends AbstractList<T> implements ObservableList<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outerList.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public int size() {
				return outerList.size();
			}

			@Override
			public T get(int index) {
				return map.apply(outerList.get(index));
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return outerList.onElement(element -> observer.accept(element.mapV(map)));
			}
		}
		return new MappedObservableList();
	}

	/**
	 * @param filter The filter function
	 * @return A list containing all elements of this list that pass the given test
	 */
	@Override
	default ObservableList<E> filter(Function<? super E, Boolean> filter) {
		return filter(filter, null);
	}

	@Override
	default ObservableList<E> filter(Function<? super E, Boolean> filter, Observable<?> refresh) {
		return filterMap(getType(), (E value) -> {
			return (value != null && filter.apply(value)) ? value : null;
		}, refresh);
	}

	@Override
	default <T> ObservableList<T> filterMap(Function<? super E, T> map) {
		return filterMap(ComposedObservableValue.getReturnType(map), map);
	}

	@Override
	default <T> ObservableList<T> filterMap(Function<? super E, T> map, Observable<?> refresh) {
		return filterMap(ComposedObservableValue.getReturnType(map), map, refresh);
	}

	@Override
	default <T> ObservableList<T> filterMap(Type type, Function<? super E, T> map) {
		return filterMap(type, map, null);
	}

	@Override
	default <T> ObservableList<T> filterMap(Type type, Function<? super E, T> map, Observable<?> refresh) {
		ObservableList<E> outer = this;
		class FilteredList extends AbstractList<T> implements ObservableList<T> {
			private AtomicReference<CollectionSession> theInternalSessionValue = new AtomicReference<>();
			private final DefaultObservableValue<CollectionSession> theInternalSession = new DefaultObservableValue<CollectionSession>() {
				private final Type TYPE = new Type(CollectionSession.class);

				@Override
				public Type getType() {
					return TYPE;
				}

				@Override
				public CollectionSession get() {
					return theInternalSessionValue.get();
				}
			};
			private final ObservableValue<CollectionSession> theExposedSession = new org.observe.ComposedObservableValue<>(
				sessions -> (CollectionSession) (sessions[0] != null ? sessions[0] : sessions[1]), true, theInternalSession,
				outer.getSession());
			private final Observer<ObservableValueEvent<CollectionSession>> theSessionController = theInternalSession.control(null);

			private List<FilteredListElement<T, E>> theFilteredElements = new java.util.ArrayList<>();

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theExposedSession;
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public int size() {
				int ret = 0;
				for(E el : outer)
					if(map.apply(el) != null)
						ret++;
				return ret;
			}

			@Override
			public T get(int index) {
				if(index < 0)
					throw new IndexOutOfBoundsException("" + index);
				int size = 0;
				int idx = index;
				for(E el : outer) {
					T mapped = map.apply(el);
					if(mapped != null) {
						size++;
						if(idx == 0)
							return mapped;
						else
							idx--;
					}
				}
				throw new IndexOutOfBoundsException(index + " of " + size);
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				// Here we're relying on observers being fired in the order they were subscribed
				Runnable refreshStartSub = refresh == null ? null : refresh.internalSubscribe(new Observer<Object>() {
					@Override
					public <V> void onNext(V value) {
						startTransaction(value);
					}

					@Override
					public <V> void onCompleted(V value) {
						startTransaction(value);
					}
				});
				Observer<Object> refreshEnd = new Observer<Object>() {
					@Override
					public <V> void onNext(V value) {
						endTransaction(value);
					}

					@Override
					public <V> void onCompleted(V value) {
						endTransaction(value);
					}
				};
				Runnable [] refreshEndSub = new Runnable[] {refresh == null ? null : refresh.internalSubscribe(refreshEnd)};
				Runnable collSub = outer
					.onElement(element -> {
						OrderedObservableElement<E> outerElement = (OrderedObservableElement<E>) element;
						FilteredListElement<T, E> retElement = new FilteredListElement<>(outerElement, map, type, theFilteredElements,
							refresh);
						theFilteredElements.add(outerElement.getIndex(), retElement);
						// The refresh end always needs to be after the elements
					Runnable oldRefreshEnd = refreshEndSub[0];
					refreshEndSub[0] = refresh == null ? null : refresh.internalSubscribe(refreshEnd);
					if(oldRefreshEnd != null)
						oldRefreshEnd.run();
					outerElement.internalSubscribe(new Observer<ObservableValueEvent<E>>() {
							@Override
							public <V2 extends ObservableValueEvent<E>> void onNext(V2 elValue) {
								if(!retElement.isIncluded()) {
									T mapped = map.apply(elValue.getValue());
									if(mapped != null)
										observer.accept(retElement);
								}
							}

							@Override
							public <V2 extends ObservableValueEvent<E>> void onCompleted(V2 elValue) {
								theFilteredElements.remove(outerElement.getIndex());
							}
						});
					});
				return () -> {
					if(refreshStartSub != null)
						refreshStartSub.run();
					Runnable oldRefreshEnd = refreshEndSub[0];
					if(oldRefreshEnd != null)
						oldRefreshEnd.run();
					collSub.run();
				};
			}

			private void startTransaction(Object cause) {
				CollectionSession newSession = new DefaultCollectionSession(cause);
				CollectionSession oldSession = theInternalSessionValue.getAndSet(newSession);
				theSessionController.onNext(new org.observe.ObservableValueEvent<>(theInternalSession, oldSession, newSession, cause));
			}

			private void endTransaction(Object cause) {
				CollectionSession session = theInternalSessionValue.getAndSet(null);
				theSessionController.onNext(new org.observe.ObservableValueEvent<>(theInternalSession, session, null, cause));
			}
		}
		return new FilteredList();
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable list
	 * @param arg The value to combine with each of this list's elements
	 * @param func The combination function to apply to this list's elements and the given value
	 * @return An observable list containing this list's elements combined with the given argument
	 */
	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, ComposedObservableValue.getReturnType(func), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable list
	 * @param arg The value to combine with each of this list's elements
	 * @param type The type for the new list
	 * @param func The combination function to apply to this list's elements and the given value
	 * @return An observable list containing this list's elements combined with the given argument
	 */
	@Override
	default <T, V> ObservableList<V> combine(ObservableValue<T> arg, Type type, BiFunction<? super E, ? super T, V> func) {
		ObservableList<E> outerList = this;
		class CombinedObservableList extends AbstractList<V> implements ObservableList<V> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outerList.getSession();
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public int size() {
				return outerList.size();
			}

			@Override
			public V get(int index) {
				return func.apply(outerList.get(index), arg.get());
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<V>> observer) {
				return outerList.onElement(element -> observer.accept(element.combineV(func, arg)));
			}
		}
		return new CombinedObservableList();
	}

	/**
	 * @param observable The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableList<E> refireWhen(Observable<?> observable) {
		ObservableList<E> outer = this;
		return new org.observe.util.ObservableListWrapper<E>(this) {
			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return outer.onElement(element -> observer.accept(element.refireWhen(observable)));
			}
		};
	}

	@Override
	default ObservableList<E> immutable() {
		return new Immutable<>(this);
	}

	/**
	 * @param <T> The type of the value to wrap
	 * @param type The type of the elements in the list
	 * @param list The list of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(Type type, List<T> list) {
		class ConstantObservableElement implements OrderedObservableElement<T> {
			private final Type theType;
			private final T theValue;
			private final int theIndex;

			public ConstantObservableElement(T value, int index) {
				theType = type;
				theValue = value;
				theIndex = index;
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer) {
				observer.onNext(new ObservableValueEvent<>(this, theValue, theValue, null));
				return () -> {
				};
			}

			@Override
			public ObservableValue<T> persistent() {
				return this;
			}

			@Override
			public int getIndex() {
				return theIndex;
			}

			@Override
			public Type getType() {
				return theType;
			}

			@Override
			public T get() {
				return theValue;
			}

			@Override
			public String toString() {
				return "" + theValue;
			}
		}
		List<T> constList = java.util.Collections.unmodifiableList(list);
		List<ObservableElement<T>> obsEls = new java.util.ArrayList<>();
		for(int i = 0; i < constList.size(); i++)
			obsEls.add(new ConstantObservableElement(constList.get(i), i));
		class ConstantObservableList extends AbstractList<T> implements ObservableList<T> {
			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(new Type(CollectionSession.class), null);
			}

			@Override
			public Type getType() {
				return type;
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				for(ObservableElement<T> ob : obsEls)
					observer.accept(ob);
				return () -> {
				};
			}

			@Override
			public T get(int index) {
				return constList.get(index);
			}

			@Override
			public int size() {
				return constList.size();
			}
		}
		return new ConstantObservableList();
	}

	/**
	 * @param <T> The type of the elements
	 * @param type The type of the elements in the list
	 * @param list The array of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(Type type, T... list) {
		return constant(type, java.util.Arrays.asList(list));
	}

	/**
	 * @param <T> The super-type of all lists in the wrapping list
	 * @param list The list to flatten
	 * @return A list containing all elements of all lists in the outer list
	 */
	public static <T> ObservableList<T> flatten(ObservableList<? extends ObservableList<T>> list) {
		class FlattenedObservableList extends AbstractList<T> implements ObservableList<T> {
			private final CombinedCollectionSessionObservable theSession = new CombinedCollectionSessionObservable(list);

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theSession;
			}

			@Override
			public Type getType() {
				return list.getType().getParamTypes().length == 0 ? new Type(Object.class) : list.getType().getParamTypes()[0];
			}

			@Override
			public T get(int index) {
				int idx = index;
				for(ObservableList<T> subList : list) {
					if(idx < subList.size())
						return subList.get(idx);
					else
						idx -= subList.size();
				}
				throw new IndexOutOfBoundsException(index + " out of " + size());
			}

			@Override
			public int size() {
				int ret = 0;
				for(ObservableList<T> subList : list)
					ret += subList.size();
				return ret;
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return list.onElement(new Consumer<ObservableElement<? extends ObservableList<T>>>() {
					private Map<ObservableList<T>, Runnable> subListSubscriptions;

					{
						subListSubscriptions = new org.observe.util.ConcurrentIdentityHashMap<>();
					}

					@Override
					public void accept(ObservableElement<? extends ObservableList<T>> subList) {
						class FlattenedListElement extends FlattenedElement<T> implements OrderedObservableElement<T> {
							private final List<FlattenedListElement> subListElements;

							FlattenedListElement(OrderedObservableElement<T> subEl, List<FlattenedListElement> subListEls) {
								super(subEl, subList);
								subListElements = subListEls;
							}

							@Override
							protected OrderedObservableElement<T> getSubElement() {
								return (OrderedObservableElement<T>) super.getSubElement();
							}

							@Override
							public int getIndex() {
								int subListIndex = ((OrderedObservableElement<?>) subList).getIndex();
								int ret = 0;
								for(int i = 0; i < subListIndex; i++)
									ret += list.get(i).size();
								int innerIndex = getSubElement().getIndex();
								for(int i = 0; i < innerIndex; i++)
									if(!subListElements.get(i).isRemoved())
										ret++;
								return ret;
							}
						}
						subList.subscribe(new Observer<ObservableValueEvent<? extends ObservableList<T>>>() {
							private List<FlattenedListElement> subListEls = new java.util.ArrayList<>();

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableList<T>>> void onNext(V2 subListEvent) {
								if(subListEvent.getOldValue() != null && subListEvent.getOldValue() != subListEvent.getValue()) {
									Runnable subListSub = subListSubscriptions.get(subListEvent.getOldValue());
									if(subListSub != null)
										subListSub.run();
								}
								Runnable subListSub = subListEvent.getValue().onElement(subElement -> {
									OrderedObservableElement<T> subListEl = (OrderedObservableElement<T>) subElement;
									FlattenedListElement flatEl = new FlattenedListElement(subListEl, subListEls);
									subListEls.add(subListEl.getIndex(), flatEl);
									subListEl.completed().act(x -> subListEls.remove(subListEl.getIndex()));
									observer.accept(flatEl);
								});
								subListSubscriptions.put(subListEvent.getValue(), subListSub);
							}

							@Override
							public <V2 extends ObservableValueEvent<? extends ObservableList<T>>> void onCompleted(V2 subListEvent) {
								subListSubscriptions.remove(subListEvent.getValue()).run();
							}
						});
					}
				});
			}
		}
		return new FlattenedObservableList();
	}

	/**
	 * The type of element in filtered lists
	 *
	 * @param <T> The type of this element
	 * @param <E> The type of element wrapped by this element
	 */
	class FilteredListElement<T, E> extends FilteredElement<T, E> implements OrderedObservableElement<T> {
		private List<FilteredListElement<T, E>> theFilteredElements;

		FilteredListElement(OrderedObservableElement<E> wrapped, Function<? super E, T> map, Type type,
			List<FilteredListElement<T, E>> filteredEls, Observable<?> refresh) {
			super(wrapped, map, type, refresh);
			theFilteredElements = filteredEls;
		}

		@Override
		protected OrderedObservableElement<E> getWrapped() {
			return (OrderedObservableElement<E>) super.getWrapped();
		}

		@Override
		public int getIndex() {
			int ret = 0;
			int outerIdx = getWrapped().getIndex();
			for(int i = 0; i < outerIdx; i++)
				if(theFilteredElements.get(i).isIncluded())
					ret++;
			return ret;
		}
	}

	/**
	 * An observable list that cannot be modified directly, but reflects the value of a wrapped list as it changes
	 *
	 * @param <E> The type of elements in the list
	 */
	public static class Immutable<E> extends AbstractList<E> implements ObservableList<E> {
		private final ObservableList<E> theWrapped;

		/** @param wrap The collection to wrap */
		public Immutable(ObservableList<E> wrap) {
			theWrapped = wrap;
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theWrapped.getSession();
		}

		@Override
		public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
			return theWrapped.onElement(observer);
		}

		@Override
		public Type getType() {
			return theWrapped.getType();
		}

		@Override
		public E get(int index) {
			return theWrapped.get(index);
		}

		@Override
		public Iterator<E> iterator() {
			return prisms.util.ArrayUtils.immutableIterator(theWrapped.iterator());
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Immutable<E> immutable() {
			return this;
		}
	}
}
