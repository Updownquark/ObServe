package org.observe.config;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

public interface ValueCreator<E, E2 extends E> {
	ConfiguredValueType<E2> getType();

	Set<Integer> getRequiredFields();

	ValueCreator<E, E2> after(ElementId after);

	ValueCreator<E, E2> before(ElementId before);
	ValueCreator<E, E2> towardBeginning(boolean towardBeginning);
	default ValueCreator<E, E2> between(ElementId after, ElementId before, boolean towardBeginning) {
		return after(after).before(before).towardBeginning(towardBeginning);
	}

	ValueCreator<E, E2> with(String fieldName, Object value) throws IllegalArgumentException;

	<F> ValueCreator<E, E2> with(ConfiguredValueField<? super E2, F> field, F value) throws IllegalArgumentException;

	<F> ValueCreator<E, E2> with(Function<? super E2, F> fieldGetter, F value) throws IllegalArgumentException;

	default CollectionElement<E> create() {
		return create(null);
	}

	CollectionElement<E> create(Consumer<? super E2> preAddAction);
}
