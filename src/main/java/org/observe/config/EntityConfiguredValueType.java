package org.observe.config;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.EntityReflectionMessage;
import org.observe.util.EntityReflector.ReflectedField;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

/**
 * A thin wrapper around {@link EntityReflector} that helps with a few {@link ObservableConfig}-specific things
 *
 * @param <E> The type of the entity
 */
public class EntityConfiguredValueType<E> implements ConfiguredValueType<E> {
	private final EntityReflector<E> theReflector;
	private final List<EntityConfiguredValueType<? super E>> theSupers;
	private final Map<TypeToken<?>, EntityReflector<?>> theSubTypes;
	private final QuickMap<String, ? extends EntityConfiguredValueField<E, ?>> theFields;

	/**
	 * @param reflector The reflector to wrap
	 * @param supers The list of super-type entities that this entity type extends
	 * @param subTypes A map of entity name to entity sub types of the entities that extend this type
	 */
	public EntityConfiguredValueType(EntityReflector<E> reflector, List<EntityConfiguredValueType<? super E>> supers,
		Map<TypeToken<?>, EntityReflector<?>> subTypes) {
		theReflector = reflector;
		theSupers = supers;
		theSubTypes = subTypes;
		QuickMap<String, EntityConfiguredValueField<E, ?>> fields = theReflector.getFields().keySet().createMap();
		for (int i = 0; i < fields.keySet().size(); i++)
			fields.put(i, new EntityConfiguredValueField<>(this, theReflector.getFields().get(i)));
		theFields = fields.unmodifiable();
	}

	@Override
	public TypeToken<E> getType() {
		return theReflector.getType();
	}

	@Override
	public List<? extends EntityConfiguredValueType<? super E>> getSupers() {
		return theSupers;
	}

	@Override
	public QuickMap<String, ? extends EntityConfiguredValueField<E, ?>> getFields() {
		return theFields;
	}

	/** @return The set of field IDs that are marked as identifying for this type */
	public Set<Integer> getIdFields() {
		return theReflector.getIdFields();
	}

	@Override
	public <F> ConfiguredValueField<E, F> getField(Function<? super E, F> fieldGetter) {
		ReflectedField<E, F> field = theReflector.getField(fieldGetter);
		return (ConfiguredValueField<E, F>) theFields.get(field.getFieldIndex());
	}

	@Override
	public boolean allowsCustomFields() {
		return false;
	}

	/**
	 * @param value The value to test
	 * @return Whether the value is an instance of this entity type
	 */
	public boolean isInstance(E value) {
		return theReflector.isInstance(value);
	}

	/**
	 * @param subType The type token of the sub type to get
	 * @return The entity type extending this of the given type
	 */
	public <E2 extends E> EntityConfiguredValueType<E2> subType(TypeToken<E2> subType) {
		if (subType.equals(theReflector.getType()))
			return (EntityConfiguredValueType<E2>) this;
		EntityReflector<E2> reflector = (EntityReflector<E2>) theSubTypes.get(subType);
		if (reflector == null)
			reflector = EntityReflector.build(subType, false).withSupers(theSubTypes).build();
		return new EntityConfiguredValueType<>(reflector, Collections.singletonList(this), theSubTypes);
	}

	/**
	 * @param backing The entity backing for the new entity
	 * @return The new instance
	 */
	public E create(EntityReflector.EntityInstanceBacking backing) {
		assertUsableDirectly();
		return theReflector.newInstance(backing);
	}

	/**
	 * @param entity The entity to associate a value into
	 * @param key The key to associate the value with
	 * @param associated The value to associate with the given key in the entity
	 * @return The entity
	 */
	public E associate(E entity, Object key, Object associated) {
		return theReflector.associate(entity, key, associated);
	}

	/**
	 * Retrieves data {@link #associate(Object, Object, Object) associated} with an entity
	 *
	 * @param entity The entity to get data from
	 * @param key The key the data was associated with
	 * @return The associated data
	 */
	public Object getAssociated(E entity, Object key) {
		return theReflector.getAssociated(entity, key);
	}

	private void assertUsableDirectly() {
		if (!theReflector.getDirectUseErrors().isEmpty()) {
			StringBuilder str = new StringBuilder();
			for (EntityReflectionMessage err : theReflector.getDirectUseErrors()) {
				switch (err.getLevel()) {
				case ERROR:
				case FATAL:
					if (str.length() > 0)
						str.append('\n');
					str.append(err);
					break;
				default:
					break;
				}
			}
			if (str.length() > 0)
				throw new IllegalArgumentException(theReflector.getType() + " cannot be used directly:\n" + str.toString());
		}
	}

	@Override
	public String toString() {
		return theReflector.getType().toString();
	}

	static class EntityConfiguredValueField<E, F> implements ConfiguredValueField<E, F> {
		private final EntityConfiguredValueType<E> theValueType;
		private final EntityReflector.ReflectedField<E, F> theField;
		private final List<EntityConfiguredValueField<? super E, ? super F>> theOverrides;

		EntityConfiguredValueField(EntityConfiguredValueType<E> valueType, EntityReflector.ReflectedField<E, F> field) {
			theValueType = valueType;
			theField = field;
			ArrayList<EntityConfiguredValueField<? super E, ? super F>> overrides = new ArrayList<>();
			for (EntityConfiguredValueType<? super E> superType : theValueType.getSupers()) {
				ConfiguredValueField<? super E, ?> override = superType.getFields().getIfPresent(theField.getName());
				if (override != null)
					overrides.add((EntityConfiguredValueField<? super E, ? super F>) override);
			}
			overrides.trimToSize();
			theOverrides = Collections.unmodifiableList(overrides);
		}

		@Override
		public EntityConfiguredValueType<E> getOwnerType() {
			return theValueType;
		}

		@Override
		public List<EntityConfiguredValueField<? super E, ? super F>> getOverrides() {
			return theOverrides;
		}

		@Override
		public String getName() {
			return theField.getName();
		}

		@Override
		public TypeToken<F> getFieldType() {
			return theField.getType();
		}

		@Override
		public int getIndex() {
			return theField.getFieldIndex();
		}

		@Override
		public F get(E entity) {
			return theField.get(entity);
		}

		@Override
		public void set(E entity, F fieldValue) throws UnsupportedOperationException {
			theField.set(entity, fieldValue);
		}

		public boolean isParentReference() {
			if (theField.getGetter().getMethod().getAnnotation(ParentReference.class) != null)
				return true;
			for (EntityConfiguredValueField<? super E, ? super F> override : theOverrides)
				if (override.isParentReference())
					return true;
			return false;
		}

		public boolean isTransient() {
			if (theField.getGetter().getMethod().getAnnotation(Transient.class) != null)
				return true;
			for (EntityConfiguredValueField<? super E, ? super F> override : theOverrides)
				if (override.isTransient())
					return true;
			return false;
		}

		@Override
		public String toString() {
			return theField.toString();
		}
	}
}
