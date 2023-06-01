package org.observe.entity.jdbc;

import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.jdbc.JdbcFieldRepresentation.JoinedField;
import org.qommons.collect.QuickSet.QuickMap;

public class H2DbDialect extends AbstractDbDialect {
	@Override
	public <E> EntityTable<E> generateTable(ObservableEntityType<E> entity, String tableName,
		QuickMap<String, JdbcFieldRepresentation<?>> fields) {
		return new H2EntityTable<>(entity, tableName, fields);
	}

	@Override
	public <E> JoinedTable<E, ?> createJoinTable(AbstractEntityTable<E> table, ObservableEntityFieldType<E, ?> fieldType,
		JoinedField<E, ?> joinedField) {
		return new H2CollectionTable<>(table, joinedField);
	}

	public class H2EntityTable<E> extends AbstractEntityTable<E> {
		public H2EntityTable(ObservableEntityType<E> type, String tableName, QuickMap<String, JdbcFieldRepresentation<?>> fields) {
			super(type, tableName, fields);
		}
	}

	public class H2CollectionTable<E, F> extends AbstractCollectionTable<E, F> {
		public H2CollectionTable(AbstractEntityTable<E> table, JoinedField<E, F> field) {
			super(table, field);
		}
	}
}
