package org.observe.config;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.TriFunction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

public class EntityConfiguredValueType<E> implements ConfiguredValueType<E> {
	private final EntityReflector<E> theReflector;
	private final QuickMap<String, EntityConfiguredValueField<? super E, ?>> theFields;

	public EntityConfiguredValueType(TypeToken<E> type) {
		this(type, null);
	}

	public EntityConfiguredValueType(TypeToken<E> type, Map<Method, Function<Object[], ?>> customMethods) {
		theReflector = new EntityReflector<>(type, null, null, customMethods);
		QuickMap<String, EntityConfiguredValueField<? super E, ?>> fields = theReflector.getFields().keySet().createMap();
		for (int i = 0; i < fields.keySet().size(); i++)
			fields.put(i, new EntityConfiguredValueField<>(this, i, fields.keySet().get(i),
				theReflector.getFields().get(i).getGetter().getReturnType()));
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

	@Override
	public int getFieldIndex(Function<? super E, ?> fieldGetter) {
		return theReflector.getFieldIndex(fieldGetter);
	}

	@Override
	public boolean allowsCustomFields() {
		return false;
	}

	public E create(IntFunction<Object> fieldGetter, BiConsumer<Integer, Object> fieldSetter) {
		return theReflector.newInstance(fieldGetter, fieldSetter);
	}

	@Override
	public String toString() {
		return theReflector.getType().toString();
	}

	static class EntityConfiguredValueField<E, F> implements ConfiguredValueField<E, F> {
		private final EntityConfiguredValueType<E> theValueType;
		private final int theIndex;
		private final String theName;
		private final TypeToken<F> theFieldType;

		EntityConfiguredValueField(EntityConfiguredValueType<E> valueType, int index, String name, TypeToken<F> fieldType) {
			theValueType = valueType;
			theIndex = index;
			theName = name;
			theFieldType = fieldType;
		}

		@Override
		public ConfiguredValueType<E> getValueType() {
			return theValueType;
		}

		@Override
		public String getName() {
			return theName;
		}

		@Override
		public TypeToken<F> getFieldType() {
			return theFieldType;
		}

		@Override
		public int getIndex() {
			return theIndex;
		}

		@Override
		public String toString() {
			return theValueType + "." + theName + " (" + theFieldType + ")";
		}
	}

	static class EntityValueCreator<E, I> implements ValueCreator<E> {
		private final EntityConfiguredValueType<E> theType;
		private final QuickMap<String, Object> theFieldValues;

		private final Function<QuickMap<String, Object>, I> theImplCreator;
		private final BiFunction<? super I, Integer, Object> theFieldGetter;
		private final TriFunction<? super I, Integer, Object, ?> theFieldSetter;
		private final BiFunction<? super I, ? super E, ? extends CollectionElement<E>> theElementProducer;

		public EntityValueCreator(EntityConfiguredValueType<E> type, Function<QuickMap<String, Object>, I> implCreator,
			BiFunction<? super I, Integer, Object> fieldGetter, TriFunction<? super I, Integer, Object, ?> fieldSetter,
			BiFunction<? super I, ? super E, ? extends CollectionElement<E>> elementProducer) {
			theType = type;
			theFieldValues = type.getFields().keySet().createMap();
			theImplCreator = implCreator;
			theFieldGetter = fieldGetter;
			theFieldSetter = fieldSetter;
			theElementProducer = elementProducer;
		}

		@Override
		public ConfiguredValueType<E> getType() {
			return theType;
		}

		@Override
		public Set<Integer> getRequiredFields() {
			return Collections.emptySet();
		}

		@Override
		public ValueCreator<E> with(String fieldName, Object value) throws IllegalArgumentException {
			ConfiguredValueField<? super E, ?> field = theType.getFields().get(fieldName);
			return with((ConfiguredValueField<? super E, Object>) field, value);
		}

		@Override
		public <F> ValueCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException {
			if (value == null) {
				if (field.getFieldType().isPrimitive())
					throw new IllegalArgumentException("Null value is not allowed for primitive field " + field);
			} else {
				if (!TypeTokens.get().isInstance(field.getFieldType(), value))
					throw new IllegalArgumentException(
						"Value of type " + value.getClass().getName() + " is not allowed for field " + field);
			}
			theFieldValues.put(field.getIndex(), value);
			return this;
		}

		@Override
		public <F> ValueCreator<E> with(Function<? super E, F> field, F value) throws IllegalArgumentException {
			int fieldIndex = theType.theReflector.getFieldIndex(field);
			return with((ConfiguredValueField<? super E, F>) theType.getFields().get(fieldIndex), value);
		}

		@Override
		public CollectionElement<E> create() {
			I newImpl = theImplCreator.apply(theFieldValues);
			E newValue = theType.theReflector.newInstance(//
				index -> theFieldGetter.apply(newImpl, index), //
				(index, fieldValue) -> theFieldSetter.apply(newImpl, index, fieldValue));
			return theElementProducer.apply(newImpl, newValue);
		}
	}
}