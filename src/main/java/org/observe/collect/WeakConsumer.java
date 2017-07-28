package org.observe.collect;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
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
			((Consumer<E>) group.theActions[theActionIndex]).accept(evt);
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
		private final WeakConsumer<?> theWeakActions;
		private final Consumer<?>[] theActions;

		public WeakConsumerGroup(WeakConsumer<?> weakActions, Consumer<?>[] actions) {
			theWeakActions = weakActions;
			theActions = actions;
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
