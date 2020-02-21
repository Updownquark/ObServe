package org.observe.entity;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Stamped;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A managed entity in a data set
 *
 * @param <E> The java-type of the entity
 */
public interface ObservableEntity<E> extends Stamped, Identifiable, Comparable<ObservableEntity<?>> {
	/** The type key for ObservableEntity that can be used to create {@link TypeToken}s efficiently */
	@SuppressWarnings("rawtypes")
	public static final TypeTokens.TypeKey<ObservableEntity> TYPE_KEY = TypeTokens.get().keyFor(ObservableEntity.class)
	.enableCompoundTypes(new TypeTokens.UnaryCompoundTypeCreator<ObservableEntity>() {
		@Override
		public <P> TypeToken<ObservableEntity<P>> createCompoundType(TypeToken<P> param) {
			return new TypeToken<ObservableEntity<P>>() {}.where(new TypeParameter<P>() {}, param);
		}
	});
	/** TypeToken<ObservableEntity<?>> */
	public static final TypeToken<ObservableEntity<?>> TYPE = TYPE_KEY.parameterized();
	/** The message returned from {@link #isAcceptable(int, Object)} or {@link #canDelete()} if the entity has already been removed */
	public static final String ENTITY_REMOVED = "This entity has been removed";

	/** @return The entity type that this entity is a member of */
	ObservableEntityType<E> getType();

	/** @return The java-typed entity */
	E getEntity();

	/** @return The entity's identity */
	EntityIdentity<E> getId();
	@Override
	default EntityIdentity<E> getIdentity() {
		return getId();
	}

	/**
	 * @param fieldIndex The index of the field to get
	 * @return The value for the given field in this entity
	 */
	Object get(int fieldIndex);

	/**
	 * @param field The field to get
	 * @return The value for the given field in this entity
	 */
	default <F> F get(ObservableEntityFieldType<? super E, F> field) {
		if (!getType().equals(field.getEntityType()))
			field = (ObservableEntityFieldType<E, F>) getType().getFields().get(field.getName());
		return (F) get(field.getFieldIndex());
	}

	/**
	 * @param fieldIndex The index of the given field to check
	 * @param value The value to check for the field
	 * @return A message detailing why the operation is unsupported or the given value is not acceptable for the given field in this entity,
	 *         or null if it is acceptable
	 */
	String isAcceptable(int fieldIndex, Object value);
	/**
	 * @param <F> The type of the field
	 * @param fieldIndex The index of the field to set
	 * @param value The value to set for the field
	 * @param cause The cause of the change, if any
	 * @return The previous value of the field
	 * @throws UnsupportedOperationException If the operation is unsupported at the moment regardless of the value
	 * @throws IllegalArgumentException If the value may not be set for the given field
	 */
	<F> F set(int fieldIndex, F value, Object cause) throws UnsupportedOperationException, IllegalArgumentException;

	/**
	 * @param fieldIndex The index of the field to set
	 * @param value The value to set for the field
	 * @param sync Whether to execute the update synchronously (blocking until complete) or asynchronously
	 * @param cause The cause of the change, if any
	 * @return The modification result
	 * @throws IllegalArgumentException If the value is unacceptable for the field
	 * @throws UnsupportedOperationException If the entity has been deleted or the field is not settable
	 * @throws EntityOperationException If the operation fails synchronously
	 */
	EntityModificationResult<E> update(int fieldIndex, Object value, boolean sync, Object cause)
		throws IllegalArgumentException, UnsupportedOperationException, EntityOperationException;

	/**
	 * @param fieldIndex The index of the field to watch
	 * @return An observable that fires an event whenever the value of the given field in this entity changes
	 */
	default Observable<? extends ObservableEntityFieldEvent<E, ?>> fieldChanges(int fieldIndex) {
		return allFieldChanges().filter(evt -> evt.getField().getFieldIndex() == fieldIndex);
	}
	/** @return An observable that fires an event whenever the value of any field in this entity changes */
	Observable<ObservableEntityFieldEvent<E, ?>> allFieldChanges();

	/**
	 * @param field The field to check
	 * @return Whether the value of the given field is already loaded in this entity for immediate access
	 */
	boolean isLoaded(ObservableEntityFieldType<? super E, ?> field);

	/**
	 * @param field The field to load
	 * @param onLoad Called when the load succeeds (or null to do the call synchronously, blocking until success or failure)
	 * @param onFail Called when the load fails
	 * @return This entity
	 * @throws EntityOperationException If the operation fails synchronously
	 */
	<F> ObservableEntity<E> load(ObservableEntityFieldType<E, F> field, Consumer<? super F> onLoad,
		Consumer<EntityOperationException> onFail) throws EntityOperationException;

	/**
	 * @param fieldName The name of the field to get
	 * @return An entity field value for the given field
	 */
	default ObservableEntityField<E, ?> getField(String fieldName) {
		ObservableEntityFieldType<E, ?> fieldType = getType().getFields().getIfPresent(fieldName);
		if (fieldType == null)
			throw new IllegalArgumentException("No such field " + getType().getName() + "." + fieldName);
		return getField(fieldType);
	}
	/**
	 * @param fieldIndex The index of the field to get
	 * @return An entity field value for the given field
	 */
	default ObservableEntityField<E, ?> getField(int fieldIndex) {
		return getField(getType().getFields().get(fieldIndex));
	}
	/**
	 * @param fieldGetter The getter for the field to get
	 * @return An entity field value for the given field
	 */
	default <F> ObservableEntityField<E, F> getField(Function<? super E, F> fieldGetter) {
		return getField(getType().getField(fieldGetter));
	}
	/**
	 * @param fieldType The type of the field to get
	 * @return An entity field value for the given field
	 */
	default <F> ObservableEntityField<E, F> getField(ObservableEntityFieldType<? super E, F> fieldType) {
		ObservableEntityFieldType<E, F> myFieldType;
		if (fieldType.getEntityType() == getType()) {
			if (getType().getFields().get(fieldType.getFieldIndex()) != fieldType)
				throw new IllegalArgumentException("Unrecognized field type: " + fieldType);
			myFieldType = (ObservableEntityFieldType<E, F>) fieldType;
		} else {
			myFieldType = (ObservableEntityFieldType<E, F>) getType().getFields().getIfPresent(fieldType.getName());
			if (myFieldType == null)
				throw new IllegalArgumentException("No such field " + getType().getName() + "." + fieldType.getName());
		}
		return new ObservableEntityField<>(this, myFieldType);
	}

	@Override
	default int compareTo(ObservableEntity<?> o) {
		return getId().compareTo(o.getId());
	}

	/** @return Whether this entity is still present in the entity set */
	boolean isPresent();

	/** @return A message detailing why this entity cannot be {@link #delete(Object) deleted} from the entity set, or null if it can */
	String canDelete();
	/**
	 * Deletes this entity from the entity set
	 *
	 * @param cause The cause of the change, if any
	 * @throws UnsupportedOperationException if this entity cannot be deleted for any reason
	 */
	void delete(Object cause) throws UnsupportedOperationException;
	/**
	 * @return An observable that will fire once when this entity is deleted from the entity set, either by the {@link #delete(Object)}
	 *         method, as a result of a {@link EntityDeletion delete operation}, or externally
	 */
	default Observable<ObservableEntity<?>> onDelete() {
		// Use ID field if we can, since those only fire completed events
		if (getType().getIdentityFields().keySize() > 0)
			return fieldChanges(getType().getIdentityFields().get(0).getFieldIndex()).completed().map(__ -> this);
		else if (getType().getFields().keySize() > 0)
			return fieldChanges(0).completed().map(__ -> this);
		else
			throw new UnsupportedOperationException("This default method must be overridden if the type has no fields");
	}
}
