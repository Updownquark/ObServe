package org.observe.entity.impl;

import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCreationResult;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldEvent;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.qommons.ValueHolder;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.QuickSet.QuickMap;

interface EntityCreatorHelper<E, E2 extends E> extends EntityCreator<E, E2> {
	QueryResults<E> getQuery();

	@Override
	default CollectionElement<E> create(Consumer<? super E2> preAddAction) throws EntityOperationException {
		ValueHolder<ObservableEntity<E2>> entity = getQuery() == null ? null : new ValueHolder<>();
		create(true, null, entity);
		if (getQuery() == null)
			return null;
		return new EntityElement<>(getQuery().getResults().getElement(entity.get(), true));
	}

	class EntityElement<E> implements CollectionElement<E> {
		private final CollectionElement<ObservableEntity<? extends E>> theElement;

		EntityElement(CollectionElement<ObservableEntity<? extends E>> element) {
			theElement = element;
		}

		@Override
		public ElementId getElementId() {
			return theElement.getElementId();
		}

		@Override
		public E get() {
			return theElement.get().getEntity();
		}

		@Override
		public int hashCode() {
			return getElementId().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof CollectionElement && getElementId().equals(((CollectionElement<?>) obj).getElementId());
		}

		@Override
		public String toString() {
			return get().toString();
		}
	}

	@Override
	default String canCreate() {
		// TODO Constraint checks
		if (getQuery() != null) {
			ObservableEntity<E2> entity = new PotentialEntity<>(this);
			EntityCondition<E> broken = getQuery().getSelection().test(entity, QuickMap.empty());
			if (broken != null)
				return QUERY_CONDITION_UNMATCHED + ": " + broken;
		}
		return null;
	}

	class PotentialEntity<E> implements ObservableEntity<E> {
		private final EntityCreatorHelper<?, E> theCreator;

		PotentialEntity(EntityCreatorHelper<?, E> creator) {
			theCreator = creator;
		}

		@Override
		public ObservableEntityType<E> getType() {
			return theCreator.getType();
		}

		@Override
		public Object get(int fieldIndex) {
			ConfigurableCreator<?, E> configCreator;
			PreparedCreator<?, E> preparedCreator;
			if (theCreator instanceof ConfigurableCreator) {
				configCreator = (ConfigurableCreator<?, E>) theCreator;
				preparedCreator = null;
			} else {
				preparedCreator = (PreparedCreator<?, E>) theCreator;
				configCreator = preparedCreator.getDefinition();
			}

			EntityOperationVariable<E> vbl = configCreator.getFieldVariables().get(fieldIndex);
			if (vbl != null)
				return preparedCreator.getVariableValues().get(vbl.getName());
			return configCreator.getFieldValues().get(fieldIndex);
		}

		@Override
		public ObservableEntity<?> getEntity(int fieldIndex) {
			if (theCreator.getEntityType().getFields().get(fieldIndex).getTargetEntity() == null)
				throw new IllegalArgumentException(
					theCreator.getEntityType().getFields().get(fieldIndex) + " is not an entity-typed field");
			Object v = get(fieldIndex);
			if (v != null && !(v instanceof ObservableEntity))
				v = ((ObservableEntityType<Object>) theCreator.getEntityType().getFields().get(fieldIndex).getTargetEntity())
				.observableEntity(v);
			return (ObservableEntity<?>) v;
		}

		@Override
		public long getStamp() {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public E getEntity() {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public EntityIdentity<E> getId() {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public String isAcceptable(int fieldIndex, Object value) {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public <F> F set(int fieldIndex, F value, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public <F> ObservableEntity<F> setEntity(int fieldIndex, ObservableEntity<F> value, Object cause)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public EntityModificationResult<E> update(int fieldIndex, Object value, boolean sync, Object cause)
			throws IllegalArgumentException, UnsupportedOperationException, EntityOperationException {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public Observable<ObservableEntityFieldEvent<E, ?>> allFieldChanges() {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public boolean isLoaded(ObservableEntityFieldType<? super E, ?> field) {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public <F> ObservableEntity<E> load(ObservableEntityFieldType<E, F> field, Consumer<? super F> onLoad,
			Consumer<EntityOperationException> onFail) throws EntityOperationException {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public boolean isPresent() {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public String canDelete() {
			throw new IllegalStateException("Should not be called from condition test");
		}

		@Override
		public void delete(Object cause) throws UnsupportedOperationException {
			throw new IllegalStateException("Should not be called from condition test");
		}
	}

	@Override
	default EntityCreationResult<E2> createAsync(Consumer<? super E2> preAddAction) {
		try {
			return ((ObservableEntityTypeImpl<E2>) getEntityType()).getEntitySet().create(this, false, null, //
				preAddAction == null ? null : entity -> preAddAction.accept(entity.getEntity()));
		} catch (EntityOperationException e) {
			throw new IllegalStateException("Should not have thrown inline", e);
		}
	}

	@Override
	default EntityCreationResult<E2> create(boolean sync, Object cause, Consumer<? super ObservableEntity<E2>> preAdd)
		throws IllegalStateException, EntityOperationException {
		String canCreate = canCreate();
		if (canCreate != null)
			throw new EntityOperationException(canCreate);
		return ((ObservableEntityTypeImpl<E2>) getEntityType()).getEntitySet().create(this, sync, cause, preAdd);
	}
}
