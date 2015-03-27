package org.observe;

import java.util.List;
import java.util.function.Function;

/**
 * An observable that depends on the values of other observables
 *
 * @param <T> The type of the composed observable
 */
public class ComposedObservable<T> implements Observable<T> {
	private static final Object NONE = new Object();

	private final List<Observable<?>> theComposed;
	private final Function<Object [], T> theFunction;

	/**
	 * @param function The function that operates on the argument observables to produce this observable's value
	 * @param composed The argument observables whose values are passed to the function
	 */
	public ComposedObservable(Function<Object [], T> function, Observable<?>... composed) {
		theFunction = function;
		theComposed = new java.util.ArrayList<>(java.util.Arrays.asList(composed));
	}

	@Override
	public Runnable internalSubscribe(Observer<? super T> observer) {
		Runnable [] composedSubs = new Runnable[theComposed.size()];
		Object [] values = new Object[theComposed.size()];
		for(int i = 0; i < theComposed.size(); i++) {
			int index = i;
			values[i] = NONE;
			composedSubs[i] = theComposed.get(i).internalSubscribe(new Observer<Object>() {
				@Override
				public <V> void onNext(V value) {
					values[index] = value;
					Object next = getNext();
					if(next != NONE)
						observer.onNext((T) next);
				}

				@Override
				public <V> void onCompleted(V value) {
					values[index] = value;
					Object next = getNext();
					if(next != NONE)
						observer.onCompleted((T) next);
				}

				@Override
				public void onError(Throwable error) {
					observer.onError(error);
				}

				private Object getNext() {
					Object [] args = values.clone();
					for(Object value : args)
						if(value == NONE)
							return NONE;
					return theFunction.apply(args);
				}
			});
		}
		return () -> {
			for(Runnable sub : composedSubs)
				sub.run();
		};
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
