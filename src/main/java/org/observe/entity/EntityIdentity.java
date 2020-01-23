package org.observe.entity;

import java.util.Objects;

import org.qommons.collect.QuickSet.QuickMap;

public class EntityIdentity<E> {
	private final ObservableEntityType<E> theEntityType;
	final QuickMap<String, Object> theFields;

	private EntityIdentity(ObservableEntityType<E> entityType, Object[] fieldValues) {
		theEntityType = entityType;
		QuickMap<String, Object> fieldMap = entityType.getIdentityFields().keySet().createMap();
		for (int i = 0; i < fieldValues.length; i++)
			fieldMap.put(i, fieldValues[i]);
		theFields = fieldMap.unmodifiable();
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	public QuickMap<String, Object> getFields() {
		return theFields;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (int i = 0; i < theFields.keySet().size(); i++) {
			if (i != 0)
				hash = hash * 31;
			hash += Objects.hashCode(theFields.get(i));
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof EntityIdentity))
			return false;
		EntityIdentity<?> other = (EntityIdentity<?>) obj;
		if (!theEntityType.equals(theEntityType))
			return false;
		for (int i = 0; i < theFields.keySet().size(); i++)
			if (!Objects.equals(theFields.get(i), other.theFields.get(i)))
				return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append(theEntityType).append('(');
		for (int i = 0; i < theFields.keySet().size(); i++) {
			if (i > 0)
				str.append(',');
			str.append(theEntityType.getFields().get(i).getName()).append('=').append(theFields.get(i));
		}
		str.append(')');
		return str.toString();
	}
}
