package org.observe.entity.jdbc;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.entity.EntityChange;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.impl.AbstractTabularEntityProvider;
import org.observe.entity.jdbc.JdbcEntityProvider.Column;
import org.observe.entity.jdbc.JdbcEntityProvider.EntryColumn;
import org.observe.entity.jdbc.JdbcEntityProvider.SerialColumn;
import org.observe.entity.jdbc.JdbcTypesSupport.JdbcColumn;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.condition.Condition;

import com.google.common.reflect.TypeToken;

public class JdbcEntityProvider4 extends AbstractTabularEntityProvider {
	private final ConnectionPool theConnectionPool;
	private final DbDialect2 theDialect;
	private final TableNamingStrategy theNaming;
	private final JdbcTypesSupport theTypeSupport;
	private final String theDefaultSchema;

	private Map<String, DbDialect2.Table> theTables;

	public JdbcEntityProvider4(ConnectionPool connectionPool, DbDialect2 dialect, TableNamingStrategy namingStrategy,
		JdbcTypesSupport typeSupport, String defaultSchema) {
		theConnectionPool = connectionPool;
		theDialect = dialect;
		theNaming = namingStrategy;
		theTypeSupport = typeSupport;
		theDefaultSchema = defaultSchema;
	}

	@Override
	protected void init() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected <E> Table createTable(EntityTable<E> entity) throws SQLException, EntityOperationException {
		if (theTables == null) {
			theTables = new LinkedHashMap<>();
			theConnectionPool.connect((stmt, canceled) -> {
				for (DbDialect2.Table table : theDialect.queryTables(stmt.getConnection()))
					theTables.put(table.getName(), table);
				return null;
			});
		}
		DbDialect2.Table dbTable = theTables.get(theNaming.getDefaultTableName(entity.getType()));
		if (dbTable == null) {
			for (DbDialect2.Table tbl : theTables.values()) {
				if (theNaming.tableNameMatches(entity.getType(), tbl.getName())) {
					dbTable = tbl;
					break;
				}
			}
			if (dbTable == null) {
				dbTable = theConnectionPool.connect((stmt, canceled) -> {
					return createDbTable(entity, stmt);
				});
			}
		}
		QuickMap<String, TableColumn> idColumns = QuickSet.of(dbTable.getColumns().values().stream()//
			.filter(c -> c.isIdentifier()).map(c -> c.getName()).toArray(String[]::new))//
			.createMap();
		QuickMap<String, TableColumn> columns = QuickSet.of(dbTable.getColumns().keySet()).createMap();
		Table table = new Table.Default(dbTable.getName(), idColumns, columns);
		for (DbDialect2.TableColumn dbColumn : dbTable.getColumns().values()) {
			TableColumn column = new JdbcTableColumn(table, dbColumn);
			columns.put(dbColumn.getName(), column);
			if (dbColumn.isIdentifier())
				idColumns.put(dbColumn.getName(), column);
		}
		return table;
	}

	static class JdbcTableColumn implements TableColumn {
		private final Table theOwner;
		private final DbDialect2.TableColumn theColumn;

		public JdbcTableColumn(Table owner, DbDialect2.TableColumn column) {
			theOwner = owner;
			theColumn = column;
		}

		@Override
		public Table getOwner() {
			return theOwner;
		}

		@Override
		public String getName() {
			return theColumn.getName();
		}
	}

	protected DbDialect2.Table createDbTable(EntityTable<?> entity, Statement stmt) throws SQLException {
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE TABLE ");
		if (theDefaultSchema != null)
			sql.append(theDefaultSchema).append('.');
		String tableName = theNaming.getDefaultTableName(entity.getType());
		sql.append(tableName).append('(');
		boolean[] first = new boolean[] { true };
		for (int c = 0; c < entity.getFields().keySize(); c++) {
			// Don't duplicate non-ID inherited fields
			if (entity.getType().getFields().get(c).getIdIndex() < 0 && !entity.getType().getFields().get(c).getOverrides().isEmpty())
				continue;
			String colName = theNaming.getColumnName(entity.getType().getFields().get(c), tableName);
			JdbcColumn<?> typeSupport = theTypeSupport.getColumnSupport(entity.getType().getFields().get(c).getFieldType());
			if (typeSupport != null) {
				typeSupport.
				((SerialColumn<?>) col).forEachColumn(column -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(',');
					sql.append("\n\t").append(column.getName()).append(' ').append(column.getType().getTypeName());
					column.writeConstraints(sql);
				});
			}
			// Auto-increment the last ID field if supported
			if (entity.getFields().get(c).getIdIndex() == entity.getIdentityFields().keySize() - 1
				&& theTypeSupport.getAutoIncrement() != null)
				sql.append(' ').append(theTypeSupport.getAutoIncrement());
		}
		sql.append(",\n\n\tPRIMARY KEY (");
		first[0] = true;
		for (ObservableEntityFieldType<?, ?> idField : entity.getIdentityFields().allValues()) {
			((SerialColumn<?>) theNaming.getColumn(idField.getIndex())).forEachColumn(col -> {
				if (first[0])
					first[0] = false;
				else
					sql.append(", ");
				sql.append(col.getName());
			});
		}
		sql.append(")\n);");
		System.out.println("Creating table for " + entity + ": " + sql);
		stmt.execute(sql.toString());
		for (int c = 0; c < entity.getFields().keySize(); c++) {
			if (!entity.getFields().get(c).getOverrides().isEmpty())
				continue;
			Column<?> col = theNaming.getColumn(c);
			if (col instanceof SerialColumn)
				continue;
			EntryColumn<?, ?, ?> entryCol = (EntryColumn<?, ?, ?>) col;
			sql.setLength(0);
			sql.append("CREATE TABLE ");
			if (theDefaultSchema != null)
				sql.append(theDefaultSchema).append('.');
			sql.append(entryCol.getTableName()).append('(');
			first[0] = true;
			entryCol.getOwnerColumn().forEachColumn(column -> {
				if (first[0])
					first[0] = false;
				else
					sql.append(',');
				sql.append("\n\t").append(column.getName()).append(' ').append(column.getType().getTypeName());
				column.writeConstraints(sql);
			});
			if (entryCol.getIndexColumn() != null)
				sql.append(",\n\tINDEX INTEGER NOT NULL");
			entryCol.getValueColumn().forEachColumn(column -> {
				sql.append(",\n\t").append(column.getName()).append(' ').append(column.getType().getTypeName());
				column.writeConstraints(sql);
			});
			sql.append(",\n\n\tPRIMARY KEY (");
			first[0] = true;
			entryCol.getOwnerColumn().forEachColumn(column -> {
				if (first[0])
					first[0] = false;
				else
					sql.append(", ");
				sql.append(column.getName());
			});
			if (entryCol.getIndexColumn() != null)
				sql.append(", ").append(entryCol.getIndexColumn());
			else
				entryCol.getValueColumn().forEachColumn(column -> sql.append(", ").append(column.getName()));
			sql.append("),\n\tFOREIGN KEY (");
			first[0] = true;
			entryCol.getOwnerColumn().forEachColumn(column -> {
				if (first[0])
					first[0] = false;
				else
					sql.append(", ");
				sql.append(column.getName());
			});
			sql.append(") REFERENCES ").append(theNaming.getTableName()).append('(');
			first[0] = true;
			for (ObservableEntityFieldType<?, ?> field : entity.getType().getIdentityFields().allValues()) {
				((SerialColumn<?>) theNaming.getColumn(field.getIndex())).forEachColumn(column -> {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					sql.append(column.getName());
				});
			}
			sql.append(")\n);");
			System.out.println("Creating table for " + type.getFields().get(c) + ": " + sql);
			stmt.execute(sql.toString());
		}
	}

	@Override
	protected Table createCollectionTable(Table ownerTable, String fieldName, Class<?> collectionClass) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected <F> TableField<F> createScalarField(Table ownerTable, Class<F> fieldClass, TypeToken<F> fieldType,
		ObservableEntityFieldType<?, ?> field) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected <F> TableField<F> createCustomNonScalarField(Table ownerTable, Class<F> fieldClass, TypeToken<F> fieldType, String fieldName,
		ObservableEntityFieldType<?, ?> field, ObservableEntityType<?> targetEntity, ObservableEntityType<?> keyTargetEntity,
		ObservableEntityType<?> valueTargetEntity) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected <V> ReferenceColumn<V> createReferenceColumn(Table ownerTable, TableColumn idField) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected TableOperation<Row> create(List<TableCreateRequest> creates) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected Selection select(TableOrJoin tableOrJoin, Condition<TableOrJoin, ?, ?> condition) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected QueryOperation query(Selection selection) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected TableOperation<Long> update(Selection selection, Map<TableOrJoinColumn, Object> values) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected TableOperation<Long> delete(Selection selection) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void dispose(Object prepared) {
	}

	@Override
	protected Iterable<EntityChange<?>> getExternalChanges() {
		throw new UnsupportedOperationException("Not implemented");
	}
}
