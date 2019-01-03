package org.observe.entity;

import java.util.function.Function;

public interface EntityCondition<E> {
	EntitySelection<E> getSelection();

	<F> EntityCondition<E> compare(EntityValueAccess<? super E, F> field, F value, int ltEqGt, boolean withEqual);

	<F> EntityCondition<E> compareVariable(EntityValueAccess<? super E, F> field, String variableName, int ltEqGt,
		boolean withEqual);

	default <F> EntityCondition<E> equal(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 0, true);
	}

	default <F> EntityCondition<E> notEqual(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 0, false);
	}

	default <F> EntityCondition<E> lessThan(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, -1, false);
	}

	default <F> EntityCondition<E> lessThanOrEqual(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, -1, true);
	}

	default <F> EntityCondition<E> greaterThan(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 1, false);
	}

	default <F> EntityCondition<E> greaterThanOrEqual(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 1, true);
	}

	default <F> EntityCondition<E> equalVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 0, true);
	}

	default <F> EntityCondition<E> notEqualVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 0, false);
	}

	default <F> EntityCondition<E> lessThanVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, -1, false);
	}

	default <F> EntityCondition<E> lessThanOrEqualVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, -1, true);
	}

	default <F> EntityCondition<E> greaterThanVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 1, false);
	}

	default <F> EntityCondition<E> greaterThanOrEqualVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 1, true);
	}

	default <F> EntityValueAccess<E, F> valueFor(Function<? super E, F> fieldGetter) {
		return getSelection().getEntityType().fieldAccess(fieldGetter);
	}
	default <F> EntityValueAccess<E, F> valueFor(ObservableEntityFieldType<? super E, F> field) {
		return getSelection().getEntityType().fieldValue(field);
	}

	EntityCondition<E> or(Function<EntityCondition<E>, EntityCondition<E>> condition);
	EntityCondition<E> and(Function<EntityCondition<E>, EntityCondition<E>> condition);
}
