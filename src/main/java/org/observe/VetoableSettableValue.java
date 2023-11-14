package org.observe;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Function;

import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.CausalLock;
import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * A settable value that allows listeners to veto events. I.e. a listener being notified of a new value can then call
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
public class VetoableSettableValue<T> implements SettableValue<T> {
	private final TypeToken<T> theType;
	private final String theDescription;
	private final boolean isNullable;
	private final CausalLock theLock;
	private final ListenerList<ListenerHolder<T>> theListeners;
	private final VSVChanges theChanges = new VSVChanges();
	private volatile T theValue;
	private volatile long theStamp;
	private boolean isAlive;

	private Object theIdentity;
	private Object theChangesIdentity;

	VetoableSettableValue(TypeToken<T> type, String description, boolean nullable, ListenerList.Builder listening,
		Function<Object, Transactable> lock, T initialValue) {
		theType = type;
		theDescription = description;
		isNullable = nullable;
		theLock = lock == null ? null : new CausalLock(lock.apply(this));
		if (theLock != null) {
			// We secure this list ourselves, so no need for any thread-safety
			listening.forEachSafe(false).allowReentrant().withFastSize(false).withSync(false);
		}
		theListeners = listening.build();
		theValue = initialValue;
		isAlive = true;
	}

	@Override
	public TypeToken<T> getType() {
		return theType;
	}

	@Override
	public Object getIdentity() {
		if (theIdentity == null)
			theIdentity = Identifiable.baseId(theDescription, this);
		return theIdentity;
	}

	/** @return Whether null can be assigned to this value */
	public boolean isNullable() {
		return isNullable;
	}

	@Override
	public boolean isLockSupported() {
		return theLock.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLock.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLock.tryLock(write, cause);
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

	private Collection<?> getCurrentCauses() {
		return theLock == null ? Collections.emptyList() : theLock.getCurrentCauses();
	}

	@Override
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if (accept != null)
			throw new IllegalArgumentException(accept);
		try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(true, cause)) {
			if (!isAlive)
				throw new UnsupportedOperationException("This value is no longer alive");
			T oldValue = theValue;
			if (value == oldValue && theListeners.isFiring())
				return oldValue; // Don't throw errors on recursive updates
			long[] oldStamp = new long[] { theStamp };
			long newStamp = oldStamp[0] + 1;
			theStamp = newStamp;
			theValue = value;
			if (!theListeners.isEmpty()) {
				ObservableValueEvent<T>[] evt = new ObservableValueEvent[] { createChangeEvent(oldValue, value, getCurrentCauses()) };
				LinkedList<Transaction> finishers = new LinkedList<>();
				finishers.add(evt[0].use());
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
								evt[0] = createChangeEvent(listener.knownValue, value, getCurrentCauses());
								finishers.add(evt[0].use());
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
	 * Causes this observable to fire all its listeners' {@link Observer#onCompleted(Causable)} methods. Calls to
	 * {@link #set(Object, Object)} after this call will throw {@link UnsupportedOperationException}s.
	 *
	 * @param cause The cause of the death
	 */
	protected void kill(Object cause) {
		try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(true, cause)) {
			if (!isAlive)
				throw new UnsupportedOperationException("This value is already dead");
			isAlive = false;
			long stamp = theStamp;
			ObservableValueEvent<T> completeEvt = createChangeEvent(theValue, theValue, getCurrentCauses());
			try (Transaction evtT = completeEvt.use()) {
				theListeners.forEach(//
					listener -> {
						if (listener.lastUpdated < stamp) {
							ObservableValueEvent<T> changeEvt = createChangeEvent(listener.knownValue, theValue, completeEvt);
							try (Transaction cet = changeEvt.use()) {
								listener.observer.onNext(changeEvt);
							}
							listener.lastUpdated = stamp;
						}
						listener.observer.onCompleted(completeEvt);
					});
			}
			theListeners.clear();
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
		public Object getIdentity() {
			if (theChangesIdentity == null)
				theChangesIdentity = Identifiable.wrap(VSVChanges.this.getIdentity(), "noInitChanges");
			return theChangesIdentity;
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
			try (Transaction lock = theLock == null ? Transaction.NONE : theLock.lock(false, null)) {
				if (isAlive)
					return theListeners.add(new ListenerHolder<>(observer, theStamp, theValue), false)::run;
				else
					return Subscription.NONE;
			}
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theLock == null ? ThreadConstraint.ANY : theLock.getThreadConstraint();
		}

		@Override
		public boolean isEventing() {
			return theListeners.isFiring();
		}

		@Override
		public boolean isSafe() {
			return theLock != null;
		}

		@Override
		public Transaction lock() {
			return theLock == null ? Transaction.NONE : theLock.lock(false, null);
		}

		@Override
		public Transaction tryLock() {
			return theLock == null ? Transaction.NONE : theLock.tryLock(false, null);
		}

		@Override
		public CoreId getCoreId() {
			return theLock == null ? CoreId.EMPTY : theLock.getCoreId();
		}

		@Override
		public String toString() {
			return "simple value changes " + theValue;
		}
	}
}
