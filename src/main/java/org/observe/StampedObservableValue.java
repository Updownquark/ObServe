package org.observe;

public interface StampedObservableValue<T> extends ObservableValue<T> {
	long getStamp();
}
