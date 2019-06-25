package org.observe.entity.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntityValueAccess;

public abstract class EntityConditionImpl<E> implements EntityCondition<E> {
	private final EntitySelection<E> theSelection;

	public EntityConditionImpl(EntitySelection<E> selection) {
		theSelection = selection;
	}

	@Override
	public EntitySelection<E> getSelection() {
		return theSelection;
	}

	@Override
	public <F> EntityCondition<E> compare(EntityValueAccess<? super E, F> field, F value, int ltEqGt, boolean withEqual) {
		DefiniteCondition<E, F> condition = new DefiniteCondition<>(theSelection, field, value, ltEqGt, withEqual);
		if (this instanceof None)
			return condition;
		else
			return and(none -> condition);
	}

	@Override
	public <F> EntityCondition<E> compareVariable(EntityValueAccess<? super E, F> field, String variableName, int ltEqGt,
		boolean withEqual) {
		VariableCondition<E, F> condition = new VariableCondition(theSelection, field, variableName, ltEqGt, withEqual);
		if (this instanceof None)
			return condition;
		else
			return and(none -> condition);
	}

	@Override
	public EntityCondition<E> or(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(new None<>(theSelection));
		if (c instanceof None)
			return this;
		else if (this instanceof OrCondition)
			return new OrCondition<>(theSelection, (OrCondition<E>) this, (EntityConditionImpl<E>) c);
		else
			return new OrCondition<>(theSelection, this, (EntityConditionImpl<E>) c);
	}

	@Override
	public EntityCondition<E> and(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(new None<>(theSelection));
		if (c instanceof None)
			return this;
		else if (this instanceof AndCondition)
			return new AndCondition<>(theSelection, (AndCondition<E>) this, (EntityConditionImpl<E>) c);
		else
			return new AndCondition<>(theSelection, this, (EntityConditionImpl<E>) c);
	}

	public static class None<E> extends EntityConditionImpl<E> {
		public None(EntitySelection<E> selection) {
			super(selection);
		}
	}

	public static class DefiniteCondition<E, F> extends EntityConditionImpl<E> {
		private final EntityValueAccess<? super E, F> theField;
		private final F theValue;
		private final int theComparison;
		private final boolean isWithEqual;

		public DefiniteCondition(EntitySelection<E> selection, EntityValueAccess<? super E, F> field, F value, int comparison,
			boolean isWithEqual) {
			super(selection);
			theField = field;
			theValue = value;
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}
	}

	public static class VariableCondition<E, F> extends EntityConditionImpl<E> {
		private final EntityOperationVariable<E, F> theVariable;
		private final int theComparison;
		private final boolean isWithEqual;

		public VariableCondition(EntitySelection<E> selection, EntityValueAccess<? super E, F> field, String variableName, int comparison,
			boolean isWithEqual) {
			super(selection);
			theVariable = new EntityOperationVariable<>(selection, variableName, field);
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}
	}

	public static abstract class JoinedCondition<E> extends EntityConditionImpl<E> {
		private final List<EntityConditionImpl<E>> theConditions;

		public JoinedCondition(EntitySelection<E> selection, EntityConditionImpl<E>... conditions) {
			super(selection);
			theConditions = Collections.unmodifiableList(Arrays.asList(conditions));
		}

		protected JoinedCondition(EntitySelection<E> selection, JoinedCondition other, EntityConditionImpl<E> addedCondition) {
			super(selection);
			List<EntityConditionImpl<E>> conditions = new ArrayList<>(other.theConditions.size() + 1);
			conditions.addAll(other.theConditions);
			conditions.add(addedCondition);
			theConditions = Collections.unmodifiableList(conditions);
		}
	}

	public static class OrCondition<E> extends JoinedCondition<E> {
		public OrCondition(EntitySelection<E> selection, EntityConditionImpl<E>... conditions) {
			super(selection, conditions);
		}

		protected OrCondition(EntitySelection<E> selection, OrCondition other, EntityConditionImpl<E> addedCondition) {
			super(selection, other, addedCondition);
		}
	}

	public static class AndCondition<E> extends JoinedCondition<E> {
		public AndCondition(EntitySelection<E> selection, EntityConditionImpl<E>... conditions) {
			super(selection, conditions);
		}

		protected AndCondition(EntitySelection<E> selection, AndCondition other, EntityConditionImpl<E> addedCondition) {
			super(selection, other, addedCondition);
		}
	}
}
