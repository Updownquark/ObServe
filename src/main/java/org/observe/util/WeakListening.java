package org.observe.util;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Observer.SimpleObserver;
import org.observe.SimpleObservable;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Subscription;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * <p>
 * A collection of listeners that are weakly reachable from the event sources they are subscribed to. As long the WeakListening object is
 * strongly reachable, all the listeners are safe from garbage collection. If the WeakListening object is garbage-collected, all listeners
 * become available for garbage collection and the subscriptions to the event sources will be unsubscribed when they are next utilized.
 * </p>
 *
 * <p>
 * This mechanism is used by {@link org.observe.collect.ObservableCollection.CollectionDataFlow#supportsPassive() actively-managed} derived
 * collections so that as long as the collection is being used, the collection functions. But when the collection is no longer used, the
 * subscriptions driving the derived collection's values may be released.
 * </p>
 */
public class WeakListening {
	static class WeakActionKey {
	}

	private interface WeakActionMaker<X> {
		X make(WeakListening listening, WeakActionKey actionKey);
	}

	private final Map<WeakActionKey, ActionStruct> theActions;
	private final WeakReference<WeakListening> theWeakMe;

	private WeakListening() {
		theActions = new LinkedHashMap<>();
		theWeakMe = new WeakReference<>(this);
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
	 * Adds an observer subscription to this listening
	 *
	 * @param action The observer action to invoke when the event source fires
	 * @param subscribe A function to subscribe to the event source
	 * @return A subscription that will terminate the subscription to the event source
	 */
	public <T> Subscription withObserver(SimpleObserver<T> action, Function<? super SimpleObserver<T>, ? extends Subscription> subscribe) {
		return with(action, WeakObserver::new, subscribe);
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
		WeakActionKey actionKey = new WeakActionKey();
		ActionStruct as = new ActionStruct(null);
		theActions.put(actionKey, as);

		SimpleObservable<Void> childUnsub = new SimpleObservable<>();
		// It looks like the key is never removed from the map by unsubscription here,
		// but when I add that in the subscription lambda, I get ConcurrentModificationExceptions from the unsubscribe() method
		as.subscription = () -> childUnsub.onNext(null);

		@SuppressWarnings("resource")
		Builder child = new Builder().withUntil(//
			action -> childUnsub.act0(action::run));
		return child;
	}

	// This a utility method, not public, as weakMaker must produce an X that is also an extension of WeakAction
	private <X> Subscription with(X action, WeakActionMaker<X> weakMaker,
		Function<? super X, ? extends Subscription> subscribe) {
		WeakActionKey actionKey = new WeakActionKey();
		X weak = weakMaker.make(this, actionKey);
		ActionStruct as = new ActionStruct(action);
		theActions.put(actionKey, as);
		as.subscription = subscribe.apply(weak);
		((WeakAction) weak).withSubscription(as.subscription);
		return () -> {
			as.unsubscribe();
		};
	}

	WeakReference<WeakListening> getWeakRef() {
		return theWeakMe;
	}

	Object getAction(WeakActionKey actionKey) {
		ActionStruct action = theActions.get(actionKey);
		return action == null ? null : action.action;
	}

	void unsubscribe() {
		Iterator<ActionStruct> subIter = theActions.values().iterator();
		int size = theActions.size();
		while (subIter.hasNext()) {
			subIter.next().unsubscribe();
			if (theActions.size() == size)
				subIter.remove();
			size--;
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
				theListening::unsubscribe, until);
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
		private final WeakActionKey theActionKey;
		private Subscription theSubscription;

		WeakAction(WeakListening listening, WeakActionKey actionKey) {
			theListening = listening.getWeakRef();
			theActionKey = actionKey;
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
				return (A) listening.getAction(theActionKey);
		}
	}

	private static class WeakRunnable extends WeakAction implements Runnable {
		WeakRunnable(WeakListening listening, WeakActionKey actionKey) {
			super(listening, actionKey);
		}

		@Override
		public void run() {
			Runnable action = getAction();
			if (action != null)
				action.run();
		}
	}

	private static class WeakConsumer<E> extends WeakAction implements Consumer<E> {
		WeakConsumer(WeakListening listening, WeakActionKey actionKey) {
			super(listening, actionKey);
		}

		@Override
		public void accept(E value) {
			Consumer<E> action = getAction();
			if (action != null)
				action.accept(value);
		}

		@Override
		public String toString() {
			return getAction().toString();
		}
	}

	private static class WeakObserver<E> extends WeakAction implements SimpleObserver<E> {
		WeakObserver(WeakListening listening, WeakActionKey actionKey) {
			super(listening, actionKey);
		}

		@Override
		public void onNext(E value) {
			SimpleObserver<E> action = getAction();
			if (action != null)
				action.onNext(value);
		}

		@Override
		public String toString() {
			return getAction().toString();
		}
	}

	private static class WeakBiConsumer<E, F> extends WeakAction implements BiConsumer<E, F> {
		WeakBiConsumer(WeakListening listening, WeakActionKey actionKey) {
			super(listening, actionKey);
		}

		@Override
		public void accept(E value1, F value2) {
			BiConsumer<E, F> action = getAction();
			if (action != null)
				action.accept(value1, value2);
		}
	}

	/**
	 * @param strongListener The listener to subscribe to
	 * @param subscribe Subscribes to an observable
	 * @return A subscription that, when called will cease listening. If the subscription is garbage-collected, it will be unsubscribed as
	 *         well.
	 */
	public static <T> Subscription consumeWeakly(Consumer<? super T> strongListener,
		Function<? super Consumer<T>, Subscription> subscribe) {
		Subscription[] sub = new Subscription[1];
		sub[0] = subscribe.apply(new StandaloneWeakConsumer<>(strongListener, sub));
		return sub[0];
	}

	static <T> Subscription observeWeakly(Observer<? super T> strongListener, Observable<T> observable) {
		Subscription[] sub = new Subscription[1];
		sub[0] = observable.subscribe(new StandaloneWeakObserver<>(strongListener, sub));
		return sub[0];
	}

	/**
	 * @param observable The observable to observe weakly
	 * @return An observable whose subscriptions only subscribe to the observable weakly--the subscriptions to the observable will be
	 *         unsubscribed if the weak observable is garbage-collected
	 */
	public static <T> Observable<T> weaklyListeningObservable(Observable<T> observable) {
		class WeaklyListeningObservable implements Observable<T> {
			private final ListenerList<Observer<? super T>> theObservers = ListenerList.build().allowReentrant().forEachSafe(false)
				.withFastSize(false).build();

			@Override
			public boolean isEventing() {
				return theObservers.isFiring();
			}

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
			public Identifiable alias(String alias) {
				observable.alias(alias);
				return this;
			}

			@Override
			public Set<String> getAliases() {
				return observable.getAliases();
			}

			@Override
			public ThreadConstraint getThreadConstraint() {
				return observable.getThreadConstraint();
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

			@Override
			public CoreId getCoreId() {
				return observable.getCoreId();
			}

			@Override
			public long getStamp() {
				return observable.getStamp();
			}

			@Override
			public CoreChangeSources getChangeSources() {
				return observable.getChangeSources();
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
		public void onNext(T t) {
			Observer<? super T> listener = theListenerRef.get();
			if (listener != null)
				listener.onNext(t);
			else
				theSubscription[0].unsubscribe();
		}

		@Override
		public void onCompleted(Supplier<Causable> cause) {
			Observer<? super T> listener = theListenerRef.get();
			if (listener != null)
				listener.onCompleted(cause);
		}
	}
}
