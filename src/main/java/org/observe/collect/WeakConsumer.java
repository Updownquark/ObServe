package org.observe.collect;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Subscription;

public class WeakConsumer<E> implements Consumer<E> {
	private final WeakReference<WeakConsumerGroup> theGroup;
	private final int theActionIndex;
	private Subscription theSubscription;

	private WeakConsumer(WeakConsumerGroup group, int index) {
		theGroup = new WeakReference<>(group);
		theActionIndex = index;
	}

	@Override
	public void accept(E evt) {
		WeakConsumerGroup group = theGroup.get();
		if (group == null) {
			theSubscription.unsubscribe();
			theSubscription = null;
		} else
			((Consumer<E>) group.theStrongActions[theActionIndex]).accept(evt);
	}

	private void unsubscribe() {
		Subscription sub = theSubscription;
		theSubscription = null;
		if (sub != null)
			sub.unsubscribe();
	}

	public static WeakConsumerBuilder build() {
		return new WeakConsumerBuilder();
	}

	public static class WeakConsumerBuilder {
		private class ActionStruct<E> {
			Consumer<E> theAction;
			Function<? super Consumer<E>, Subscription> theObject;
			boolean isKill;

			ActionStruct(Consumer<E> action, Function<? super Consumer<E>, Subscription> object, boolean kill) {
				theAction = action;
				theObject = object;
				isKill = kill;
			}
		}
		private final List<ActionStruct<?>> theActions;

		WeakConsumerBuilder() {
			theActions = new LinkedList<>();
		}

		<E> WeakConsumerBuilder withAction(Consumer<E> action, Function<? super Consumer<E>, Subscription> object) {
			theActions.add(new ActionStruct<>(action, object, false));
			return this;
		}

		WeakConsumerBuilder withUntil(Function<? super Consumer<Object>, Subscription> object) {
			theActions.add(new ActionStruct<>(null, object, true));
			return this;
		}

		public Subscription build() {
			WeakConsumer<?>[] weakActions = new WeakConsumer[theActions.size()];
			Consumer<?>[] strongActions = new Consumer[theActions.size()];
			WeakConsumerGroup group = new WeakConsumerGroup(weakActions, strongActions);
			for (int i = 0; i < weakActions.length; i++) {
				weakActions[i] = new WeakConsumer<>(group, i);
				ActionStruct<?> action = theActions.get(i);
				strongActions[i] = action.isKill ? v -> group.unsubscribe() : (Consumer<Object>) theActions.get(i).theAction;
				weakActions[i].theSubscription = ((Function<Consumer<?>, Subscription>) theActions.get(i).theObject).apply(weakActions[i]);
			}
			return group;
		}
	}

	private static class WeakConsumerGroup implements Subscription {
		private final WeakConsumer<?>[] theWeakActions;
		private final Consumer<?>[] theStrongActions;
		private final AtomicBoolean isDone;

		public WeakConsumerGroup(WeakConsumer<?>[] weakActions, Consumer<?>[] strongActions) {
			theWeakActions = weakActions;
			theStrongActions = strongActions;
			isDone = new AtomicBoolean();
		}

		@Override
		public void unsubscribe() {
			if (isDone.getAndSet(true))
				return;
			for (WeakConsumer<?> action : theWeakActions)
				action.unsubscribe();
		}
	}
}
