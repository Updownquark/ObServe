package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import org.observe.entity.EntityIdentity;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.jdbc.DbDialect.JoinedTable;
import org.observe.entity.jdbc.JdbcTypesSupport.JdbcColumn;
import org.qommons.IterableUtils;
import org.qommons.Named;
import org.qommons.collect.QuickSet.QuickMap;

public interface JdbcFieldRepresentation<F> {
	public interface Column<F> extends Named {
		JdbcColumn<F> getColumn();
		// TODO Constraints?
	}

	interface SerialColumnComponent<F> {
		Column<F> getColumn();

		boolean isNull();

		StringBuilder writeValue(StringBuilder str);
	}

	interface FieldDeserializer<F> {
		<F2> F2 deserializeColumn(JdbcColumn<F2> column);
	}

	interface ResultIndexer {
		int getColumnIndex(String columnName);
	}

	public interface SerialField<F> extends JdbcFieldRepresentation<F> {
		int getColumnCount();

		Iterable<Column<?>> getColumns();

		Iterable<SerialColumnComponent<?>> getColumnValues(F fieldValue);

		F deserialize(ResultSet rs, ResultIndexer indexer) throws SQLException;
	}

	public interface SimpleField<F> extends SerialField<F>, Column<F> {
		@Override
		default int getColumnCount() {
			return 1;
		}

		@Override
		default Iterable<Column<?>> getColumns() {
			return Collections.singleton(this);
		}

		@Override
		default Iterable<SerialColumnComponent<?>> getColumnValues(F fieldValue) {
			return Collections.singleton(new SerialColumnComponent<F>() {
				@Override
				public Column<F> getColumn() {
					return SimpleField.this;
				}

				@Override
				public boolean isNull() {
					return fieldValue == null;
				}

				@Override
				public StringBuilder writeValue(StringBuilder str) {
					SimpleField.this.getColumn().serialize(fieldValue, str);
					return str;
				}
			});
		}

		@Override
		default F deserialize(ResultSet rs, ResultIndexer indexer) throws SQLException {
			return getColumn().deserialize(rs, indexer.getColumnIndex(getName()));
		}
	}

	interface ReferenceField<F> extends SerialField<F> {
		ObservableEntityType<F> getReferenceType();

		QuickMap<String, SerialField<?>> getIdColumns();

		@Override
		default int getColumnCount() {
			int cc = 0;
			for (SerialField<?> idField : getIdColumns().allValues()) {
				cc += idField.getColumnCount();
			}
			return cc;
		}

		@Override
		default Iterable<Column<?>> getColumns() {
			return IterableUtils.flatten(IterableUtils.map(getIdColumns().allValues(), f -> f.getColumns()));
		}

		@Override
		default Iterable<SerialColumnComponent<?>> getColumnValues(F fieldValue) {
			EntityIdentity<? extends F> value;
			if (fieldValue instanceof EntityIdentity)
				value = (EntityIdentity<? extends F>) fieldValue;
			else if (fieldValue instanceof ObservableEntity)
				value = ((ObservableEntity<? extends F>) fieldValue).getId();
			else
				value = getReferenceType().observableEntity(fieldValue).getId();

			Iterable<SerialColumnComponent<?>>[] idColumns = new Iterable[getIdColumns().keySize()];
			for (int i = 0; i < idColumns.length; i++)
				idColumns[i] = ((SerialField<Object>) getIdColumns().get(i)).getColumnValues(value.getFields().get(i));
			return IterableUtils.iterable(idColumns);
		}

		@Override
		default F deserialize(ResultSet rs, ResultIndexer indexer) throws SQLException {
			EntityIdentity.Builder<F> builder = null;
			for (int i = 0; i < getIdColumns().keySize(); i++) {
				Object idVal = getIdColumns().get(i).deserialize(rs, indexer);
				if (builder == null) {
					if (idVal == null)
						return null;
					builder = getReferenceType().buildId();
				} else if (idVal == null)
					throw new IllegalStateException("Some pieces of the ID are present, others not");
				builder.with(i, idVal);
			}
			return (F) builder.build();
		}
	}

	interface OverriddenField<F> extends JdbcFieldRepresentation<F> {
		List<JdbcFieldRepresentation<? super F>> getOverrides();
	}

	interface JoinedField<E, F> extends JdbcFieldRepresentation<F> {
		String getTableName();

		SerialField<E> getOwner();
		String getIndexColumn();
		JdbcFieldRepresentation<F> getValue();

		Object createInitialValue(EntityIdentity<E> entity, JoinedTable<E, F> table);
		void addValue(Object value, ResultSet rs, String qualifier) throws SQLException;
	}
}