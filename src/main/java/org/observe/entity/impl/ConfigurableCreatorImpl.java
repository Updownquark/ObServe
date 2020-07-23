package org.observe.entity.impl;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.observe.config.ConfiguredValueField;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.observe.util.TypeTokens;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet.QuickMap;

class ConfigurableCreatorImpl<E, E2 extends E> extends AbstractConfigurableOperation<E2>
implements ConfigurableCreator<E, E2>, EntityCreatorHelper<E, E2> {
	public static final String FIXED_BY_CONDITION = "Field is fixed by query condition";
	public static final String CANT_CREATE_HAS_VARIABLES = "Use prepare() to satisfy variable values";

	private final QuickMap<String, Object> theFieldValues;
	private final QuickMap<String, EntityOperationVariable<E2>> theFieldVariables;
	private final QueryResults<E> theQuery;
	private final Set<Integer> theRequiredFields;

	ConfigurableCreatorImpl(ObservableEntityType<E2> entityType, boolean reportInChanges,
		QuickMap<String, EntityOperationVariable<E2>> variables, QuickMap<String, Object> fieldValues,
		QuickMap<String, EntityOperationVariable<E2>> fieldVariables, QueryResults<E> query) {
		super(entityType, reportInChanges, variables);
		theFieldValues = fieldValues;
		theFieldVariables = fieldVariables;
		theQuery = query;
		Set<Integer> requiredFields = new TreeSet<>();
		// TODO Required fields should include ID and not-null fields for which auto-generation is not enabled
		// If query!=null,
		// * should exclude field values which are constrained to a particular value by the query's condition
		// * should include field values which are referred to in the query's condition but not constrained to a particular value
		theRequiredFields = Collections.unmodifiableSet(requiredFields);
		// TODO If query!=null, fill field values with specifications from query conditions
	}

	@Override
	public QueryResults<E> getQuery() {
		return theQuery;
	}

	@Override
	public QuickMap<String, Object> getFieldValues() {
		return theFieldValues;
	}

	@Override
	public QuickMap<String, EntityOperationVariable<E2>> getFieldVariables() {
		return theFieldVariables;
	}

	@Override
	public <F> ConfigurableCreator<E, E2> withField(ObservableEntityFieldType<? super E2, F> field, F value) {
		if (getEntityType().getFields().get(field.getIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		String acceptable = isAcceptable(field, value);
		if (acceptable != null)
			throw new IllegalArgumentException(acceptable);
		QuickMap<String, Object> copy = theFieldValues.copy();
		copy.put(field.getIndex(), value);
		return new ConfigurableCreatorImpl<>(getEntityType(), isReportInChanges(), getVariables(), copy.unmodifiable(), theFieldVariables,
			theQuery);
	}

	@Override
	public ConfigurableCreator<E, E2> withVariable(ObservableEntityFieldType<? super E2, ?> field, String variableName) {
		if (getEntityType().getFields().get(field.getIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		QuickMap<String, EntityOperationVariable<E2>> variables = getOrAddVariable(variableName);
		QuickMap<String, EntityOperationVariable<E2>> fieldVariables = theFieldVariables.copy();
		fieldVariables.put(field.getIndex(), variables.get(variableName));
		return new ConfigurableCreatorImpl<>(getEntityType(), isReportInChanges(), variables, theFieldValues, fieldVariables.unmodifiable(),
			theQuery);
	}

	@Override
	public Set<Integer> getRequiredFields() {
		return theRequiredFields;
	}

	@Override
	public ConfigurableCreator<E, E2> after(ElementId after) {
		// Though theoretically one should be able to determine where the new value will show up
		// and throw an exception if the given position cannot be guaranteed,
		// this is complicated in that field values which may affect the order can be set after this call
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ConfigurableCreator<E, E2> before(ElementId before) {
		// Though theoretically one should be able to determine where the new value will show up
		// and throw an exception if the given position cannot be guaranteed,
		// this is complicated in that field values which may affect the order can be set after this call
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ConfigurableCreator<E, E2> towardBeginning(boolean towardBeginning) {
		return this; // This is just a suggestion in the BetterCollection API, so no need to throw an exception
	}

	@Override
	public String isEnabled(ConfiguredValueField<? super E2, ?> field) {
		if (theQuery != null) {
			ObservableEntityFieldType<E, ?> superField = theQuery.getOperation().getEntityType().getFields().getIfPresent(field.getName());
			if (superField == null)
				return null; // Field is for the sub-type, meaning the super-typed query can't discriminate on it
			EntityCondition<E> condition = theQuery.getSelection().getCondition(superField);
			if (condition == null)
				return null; // No constraints in the query condition
			if (condition instanceof EntityCondition.ValueCondition//
				&& ((EntityCondition.ValueCondition<E, ?>) condition).getSymbol() == EntityCondition.EQUALS)
				return "Field is fixed by query condition";
		}
		return null;
	}

	@Override
	public <F> String isAcceptable(ConfiguredValueField<? super E2, F> field, F value) {
		if (!TypeTokens.get().isInstance(field.getFieldType(), value))
			return StdMsg.BAD_TYPE;
		// TODO Type/constraint check
		if (theQuery != null) {
			ObservableEntityFieldType<E, ?> superField = theQuery.getOperation().getEntityType().getFields().getIfPresent(field.getName());
			if (superField == null)
				return null; // Field is for the sub-type, meaning the super-typed query can't discriminate on it
			EntityCondition<E> condition = theQuery.getSelection().getCondition(superField);
			if (condition == null)
				return null; // No constraints in the query condition
			if (condition instanceof EntityCondition.ValueCondition) {
				if (((EntityCondition.ValueCondition<E, ?>) condition).getSymbol() == EntityCondition.EQUALS)
					return FIXED_BY_CONDITION;
			}
		}
		return null;
	}

	@Override
	public String canCreate() {
		if (theFieldVariables != null && theFieldVariables.valueCount() > 0)
			return CANT_CREATE_HAS_VARIABLES;
		return EntityCreatorHelper.super.canCreate();
	}

	@Override
	public PreparedCreator<E, E2> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedCreatorImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this),
			getVariables().keySet().createMap(), theQuery);
	}
}
