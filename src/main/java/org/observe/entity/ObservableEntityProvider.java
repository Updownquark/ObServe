package org.observe.entity;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.observe.Observable;
import org.qommons.Transactable;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * <p>
 * An implementation data source to provide an ObservableEntityDataSet with entity data and possibly the power to execute changes to the
 * data set.
 * </p>
 * <p>
 * The Transactable interface does not necessarily allow the locking of the data source to the exclusion of other users of it. It only means
 * that no {@link #changes() changes} will fire while it is locked. Changes occurring in the data source during a lock hold will be fired
 * when the lock is released.
 * </p>
 */
public interface ObservableEntityProvider extends Transactable {
	public class SimpleEntity<E> {
		private final EntityIdentity<E> theIdentity;
		private final QuickMap<String, Object> theFields;

		public SimpleEntity(EntityIdentity<E> identity, QuickMap<String, Object> fields) {
			theIdentity = identity;
			theFields = fields;
		}

		public EntityIdentity<E> getIdentity() {
			return theIdentity;
		}

		public QuickMap<String, Object> getFields() {
			return theFields;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theIdentity.toString());
			boolean firstField = true;
			for (int f = 0; f < theFields.keySize(); f++) {
				if (theFields.get(f) == EntityUpdate.NOT_SET || theIdentity.getEntityType().getFields().get(f).getIdIndex() >= 0)
					continue;
				if (firstField) {
					firstField = false;
					str.append('{');
				} else
					str.append(", ");
				str.append(theIdentity.getEntityType().getFields().get(f).getName()).append('=').append(theFields.get(f));
			}
			if (!firstField)
				str.append('}');
			return str.toString();
		}
	}

	void install(ObservableEntityDataSet entitySet) throws IllegalStateException;

	Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException;

	<E> SimpleEntity<E> create(EntityCreator<E> creator, Object prepared, //
		Consumer<SimpleEntity<E>> identityFieldsOnAsyncComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException;

	long count(EntityQuery<?> query, Object prepared, //
		LongConsumer onAsyncComplete, Consumer<EntityOperationException> onError) throws EntityOperationException;

	<E> Iterable<SimpleEntity<? extends E>> query(EntityQuery<E> query, Object prepared,
		Consumer<Iterable<SimpleEntity<? extends E>>> onAsyncComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException;

	<E> long update(EntityUpdate<E> update, Object prepared, //
		LongConsumer onAsyncComplete, Consumer<EntityOperationException> onError) throws EntityOperationException;

	<E> long delete(EntityDeletion<E> delete, Object prepared, //
		LongConsumer onAsyncComplete, Consumer<EntityOperationException> onError) throws EntityOperationException;

	/** @return An observable that fires a change whenever the entity data is changed */
	Observable<List<EntityChange<?>>> changes();

	List<EntityLoadRequest.Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests, //
		Consumer<List<EntityLoadRequest.Fulfillment<?>>> onComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException;
}
