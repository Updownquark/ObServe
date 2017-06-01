package org.observe.db.relational;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.collect.ObservableCollection;

public interface RelationalQueryBuilder<E extends ObservableEntity<? extends E>> {
	RelationalQueryBuilder<E> where(Consumer<? super RelationalCondition<E>> where);

	RelationalQueryBuilder<E> joinNone();
	RelationalQueryBuilder<E> joinAll();
	RelationalQueryBuilder<E> join(Function<? super E, ? extends ObservableEntity<?>>... fields);

	// Group By
	// Distinct
	// <G> ObservableMap<G, Integer> groupBy(

	ObservableCollection<E> get();

	ObservableCollection<E> orderBy(Consumer<? super RelationalOrder<E>> order);

	int count();
}
