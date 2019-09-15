package org.observe.config;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.ReflectedField;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

public class EntityConfiguredValueType<E> implements ConfiguredValueType<E> {
	private final EntityReflector<E> theReflector;
	private final Map<TypeToken<?>, EntityReflector<?>> theSubTypes;
	private final QuickMap<String, EntityConfiguredValueField<? super E, ?>> theFields;

	public EntityConfiguredValueType(EntityReflector<E> reflector, Map<TypeToken<?>, EntityReflector<?>> subTypes) {
		theReflector = reflector;
		theSubTypes = subTypes;
		QuickMap<String, EntityConfiguredValueField<? super E, ?>> fields = theReflector.getFields().keySet().createMap();
		for (int i = 0; i < fields.keySet().size(); i++)
			fields.put(i, new EntityConfiguredValueField<>(this, theReflector.getFields().get(i)));
		theFields = fields.unmodifiable();
	}

	@Override
	public TypeToken<E> getType() {
		return theReflector.getType();
	}

	@Override
	public QuickMap<String, ConfiguredValueField<? super E, ?>> getFields() {
		return (QuickMap<String, ConfiguredValueField<? super E, ?>>) (QuickMap<?, ?>) theFields;
	}

	/** @return The set of field IDs that are marked as identifying for this type */
	public Set<Integer> getIdFields() {
		return theReflector.getIdFields();
	}

	@Override
	public <F> ConfiguredValueField<? super E, F> getField(Function<? super E, F> fieldGetter) {
		ReflectedField<? super E, F> field = theReflector.getField(fieldGetter);
		return (ConfiguredValueField<? super E, F>) theFields.get(field.getFieldIndex());
	}

	@Override
	public boolean allowsCustomFields() {
		return false;
	}

	public <E2 extends E> EntityConfiguredValueType<E2> subType(TypeToken<E2> subType) {
		if (subType.equals(theReflector.getType()))
			return (EntityConfiguredValueType<E2>) this;
		EntityReflector<E2> reflector = (EntityReflector<E2>) theSubTypes.get(subType);
		if (reflector == null)
			reflector = EntityReflector.build(subType).withSupers(theSubTypes).build();
		return new EntityConfiguredValueType<>(reflector, theSubTypes);
	}

	public E create(IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
		return theReflector.newInstance(fieldGetter, fieldSetter);
	}

	public E associate(E entity, Object key, Object associated) {
		return theReflector.associate(entity, key, associated);
	}

	public Object getAssociated(E entity, Object key) {
		return theReflector.getAssociated(entity, key);
	}

	@Override
	public String toString() {
		return theReflector.getType().toString();
	}

	static class EntityConfiguredValueField<E, F> implements ConfiguredValueField<E, F> {
		private final EntityConfiguredValueType<E> theValueType;
		private final EntityReflector.ReflectedField<? super E, F> theField;

		EntityConfiguredValueField(EntityConfiguredValueType<E> valueType, EntityReflector.ReflectedField<? super E, F> field) {
			theValueType = valueType;
			theField = field;
		}

		@Override
		public ConfiguredValueType<E> getValueType() {
			return theValueType;
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

		@Override
		public String toString() {
			return theField.toString();
		}
	}
}
