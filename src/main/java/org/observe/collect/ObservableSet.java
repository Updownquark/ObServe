package org.observe.collect;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.*;
import org.observe.Observable;
import org.observe.Observer;

import prisms.lang.Type;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, Set<E> {
	/**
	 * @param filter The filter function
	 * @return A set containing all elements passing the given test
	 */
	@Override
	default ObservableSet<E> filter(Function<? super E, Boolean> filter) {
		return filter(filter, null);
	}

	@Override
	default ObservableSet<E> filter(Function<? super E, Boolean> filter, Observable<?> refresh) {
		ObservableSet<E> outer = this;
		class FilteredSet extends AbstractSet<E> implements ObservableSet<E> {
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

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return theExposedSession;
			}

			@Override
			public Type getType() {
				return outer.getType();
			}

			@Override
			public int size() {
				int ret = 0;
				for(E el : outer)
					if(filter.apply(el) != null)
						ret++;
				return ret;
			}

			@Override
			public Iterator<E> iterator() {
				return new Iterator<E>() {
					private final Iterator<E> backing = outer.iterator();

					private E nextVal;

					@Override
					public boolean hasNext() {
						while(nextVal == null && backing.hasNext()) {
							nextVal = backing.next();
							if(!filter.apply(nextVal))
								nextVal = null;
						}
						return nextVal != null;
					}

					@Override
					public E next() {
						if(nextVal == null && !hasNext())
							throw new java.util.NoSuchElementException();
						E ret = nextVal;
						nextVal = null;
						return ret;
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				Function<E, E> map = value -> (filter.apply(value) ? value : null);
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
				Runnable collSub = outer.onElement(element -> {
					FilteredElement<E, E> retElement = new FilteredElement<>(element, map, getType(), refresh);
					// The refresh end always needs to be after the elements
					Runnable oldRefreshEnd = refreshEndSub[0];
					refreshEndSub[0] = refresh == null ? null : refresh.internalSubscribe(refreshEnd);
					if(oldRefreshEnd != null)
						oldRefreshEnd.run();
					element.act(elValue -> {
						if(!retElement.isIncluded()) {
							E mapped = map.apply(elValue.getValue());
							if(mapped != null)
								observer.accept(retElement);
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

			@Override
			public String toString() {
				return "filter(" + outer + ")";
			}
		}
		return new FilteredSet();
	}

	/**
	 * @param observable The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refireWhen(Observable<?> observable) {
		ObservableSet<E> outer = this;
		return new org.observe.util.ObservableSetWrapper<E>(this) {
			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return outer.onElement(element -> observer.accept(element.refireWhen(observable)));
			}
		};
	}

	/**
	 * @param refire A function that supplies a refire observable as a function of element value
	 * @return A collection whose values individually refire when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refireWhenEach(Function<? super E, Observable<?>> refire) {
		ObservableCollection<E> outer = this;
		return new org.observe.util.ObservableSetWrapper<E>(this) {
			@Override
			public Runnable onElement(Consumer<? super ObservableElement<E>> observer) {
				return outer.onElement(element -> observer.accept(element.refireWhenForValue(refire)));
			}
		};
	}

	@Override
	default ObservableSet<E> immutable() {
		return new Immutable<>(this);
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(Type type, java.util.Collection<T> coll) {
		Set<T> modSet = new java.util.LinkedHashSet<>(coll);
		Set<T> constSet = java.util.Collections.unmodifiableSet(modSet);
		java.util.List<ObservableElement<T>> els = new java.util.ArrayList<>();
		for(T value : constSet)
			els.add(new ObservableElement<T>() {
				@Override
				public Type getType() {
					return type;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer) {
					observer.onNext(new ObservableValueEvent<>(this, null, value, null));
					return () -> {
					};
				}

				@Override
				public ObservableValue<T> persistent() {
					return this;
				}
			});
		class ConstantObservableSet extends AbstractSet<T> implements ObservableSet<T> {
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
				for(ObservableElement<T> el : els)
					observer.accept(el);
				return () -> {
				};
			}

			@Override
			public int size() {
				return constSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return constSet.iterator();
			}
		}
		return new ConstantObservableSet();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(Type type, T... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> ObservableSet<T> unique(ObservableCollection<T> coll) {
		class UniqueFilteredElement implements ObservableElement<T> {
			private ObservableElement<T> theWrappedElement;
			private Set<UniqueFilteredElement> theElements;
			private boolean isIncluded;

			UniqueFilteredElement(ObservableElement<T> wrapped, Set<UniqueFilteredElement> elements) {
				theWrappedElement = wrapped;
				theElements = elements;
			}

			@Override
			public ObservableValue<T> persistent() {
				return theWrappedElement.persistent();
			}

			@Override
			public Type getType() {
				return coll.getType();
			}

			@Override
			public T get() {
				return theWrappedElement.get();
			}

			boolean isIncluded() {
				return isIncluded;
			}

			boolean shouldBeIncluded(T value) {
				for(UniqueFilteredElement el : theElements)
					if(el != this && el.isIncluded && Objects.equals(el.get(), value))
						return false;
				return true;
			}

			@Override
			public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer2) {
				Runnable [] innerSub = new Runnable[1];
				innerSub[0] = theWrappedElement.internalSubscribe(new Observer<ObservableValueEvent<T>>() {
					@Override
					public <V2 extends ObservableValueEvent<T>> void onNext(V2 elValue) {
						boolean shouldBe = shouldBeIncluded(elValue.getValue());
						if(isIncluded && !shouldBe) {
							isIncluded = false;
							observer2.onCompleted(new ObservableValueEvent<>(UniqueFilteredElement.this, elValue.getOldValue(), elValue
								.getValue(), elValue));
							if(innerSub[0] != null) {
								innerSub[0].run();
								innerSub[0] = null;
							}
						} else if(!isIncluded && shouldBe) {
							isIncluded = true;
							observer2.onNext(new ObservableValueEvent<>(UniqueFilteredElement.this, elValue.getOldValue(), elValue
								.getValue(), elValue));
						}
					}

					@Override
					public <V2 extends ObservableValueEvent<T>> void onCompleted(V2 elValue) {
						T oldVal, newVal;
						if(elValue != null) {
							oldVal = elValue.getOldValue();
							newVal = elValue.getValue();
						} else {
							oldVal = get();
							newVal = oldVal;
						}
						observer2.onCompleted(new ObservableValueEvent<>(UniqueFilteredElement.this, oldVal, newVal, elValue));
					}
				});
				if(!isIncluded) {
					return () -> {
					};
				}
				return innerSub[0];
			}

			@Override
			public String toString() {
				return "filter(" + theWrappedElement + ")";
			}
		}
		class UniqueSet extends AbstractSet<T> implements ObservableSet<T> {
			private java.util.concurrent.ConcurrentHashMap<UniqueFilteredElement, Object> theElements = new java.util.concurrent.ConcurrentHashMap<>();

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return coll.getSession();
			}

			@Override
			public Type getType() {
				return coll.getType().getParamTypes().length == 0 ? new Type(Object.class) : coll.getType().getParamTypes()[0];
			}

			@Override
			public int size() {
				HashSet<T> set = new HashSet<>();
				for(T val : coll)
					set.add(val);
				return set.size();
			}

			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<T> backing = coll.iterator();
					private final HashSet<T> set = new HashSet<>();
					private T nextVal;

					@Override
					public boolean hasNext() {
						while(nextVal == null && backing.hasNext()) {
							nextVal = backing.next();
							if(!set.add(nextVal))
								nextVal = null;
						}
						return nextVal != null;
					}

					@Override
					public T next() {
						if(nextVal == null && !hasNext())
							throw new java.util.NoSuchElementException();
						T ret = nextVal;
						nextVal = null;
						return ret;
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<T>> observer) {
				return coll.onElement(element -> {
					UniqueFilteredElement retElement = new UniqueFilteredElement(element, theElements.keySet());
					theElements.put(retElement, 0);
					element.subscribe(new Observer<ObservableValueEvent<T>>() {
						@Override
						public <V2 extends ObservableValueEvent<T>> void onNext(V2 elValue) {
							if(!retElement.isIncluded() && retElement.shouldBeIncluded(elValue.getValue())) {
								observer.accept(retElement);
							}
						}

						@Override
						public <V2 extends ObservableValueEvent<T>> void onCompleted(V2 elValue) {
							theElements.remove(retElement);
							if(retElement.isIncluded()) {
								for(UniqueFilteredElement el2 : theElements.keySet()) {
									if(el2 == retElement)
										continue;
									if(!el2.isIncluded() && Objects.equals(el2.get(), elValue.getOldValue())) {
										observer.accept(el2);
										break;
									}
								}
							}
						}
					});
				});
			}
		}
		return new UniqueSet();
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	public static class Immutable<E> extends AbstractSet<E> implements ObservableSet<E> {
		private final ObservableSet<E> theWrapped;

		/** @param wrap The set to wrap */
		public Immutable(ObservableSet<E> wrap) {
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
