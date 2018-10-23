package org.observe.db.relational.jdbc;

import java.sql.Connection;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.db.relational.EntitySource;
import org.observe.db.relational.ObservableEntity;
import org.observe.db.relational.ObservableEntityType;
import org.observe.db.relational.RelationalDB;
import org.observe.db.relational.RelationalQueryBuilder;

public class JdbcObserveDB implements RelationalDB {
	private final Connection theConnection;
	private final EntitySource theLocalSource;

	private final ReentrantReadWriteLock theUpdateLock;

	public JdbcObserveDB(Connection connection, EntitySource localSource) {
		theConnection = connection;
		theLocalSource = localSource;
	}

	@Override
	public ObservableCollection<EntitySource> getSources() {

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObservableSortedSet<ObservableEntityType<?>> getEntityTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E extends ObservableEntity<E>> ObservableEntityType<E> getEntityType(Class<E> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E extends ObservableEntity<E>> RelationalQueryBuilder<E> query(ObservableEntityType<E> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E extends ObservableEntity<E>> RelationalQueryBuilder<E> query(Class<E> type) {
		// TODO Auto-generated method stub
		return null;
	}

}
