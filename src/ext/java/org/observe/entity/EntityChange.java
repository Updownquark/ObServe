package org.observe.entity;

import java.time.Instant;
import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;

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
	public abstract BetterList<EntityIdentity<E>> getEntities();

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
		private final BetterList<EntityIdentity<E>> entities;

		/**
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param added Whether this is an addition or deletion change
		 * @param entities The entities that were added or deleted from the entity set
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityExistenceChange(ObservableEntityType<E> type, Instant time, boolean added, BetterList<EntityIdentity<E>> entities,
			Object customData) {
			super(type, time, added ? EntityChangeType.add : EntityChangeType.remove, customData);
			this.entities = entities;
		}

		@Override
		public BetterList<EntityIdentity<E>> getEntities() {
			return entities;
		}

		@Override
		public String toString() {
			return (changeType == EntityChangeType.add ? "+" : "-") + entities;
		}
	}

	/**
	 * Represents a change to a field of one or more entities of a common type
	 *
	 * @param <E> The super type of the entities changed
	 * @param <F> The type of the field
	 */
	public static class FieldChange<E, F> {
		private final ObservableEntityFieldType<E, F> theField;
		private final List<F> theOldValues;
		private final F theNewValue;

		/**
		 * @param field The field that was changed
		 * @param oldValues The list of previous values of the field for each entity affected (in the same order as
		 *        {@link EntityChange#getEntities()})
		 * @param newValue The new value of the field in all the entities
		 */
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

		@Override
		public String toString() {
			return theField + "->" + theNewValue;
		}
	}

	/**
	 * Represents the update of a simple-typed field in an entity
	 *
	 * @param <E> The type of the entity
	 */
	public static class EntityFieldValueChange<E> extends EntityChange<E> {
		private final BetterList<EntityIdentity<E>> entities;
		private final List<FieldChange<E, ?>> theFieldChanges;

		/**
		 * @param type The super type of all entities affected by this change
		 * @param time The time that the change was made
		 * @param entities The identities of the entities that changed
		 * @param fieldChanges Records of each field that changed
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityFieldValueChange(ObservableEntityType<E> type, Instant time, BetterList<EntityIdentity<E>> entities,
			List<FieldChange<E, ?>> fieldChanges, Object customData) {
			super(type, time, EntityChangeType.setField, customData);
			this.entities = entities;
			theFieldChanges = fieldChanges;
		}

		@Override
		public BetterList<EntityIdentity<E>> getEntities() {
			return entities;
		}

		/** @return The fields that changed */
		public List<FieldChange<E, ?>> getFieldChanges() {
			return theFieldChanges;
		}

		@Override
		public String toString() {
			return theFieldChanges + " for " + entities;
		}
	}

	/**
	 * Represents a change to a collection owned by an entity
	 *
	 * @param <E> The type of the entity
	 * @param <F> The type of the values in the collection
	 */
	public static class EntityCollectionFieldChange<E, F> extends EntityChange<E> {
		/** The field whose value changed in the entity */
		public final ObservableEntityFieldType<E, ?> field;
		/** The identity of the entity that changed */
		public final EntityIdentity<E> entity;
		/** The type of the collection change */
		public final CollectionChangeType collectionChangeType;
		/** The location of the element added, removed, or changed */
		public final ElementId element;
		/** The new value of the collection element. May be null for a removal. */
		public final F value;

		/**
		 * @param time The time that the change was made
		 * @param entity The identity of the entity that changed
		 * @param field The collection field that was updated
		 * @param collectionChangeType The type of the collection change
		 * @param element The location of the element added, removed, or changed
		 * @param value The new value of the collection element. May be null for a removal.
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityCollectionFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, ?> field,
			CollectionChangeType collectionChangeType, ElementId element, F value, Object customData) {
			super(entity.getEntityType(), time, EntityChangeType.updateCollectionField, customData);
			this.field = field;
			this.entity = entity;
			this.collectionChangeType = collectionChangeType;
			this.element = element;
			this.value = value;
		}

		@Override
		public BetterList<EntityIdentity<E>> getEntities() {
			return BetterList.of(entity);
		}
	}

	/**
	 * Represents a change to a map owned by an entity
	 *
	 * @param <E> The type of the entity
	 * @param <K> The type of the keys in the map
	 * @param <V> The type of the values in the map
	 */
	public static class EntityMapFieldChange<E, K, V> extends EntityChange<E> {
		/** The field whose value changed in the entity */
		public final ObservableEntityFieldType<E, ?> field;
		/** The identity of the entity that changed */
		public final EntityIdentity<E> entity;
		/** The type of the map change */
		public final CollectionChangeType collectionChangeType;
		/** The location of the entry added, removed, or changed. May be null except for an added event. */
		public final ElementId element;
		/** The key that was added, removed, or updated */
		public final K key;
		/** The new value for the key in the map (null for removal) */
		public final V value; // May be null for removal

		/**
		 * @param time The time that the change was made
		 * @param entity The identity of the entity that changed
		 * @param field The map field that was updated
		 * @param collectionChangeType The type of the map change
		 * @param element The location of the entry added, removed, or changed
		 * @param key The key that was added, removed, or updated
		 * @param value The new value for the key in the map (null for removal)
		 * @param customData Data from the source that may be used to optimize {@link EntityLoadRequest load requests} due to this change
		 */
		public EntityMapFieldChange(Instant time, EntityIdentity<E> entity, ObservableEntityFieldType<E, ?> field,
			CollectionChangeType collectionChangeType, ElementId element, K key, V value, Object customData) {
			super(entity.getEntityType(), time, EntityChangeType.updateMapField, customData);
			this.field = field;
			this.entity = entity;
			this.collectionChangeType = collectionChangeType;
			this.element = element;
			this.key = key;
			this.value = value;
		}

		@Override
		public BetterList<EntityIdentity<E>> getEntities() {
			return BetterList.of(entity);
		}
	}
}
