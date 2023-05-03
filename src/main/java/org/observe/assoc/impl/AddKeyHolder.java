package org.observe.assoc.impl;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Lockable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/**
 * Used internally for adding entries to
 * {@link org.observe.collect.ObservableCollection.CollectionDataFlow#groupBy(Function, java.util.function.BiFunction) grouped} multi-maps
 *
 * @param <K> The key type of the map
 */
public interface AddKeyHolder<K> extends Consumer<K>, Lockable {
	/** Clears the value from this holder */
	void clear();

	/**
	 * @param reverse The function to produce a key of this type from a key of the mapped type
	 * @return The mapped key holder
	 */
	default <K2> AddKeyHolder<K2> map(Function<K2, K> reverse) {
		return new Mapped<>(this, reverse);
	}

	/**
	 * Base implementation of {@link AddKeyHolder}
	 *
	 * @param <K> The key type of the map
	 */
	public static class Default<K> implements AddKeyHolder<K>, Supplier<K> {
		private final ReentrantLock theLock;
		private K theKey;
		private boolean isPresent;

		/** Creates the key holder */
		public Default() {
			theLock = new ReentrantLock();
		}

		/** @return Whether a key is present in this holder */
		public boolean isPresent() {
			return isPresent;
		}

		@Override
		public void accept(K key) {
			theKey=key;
			isPresent = true;
		}

		@Override
		public K get() {
			return theKey;
		}

		@Override
		public void clear() {
			theKey = null;
			isPresent = false;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.ANY;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			theLock.lock();
			return theLock::unlock;
		}

		@Override
		public Transaction tryLock() {
			if (theLock.tryLock())
				return theLock::unlock;
			else
				return null;
		}

		@Override
		public CoreId getCoreId() {
			return new CoreId(theLock);
		}
	}

	/**
	 * Implements {@link AddKeyHolder#map(Function)}
	 *
	 * @param <K> The key type of the source key holder
	 * @param <K2> The key type of the mapped key holder
	 */
	public static class Mapped<K, K2> implements AddKeyHolder<K2> {
		private final AddKeyHolder<K> theSource;
		private final Function<K2, K> theReverse;

		/**
		 * @param source The source key holder
		 * @param reverse The function to produce a key of this type from a key of the mapped type
		 */
		public Mapped(AddKeyHolder<K> source, Function<K2, K> reverse) {
			theSource = source;
			theReverse = reverse;
		}

		@Override
		public void accept(K2 key) {
			theSource.accept(theReverse.apply(key));
		}

		@Override
		public void clear() {
			theSource.clear();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theSource.getThreadConstraint();
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock() {
			return theSource.lock();
		}

		@Override
		public Transaction tryLock() {
			return theSource.tryLock();
		}

		@Override
		public CoreId getCoreId() {
			return theSource.getCoreId();
		}
	}
}
