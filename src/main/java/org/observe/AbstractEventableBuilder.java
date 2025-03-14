package org.observe;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.TransactableBuilder;
import org.qommons.collect.ListenerList;

public class AbstractEventableBuilder<V, B extends AbstractEventableBuilder<V, B>> extends TransactableBuilder.Default<B> {
	private BiConsumer<? super V, ListenerList.Builder> theListenerBuilder;

	protected AbstractEventableBuilder(String defaultDescrip) {
		super(defaultDescrip);
		theListenerBuilder = (v, lb) -> lb.withErrorLogging(err -> {
			System.err.println("Notification error at " + v);
			err.printStackTrace();
		}).reentrancyError(() -> "Reentrancy not allowed: " + v);
	}

	public B withListening(Consumer<ListenerList.Builder> listening) {
		BiConsumer<? super V, ListenerList.Builder> oldLB = theListenerBuilder;
		theListenerBuilder = (v, lb) -> {
			oldLB.accept(v, lb);
			listening.accept(lb);
		};
		return (B) this;
	}

	public B withListening(BiConsumer<? super V, ListenerList.Builder> listening) {
		BiConsumer<? super V, ListenerList.Builder> oldLB = theListenerBuilder;
		theListenerBuilder = (v, lb) -> {
			oldLB.accept(v, lb);
			listening.accept(v, lb);
		};
		return (B) this;
	}

	protected EventableData<V> buildData() {
		return new EventableData<>(getLocker(), theListenerBuilder);
	}

	protected static class EventableData<V> {
		private final Function<? super V, Transactable> theLockMaker;
		private final BiConsumer<? super V, ListenerList.Builder> theListeningConfig;

		private Transactable theLock;
		private ListenerList.Builder theListening;

		public EventableData(Function<? super V, Transactable> lockMaker, BiConsumer<? super V, ListenerList.Builder> listeningConfig) {
			theLockMaker = lockMaker;
			theListeningConfig = listeningConfig;
		}

		private void init(V value) {
			if (theLock == null) {
				theLock = theLockMaker.apply(value);
				ListenerList.Builder listening = ListenerList.build();
				if (theListeningConfig != null)
					theListeningConfig.accept(value, listening);
				if (theLock != null && theLock.getThreadConstraint().isDedicated())
					listening.forEachSafe(false); // forEach will only be called on a single thread, so no need to secure firing here
				theListening = listening;
			}
		}

		public Transactable getLock(V value) {
			init(value);
			return theLock;
		}

		public ListenerList.Builder getListening(V value) {
			init(value);
			return theListening;
		}
	}
}
