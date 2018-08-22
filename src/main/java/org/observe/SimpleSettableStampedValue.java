package org.observe;

import java.util.concurrent.atomic.AtomicLong;

import com.google.common.reflect.TypeToken;

public class SimpleSettableStampedValue<T> extends SimpleSettableValue<T> implements SettableStampedValue<T> {
	private final AtomicLong theStamp;

	public SimpleSettableStampedValue(Class<T> type, boolean nullable) {
		super(type, nullable);
		theStamp = new AtomicLong();
	}

	public SimpleSettableStampedValue(TypeToken<T> type, boolean nullable) {
		super(type, nullable);
		theStamp = new AtomicLong();
	}

	@Override
	public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
		theStamp.getAndIncrement();
		return super.set(value, cause);
	}

	@Override
	public long getStamp() {
		return theStamp.get();
	}
}
