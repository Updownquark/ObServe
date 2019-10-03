package org.observe.util;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * <p>
 * A collection of listeners that are weakly reachable from the event sources they are subscribed to. As long the WeakListening object is
 * strongly reachable, all the listeners are safe from garbage collection. If the WeakListening object is garbage-collected, all listeners
 * become available for garbage collection and the subscriptions to the event sources will be unsubscribed.
 * </p>
 *
 * <p>
 * This mechanism is used by {@link org.observe.collect.ObservableCollection.CollectionDataFlow#supportsPassive() actively-managed} derived
 * collections so that as long as the collection is being used, the collection functions. But when the collection is no longer used, the
 * subscriptions driving the derived collection's values may be released.
 * </p>
 */
public class WeakListening {
	private final AtomicLong theIdGen;
	private final ConcurrentHashMap<Long, ActionStruct> theActions;

	private WeakListening() {
		theIdGen = new AtomicLong();
		theActions = new ConcurrentHashMap<>();
	}

	/**
	 * Adds a runnable (zero-argument) subscription to this listening
	 *
	 * @param action The runnable action to invoke when the event source fires
	 * @param subscribe A function to subscribe to the event source
	 * @return A subscription that will terminate the subscription to the event source
	 */
	public Subscription withAction(Runnable action, Function<? super Runnable, ? extends Subscription> subscribe) {
		return with(action, WeakRunnable::new, subscribe);
	}

	/**
	 * Adds a consumer (one-argument) subscription to this listening
	 *
	 * @param action The consumer action to invoke when the event source fires
	 * @param subscribe A function to subscribe to the event source
	 * @return A subscription that will terminate the subscription to the event source
	 */
	public <T> Subscription withConsumer(Consumer<T> action, Function<? super Consumer<T>, ? extends Subscription> subscribe) {
		return with(action, WeakConsumer::new, subscribe);
	}

	/**
	 * Adds a bi-consumer (two-argument) subscription to this listening
	 *
	 * @param action The consumer action to invoke when the event source fires
	 * @param subscribe A function to subscribe to the event source
	 * @return A subscription that will terminate the subscription to the event source
	 */
	public <T, U> Subscription withBiConsumer(BiConsumer<T, U> action,
		Function<? super BiConsumer<T, U>, ? extends Subscription> subscribe) {
		return with(action, WeakBiConsumer::new, subscribe);
	}

	/**
	 * @return The builder for a WeakListening object dependent on this one. If the parent object is cleared, the child is cleared. But if
	 *         the child is cleared by an {@link Builder#withUntil(Function) until} or by {@link Builder#unsubscribe() unsubscribe()}, the
	 *         parent is not affected.
	 */
	public Builder child() {
		Long actionId = theIdGen.getAndIncrement();
		ActionStruct as = new ActionStruct(null);
		theActions.put(actionId, as);

		SimpleObservable<Void> childUnsub = new SimpleObservable<>();
		as.subscription = () -> childUnsub.onNext(null);

		Builder child = new Builder().withUntil(//
			action -> childUnsub.act(v -> action.run()));
		return child;
	}

	// This a utility method, not public, as weakMaker must produce an X that is also an extension of WeakAction
	private <X> Subscription with(X action, BiFunction<WeakListening, Long, X> weakMaker,
		Function<? super X, ? extends Subscription> subscribe) {
		Long actionId = theIdGen.getAndIncrement();
		X weak = weakMaker.apply(this, actionId);
		ActionStruct as = new ActionStruct(action);
		theActions.put(actionId, as);
		as.subscription = subscribe.apply(weak);
		((WeakAction) weak).withSubscription(as.subscription);
		return () -> {
			as.unsubscribe();
			theActions.remove(actionId);
		};
	}

	Object getAction(Long actionId) {
		ActionStruct action = theActions.get(actionId);
		return action == null ? null : action.action;
	}

	void unsubscribe() {
		Iterator<ActionStruct> subIter = theActions.values().iterator();
		while (subIter.hasNext()) {
			subIter.next().unsubscribe();
			subIter.remove();
		}
	}

	/** @return A builder containing a WeakListening object that can also perform additional operations on it */
	public static Builder build() {
		return new Builder();
	}

	/** Contains and manages a WeakListening object */
	public static class Builder implements Subscription {
		private final WeakListening theListening;

		private Builder() {
			theListening = new WeakListening();
		}

		/**
		 * Adds an until to the WeakListening, meaning that when the given event source fires, the WeakListening object will be cleared.
		 *
		 * @param until The event source to clear the WeakListening
		 * @return This builder
		 */
		public Builder withUntil(Function<? super Runnable, ? extends Subscription> until) {
			theListening.withAction(//
				() -> theListening.unsubscribe(), //
				until);
			return this;
		}

		/** @return The WeakListening managed by this builder */
		public WeakListening getListening() {
			return theListening;
		}

		@Override
		public void unsubscribe() {
			theListening.unsubscribe();
		}
	}

	private static class ActionStruct {
		final Object action;
		Subscription subscription;

		ActionStruct(Object action) {
			this.action = action;
		}

		void unsubscribe() {
			Subscription sub = subscription;
			subscription = null;
			if (sub != null)
				sub.unsubscribe();
		}
	}

	private static abstract class WeakAction {
		private final Reference<WeakListening> theListening;
		private final Long theActionId;
		private Subscription theSubscription;

		WeakAction(WeakListening listening, Long actionId) {
			theListening = new WeakReference<>(listening);
			theActionId = actionId;
		}

		void withSubscription(Subscription sub) {
			theSubscription = sub;
		}

		<A> A getAction() {
			WeakListening listening = theListening.get();
			if (listening == null) {
				Subscription sub = theSubscription;
				theSubscription = null;
				if (sub != null)
					sub.unsubscribe();
				return null;
			} else
				return (A) listening.getAction(theActionId);
		}
	}

	private static class WeakRunnable extends WeakAction implements Runnable {
		WeakRunnable(WeakListening listening, Long actionId) {
			super(listening, actionId);
		}

		@Override
		public void run() {
			Runnable action = getAction();
			if (action != null)
				action.run();
		}
	}

	private static class WeakConsumer<E> extends WeakAction implements Consumer<E> {
		WeakConsumer(WeakListening listening, Long actionId) {
			super(listening, actionId);
		}

		@Override
		public void accept(E value) {
			Consumer<E> action = getAction();
			if (action != null)
				action.accept(value);
		}
	}

	private static class WeakBiConsumer<E, F> extends WeakAction implements BiConsumer<E, F> {
		WeakBiConsumer(WeakListening listening, Long actionId) {
			super(listening, actionId);
		}

		@Override
		public void accept(E value1, F value2) {
			BiConsumer<E, F> action = getAction();
			if (action != null)
				action.accept(value1, value2);
		}
	}

	public static <T> Subscription consumeWeakly(Consumer<? super T> strongListener,
		Function<? super Consumer<T>, Subscription> subscribe) {
		Subscription[] sub = new Subscription[1];
		sub[0] = subscribe.apply(new StandaloneWeakConsumer<>(strongListener, sub));
		return sub[0];
	}

	public static <T> Subscription observeWeakly(Observer<? super T> strongListener, Observable<T> observable) {
		Subscription[] sub = new Subscription[1];
		sub[0] = observable.subscribe(new StandaloneWeakObserver<>(strongListener, sub));
		return sub[0];
	}

	public static <T> Observable<T> weaklyListeningObservable(Observable<T> observable) {
		class WeaklyListeningObservable implements Observable<T> {
			private final ListenerList<Observer<? super T>> theObservers = ListenerList.build().allowReentrant().forEachSafe(false)
				.withFastSize(false).build();

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				Runnable remove = theObservers.add(observer, false);
				Subscription weakObs = observeWeakly(observer, observable);
				return () -> {
					remove.run();
					weakObs.unsubscribe();
				};
			}

			@Override
			public Object getIdentity() {
				return observable.getIdentity();
			}

			@Override
			public boolean isSafe() {
				return observable.isSafe();
			}

			@Override
			public boolean isLockSupported() {
				return observable.isLockSupported();
			}

			@Override
			public Transaction lock() {
				return observable.lock();
			}

			@Override
			public Transaction tryLock() {
				return observable.tryLock();
			}
		}
		return new WeaklyListeningObservable();
	}

	private static class StandaloneWeakConsumer<T> implements Consumer<T> {
		private final WeakReference<Consumer<? super T>> theListenerRef;
		private final Subscription[] theSubscription;

		StandaloneWeakConsumer(Consumer<? super T> strongListener, Subscription[] sub) {
			theListenerRef = new WeakReference<>(strongListener);
			theSubscription = sub;
		}

		@Override
		public void accept(T t) {
			Consumer<? super T> listener = theListenerRef.get();
			if (listener != null)
				listener.accept(t);
			else
				theSubscription[0].unsubscribe();
		}
	}

	private static class StandaloneWeakObserver<T> implements Observer<T> {
		private final WeakReference<Observer<? super T>> theListenerRef;
		private final Subscription[] theSubscription;

		StandaloneWeakObserver(Observer<? super T> strongListener, Subscription[] sub) {
			theListenerRef = new WeakReference<>(strongListener);
			theSubscription = sub;
		}

		@Override
		public <V extends T> void onNext(V t) {
			Observer<? super T> listener = theListenerRef.get();
			if (listener != null)
				listener.onNext(t);
			else
				theSubscription[0].unsubscribe();
		}

		@Override
		public <V extends T> void onCompleted(V value) {
			Observer<? super T> listener = theListenerRef.get();
			if (listener != null)
				listener.onCompleted(value);
		}
	}
}
