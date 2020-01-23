package org.observe.entity;

import java.util.function.Function;

import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

/**
 * A compound {@link EntityValueAccess}
 * 
 * @param <E> The entity type
 * @param <T> The information type
 */
public class EntityChainAccess<E, T> implements EntityValueAccess<E, T> {
	private final BetterList<ObservableEntityFieldType<?, ?>> theFieldSequence;

	<F> EntityChainAccess(ObservableEntityFieldType<E, F> firstField, ObservableEntityFieldType<F, T> secondField) {
		this(new ObservableEntityFieldType[] { firstField, secondField });
	}

	EntityChainAccess(ObservableEntityFieldType<?, ?>[] fields) {
		ObservableEntityType<?> entity = null;
		for (int f = 0; f < fields.length; f++) {
			if (f > 0) {
				if (entity.getFields().get(fields[f].getFieldIndex()) != fields[f])
					throw new IllegalArgumentException("Bad field sequence--" + fields[f] + " does not follow " + fields[f - 1]);
			}
			entity = fields[f].getTargetEntity();
			if (f < fields.length - 1 && entity == null)
				throw new IllegalArgumentException("Bad field sequence--intermediate field does not target an entity: " + fields[f]);
		}
		theFieldSequence = BetterList.of(fields);
	}

	/** @return The sequence of fields that this value access obtains information through */
	public BetterList<ObservableEntityFieldType<?, ?>> getFieldSequence() {
		return theFieldSequence;
	}

	@Override
	public TypeToken<T> getValueType() {
		return (TypeToken<T>) theFieldSequence.getLast().getValueType();
	}

	@Override
	public ObservableEntityType<T> getTargetEntity() {
		return (ObservableEntityType<T>) theFieldSequence.getLast().getTargetEntity();
	}

	@Override
	public String canAccept(T value) {
		return ((ObservableEntityFieldType<?, T>) theFieldSequence.getLast()).canAccept(value);
	}

	@Override
	public <T2> EntityValueAccess<E, T2> dot(Function<? super T, T2> attr) {
		ObservableEntityType<T> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		ObservableEntityFieldType<T, T2> lastField = target.getField(attr);
		ObservableEntityFieldType<?, ?>[] fields = new ObservableEntityFieldType[theFieldSequence.size() + 1];
		theFieldSequence.toArray(fields);
		fields[fields.length - 1] = lastField;
		return new EntityChainAccess<>(fields);
	}

	@Override
	public T getValue(E entity) {
		Object value = entity;
		for (ObservableEntityFieldType<?, ?> field : theFieldSequence)
			value = ((ObservableEntityFieldType<Object, Object>) field).getValue(entity);
		return (T) value;
	}

	@Override
	public int hashCode() {
		return theFieldSequence.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		else if (!(o instanceof EntityChainAccess))
			return false;
		return theFieldSequence.equals(((EntityChainAccess<?, ?>) o).theFieldSequence);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(theFieldSequence.getFirst().getEntityType().getName());
		for (ObservableEntityFieldType<?, ?> field : theFieldSequence)
			str.append('.').append(field.getName());
		return str.toString();
	}
}
