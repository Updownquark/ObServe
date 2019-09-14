package org.observe.config;

import java.util.Set;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.config.ObservableConfigFormat.EntityConfigCreator;

public abstract class SimpleValueCreator<E> implements ValueCreator<E> {
	private final ObservableConfigFormat.EntityConfigCreator<E> theCreator;

	public SimpleValueCreator(EntityConfigCreator<E> creator) {
		theCreator = creator;
	}

	@Override
	public ConfiguredValueType<E> getType() {
		return theCreator.getEntityType();
	}

	@Override
	public Set<Integer> getRequiredFields() {
		return theCreator.getRequiredFields();
	}

	@Override
	public ValueCreator<E> with(String fieldName, Object value) throws IllegalArgumentException {
		theCreator.with(fieldName, value);
		return this;
	}

	@Override
	public <F> ValueCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException {
		theCreator.with(field, value);
		return this;
	}

	@Override
	public <F> ValueCreator<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException {
		theCreator.with(fieldGetter, value);
		return this;
	}

	protected E createValue(ObservableConfig config, Observable<?> until) {
		return theCreator.create(config, until);
	}
}
