package org.observe.entity.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.observe.entity.EntityOperationException;
import org.observe.entity.impl.AbstractTabularEntityProvider.EntityTable;
import org.qommons.Named;
import org.qommons.collect.QuickSet.QuickMap;

public interface DbDialect2 {
	public static abstract class TableOrJoin implements Named {
		private final String theName;
		private String theShortName;

		public TableOrJoin(String name) {
			theName = name;
			theShortName = theName;
		}

		@Override
		public String getName() {
			return theName;
		}

		public String getShortName() {
			return theShortName;
		}

		protected void setShortName(String shortName) {
			theShortName = shortName;
		}

		public abstract Set<String> getColumnNames();

		@Override
		public String toString() {
			return theName;
		}
	}

	public static abstract class Column implements Named {
		private final TableOrJoin theTable;
		private final String theName;

		public Column(TableOrJoin table, String name) {
			theTable = table;
			theName = name;
		}

		public TableOrJoin getTable() {
			return theTable;
		}

		@Override
		public String getName() {
			return theName;
		}

		public abstract int getSqlType();

		@Override
		public int hashCode() {
			return Objects.hash(getTable(), getName());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof TableColumn))
				return false;
			return getTable().equals(((TableColumn) obj).getTable()) && getName().equals(((TableColumn) obj).getName());
		}

		@Override
		public String toString() {
			return theTable.getName() + "." + getName();
		}
	}

	public class Table extends TableOrJoin {
		private final String theSchema;
		private final Map<String, TableColumn> theColumns;

		Table(String schema, String name, Map<String, TableColumn> columns) {
			super(name);
			theSchema = schema;
			theColumns = columns;
		}

		public String getSchema() {
			return theSchema;
		}

		@Override
		public Set<String> getColumnNames() {
			return theColumns.keySet();
		}

		public Map<String, TableColumn> getColumns() {
			return theColumns;
		}

		@Override
		public int hashCode() {
			return getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof Table))
				return false;
			else
				return getName().equals(((Table) obj).getName());
		}
	}

	public class TableColumn extends Column {
		private final int theSqlType;
		private final boolean isIdentifier;
		private final int theSize;
		private final boolean isNullable;
		private final boolean isAutoIncrement;

		TableColumn(Table table, String name, int sqlType, boolean identifier, int size, boolean nullable, boolean autoIncrement) {
			super(table, name);
			theSqlType = sqlType;
			isIdentifier = identifier;
			theSize = size;
			isNullable = nullable;
			isAutoIncrement = autoIncrement;
		}

		@Override
		public Table getTable() {
			return (Table) super.getTable();
		}

		@Override
		public int getSqlType() {
			return theSqlType;
		}

		public boolean isIdentifier() {
			return isIdentifier;
		}

		public int getSize() {
			return theSize;
		}

		public boolean isNullable() {
			return isNullable;
		}

		public boolean isAutoIncrement() {
			return isAutoIncrement;
		}
	}

	public enum JoinType {
		INNER, LEFT
	}

	public class Join extends TableOrJoin {
		private final TableOrJoin theLeft;
		private final String theLeftAlias;
		private final Table theRight;
		private final String theRightAlias;
		private final JoinType theJoinType;
		private final Map<String, String> theJoinColumns;

		public Join(TableOrJoin left, String leftAlias, Table right, String rightAlias, JoinType joinType, String joinTableName) {
			super(joinTableName);
			theLeft = left;
			theLeftAlias = leftAlias;
			theRight = right;
			theRightAlias = rightAlias;
			theJoinType = joinType;
			theJoinColumns = new LinkedHashMap<>();
		}

		public TableOrJoin getLeft() {
			return theLeft;
		}

		public String getLeftAlias() {
			return theLeftAlias;
		}

		public Table getRight() {
			return theRight;
		}

		public String getRightAlias() {
			return theRightAlias;
		}

		public JoinType getJoinType() {
			return theJoinType;
		}

		@Override
		public Set<String> getColumnNames() {
			return theJoinColumns.keySet();
		}

		public Map<String, String> getJoinColumns() {
			return Collections.unmodifiableMap(theJoinColumns);
		}

		public Join addJoinColumn(String leftColumn, String rightColumn) {
			if (!theLeft.getColumnNames().contains(leftColumn))
				throw new IllegalArgumentException(leftColumn + " is not a field of " + theLeft);
			if (!theRight.getColumnNames().contains(rightColumn))
				throw new IllegalArgumentException(rightColumn + " is not a field of " + theRight);
			theJoinColumns.put(leftColumn, rightColumn);
			return this;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theLeft.toString()).append(" AS ").append(theLeftAlias)//
				.append(theJoinType).append(" JOIN ").append(theRight.getName()).append(" AS ").append(theRightAlias)//
				.append(" ON ");
			boolean firstColSet = true;
			for (Map.Entry<String, String> column : theJoinColumns.entrySet()) {
				if (firstColSet)
					firstColSet = true;
				else
					str.append(", ");
				str.append(theLeftAlias).append('.').append(column.getKey()).append('=').append(theRightAlias).append(column.getValue());
			}
			return str.toString();
		}
	}

	public class EntityMappedTable {
		private final Table theTable;
		private final QuickMap<String, JdbcFieldRepresentation<?>> theFields;
		private final Map<String, String> theExtraColumnValues;

		public EntityMappedTable(Table table, QuickMap<String, JdbcFieldRepresentation<?>> fields, Map<String, String> extraColumnValues) {
			theTable = table;
			theFields = fields;
			theExtraColumnValues = extraColumnValues;
		}

		public Table getTable() {
			return theTable;
		}

		public QuickMap<String, JdbcFieldRepresentation<?>> getFields() {
			return theFields;
		}

		public Map<String, String> getExtraColumnValues() {
			return theExtraColumnValues;
		}
	}

	List<Table> queryTables(Connection conn) throws SQLException;

	EntityMappedTable map(EntityTable<?> entity, Map<String, Table> tables) throws SQLException, EntityOperationException;

	// Table renameTable(Table table, String name);
	// QuickTableColumn renameColumn(QuickTableColumn column, String name);
	// Table removeColumn(QuickTableColumn column);
	// Table addColumn(String columnName, int sqlType, QuickTableColumn foreignKey, );

	// String query(JoinTable table, List<QuickTableColumn> columns) throws SQLException;
}
