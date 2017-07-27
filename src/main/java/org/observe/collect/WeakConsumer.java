package org.observe.collect;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;

public class WeakConsumer<E> implements Consumer<E> {
	private final WeakReference<Consumer<? super E>> theAction;
	private Subscription theSubscription;

	private WeakConsumer(Consumer<? super E> action) {
		theAction = new WeakReference<>(action);
	}

	private WeakConsumer<E> withSubscription(Subscription sub) {
		if (theSubscription != null)
			throw new IllegalStateException("Already initialized");
		theSubscription = sub;
		return this;
	}

	@Override
	public void accept(E evt) {
		Consumer<? super E> action = theAction.get();
		if (action != null)
			action.accept(evt);
		else
			theSubscription.unsubscribe();
	}

	public static <E> Subscription subscribeWeak(Consumer<E> action, Function<? super Consumer<E>, Subscription> object,
		Observable<?> until) {

		Consumer<?>[] strongActions = new Consumer[2];
		WeakConsumer<?>[] weakActions = new WeakConsumer[2];
		Subscription[] weakSubs = new Subscription[2];

		strongActions[0] = action;
		weakActions[0] = new WeakConsumer<>(action);
		weakSubs[0] = object.apply((Consumer<E>) weakActions[0]);
		if (until != null) {
			strongActions[1] = v -> {
				weakSubs[1] = null;
				Subscription ws = weakSubs[0];
				weakSubs[0] = null;
				if (ws != null)
					ws.unsubscribe();
			};
			weakActions[1] = new WeakConsumer<>(strongActions[1]);
			weakSubs[1] = until.take(1).act((Consumer<Object>) weakActions[1]);
		}
		return new WeakSubscription<>(weakSubs, strongActions);
	}

	private static class WeakSubscription<E> implements Subscription {
		private Subscription[] weakSubs;
		private Consumer<?>[] strongActions;

		WeakSubscription(Subscription[] weakSubs, Consumer<?>[] strongActions) {
			this.weakSubs = weakSubs;
			this.strongActions = strongActions;
		}

		@Override
		public void unsubscribe() {
			Subscription ws0 = weakSubs[0];
			Subscription ws1 = weakSubs[1];
			weakSubs[0] = null;
			weakSubs[1] = null;
			if (ws0 != null)
				ws0.unsubscribe();
			if (ws1 != null)
				ws1.unsubscribe();
			strongActions[0] = null;
			strongActions[1] = null;
		}
	}
}
