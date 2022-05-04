package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.observe.config.OperationResult;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntityChange;
import org.observe.entity.EntityCreator;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityProvider;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.jdbc.ConnectionPool.SqlAction;
import org.observe.entity.jdbc.DbDialect2.DbColumn;
import org.observe.entity.jdbc.DbDialect2.DbEntity;
import org.observe.entity.jdbc.DbDialect2.DbField;
import org.observe.entity.jdbc.DbDialect2.DbTable;
import org.observe.entity.jdbc.DbDialect2.JoinTable;
import org.qommons.StringUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class JdbcEntityProvider3 implements ObservableEntityProvider {
	private final DbDialect2 theDialect;
	private final ConnectionPool theConnectionPool;
	private final Map<String, DbEntity<?>> theDbEntities;

	public JdbcEntityProvider3(ObservableEntityDataSet dataSet, DbDialect2 dialect, ConnectionPool connectionPool) throws SQLException {
		theDialect = dialect;
		theConnectionPool = connectionPool;
		theDbEntities = new HashMap<>();
		try (Connection conn = connectionPool.connect()) {
			for (ObservableEntityType<?> entity : dataSet.getEntityTypes()) {
				DbEntity<?> dbEntity = dialect.createDbEntity(entity, conn);
				theDbEntities.put(entity.getName(), dbEntity);
			}
		}
		// Name all the tables and columns with entity set-wide distinct short names
		Map<String, Set<DbName>> tables = new HashMap<>();
		Set<DbColumn> columns = new HashSet<>();
		Set<DbName> tableShortNames = new HashSet<>();
		for (DbEntity<?> dbEntity : theDbEntities.values()) {
			if (DbName.nameTable(dbEntity.getRootTable(), tableShortNames))
				tables.put(dbEntity.getRootTable().getShortName(), new HashSet<>());
			for (DbField<?, ?> field : dbEntity.getFields().values()) {
				if (field.getEntity() != dbEntity.getEntityType())
					continue;
				for (JoinTable join : field.getJoins()) {
					if (DbName.nameTable(join.getTarget(), tableShortNames))
						tables.put(join.getTarget().getShortName(), new HashSet<>());
					for (DbColumn column : join.getJoinColumns().values()) {
						if (columns.add(column))
							DbName.nameColumn(column, tables);
					}
				}
				for (DbColumn column : field.getColumns()) {
					if (columns.add(column))
						DbName.nameColumn(column, tables);
				}
			}
		}
	}

	private static class DbName {
		final StringUtils.Name name;
		final int[] letterCount;
		String suffix;
		int hashCache;

		DbName(String name) {
			this.name = StringUtils.split(name.toLowerCase(), '_');
			letterCount = new int[this.name.getComponents().length];
			int minLettersPerSection = Math.max(1, 8 / this.name.getComponents().length);
			for (int i = 0; i < this.name.getComponents().length; i++) {
				letterCount[i] = Math.min(minLettersPerSection, this.name.getComponents()[i].length());
			}
			suffix = "";
			hashCache = -1;
		}

		boolean incrementLength() {
			int minSection = 0;
			int minLength = 0;
			for (int i = letterCount.length - 1; i >= 0; i--) {
				if (letterCount[i] < name.getComponents()[i].length() && (minLength > 0 && letterCount[i] < minLength)) {
					minSection = i;
					minLength = letterCount[i];
				}
			}
			if (minLength == 0)
				return false;
			letterCount[minSection]++;
			return true;
		}

		void incrementSuffix() {
			suffix = StringUtils.add(suffix, suffix.length(), 1);
		}

		@Override
		public int hashCode() {
			if (hashCache != -1)
				return hashCache;
			hashCache = 0;
			for (int s = 0; s < letterCount.length; s++) {
				for (int i = 0; i < letterCount[s]; i++)
					hashCache = Integer.rotateLeft(hashCache, 5) ^ name.getComponents()[s].charAt(i);
			}
			for (int i = 0; i < suffix.length(); i++)
				hashCache = Integer.rotateLeft(hashCache, 5) ^ suffix.charAt(i);
			return hashCache;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof DbName))
				return false;
			DbName other = (DbName) obj;
			int s1 = 0, s2 = 0, i1 = 0, i2 = 0;
			while (s1 <= letterCount.length && s2 <= other.letterCount.length) {
				String c1 = s1 < letterCount.length ? name.getComponents()[s1] : suffix;
				String c2 = s2 < other.letterCount.length ? other.name.getComponents()[s2] : other.suffix;
				boolean cont = false;
				if (i1 == c1.length()) {
					s1++;
					i1 = 0;
					cont = true;
				}
				if (i2 == c2.length()) {
					s2++;
					i2 = 0;
					cont = true;
				}
				if (!cont && c1.charAt(i1) != c2.charAt(i2))
					return false;
			}
			return s1 == letterCount.length && s2 == other.letterCount.length && i1 == suffix.length() && i2 == other.suffix.length();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			for (int s = 0; s < letterCount.length; s++) {
				str.append(name.getComponents()[s], 0, letterCount[s]);
			}
			str.append(suffix);
			return str.toString();
		}

		private static boolean nameTable(DbTable tbl, Set<DbName> tableShortNames) {
			if (tbl.getShortName() != null)
				return false;
			DbName name = new DbName(tbl.getName());
			boolean maxLength = false;
			while (!tableShortNames.add(name)) {
				if (maxLength || !name.incrementLength()) {
					maxLength = true;
					name.incrementSuffix();
				}
			}
			tbl.setShortName(name.toString());
			return true;
		}

		private static void nameColumn(DbColumn column, Map<String, Set<DbName>> tables) {
			if (column.getShortName() != null)
				return;
			Set<DbName> columnNames = tables.get(column.getTable().getShortName());
			DbName name = new DbName(column.getName());
			boolean maxLength = false;
			while (!columnNames.add(name)) {
				if (maxLength || !name.incrementLength()) {
					maxLength = true;
					name.incrementSuffix();
				}
			}
			column.setShortName(column.getTable().getShortName() + "_" + name.toString());
		}
	}

	@Override
	public void install(ObservableEntityDataSet entitySet) throws EntityOperationException {
		// TODO Auto-generated method stub

	}

	@Override
	public Object prepare(ConfigurableOperation<?> operation) throws EntityOperationException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public void dispose(Object prepared) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public <E> SimpleEntity<E> create(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges)
		throws EntityOperationException {
		if(prepared!=null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		DbEntity<?> entity=theDbEntities.get(creator.getEntityType().getName());
		if(entity==null)
			throw new EntityOperationException("No such entity "+creator.getEntityType().getName()+" in this data source");
		try {
			return theConnectionPool.connect(new SqlAction<SimpleEntity<E>>() {
				@Override
				public SimpleEntity<E> execute(Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
					SimpleEntity<E> entity=new SimpleEntity<>(creator.getType().buildId()//
						.withFieldValues(fields).build());
					entity.insert(entity, ((ConfigurableCreator<E, E>) creator).getFieldValues(), stmt, canceled);
					return .
						boolean wasAutoCommit=stmt.getConnection().getAutoCommit();
					stmt.getConnection().setAutoCommit(false);
					try {
						stmt.execu
					} finally {
						stmt.getConnection().setAutoCommit(wasAutoCommit);
					}
					// TODO Auto-generated method stub
					return null;
				}
			});
		} catch (SQLException e) {
			throw new EntityOperationException(e);
		}
	}

	@Override
	public <E> OperationResult<SimpleEntity<E>> createAsync(EntityCreator<? super E, E> creator, Object prepared, boolean reportInChanges) {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OperationResult<Long> count(EntityQuery<?> query, Object prepared) {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> OperationResult<Iterable<SimpleEntity<? extends E>>> query(EntityQuery<E> query, Object prepared) {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> long update(EntityUpdate<E> update, Object prepared, boolean reportInChanges) throws EntityOperationException {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <E> OperationResult<Long> updateAsync(EntityUpdate<E> update, Object prepared, boolean reportInChanges) {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <E> long delete(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) throws EntityOperationException {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <E> OperationResult<Long> deleteAsync(EntityDeletion<E> delete, Object prepared, boolean reportInChanges) {
		if (prepared != null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
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
