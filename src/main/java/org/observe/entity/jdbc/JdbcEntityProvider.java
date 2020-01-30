package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.observe.Observable;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCondition.CompositeCondition;
import org.observe.entity.EntityCondition.LiteralCondition;
import org.observe.entity.EntityCondition.OrCondition;
import org.observe.entity.EntityCondition.ValueCondition;
import org.observe.entity.EntityCondition.VariableCondition;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.qommons.IntList;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.collect.StampedLockingStrategy;

/** An observable entity provider implementation backed by a JDBC-accessed relational database */
public class JdbcEntityProvider implements ObservableEntityProvider {
	public interface SqlConsumer<T> {
		void accept(T t) throws SQLException;
	}

	public interface ConnectionPool {
		void connect(boolean sync, SqlConsumer<Statement> action, Consumer<SQLException> asyncError) throws SQLException;
	}

	protected static class TableNaming {
		private final String theTableName;
		private final String[] theColumnNames;

		protected TableNaming(String tableName, String[] columnNames) {
			theTableName = tableName;
			theColumnNames = columnNames;
		}

		protected String getTableName() {
			return theTableName;
		}

		protected String[] getColumnNames() {
			return theColumnNames;
		}
	}

	protected static class Join<E1, E2, F> {
		private final ObservableEntityFieldType<E1, F> theLeftSide;
		private final ObservableEntityFieldType<E2, F> theRightSide;

		protected Join(ObservableEntityFieldType<E1, F> leftSide, ObservableEntityFieldType<E2, F> rightSide) {
			theLeftSide = leftSide;
			theRightSide = rightSide;
		}

		public ObservableEntityFieldType<E1, F> getLeftSide() {
			return theLeftSide;
		}

		public ObservableEntityFieldType<E2, F> getRightSide() {
			return theRightSide;
		}
	}

	private final StampedLockingStrategy theLocker;
	private final ConnectionPool theConnectionPool;
	private final Map<String, TableNaming> theTableNaming;

	public JdbcEntityProvider(StampedLockingStrategy locker, ConnectionPool connectionPool) {
		theLocker = locker;
		theConnectionPool = connectionPool;
		theTableNaming = new HashMap<>();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLocker.tryLock(write, cause);
	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws IllegalStateException {
		for (ObservableEntityType<?> type : entitySet.getEntityTypes()) {
			theTableNaming.put(type.getName(), generateNaming(type));
		}
	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		throw new UnsupportedOperationException("TODO Not implemented yet");
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<E> creator, Object prepared, Consumer<SimpleEntity<E>> identityFieldsOnAsyncComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		TableNaming naming = theTableNaming.get(creator.getEntityType().getName());
		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO ");
		sql.append(naming.getTableName());
		boolean firstValue = true;
		QuickMap<String, Object> values = ((ConfigurableCreator<E>) creator).getFieldValues();
		for (int f = 0; f < values.keySize(); f++) {
			Object value = values.get(f);
			if (value != EntityUpdate.NOT_SET) {
				if (firstValue) {
					sql.append(" (");
					firstValue = false;
				} else
					sql.append(", ");
				sql.append(naming.getColumnNames()[f]);
			}
		}
		if (!firstValue) {
			sql.append(") VALUES (");
			for (int f = 0; f < values.keySize(); f++) {
				Object value = values.get(f);
				if (value != EntityUpdate.NOT_SET) {
					if (firstValue)
						firstValue = false;
					else
						sql.append(", ");
					serialize(creator.getEntityType().getFields().get(f), value, sql);
				}
			}
			sql.append(')');
		}
		QuickMap<String, Object> fields = creator.getEntityType().getFields().keySet().createMap();
		EntityIdentity.Builder<E> idBuilder = creator.getEntityType().buildId();
		try {
			theConnectionPool.connect(identityFieldsOnAsyncComplete == null, stmt -> {
				stmt.execute(sql.toString(), naming.getColumnNames());
				ResultSet idRS = stmt.getGeneratedKeys();
				for (int i = 0; i < naming.getColumnNames().length; i++) {
					Object value = deserialize(creator.getEntityType().getIdentityFields().get(i),
						idRS.getObject(naming.getColumnNames()[i]));
					fields.put(i, value);
					int idIndex = creator.getEntityType().getFields().get(i).getIdIndex();
					if (idIndex >= 0)
						idBuilder.with(idIndex, value);
				}
				if (identityFieldsOnAsyncComplete != null) {
					identityFieldsOnAsyncComplete.accept(//
						new SimpleEntity<>(idBuilder.build(), fields));
				}
			}, sqle -> {
				onError.accept(new EntityOperationException("Could not create " + creator.getEntityType(), sqle));
			});
		} catch (SQLException e) {
			throw new EntityOperationException("Could not create " + creator.getEntityType(), e);
		}
		if (identityFieldsOnAsyncComplete != null)
			return null;
		else
			return new SimpleEntity<>(idBuilder.build(), fields);
	}

	@Override
	public long count(EntityQuery<?> query, Object prepared, LongConsumer onAsycnComplete, Consumer<EntityOperationException> onError)
		throws EntityOperationException {
		throw new UnsupportedOperationException("TODO Not implemented yet");
	}

	@Override
	public <E> Iterable<SimpleEntity<? extends E>> query(EntityQuery<E> query, Object prepared,
		Consumer<Iterable<SimpleEntity<? extends E>>> onAsyncComplete, Consumer<EntityOperationException> onError)
			throws EntityOperationException {
		TableNaming naming = theTableNaming.get(query.getEntityType().getName());
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT * FROM ");
		sql.append(naming.getTableName());
		if (query.getSelection() instanceof EntityCondition.All) {} else {
			sql.append("WHERE ");
			appendCondition(sql, query.getSelection());
		}
		// TODO Auto-generated method stub
	}

	@Override
	public long update(EntityUpdate<?> update, Object prepared, LongConsumer onAsyncComplete, Consumer<EntityOperationException> onError)
		throws EntityOperationException {
		// TODO Auto-generated method stub
	}

	@Override
	public long delete(EntityDeletion<?> delete, Object prepared, LongConsumer onAsyncComplete, Consumer<EntityOperationException> onError)
		throws EntityOperationException {
		// TODO Auto-generated method stub
	}

	@Override
	public Observable<List<EntityChange<?>>> changes() {
		// TODO Auto-generated method stub
	}

	@Override
	public List<Fulfillment<?>> loadEntityData(List<EntityLoadRequest<?>> loadRequests, Consumer<List<Fulfillment<?>>> onComplete,
		Consumer<EntityOperationException> onError) throws EntityOperationException {
		// TODO Auto-generated method stub
	}

	protected TableNaming generateNaming(ObservableEntityType<?> type) {
		String[] colNames = new String[type.getFields().keySize()];
		for (int c = 0; c < colNames.length; c++)
			colNames[c] = sqlIfyName(type.getFields().get(c).getName());
		return new TableNaming(sqlIfyName(type.getName()), colNames);
	}

	protected static String sqlIfyName(String javaName) {
		return StringUtils.parseByCase(javaName, true).toCaseScheme(false, false, "_").toUpperCase();
	}

	private <E> void serialize(ObservableEntityFieldType<E, ?> observableEntityFieldType, Object value, StringBuilder sql) {
		// TODO Auto-generated method stub
	}

	private <E, F> F deserialize(ObservableEntityFieldType<E, F> observableEntityFieldType, Object object) {
		// TODO Auto-generated method stub
	}

	private <E> void appendCondition(StringBuilder sql, EntityCondition<E> selection, TableNaming naming,
		QuickMap<String, IntList> variableLocations, int[] lastLocation, Map<String, Join<?, ?, ?>> joins) {
		if(selection instanceof ValueCondition){
			ValueCondition<E, ?> vc = (ValueCondition<E, ?>) selection;
			if (vc.getField() instanceof ObservableEntityFieldType) {
				sql.append(naming.getColumnNames()[((ObservableEntityFieldType<E, ?>) vc.getField()).getFieldIndex()]).append(' ');
				if (vc.getComparison() < 0) {
					sql.append('<');
					if (vc.isWithEqual())
						sql.append('=');
				} else if (vc.getComparison() > 0) {
					sql.append('>');
					if (vc.isWithEqual())
						sql.append('=');
				} else if (vc instanceof LiteralCondition && ((LiteralCondition<E, ?>) vc).getValue() == null) {
					sql.append("IS");
					if (!vc.isWithEqual())
						sql.append(" NOT");
				} else if (vc.isWithEqual())
					sql.append('=');
				else
					sql.append("<>");
				sql.append(' ');
				if (vc instanceof LiteralCondition) {
					Object value = ((LiteralCondition<E, ?>) vc).getValue();
					serialize((ObservableEntityFieldType<E, Object>) vc.getField(), value, sql);
				} else {
					sql.append('?');
					variableLocations.get(((VariableCondition<E, ?>) vc).getVariable().getName()).add(lastLocation[0]);
					lastLocation[0]++;
				}
			} else {
				throw new UnsupportedOperationException("TODO Chain selection is not supported yet");
			}
		} else if (selection instanceof CompositeCondition) {
			String name = selection instanceof OrCondition ? "OR" : "AND";
			boolean firstComponent = true;
			for (EntityCondition<E> component : ((CompositeCondition<E>) selection).getConditions()) {
				if (firstComponent)
					firstComponent = false;
				else
					sql.append(' ').append(name).append(' ');
				boolean paren = component instanceof CompositeCondition;
				if (paren)
					sql.append('(');
				appendCondition(sql, component, naming, variableLocations, lastLocation, joins);
				if (paren)
					sql.append(')');
			}
		} else
			throw new IllegalStateException("Unrecognized condition type: " + selection.getClass().getName());
	}
}
