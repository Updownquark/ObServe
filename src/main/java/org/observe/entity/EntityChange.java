package org.observe.entity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.observe.collect.CollectionChangeType;
import org.qommons.collect.BetterList;

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
	private final Object theCustomData;

	/**
	 * @param type The super type of all entities affected by this change
	 * @param time The time that the change was made
	 * @param changeType The type of the change
	 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
	 */
	public EntityChange(ObservableEntityType<E> type, Instant time, EntityChangeType changeType, Object customData) {
		theType = type;
		this.time = time;
		this.changeType = changeType;
		theCustomData = customData;
	}

	/** @return The super type of all entities affected by this change */
	public ObservableEntityType<E> getEntityType() {
		return theType;
	}

	/** @return The identities of all entities affected by the change */
	public abstract BetterList<EntityIdentity<? extends E>> getEntities();

	/** @return Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change */
	public Object getCustomData() {
		return theCustomData;
	}

	/**
	 * Represents the addition or deletion of entities from the entity set
	 *
	 * @param <E> The type of the entities that were deleted
	 */
	public static class EntityExistenceChange<E> extends EntityChange<E> {
		private final BetterList<EntityIdentity<? extends E>> entities;

		/**
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param added Whether this is an addition or deletion change
		 * @param entities The entities that were added or deleted from the entity set
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityExistenceChange(ObservableEntityType<E> type, Instant time, boolean added,
			BetterList<EntityIdentity<? extends E>> entities, Object customData) {
			super(type, time, added ? EntityChangeType.add : EntityChangeType.remove, customData);
			this.entities = entities;
		}

		@Override
		public BetterList<EntityIdentity<? extends E>> getEntities() {
			return entities;
		}
	}

	public static class FieldChange<E, F> {
		private final ObservableEntityFieldType<E, F> theField;
		private final List<F> theOldValues;
		private final F theNewValue;

		public FieldChange(ObservableEntityFieldType<E, F> field, List<F> oldValues, F newValue) {
			theField = field;
			theOldValues = oldValues;
			theNewValue = newValue;
		}

		/** @return The field that changed */
		public ObservableEntityFieldType<E, F> getField() {
			return theField;
		}

		/** @return The previously set values of the field for each entity affected (in the same order) */
		public List<F> getOldValues() {
			return theOldValues;
		}

		/** @return The single new value of the field in all the affected entities */
		public F getNewValue() {
			return theNewValue;
		}
	}

	/**
	 * Represents the update of a simple-typed field in an entity
	 *
	 * @param <E> The type of the entity
	 */
	public static class EntityFieldValueChange<E> extends EntityChange<E> {
		private final BetterList<EntityIdentity<? extends E>> entities;
		private final List<FieldChange<E, ?>> theFieldChanges;

		/**
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param entities The identities of the entities that changed
		 * @param fieldChanges Records of each field that changed
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityFieldValueChange(ObservableEntityType<E> type, Instant time, BetterList<EntityIdentity<? extends E>> entities,
			List<FieldChange<E, ?>> fieldChanges, Object customData) {
			super(type, time, EntityChangeType.setField, customData);
			this.entities = entities;
			theFieldChanges = fieldChanges;
		}

		@Override
		public BetterList<EntityIdentity<? extends E>> getEntities() {
			return entities;
		}

		/** @return The fields that changed */
		public List<FieldChange<E, ?>> getFieldChanges() {
			return theFieldChanges;
		}
	}

	/**
	 * Represents a change to a collection owned by an entity
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the values in the collection
	 * @param <C> The collection sub-type of the field
	 */
	public static class EntityCollectionFieldChange<E, F, C extends Collection<F>> extends EntityChange<E> {
		/** The field whose value changed in the entity */
		public final ObservableEntityFieldType<E, C> field;
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
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityCollectionFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, C> field,
			CollectionChangeType collectionChangeType, int index, F value, Object customData) {
			super(entity.getEntityType(), time, EntityChangeType.updateCollectionField, customData);
			this.field = field;
			this.entity = entity;
			this.collectionChangeType = collectionChangeType;
			this.index = index;
			this.value = value;
		}

		@Override
		public BetterList<EntityIdentity<? extends E>> getEntities() {
			return BetterList.of(entity);
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
	public static class EntityMapFieldChange<E, K, V, M extends Map<K, V>> extends EntityChange<E> {
		/** The field whose value changed in the entity */
		public final ObservableEntityFieldType<E, M> field;
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
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityMapFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, M> field,
			CollectionChangeType collectionChangeType, K key, V value, Object customData) {
			super(entity.getEntityType(), time, EntityChangeType.updateMapField, customData);
			this.field = field;
			this.entity = entity;
			this.collectionChangeType = collectionChangeType;
			this.key = key;
			this.value = value;
		}

		@Override
		public BetterList<EntityIdentity<? extends E>> getEntities() {
			return BetterList.of(entity);
		}
	}
}
