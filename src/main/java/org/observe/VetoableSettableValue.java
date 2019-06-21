package org.observe;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A stamped settable value that allows listeners to veto events. I.e. a listener being notified of a new value can then call
 * {@link #set(Object, Object)} to change the value. When this happens, listeners that have not yet been notified of the previous value
 * never will be. The {@link ObservableValueEvent} instances that each listener receives will have an
 * {@link ObservableValueEvent#getOldValue() old value} of the value that the listener was last notified of.
 * <p>
 *
 * <p>
 * Of course, care must be taken to ensure this cycle does not become infinite or a {@link StackOverflowError} will be thrown.
 * </p>
 *
 * @param <T> The type of the value
 */
public class VetoableSettableValue<T> implements SettableStampedValue<T> {
	private final TypeToken<T> theType;
	private final boolean isNullable;
	private final ReentrantReadWriteLock theLock;
	private final ListenerList<ListenerHolder<T>> theListeners;
	private final VSVChanges theChanges = new VSVChanges();
	private volatile T theValue;
	private volatile long theStamp;
	private boolean isAlive;

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 */
	public VetoableSettableValue(TypeToken<T> type, boolean nullable) {
		this(type, nullable, new ReentrantReadWriteLock());
	}

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 * @param lock The lock for this value;
	 */
	public VetoableSettableValue(TypeToken<T> type, boolean nullable, ReentrantReadWriteLock lock) {
		theType = type;
		isNullable = nullable;
		theLock = lock;
		// We secure this list ourselves, so no need for any thread-safety
		theListeners = ListenerList.build().forEachSafe(false).allowReentrant().withFastSize(false)
			.withSyncType(ListenerList.SynchronizationType.NONE).build();
		isAlive = true;
	}

	@Override
	public TypeToken<T> getType() {
		return theType;
	}

	/** @return Whether null can be assigned to this value */
	public boolean isNullable() {
		return isNullable;
	}

	@Override
	public T get() {
		return theValue;
	}

	@Override
	public Observable<ObservableValueEvent<T>> noInitChanges() {
		return theChanges;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if (accept != null)
			throw new IllegalArgumentException(accept);
		Lock lock = theLock == null ? null : theLock.writeLock();
		if (lock != null)
			lock.lock();
		try {
			if (!isAlive)
				throw new UnsupportedOperationException("This value is no longer alive");
			long[] oldStamp = new long[] { theStamp };
			long newStamp = oldStamp[0] + 1;
			theStamp = newStamp;
			T oldValue = theValue;
			theValue = value;
			if (!theListeners.isEmpty()) {
				ObservableValueEvent<T>[] evt = new ObservableValueEvent[] { createChangeEvent(oldValue, value, cause) };
				LinkedList<Transaction> finishers = new LinkedList<>();
				finishers.add(Causable.use(evt[0]));
				try {
					theListeners.forEach(//
						listener -> {
							long updateDiff = listener.lastUpdated - oldStamp[0];
							if (updateDiff > 0) {
								// Either the listener was added during this event firing
								// or the value for this set call was vetoed by another listener
								return;
							} else if (updateDiff == 0) {
								// The current event is
							} else {
								// This event represents a veto, where a listener caused a set event
								// Since listeners are always added to the end, we may assume that all future listeners
								// also have not been told of the now-vetoed value
								oldStamp[0] = listener.lastUpdated;
								evt[0] = createChangeEvent(listener.knownValue, value, cause);
								finishers.add(Causable.use(evt[0]));
							}
							listener.lastUpdated = newStamp;
							listener.knownValue = value;
							listener.observer.onNext(evt[0]);
						});
				} finally {
					while (!finishers.isEmpty()) {
						try {
							finishers.removeLast().close();
						} catch (RuntimeException e) {
							e.printStackTrace();
						}
					}
				}
			}
			return oldValue;
		} finally {
			if (lock != null)
				lock.unlock();
		}
	}

	@Override
	public <V extends T> String isAcceptable(V value) {
		if (value == null && !isNullable)
			return "Null values not acceptable for this value";
		if (value != null && !TypeTokens.get().isInstance(theType, value))
			return "Value of type " + value.getClass().getName() + " cannot be assigned as " + theType;
		return null;
	}

	@Override
	public ObservableValue<String> isEnabled() {
		return ALWAYS_ENABLED;
	}

	/**
	 * Causes this observable to fire all its listeners' {@link Observer#onCompleted(Object)} methods. Calls to {@link #set(Object, Object)}
	 * after this call will throw {@link UnsupportedOperationException}s.
	 *
	 * @param cause The cause of the death
	 */
	protected void kill(Object cause) {
		Lock lock = theLock == null ? null : theLock.writeLock();
		if (lock != null)
			lock.lock();
		try {
			if (!isAlive)
				throw new UnsupportedOperationException("This value is no already dead");
			isAlive = false;
			long stamp = theStamp;
			ObservableValueEvent<T> completeEvt = createChangeEvent(theValue, theValue, cause);
			try (Transaction evtT = Causable.use(completeEvt)) {
				theListeners.forEach(//
					listener -> {
						if (listener.lastUpdated < stamp) {
							ObservableValueEvent<T> changeEvt = createChangeEvent(listener.knownValue, theValue, completeEvt);
							try (Transaction cet = Causable.use(changeEvt)) {
								listener.observer.onNext(changeEvt);
							}
							listener.lastUpdated = stamp;
						}
						listener.observer.onCompleted(completeEvt);
					});
			}
			theListeners.clear();
		} finally {
			if (lock != null)
				lock.unlock();
		}
	}

	@Override
	public String toString() {
		return "simple value " + theValue;
	}

	private static class ListenerHolder<T> {
		final Observer<? super ObservableValueEvent<T>> observer;
		long lastUpdated;
		T knownValue;

		ListenerHolder(Observer<? super ObservableValueEvent<T>> observer, long lastUpdated, T value) {
			this.observer = observer;
			this.lastUpdated = lastUpdated;
			knownValue = value;
		}
	}

	private class VSVChanges implements Observable<ObservableValueEvent<T>> {
		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			Lock lock = theLock == null ? null : theLock.readLock();
			if (lock != null)
				lock.lock();
			try {
				if (isAlive)
					return theListeners.add(new ListenerHolder<>(observer, theStamp, theValue), false)::run;
				else
					return Subscription.NONE;
			} finally {
				if (lock != null)
					lock.unlock();
			}
		}

		@Override
		public boolean isSafe() {
			return theLock != null;
		}

		@Override
		public Transaction lock() {
			return Transactable.lock(theLock, false);
		}

		@Override
		public Transaction tryLock() {
			return Transactable.tryLock(theLock, false);
		}

		@Override
		public String toString() {
			return "simple value changes " + theValue;
		}
	}
}