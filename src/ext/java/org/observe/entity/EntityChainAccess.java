package org.observe.entity;

import java.util.Iterator;
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
				if (entity.getFields().get(fields[f].getIndex()) != fields[f])
					throw new IllegalArgumentException("Bad field sequence--" + fields[f] + " does not follow " + fields[f - 1]);
			}
			entity = fields[f].getTargetEntity();
			if (f < fields.length - 1 && entity == null)
				throw new IllegalArgumentException("Bad field sequence--intermediate field does not target an entity: " + fields[f]);
		}
		theFieldSequence = BetterList.of(fields);
	}

	@Override
	public BetterList<ObservableEntityFieldType<?, ?>> getFieldSequence() {
		return theFieldSequence;
	}

	@Override
	public ObservableEntityType<E> getSourceEntity() {
		return ((ObservableEntityFieldType<E, ?>) theFieldSequence.getFirst()).getOwnerType();
	}

	@Override
	public TypeToken<T> getValueType() {
		return (TypeToken<T>) theFieldSequence.getLast().getOwnerType();
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
	public boolean isOverride(EntityValueAccess<? extends E, ?> field) {
		if (!(field instanceof EntityChainAccess))
			return false;
		EntityChainAccess<?, ?> other = (EntityChainAccess<?, ?>) field;
		if (theFieldSequence.size() != other.theFieldSequence.size())
			return false;
		for (int f = 0; f < theFieldSequence.size(); f++)
			if (!((ObservableEntityFieldType<E, ?>) theFieldSequence.get(f)).isOverride(//
				(ObservableEntityFieldType<E, ?>) other.theFieldSequence.get(f)))
				return false;
		return true;
	}

	@Override
	public <T2> EntityChainAccess<E, T2> dot(Function<? super T, T2> attr) {
		ObservableEntityType<T> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		ObservableEntityFieldType<T, T2> lastField = target.getField(attr);
		return dot(lastField);
	}

	@Override
	public <T2> EntityChainAccess<E, T2> dot(ObservableEntityFieldType<? super T, T2> field) {
		ObservableEntityType<T> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("This method can only be used with entity-typed fields");
		else if (!field.getOwnerType().isAssignableFrom(target))
			throw new IllegalArgumentException(field + " cannot be applied to " + target);
		else if (!field.getOwnerType().equals(target))
			field = (ObservableEntityFieldType<T, T2>) target.getFields().get(field.getName());
		ObservableEntityFieldType<?, ?>[] fields = new ObservableEntityFieldType[theFieldSequence.size() + 1];
		theFieldSequence.toArray(fields);
		fields[fields.length - 1] = field;
		return new EntityChainAccess<>(fields);
	}

	@Override
	public T get(E entity) {
		Object value = entity;
		for (ObservableEntityFieldType<?, ?> field : theFieldSequence)
			value = ((ObservableEntityFieldType<Object, Object>) field).get(value);
		return (T) value;
	}

	@Override
	public T getValue(ObservableEntity<? extends E> entity) {
		Object value = entity;
		for (ObservableEntityFieldType<?, ?> field : theFieldSequence)
			value = ((ObservableEntityFieldType<Object, Object>) field).get(value);
		return (T) value;
	}

	@Override
	public void setValue(ObservableEntity<? extends E> entity, T value) {
		Object target = entity;
		Iterator<ObservableEntityFieldType<?, ?>> fieldIter = theFieldSequence.iterator();
		ObservableEntityFieldType<?, ?> lastField = null;
		while (fieldIter.hasNext()) {
			lastField = fieldIter.next();
			if (fieldIter.hasNext())
				target = ((ObservableEntityFieldType<Object, Object>) lastField).get(target);
			else
				break;
		}
		((ObservableEntityFieldType<Object, T>) lastField).set(target, value);
	}

	@Override
	public int compare(T o1, T o2) {
		if (o1 == o2)
			return 0;
		Object v1 = o1;
		Object v2 = o2;
		for (ObservableEntityFieldType<?, ?> field : theFieldSequence) {
			ObservableEntityFieldType<Object, Object> f = (ObservableEntityFieldType<Object, Object>) field;
			v1 = f.get(v1);
			v2 = f.get(v2);
			if (f.compare(v1, v2) == 0)
				return 0;
		}
		return 0;
	}

	@Override
	public int compareTo(EntityValueAccess<E, ?> o) {
		if (!(o instanceof EntityChainAccess))
			return 1;
		EntityChainAccess<E, ?> other = (EntityChainAccess<E, ?>) o;
		for (int f = 0; f < theFieldSequence.size() && f < other.theFieldSequence.size(); f++) {
			int comp = ((ObservableEntityFieldType<E, ?>) theFieldSequence.get(f)).compareTo(//
				(ObservableEntityFieldType<E, ?>) other.theFieldSequence.get(f));
			if (comp != 0)
				return comp;
		}
		return Integer.compare(theFieldSequence.size(), other.theFieldSequence.size());
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
		StringBuilder str = new StringBuilder(theFieldSequence.getFirst().getOwnerType().getName());
		for (ObservableEntityFieldType<?, ?> field : theFieldSequence)
			str.append('.').append(field.getName());
		return str.toString();
	}
}
