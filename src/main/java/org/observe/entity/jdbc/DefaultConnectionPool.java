package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

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

	private class AsyncTask<T> {
		private final SqlAction<Statement, T> theAction;
		private final Consumer<T> onComplete;
		private final Consumer<SQLException> onError;

		AsyncTask(SqlAction<Statement, T> action, Consumer<T> onComplete, Consumer<SQLException> onError) {
			theAction = action;
			this.onComplete = onComplete;
			this.onError = onError;
		}

		public void run(Statement stmt) {
			T result;
			try {
				result = theAction.apply(stmt);
				onComplete.accept(result);
			} catch (SQLException e) {
				onError.accept(e);
			}
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
				throw new IllegalStateException("Could not create statement");
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
	public <T> T connect(SqlAction<Statement, T> action, Consumer<T> asyncOnComplete, Consumer<SQLException> asyncError)
		throws SQLException {
		if (isClosed)
			throw new IllegalStateException("This pool has been closed");
		if (asyncOnComplete != null) {
			theAsyncExecutor.execute(new AsyncTask<>(action, asyncOnComplete, asyncError));
			return null;
		} else {
			try (Statement stmt = theSynchronousConnections.get().connection.get().createStatement()) {
				return action.apply(stmt);
			}
		}
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
