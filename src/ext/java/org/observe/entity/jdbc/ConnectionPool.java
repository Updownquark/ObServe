package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.observe.config.OperationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.PreparedOperation;
import org.qommons.collect.QuickSet.QuickMap;

public interface ConnectionPool {
	public interface SqlAction<T> {
		T execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException;
	}

	public interface PreparedSqlAction<T> {
		T execute(PreparedStatement stmt, QuickMap<String, Object> variableValues, BooleanSupplier canceled)
			throws SQLException, EntityOperationException;
	}

	public interface PreparedSqlOperation<E, T> {
		T execute(PreparedOperation<E> operation) throws SQLException, EntityOperationException;

		OperationResult<T> executeAsync(PreparedOperation<E> operation, Function<SQLException, EntityOperationException> sqlError);

		void dispose();
	}

	Connection connect() throws SQLException;

	<T> T connect(SqlAction<T> action) throws SQLException, EntityOperationException;

	<T> OperationResult<T> connectAsync(SqlAction<T> action, Function<SQLException, EntityOperationException> sqlError);

	<E, T> PreparedSqlOperation<E, T> prepare(String sql, PreparedSqlAction<T> action) throws SQLException;
}