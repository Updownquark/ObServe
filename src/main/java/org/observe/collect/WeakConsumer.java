package org.observe.collect;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Subscription;

public class WeakConsumer<E> implements Consumer<E> {
	private final WeakConsumerGroup theGroup;
	private final WeakReference<Consumer<? super E>> theAction;
	private Subscription theSubscription;

	private WeakConsumer(WeakConsumerGroup group, Consumer<? super E> action) {
		theGroup = group;
		theAction = new WeakReference<>(action);
	}

	@Override
	public void accept(E evt) {
		Consumer<? super E> action = theAction.get();
		if (action != null)
			action.accept(evt);
		else
			theSubscription.unsubscribe();
	}

	public static WeakConsumerBuilder build() {
		return new WeakConsumerBuilder();
	}

	public static class WeakConsumerBuilder {
		private class ActionStruct<E> {
			Consumer<E> theAction;
			Function<? super Consumer<E>, Subscription> theObject;

			ActionStruct(Consumer<E> action, Function<? super Consumer<E>, Subscription> object) {
				theAction = action;
				theObject = object;
			}
		}
		private final List<ActionStruct<?>> theActions;

		WeakConsumerBuilder() {
			theActions = new LinkedList<>();
		}

		<E> WeakConsumerBuilder withAction(Consumer<E> action, Function<? super Consumer<E>, Subscription> object) {
			theActions.add(new ActionStruct<>(action, object));
			return this;
		}

		public Subscription build() {
			WeakConsumer<?>[] weakActions = new WeakConsumer[theActions.size()];
			WeakConsumerGroup group = new WeakConsumerGroup(weakActions);
			for (int i = 0; i < weakActions.length; i++) {
				weakActions[i] = new WeakConsumer<Object>(group, theActions.get(i));
				weakActions[i].theSubscription = ((Function<Consumer<?>, Subscription>) theActions.get(i).theObject).apply(weakActions[0]);
			}
			return group;
		}
	}

	public static class WeakConsumerGroup implements Subscription {
		private final AtomicReference<WeakConsumer<?>[]> theActions;

		public WeakConsumerGroup(WeakConsumer<?>[] actions) {
			theActions = new AtomicReference<>(actions);
		}

		@Override
		public void unsubscribe() {
			WeakConsumer<?>[] actions = theActions.getAndSet(null);
			if (actions == null)
				return;
			for (WeakConsumer<?> action : actions)
				action.theSubscription.unsubscribe();
		}
	}
}
