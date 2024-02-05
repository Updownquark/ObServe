package org.observe;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import org.observe.util.TypeTokens;
import org.qommons.CausalLock;
import org.qommons.DefaultCausalLock;
import org.qommons.Identifiable;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

import com.google.common.reflect.TypeToken;

/**
 * A simple holder for a value that can be retrieved, set, and listened to
 *
 * @param <T> The type of the value
 */
public class SimpleSettableValue<T> implements SettableValue<T> {
	private final SimpleObservable<ObservableValueEvent<T>> theEventer;
	private final CausalLock theLock;

	private final TypeToken<T> theType;
	private final boolean isNullable;
	private final Object theIdentity;
	private long theStamp;
	private T theValue;

	/**
	 * @param type The type of the value
	 * @param description An optional description for this value's identity
	 * @param nullable Whether null can be assigned to the value
	 * @param lock The lock for this value
	 * @param listening Listening builder for this value's listener list (may be null)
	 * @param initialValue The initial value for this value
	 */
	protected SimpleSettableValue(TypeToken<T> type, String description, boolean nullable, Function<Object, Transactable> lock,
		ListenerList.Builder listening, T initialValue) {
		theType = type;
		isNullable = nullable && !type.isPrimitive();
		theIdentity = Identifiable.baseId(description, this);
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

	@Override
	public TypeToken<T> getType() {
		return theType;
	}

	@Override
	public Object getIdentity() {
		return theIdentity;
	}

	@Override
	public boolean isLockSupported() {
		return theEventer.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return Transactable.lock(theEventer.getLock(), write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return Transactable.tryLock(theEventer.getLock(), write, cause);
	}

	@Override
	public Observable<ObservableValueEvent<T>> noInitChanges() {
		return theEventer.readOnly();
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
		return theStamp;
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theLock == null ? Collections.emptyList() : theLock.getCurrentCauses();
	}

	@Override
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if (accept != null)
			throw new IllegalArgumentException(accept);
		try (Transaction t = theLock == null ? Transaction.NONE : theLock.lock(true, cause)) {
			T old = theValue;
			if (value == old && theEventer.isEventing())
				return old; // Don't throw errors on recursive updates
			theStamp++;
			theValue = value;
			ObservableValueEvent<T> evt = createChangeEvent(old, value, getUnfinishedCauses());
			try (Transaction evtT = evt.use()) {
				theEventer.onNext(evt);
			}
			return old;
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
	 * @param lock The lock for this value
	 * @param listening Listening options for this value
	 * @return The observable for this value to use to fire its initial and change events
	 */
	protected SimpleObservable<ObservableValueEvent<T>> createEventer(Transactable lock, ListenerList.Builder listening) {
		return new SimpleObservable<>(null, Identifiable.wrap(theIdentity, "noInitChanges"), null, true, __ -> lock, listening);
	}

	@Override
	public String toString() {
		return new StringBuilder(theIdentity.toString()).append('(').append(theValue).append(')').toString();
	}
}
