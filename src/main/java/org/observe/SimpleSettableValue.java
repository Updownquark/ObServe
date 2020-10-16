package org.observe;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.util.TypeTokens;
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

	private final TypeToken<T> theType;
	private final boolean isNullable;
	private final Object theIdentity;
	private long theStamp;
	private T theValue;

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 */
	public SimpleSettableValue(Class<T> type, boolean nullable) {
		this(TypeTokens.get().of(type), nullable);
	}

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 */
	public SimpleSettableValue(TypeToken<T> type, boolean nullable) {
		this(type, nullable, new ReentrantReadWriteLock(), null);
	}

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 * @param lock The lock for this value
	 * @param listeningOptions Listening options for this value's listener list
	 */
	public SimpleSettableValue(TypeToken<T> type, boolean nullable, ReentrantReadWriteLock lock,
		Consumer<ListenerList.Builder> listeningOptions) {
		this(type, "settable-value" + SettableValue.Builder.ID_GEN.getAndIncrement(), //
			nullable, lock == null ? null : sv -> Transactable.transactable(lock, sv), listeningFor(listeningOptions));
	}

	private static ListenerList.Builder listeningFor(Consumer<ListenerList.Builder> listeningOptions) {
		ListenerList.Builder listening = ListenerList.build();
		if (listeningOptions != null)
			listeningOptions.accept(listening);
		return listening;
	}

	SimpleSettableValue(TypeToken<T> type, String description, boolean nullable, Function<Object, Transactable> lock,
		ListenerList.Builder listening) {
		theType = type;
		isNullable = nullable && !type.isPrimitive();
		theIdentity = Identifiable.baseId(description, this);
		theEventer = createEventer(lock == null ? null : lock.apply(this), listening);
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
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if (accept != null)
			throw new IllegalArgumentException(accept);
		try (Transaction t = theEventer.lockWrite()) {
			T old = theValue;
			theStamp++;
			theValue = value;
			ObservableValueEvent<T> evt = createChangeEvent(old, value, cause);
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
		return "simple value " + theValue;
	}
}
