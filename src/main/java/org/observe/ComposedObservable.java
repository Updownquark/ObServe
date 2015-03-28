package org.observe;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.util.ListenerSet;

/**
 * An observable that depends on the values of other observables
 *
 * @param <T> The type of the composed observable
 */
public class ComposedObservable<T> implements Observable<T> {
	private static final Object NONE = new Object();

	private final List<Observable<?>> theComposed;
	private final Function<Object [], T> theFunction;
	private final ListenerSet<Observer<? super T>> theObservers;

	/**
	 * @param function The function that operates on the argument observables to produce this observable's value
	 * @param composed The argument observables whose values are passed to the function
	 */
	public ComposedObservable(Function<Object [], T> function, Observable<?>... composed) {
		theFunction = function;
		theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
		theObservers = new ListenerSet<>();
		theObservers.setUsedListener(new Consumer<Boolean>() {
			private final Runnable [] composedSubs = new Runnable[theComposed.size()];
			private final Object [] values = new Object[theComposed.size()];

			@Override
			public void accept(Boolean used) {
				if(used) {
					for(int i = 0; i < theComposed.size(); i++) {
						int index = i;
						composedSubs[i] = theComposed.get(i).internalSubscribe(new Observer<Object>() {
							@Override
							public <V> void onNext(V value) {
								values[index] = value;
								Object next = getNext();
								if(next != NONE)
									fireNext((T) next);
							}

							@Override
							public <V> void onCompleted(V value) {
								values[index] = value;
								Object next = getNext();
								if(next != NONE)
									fireCompleted((T) next);
							}

							@Override
							public void onError(Throwable error) {
								fireError(error);
							}

							private Object getNext() {
								Object [] args = values.clone();
								for(Object value : args)
									if(value == NONE)
										return NONE;
								return theFunction.apply(args);
							}

							private void fireNext(T next) {
								theObservers.forEach(listener -> listener.onNext(next));
							}

							private void fireCompleted(T next) {
								theObservers.forEach(listener -> listener.onCompleted(next));
							}

							private void fireError(Throwable error) {
								theObservers.forEach(listener -> listener.onError(error));
							}
						});
					}
				} else {
					for(int i = 0; i < theComposed.size(); i++) {
						composedSubs[i].run();
						composedSubs[i] = null;
						values[i] = null;
					}
				}
			}
		});
	}

	@Override
	public Runnable internalSubscribe(Observer<? super T> observer) {
		theObservers.add(observer);
		return () -> theObservers.remove(observer);
	}

	/** @return The observables that this observable uses as sources */
	public Observable<?> [] getComposed() {
		return theComposed.toArray(new Observable[theComposed.size()]);
	}

	@Override
	public String toString() {
		return theComposed.toString();
	}
}
