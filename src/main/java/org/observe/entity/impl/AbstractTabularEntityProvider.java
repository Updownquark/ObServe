package org.observe.entity.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.config.OperationResult;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.qommons.Named;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiMap;
import org.qommons.collect.QuickSet.QuickMap;

public abstract class AbstractTabularEntityProvider<//
C extends AbstractTabularEntityProvider.TableColumn, //
T extends AbstractTabularEntityProvider.Table<C>> implements ObservableEntityProvider {

	public interface Table<C extends TableColumn> extends Named {
		QuickMap<String, C> getColumns();
	}

	public interface TableColumn extends Named {}

	public static class EntityTable<C extends TableColumn, T extends Table<C>, E> {
		private final ObservableEntityType<E> theType;
		private final QuickMap<String, TableField<C, T, ? super E, ?>> theFields;
	}

	public interface TableField<C extends TableColumn, T extends Table<C>, E, F> {
		EntityTable<C, T, E> getTable();

		ObservableEntityFieldType<E, F> getField();

	}

	public interface TableOperation<C extends TableColumn, T extends Table<C>> {

	}

	public interface TableQuery<C extends TableColumn, T extends Table<C>> {
		T getTable();

		TableQuery<C, T> addColumns(TableColumn... columns);

		TableQuery<C, T> join(Table table, TableColumn... joinColumns);
	}

	public interface TableInsert<C extends TableColumn, T extends Table<C>> {
		T getTable();

		// TableInsert<C, T> with(
	}

	public interface TableUpdate<C extends TableColumn, T extends Table<C>> {
		T getTable();

	}

	public interface TableDelete<C extends TableColumn, T extends Table<C>> {
		T getTable();

	}

	public interface TableCreate<C extends TableColumn, T extends Table<C>> {

	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws EntityOperationException {
		// TODO Auto-generated method stub

	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dispose(Object prepared) {
		// TODO Auto-generated method stub

	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges)
		throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> OperationResult<SimpleEntity<E>> createAsync(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OperationResult<Long> count(EntityQuery<?> query, Object prepared) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> OperationResult<Iterable<SimpleEntity<? extends E>>> query(EntityQuery<E> query, Object prepared) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, boolean reportInChanges) throws EntityOperationException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <E> OperationResult<Long> updateAsync(EntityUpdate<E> update, Object prepared, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) throws EntityOperationException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <E> OperationResult<Long> deleteAsync(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <V> ElementId updateCollection(BetterCollection<V> collection, CollectionOperationType changeType, ElementId element, V value,
		boolean reportInChanges) throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <V> OperationResult<ElementId> updateCollectionAsync(BetterCollection<V> collection, CollectionOperationType changeType,
		ElementId element, V value, boolean reportInChanges) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <K, V> ElementId updateMap(Map<K, V> collection, CollectionOperationType changeType, K key, V value, Runnable asyncResult) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <K, V> ElementId updateMultiMap(MultiMap<K, V> collection, CollectionOperationType changeType, ElementId valueElement, K key,
		V value, Consumer<ElementId> asyncResult) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<EntityChange<?>> changes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests) throws EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OperationResult<List<Fulfillment<?>>> loadEntityDataAsync(List<EntityLoadRequest<?>> loadRequests) {
		// TODO Auto-generated method stub
		return null;
	}

}
