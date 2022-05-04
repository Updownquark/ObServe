package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.function.Function;

import org.observe.config.OperationResult;
import org.observe.entity.EntityOperationException;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.threading.ElasticExecutor;
import org.qommons.threading.ElasticExecutor.TaskExecutor;
import org.qommons.tree.BetterTreeList;

public class DefaultConnectionPool implements ConnectionPool {
	private class SynchronousConnection {
		final CollectionElement<Connection> connection;

		SynchronousConnection(CollectionElement<Connection> connection) {
			this.connection = connection;
		}

		void close() throws SQLException {
			synchronized (theSynchronousConnectionList) {
				if (connection.getElementId().isPresent() && !connection.get().isClosed()) {
					Connection c = connection.get();
					theSynchronousConnectionList.mutableElement(connection.getElementId()).remove();
					c.close();
				}
			}
		}

		@Override
		protected void finalize() throws Throwable {
			close();
		}
	}

	private static class AsyncTask<T> extends OperationResult.AsyncResult<T> {
		private final SqlAction<T> theAction;
		private final Function<SQLException, EntityOperationException> theSqlError;

		AsyncTask(SqlAction<T> action, Function<SQLException, EntityOperationException> sqlError) {
			theAction = action;
			theSqlError = sqlError;
		}

		public void run(Connection conn) {
			if (!begin())
				return;
			try {
				T result = doAction(conn, theAction);
				fulfilled(result);
			} catch (SQLException e) {
				failed(theSqlError.apply(e));
			} catch (EntityOperationException e) {
				failed(e);
			} catch (RuntimeException | Error e) {
				failed(new EntityOperationException(e));
			}
		}
	}

	private class AsyncTaskExecutor implements TaskExecutor<AsyncTask<?>> {
		private final Connection theExecutorConnection;

		AsyncTaskExecutor(Connection connection) {
			theExecutorConnection = connection;
		}

		@Override
		public void execute(AsyncTask<?> task) {
			task.run(theExecutorConnection);
		}

		@Override
		public void close() throws Exception {
			theExecutorConnection.close();
		}
	}

	private final SqlConnector theConnection;
	private final ThreadLocal<SynchronousConnection> theSynchronousConnections;
	private final BetterList<Connection> theSynchronousConnectionList;
	private final ElasticExecutor<AsyncTask<?>> theAsyncExecutor;
	private volatile boolean isClosed;

	public DefaultConnectionPool(String name, SqlConnector connection) {
		theConnection = connection;
		theSynchronousConnectionList = BetterTreeList.<Connection> build().safe(false).build();
		theSynchronousConnections = ThreadLocal.withInitial(() -> {
			try {
				Connection conn = theConnection.getConnection();
				conn.setAutoCommit(false);
				return new SynchronousConnection(theSynchronousConnectionList.addElement(conn, false));
			} catch (SQLException e) {
				throw new IllegalStateException("Lost connection", e);
			}
		});
		theAsyncExecutor = new ElasticExecutor<>(name, () -> {
			try {
				Connection conn = theConnection.getConnection();
				conn.setAutoCommit(false);
				return new AsyncTaskExecutor(conn);
			} catch (SQLException e) {
				throw new IllegalStateException("Lost connection", e);
			}
		}).cacheWorkers(true).setThreadRange(1, 16).setMaxQueueSize(ElasticExecutor.MAX_POSSIBLE_QUEUE_SIZE).setPreferredQueueSize(2);
	}

	@Override
	public <T> T connect(SqlAction<T> action) throws SQLException, EntityOperationException {
		if (isClosed)
			throw new IllegalStateException("This pool has been closed");
		Connection conn = theSynchronousConnections.get().connection.get();
		return doAction(conn, action);
	}

	static <T> T doAction(Connection conn, SqlAction<T> action) throws SQLException, EntityOperationException {
		Savepoint savepoint = conn.setSavepoint("pool");
		try (Statement stmt = conn.createStatement()) {
			T returned = action.execute(stmt, JdbcEntityProvider.ALWAYS_FALSE);
			conn.commit();
			return returned;
		} catch (RuntimeException | Error e) {
			conn.rollback(savepoint);
			conn.releaseSavepoint(savepoint);
			throw new EntityOperationException(e);
		}
	}

	@Override
	public <T> OperationResult<T> connectAsync(SqlAction<T> action, Function<SQLException, EntityOperationException> sqlError) {
		if (isClosed)
			throw new IllegalStateException("This pool has been closed");
		AsyncTask<T> task = new AsyncTask<>(action, sqlError);
		theAsyncExecutor.execute(task);
		return task;
	}

	@Override
	public <E, T> PreparedSqlOperation<E, T> prepare(String sql, PreparedSqlAction<T> action) throws SQLException {
	}

	public void close() {
		isClosed = true;
		theAsyncExecutor.clear(null);
		theAsyncExecutor.cacheWorkers(false);
		synchronized (theSynchronousConnectionList) {
			for (CollectionElement<Connection> sc : theSynchronousConnectionList.elements()) {
				Connection c = sc.get();
				try {
					c.close();
				} catch (SQLException e) {}
				theSynchronousConnectionList.mutableElement(sc.getElementId()).remove();
			}
		}
	}
}
