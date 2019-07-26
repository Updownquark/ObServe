package org.observe.config;

import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import org.observe.util.TypeTokens;
import org.qommons.collect.QuickSet.QuickMap;

public abstract class SimpleValueCreator<E> implements ValueCreator<E> {
	private final ConfiguredValueType<E> theType;
	private final QuickMap<String, Object> theFieldValues;

	public SimpleValueCreator(ConfiguredValueType<E> type) {
		theType = type;
		theFieldValues = type.getFields().keySet().createMap();
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
				throw new IllegalArgumentException("Value of type " + value.getClass().getName() + " is not allowed for field " + field);
		}
		theFieldValues.put(field.getIndex(), value);
		return this;
	}

	@Override
	public <F> ValueCreator<E> with(Function<? super E, F> field, F value) throws IllegalArgumentException {
		int fieldIndex = theType.getFieldIndex(field);
		return with((ConfiguredValueField<? super E, F>) theType.getFields().get(fieldIndex), value);
	}

	protected QuickMap<String, Object> getFieldValues() {
		return theFieldValues;
	}
}
