package org.observe.remote;

import org.observe.ObservableValueEvent;
import org.observe.collect.ObservableCollectionEvent;

public interface ObservableDataSerializer<T, C> {
	void serializeValue(T value, C connection);
	void serializeValueChange(ObservableValueEvent<? extends T> change, long stamp, C connection);
	void serializeCollectionChange(ObservableCollectionEvent<? extends T> change, ByteAddress address, long stamp, C connection);
	void serializeMove(ByteAddress source, ByteAddress dest, T value, long stamp, C connection);
}
