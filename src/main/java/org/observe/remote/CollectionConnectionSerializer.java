package org.observe.remote;

import org.observe.collect.CollectionChangeType;

public interface CollectionConnectionSerializer<E, VP, CP> {
	public class SerializedCollectionChange<E> {
		public final long eventId;
		public final ByteAddress elementId;
		public final CollectionChangeType type;
		public final E oldValue;
		public final E newValue;
		public final boolean transactionEnd;

		public SerializedCollectionChange(long eventId, ByteAddress elementId, CollectionChangeType type, E oldValue, E newValue,
			boolean transactionEnd) {
			this.eventId = eventId;
			this.elementId = elementId;
			this.type = type;
			this.oldValue = oldValue;
			this.newValue = newValue;
			this.transactionEnd = transactionEnd;
		}
	}

	VP serializeValue(E value);
	E deserializeValue(VP serialized);

	CP serializeChange(SerializedCollectionChange<? extends E> change);
	SerializedCollectionChange<E> deserializeChange(CP change);
}
