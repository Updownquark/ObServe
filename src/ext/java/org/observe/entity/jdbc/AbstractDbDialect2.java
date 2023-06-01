package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractDbDialect2 implements DbDialect2 {
	private final Set<String> theSchemata;

	public AbstractDbDialect2(String... schemata) {
		if (schemata.length == 0)
			theSchemata = Collections.emptySet();
		else {
			Set<String> schemataSet = new HashSet<>(schemata.length * 2);
			for (String schema : schemata)
				schemataSet.add(schema.toUpperCase());
			theSchemata = Collections.unmodifiableSet(schemataSet);
		}
	}

	@Override
	public List<Table> queryTables(Connection conn) throws SQLException {
		List<String[]> schemata = new ArrayList<>();
		try (ResultSet rs = conn.getMetaData().getSchemas()) {
			while (rs.next()) {
				String schema = rs.getString("TABLE_SCHEM");
				if (theSchemata.isEmpty() || theSchemata.contains(schema.toUpperCase()))
					schemata.add(new String[] { schema, rs.getString("TABLE_CATALOG") });
			}
		}

		Map<String, String[]> tables = new LinkedHashMap<>();
		for (String[] schema : schemata) {
			try (ResultSet rs = conn.getMetaData().getTables(schema[1], schema[0], null, new String[] { "TABLE" })) {
				while (rs.next()) {
					String tableName = rs.getString("TABLE_NAME");
					tables.putIfAbsent(tableName, new String[] { //
						rs.getString("TABLE_CAT"), //
						rs.getString("TABLE_SCHEM"), //
						tableName });
				}
			}
		}

		Map<String, Table> dbTables = new LinkedHashMap<>();
		Map<String, Map<String, TableColumn>> tableColumns = new HashMap<>();
		Map<String, Set<String>> idColumns = new HashMap<>();
		for (String[] table : tables.values()) {
			Map<String, TableColumn> columns = new LinkedHashMap<>();
			dbTables.put(table[2], new Table(table[1], table[2], columns));
			Set<String> idcs = new HashSet<>();
			idColumns.put(table[2], idcs);
			try (ResultSet rs = conn.getMetaData().getPrimaryKeys(table[0], table[1], table[2])) {
				idcs.add(rs.getString("COLUMN_NAME"));
			}
		}

		for (String[] table : tables.values()) {
			Table dbTable = dbTables.get(table[2]);
			Map<String, TableColumn> columns = tableColumns.get(table[2]);
			Set<String> idcs = idColumns.get(dbTable.getName());
			try (ResultSet rs = conn.getMetaData().getColumns(table[0], table[1], table[2], null)) {
				String columnName = rs.getString("COLUMN_NAME");
				int dataType = rs.getInt("DATA_TYPE");
				boolean id = idcs.contains(columnName);
				int size = rs.getInt("COLUMN_SIZE");
				boolean nullable = "YES".equals(rs.getString("IS_NULLABLE"));
				boolean autoInc = "YES".equals(rs.getString("IS_AUTOINCREMENT"));
				columns.put(columnName, new TableColumn(dbTable, columnName, dataType, id, size, nullable, autoInc));
			}
		}

		return new ArrayList<>(dbTables.values());
	}
}
