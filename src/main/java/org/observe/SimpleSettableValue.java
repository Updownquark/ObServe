package org.observe;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import org.qommons.CausalLock;
import org.qommons.DefaultCausalLock;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * A simple holder for a value that can be retrieved, set, and listened to
 *
 * @param <T> The type of the value
 */
public class SimpleSettableValue<T> extends AbstractIdentifiable implements SettableValue<T> {
	private final SettableValueListening<ObservableValueEvent<T>> theEventer;
	private final CausalLock theLock;

	private final boolean isNullable;
	private T theValue;

	/**
	 * @param description An optional description for this value's identity
	 * @param nullable Whether null can be assigned to the value
	 * @param lock The lock for this value
	 * @param listening Listening builder for this value's listener list (may be null)
	 * @param initialValue The initial value for this value
	 */
	protected SimpleSettableValue(String description, boolean nullable, Function<Object, Transactable> lock,
		Function<? super SettableValue<T>, ListenerList.Builder> listening, T initialValue) {
		isNullable = nullable;
		initIdentity(Identifiable.baseId(description, this));
		if (lock == null)
			theLock = null;
		else {
			Transactable tLock = lock.apply(this);
			if (tLock instanceof CausalLock)
				theLock = (CausalLock) tLock;
			else
				theLock = new DefaultCausalLock(tLock);
		}
		theEventer = createEventer(theLock, listening);
		theValue = initialValue;
	}

	/**
	 * @param description The description for this value
	 * @param nullable Whether this value can accept null values
	 * @param eventableData The lock/listener data for the value
	 * @param initialValue The initial value for this value
	 */
	protected SimpleSettableValue(String description, boolean nullable,
		AbstractEventableBuilder.EventableData<? super SimpleSettableValue<T>> eventableData, T initialValue) {
		isNullable = nullable;
		initIdentity(Identifiable.baseId(description, this));
		Transactable tLock = eventableData.getLock(this);
		if (tLock instanceof CausalLock)
			theLock = (CausalLock) tLock;
		else
			theLock = new DefaultCausalLock(tLock);
		theEventer = createEventer(theLock, eventableData);
		theValue = initialValue;
	}

	@Override
	protected Object createIdentity() {
		throw new IllegalStateException("Should have been initialized");
	}

	@Override
	public SimpleSettableValue<T> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public Getter<T> lock(boolean tryOnly) {
		Transaction lock = theEventer.lock(tryOnly);
		return Getter.of(this, lock);
	}

	@Override
	public Setter<T> lockWrite(boolean tryOnly, Object cause) {
		Transaction lock = theEventer.lockWrite(tryOnly, cause);
		if (lock == null)
			return null;
		return new Setter<T>() {
			@Override
			public T get() {
				return theValue;
			}

			@Override
			public String isEnabled() {
				return null;
			}

			@Override
			public String isAcceptable(T value) {
				if (value == null && !isNullable)
					return "Null values not acceptable for this value";
				return null;
			}

			@Override
			public T set(T value) {
				String msg = isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				T old = theValue;
				theValue = value;
				if (!theEventer.isAnyoneListening()) { // Don't bother creating the event
					theValue = value;
					theEventer.incrementStamp();
				} else if (value == old && theEventer.isEventing()) { // Don't throw errors on recursive updates
					theEventer.incrementStamp();
				} else {
					theValue = value;
					ObservableValueEvent<T> evt = createChangeEvent(old, value, getUnfinishedCauses());
					try (Transaction evtT = evt.use()) {
						theEventer.fire(evt);
					}
				}
				return old;
			}

			@Override
			public void close() {
				lock.close();
			}
		};
	}

	@Override
	public Observable<ObservableValueEvent<T>> noInitChanges() {
		return theEventer;
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
	public long getStamp() {
		return theEventer.getStamp();
	}

	@Override
	public boolean isEventing() {
		return theEventer.isEventing();
	}

	/** @return Whether anyone is listening to changes to this value */
	public boolean isAnyoneListening() {
		return theEventer.isAnyoneListening();
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theLock == null ? Collections.emptyList() : theLock.getCurrentCauses();
	}

	@Override
	public T set(T value) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if (accept != null)
			throw new IllegalArgumentException(accept);

		if (!theEventer.isAnyoneListening()) {
			// If no one's listening, there's no reason to lock anything or make any events.
			T old = theValue;
			theValue = value;
			theEventer.incrementStamp();
			return old;
		}
		// If we're currently in an unlocked state, we can avoid creating 2 causables (one for the root lock, and one for the event)
		// Make a first try at the event outside the lock so we can avoid creating 2 causes for a simple set operation
		// If the value changes before we obtain the lock, we'll have to create another event
		if (getCurrentCauses().isEmpty()) {
			ObservableValueEvent<T> evt = createChangeEvent(theValue, value, getCurrentCauses());
			try (Transaction evtT = evt.use(); Transaction t = theLock == null ? Transaction.NONE : theLock.lockWrite(false, evt)) {
				T old = theValue;
				if (value == old && theEventer.isEventing()) {
					theEventer.incrementStamp();
					return old; // Don't throw errors on recursive updates
				}
				theValue = value;
				Collection<Cause> causes = getUnfinishedCauses();
				if (old == evt.getOldValue() && causes.size() == 1 && causes.iterator().next() == evt)
					theEventer.fire(evt);
				else {
					ObservableValueEvent<T> evt2 = createChangeEvent(old, value, getUnfinishedCauses());
					try (Transaction evt2T = evt2.use()) {
						theEventer.fire(evt2);
					}
				}
				return old;
			}
		} else {
			try (Transaction t = theLock == null ? Transaction.NONE : theLock.lockWrite(false, null)) {
				T old = theValue;
				if (value == old && theEventer.isEventing()) {
					theEventer.incrementStamp();
					return old; // Don't throw errors on recursive updates
				}
				theValue = value;
				ObservableValueEvent<T> evt = createChangeEvent(old, value, getUnfinishedCauses());
				try (Transaction evtT = evt.use()) {
					theEventer.fire(evt);
				}
				return old;
			}
		}
	}

	@Override
	public String isAcceptable(T value) {
		if (value == null && !isNullable)
			return "Null values not acceptable for this value";
		return null;
	}

	@Override
	public ObservableValue<String> isEnabled() {
		return ALWAYS_ENABLED;
	}

	/**
	 * @param lock The lock for this value
	 * @param listening Listening options for this value
	 * @return The observable for this value to use to fire its initial and change events
	 */
	protected SettableValueListening<ObservableValueEvent<T>> createEventer(Transactable lock,
		Function<? super SettableValue<T>, ListenerList.Builder> listening) {
		ListenerList.Builder listenerBuilder = listening == null ? ListenerList.build() : listening.apply(this);
		return new SettableValueListening<>(Identifiable.wrap(getIdentity(), "noInitChanges"), lock,
			listenerBuilder.skipAddByDefault(true).build());
	}

	/**
	 * @param lock The lock for this value
	 * @param eventableData The lock/listener data for the value
	 * @return The observable for this value to use to fire its initial and change events
	 */
	protected SettableValueListening<ObservableValueEvent<T>> createEventer(Transactable lock,
		AbstractEventableBuilder.EventableData<? super SimpleSettableValue<T>> eventableData) {
		ListenerList.Builder listenerBuilder = eventableData.getListening(this);
		return new SettableValueListening<>(Identifiable.wrap(getIdentity(), "noInitChanges"), lock,
			listenerBuilder.skipAddByDefault(true).build());
	}

	@Override
	public String toString() {
		return new StringBuilder(getIdentity().toString()).append('(').append(theValue).append(')').toString();
	}
}
