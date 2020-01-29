package org.observe.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.observe.collect.CollectionChangeType;

/**
 * Represents a change to an external entity in an entity source, as reported by a {@link ObservableEntityProvider}
 *
 * @param <E> The type of the entity whose value changed
 */
public abstract class EntityChange<E> {
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

	private final ObservableEntityType<E> theType;
	/** The time that the change was made */
	public final Instant time;
	/** The type of the change */
	public final EntityChangeType changeType;

	/**
	 * @param type The super type of all entities affected by this change
	 * @param time The time that the change was made
	 * @param changeType The type of the change
	 */
	public EntityChange(ObservableEntityType<E> type, Instant time, EntityChangeType changeType) {
		theType = type;
		this.time = time;
		this.changeType = changeType;
	}

	/** @return The super type of all entities affected by this change */
	public ObservableEntityType<E> getEntityType() {
		return theType;
	}

	/** @return The identities of all entities affected by the change */
	public abstract Set<EntityIdentity<? extends E>> getEntities();

	/**
	 * Represents the addition or deletion of entities from the entity set
	 *
	 * @param <E> The type of the entities that were deleted
	 */
	public static class EntityExistenceChange<E> extends EntityChange<E> {
		private final Set<EntityIdentity<? extends E>> entities;

		/**
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param added Whether this is an addition or deletion change
		 * @param entities The entities that were added or deleted from the entity set
		 */
		public EntityExistenceChange(ObservableEntityType<E> type, Instant time, boolean added, Set<EntityIdentity<? extends E>> entities) {
			super(type, time, added ? EntityChangeType.add : EntityChangeType.remove);
			this.entities = entities;
		}

		@Override
		public Set<EntityIdentity<? extends E>> getEntities() {
			return entities;
		}
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
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param changeType The type of the change
		 * @param field The field whose value changed in the entity
		 */
		public EntityFieldChange(ObservableEntityType<E> type, Instant time, EntityChangeType changeType,
			ObservableEntityFieldType<E, F> field) {
			super(type, time, changeType);
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
		private final Set<EntityIdentity<? extends E>> entities;
		/** The previous value in the entity's field */
		public final F oldValue;
		/** The new value in the entity's field */
		public final F newValue;

		/**
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param entities The identities of the entities that changed
		 * @param field The field whose value changed in the entity
		 * @param oldValue The previous value of the field
		 * @param newValue The new value of the field
		 */
		public EntityFieldValueChange(ObservableEntityType<E> type, Instant time, Set<EntityIdentity<? extends E>> entities,
			ObservableEntityFieldType<E, F> field,
			F oldValue, F newValue) {
			super(type, time, EntityChangeType.setField, field);
			this.entities = entities;
			this.oldValue = oldValue;
			this.newValue = newValue;
		}

		@Override
		public Set<EntityIdentity<? extends E>> getEntities() {
			return entities;
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
		/** The identity of the entity that changed */
		public final EntityIdentity<E> entity;
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
			super(entity.getEntityType(), time, EntityChangeType.updateCollectionField, field);
			this.entity = entity;
			this.collectionChangeType = collectionChangeType;
			this.index = index;
			this.value = value;
		}

		@Override
		public Set<EntityIdentity<? extends E>> getEntities() {
			return Collections.singleton(entity);
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
		/** The identity of the entity that changed */
		public final EntityIdentity<E> entity;
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
			super(entity.getEntityType(), time, EntityChangeType.updateMapField, field);
			this.entity = entity;
			this.collectionChangeType = collectionChangeType;
			this.key = key;
			this.value = value;
		}

		@Override
		public Set<EntityIdentity<? extends E>> getEntities() {
			return Collections.singleton(entity);
		}
	}
}
