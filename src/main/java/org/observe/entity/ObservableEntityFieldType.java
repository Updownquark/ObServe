package org.observe.entity;

import java.util.List;
import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityFieldType<E, F> extends EntityValueAccess<E, F> {
	ObservableEntityType<E> getEntityType();
	TypeToken<F> getFieldType();
	String getName();
	int getFieldIndex();
	int getIdIndex();

	List<? extends ObservableEntityFieldType<? super E, F>> getOverrides();
	List<FieldConstraint<E, F>> getConstraints();

	@Override
	default String canAccept(F value) {
		StringBuilder str = null;
		for (FieldConstraint<E, F> c : getConstraints()) {
			String msg = c.canAccept(value);
			if (msg != null) {
				if (str == null)
					str = new StringBuilder();
				else
					str.append("; ");
				str.append(msg);
			}
		}
		return str == null ? null : str.toString();
	}

	@Override
	default <T> EntityValueAccess<E, T> dot(Function<? super F, T> attr) {
		ObservableEntityType<F> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		ObservableEntityFieldType<F, T> lastField = target.getField(attr);
		return new EntityChainAccess<>(this, lastField);
	}

	@Override
	default TypeToken<F> getValueType() {
		return getFieldType();
	}

	@Override
	default F getValue(E entity) {
		return (F) getEntityType().observableEntity(entity).get(getFieldIndex());
	}
}
