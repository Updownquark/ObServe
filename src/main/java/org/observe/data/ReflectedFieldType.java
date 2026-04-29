package org.observe.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.config.ConfiguredValueField;
import org.observe.data.ReflectedEntityValueType.RealFieldValueProducer;
import org.observe.util.EntityReflector;
import org.qommons.QommonsUtils;
import org.qommons.data.types.EntityField;

import com.google.common.reflect.TypeToken;

public class ReflectedFieldType<E, G, R> implements ConfiguredValueField<E, R> {
	private final ReflectedEntityValueType<E> theOwner;
	private final EntityField<G> theField;
	private final EntityReflector.ReflectedField<E, R> theReflector;
	private final List<ReflectedFieldType<? super E, G, R>> theOverrides;
	private final RealFieldValueProducer<G, R> theRealMapping;
	private final Function<R, G> theGenericMapping;

	ReflectedFieldType(ReflectedEntityValueType<E> owner, EntityField<G> field) {
		theOwner = owner;
		theField = field;
		theReflector = (EntityReflector.ReflectedField<E, R>) owner.getReflector().getFields().get(owner.getGenericType().indexOf(field));
		List<ReflectedFieldType<? super E, G, R>> overrides = Collections.emptyList();
		for (ReflectedEntityValueType<? super E> superType : theOwner.getSupers()) {
			ReflectedFieldType<?, ?, ?> override = superType.getFields().get(theReflector.getName());
			if (override != null) {
				if (overrides.isEmpty())
					overrides = new ArrayList<>();
				overrides.add((ReflectedFieldType<? super E, G, R>) override);
			}
		}
		theOverrides = QommonsUtils.unmodifiableCopy(overrides);
		theRealMapping = theOwner.mapGenericToReal(theField.getType(), theReflector.getType(), theField);
		theGenericMapping = theOwner.mapRealToGeneric(theReflector.getType(), theField.getType());
	}

	@Override
	public ReflectedEntityValueType<E> getOwnerType() {
		return theOwner;
	}

	public EntityField<G> getGenericField() {
		return theField;
	}

	/** @return A function that creates values for the reflected entity field from generic field values */
	public RealFieldValueProducer<G, R> getRealMapping() {
		return theRealMapping;
	}

	/** @return A function that creates values generic field from reflected entity field values. This will be null for */
	public Function<R, G> getGenericMapping() {
		return theGenericMapping;
	}

	@Override
	public List<? extends ReflectedFieldType<? super E, G, R>> getOverrides() {
		return theOverrides;
	}

	@Override
	public String getName() {
		return theField.getName();
	}

	@Override
	public TypeToken<R> getFieldType() {
		return theReflector.getType();
	}

	@Override
	public int getIndex() {
		return theReflector.getFieldIndex();
	}

	@Override
	public R get(E entity) {
		MappedEntity<E> backing = (MappedEntity<E>) EntityReflector.getAssociated(entity, MappedEntity.ENTITY_ASSOC);
		if (backing != null)
			return (R) backing.get(theReflector.getFieldIndex());
		else
			return theReflector.get(entity);
	}

	@Override
	public boolean isSettable(E entity) {
		return ((MappedEntity<E>) EntityReflector.getAssociated(entity, MappedEntity.ENTITY_ASSOC))
			.isEnabled(theReflector.getFieldIndex()) == null;
	}

	@Override
	public void set(E entity, R fieldValue) throws UnsupportedOperationException, IllegalArgumentException {
		((MappedEntity<E>) EntityReflector.getAssociated(entity, MappedEntity.ENTITY_ASSOC)).set(theReflector.getFieldIndex(), fieldValue);
	}

	@Override
	public String toString() {
		return theField.toString();
	}
}
