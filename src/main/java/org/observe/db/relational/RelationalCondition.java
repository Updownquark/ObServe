package org.observe.db.relational;

import java.util.function.Consumer;
import java.util.function.Function;

public interface RelationalCondition<E> {
	<T> RelationalCondition<E> and(Function<? super E, T> field, Consumer<? super RelationalCondition<T>> c);
}

