package org.observe.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

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

	public static abstract class EntityFieldChange<E, F> extends EntityChange<E> {
		public final ObservableEntityFieldType<E, F> field;

		public EntityFieldChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType,
			ObservableEntityFieldType<E, F> field) {
			super(time, entity, changeType);
			this.field = field;
		}
	}

	public static class EntityFieldValueChange<E, F> extends EntityFieldChange<E, F> {
		public final F newValue;

		public EntityFieldValueChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, F> field, F newValue) {
			super(time, entity, EntityChangeType.setField, field);
			this.newValue = newValue;
		}
	}

	public static class EntityCollectionFieldChange<E, F, C extends Collection<F>> extends EntityFieldChange<E, C> {
		public final CollectionChangeType collectionChangeType;
		public final int index; // May be -1 if unknown
		public final F value; // May be null for a removal by index

		public EntityCollectionFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, C> field,
			CollectionChangeType collectionChangeType, int index, F value) {
			super(time, entity, EntityChangeType.updateCollectionField, field);
			this.collectionChangeType = collectionChangeType;
			this.index = index;
			this.value = value;
		}
	}

	public static class EntityMapFieldChange<E, K, V, M extends Map<K, V>> extends EntityFieldChange<E, M> {
		public final CollectionChangeType collectionChangeType;
		public final K key;
		public final V value; // May be null for removal

		public EntityMapFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, M> field,
			CollectionChangeType collectionChangeType, K key, V value) {
			super(time, entity, EntityChangeType.updateMapField, field);
			this.collectionChangeType = collectionChangeType;
			this.key = key;
			this.value = value;
		}
	}
}
