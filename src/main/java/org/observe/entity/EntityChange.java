package org.observe.entity;

import java.time.Instant;

import org.observe.collect.CollectionChangeType;

public class EntityChange<E> {
	public enum EntityChangeType {
		add, //
		remove, //
		setField, //
		updateCollectionField,
		updateMapField
	}

	public final Instant time;
	public final EntityIdentity<E> entity;
	public final EntityChangeType changeType;

	public EntityChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType) {
		this.time = time;
		this.entity = entity;
		this.changeType = changeType;
	}

	public static class EntityFieldChange<E, F> extends EntityChange<E> {
		public final ObservableEntityFieldType<E, F> field;
		public final F newValue;

		public EntityFieldChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType, ObservableEntityFieldType<E, F> field,
			F newValue) {
			super(time, entity, changeType);
			this.field = field;
			this.newValue = newValue;
		}
	}

	public static class EntityCollectionFieldChange<E, F> extends EntityChange<E> {
		public final CollectionChangeType collectionChangeType;
		public final int index; // May be -1 if unknown
		public final F value; // May be null for a removal by index

		public EntityCollectionFieldChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType,
			CollectionChangeType collectionChangeType, int index, F value) {
			super(time, entity, changeType);
			this.collectionChangeType = collectionChangeType;
			this.index = index;
			this.value = value;
		}
	}

	public static class EntityMapFieldChange<E, K, V> extends EntityChange<E> {
		public final CollectionChangeType collectionChangeType;
		public final K key;
		public final V value; // May be null for removal

		public EntityMapFieldChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType,
			CollectionChangeType collectionChangeType, K key, V value) {
			super(time, entity, changeType);
			this.collectionChangeType = collectionChangeType;
			this.key = key;
			this.value = value;
		}
	}
}
