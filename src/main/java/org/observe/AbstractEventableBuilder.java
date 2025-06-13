package org.observe;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.TransactableBuilder;
import org.qommons.collect.ListenerList;

/**
 * Abstract class for a builder of a structure that supports listening via {@link ListenerList}
 *
 * @param <V> The value that this builder builds
 * @param <B> The sub-type of this builder
 */
public class AbstractEventableBuilder<V, B extends AbstractEventableBuilder<V, B>> extends TransactableBuilder.Default<B> {
	private BiConsumer<? super V, ListenerList.Builder> theListenerBuilder;

	/** @param defaultDescrip The description for the built object, if {@link #withDescription(String)} is called */
	protected AbstractEventableBuilder(String defaultDescrip) {
		super(defaultDescrip);
		theListenerBuilder = (v, lb) -> lb.withErrorLogging(err -> {
			System.err.println("Notification error at " + v);
			err.printStackTrace();
		}).reentrancyError(() -> "Reentrancy not allowed: " + v);
	}

	/**
	 * @param listening Configuration for the listener list of the built value
	 * @return This builder
	 */
	public B withListening(Consumer<ListenerList.Builder> listening) {
		BiConsumer<? super V, ListenerList.Builder> oldLB = theListenerBuilder;
		theListenerBuilder = (v, lb) -> {
			oldLB.accept(v, lb);
			listening.accept(lb);
		};
		return (B) this;
	}

	/**
	 * @param listening Configuration for the listener list of the built value
	 * @return This builder
	 */
	public B withListening(BiConsumer<? super V, ListenerList.Builder> listening) {
		BiConsumer<? super V, ListenerList.Builder> oldLB = theListenerBuilder;
		theListenerBuilder = (v, lb) -> {
			oldLB.accept(v, lb);
			listening.accept(v, lb);
		};
		return (B) this;
	}

	/** @return A data object containing information for building the eventable structure */
	protected EventableData<V> buildData() {
		return new EventableData<>(getLocker(), theListenerBuilder);
	}

	/**
	 * A structure containing information on how to build an eventable structure
	 *
	 * @param <V> The value to be built
	 */
	protected static class EventableData<V> {
		private final Function<? super V, Transactable> theLockMaker;
		private final BiConsumer<? super V, ListenerList.Builder> theListeningConfig;

		private Transactable theLock;
		private ListenerList.Builder theListening;

		/**
		 * @param lockMaker The producer of the lock for the value
		 * @param listeningConfig Configuration of the value's listener list
		 */
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

		/**
		 * @param value The instantiated value
		 * @return The lock for the value
		 */
		public Transactable getLock(V value) {
			init(value);
			return theLock;
		}

		/**
		 * @param value The instantiated value
		 * @return The configured listener list builder for the value's listners
		 */
		public ListenerList.Builder getListening(V value) {
			init(value);
			return theListening;
		}
	}
}
