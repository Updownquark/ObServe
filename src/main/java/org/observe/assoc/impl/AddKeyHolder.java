package org.observe.assoc.impl;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Lockable;
import org.qommons.Transaction;

public interface AddKeyHolder<K> extends Consumer<K>, Lockable {
	void clear();

	default <K2> AddKeyHolder<K2> map(Function<K2, K> reverse) {
		return new Mapped<>(this, reverse);
	}

	public static class Default<K> implements AddKeyHolder<K>, Supplier<K> {
		private final ReentrantLock theLock;
		private K theKey;
		private boolean isPresent;

		public Default() {
			theLock = new ReentrantLock();
		}

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
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			theLock.lock();
			return () -> theLock.unlock();
		}

		@Override
		public Transaction tryLock() {
			if (theLock.tryLock())
				return () -> theLock.unlock();
				else
					return null;
		}
	}

	public static class Mapped<K, K2> implements AddKeyHolder<K2> {
		private final AddKeyHolder<K> theSource;
		private final Function<K2, K> theReverse;

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
	}
}
