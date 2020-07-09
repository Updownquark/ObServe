package org.observe.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.observe.config.ObservableValueSet;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiMap;
import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

/**
 * <p>
 * An implementation data source to provide an ObservableEntityDataSet with entity data and possibly the power to execute changes to the
 * data set.
 * </p>
 */
public interface ObservableEntityProvider {
	public enum CollectionOperationType {
		add, remove, update, clear;
	}

	public class SimpleEntity<E> {
		private final EntityIdentity<E> theIdentity;
		private final QuickMap<String, Object> theFields;

		public SimpleEntity(EntityIdentity<E> identity) {
			theIdentity = identity;
			theFields = identity.getEntityType().getFields().keySet().createMap().fill(EntityUpdate.NOT_SET);
		}

		public EntityIdentity<E> getIdentity() {
			return theIdentity;
		}

		public QuickMap<String, Object> getFields() {
			return theFields.unmodifiable();
		}

		public void set(int fieldIndex, Object value) throws IllegalArgumentException {
			ObservableEntityFieldType<E, ?> field = theIdentity.getEntityType().getFields().get(fieldIndex);
			String msg = checkType(field.getFieldType(), value, field);
			if (msg == null)
				throw new IllegalArgumentException("Illegal value for field " + field + " (type " + field.getFieldType() + "): " + msg);
			else
				theFields.put(fieldIndex, value);
		}

		private String checkType(TypeToken<?> type, Object value, ObservableEntityFieldType<E, ?> field) {
			if (value == null)
				return field == null ? null : field.canAccept(null);
			Class<?> raw = TypeTokens.getRawType(type);
			ObservableEntityType<?> target = theIdentity.getEntityType().getEntitySet().getEntityType(raw);
			if (target != null) {
				ObservableEntityType<?> fieldEntityType;
				if (value instanceof EntityIdentity)
					fieldEntityType = ((EntityIdentity<?>) value).getEntityType();
				else if (value instanceof SimpleEntity)
					fieldEntityType = ((SimpleEntity<?>) value).getIdentity().getEntityType();
				else {
					fieldEntityType = null;
					return "Entity field must be assigned a value of type "//
						+ EntityIdentity.class.getSimpleName() + " or " + SimpleEntity.class.getSimpleName()//
						+ ", not " + value.getClass().getName();
				}
				if (fieldEntityType != null && !target.isAssignableFrom(fieldEntityType))
					return "Entity of type " + fieldEntityType.getName() + " is not valid for field of type " + target.getName();
			} else if (Collection.class.isAssignableFrom(raw)) {
				if (!(value instanceof BetterCollection))
					return "Collection-type fields must be supplied with an instance of " + BetterCollection.class.getName();
				TypeToken<?> elementType = type.resolveType(Collection.class.getTypeParameters()[0]);
				int index = 0;
				for (Object element : ((BetterCollection<?>) value)) {
					String msg = checkType(elementType, element, null);
					if (msg != null)
						return "Element[" + index + "]: " + msg;
					index++;
				}
			} else if (ObservableValueSet.class.isAssignableFrom(raw)) {
				if (!(value instanceof BetterCollection))
					return ObservableValueSet.class.getSimpleName() + "-type fields must be supplied with an instance of "
					+ BetterCollection.class.getName();
				TypeToken<?> elementType = type.resolveType(ObservableValueSet.class.getTypeParameters()[0]);
				int index = 0;
				for (Object element : ((BetterCollection<?>) value)) {
					String msg = checkType(elementType, element, null);
					if (msg != null)
						return "Element[" + index + "]: " + msg;
					index++;
				}
			} else if (Map.class.isAssignableFrom(raw)) {
				if (!(value instanceof Map))
					return "Map-type fields must be supplied with an instance of " + Map.class.getName();
				TypeToken<?> keyType = type.resolveType(Map.class.getTypeParameters()[0]);
				TypeToken<?> valueType = type.resolveType(Map.class.getTypeParameters()[1]);
				int index = 0;
				for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
					String msg = checkType(keyType, entry.getKey(), null);
					if (msg != null)
						return "Key[" + index + "]: " + msg;
					msg = checkType(valueType, entry.getValue(), null);
					if (msg != null)
						return "Value@" + entry.getKey() + "[" + index + "]: " + msg;
					index++;
				}
			} else if (MultiMap.class.isAssignableFrom(raw)) {
				if (!(value instanceof MultiMap))
					return "MultiMap-type fields must be supplied with an instance of " + MultiMap.class.getName();
				TypeToken<?> keyType = type.resolveType(MultiMap.class.getTypeParameters()[0]);
				TypeToken<?> valueType = type.resolveType(MultiMap.class.getTypeParameters()[1]);
				int keyIndex = 0;
				for (MultiMap.MultiEntry<?, ?> entry : ((MultiMap<?, ?>) value).entrySet()) {
					String msg = checkType(keyType, entry.getKey(), null);
					if (msg != null)
						return "Key[" + keyIndex + "]: " + msg;
					int valueIndex = 0;
					for (Object entryVvalue : entry.getValues()) {
						msg = checkType(valueType, entryVvalue, null);
						if (msg != null)
							return "Value@" + entry.getKey() + "[" + keyIndex + ", " + valueIndex + "]: " + msg;
						valueIndex++;
					}
					keyIndex++;
				}
			} else if (field != null) {
				return ((ObservableEntityFieldType<E, Object>) field).canAccept(value);
			}
			return null;
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

	<E> SimpleEntity<E> create(EntityCreator<? super E, E> creator, Object prepared, //
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

	<V> ElementId updateCollection(BetterCollection<V> collection, CollectionOperationType changeType, ElementId element, V value,
		Consumer<ElementId> asyncResult);

	<K, V> ElementId updateMap(Map<K, V> collection, CollectionOperationType changeType, K key, V value, Runnable asyncResult);

	<K, V> ElementId updateMultiMap(MultiMap<K, V> collection, CollectionOperationType changeType, ElementId valueElement, K key,
		V value, Consumer<ElementId> asyncResult);

	/** @return All changes to the entity data (including those resulting from calls to this provider) since the previous invocation */
	List<EntityChange<?>> changes();

	List<EntityLoadRequest.Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests, //
		Consumer<List<EntityLoadRequest.Fulfillment<?>>> onComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException;
}
