package org.observe.entity;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * Represents the unique identity of an entity in an {@link ObservableEntityDataSet entity set}
 *
 * @param <E> The type of the entity represented
 */
public class EntityIdentity<E> implements Comparable<EntityIdentity<?>> {
	private final ObservableEntityType<E> theEntityType;
	final QuickMap<String, Object> theFields;

	private EntityIdentity(ObservableEntityType<E> entityType, QuickMap<String, Object> fieldValues) {
		theEntityType = entityType;
		theFields = fieldValues;
	}

	/** @return The entity type that this identity represents an entity of */
	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	/** @return The identity fields of this identity */
	public QuickMap<String, Object> getFields() {
		return theFields;
	}

	public <F> F getValue(ObservableEntityFieldType<? super E, F> fieldType) {
	}

	@Override
	public int compareTo(EntityIdentity<?> other) {
		if (this == other)
			return 0;
		if (theEntityType.equals(other.theEntityType)) {
			for (int f = 0; f < theFields.keySize(); f++) {
				int comp = ((ObservableEntityFieldType<E, Object>) theEntityType.getIdentityFields().get(f)).compare(theFields.get(f),
					other.theFields.get(f));
				if (comp != 0)
					return comp;
			}
			return 0;
		} else if (theEntityType.isAssignableFrom(other.theEntityType)) {
			for (int f = 0; f < theFields.keySize(); f++) {
				ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) theEntityType.getIdentityFields()
					.get(f);
				int comp = field.compare(theFields.get(f), ((EntityIdentity<? extends E>) other).getValue(field));
				if (comp != 0)
					return comp;
			}
			return 0;
		} else if (other.theEntityType.isAssignableFrom(theEntityType))
			return other.compareTo(this);
		else
			throw new IllegalArgumentException(theEntityType + " and " + other.theEntityType + " are not related");
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (int i = 0; i < theFields.keySet().size(); i++) {
			if (i != 0)
				hash = hash * 31;
			hash += Objects.hashCode(theFields.get(i));
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof EntityIdentity))
			return false;
		EntityIdentity<?> other = (EntityIdentity<?>) obj;
		if (!theEntityType.equals(theEntityType))
			return false;
		for (int i = 0; i < theFields.keySet().size(); i++)
			if (!Objects.equals(theFields.get(i), other.theFields.get(i)))
				return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(theEntityType).append('(');
		for (int i = 0; i < theFields.keySet().size(); i++) {
			if (i > 0)
				str.append(',');
			str.append(theEntityType.getFields().get(i).getName()).append('=').append(theFields.get(i));
		}
		str.append(')');
		return str.toString();
	}

	/**
	 * @param entityType The entity type to build an identity for
	 * @return A builder to create an {@link EntityIdentity} for the type
	 */
	public static <E> Builder<E> build(ObservableEntityType<E> entityType) {
		return new Builder<>(entityType);
	}

	/**
	 * Builds an {@link EntityIdentity}
	 *
	 * @param <E> The entity type to build an ID for
	 */
	public static class Builder<E> {
		private final ObservableEntityType<E> theEntityType;
		private final QuickMap<String, Object> theFields;

		Builder(ObservableEntityType<E> entityType) {
			theEntityType = entityType;
			theFields = entityType.getIdentityFields().keySet().createMap();
			theFields.fill(EntityUpdate.NOT_SET);
		}

		/** @return The entity type that this builder can create identities for */
		public ObservableEntityType<E> getEntityType() {
			return theEntityType;
		}

		/** @return The identity field values set in the builder. Unset fields will have {@link EntityUpdate#NOT_SET} for a value. */
		public QuickMap<String, Object> getFields() {
			return theFields.unmodifiable();
		}

		/**
		 * @param idIndex The {@link ObservableEntityFieldType#getIdIndex() identity index} of the field to set
		 * @param value The value for the field
		 * @return This builder
		 * @throws IllegalArgumentException If the index is not an identity index in this type, or if the value is not acceptable for the
		 *         field
		 */
		public Builder<E> with(int idIndex, Object value) throws IllegalArgumentException {
			return with((ObservableEntityFieldType<E, Object>) theEntityType.getIdentityFields().get(idIndex), value);
		}

		/**
		 * @param fieldName The name of the field to set
		 * @param value The value for the field
		 * @return This builder
		 * @throws IllegalArgumentException If the field is not found in this entity type, is not an identity field of this type, or if the
		 *         value is not acceptable for the field
		 */
		public Builder<E> with(String fieldName, Object value) throws IllegalArgumentException {
			int index = theEntityType.getFields().keyIndexTolerant(fieldName);
			if (index < 0)
				throw new IllegalArgumentException("No such field " + theEntityType.getName() + "." + fieldName);
			return with((ObservableEntityFieldType<E, Object>) theEntityType.getFields().get(index), value);
		}

		/**
		 * @param fieldGetter The getter for a field in this entity's java type
		 * @param value The value for the field
		 * @return This builder
		 * @throws IllegalArgumentException If the getter is not a field getter in this type, the field is not an identity field of this
		 *         type, or if the value is not acceptable for the field
		 */
		public <F> Builder<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException {
			return with(theEntityType.getField(fieldGetter), value);
		}

		/**
		 * @param field The identity field of this entity type to set the value for
		 * @param value The value for the field
		 * @return This builder
		 * @throws IllegalArgumentException If the field is not an identity field of this type, or if the value is not acceptable for the
		 *         field
		 */
		public <F> Builder<E> with(ObservableEntityFieldType<E, F> field, F value) throws IllegalArgumentException {
			if (field.getIdIndex() < 0)
				throw new IllegalArgumentException(field + " is not an ID field");
			else if (theEntityType.getIdentityFields().get(field.getIdIndex()) != field)
				throw new IllegalArgumentException(field + " is not a field of " + theEntityType);
			else if (value != null && !TypeTokens.getRawType(field.getFieldType()).isInstance(value))
				throw new IllegalArgumentException("Value " + value + ", type " + value.getClass().getName() + " is not valid for field "
					+ field + " (" + field.getFieldType() + ")");
			String msg = field.canAccept(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			theFields.put(field.getIdIndex(), value);
			return this;
		}

		/**
		 * @return The new identity
		 * @throws IllegalStateException If any identity fields have not been set
		 */
		public EntityIdentity<E> build() throws IllegalStateException {
			QuickMap<String, Object> fields = theFields.copy();
			Set<String> unset = null;
			for (int f = 0; f < fields.keySize(); f++) {
				if (fields.get(f) == EntityUpdate.NOT_SET) {
					if (unset == null)
						unset = new LinkedHashSet<>();
					unset.add(fields.keySet().get(f));
				}
			}
			if (unset != null)
				throw new IllegalStateException(
					"Fields not specified: " + StringUtils.conversational(",", "and").print(unset, StringBuilder::append));
			return new EntityIdentity<>(theEntityType, fields.unmodifiable());
		}
	}

}
