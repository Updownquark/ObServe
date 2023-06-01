package org.observe.entity.jdbc;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityProvider.CollectionOperationType;
import org.observe.entity.ObservableEntityProvider.SimpleEntity;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.QuickSet.QuickMap;

public interface DbDialect {
	public interface EntityTable<E> {
		public ObservableEntityType<E> getType();
		public String getTableName();
		public QuickMap<String, JdbcFieldRepresentation<?>> getFields();

		public JoinedTable<E, ?> getJoinTable(int fieldIndex) throws IllegalArgumentException;

		public List<? extends EntityTable<? super E>> getSupers();
		public List<? extends EntityTable<? extends E>> getExtensions();

		SimpleEntity<E> create(ConfigurableCreator<?, E> creator, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
		// Object prepareCreate(PreparedCreator<?, E> creator, Connection connection) throws SQLException, EntityOperationException;
		// SimpleEntity<E> createPrepared(PreparedCreator<?, E> creator, Object prepared) throws SQLException, EntityOperationException;

		long count(ConfigurableQuery<E> query, Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException;
		// Object prepareCount(PreparedQuery<E> query, Connection connection) throws SQLException, EntityOperationException;
		// long countPrepared(PreparedQuery<E> query, Object prepared) throws SQLException, EntityOperationException;

		Iterable<SimpleEntity<? extends E>> query(ConfigurableQuery<E> query, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
		// Object prepareQuery(PreparedQuery<E> query, Connection connection) throws SQLException, EntityOperationException;
		// Iterable<SimpleEntity<? extends E>> queryPrepared(PreparedQuery<E> query, Object prepared)
		// throws SQLException, EntityOperationException;

		Fulfillment<E> fulfill(EntityLoadRequest<E> loadRequest, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;

		long updateGetCount(ConfigurableUpdate<E> update, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
		List<EntityIdentity<E>> updateGetAffected(ConfigurableUpdate<E> condition, QuickMap<String, List<Object>> oldValues, Statement stmt,
			BooleanSupplier canceled) throws SQLException, EntityOperationException;
		// Object prepareUpdate(PreparedUpdate<E> update, Connection connection) throws SQLException, EntityOperationException;
		// long updatePreparedGetCount(PreparedUpdate<E> update, Object prepared) throws SQLException, EntityOperationException;
		// Iterable<EntityIdentity<E>> updatePreparedGetAffected(PreparedUpdate<E> update, Object prepared)
		// throws SQLException, EntityOperationException;

		long deleteGetCount(ConfigurableDeletion<E> delete, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
		List<EntityIdentity<E>> deleteGetAffected(ConfigurableDeletion<E> delete, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
		// Object prepareDelete(PreparedDeletion<E> delete, Connection connection) throws SQLException, EntityOperationException;
		// long deletePreparedGetCount(PreparedDeletion<E> delete, Object prepared) throws SQLException, EntityOperationException;
		// Iterable<EntityIdentity<E>> deletePreparedGetAffected(PreparedDeletion<E> delete, Object prepared)
		// throws SQLException, EntityOperationException;
	}

	public interface JoinedTable<E, F> {
		JdbcFieldRepresentation.JoinedField<E, F> getJoinField();

		void modify(CollectionOperationType changeType, int index, int size, E owner, F value, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
	}

	<E> EntityTable<E> generateTable(ObservableEntityType<E> entity, String tableName, QuickMap<String, JdbcFieldRepresentation<?>> fields);

	void initialize(Map<ObservableEntityType<?>, EntityTable<?>> tables);
}
