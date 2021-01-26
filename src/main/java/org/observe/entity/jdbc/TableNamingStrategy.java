package org.observe.entity.jdbc;

import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;

public interface TableNamingStrategy {
	String getDefaultTableName(ObservableEntityType<?> entityType);
	boolean tableNameMatches(ObservableEntityType<?> entityType, String tableName);

	String getColumnName(ObservableEntityFieldType<?, ?> field, String tableName);
	boolean columnNameMatches(ObservableEntityFieldType<?, ?> field, String tableName, String columnName);

	String getAssociationTableName(ObservableEntityFieldType<?, ?> field, String ownerTableName);
	boolean associationTableNameMatches(ObservableEntityFieldType<?, ?> field, String ownerTableName, String assocTableName);
}
