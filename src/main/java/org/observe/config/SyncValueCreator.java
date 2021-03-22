package org.observe.config;

import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

/**
 * A {@link ConfigurableValueCreator} without asynchronous capabilities
 *
 * @param <E> The type of the value set
 * @param <E2> The sub-type of the value to create
 */
public interface SyncValueCreator<E, E2 extends E> extends ConfigurableValueCreator<E, E2> {
	@Override
	SyncValueCreator<E, E2> after(ElementId after);

	@Override
	SyncValueCreator<E, E2> before(ElementId before);

	@Override
	SyncValueCreator<E, E2> towardBeginning(boolean towardBeginning);

	@Override
	default SyncValueCreator<E, E2> between(ElementId after, ElementId before, boolean towardBeginning) {
		ConfigurableValueCreator.super.between(after, before, towardBeginning);
		return this;
	}

	@Override
	default SyncValueCreator<E, E2> with(String fieldName, Object value) throws IllegalArgumentException {
		ConfigurableValueCreator.super.with(fieldName, value);
		return this;
	}

	@Override
	default <F> SyncValueCreator<E, E2> with(Function<? super E2, F> fieldGetter, F value) throws IllegalArgumentException {
		ConfigurableValueCreator.super.with(fieldGetter, value);
		return this;
	}

	@Override
	<F> SyncValueCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException;

	@Override
	default SyncValueCreator<E, E2> copy(E template) {
		ConfigurableValueCreator.super.copy(template);
		return this;
	}

	@Override
	default CollectionElement<E> create() {
		return create(null);
	}

	@Override
	CollectionElement<E> create(Consumer<? super E2> preAddAction);

	@Override
	default ObservableCreationResult<E2> createAsync(Consumer<? super E2> preAddAction) {
		return ObservableCreationResult.simpleResult(getType(), (E2) create(preAddAction).get());
	}
}
