package org.observe.db.relational;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;

public interface RelationalQueryBuilder<E extends ObservableEntity<? extends E>> {
	ObservableEntityType<E> getEntity();

	RelationalQueryBuilder<E> where(Consumer<? super RelationalCondition<E>> where);

	RelationalQueryBuilder<E> joinNone();
	RelationalQueryBuilder<E> joinAll();
	RelationalQueryBuilder<E> join(Function<? super E, ? extends ObservableEntity<?>>... fields);

	// Group By
	// Distinct
	// <G> ObservableMap<G, Integer> groupBy(

	ObservableCollection<E> query();

	ObservableCollection<E> orderBy(Consumer<? super RelationalOrder<E>> order);

	ObservableValue<Long> count();
}
