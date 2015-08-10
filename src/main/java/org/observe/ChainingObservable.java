package org.observe;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An observable for which calls may be chained. The {@link #subscribe(Observer)} and {@link #act(Action)} methods of ChainingObservable
 * return a chained observable, which may be filtered, mapped, etc. or observed itself. If any of these ChainingObservables' Subscription
 * {@link #unsubscribe()} methods is called, all observers on any link in the chain will be unsubscribed.
 *
 * @param <T> The type of values that the observable emits
 */
public interface ChainingObservable<T> extends Observable<T>, Subscription {
	/** @return The regular (non-chaining) observable behind this chaining observable */
	Observable<T> unchain();

	@Override
	ChainingObservable<T> subscribe(Observer<? super T> observer);

	@Override
	ChainingObservable<T> act(Action<? super T> action);

	@Override
	ChainingObservable<Throwable> error();

	@Override
	ChainingObservable<T> completed();

	@Override
	ChainingObservable<T> noInit();

	@Override
	ChainingObservable<T> filter(Function<? super T, Boolean> func);

	@Override
	<R> ChainingObservable<R> map(Function<? super T, R> func);

	@Override
	<R> ChainingObservable<R> filterMap(Function<? super T, R> func);

	@Override
	<V, R> ChainingObservable<R> combine(Observable<V> other, BiFunction<? super T, ? super V, R> func);

	@Override
	ChainingObservable<T> takeUntil(Observable<?> until);

	@Override
	ChainingObservable<T> take(int times);

	@Override
	ChainingObservable<T> skip(int times);

	@Override
	ChainingObservable<T> skip(java.util.function.Supplier<Integer> times);
}
