package org.observe;

import com.google.common.reflect.TypeToken;

/**
 * A simple holder for a value that can be retrieved, set, and listened to
 *
 * @param <T> The type of the value
 */
public class SimpleSettableValue<T> extends DefaultSettableValue<T> {
	private final Observer<ObservableValueEvent<T>> theController;

	private final TypeToken<T> theType;
	private final boolean isNullable;
	private T theValue;

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 */
	public SimpleSettableValue(TypeToken<T> type, boolean nullable) {
		theController = control(null);
		theType = type;
		isNullable = nullable && !type.isPrimitive();
	}

	/**
	 * @param type The type of the value
	 * @param nullable Whether null can be assigned to the value
	 */
	public SimpleSettableValue(Class<T> type, boolean nullable) {
		this(TypeToken.of(type), nullable);
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
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		String accept = isAcceptable(value);
		if(accept != null)
			throw new IllegalArgumentException(accept);
		T old = theValue;
		theValue = value;
		theController.onNext(createChangeEvent(old, value, cause));
		return old;
	}

	@Override
	public <V extends T> String isAcceptable(V value) {
		if(value == null && !isNullable)
			return "Null values not acceptable for this value";
		if(value != null && !theType.wrap().getRawType().isInstance(value))
			return "Value of type " + value.getClass().getName() + " cannot be assigned as " + theType;
		return null;
	}

	@Override
	public ObservableValue<String> isEnabled() {
		return ObservableValue.constant(TypeToken.of(String.class), null);
	}

	@Override
	public String toString() {
		return "simple value " + theValue;
	}
}
