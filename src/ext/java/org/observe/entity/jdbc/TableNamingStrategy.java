package org.observe.entity.jdbc;

import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.qommons.StringUtils;

public interface TableNamingStrategy {
	public static final Default DEFAULT = new Default();

	String getDefaultTableName(ObservableEntityType<?> entityType);
	boolean tableNameMatches(ObservableEntityType<?> entityType, String tableName);

	String getColumnName(ObservableEntityFieldType<?, ?> field, String tableName);
	boolean columnNameMatches(ObservableEntityFieldType<?, ?> field, String tableName, String columnName);

	String getAssociationTableName(ObservableEntityFieldType<?, ?> field, String ownerTableName);
	boolean associationTableNameMatches(ObservableEntityFieldType<?, ?> field, String ownerTableName, String assocTableName);

	public static class Default implements TableNamingStrategy {
		@Override
		public String getDefaultTableName(ObservableEntityType<?> entityType) {
			return toSqlName(entityType.getName());
		}

		@Override
		public boolean tableNameMatches(ObservableEntityType<?> entityType, String tableName) {
			return toSqlName(entityType.getName()).equalsIgnoreCase(tableName);
		}

		@Override
		public String getColumnName(ObservableEntityFieldType<?, ?> field, String tableName) {
			return toSqlName(field.getName());
		}

		@Override
		public boolean columnNameMatches(ObservableEntityFieldType<?, ?> field, String tableName, String columnName) {
			return toSqlName(field.getName()).equalsIgnoreCase(columnName);
		}

		@Override
		public String getAssociationTableName(ObservableEntityFieldType<?, ?> field, String ownerTableName) {
			return ownerTableName + "_" + toSqlName(field.getName());
		}

		@Override
		public boolean associationTableNameMatches(ObservableEntityFieldType<?, ?> field, String ownerTableName, String assocTableName) {
			if (!assocTableName.startsWith(ownerTableName + "_"))
				return false;
			return toSqlName(field.getName()).equalsIgnoreCase(assocTableName.substring(ownerTableName.length() + 1));
		}

		protected String toSqlName(String name) {
			return StringUtils.parseByCase(name, true).toCaseScheme(false, false, "_");
		}
	}
}
