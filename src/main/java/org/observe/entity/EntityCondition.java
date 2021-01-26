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
	/**
	 * Allows {@link EntityCondition} to implement {@link EntityCondition#query() query()}, {@link EntityCondition#update() update()}, and
	 * {@link EntityCondition#delete() delete()}
	 *
	 * @param <E> The type of entity the condition applies to
	 */
	public interface SelectionMechanism<E> {
		/**
		 * @param selection The selection to query with
		 * @return The configurable query
		 */
		ConfigurableQuery<E> query(EntityCondition<E> selection);

		/**
		 * @param selection The selection to update with
		 * @return The configurable update
		 */
		ConfigurableUpdate<E> update(EntityCondition<E> selection);

		/**
		 * @param selection The selection to delete with
		 * @return The configurable deletion
		 */
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
		Map<String, EntityOperationVariable<E>> globalVars, Map<String, EntityOperationVariable<E>> vars) {
		theEntityType = entityType;
		theMechanism = mechanism;
		theGlobalVariables = vars;
		theVariables = vars;
	}

	/** @return The type of entities that this condition selects from */
	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	/** @return An integer uniquely identifying this condition class */
	protected abstract int getConditionType();

	/**
	 * @param condition Produces a condition to OR with this condition
	 * @return A condition that is true when either this condition or the new condition matches an entity
	 */
	public EntityCondition<E> or(Function<All<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getAll());
		if (c instanceof All)
			return this;
		else if (this instanceof OrCondition)
			return new OrCondition<>(theEntityType, (OrCondition<E>) this, c);
		else
			return new OrCondition<>(theEntityType, this, c);
	}

	/**
	 * @param condition Produces a condition to AND with this condition
	 * @return A condition that is true when both this condition and the new condition matches an entity
	 */
	public EntityCondition<E> and(Function<All<E>, EntityCondition<E>> condition) {
		EntityCondition<E> c = condition.apply(getAll());
		if (c instanceof All)
			return this;
		else if (this instanceof AndCondition)
			return new AndCondition<>(theEntityType, (AndCondition<E>) this, c);
		else
			return new AndCondition<>(theEntityType, this, c);
	}

	/** @return All variables used by this condition and any that produced it */
	protected Map<String, EntityOperationVariable<E>> getGlobalVariables() {
		return theGlobalVariables;
	}

	/** @return All variables used by this condition */
	public Map<String, EntityOperationVariable<E>> getVariables() {
		return theVariables;
	}

	/**
	 * @param entity The entity to test
	 * @param varValues The values of all variables in this condition
	 * @return null if this condition matches the given entity with the given variable values, or the most specific condition that is
	 *         violated
	 */
	public abstract EntityCondition<E> test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues);

	/**
	 * @param other The other condition to test
	 * @return Whether this condition is always true when the other condition is true
	 */
	public abstract boolean contains(EntityCondition<?> other);

	/**
	 * @param transform A transform function to apply to this condition
	 * @return The transformed condition
	 */
	public abstract EntityCondition<E> transform(Function<EntityCondition<E>, EntityCondition<E>> transform);

	/**
	 * @param field The field to get the condition for
	 * @return The component of this condition related to the given field which must be satisfied for this condition to be satisfied
	 */
	public abstract EntityCondition<E> getCondition(ObservableEntityFieldType<E, ?> field);

	/**
	 * @param variables The values of all variables used by this condition
	 * @return A condition identical to this one, but with all {@link VariableCondition variable} conditions replaced with
	 *         {@link LiteralCondition literal} ones with values corresponding to that in the given map
	 */
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

	/** @return An {@link All} condition */
	protected All<E> getAll() {
		return new All<>(theEntityType, theMechanism, theVariables);
	}

	/**
	 * @param field The field to compare
	 * @param value The field value to compare against
	 * @param compare Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0)
	 * @param withEqual Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false)
	 * @return The new literal condition
	 */
	protected <F> LiteralCondition<E, F> createLiteral(EntityValueAccess<E, F> field, F value, int compare, boolean withEqual) {
		return new LiteralCondition<>(theEntityType, theMechanism, field, value, compare, withEqual, theGlobalVariables);
	}

	/**
	 * @param field The field to compare
	 * @param variable The variable to compare against
	 * @param compare Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0)
	 * @param withEqual Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false)
	 * @return The new variable condition
	 */
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
		/**
		 * @param entityType The entity type for the condition
		 * @param mechanism The selection mechanism to power {@link #query()}, {@link #update()}, and {@link #delete()}
		 * @param globalVars The variables for the condition
		 */
		public All(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism,
			Map<String, EntityOperationVariable<E>> globalVars) {
			super(entityType, mechanism, globalVars, Collections.emptyMap());
		}

		@Override
		protected int getConditionType() {
			return 0;
		}

		/**
		 * Just an explicit way of selecting all entities
		 *
		 * @return This all condition
		 */
		public All<E> all() {
			return this;
		}

		@Override
		public EntityCondition<E> or(Function<All<E>, EntityCondition<E>> condition) {
			return this;
		}

		@Override
		public EntityCondition<E> and(Function<All<E>, EntityCondition<E>> condition) {
			return condition.apply(this);
		}

		/**
		 * @param <F> The type of the field for the condition
		 * @param field The value access to use to derive a value to compare against the condition value (specified later)
		 * @return An intermediate to select the type of the comparison
		 */
		public <F> EntityConditionIntermediate1<E, F> where(EntityValueAccess<E, F> field) {
			return new EntityConditionIntermediate1<>(this, field);
		}

		/**
		 * @param <F> The type of the field for the condition
		 * @param field A function calling the entity field to use to derive a value to compare against the condition value (specified
		 *        later). Field sequences are not supported using this method.
		 * @return An intermediate to select the type of the comparison
		 */
		public <F> EntityConditionIntermediate1<E, F> where(Function<? super E, F> field) {
			return where(getEntityType().getField(field));
		}

		/**
		 * @param <F> The type of the field for the condition
		 * @param init The function calling the entity field to serve as a starting point for the value access to use to derive a value to
		 *        compare against the condition value (specified later)
		 * @param field Creates a field sequence starting from the field (see {@link ObservableEntityFieldType#dot(Function)}
		 * @return An intermediate to select the type of the comparison
		 */
		public <I, F> EntityConditionIntermediate1<E, F> where(Function<? super E, I> init,
			Function<ObservableEntityFieldType<E, I>, EntityValueAccess<E, F>> field) {
			return where(//
				field.apply(//
					getEntityType().getField(init)));
		}

		/**
		 * @param id The ID of the entity to match
		 * @return A condition that matches only the entity with the given ID
		 */
		public EntityCondition<E> entity(EntityIdentity<? extends E> id) {
			EntityIdentity<E> id2 = getEntityType().fromSubId(id);
			EntityCondition<E> c = this;
			for (int i = 0; i < id2.getFields().keySize(); i++) {
				int idIndex = i;
				c = c.and(all -> all.where((ObservableEntityFieldType<E, Object>) id2.getEntityType().getIdentityFields().get(idIndex))
					.equal().value(id2.getFields().get(idIndex)));
			}
			return c;
		}

		/**
		 * @param entity The entity to match
		 * @return A condition that matches only the given entity
		 */
		public EntityCondition<E> entity(E entity) {
			return entity(getEntityType().observableEntity(entity).getId());
		}

		@Override
		public EntityCondition<E> getCondition(ObservableEntityFieldType<E, ?> field) {
			return null;
		}

		@Override
		public EntityCondition<E> test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			return null;
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

	/**
	 * Returned from {@link EntityCondition.All#where(EntityValueAccess)} to create a value-based condition
	 *
	 * @param <E> The type of entity this condition applies to
	 * @param <F> The type of the value the new condition will be based on
	 */
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

		/** @return The source condition */
		public All<E> getSource() {
			return theSource;
		}

		/** @return The field to use to derive a value from the entity to compare against the condition value (specified later) */
		public EntityValueAccess<E, F> getField() {
			return theField;
		}

		/**
		 * @param ltEqGt The type of the comparison to make:
		 *        <ul>
		 *        <li>&lt;0 for less than or less-than-or-equal</li>
		 *        <li>0 for equal or not-equal</li>
		 *        <li>&gt;0 for greater than or greater-than-or-equal</li>
		 *        </ul>
		 * @param withEqual True for less-than-or-equal, equal, or greater-than-or-equal. False for less than, not-equal, or greater than.
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> compare(int ltEqGt, boolean withEqual) {
			return new EntityConditionIntermediate2<>(this, ltEqGt, withEqual);
		}

		/**
		 * For making an equals comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> equal() {
			return compare(0, true);
		}

		/**
		 * For making an not-equals comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> notEqual() {
			return compare(0, false);
		}

		/**
		 * For making a less than comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> lessThan() {
			return compare(-1, false);
		}

		/**
		 * For making a less than or equal comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> lessThanOrEqual() {
			return compare(-1, true);
		}

		/**
		 * For making a greater than comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> greaterThan() {
			return compare(1, false);
		}

		/**
		 * For making a greater than or equal comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> greaterThanOrEqual() {
			return compare(1, true);
		}

		/**
		 * For making an equals comparison. Shortcut for {@link #equal()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> eq() {
			return equal();
		}

		/**
		 * For making an not-equals comparison. Shortcut for {@link #notEqual()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> neq() {
			return notEqual();
		}

		/**
		 * For making a less than comparison. Shortcut for {@link #lessThan()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> lt() {
			return lessThan();
		}

		/**
		 * For making a less than or equal comparison. Shortcut for {@link #lessThanOrEqual()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> lte() {
			return lessThanOrEqual();
		}

		/**
		 * For making a greater than comparison. Shortcut for {@link #greaterThan()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> gt() {
			return greaterThan();
		}

		/**
		 * For making a greater than or equal comparison. Shortcut for {@link #greaterThanOrEqual()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		public EntityConditionIntermediate2<E, F> gte() {
			return greaterThanOrEqual();
		}

		/**
		 * For checking for a null value
		 *
		 * @return A condition that passes when the entity-derived value is null
		 */
		public ValueCondition<E, F> NULL() {
			return compare(0, true).value(null);
		}

		/**
		 * For checking for a non-null value
		 *
		 * @return A condition that passes when the entity-derived value is not null
		 */
		public ValueCondition<E, F> notNull() {
			return compare(0, false).value(null);
		}

		/**
		 * For checking for a null value
		 *
		 * @param isNull True if the condition should pass when the value is null; false if it should pass for NOT NULL
		 * @return A condition that passes when the entity-derived value is or is not null
		 */
		public ValueCondition<E, F> NULL(boolean isNull) {
			return compare(0, isNull).value(null);
		}
	}

	/**
	 * Returned from most of the methods in {@link EntityCondition.EntityConditionIntermediate1} which is returned from
	 * {@link EntityCondition.All#where(EntityValueAccess)}. Allows specification of the value or variable to compare the entity-derived
	 * value against.
	 *
	 * @param <E> The type of entity this condition applies to
	 * @param <F> The type of the value the new condition will be based on
	 */
	public static class EntityConditionIntermediate2<E, F> {
		private final EntityConditionIntermediate1<E, F> thePrecursor;
		private final int theCompare;
		private final boolean isWithEqual;

		EntityConditionIntermediate2(EntityConditionIntermediate1<E, F> precursor, int compare, boolean withEqual) {
			thePrecursor = precursor;
			theCompare = compare;
			isWithEqual = withEqual;
		}

		/**
		 * @param value The literal value to compare the entity-derived value against
		 * @return The new condition
		 */
		public LiteralCondition<E, F> value(F value) {
			return thePrecursor.getSource().createLiteral(thePrecursor.getField(), value, theCompare, isWithEqual);
		}

		/**
		 * @param variableName The name of the variable whose satisfied value to compare the entity-derived value against. The variable will
		 *        be declared if one of the given name was not present in the source condition.
		 * @return The new condition
		 */
		public VariableCondition<E, F> variable(String variableName) {
			EntityOperationVariable<E> vbl = thePrecursor.getSource().getVariables().get(variableName);
			if (vbl == null)
				vbl = new EntityOperationVariable<>(thePrecursor.getField().getSourceEntity(), variableName);
			// TODO Check against the variable type
			return thePrecursor.getSource().createVariable(thePrecursor.getField(), vbl, theCompare, isWithEqual);
		}

		/**
		 * @param variableName The name of the variable whose satisfied value to compare the entity-derived value against
		 * @return The new condition
		 * @throws IllegalArgumentException If a variable with the given name already exists
		 */
		public VariableCondition<E, F> declareVariable(String variableName) throws IllegalArgumentException {
			if (thePrecursor.getSource().getGlobalVariables().containsKey(variableName))
				throw new IllegalArgumentException("A variable named " + variableName + " has already been declared");
			return thePrecursor.getSource().createVariable(thePrecursor.getField(), new EntityOperationVariable<>(null, variableName),
				theCompare, isWithEqual);
		}

		/**
		 * @param variableName The name of the variable whose satisfied value to compare the entity-derived value against
		 * @return The new condition
		 * @throws IllegalArgumentException If no variable with the given name exists
		 */
		public VariableCondition<E, F> reuseVariable(String variableName) {
			EntityOperationVariable<E> vbl = thePrecursor.getSource().getVariables().get(variableName);
			if (vbl == null)
				throw new IllegalArgumentException("A variable named " + variableName + " has not been declared");
			// TODO Check against the variable type
			return thePrecursor.getSource().createVariable(thePrecursor.getField(), vbl, theCompare, isWithEqual);
		}
	}

	/** The condition {@link ValueCondition#getSymbol() symbol} for equality, = */
	public static final char EQUALS = '=';
	/** The condition {@link ValueCondition#getSymbol() symbol} for inequality, ≠(\u2260) */
	public static final char NOT_EQUALS = '\u2260';
	/** The condition {@link ValueCondition#getSymbol() symbol} for less than, &lt; */
	public static final char LESS = '<';
	/** The condition {@link ValueCondition#getSymbol() symbol} for less than or equal, ≤(\u2264) */
	public static final char LESS_OR_EQUAL = '\u2264';
	/** The condition {@link ValueCondition#getSymbol() symbol} for greater than, &gt; */
	public static final char GREATER = '>';
	/** The condition {@link ValueCondition#getSymbol() symbol} for greater than or equal, ≥(\u2265) */
	public static final char GREATER_OR_EQUAL = '\u2264';

	/**
	 * A condition that tests the value of an entity's field against a {@link EntityCondition.LiteralCondition literal} or
	 * {@link EntityCondition.VariableCondition variable} value.
	 *
	 * @param <E> The entity type of the condition
	 * @param <F> The type of the field
	 */
	public static abstract class ValueCondition<E, F> extends EntityCondition<E> {
		private final EntityValueAccess<E, F> theField;
		private final int theComparison;
		private final boolean isWithEqual;

		/**
		 * @param entityType The entity type for the condition
		 * @param mechanism The selection mechanism to enable operations off of the condition
		 * @param field The field value to compare against
		 * @param comparison Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0)
		 * @param isWithEqual Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false)
		 * @param globalVars Global variables
		 * @param vars The variables for this condition specifically
		 */
		protected ValueCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism, EntityValueAccess<E, F> field,
			int comparison, boolean isWithEqual, Map<String, EntityOperationVariable<E>> globalVars,
			Map<String, EntityOperationVariable<E>> vars) {
			super(entityType, mechanism, globalVars, vars);
			theField = field;
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}

		/** @return The field to compare values of */
		public EntityValueAccess<E, F> getField() {
			return theField;
		}

		/** @return Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0) */
		public int getComparison() {
			return theComparison;
		}

		/** @return Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false) */
		public boolean isWithEqual() {
			return isWithEqual;
		}

		/**
		 * @return ≤(\u2264), &lt;, =, ≠(\u2260), ≥(\u2265), or &gt;
		 * @see EntityCondition#EQUALS
		 * @see EntityCondition#NOT_EQUALS
		 * @see EntityCondition#LESS
		 * @see EntityCondition#LESS_OR_EQUAL
		 * @see EntityCondition#GREATER
		 * @see EntityCondition#GREATER_OR_EQUAL
		 */
		public char getSymbol() {
			if (theComparison < 0) {
				if (isWithEqual)
					return LESS_OR_EQUAL;
				else
					return LESS;
			} else if (theComparison == 0) {
				if (isWithEqual)
					return EQUALS;
				else
					return NOT_EQUALS;
			} else {
				if (isWithEqual)
					return GREATER_OR_EQUAL;
				else
					return GREATER;
			}
		}

		/**
		 * @param varValues The variable values of the environment where this condition is being used
		 * @return The value to compare the entity-derived value against for this condition
		 */
		public abstract F getConditionValue(QuickMap<String, Object> varValues);

		/**
		 * @param other The value condition to compare to
		 * @return The comparison of this condition's value to the other's
		 */
		protected abstract int compareValue(ValueCondition<? super E, ? super F> other);

		@Override
		public EntityCondition<E> getCondition(ObservableEntityFieldType<E, ?> field) {
			if (theField.getFieldSequence().getFirst().equals(field))
				return this;
			return null;
		}

		@Override
		public EntityCondition<E> test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			F value1 = theField.getValue(entity);
			F value2 = getConditionValue(varValues);
			if (test(value1, value2))
				return null;
			else
				return this;
		}

		/**
		 * @param entityDerivedValue The value derived from the source entity via this condition's {@link #getField() field}
		 * @param conditionValue The value to compare against
		 * @return Whether this condition's test passes
		 */
		protected boolean test(F entityDerivedValue, F conditionValue) {
			int comp = getField().compare(entityDerivedValue, conditionValue);
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

	/**
	 * A condition that tests the value of an entity's field against a constant value
	 *
	 * @param <E> The entity type of the condition
	 * @param <F> The type of the field
	 */
	public static class LiteralCondition<E, F> extends ValueCondition<E, F> {
		private final F theValue;

		LiteralCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism, EntityValueAccess<E, F> field, F value,
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

		/** @return The value to compare against */
		public F getValue() {
			return theValue;
		}

		@Override
		public F getConditionValue(QuickMap<String, Object> varValues) {
			return getValue();
		}

		/**
		 * @param entityDerivedValue The value derived from the source entity by this condition's {@link #getField() field}
		 * @return Whether this condition would pass for the given entity-derived value
		 */
		public boolean test(F entityDerivedValue) {
			return test(entityDerivedValue, theValue);
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

	/**
	 * A condition that tests the value of an entity's field against a variable value
	 *
	 * @param <E> The entity type of the condition
	 * @param <F> The type of the field
	 */
	public static class VariableCondition<E, F> extends ValueCondition<E, F> {
		private final EntityOperationVariable<E> theVariable;

		VariableCondition(ObservableEntityType<E> entityType, SelectionMechanism<E> mechanism, EntityValueAccess<E, F> field,
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

		/** @return The variable to test against */
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
			if (!theVariable.getName().equals(otherV.theVariable.getName()))
				return false;
			if (getComparison() < 0) {
				if (otherV.getComparison() > 0)
					return false;
				else if (otherV.getComparison() < 0)
					return isWithEqual() || !otherV.isWithEqual();
				else
					return isWithEqual() && otherV.isWithEqual();
			} else if (getComparison() > 0) {
				if (otherV.getComparison() < 0)
					return false;
				else if (otherV.getComparison() > 0)
					return isWithEqual() || !otherV.isWithEqual();
				else
					return isWithEqual() && otherV.isWithEqual();
			} else
				return otherV.getComparison() == 0 && isWithEqual() == otherV.isWithEqual();
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

	/**
	 * Either an {@link EntityCondition.OrCondition OR} or an {@link EntityCondition.AndCondition AND} condition
	 *
	 * @param <E> The entity type of the condition
	 */
	public static abstract class CompositeCondition<E> extends EntityCondition<E> {
		private final List<EntityCondition<E>> theConditions;

		CompositeCondition(ObservableEntityType<E> entityType, EntityCondition<E>... conditions) {
			super(entityType, conditions[0].theMechanism, joinVars(conditions));
			theConditions = Collections.unmodifiableList(Arrays.asList(conditions));
		}

		CompositeCondition(ObservableEntityType<E> entityType, CompositeCondition<E> other, EntityCondition<E> addedCondition) {
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

		/** @return The component conditions of this composite */
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

	/**
	 * A condition that passes if any one of its component conditions does
	 *
	 * @param <E> The entity type of the condition
	 */
	public static class OrCondition<E> extends CompositeCondition<E> {
		OrCondition(ObservableEntityType<E> entityType, EntityCondition<E>... conditions) {
			super(entityType, conditions);
		}

		OrCondition(ObservableEntityType<E> entityType, OrCondition<E> other, EntityCondition<E> addedCondition) {
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
		public EntityCondition<E> getCondition(ObservableEntityFieldType<E, ?> field) {
			if (getConditions().size() == 1)
				return getConditions().get(0).getCondition(field);
			List<EntityCondition<E>> components = null;
			for (EntityCondition<E> condition : getConditions()) {
				EntityCondition<E> component = condition.getCondition(field);
				if (component == null)
					return null;
				if (components == null)
					components = new ArrayList<>(getConditions().size());
				components.add(component);
			}
			return new OrCondition<>(getEntityType(), components.toArray(new EntityCondition[components.size()]));
		}

		@Override
		public EntityCondition<E> test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			if (getConditions().size() == 1)
				return getConditions().get(0).test(entity, varValues);
			for (EntityCondition<E> condition : getConditions()) {
				if (condition.test(entity, varValues) == null)
					return null;
			}
			return this;
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

	/**
	 * A condition that passes if all of its component conditions do
	 *
	 * @param <E> The entity type of the condition
	 */
	public static class AndCondition<E> extends CompositeCondition<E> {
		AndCondition(ObservableEntityType<E> entityType, EntityCondition<E>... conditions) {
			super(entityType, conditions);
		}

		AndCondition(ObservableEntityType<E> entityType, AndCondition<E> other, EntityCondition<E> addedCondition) {
			super(entityType, other, addedCondition);
		}

		@Override
		protected int getConditionType() {
			return 11;
		}

		@Override
		public EntityCondition<E> getCondition(ObservableEntityFieldType<E, ?> field) {
			List<EntityCondition<E>> components = null;
			for (EntityCondition<E> condition : getConditions()) {
				EntityCondition<E> component = condition.getCondition(field);
				if (component != null) {
					if (components == null)
						components = new ArrayList<>(getConditions().size());
					components.add(component);
				}
			}
			if (components == null)
				return null;
			else if (components.size() == 1)
				return components.get(0);
			return new AndCondition<>(getEntityType(), components.toArray(new EntityCondition[components.size()]));
		}

		@Override
		public EntityCondition<E> test(ObservableEntity<? extends E> entity, QuickMap<String, Object> varValues) {
			for (EntityCondition<E> condition : getConditions()) {
				EntityCondition<E> broken = condition.test(entity, varValues);
				if (broken != null)
					return broken;
			}
			return null;
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
