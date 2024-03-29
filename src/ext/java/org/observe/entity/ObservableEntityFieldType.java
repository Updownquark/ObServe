package org.observe.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.config.ConfiguredValueField;
import org.observe.config.ObservableValueSet;
import org.qommons.collect.MultiMap;

import com.google.common.reflect.TypeToken;

/**
 * Represents a field in an entity type
 *
 * @param <E> The type of the entity
 * @param <F> The type of the field
 */
public interface ObservableEntityFieldType<E, F> extends ConfiguredValueField<E, F>, EntityValueAccess<E, F> {
	/** @return The entity type that this field is a member of */
	@Override
	ObservableEntityType<E> getOwnerType();

	@Override
	default ObservableEntityType<E> getSourceEntity() {
		return getOwnerType();
	}

	/**
	 * @return The index of this field in the entity type's {@link ObservableEntityType#getIdentityFields() identity field map}, or -1 if
	 *         this is not an identity field
	 */
	int getIdIndex();

	/** @return The entity type for keys of this field (if it is a {@link Map}- or {@link MultiMap}-typed field */
	ObservableEntityType<?> getKeyTarget();

	/**
	 * @return The entity type for values of this field (if it is a {@link Collection}-, {@link ObservableValueSet}-, {@link Map}-, or
	 *         {@link MultiMap}-typed field
	 */
	ObservableEntityType<?> getValueTarget();

	@Override
	List<? extends ObservableEntityFieldType<? super E, ? super F>> getOverrides();
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
	default <T> EntityChainAccess<E, T> dot(Function<? super F, T> attr) {
		ObservableEntityType<F> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		ObservableEntityFieldType<F, T> lastField = target.getField(attr);
		return new EntityChainAccess<>(this, lastField);
	}

	@Override
	default <T> EntityChainAccess<E, T> dot(ObservableEntityFieldType<? super F, T> field) {
		ObservableEntityType<F> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		else if (!field.getOwnerType().isAssignableFrom(target))
			throw new IllegalArgumentException(field + " cannot be applied to " + target);
		else if (!field.getOwnerType().equals(target))
			field = (ObservableEntityFieldType<F, T>) target.getFields().get(field.getName());
		return new EntityChainAccess<>(this, (ObservableEntityFieldType<F, T>) field);
	}

	@Override
	default TypeToken<F> getValueType() {
		return getFieldType();
	}

	@Override
	default F get(E entity) {
		return getValue(getOwnerType().observableEntity(entity));
	}

	@Override
	default void set(E entity, F fieldValue) throws UnsupportedOperationException {
		setValue(getOwnerType().observableEntity(entity), fieldValue);
	}

	@Override
	default F getValue(ObservableEntity<? extends E> entity) {
		if (getOwnerType() == entity.getType())
			return (F) entity.get(getIndex());
		else if (getOwnerType().isAssignableFrom(entity.getType()))
			return (F) entity.getField(getName()).get();
		else
			throw new IllegalArgumentException(this + " cannot be applied to an instance of " + entity.getType());
	}

	@Override
	default void setValue(ObservableEntity<? extends E> entity, F value) {
		if (getOwnerType() == entity.getType())
			entity.set(getIndex(), value, null);
		else if (getOwnerType().isAssignableFrom(entity.getType()))
			((ObservableEntityField<E, F>) entity.getField(getName())).set(value, null);
		else
			throw new IllegalArgumentException(this + " cannot be applied to an instance of " + entity.getType());
	}

	@Override
	default int compareTo(EntityValueAccess<E, ?> o) {
		if (!(o instanceof ObservableEntityFieldType))
			return -1;
		if (getOwnerType() != ((ObservableEntityFieldType<?, ?>) o).getOwnerType())
			throw new IllegalArgumentException("Cannot compare fields of different entity types");
		return Integer.compare(getIndex(), ((ObservableEntityFieldType<E, ?>) o).getIndex());
	}

	@Override
	default boolean isOverride(EntityValueAccess<? extends E, ?> field) {
		if (!(field instanceof ObservableEntityFieldType))
			return false;
		return ObservableEntityUtil.isOverride(this, (ObservableEntityFieldType<?, ?>) field);
	}
}
