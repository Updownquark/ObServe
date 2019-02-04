package org.observe;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.observe.util.TypeTokens;
import org.qommons.Causable;
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

	public SimpleSettableValue(TypeToken<T> type, boolean nullable, ReentrantReadWriteLock lock,
		Consumer<ListenerList.Builder> listeningOptions) {
		theType = type;
		isNullable = nullable && !type.isPrimitive();
		theEventer = createEventer(lock, listeningOptions);
	}

	@Override
	public TypeToken<T> getType() {
		return theType;
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

	protected void fireInitial(Observer<? super ObservableValueEvent<T>> observer) {
		ObservableValueEvent<T> event = createInitialEvent(get(), null);
		try (Transaction t = Causable.use(event)) {
			observer.onNext(event);
		}
	}

	@Override
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if (accept != null)
			throw new IllegalArgumentException(accept);
		if (theEventer.isLockSupported())
			theEventer.getLock().writeLock().lock();
		try {
			T old = theValue;
			theValue = value;
			ObservableValueEvent<T> evt = createChangeEvent(old, value, cause);
			try (Transaction t = ObservableValueEvent.use(evt)) {
				theEventer.onNext(evt);
			}
			return old;
		} finally {
			if (theEventer.isLockSupported())
				theEventer.getLock().writeLock().unlock();
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

	/** @return The observable for this value to use to fire its initial and change events */
	protected SimpleObservable<ObservableValueEvent<T>> createEventer(ReentrantReadWriteLock lock,
		Consumer<ListenerList.Builder> listeningOptions) {
		return new SimpleObservable<>(null, true, lock, listeningOptions);
	}

	@Override
	public String toString() {
		return "simple value " + theValue;
	}
}
