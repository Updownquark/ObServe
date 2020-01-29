package org.observe.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * A condition to select which entities an {@link EntitySetOperation} will operate on based on the fields of the entity
 *
 * @param <E> The entity type that the condition is for
 */
public abstract class EntityCondition<E> implements Comparable<EntityCondition<E>> {
	public interface SelectionMechanism<E> {
		ConfigurableQuery<E> query(EntityCondition<E> selection);
		ConfigurableUpdate<E> update(EntityCondition<E> selection);
		ConfigurableDeletion<E> delete(EntityCondition<E> selection);
	}

	private final ObservableEntityType<E> theEntityType;
	private final SelectionMechanism<E> theMechanism;
	private final Map<String, EntityOperationVariable<E>> theGlobalVariables;
	private final Map<String, EntityOperationVariable<E>> theVariables;

	private EntityCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism,
		Map<String, EntityOperationVariable<E>> vars) {
		this(entityType, mechanism, vars, vars);
	}

	private EntityCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism,
		Map<String, EntityOperationVariable<E>> globalVars,
		Map<String, EntityOperationVariable<E>> vars) {
		theEntityType = entityType;
		theMechanism = mechanism;
		theGlobalVariables = vars;
		theVariables = vars;
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	protected abstract int getConditionType();

	public EntityCondition<E> or(Function<All<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getAll());
		if (c instanceof All)
			return this;
		else if (this instanceof OrCondition)
			return new OrCondition<>(theEntityType, (OrCondition<E>) this, c);
		else
			return new OrCondition<>(theEntityType, this, c);
	}

	public EntityCondition<E> and(Function<All<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getAll());
		if (c instanceof All)
			return this;
		else if (this instanceof AndCondition)
			return new AndCondition<>(theEntityType, (AndCondition<E>) this, c);
		else
			return new AndCondition<>(theEntityType, this, c);
	}

	protected Map<String, EntityOperationVariable<E>> getGlobalVariables() {
		return theGlobalVariables;
	}

	public Map<String, EntityOperationVariable<E>> getVariables() {
		return theVariables;
	}

	public abstract boolean test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues);

	public abstract boolean contains(EntityCondition<?> other);

	public abstract EntityCondition<E> transform(Function<EntityCondition<E>, EntityCondition<E>> transform);

	public EntityCondition<E> satisfy(QuickMap<String, Object> variables) {
		return transform(condition -> {
			if (condition instanceof VariableCondition) {
				VariableCondition<E, Object> vblCondition = (VariableCondition<E, Object>) condition;
				Object value = variables.get(vblCondition.getVariable().getName());
				// TODO Check constraints
				return new LiteralCondition<>(condition.getEntityType(), condition.theMechanism, vblCondition.getField(), value,
					vblCondition.getComparison(), vblCondition.isWithEqual(), Collections.emptyMap());
			} else
				return condition;
		});
	}

	/** @return A configurable query for entities matching this selection */
	public ConfigurableQuery<E> query() {
		return theMechanism.query(this);
	}
	/** @return A configurable update to modify entities matching this selection */
	public ConfigurableUpdate<E> update() {
		return theMechanism.update(this);
	}
	/** @return A configurable deletion to remove entities matching this selection */
	public ConfigurableDeletion<E> delete() {
		return theMechanism.delete(this);
	}

	protected All<E> getAll() {
		return new All<>(theEntityType, theMechanism, theVariables);
	}

	protected <F> LiteralCondition<E, F> createLiteral(EntityValueAccess<E, F> field, F value, int compare, boolean withEqual) {
		return new LiteralCondition<>(theEntityType, theMechanism, field, value, compare, withEqual, theGlobalVariables);
	}

	protected <F> VariableCondition<E, F> createVariable(EntityValueAccess<E, F> field, EntityOperationVariable<E> variable, int compare,
		boolean withEqual) {
		return new VariableCondition<>(theEntityType, theMechanism, field, variable, compare, withEqual, theGlobalVariables);
	}

	/**
	 * Selects all entities of a type
	 *
	 * @param <E> The java type of the entity
	 */
	public static class All<E> extends EntityCondition<E> {
		public All(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism,
			Map<String, EntityOperationVariable<E>> globalVars) {
			super(entityType, mechanism, globalVars, Collections.emptyMap());
		}

		@Override
		protected int getConditionType() {
			return 0;
		}

		@Override
		public EntityCondition<E> or(Function<All<E>, EntityCondition<E>> condition) {
			return this;
		}

		@Override
		public EntityCondition<E> and(Function<All<E>, EntityCondition<E>> condition) {
			return condition.apply(this);
		}

		public <F> EntityConditionIntermediate1<E, F> where(EntityValueAccess<E, F> field) {
			return new EntityConditionIntermediate1<>(this, field);
		}

		public <F> EntityConditionIntermediate1<E, F> where(Function<? super E, F> field) {
			return where(getEntityType().getField(field));
		}

		public <I, F> EntityConditionIntermediate1<E, F> where(Function<? super E, I> init,
			Function<ObservableEntityFieldType<E, I>, EntityValueAccess<E, F>> field) {
			return where(//
				field.apply(//
					getEntityType().getField(init)));
		}

		public EntityCondition<E> entity(EntityIdentity<E> id) {
			EntityCondition<E> c = this;
			for (int i = 0; i < id.getFields().keySize(); i++) {
				int idIndex = i;
				c = c.and(all -> all.where((ObservableEntityFieldType<E, Object>) id.getEntityType().getIdentityFields().get(idIndex))
					.equal().value(id.getFields().get(idIndex)));
			}
			return c;
		}

		@Override
		public boolean test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			return true;
		}

		@Override
		public boolean contains(EntityCondition<?> other) {
			return getEntityType().isAssignableFrom(other.getEntityType());
		}

		@Override
		public EntityCondition<E> transform(Function<EntityCondition<E>, EntityCondition<E>> transform) {
			return transform.apply(this);
		}

		@Override
		public int compareTo(EntityCondition<E> o) {
			return Integer.compare(getConditionType(), o.getConditionType());
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

	public static class EntityConditionIntermediate1<E, F> {
		private final All<E> theSource;
		private final EntityValueAccess<E, F> theField;

		EntityConditionIntermediate1(All<E> source, EntityValueAccess<E, F> field) {
			theSource = source;
			theField = field;
			if (field.getSourceEntity() != theSource.getEntityType())
				throw new IllegalStateException(
					"Field " + field + " is for " + field.getSourceEntity() + ", not " + theSource.getEntityType());
		}

		public All<E> getSource() {
			return theSource;
		}

		public EntityValueAccess<E, F> getField() {
			return theField;
		}

		public EntityConditionIntermediate2<E, F> compare(int ltEqGt, boolean withEqual) {
			return new EntityConditionIntermediate2<>(this, ltEqGt, withEqual);
		}

		public EntityConditionIntermediate2<E, F> equal() {
			return compare(0, true);
		}

		public EntityConditionIntermediate2<E, F> notEqual() {
			return compare(0, false);
		}

		public EntityConditionIntermediate2<E, F> lessThan() {
			return compare(-1, false);
		}

		public EntityConditionIntermediate2<E, F> lessThanOrEqual() {
			return compare(-1, true);
		}

		public EntityConditionIntermediate2<E, F> greaterThan() {
			return compare(1, false);
		}

		public EntityConditionIntermediate2<E, F> greaterThanOrEqual() {
			return compare(1, true);
		}

		public EntityConditionIntermediate2<E, F> eq() {
			return equal();
		}

		public EntityConditionIntermediate2<E, F> neq() {
			return notEqual();
		}

		public EntityConditionIntermediate2<E, F> lt() {
			return lessThan();
		}

		public EntityConditionIntermediate2<E, F> lte() {
			return lessThanOrEqual();
		}

		public EntityConditionIntermediate2<E, F> gt() {
			return greaterThan();
		}

		public EntityConditionIntermediate2<E, F> gte() {
			return greaterThanOrEqual();
		}

		public ValueCondition<E, F> NULL() {
			return compare(0, true).value(null);
		}

		public ValueCondition<E, F> notNull() {
			return compare(0, false).value(null);
		}
	}

	public static class EntityConditionIntermediate2<E, F> {
		private final EntityConditionIntermediate1<E, F> thePrecursor;
		private final int theCompare;
		private final boolean isWithEqual;

		EntityConditionIntermediate2(EntityConditionIntermediate1<E, F> precursor, int compare, boolean withEqual) {
			thePrecursor = precursor;
			theCompare = compare;
			isWithEqual = withEqual;
		}

		public LiteralCondition<E, F> value(F value) {
			return thePrecursor.getSource().createLiteral(thePrecursor.getField(), value, theCompare, isWithEqual);
		}

		public VariableCondition<E, F> variable(String variableName) {
			EntityOperationVariable<E> vbl = thePrecursor.getSource().getVariables().get(variableName);
			if (vbl == null)
				vbl = new EntityOperationVariable<>(null, variableName);
			return thePrecursor.getSource().createVariable(thePrecursor.getField(), vbl, theCompare, isWithEqual);
		}

		public VariableCondition<E, F> declareVariable(String variableName) {
			if (thePrecursor.getSource().getGlobalVariables().containsKey(variableName))
				throw new IllegalArgumentException("A variable named " + variableName + " has already been declared");
			return thePrecursor.getSource().createVariable(thePrecursor.getField(), new EntityOperationVariable<>(null, variableName),
				theCompare, isWithEqual);
		}

		public VariableCondition<E, F> reuseVariable(String variableName) {
			EntityOperationVariable<E> vbl = thePrecursor.getSource().getVariables().get(variableName);
			if (vbl == null)
				throw new IllegalArgumentException("A variable named " + variableName + " has not been declared");
			return thePrecursor.getSource().createVariable(thePrecursor.getField(), vbl, theCompare, isWithEqual);
		}
	}

	public static abstract class ValueCondition<E, F> extends EntityCondition<E> {
		private final EntityValueAccess<E, F> theField;
		private final int theComparison;
		private final boolean isWithEqual;

		public ValueCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism, EntityValueAccess<E, F> field,
			int comparison, boolean isWithEqual, Map<String, EntityOperationVariable<E>> globalVars,
			Map<String, EntityOperationVariable<E>> vars) {
			super(entityType, mechanism, globalVars, vars);
			theField = field;
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}

		public EntityValueAccess<E, F> getField() {
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

		public abstract F getConditionValue(QuickMap<String, Object> varValues);
		protected abstract int compareValue(ValueCondition<? super E, ? super F> other);

		@Override
		public boolean test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			F value1 = theField.getValue(entity);
			F value2 = getConditionValue(varValues);
			return test(value1, value2);
		}

		protected boolean test(F value1, F value2) {
			int comp = getField().compare(value1, value2);
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
		public EntityCondition<E> transform(Function<EntityCondition<E>, EntityCondition<E>> transform) {
			return transform.apply(this);
		}

		@Override
		public int compareTo(EntityCondition<E> o) {
			int comp = Integer.compare(getConditionType(), o.getConditionType());
			ValueCondition<E, ?> other = (ValueCondition<E, ?>) o;
			if (comp == 0)
				comp = getField().compareTo(other.getField());
			if (comp == 0)
				comp = Integer.compare(getComparison(), other.getComparison());
			if (comp == 0)
				comp = Boolean.compare(isWithEqual, other.isWithEqual);
			if (comp == 0)
				comp = compareValue((ValueCondition<E, F>) other);
			return comp;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof LiteralCondition))
				return false;
			ValueCondition<?, ?> other = (ValueCondition<?, ?>) obj;
			return getField().equals(other.getField())//
				&& getComparison() == other.getComparison() && isWithEqual() == other.isWithEqual()//
				&& compareValue((ValueCondition<E, F>) other) == 0;
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theField).append(getSymbol()).toString();
		}
	}

	public static class LiteralCondition<E, F> extends ValueCondition<E, F> {
		private final F theValue;

		public LiteralCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism, EntityValueAccess<E, F> field, F value,
			int comparison, boolean isWithEqual, Map<String, EntityOperationVariable<E>> globalVars) {
			super(entityType, mechanism, field, comparison, isWithEqual, globalVars, Collections.emptyMap());
			if (value != null && !TypeTokens.get().isInstance(field.getValueType(), value))
				throw new IllegalArgumentException(
					value.getClass().getName() + " " + value + " is not an instance of " + field.getValueType() + " for field " + field);
			theValue = value;
		}

		@Override
		protected int getConditionType() {
			return 1;
		}

		public F getValue() {
			return theValue;
		}

		@Override
		public F getConditionValue(QuickMap<String, Object> varValues) {
			return getValue();
		}

		public boolean test(F fieldValue) {
			return test(fieldValue, theValue);
		}

		@Override
		public boolean contains(EntityCondition<?> other) {
			if (other == this)
				return true;
			else if (!getEntityType().isAssignableFrom(other.getEntityType()))
				return false;
			else if (other instanceof AndCondition) {
				for (EntityCondition<?> c : ((AndCondition<?>) other).getConditions())
					if (c.contains(this))
						return true;
				return false;
			} else if (!(other instanceof LiteralCondition))
				return false;
			else if (!getField().isOverride(((LiteralCondition<? extends E, ?>) other).getField()))
				return false;

			LiteralCondition<? super E, ? super F> otherV = (LiteralCondition<? super E, ? super F>) other;
			if (getComparison() < 0) {
				if (otherV.getComparison() > 0)
					return false;
				else if (otherV.getComparison() < 0) {
					int comp = compareValue(otherV);
					if (comp > 0)
						return true;
					else if (comp == 0)
						return isWithEqual() || !otherV.isWithEqual();
					else
						return false;
				} else if (!isWithEqual() || !otherV.isWithEqual())
					return false;
				else
					return compareValue(otherV) == 0;
			} else if (getComparison() > 0) {
				if (otherV.getComparison() < 0)
					return false;
				else if (otherV.getComparison() > 0) {
					int comp = compareValue(otherV);
					if (comp < 0)
						return true;
					else if (comp == 0)
						return isWithEqual() || !otherV.isWithEqual();
					else
						return false;
				} else if (!isWithEqual() || !otherV.isWithEqual())
					return false;
				else
					return compareValue(otherV) == 0;
			} else {
				return otherV.getComparison() == 0 && isWithEqual() == otherV.isWithEqual() && compareValue(otherV) == 0;
			}
		}

		@Override
		protected int compareValue(ValueCondition<? super E, ? super F> other) {
			return getField().compare(theValue, ((LiteralCondition<E, F>) other).getValue());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getField(), theValue, getComparison(), isWithEqual());
		}

		@Override
		public String toString() {
			return super.toString() + theValue;
		}
	}

	public static class VariableCondition<E, F> extends ValueCondition<E, F> {
		private final EntityOperationVariable<E> theVariable;

		public VariableCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism, EntityValueAccess<E, F> field,
			EntityOperationVariable<E> variable, int comparison, boolean isWithEqual, Map<String, EntityOperationVariable<E>> globalVars) {
			super(entityType, mechanism, field, comparison, isWithEqual, addVar(globalVars, variable), singleVar(variable));
			theVariable = variable;
		}

		private static <E> Map<String, EntityOperationVariable<E>> addVar(Map<String, EntityOperationVariable<E>> vars,
			EntityOperationVariable<E> var) {
			if (vars.containsKey(var.getName()))
				return vars;
			LinkedHashMap<String, EntityOperationVariable<E>> newVars = new LinkedHashMap<>((vars.size() + 1) * 4 / 3);
			newVars.putAll(vars);
			newVars.put(var.getName(), var);
			return Collections.unmodifiableMap(vars);
		}

		private static <E> Map<String, EntityOperationVariable<E>> singleVar(EntityOperationVariable<E> var) {
			Map<String, EntityOperationVariable<E>> newMap = new LinkedHashMap<>(1);
			newMap.put(var.getName(), var);
			return Collections.unmodifiableMap(newMap);
		}

		@Override
		protected int getConditionType() {
			return 2;
		}

		public EntityOperationVariable<E> getVariable() {
			return theVariable;
		}

		@Override
		public F getConditionValue(QuickMap<String, Object> varValues) {
			return (F) varValues.get(theVariable.getName());
		}

		@Override
		public int compareTo(EntityCondition<E> o) {
			int comp = Integer.compare(getConditionType(), o.getConditionType());
			if (comp == 0)
				comp = getField().compareTo(((VariableCondition<E, ?>) o).getField());
			if (comp == 0)
				comp = StringUtils.compareNumberTolerant(theVariable.getName(), ((VariableCondition<E, F>) o).theVariable.getName(), true,
					true);
			return comp;
		}

		@Override
		public boolean contains(EntityCondition<?> other) {
			if (other == this)
				return true;
			else if (!getEntityType().isAssignableFrom(other.getEntityType()))
				return false;
			else if (other instanceof AndCondition) {
				for (EntityCondition<?> c : ((AndCondition<?>) other).getConditions())
					if (c.contains(this))
						return true;
				return false;
			} else if (!(other instanceof VariableCondition))
				return false;
			else if (!getField().isOverride(((VariableCondition<? extends E, ?>) other).getField()))
				return false;

			VariableCondition<? super E, ? super F> otherV = (VariableCondition<? super E, ? super F>) other;
			if(!theVariable.getName().equals(otherV.theVariable.getName()))
				return false;
			if(getComparison()<0){
				if(otherV.getComparison()>0)
					return false;
				else if(otherV.getComparison()<0)
					return isWithEqual() || !otherV.isWithEqual();
				else
					return isWithEqual() && otherV.isWithEqual();
			} else if(getComparison()>0){
				if(otherV.getComparison()<0)
					return false;
				else if (otherV.getComparison() > 0)
					return isWithEqual() || !otherV.isWithEqual();
				else
					return isWithEqual() && otherV.isWithEqual();
			} else
				return otherV.getComparison()==0 && isWithEqual()==otherV.isWithEqual();
		}

		@Override
		protected int compareValue(ValueCondition<? super E, ? super F> other) {
			return theVariable.getName().compareTo(((VariableCondition<? super E, ? super F>) other).theVariable.getName());
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
			super(entityType, conditions[0].theMechanism, joinVars(conditions));
			theConditions = Collections.unmodifiableList(Arrays.asList(conditions));
		}

		protected CompositeCondition(ObservableEntityType<E> entityType, CompositeCondition other, EntityCondition<E> addedCondition) {
			super(entityType, addedCondition.theMechanism, joinVars(other, addedCondition));
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
		public int compareTo(EntityCondition<E> o) {
			if (this == o)
				return 0;
			int comp = Integer.compare(getConditionType(), o.getConditionType());
			if (comp != 0)
				return comp;
			CompositeCondition<E> other = (CompositeCondition<E>) o;
			for (int c = 0; c < theConditions.size() && c < other.getConditions().size(); c++) {
				comp = theConditions.get(c).compareTo(other.theConditions.get(c));
				if (comp != 0)
					return comp;
			}
			return Integer.compare(theConditions.size(), other.theConditions.size());
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
			if (getEntityType() != other.getEntityType() || theConditions.size() != other.theConditions.size())
				return false;
			for (EntityCondition<E> c : getConditions()) {
				if (!other.getConditions().contains(c))
					return false;
			}
			return true;
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
		protected int getConditionType() {
			return 10;
		}

		@Override
		public EntityCondition<E> or(Function<All<E>, EntityCondition<E>> condition) {
			EntityCondition<E> other = condition.apply(getAll());
			if (getConditions().contains(other))
				return this;
			return new OrCondition<>(getEntityType(), this, other);
		}

		@Override
		public boolean test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			for (EntityCondition<E> condition : getConditions()) {
				if (condition.test(entity, varValues))
					return true;
			}
			return false;
		}

		@Override
		public boolean contains(EntityCondition<?> other) {
			if (!getEntityType().isAssignableFrom(other.getEntityType()))
				return false;
			for (EntityCondition<E> c : getConditions())
				if (c.contains(other))
					return true;
			if (other instanceof OrCondition) {
				for (EntityCondition<E> c : ((OrCondition<E>) other).getConditions()) {
					if (!contains(c))
						return false;
				}
				return true;
			} else
				return false;
		}

		@Override
		public EntityCondition<E> transform(Function<EntityCondition<E>, EntityCondition<E>> transform) {
			List<EntityCondition<E>> conditions = new ArrayList<>();
			boolean different = false;
			for (EntityCondition<E> c : getConditions()) {
				EntityCondition<E> transformed = c.transform(transform);
				conditions.add(transformed);
				different |= transformed != c;
			}
			if (!different)
				return this;
			else
				return new OrCondition<>(getEntityType(), conditions.toArray(new EntityCondition[conditions.size()]));
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
		protected int getConditionType() {
			return 11;
		}

		@Override
		public boolean test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			for (EntityCondition<E> condition : getConditions())
				if (!condition.test(entity, varValues))
					return false;
			return true;
		}

		@Override
		public boolean contains(EntityCondition<?> other) {
			if (!getEntityType().isAssignableFrom(other.getEntityType()))
				return false;
			boolean allContain = true;
			for (EntityCondition<E> condition : getConditions()) {
				if (!condition.contains(other)) {
					allContain = false;
					break;
				}
			}
			if (allContain)
				return true;
			if (other instanceof AndCondition) {
				for (EntityCondition<E> condition : ((AndCondition<E>) other).getConditions()) {
					if (!condition.contains(this))
						return false;
				}
				return true;
			} else
				return false;
		}

		@Override
		public EntityCondition<E> transform(Function<EntityCondition<E>, EntityCondition<E>> transform) {
			List<EntityCondition<E>> conditions = new ArrayList<>();
			boolean different = false;
			for (EntityCondition<E> c : getConditions()) {
				EntityCondition<E> transformed = c.transform(transform);
				conditions.add(transformed);
				different |= transformed != c;
			}
			if (!different)
				return this;
			else
				return new AndCondition<>(getEntityType(), conditions.toArray(new EntityCondition[conditions.size()]));
		}

		@Override
		public String toString() {
			return "AND" + super.toString();
		}
	}
}
