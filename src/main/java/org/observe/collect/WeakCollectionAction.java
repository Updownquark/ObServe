package org.observe.collect;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

public class WeakCollectionAction<E> implements Consumer<ObservableCollectionEvent<? extends E>> {
	private final WeakReference<Consumer<? super ObservableCollectionEvent<? extends E>>> theAction;
	private CollectionSubscription theSubscription;

	private WeakCollectionAction(Consumer<? super ObservableCollectionEvent<? extends E>> action) {
		theAction = new WeakReference<>(action);
	}

	void withSubscription(CollectionSubscription sub) {
		theSubscription = sub;
	}

	public static <E> void subscribeWeakly(ObservableCollection<E> collection,
		Consumer<? super ObservableCollectionEvent<? extends E>> action) {
		WeakCollectionAction<E> weakSub = new WeakCollectionAction<>(action);
		weakSub.withSubscription(collection.subscribe(weakSub));
	}

	@Override
	public void accept(ObservableCollectionEvent<? extends E> evt) {
		Consumer<? super ObservableCollectionEvent<? extends E>> action = theAction.get();
		if (action != null)
			action.accept(evt);
		else
			theSubscription.unsubscribe();
	}
}
