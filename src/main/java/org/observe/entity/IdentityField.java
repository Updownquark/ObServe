package org.observe.entity;

import org.observe.ObservableValue;

public class IdentityField<E, T> extends ObservableValue.ConstantObservableValue<T> implements ObservableEntityField<E, T> {
	public static final String IDENTITY_UNSETTABLE = "Identity fields are immutable";
	public static final ObservableValue<String> IDENTITY_UNSETTABLE_VALUE = ObservableValue.of(IDENTITY_UNSETTABLE);

	private final ObservableEntityFieldType<E, T> theFieldType;

	IdentityField(ObservableEntityFieldType<E, T> type, T value) {
		super(type.getFieldType(), value);
		theFieldType = type;
	}

	@Override
	public ObservableEntityFieldType<E, T> getFieldType() {
		return theFieldType;
	}

	@Override
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
		throw new UnsupportedOperationException(IDENTITY_UNSETTABLE);
	}

	@Override
	public <V extends T> String isAcceptable(V value) {
		return IDENTITY_UNSETTABLE;
	}

	@Override
	public ObservableValue<String> isEnabled() {
		return IDENTITY_UNSETTABLE_VALUE;
	}
}
