package org.observe.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.StringUtils;

public class EntityCondition<E> {
	private final ObservableEntityType<E> theEntitType;
	private final Map<String, EntityOperationVariable<E, ?>> theVariables;

	public EntityCondition(ObservableEntityType<E> entityType, Map<String, EntityOperationVariable<E, ?>> vars) {
		theEntitType = entityType;
		theVariables = vars;
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntitType;
	}

	public <F> EntityCondition<E> compare(EntityValueAccess<? super E, F> field, F value, int ltEqGt, boolean withEqual) {
		DefiniteCondition<E, F> condition = new DefiniteCondition<>(theEntitType, field, value, ltEqGt, withEqual);
		if (this instanceof None)
			return condition;
		else
			return and(none -> condition);
	}

	public <F> EntityCondition<E> compareVariable(EntityValueAccess<? super E, F> field, String variableName, int ltEqGt,
		boolean withEqual) {
		if (theVariables.containsKey(variableName))
			throw new IllegalArgumentException("This condition already contains a variable named " + variableName);
		VariableCondition<E, F> condition = new VariableCondition<>(theEntitType, field, variableName, ltEqGt, withEqual);
		if (this instanceof None)
			return condition;
		else
			return and(none -> condition);
	}

	public EntityCondition<E> or(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getNone());
		if (c instanceof None)
			return this;
		else if (this instanceof OrCondition)
			return new OrCondition<>(theEntitType, (OrCondition<E>) this, c);
		else
			return new OrCondition<>(theEntitType, this, c);
	}

	public EntityCondition<E> and(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getNone());
		if (c instanceof None)
			return this;
		else if (this instanceof AndCondition)
			return new AndCondition<>(theEntitType, (AndCondition<E>) this, c);
		else
			return new AndCondition<>(theEntitType, this, c);
	}

	public <F> EntityCondition<E> equal(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 0, true);
	}

	public <F> EntityCondition<E> notEqual(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 0, false);
	}

	public <F> EntityCondition<E> lessThan(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, -1, false);
	}

	public <F> EntityCondition<E> lessThanOrEqual(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, -1, true);
	}

	public <F> EntityCondition<E> greaterThan(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 1, false);
	}

	public <F> EntityCondition<E> greaterThanOrEqual(EntityValueAccess<? super E, F> field, F value) {
		return compare(field, value, 1, true);
	}

	public <F> EntityCondition<E> equalVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 0, true);
	}

	public <F> EntityCondition<E> notEqualVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 0, false);
	}

	public <F> EntityCondition<E> lessThanVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, -1, false);
	}

	public <F> EntityCondition<E> lessThanOrEqualVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, -1, true);
	}

	public <F> EntityCondition<E> greaterThanVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 1, false);
	}

	public <F> EntityCondition<E> greaterThanOrEqualVariable(EntityValueAccess<? super E, F> field, String variableName) {
		return compareVariable(field, variableName, 1, true);
	}

	public <F> EntityValueAccess<E, F> valueFor(Function<? super E, F> fieldGetter) {
		return getEntityType().getField(fieldGetter);
	}

	public Map<String, EntityOperationVariable<E, ?>> getVariables() {
		return theVariables;
	}

	protected EntityCondition<E> getNone() {
		return new None<>(theEntitType, theVariables);
	}

	public static class None<E> extends EntityCondition<E> {
		public None(ObservableEntityType<E> entityType) {
			this(entityType, Collections.emptyMap());
		}

		public None(ObservableEntityType<E> entityType, Map<String, EntityOperationVariable<E, ?>> vars) {
			super(entityType, vars);
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof None;
		}

		@Override
		public String toString() {
			return "none";
		}
	}

	public static abstract class ValueCondition<E, F> extends EntityCondition<E> {
		private final EntityValueAccess<? super E, F> theField;
		private final int theComparison;
		private final boolean isWithEqual;

		public ValueCondition(ObservableEntityType<E> entityType, EntityValueAccess<? super E, F> field, int comparison,
			boolean isWithEqual, Map<String, EntityOperationVariable<E, ?>> vars) {
			super(entityType, vars);
			theField = field;
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}

		public EntityValueAccess<? super E, F> getField() {
			return theField;
		}

		public int getComparison() {
			return theComparison;
		}

		public boolean isWithEqual() {
			return isWithEqual;
		}

		public char getSymbol() {
			if (theComparison < 0) {
				if (isWithEqual)
					return '\u2264';
				else
					return '<';
			} else if (theComparison == 0) {
				if (isWithEqual)
					return '=';
				else
					return '\u2260';
			} else {
				if (isWithEqual)
					return '\u2265';
				else
					return '>';
			}
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theField).append(getSymbol()).append(':').toString();
		}
	}

	public static class DefiniteCondition<E, F> extends ValueCondition<E, F> {
		private final F theValue;

		public DefiniteCondition(ObservableEntityType<E> entityType, EntityValueAccess<? super E, F> field, F value, int comparison,
			boolean isWithEqual) {
			super(entityType, field, comparison, isWithEqual, Collections.emptyMap());
			theValue = value;
		}

		public F getValue() {
			return theValue;
		}

		@Override
		public int hashCode() {
			return Objects.hash(getField(), theValue, getComparison(), isWithEqual());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof DefiniteCondition))
				return false;
			DefiniteCondition<?, ?> other = (DefiniteCondition<?, ?>) obj;
			return getField().equals(other.getField()) && getComparison() == other.getComparison() && isWithEqual() == other.isWithEqual();
		}

		@Override
		public String toString() {
			return super.toString() + theValue;
		}
	}

	public static class VariableCondition<E, F> extends EntityCondition<E> {
		private final EntityOperationVariable<E, F> theVariable;
		private final int theComparison;
		private final boolean isWithEqual;

		public VariableCondition(ObservableEntityType<E> entityType, EntityValueAccess<? super E, F> field, String variableName,
			int comparison, boolean isWithEqual) {
			super(entityType, singleVar(new EntityOperationVariable<>(entityType, variableName, field)));
			theVariable = new EntityOperationVariable<>(entityType, variableName, field);
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}

		private static <E> Map<String, EntityOperationVariable<E, ?>> singleVar(EntityOperationVariable<E, ?> var) {
			Map<String, EntityOperationVariable<E, ?>> newMap = new LinkedHashMap<>(1);
			newMap.put(var.getName(), var);
			return Collections.unmodifiableMap(newMap);
		}

		public EntityOperationVariable<E, F> getVariable() {
			return theVariable;
		}

		public int getComparison() {
			return theComparison;
		}

		public boolean isWithEqual() {
			return isWithEqual;
		}

		public char getSymbol() {
			if (theComparison < 0) {
				if (isWithEqual)
					return '\u2264';
				else
					return '<';
			} else if (theComparison == 0) {
				if (isWithEqual)
					return '=';
				else
					return '\u2260';
			} else {
				if (isWithEqual)
					return '\u2265';
				else
					return '>';
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theVariable, theComparison, isWithEqual);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof VariableCondition))
				return false;
			VariableCondition<?, ?> other = (VariableCondition<?, ?>) obj;
			return theVariable.equals(other.theVariable) && theComparison == other.theComparison && isWithEqual == other.isWithEqual;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theVariable.getValue()).append(getSymbol()).append(':').append(theVariable.getName())
				.toString();
		}
	}

	public static abstract class CompositeCondition<E> extends EntityCondition<E> {
		private final List<EntityCondition<E>> theConditions;

		public CompositeCondition(ObservableEntityType<E> entityType, EntityCondition<E>... conditions) {
			super(entityType, joinVars(conditions));
			theConditions = Collections.unmodifiableList(Arrays.asList(conditions));
		}

		protected CompositeCondition(ObservableEntityType<E> entityType, CompositeCondition other, EntityCondition<E> addedCondition) {
			super(entityType, joinVars(other, addedCondition));
			List<EntityCondition<E>> conditions = new ArrayList<>(other.theConditions.size() + 1);
			conditions.addAll(other.theConditions);
			conditions.add(addedCondition);
			theConditions = Collections.unmodifiableList(conditions);
		}

		private static <E> Map<String, EntityOperationVariable<E, ?>> joinVars(EntityCondition<E>... conditions) {
			int count = 0;
			for (EntityCondition<E> cond : conditions) {
				count += cond.getVariables().size();
				if (count > 3)
					break;
			}
			Map<String, EntityOperationVariable<E, ?>> vars = new LinkedHashMap<>(count * 4 / 3);
			boolean first = true;
			for (EntityCondition<E> cond : conditions) {
				if (first)
					first = false;
				else {
					for (Map.Entry<String, EntityOperationVariable<E, ?>> var : cond.getVariables().entrySet()) {
						EntityOperationVariable<E, ?> pre = vars.get(var.getKey());
						if (pre != null && pre != var.getValue())
							throw new IllegalArgumentException(
								"The composite condition would contain more than one variable named " + var.getKey());
					}
				}
				vars.putAll(cond.getVariables());
			}
			return Collections.unmodifiableMap(vars);
		}

		public List<EntityCondition<E>> getConditions() {
			return theConditions;
		}

		@Override
		public int hashCode() {
			int hash = getClass().hashCode();
			hash = hash * 31 + theConditions.hashCode();
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (obj == null || obj.getClass() != getClass())
				return false;
			CompositeCondition<E> other = (CompositeCondition<E>) obj;
			return theConditions.equals(other.theConditions);
		}

		@Override
		public String toString() {
			return StringUtils.print(new StringBuilder('('), ",", theConditions, null).append(')').toString();
		}
	}

	public static class OrCondition<E> extends CompositeCondition<E> {
		public OrCondition(ObservableEntityType<E> entityType, EntityCondition<E>... conditions) {
			super(entityType, conditions);
		}

		protected OrCondition(ObservableEntityType<E> entityType, OrCondition other, EntityCondition<E> addedCondition) {
			super(entityType, other, addedCondition);
		}

		@Override
		public String toString() {
			return "OR" + super.toString();
		}
	}

	public static class AndCondition<E> extends CompositeCondition<E> {
		public AndCondition(ObservableEntityType<E> entityType, EntityCondition<E>... conditions) {
			super(entityType, conditions);
		}

		protected AndCondition(ObservableEntityType<E> entityType, AndCondition other, EntityCondition<E> addedCondition) {
			super(entityType, other, addedCondition);
		}

		@Override
		public String toString() {
			return "AND" + super.toString();
		}
	}
}
