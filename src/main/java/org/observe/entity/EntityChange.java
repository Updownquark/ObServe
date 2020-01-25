package org.observe.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import org.observe.collect.CollectionChangeType;

/**
 * Represents a change to an external entity in an entity source, as reported by a {@link ObservableEntityProvider}
 *
 * @param <E> The type of the entity whose value changed
 */
public class EntityChange<E> {
	/** Types of entity change */
	public enum EntityChangeType {
		/** Represents the addition of a new entity to the data set */
		add,
		/** Represents the removal of an entity from the data set */
		remove,
		/** Represents the update of a simple-typed field in an entity. See {@link EntityChange.EntityFieldValueChange}. */
		setField,
		/** Represents a change to a collection owned by an entity. See {@link EntityChange.EntityCollectionFieldChange}. */
		updateCollectionField,
		/** Represents a change to a map owned by an entity. See {@link EntityChange.EntityMapFieldChange} */
		updateMapField
	}

	/** The time that the change was made */
	public final Instant time;
	/** The identity of the entity that changed */
	public final EntityIdentity<E> entity;
	/** The type of the change */
	public final EntityChangeType changeType;

	/**
	 * @param time The time that the change was made
	 * @param entity The identity of the entity that changed
	 * @param changeType The type of the change
	 */
	public EntityChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType) {
		this.time = time;
		this.entity = entity;
		this.changeType = changeType;
	}

	/**
	 * Represents a change to a field in an entity
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static abstract class EntityFieldChange<E, F> extends EntityChange<E> {
		/** The field whose value changed in the entity */
		public final ObservableEntityFieldType<E, F> field;

		/**
		 * @param time The time that the change was made
		 * @param entity The identity of the entity that changed
		 * @param changeType The type of the change
		 * @param field The field whose value changed in the entity
		 */
		public EntityFieldChange(Instant time, EntityIdentity<E> entity, EntityChangeType changeType,
			ObservableEntityFieldType<E, F> field) {
			super(time, entity, changeType);
			this.field = field;
		}
	}

	/**
	 * Represents the update of a simple-typed field in an entity
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the field
	 */
	public static class EntityFieldValueChange<E, F> extends EntityFieldChange<E, F> {
		/** The previous value in the entity's field */
		public final F oldValue;
		/** The new value in the entity's field */
		public final F newValue;

		/**
		 * @param time The time that the change was made
		 * @param entity The identity of the entity that changed
		 * @param field The field whose value changed in the entity
		 * @param oldValue The previous value of the field
		 * @param newValue The new value of the field
		 */
		public EntityFieldValueChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, F> field, F oldValue,
			F newValue) {
			super(time, entity, EntityChangeType.setField, field);
			this.oldValue = oldValue;
			this.newValue = newValue;
		}
	}

	/**
	 * Represents a change to a collection owned by an entity
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the values in the collection
	 * @param <C> The collection sub-type of the field
	 */
	public static class EntityCollectionFieldChange<E, F, C extends Collection<F>> extends EntityFieldChange<E, C> {
		/** The type of the collection change */
		public final CollectionChangeType collectionChangeType;
		/** The index of the element added, removed, or changed, or -1 if unknown */
		public final int index;
		/** The new value of the collection element. May be null for a removal if {@link #index} is not -1. */
		public final F value;

		/**
		 * @param time The time that the change was made
		 * @param entity The identity of the entity that changed
		 * @param field The collection field that was updated
		 * @param collectionChangeType The type of the collection change
		 * @param index The index of the element added, removed, or changed, or -1 if unknown
		 * @param value The new value of the collection element. May be null for a removal if {@link #index} is not -1.
		 */
		public EntityCollectionFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, C> field,
			CollectionChangeType collectionChangeType, int index, F value) {
			super(time, entity, EntityChangeType.updateCollectionField, field);
			this.collectionChangeType = collectionChangeType;
			this.index = index;
			this.value = value;
		}
	}

	/**
	 * Represents a change to a map owned by an entity
	 *
	 * @param <E> The type of the entity
	 * @param <K> The type of the keys in the map
	 * @param <V> The type of the values in the map
	 * @param <M> The map sub-type of the field
	 */
	public static class EntityMapFieldChange<E, K, V, M extends Map<K, V>> extends EntityFieldChange<E, M> {
		/** The type of the map change */
		public final CollectionChangeType collectionChangeType;
		/** The key that was added, removed, or updated */
		public final K key;
		/** The new value for the key in the map (null for removal) */
		public final V value; // May be null for removal

		/**
		 * @param time The time that the change was made
		 * @param entity The identity of the entity that changed
		 * @param field The map field that was updated
		 * @param collectionChangeType The type of the map change
		 * @param key The key that was added, removed, or updated
		 * @param value The new value for the key in the map (null for removal)
		 */
		public EntityMapFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, M> field,
			CollectionChangeType collectionChangeType, K key, V value) {
			super(time, entity, EntityChangeType.updateMapField, field);
			this.collectionChangeType = collectionChangeType;
			this.key = key;
			this.value = value;
		}
	}
}
