package org.observe.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.StringUtils;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * A condition to select which entities an {@link EntitySetOperation} will operate on based on the fields of the entity
 *
 * @param <E> The entity type that the condition is for
 */
public abstract class EntityCondition<E> {
	public static <E> EntityCondition<E> all(ObservableEntityType<E> type) {
		return new All<>(type);
	}

	private final ObservableEntityType<E> theEntityType;
	private final Map<String, EntityOperationVariable<E>> theVariables;

	private EntityCondition(ObservableEntityType<E> entityType, Map<String, EntityOperationVariable<E>> vars) {
		theEntityType = entityType;
		theVariables = vars;
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	public <F> EntityCondition<E> compare(EntityValueAccess<? super E, F> field, F value, int ltEqGt, boolean withEqual) {
		LiteralCondition<E, F> condition = new LiteralCondition<>(theEntityType, field, value, ltEqGt, withEqual);
		if (this instanceof All)
			return condition;
		else
			return and(none -> condition);
	}

	public <F> EntityCondition<E> compareVariable(EntityValueAccess<? super E, F> field, String variableName, int ltEqGt,
		boolean withEqual) {
		if (theVariables.containsKey(variableName))
			throw new IllegalArgumentException("This condition already contains a variable named " + variableName);
		VariableCondition<E, F> condition = new VariableCondition<>(theEntityType, field, variableName, ltEqGt, withEqual);
		if (this instanceof All)
			return condition;
		else
			return and(none -> condition);
	}

	public EntityCondition<E> or(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getAll());
		if (c instanceof All)
			return this;
		else if (this instanceof OrCondition)
			return new OrCondition<>(theEntityType, (OrCondition<E>) this, c);
		else
			return new OrCondition<>(theEntityType, this, c);
	}

	public EntityCondition<E> and(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getAll());
		if (c instanceof All)
			return this;
		else if (this instanceof AndCondition)
			return new AndCondition<>(theEntityType, (AndCondition<E>) this, c);
		else
			return new AndCondition<>(theEntityType, this, c);
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

	public Map<String, EntityOperationVariable<E>> getVariables() {
		return theVariables;
	}

	public abstract boolean test(E entity, QuickMap<String, Object> varValues);

	protected EntityCondition<E> getAll() {
		return new All<>(theEntityType, theVariables);
	}

	public static class All<E> extends EntityCondition<E> {
		public All(ObservableEntityType<E> entityType) {
			this(entityType, Collections.emptyMap());
		}

		public All(ObservableEntityType<E> entityType, Map<String, EntityOperationVariable<E>> vars) {
			super(entityType, vars);
		}

		@Override
		public boolean test(E entity, QuickMap<String, Object> varValues) {
			return true;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof All;
		}

		@Override
		public String toString() {
			return "all";
		}
	}

	public static abstract class ValueCondition<E, F> extends EntityCondition<E> implements Comparator<F> {
		private final EntityValueAccess<? super E, F> theField;
		private final int theComparison;
		private final boolean isWithEqual;

		public ValueCondition(ObservableEntityType<E> entityType, EntityValueAccess<? super E, F> field, int comparison,
			boolean isWithEqual, Map<String, EntityOperationVariable<E>> vars) {
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

		public abstract F getConditionValue(E entity, QuickMap<String, Object> varValues);

		@Override
		public int compare(F value1, F value2) {
			return ((Comparable<F>) value1).compareTo(value2);
		}

		@Override
		public boolean test(E entity, QuickMap<String, Object> varValues) {
			F value1 = theField.getValue(entity);
			F value2 = getConditionValue(entity, varValues);
			return test(value1, value2);
		}

		protected boolean test(F value1, F value2) {
			int comp = compare(value1, value2);
			if (isWithEqual && comp == 0)
				return true;
			if (theComparison < 0) {
				return comp < 0;
			} else if (theComparison == 0) {
				return !isWithEqual && comp != 0;
			} else {
				return comp > 0;
			}
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theField).append(getSymbol()).toString();
		}
	}

	public static class LiteralCondition<E, F> extends ValueCondition<E, F> {
		private final F theValue;

		public LiteralCondition(ObservableEntityType<E> entityType, EntityValueAccess<? super E, F> field, F value, int comparison,
			boolean isWithEqual) {
			super(entityType, field, comparison, isWithEqual, Collections.emptyMap());
			theValue = value;
		}

		public F getValue() {
			return theValue;
		}

		@Override
		public F getConditionValue(E entity, QuickMap<String, Object> varValues) {
			return getValue();
		}

		public boolean test(F fieldValue) {
			return test(fieldValue, theValue);
		}

		@Override
		public int hashCode() {
			return Objects.hash(getField(), theValue, getComparison(), isWithEqual());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof LiteralCondition))
				return false;
			LiteralCondition<?, ?> other = (LiteralCondition<?, ?>) obj;
			return getField().equals(other.getField()) && getComparison() == other.getComparison() && isWithEqual() == other.isWithEqual();
		}

		@Override
		public String toString() {
			return super.toString() + theValue;
		}
	}

	public static class VariableCondition<E, F> extends ValueCondition<E, F> {
		private final EntityOperationVariable<E> theVariable;

		public VariableCondition(ObservableEntityType<E> entityType, EntityValueAccess<? super E, F> field, String variableName,
			int comparison, boolean isWithEqual) {
			super(entityType, field, comparison, isWithEqual, singleVar(new EntityOperationVariable<>(entityType, variableName)));
			theVariable = new EntityOperationVariable<>(entityType, variableName);
		}

		private static <E> Map<String, EntityOperationVariable<E>> singleVar(EntityOperationVariable<E> var) {
			Map<String, EntityOperationVariable<E>> newMap = new LinkedHashMap<>(1);
			newMap.put(var.getName(), var);
			return Collections.unmodifiableMap(newMap);
		}

		public EntityOperationVariable<E> getVariable() {
			return theVariable;
		}

		@Override
		public F getConditionValue(E entity, QuickMap<String, Object> varValues) {
			return (F) varValues.get(theVariable.getName());
		}

		@Override
		public int hashCode() {
			return Objects.hash(theVariable, getComparison(), isWithEqual());
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof VariableCondition))
				return false;
			VariableCondition<?, ?> other = (VariableCondition<?, ?>) obj;
			return theVariable.equals(other.theVariable) && getComparison() == other.getComparison()
				&& isWithEqual() == other.isWithEqual();
		}

		@Override
		public String toString() {
			return super.toString() + ':' + theVariable.getName();
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

		private static <E> Map<String, EntityOperationVariable<E>> joinVars(EntityCondition<E>... conditions) {
			int count = 0;
			for (EntityCondition<E> cond : conditions) {
				count += cond.getVariables().size();
				if (count > 3)
					break;
			}
			Map<String, EntityOperationVariable<E>> vars = new LinkedHashMap<>(count * 4 / 3);
			boolean first = true;
			for (EntityCondition<E> cond : conditions) {
				if (first)
					first = false;
				else {
					for (Map.Entry<String, EntityOperationVariable<E>> var : cond.getVariables().entrySet()) {
						EntityOperationVariable<E> pre = vars.get(var.getKey());
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
		public boolean test(E entity, QuickMap<String, Object> varValues) {
			for (EntityCondition<E> condition : getConditions())
				if (condition.test(entity, varValues))
					return true;
			return false;
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
		public boolean test(E entity, QuickMap<String, Object> varValues) {
			for (EntityCondition<E> condition : getConditions())
				if (!condition.test(entity, varValues))
					return false;
			return true;
		}

		@Override
		public String toString() {
			return "AND" + super.toString();
		}
	}
}
