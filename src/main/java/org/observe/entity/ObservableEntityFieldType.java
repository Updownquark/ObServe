package org.observe.entity;

import java.util.List;
import java.util.function.Function;

import org.qommons.Named;

import com.google.common.reflect.TypeToken;

/**
 * Represents a field in an entity type
 *
 * @param <E> The type of the entity
 * @param <F> The type of the field
 */
public interface ObservableEntityFieldType<E, F> extends EntityValueAccess<E, F>, Named {
	/** @return The entity type that this field is a member of */
	ObservableEntityType<E> getEntityType();
	@Override
	default ObservableEntityType<E> getSourceEntity() {
		return getEntityType();
	}
	/** @return The types of values that belong in the field */
	TypeToken<F> getFieldType();
	/** return The name of the field */
	@Override
	String getName();
	/** @return The index of this field in the entity type's {@link ObservableEntityType#getFields() field map} */
	int getFieldIndex();
	/**
	 * @return The index of this field in the entity type's {@link ObservableEntityType#getIdentityFields() identity field map}, or -1 if
	 *         this is not an identity field
	 */
	int getIdIndex();

	/** @return The fields in the entity's super types that this field overrides */
	List<? extends ObservableEntityFieldType<? super E, F>> getOverrides();
	/** @return Any {@link EntityConstraint}s applying specifically to this field */
	List<FieldConstraint<E, F>> getConstraints();

	@Override
	default String canAccept(F value) {
		StringBuilder str = null;
		for (FieldConstraint<E, F> c : getConstraints()) {
			String msg = c.canAccept(value);
			if (msg != null) {
				if (str == null)
					str = new StringBuilder();
				else
					str.append("; ");
				str.append(msg);
			}
		}
		return str == null ? null : str.toString();
	}

	@Override
	default <T> EntityValueAccess<E, T> dot(Function<? super F, T> attr) {
		ObservableEntityType<F> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		ObservableEntityFieldType<F, T> lastField = target.getField(attr);
		return new EntityChainAccess<>(this, lastField);
	}

	@Override
	default TypeToken<F> getValueType() {
		return getFieldType();
	}

	@Override
	default F getValue(E entity) {
		return (F) getEntityType().observableEntity(entity).get(getFieldIndex());
	}

	@Override
	default F getValue(ObservableEntity<? extends E> entity) {
		if (getEntityType() == entity.getType())
			return (F) entity.get(getFieldIndex());
		else if (getEntityType().isAssignableFrom(entity.getType()))
			return (F) entity.getField(getName()).get();
		else
			throw new IllegalArgumentException(this + " cannot be applied to an instance of " + entity.getType());
	}

	@Override
	default int compareTo(EntityValueAccess<E, ?> o) {
		if (!(o instanceof ObservableEntityFieldType))
			return -1;
		if (getEntityType() != ((ObservableEntityFieldType<?, ?>) o).getEntityType())
			throw new IllegalArgumentException("Cannot compare fields of different entity types");
		return Integer.compare(getFieldIndex(), ((ObservableEntityFieldType<E, ?>) o).getFieldIndex());
	}

	@Override
	default boolean isOverride(EntityValueAccess<? extends E, ?> field) {
		if (!(field instanceof ObservableEntityFieldType))
			return false;
		return ObservableEntityUtil.isOverride(this, (ObservableEntityFieldType<?, ?>) field);
	}
}
