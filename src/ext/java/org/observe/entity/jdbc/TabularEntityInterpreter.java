package org.observe.entity.jdbc;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.QuickSet.QuickMap;

public interface TabularEntityInterpreter {
	public static abstract class Table {
		private final String theName;
	}

	public static class EntityTable<E> extends Table {
		private final ObservableEntityType<E> theEntityType;
		private final List<Join> theSuperJoins;
		private final QuickMap<String, EntityTableField<E>> theFields;
	}

	public interface EntityTableField<E> {
		EntityTable<E> getOwner();

		ObservableEntityFieldType<E, ?> getField();
	}

	public static abstract class FieldStructTable<E> implements EntityTableField<E> {
		private final EntityTable<E> theOwner;
		private final String theValueColumnName;
	}

	public static class FieldCollectionTable<E, V> extends FieldStructTable<E> {}

	public static class FieldMapTable<E, K, V> extends FieldStructTable<E> {}

	public static class TableColumn<V> {
		private final Table theOwner;
		private final String theName;
	}

	public static class QueryTable {
		private final String theName;
	}

	public static class QueryColumn<V> {
		private final QueryTable theOwner;
	}

	public static class Join extends QueryTable {
		private final Table theTarget;
		private final List<JoinColumn<?>> theJoinColumns;
	}

	public static class JoinColumn<V> extends QueryColumn<V> {
		private final QueryColumn<V> theSourceColumn;
		private final TableColumn<V> theJoinColumn;
	}

	public interface Operation {
		boolean isCanceled();
		void onCancel(Consumer<Operation> onCancel);

		Object getPrepared();
		Object getVariable(PreparedVariable vbl);
	}

	public interface PreparedVariable {
	}

	public interface Selection {
		Set<QueryTable> getTables();

		QueryCondition getCondition();
	}

	public static abstract class QueryCondition {}

	public static class CompositeCondition extends QueryCondition {
		private final boolean isAnd;
		private final List<QueryCondition> theComponents;
	}

	public static abstract class ColumnCondition<V> extends QueryCondition {
		private final QueryColumn<V> theColumn;
	}

	public interface Query extends Selection {
		List<QueryColumn<?>> getColumns();
	}

	public interface Update extends Selection {
		List<UpdateValue<?>> getValues();
	}

	public static class UpdateValue<V> {
		private final QueryColumn<V> theColumn;
		private final V theValue;
	}

	public interface Delete extends Selection {}

	public interface Create extends Operation {
		Table getTable();

		QuickMap<String, Object> getFieldValues();
	}

	public interface TableResults {
		boolean next();

		<V> V get(QueryColumn<V> column);
	}

	/* TODO
	 * Migration
	 * Ledger (get external changes)
	 * Prepared statements
	 */

	<E> EntityTable<E> getTable(ObservableEntityType<E> entityType) throws EntityOperationException;

	Object prepareCreate(Create create) throws EntityOperationException;
	Object prepareQuery(Query query) throws EntityOperationException;
	Object prepareCount(Selection selection) throws EntityOperationException;
	Object prepareUpdate(Update update) throws EntityOperationException;
	Object prepareDelete(Delete delete) throws EntityOperationException;

	TableResults create(Create create) throws EntityOperationException;
	TableResults query(Selection selection) throws EntityOperationException;
	int count(Query query) throws EntityOperationException;
	List<QuickMap<String, Object>> update(Update update) throws EntityOperationException;
	List<QuickMap<String, Object>> delete(Delete delete) throws EntityOperationException;
}
