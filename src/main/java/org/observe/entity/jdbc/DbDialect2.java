package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.qommons.Named;

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
		Table(String name) {
			super(name);
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

		TableColumn(Table table, String name, int sqlType) {
			super(table, name);
			theSqlType = sqlType;
		}

		@Override
		public Table getTable() {
			return (Table) super.getTable();
		}

		@Override
		public int getSqlType() {
			return theSqlType;
		}
	}

	public enum JoinType {
		INNER, LEFT
	}

	public class Join extends TableOrJoin {
		private final TableOrJoin theLeft;
		private final Table theRight;
		private final JoinType theJoinType;
		private final Map<Column, JoinColumn> theJoinColumns;

		public Join(TableOrJoin left, Table right, JoinType joinType, String joinTableName) {
			super(joinTableName);
			theLeft = left;
			theRight = right;
			theJoinType = joinType;
			theJoinColumns = new LinkedHashMap<>();
		}

		public TableOrJoin getLeft() {
			return theLeft;
		}

		public Table getRight() {
			return theRight;
		}

		public JoinType getJoinType() {
			return theJoinType;
		}

		public Map<Column, JoinColumn> getJoinColumns() {
			return Collections.unmodifiableMap(theJoinColumns);
		}

		public JoinColumn addJoinColumn(Column leftColumn, TableColumn rightColumn, String joinColumnName) {
			if (leftColumn.getTable() != theLeft)
				throw new IllegalArgumentException(leftColumn + " is not a table of " + theLeft);
			if (rightColumn.getTable() != theRight)
				throw new IllegalArgumentException(rightColumn + " is not a table of " + theRight);
			JoinColumn joinColumn = new JoinColumn(this, joinColumnName, rightColumn);
			theJoinColumns.put(leftColumn, joinColumn);
			return joinColumn;
		}
	}

	public class JoinColumn extends Column {
		private final TableColumn theWrapped;

		public JoinColumn(Join table, String name, TableColumn wrapped) {
			super(table, name);
			theWrapped = wrapped;
		}

		public TableColumn getWrapped() {
			return theWrapped;
		}

		@Override
		public int getSqlType() {
			return theWrapped.getSqlType();
		}
	}

	public class JoinTable {
		private final Table theSource;
		private final Table theTarget;
		
		private final Map<TableColumn, TableColumn> theJoinColumns;

		public JoinTable(Table source, Table target, Map<TableColumn, TableColumn> joinColumns) {
			theSource = source;
			theTarget = target;
			theJoinColumns = Collections.unmodifiableMap(new LinkedHashMap<>(joinColumns));
		}

		public Table getSource() {
			return theSource;
		}

		public Table getTarget() {
			return theTarget;
		}

		public Map<TableColumn, TableColumn> getJoinColumns() {
			return theJoinColumns;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder(theSource.getName()).append(" JOIN ").append(theTarget.getName()).append(" ON ");
			boolean firstColSet = true;
			for (Map.Entry<TableColumn, TableColumn> colSet : theJoinColumns.entrySet()) {
				if (firstColSet)
					firstColSet = false;
				else
					str.append(", ");
				str.append(colSet.getValue()).append('=').append(colSet.getKey());
			}
			return str.toString();
		}
	}

	List<Table> queryTables() throws SQLException;
	
	Table renameTable(Table table, String name);
	TableColumn renameColumn(TableColumn column, String name);
	Table removeColumn(TableColumn column);
	Table addColumn(String columnName, int sqlType, TableColumn foreignKey, );

	ResultSet query(JoinTable table, List<TableColumn> columns) throws SQLException;
}
