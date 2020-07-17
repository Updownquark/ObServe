package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityProvider.Cancelable;
import org.observe.entity.jdbc.JdbcEntityProvider.PreparedSqlAction;
import org.observe.entity.jdbc.JdbcEntityProvider.PreparedSqlOperation;
import org.observe.entity.jdbc.JdbcEntityProvider.SqlAction;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.threading.ElasticExecutor;
import org.qommons.threading.ElasticExecutor.TaskExecutor;
import org.qommons.tree.BetterTreeList;

public class DefaultConnectionPool implements JdbcEntityProvider.ConnectionPool {
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

	private static class AsyncTask<T> implements Cancelable {
		private final SqlAction<T> theAction;
		private final Consumer<T> onComplete;
		private final Consumer<SQLException> onSqlError;
		private final Consumer<EntityOperationException> onEoeError;
		private volatile boolean isRunning;
		volatile Runnable canceled;
		boolean reportedCanceled;

		AsyncTask(SqlAction<T> action, Consumer<T> onComplete, Consumer<SQLException> onSqlError,
			Consumer<EntityOperationException> onEoeError) {
			theAction = action;
			this.onComplete = onComplete;
			this.onSqlError = onSqlError;
			this.onEoeError = onEoeError;
		}

		public void run(Statement stmt) {
			isRunning = true;
			if (canceled != null) {
				canceled.run();
				return;
			}
			T result;
			try {
				result = theAction.execute(stmt, //
					this::isCanceled);
				onComplete.accept(result);
			} catch (SQLException e) {
				onSqlError.accept(e);
			} catch (EntityOperationException e) {
				onEoeError.accept(e);
			}
		}

		boolean isCanceled() {
			if (canceled != null) {
				if (!reportedCanceled) {
					reportedCanceled = true;
					canceled.run();
				}
				return true;
			}
			return false;
		}

		@Override
		public void cancel(boolean mayInterruptIfExecuting, Runnable onCancelSuccess) {
			if (mayInterruptIfExecuting || !isRunning)
				this.canceled = onCancelSuccess;
		}
	}

	private class AsyncTaskExecutor implements TaskExecutor<AsyncTask<?>> {
		private final Connection theConnection;

		AsyncTaskExecutor(Connection connection) {
			theConnection = connection;
		}

		@Override
		public void execute(AsyncTask<?> task) {
			try (Statement stmt = theConnection.createStatement()) {
				task.run(stmt);
			} catch (SQLException e) {
				throw new IllegalStateException("Could not create or close statement");
			}
		}

		@Override
		public void close() throws Exception {
			theConnection.close();
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
				return new SynchronousConnection(theSynchronousConnectionList.addElement(theConnection.getConnection(), false));
			} catch (SQLException e) {
				throw new IllegalStateException("Lost connection", e);
			}
		});
		theAsyncExecutor = new ElasticExecutor<>(name, () -> {
			try {
				return new AsyncTaskExecutor(theConnection.getConnection());
			} catch (SQLException e) {
				throw new IllegalStateException("Lost connection", e);
			}
		}).cacheWorkers(true).setThreadRange(1, 16).setMaxQueueSize(ElasticExecutor.MAX_POSSIBLE_QUEUE_SIZE).setPreferredQueueSize(2);
	}

	@Override
	public <T> T connect(SqlAction<T> action) throws SQLException, EntityOperationException {
		if (isClosed)
			throw new IllegalStateException("This pool has been closed");
		try (Statement stmt = theSynchronousConnections.get().connection.get().createStatement()) {
			return action.execute(stmt, JdbcEntityProvider.ALWAYS_FALSE);
		}
	}

	@Override
	public <T> Cancelable connectAsync(SqlAction<T> action, Consumer<T> onComplete, Consumer<SQLException> sqlError,
		Consumer<EntityOperationException> eoeError) {
		if (isClosed)
			throw new IllegalStateException("This pool has been closed");
		AsyncTask<T> task = new AsyncTask<>(action, onComplete, sqlError, eoeError);
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
