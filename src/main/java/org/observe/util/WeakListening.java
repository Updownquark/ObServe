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

import org.observe.Subscription;

public class WeakListening {
	private final AtomicLong theIdGen;
	private final ConcurrentHashMap<Long, ActionStruct> theActions;

	private WeakListening() {
		theIdGen = new AtomicLong();
		theActions = new ConcurrentHashMap<>();
	}

	public Subscription withAction(Runnable action, Function<? super Runnable, ? extends Subscription> subscribe) {
		return with(action, WeakRunnable::new, subscribe);
	}

	public <T> Subscription withConsumer(Consumer<T> action, Function<? super Consumer<T>, ? extends Subscription> subscribe) {
		return with(action, WeakConsumer::new, subscribe);
	}

	public <T, U> Subscription withBiConsumer(BiConsumer<T, U> action,
		Function<? super BiConsumer<T, U>, ? extends Subscription> subscribe) {
		return with(action, WeakBiConsumer::new, subscribe);
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

	private Object getAction(Long actionId) {
		return theActions.get(actionId);
	}

	private void unsubscribe() {
		Iterator<ActionStruct> subIter = theActions.values().iterator();
		while (subIter.hasNext()) {
			subIter.next().unsubscribe();
			subIter.remove();
		}
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder implements Subscription {
		private final WeakListening theListening;

		private Builder() {
			theListening = new WeakListening();
		}

		public Builder withUntil(Function<? super Runnable, ? extends Subscription> until) {
			theListening.withAction(//
				() -> theListening.unsubscribe(), //
				until);
			return this;
		}

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

		private void unsubscribe() {
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

		<A> void doOnAction(Consumer<A> a) {
			WeakListening listening = theListening.get();
			if (listening == null) {
				Subscription sub = theSubscription;
				theSubscription = null;
				if (sub != null)
					sub.unsubscribe();
			} else
				a.accept((A) listening.getAction(theActionId));
		}
	}

	private static class WeakRunnable extends WeakAction implements Runnable {
		WeakRunnable(WeakListening listening, Long actionId) {
			super(listening, actionId);
		}

		@Override
		public void run() {
			doOnAction((Runnable r) -> r.run());
		}
	}

	private static class WeakConsumer<E> extends WeakAction implements Consumer<E> {
		WeakConsumer(WeakListening listening, Long actionId) {
			super(listening, actionId);
		}

		@Override
		public void accept(E value) {
			doOnAction((Consumer<E> action) -> action.accept(value));
		}
	}

	private static class WeakBiConsumer<E, F> extends WeakAction implements BiConsumer<E, F> {
		WeakBiConsumer(WeakListening listening, Long actionId) {
			super(listening, actionId);
		}

		@Override
		public void accept(E value1, F value2) {
			doOnAction((BiConsumer<E, F> action) -> action.accept(value1, value2));
		}
	}
}
