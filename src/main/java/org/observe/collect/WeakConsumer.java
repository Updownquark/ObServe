package org.observe.collect;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

public class WeakConsumer<E> implements Consumer<E> {
	private final WeakReference<Consumer<? super E>> theAction;
	private CollectionSubscription theSubscription;

	public WeakConsumer(Consumer<? super E> action) {
		theAction = new WeakReference<>(action);
	}

	public void withSubscription(CollectionSubscription sub) {
		if (theSubscription != null)
			throw new IllegalStateException("Already initialized");
		theSubscription = sub;
	}

	@Override
	public void accept(E evt) {
		Consumer<? super E> action = theAction.get();
		if (action != null)
			action.accept(evt);
		else
			theSubscription.unsubscribe();
	}
}
