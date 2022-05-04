package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.entity.ConfigurableCreator;
import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCondition.CompositeCondition;
import org.observe.entity.EntityCondition.LiteralCondition;
import org.observe.entity.EntityCondition.OrCondition;
import org.observe.entity.EntityCondition.ValueCondition;
import org.observe.entity.EntityCondition.VariableCondition;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityLoadRequest.Fulfillment;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityUpdate;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.FieldLoadType;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityProvider.CollectionOperationType;
import org.observe.entity.ObservableEntityProvider.SimpleEntity;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.jdbc.JdbcFieldRepresentation.Column;
import org.observe.entity.jdbc.JdbcFieldRepresentation.JoinedField;
import org.observe.entity.jdbc.JdbcFieldRepresentation.OverriddenField;
import org.observe.entity.jdbc.JdbcFieldRepresentation.SerialColumnComponent;
import org.observe.entity.jdbc.JdbcFieldRepresentation.SerialField;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.IntList;
import org.qommons.QommonsUtils;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.QuickSet.QuickMap;

public abstract class AbstractDbDialect implements DbDialect {
	@Override
	public void initialize(Map<ObservableEntityType<?>, EntityTable<?>> tables) {
		Map<EntityTable<?>, List<AbstractEntityTable<?>>> extensions = new HashMap<>();
		for (EntityTable<?> table : tables.values()) {
			for (ObservableEntityType<?> superType : table.getType().getSupers())
				extensions.computeIfAbsent(tables.get(superType), __ -> new LinkedList<>()).add((AbstractEntityTable<?>) table);
		}
		for (EntityTable<?> table : tables.values()) {
			List<AbstractEntityTable<?>> supers = table.getType().getSupers().stream()
				.map(type -> (AbstractEntityTable<?>) tables.get(type)).collect(Collectors.toList());
			((AbstractEntityTable<?>) table).init(supers, extensions.getOrDefault(table, Collections.emptyList()));
		}

		// Assign each table a nickname for conciseness
		Map<String, List<AbstractEntityTable<?>>> entityTablesByAcronym = new HashMap<>();
		StringBuilder str = new StringBuilder();
		for (EntityTable<?> table : tables.values()) {
			str.setLength(0);
			str.append(Character.toUpperCase(table.getType().getName().charAt(0)));
			for (int c = 1; c < table.getType().getName().length(); c++) {
				char ch = table.getType().getName().charAt(c);
				if (Character.isUpperCase(ch))
					str.append(ch);
			}
			entityTablesByAcronym.computeIfAbsent(str.toString(), __ -> new LinkedList<>()).add((AbstractEntityTable<?>) table);
		}
		for (Map.Entry<String, List<AbstractEntityTable<?>>> nick : entityTablesByAcronym.entrySet()) {
			if (nick.getValue().size() == 1)
				nick.getValue().get(0).setNickName(nick.getKey());
			int i = 0;
			for (AbstractEntityTable<?> table : nick.getValue()) {
				table.setNickName(nick.getKey() + i);
				i++;
			}
		}
	}

	public abstract <E> JoinedTable<E, ?> createJoinTable(AbstractEntityTable<E> table, ObservableEntityFieldType<E, ?> fieldType,
		JoinedField<E, ?> joinedField);

	public abstract class AbstractEntityTable<E> implements EntityTable<E> {
		private final ObservableEntityType<E> theType;
		private final String theTableName;
		private String theNickName;
		private final QuickMap<String, JdbcFieldRepresentation<?>> theFields;
		private final QuickMap<String, JoinedTable<E, ?>> theJoinTables;
		private final List<AbstractEntityTable<? super E>> theSupers;
		private final List<AbstractEntityTable<? super E>> theExposedSupers;
		private List<AbstractEntityTable<? extends E>> theExtensions;
		private List<AbstractEntityTable<? extends E>> theExposedExtensions;

		private final int theColumnCount;
		private final int theIdColumnCount;

		private String theSuperJoinSql;
		private String theExtensionJoinSql;

		public AbstractEntityTable(ObservableEntityType<E> type, String tableName, QuickMap<String, JdbcFieldRepresentation<?>> fields) {
			theType = type;
			theTableName = tableName;
			theFields = fields;
			if (type.getSupers().isEmpty())
				theSupers = theExposedSupers = Collections.emptyList();
			else {
				theSupers = new ArrayList<>(type.getSupers().size());
				theExposedSupers = Collections.unmodifiableList(theSupers);
			}
			QuickMap<String, JoinedTable<E, ?>> joinTables = null;
			int colCount = 0, idColCount = 0;
			for (int i = 0; i < theFields.keySize(); i++) {
				if (theFields.get(i) instanceof SerialField) {
					int cc = ((SerialField<?>) theFields.get(i)).getColumnCount();
					colCount += cc;
					if (theType.getFields().get(i).getIdIndex() >= 0)
						idColCount += cc;
				} else if (theFields.get(i) instanceof JoinedField) {
					if (joinTables == null)
						joinTables = theFields.keySet().createMap();
					joinTables.put(i, createJoinTable(this, type.getFields().get(i), (JoinedField<E, ?>) theFields.get(i)));
				}
			}
			theColumnCount = colCount;
			theIdColumnCount = idColCount;
			theJoinTables = joinTables == null ? null : joinTables.unmodifiable();
		}

		void setNickName(String nickName) {
			theNickName = nickName;
		}

		protected void init(List<? extends AbstractEntityTable<?>> supers, List<? extends AbstractEntityTable<?>> extensions) {
			theSupers.addAll((Collection<? extends AbstractEntityTable<? super E>>) supers);
			if (extensions.isEmpty())
				theExtensions = theExposedExtensions = Collections.emptyList();
			else {
				theExtensions = new ArrayList<>(extensions.size());
				theExtensions.addAll((Collection<? extends AbstractEntityTable<? extends E>>) extensions);
				theExposedExtensions = Collections.unmodifiableList(theExtensions);
			}
		}

		protected String getSuperJoinSql() {
			if (theSuperJoinSql == null) {
				StringBuilder sql = new StringBuilder();
				for (AbstractEntityTable<? super E> superTable : theSupers) {
					sql.append(" INNER JOIN ").append(superTable.getTableName()).append(" AS ").append(superTable.getType().getName())
					.append(superTable.getSuperJoinSql());
				}
				theSuperJoinSql = sql.toString();
			}
			return theSuperJoinSql;
		}

		protected String getExtensionJoinSql() {
			if (theExtensionJoinSql == null) {
				StringBuilder sql = new StringBuilder();
				for (AbstractEntityTable<? extends E> extTable : theExtensions) {
					sql.append(" LEFT JOIN ").append(extTable.getTableName()).append(" AS ").append(extTable.theNickName).append(" ON ");
					boolean first = true;
					for (int i = 0; i < theType.getIdentityFields().keySize(); i++) {
						if (first)
							first = false;
						else
							sql.append(" AND ");
						String fieldName = theType.getIdentityFields().keySet().get(i);
						sql.append(extTable.theNickName).append('.').append(fieldName)//
						.append('=').append(theNickName).append('.').append(fieldName);
					}
					sql.append(extTable.getExtensionJoinSql());
				}
				theExtensionJoinSql = sql.toString();
			}
			return theExtensionJoinSql;
		}

		@Override
		public ObservableEntityType<E> getType() {
			return theType;
		}

		@Override
		public String getTableName() {
			return theTableName;
		}

		@Override
		public QuickMap<String, JdbcFieldRepresentation<?>> getFields() {
			return theFields;
		}

		@Override
		public JoinedTable<E, ?> getJoinTable(int fieldIndex) throws IllegalArgumentException {
			return theJoinTables == null ? null : theJoinTables.get(fieldIndex);
		}

		@Override
		public List<? extends AbstractEntityTable<? super E>> getSupers() {
			return theExposedSupers;
		}

		@Override
		public List<? extends AbstractEntityTable<? extends E>> getExtensions() {
			return theExposedExtensions;
		}

		@Override
		public SimpleEntity<E> create(ConfigurableCreator<?, E> creator, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			return _create(creator, null, stmt, canceled);
		}

		private <E2 extends E> SimpleEntity<E2> _create(ConfigurableCreator<?, E2> creator, SimpleEntity<E2> created, Statement stmt,
			BooleanSupplier canceled) throws SQLException, EntityOperationException {
			if (!theSupers.isEmpty()) {
				for (AbstractEntityTable<? super E> superTable : theSupers)
					created = superTable._create(creator, created, stmt, canceled);
			}
			ColumnData columns = new ColumnData();
			QuickMap<String, Object> values = ((ConfigurableCreator<? super E, E>) creator).getFieldValues();
			columns.addTable(this, created != null, false, false, field -> {
				if (values.get(field.getIndex()) == EntityUpdate.NOT_SET)
					return false;
				else if (!(theFields.get(field.getIndex()) instanceof SerialField))
					return false;
				return true;
			});
			StringBuilder sql = new StringBuilder();
			sql.append("INSERT INTO ");
			sql.append(getTableName());
			if (!columns.isEmpty()) {
				columns.toSql(sql.append('('));
				sql.append(") VALUES (");
				boolean firstValue = true;
				for (ObservableEntityFieldType<E, ?> field : theType.getFields().allValues()) {
					JdbcFieldRepresentation<?> column = getFields().get(field.getIndex());
					if (!(column instanceof SerialField))
						continue;
					Object value;
					if (creator.getEntityType() == theType)
						value = values.get(field.getIndex());
					else
						value = values.get(field.getName());
					if (!field.getOverrides().isEmpty()) {
						if (field.getIdIndex() >= 0) {
							for (SerialColumnComponent<?> col : ((SerialField<Object>) column)
								.getColumnValues(created.getIdentity().getFields().get(field.getIdIndex()))) {
								if (firstValue)
									firstValue = false;
								else
									sql.append(", ");
								col.writeValue(sql);
							}
						}
						continue;
					} else if (created != null && getField(created, field.getIndex()) != EntityUpdate.NOT_SET) {
						for (SerialColumnComponent<?> col : ((SerialField<Object>) column)
							.getColumnValues(getField(created, field.getIndex()))) {
							if (firstValue)
								firstValue = false;
							else
								sql.append(", ");
							col.writeValue(sql);
						}
					} else if (value == EntityUpdate.NOT_SET)
						continue;
					else {
						for (SerialColumnComponent<?> col : ((SerialField<Object>) column).getColumnValues(value)) {
							if (firstValue)
								firstValue = false;
							else
								sql.append(", ");
							col.writeValue(sql);
						}
					}
				}
				sql.append(')');
			}
			List<String> columnNames = columns.getColumnNames();
			stmt.execute(sql.toString(), columnNames.toArray(new String[columnNames.size()]));
			try (ResultSet rs = stmt.getGeneratedKeys()) {
				if (!rs.next())
					throw new EntityOperationException("No generated key set?");
				EntityIdentity.Builder<E2> idBuilder = creator.getEntityType().buildId();
				for (ObservableEntityFieldType<E, ?> idField : theType.getIdentityFields().allValues()) {
					idBuilder.with(idField.getIdIndex(), ((SerialField<Object>) theFields.get(idField.getIndex())).deserialize(rs,
						col -> columns.indexOf(theNickName, col)));
				}
				created = new SimpleEntity<>(idBuilder.build());
				fillEntity(created, rs, columns, false, false);
				if (rs.next())
					System.err.println("Multiple generated key sets?");
			}
			return created;
		}

		@Override
		public long count(ConfigurableQuery<E> query, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			StringBuilder sql = new StringBuilder(getCountSelect()).append(getTableName()).append(" AS ").append(theNickName);
			// Join with as few tables as possible to satisfy the query
			for (AbstractEntityTable<? super E> superTable : theSupers) {
				if (canceled.getAsBoolean())
					return -1;
				superTable.maybeJoinWithSupers(sql, this, field -> {
					return query.getSelection().getCondition(getType().getFields().get(field.getName())) != null;
				}, canceled);
			}
			if (canceled.getAsBoolean())
				return -1;
			if (query.getSelection() instanceof EntityCondition.All) {//
			} else {
				sql.append(" WHERE ");
				appendCondition(sql, query.getSelection(), QuickMap.empty(), null, false, canceled);
			}
			if (canceled.getAsBoolean())
				return -1;
			try (ResultSet rs = stmt.executeQuery(sql.toString())) {
				if (!rs.next())
					return 0L;
				return rs.getLong(1);
			}
		}

		protected String getCountSelect(){
			return "SELECT COUNT(*) FROM ";
		}

		void maybeJoinWithSupers(StringBuilder sql, AbstractEntityTable<? extends E> source,
			Predicate<ObservableEntityFieldType<? super E, ?>> needsField, BooleanSupplier canceled) {
			boolean needThis = false;
			for (ObservableEntityFieldType<E, ?> field : theType.getFields().allValues()) {
				if (field.getIdIndex() < 0 && field.getOverrides().isEmpty() && needsField.test(field)) {
					needThis = true;
					break;
				}
			}
			if (canceled.getAsBoolean())
				return;
			if (needThis)
				joinWithSuper(sql, source, canceled);
			for (AbstractEntityTable<? super E> superTable : theSupers) {
				if (canceled.getAsBoolean())
					return;
				superTable.maybeJoinWithSupers(sql, source, field -> needsField.test(field), canceled);
			}
		}

		protected void joinWithSuper(StringBuilder sql, AbstractEntityTable<? extends E> source, BooleanSupplier canceled) {
			sql.append(" INNER JOIN ").append(getTableName()).append(" AS ").append(theNickName).append(" ON ");
			boolean first = true;
			for (int i = 0; i < theType.getIdentityFields().keySize(); i++) {
				for (Column<?> column : ((SerialField<Object>) theFields.get(theType.getIdentityFields().get(i).getIndex())).getColumns()) {
					if (canceled.getAsBoolean())
						return;
					if (first)
						first = false;
					else
						sql.append(" AND ");
					sql.append(theNickName).append('.').append(column.getName())//
						.append('=').append(source.theNickName).append('.').append(column.getName());
				}
			}
		}

		@Override
		public Iterable<SimpleEntity<? extends E>> query(ConfigurableQuery<E> query, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			StringBuilder sql = new StringBuilder(" FROM ").append(getTableName()).append(" AS ").append(theNickName);
			// Join with as few tables as possible to satisfy the query
			for (AbstractEntityTable<? super E> superTable : theSupers) {
				if (canceled.getAsBoolean())
					return null;
				superTable.maybeJoinWithSupers(sql, this, field -> {
					return query.getSelection().getCondition(getType().getFields().get(field.getName())) != null//
						|| query.getFieldLoadTypes().get(field.getName()) != FieldLoadType.Lazy;
				}, canceled);
			}
			if (canceled.getAsBoolean())
				return null;
			sql.append(getExtensionJoinSql());
			ColumnData columns = new ColumnData();
			columns.addTable(this, true, false, true, null);
			if (query.getSelection() instanceof EntityCondition.All) {//
			} else {
				sql.append(" WHERE ");
				appendCondition(sql, query.getSelection(), QuickMap.empty(), null, false, canceled);
			}
			if (canceled.getAsBoolean())
				return null;
			String columnSql = columns.toSql(new StringBuilder("SELECT ")).toString();
			System.out.println(columnSql + sql);
			List<SimpleEntity<? extends E>> entities = new ArrayList<>();
			try (ResultSet rs = stmt.executeQuery(columnSql + sql.toString())) {
				while (rs.next()) {
					if (canceled.getAsBoolean())
						return entities;
					entities.add(buildEntity(rs, columns, true));
				}
			}
			return entities;
		}

		@Override
		public Fulfillment<E> fulfill(EntityLoadRequest<E> loadRequest, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT ");
			boolean[] firstField = new boolean[] { true };
			for (int i = 0; i < loadRequest.getType().getIdentityFields().keySize(); i++)
				addFieldRef(loadRequest.getType().getIdentityFields().get(i), sql, firstField);
			for (EntityValueAccess<E, ?> field : loadRequest.getFields())
				addFieldRef(field, sql, firstField);
			ColumnData columns = new ColumnData();
			columns.addTable(this, true, true, false, field -> {
				int fieldIdx = (field.getOwnerType() == theType) ? field.getIndex() : theFields.keySet().indexOf(field.getName());
				field = theType.getFields().get(fieldIdx);
				if (loadRequest.getFields().contains(field))
					return true;
				for (EntityValueAccess<E, ?> f : loadRequest.getFields()) {
					if (!(f instanceof ObservableEntityFieldType) && f.getFieldSequence().getFirst().equals(field))
						return true;
				}
				return false;
			});
			SimpleEntity<E>[] results = new SimpleEntity[loadRequest.getEntities().size()];

			sql.append(" FROM ").append(theTableName).append(" AS ").append(theNickName);
			// Join with as few tables as possible to satisfy the query
			for (AbstractEntityTable<? super E> superTable : theSupers) {
				if (canceled.getAsBoolean())
					return null;
				superTable.maybeJoinWithSupers(sql, this, field -> {
					if (loadRequest.getFields().contains(field))
						return true;
					for (EntityValueAccess<E, ?> f : loadRequest.getFields()) {
						if (!(f instanceof ObservableEntityFieldType) && f.getFieldSequence().getFirst().equals(f))
							return true;
					}
					return false;
				}, canceled);
			}
			if (canceled.getAsBoolean())
				return null;
			sql.append(" WHERE ");
			if (loadRequest.getChange() != null && loadRequest.getChange().getCustomData() instanceof EntityCondition)
				appendCondition(sql, (EntityCondition<E>) loadRequest.getChange().getCustomData(), QuickMap.empty(), null, false, canceled);
			else {
				boolean firstEntity = true;
				for (EntityIdentity<? extends E> entity : loadRequest.getEntities()) {
					if (firstEntity)
						firstEntity = false;
					else
						sql.append(" OR ");
					if (entity.getFields().keySize() > 1)
						sql.append('(');
					firstField[0] = true;
					for (int i = 0; i < entity.getFields().keySize(); i++) {
						ObservableEntityFieldType<E, ?> field = loadRequest.getType().getIdentityFields().get(i);
						JdbcFieldRepresentation<?> jfr = theFields.get(field.getIndex());
						if (jfr instanceof SerialField) {
							for (SerialColumnComponent<?> col : ((SerialField<Object>) jfr).getColumnValues(entity.getFields().get(i))) {
								if (firstField[0])
									firstField[0] = false;
								else
									sql.append(" AND ");
								sql.append(col.getColumn().getName()).append('=').append(theNickName).append('.');
								col.writeValue(sql);
							}
						}
					}
					if (entity.getFields().keySize() > 1)
						sql.append(')');
				}
			}

			try (ResultSet rs = stmt.executeQuery(sql.toString())) {
				while (rs.next()) {
					SimpleEntity<E> entity = buildEntity(rs, columns, false);
					CollectionElement<EntityIdentity<E>> requested = loadRequest.getEntities().getElement(entity.getIdentity(), true);
					if (requested == null)
						continue;
					results[loadRequest.getEntities().getElementsBefore(requested.getElementId())] = entity;
				}
			}
			// TODO Check for missing results and do a secondary, identity-based query for the missing ones
			// Non-serial fields
			if (theJoinTables != null) {
				for (EntityValueAccess<? extends E, ?> field : loadRequest.getFields()) {
					if (!(field instanceof ObservableEntityFieldType)
						|| theJoinTables.get(((ObservableEntityFieldType<E, ?>) field).getIndex()) == null)
						continue;
					AbstractCollectionTable<E, Object> collectionTable = (AbstractCollectionTable<E, Object>) theJoinTables
						.get(((ObservableEntityFieldType<E, ?>) field).getIndex());
					collectionTable.load(loadRequest, (ObservableEntityFieldType<E, ?>) field,
						(JoinedField<E, Object>) theFields.get(((ObservableEntityFieldType<E, ?>) field).getIndex()), results, stmt);
				}
			}
			return new Fulfillment<>(loadRequest, QommonsUtils.map(Arrays.asList(results), r -> r.getFields(), true));
		}

		@Override
		public long updateGetCount(ConfigurableUpdate<E> update, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			// TODO Auto-generated method stub
		}

		@Override
		public List<EntityIdentity<E>> updateGetAffected(ConfigurableUpdate<E> condition, QuickMap<String, List<Object>> oldValues,
			Statement stmt, BooleanSupplier canceled) throws SQLException, EntityOperationException {
			// TODO Auto-generated method stub
		}

		@Override
		public long deleteGetCount(ConfigurableDeletion<E> delete, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			// TODO Auto-generated method stub
		}

		@Override
		public List<EntityIdentity<E>> deleteGetAffected(ConfigurableDeletion<E> delete, Statement stmt, BooleanSupplier canceled)
			throws SQLException, EntityOperationException {
			// TODO Auto-generated method stub
		}

		protected <F> void addFieldRef(EntityValueAccess<E, F> field, StringBuilder sql, boolean[] firstColumn) {
			if (field instanceof ObservableEntityFieldType) {
				JdbcFieldRepresentation<? super F> column = (JdbcFieldRepresentation<F>) theFields
					.get(((ObservableEntityFieldType<?, ?>) field).getIndex());
				while (column instanceof OverriddenField)
					column = ((OverriddenField<F>) column).getOverrides().get(0);
				if (!(column instanceof SerialField))
					return;
				for (Column<?> col : ((SerialField<? super F>) column).getColumns()) {
					if (firstColumn[0])
						firstColumn[0] = false;
					else
						sql.append(", ");
					// TODO qualify with nickname of the table whose field it is
					sql.append(col.getName());
				}
			} else
				throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
		}

		protected StringBuilder appendCondition(StringBuilder sql, EntityCondition<E> selection,
			QuickMap<String, IntList> variableLocations, int[] lastLocation, boolean inner, BooleanSupplier canceled) {
			if (canceled.getAsBoolean())
				return sql;
			if (selection instanceof ValueCondition) {
				ValueCondition<E, ?> vc = (ValueCondition<E, ?>) selection;
				if (vc.getField() instanceof ObservableEntityFieldType) {
					ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) vc.getField();
					JdbcFieldRepresentation<Object> jdbcField = (JdbcFieldRepresentation<Object>) theFields.get(field.getIndex());
					boolean compound = false;
					if (!(jdbcField instanceof SerialField)) {
						throw new IllegalArgumentException("Conditions on non-serial columns unsupported: " + field);
					} else if (((SerialField<Object>) jdbcField).getColumnCount() != 1) {
						if (vc.getComparison() != 0)
							throw new IllegalArgumentException("Non-equality comparisons on compound columns unsupported: " + field);
						compound = true;
					}
					String symbol;
					if (vc.getComparison() < 0) {
						if (vc.isWithEqual())
							symbol = "<=";
						else
							symbol = "<";
					} else if (vc.getComparison() > 0) {
						if (vc.isWithEqual())
							symbol = ">=";
						else
							symbol = ">";
					} else if (vc instanceof LiteralCondition && ((LiteralCondition<E, ?>) vc).getValue() == null) {
						if (!vc.isWithEqual())
							symbol = "IS NOT";
						else
							symbol = "IS";
					} else if (vc.isWithEqual())
						symbol = "=";
					else
						symbol = "<>";
					if (compound && inner)
						sql.append('(');
					if (vc instanceof LiteralCondition) {
						Object value = ((LiteralCondition<E, ?>) vc).getValue();
						boolean first = true;
						for (SerialColumnComponent<?> col : ((SerialField<Object>) jdbcField).getColumnValues(value)) {
							if (canceled.getAsBoolean())
								return sql;
							if (first)
								first = false;
							else if (vc.isWithEqual())
								sql.append(" AND");
							else
								sql.append(" OR");
							// TODO qualify with nickname of the table whose field it is
							sql.append(' ').append(col.getColumn().getName()).append(symbol);
							col.writeValue(sql);
						}
					} else {
						boolean[] first = new boolean[] { true };
						for (Column<?> col : ((SerialField<Object>) jdbcField).getColumns()) {
							if (canceled.getAsBoolean())
								return sql;
							if (first[0])
								first[0] = false;
							else if (vc.isWithEqual())
								sql.append(" AND");
							else
								sql.append(" OR");
							// TODO qualify with nickname of the table whose field it is
							sql.append(' ').append(col.getName()).append(symbol).append('?');
							variableLocations.get(((VariableCondition<E, ?>) vc).getVariable().getName()).add(lastLocation[0]);
							lastLocation[0]++;
						}
					}
					if (compound && inner)
						sql.append(')');
				} else
					throw new UnsupportedOperationException("TODO Chain selection is not supported yet"); // TODO
			} else if (selection instanceof CompositeCondition) {
				String name = selection instanceof OrCondition ? "OR" : "AND";
				if (inner)
					sql.append('(');
				boolean firstComponent = true;
				for (EntityCondition<E> component : ((CompositeCondition<E>) selection).getConditions()) {
					if (canceled.getAsBoolean())
						return sql;
					if (firstComponent)
						firstComponent = false;
					else
						sql.append(' ').append(name).append(' ');
					appendCondition(sql, component, variableLocations, lastLocation, true, canceled);
				}
				if (inner)
					sql.append(')');
			} else
				throw new IllegalStateException("Unrecognized condition type: " + selection.getClass().getName());
			return sql;
		}

		protected <E2 extends E> SimpleEntity<E2> buildEntity(ResultSet results, ColumnData columns, boolean resolveSubType)
			throws SQLException {
			AbstractEntityTable<E2> entityType;
			if (resolveSubType)
				entityType = resolveEntityType(results, columns);
			else
				entityType = (AbstractEntityTable<E2>) this;
			EntityIdentity.Builder<E2> idBuilder = entityType.theType.buildId();
			for (ObservableEntityFieldType<E, ?> idField : theType.getIdentityFields().allValues()) {
				idBuilder.with(idField.getIdIndex(),
					((SerialField<Object>) theFields.get(idField.getIndex())).deserialize(results,
						col -> columns.indexOf(theNickName, col) + 1));
			}
			SimpleEntity<E2> entity = new SimpleEntity<>(idBuilder.build());
			entityType.fillEntity(entity, results, columns, true, true);
			return entity;
		}

		protected <E2 extends E> AbstractEntityTable<E2> resolveEntityType(ResultSet results, ColumnData columns)
			throws SQLException {
			for (AbstractEntityTable<? extends E> extension : theExtensions) {
				Column<?> column = ((SerialField<?>) extension.theFields.get(extension.getType().getIdentityFields().get(0).getIndex()))
					.getColumns().iterator().next();
				if (column.getColumn().deserialize(results, columns.indexOf(extension.theNickName, column.getName())) != null)
					return (AbstractEntityTable<E2>) extension.resolveEntityType(results, columns);
			}
			return (AbstractEntityTable<E2>) this;
		}

		protected void fillEntity(SimpleEntity<? extends E> entity, ResultSet results, ColumnData columns,
			boolean withSupers, boolean qualified) throws SQLException {
			for (int f = 0; f < theFields.keySize(); f++) {
				if (getField(entity, f) != EntityUpdate.NOT_SET)
					continue; // Already populated
				if (theType.getFields().get(f).getIdIndex() >= 0 || !theType.getFields().get(f).getOverrides().isEmpty()
					|| !(theFields.get(f) instanceof SerialField))
					continue;
				if (columns.contains(theNickName, ((SerialField<?>) theFields.get(f)).getColumns().iterator().next().getName()))
					entity.set(f, ((SerialField<Object>) theFields.get(f)).deserialize(results, col -> columns.indexOf(theNickName, col)));
			}
			if (withSupers) {
				for (AbstractEntityTable<? super E> superTable : theSupers)
					superTable.fillEntity(entity, results, columns, true, qualified);
			}
		}

		private Object getField(SimpleEntity<? extends E> entity, int f) {
			if (entity.getIdentity().getEntityType() == theType)
				return entity.getFields().get(f);
			else
				return entity.getFields().get(theFields.keySet().get(f));
		}

		@Override
		public String toString() {
			return getTableName();
		}
	}

	public abstract class AbstractCollectionTable<E, F> implements JoinedTable<E, F> {
		private final AbstractEntityTable<E> theTable;
		private final JoinedField<E, F> theField;

		public AbstractCollectionTable(AbstractEntityTable<E> table, JoinedField<E, F> field) {
			theTable = table;
			theField = field;
		}

		public AbstractEntityTable<E> getTable() {
			return theTable;
		}

		@Override
		public JoinedField<E, F> getJoinField() {
			return theField;
		}

		@Override
		public void modify(CollectionOperationType changeType, int index, int size, E owner, F value, Statement stmt,
			BooleanSupplier canceled) throws SQLException, EntityOperationException {
			StringBuilder sql = new StringBuilder();
			boolean[] first = new boolean[] { true };
			switch (changeType) {
			case add:
				if (index != size) {
					sql.append("UPDATE ");
					sql.append(getJoinField().getTableName()).append(" SET ").append(getJoinField().getIndexColumn()).append('=')
					.append(getJoinField().getIndexColumn()).append("+1 WHERE ");
					boolean compoundId = getJoinField().getOwner().getColumnCount() > 1;
					if (compoundId)
						sql.append('(');
					first[0] = true;
					for (SerialColumnComponent<?> col : getJoinField().getOwner().getColumnValues(owner)) {
						if (first[0])
							first[0] = false;
						else
							sql.append(" AND ");
						sql.append(col.getColumn().getName()).append('=');
						col.writeValue(sql);
					}
					if (compoundId)
						sql.append(')');
					if (index > 0)
						sql.append(" AND ").append(getJoinField().getIndexColumn()).append(">=").append(index);
					System.out.println(sql);
					stmt.executeUpdate(sql.toString());
					sql.setLength(0);
				}
				sql.append("INSERT INTO ");
				sql.append(getJoinField().getTableName()).append(" (");
				first[0] = true;
				for (Column<?> col : getJoinField().getOwner().getColumns()) {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					sql.append(col.getName());
				}
				if (getJoinField().getIndexColumn() != null)
					sql.append(", ").append(getJoinField().getIndexColumn());
				for (Column<?> col : ((SerialField<F>) getJoinField().getValue()).getColumns()) {
					sql.append(", ").append(col.getName());
				}
				sql.append(") VALUES (");
				first[0] = true;
				for (SerialColumnComponent<?> col : getJoinField().getOwner().getColumnValues(owner)) {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					col.writeValue(sql);
				}
				if (getJoinField().getIndexColumn() != null)
					sql.append(", ").append(index);
				for (SerialColumnComponent<?> col : ((SerialField<F>) getJoinField().getValue()).getColumnValues(value)) {
					sql.append(", ");
					col.writeValue(sql);
				}
				sql.append(')');
				System.out.println(sql);
				stmt.executeUpdate(sql.toString());
				return;
			case remove:
				sql.append("DELETE FROM ");
				sql.append(getJoinField().getTableName()).append(" WHERE ");
				first[0] = true;
				for (SerialColumnComponent<?> col : getJoinField().getOwner().getColumnValues(owner)) {
					if (first[0])
						first[0] = false;
					else
						sql.append(" AND ");
					sql.append(col.getColumn().getName()).append('=');
					col.writeValue(sql);
				}
				if (getJoinField().getIndexColumn() != null)
					sql.append(" AND ").append(getJoinField().getIndexColumn()).append('=').append(index);
				else {
					sql.append(" AND ");
					for (SerialColumnComponent<?> col : ((SerialField<F>) getJoinField().getValue()).getColumnValues(value)) {
						sql.append(col.getColumn().getName()).append('=');
						col.writeValue(sql);
					}
				}
				System.out.println(sql);
				int count = stmt.executeUpdate(sql.toString());
				if (count != 1) {
					System.err.println("Expected 1 row removed, but removed " + count);
				}
				if (getJoinField().getIndexColumn() != null && index < size - 1) {
					sql.setLength(0);
					sql.append("UPDATE ");
					sql.append(getJoinField().getTableName()).append(" SET ").append(getJoinField().getIndexColumn()).append('=')
					.append(getJoinField().getIndexColumn()).append("-1 WHERE ");
					boolean compoundId = getJoinField().getOwner().getColumnCount() > 1;
					if (compoundId)
						sql.append('(');
					first[0] = true;
					for (SerialColumnComponent<?> col : getJoinField().getOwner().getColumnValues(owner)) {
						if (first[0])
							first[0] = false;
						else
							sql.append(" AND ");
						sql.append(col.getColumn().getName()).append('=');
						col.writeValue(sql);
					}
					if (compoundId)
						sql.append(')');
					if (index > 0)
						sql.append(" AND ").append(getJoinField().getIndexColumn()).append(">").append(index);
					stmt.executeUpdate(sql.toString());
					System.out.println(sql);
					sql.setLength(0);
				} else
					index = -1;
				return;
			case update:
				sql.append("UPDATE ");
				sql.append(getJoinField().getTableName()).append(" SET ");
				first[0] = true;
				for (SerialColumnComponent<?> col : ((SerialField<F>) getJoinField().getValue()).getColumnValues(value)) {
					if (first[0])
						first[0] = false;
					else
						sql.append(", ");
					sql.append(col.getColumn().getName()).append('=');
					col.writeValue(sql);
				}
				sql.append(" WHERE ");
				first[0] = true;
				for (SerialColumnComponent<?> col : getJoinField().getOwner().getColumnValues(owner)) {
					if (first[0])
						first[0] = false;
					else
						sql.append(" AND ");
					sql.append(col.getColumn().getName()).append('=');
					col.writeValue(sql);
				}
				if (getJoinField().getIndexColumn() != null)
					sql.append(" AND ").append(getJoinField().getIndexColumn()).append('=').append(index);
				else {
					sql.append(" AND ");
					for (SerialColumnComponent<?> col : ((SerialField<F>) getJoinField().getValue()).getColumnValues(value)) {
						sql.append(col.getColumn().getName()).append('=');
						col.writeValue(sql);
					}
				}
				count = stmt.executeUpdate(sql.toString());
				if (count != 1) {
					System.err.println("Expected 1 row updated, but updated " + count);
				}
				System.out.println(sql);
				return;
			case clear:
				sql.append("DELETE FROM ");
				sql.append(getJoinField().getTableName()).append(" WHERE ");
				first[0] = true;
				for (SerialColumnComponent<?> col : getJoinField().getOwner().getColumnValues(owner)) {
					if (first[0])
						first[0] = false;
					else
						sql.append(" AND ");
					sql.append(col.getColumn().getName()).append('=');
					col.writeValue(sql);
				}
				System.out.println(sql);
				count = stmt.executeUpdate(sql.toString());
				if (count != size) {
					System.err.println("Expected " + size + " row(s) removed, but removed " + count);
				}
				return;
			}
			throw new IllegalStateException("" + changeType);
		}

		static final int MAX_RANGES_PER_QUERY = 25;
		static final int MAX_IDS_PER_QUERY = 100;

		private void load(EntityLoadRequest<E> loadRequest, ObservableEntityFieldType<E, ?> field, JoinedField<E, F> column,
			SimpleEntity<E>[] results, Statement stmt) throws SQLException {
			if (!(column.getValue() instanceof SerialField)) {
				System.err.println("Non-serial values of joined fields not supported");
				return;
			}
			StringBuilder sql = new StringBuilder("SELECT ");
			StringBuilder order = new StringBuilder(" ORDER BY ");
			{
				int[] columnIndex = new int[1];
				for (Column<?> col : column.getOwner().getColumns()) {
					if (columnIndex[0] > 0) {
						sql.append(", ");
						order.append(", ");
					}
					sql.append(col.getName());
					order.append(col.getName());
					columnIndex[0]++;
				}
				if (column.getIndexColumn() != null) {
					sql.append(", ").append(column.getIndexColumn());
					order.append(", ").append(column.getIndexColumn());
					columnIndex[0]++;
				}
				for (Column<?> col : ((SerialField<F>) column.getValue()).getColumns()) {
					sql.append(", ").append(col.getName());
					columnIndex[0]++;
				}
			}
			sql.append(" FROM ").append(column.getTableName()).append(" WHERE ");
			List<BiTuple<String, int[]>> queries;
			// See if we can grab everything in one query. This is possible if the list of entities is small
			// and either the owning entity has a simple identity (one field)
			// or the identities of all the entities to be loaded are equivalent but for one field
			if (loadRequest.getEntities().size() == 1) {
				boolean[] firstCol = new boolean[] { true };
				StringBuilder queryStr = new StringBuilder();
				for (SerialColumnComponent<?> col : column.getOwner().getColumnValues((E) loadRequest.getEntities().get(0))) {
					if (firstCol[0])
						firstCol[0] = false;
					else
						queryStr.append(" AND ");
					queryStr.append(col.getColumn().getName()).append('=');
					col.writeValue(queryStr);
				}
				queries = Collections.singletonList(new BiTuple<>(queryStr.toString(), sequence(0, 1)));
			} else if (column.getOwner().getColumnCount() == 1) {
				// Single ID column
				Column<E> idColumn = (Column<E>) column.getOwner().getColumns().iterator().next();
				List<BiTuple<String[], int[]>> ranges = getContinuousRanges(loadRequest.getEntities(),
					loadRequest.getType().getIdentityFields().get(0));
				if (ranges.size() == 1) {
					queries = Collections.singletonList(new BiTuple<>(//
						new StringBuilder().append(idColumn.getName()).append(">=").append(ranges.get(0).getValue1()[0])//
						.append(" AND ").append(idColumn.getName()).append("<=").append(ranges.get(0).getValue1()[1]).toString(), //
						sequence(0, loadRequest.getEntities().size())));
				} else if (Math.ceil(ranges.size() * 1.0 / MAX_RANGES_PER_QUERY) <= Math
					.ceil(loadRequest.getEntities().size() * 1.0 / MAX_IDS_PER_QUERY)) {
					IntList entities = new IntList();
					StringBuilder str = new StringBuilder();
					int querySize = ranges.size() / ((int) Math.ceil(ranges.size() / MAX_RANGES_PER_QUERY));
					queries = new ArrayList<>(querySize);
					for (int r = 0; r < ranges.size(); r++) {
						if (r % querySize == querySize - 1) {
							queries.add(new BiTuple<>(str.toString(), entities.toArray()));
							str.setLength(0);
							entities.clear();
						}
						if (str.length() > 0)
							str.append(" OR ");
						BiTuple<String[], int[]> range = ranges.get(r);
						if (range.getValue1().length == 1)
							str.append(idColumn.getName()).append('=').append(range.getValue1()[0]);
						else
							str.append('(').append(idColumn.getName()).append(">=").append(range.getValue1()[0])//
							.append(" AND ").append(idColumn.getName()).append("<=").append(range.getValue1()[1]).append(')');
						entities.addAll(range.getValue2());
					}
					queries.add(new BiTuple<>(str.toString(), entities.toArray()));
				} else {
					// Using IN clause(s) will be simpler and likely be faster
					int querySize = loadRequest.getEntities().size()
						/ ((int) Math.ceil(loadRequest.getEntities().size() / MAX_IDS_PER_QUERY));
					queries = new ArrayList<>(querySize);
					StringBuilder str = new StringBuilder(idColumn.getName()).append(" IN (");
					int start = 0;
					for (int i = 0; i < loadRequest.getEntities().size(); i++) {
						int mod = i % querySize;
						if (mod == querySize - 1) {
							str.append(')');
							queries.add(new BiTuple<>(str.toString(), sequence(start, i)));
							str.setLength(0);
							start = i;
						} else if (mod != 0)
							str.append(", ");
						idColumn.getColumn().serialize((E) loadRequest.getEntities().get(i), str);
					}
					str.append(')');
					queries.add(new BiTuple<>(str.toString(), sequence(start, loadRequest.getEntities().size())));
				}
			} else {
				// TODO Multiple identity fields
				throw new UnsupportedOperationException();
			}
			Map<EntityIdentity<E>, Integer> sortedIds = new TreeMap<>();
			for (int i = 0; i < loadRequest.getEntities().size(); i++) {
				results[i].set(field.getIndex(), column.createInitialValue(loadRequest.getEntities().get(i), this));
				sortedIds.put(loadRequest.getEntities().get(i), i);
			}
			for (BiTuple<String, int[]> query : queries) {
				try (ResultSet rs = stmt.executeQuery(sql + query.getValue1() + order)) {
					Iterator<Map.Entry<EntityIdentity<E>, Integer>> entityIter = sortedIds.entrySet().iterator();
					Map.Entry<EntityIdentity<E>, Integer> entity = entityIter.next();
					while (rs.next()) {
						while (!matches(entity.getKey(), rs))
							entity = entityIter.next();
						column.addValue(//
							results[entity.getValue()].getFields().get(field.getIndex()), //
							rs, getJoinField().getTableName());
					}
				}
			}
		}

		private boolean matches(EntityIdentity<E> key, ResultSet rs) throws SQLException {
			for (ObservableEntityFieldType<E, ?> idField : key.getEntityType().getIdentityFields().allValues()) {
				ObservableEntityFieldType<? super E, ?> rootField = idField;
				while (!rootField.getOverrides().isEmpty())
					rootField = rootField.getOverrides().get(0);
				SerialField<?> column = (SerialField<?>) theTable.theFields.get(idField.getIndex());
				Object serial = column.deserialize(rs, rootField.getOwnerType().getName());
				if (!Objects.equals(key.getFields().get(idField.getIdIndex()), serial))
					return false;
			}
			return true;
		}

		private int[] sequence(int start, int end) {
			int[] seq = new int[end - start];
			for (int i = 0; i < seq.length; i++)
				seq[i] = start + i;
			return seq;
		}

		private <I extends Comparable<I>> List<BiTuple<String[], int[]>> getContinuousRanges(List<EntityIdentity<E>> entities,
			ObservableEntityFieldType<E, ?> idField) {
			SerialField<?> idColumn = (SerialField<?>) theTable.theFields.get(idField.getIndex());
			Column<I> simpleIdColumn = (Column<I>) idColumn.getColumns().iterator().next();
			if (!simpleIdColumn.getColumn().testsAdjacent())
				return null; // No adjacent test, can't detect ranges
			int idIndex = idField.getIdIndex();
			// Get the sorted list of IDs to select
			List<EntityIdentity<? extends E>> sorted = new ArrayList<>(entities.size());
			for (EntityIdentity<? extends E> entity : entities) {
				int idx = Collections.<EntityIdentity<? extends E>> binarySearch(sorted, entity, (e1, e2) -> {
					I id1 = (I) e1.getFields().get(idIndex);
					I id2 = (I) e2.getFields().get(idIndex);
					return id1.compareTo(id2);
				});
				sorted.add(-idx - 1, entity); // Assume no duplicates, index will be negative
			}
			// Group the IDs into ranges
			if (sorted.size() == 1) {
				String serialized = simpleIdColumn.getColumn().serialize((I) sorted.get(0).getFields().get(idIndex), new StringBuilder())
					.toString();
				return Collections.singletonList(new BiTuple<>(new String[] { serialized }, sequence(0, entities.size())));
			}
			List<BiTuple<String[], int[]>> ranges = new ArrayList<>();
			IntList rangeEntities = new IntList();
			rangeEntities.add(0);
			I start = (I) sorted.get(0).getFields().get(idIndex), end = null;
			for (int i = 1; i < sorted.size(); i++) {
				I id = (I) sorted.get(i).getFields().get(idIndex);
				if (!simpleIdColumn.getColumn().isAdjacent(start, id)) {
					if (end == null)
						ranges.add(new BiTuple<>(//
							new String[] { simpleIdColumn.getColumn().serialize(start, new StringBuilder()).toString() }, //
							rangeEntities.toArray()));
					else
						ranges.add(new BiTuple<>(
							new String[] { //
								simpleIdColumn.getColumn().serialize(start, new StringBuilder()).toString(),
								simpleIdColumn.getColumn().serialize(end, new StringBuilder()).toString() }, //
							rangeEntities.toArray()));
					rangeEntities.clear();
					start = id;
					end = null;
				} else
					end = id;
				rangeEntities.add(i);
			}
			return ranges;
		}

		@Override
		public String toString() {
			return theTable + "." + theField.getTableName();
		}
	}

	protected class ColumnData {
		private final List<TableColumns> theTables = new ArrayList<>(6);

		public boolean isEmpty() {
			return theTables.isEmpty();
		}

		public boolean contains(String tableNick, String columnName) {
			return indexOf(tableNick, columnName) >= 0;
		}

		public int indexOf(String tableNick, String columnName) {
			int tableIdx = ArrayUtils.binarySearch(theTables, t -> tableNick.compareTo(t.theTable.theNickName));
			if (tableIdx < 0)
				return -1;
			int colIdx = theTables.get(tableIdx).getColumnIndex(columnName);
			if (colIdx < 0)
				return -1;
			int preColumns = 0;
			for (int i = 0; i < tableIdx; i++)
				preColumns += theTables.get(tableIdx).theColumns.size();
			return preColumns + colIdx;
		}

		public void addTable(AbstractEntityTable<?> table, boolean withIds, boolean withSupers, boolean withExtensions,
			Predicate<ObservableEntityFieldType<?, ?>> fieldFilter) {
			int tableIdx = ArrayUtils.binarySearch(theTables, t -> table.theNickName.compareTo(t.theTable.theNickName));
			TableColumns columns;
			if (tableIdx < 0)
				columns = new TableColumns(table, withIds);
			else
				columns = theTables.get(tableIdx);
			for (int f = 0; f < table.theFields.keySize(); f++) {
				if (!(table.theFields.get(f) instanceof SerialField))
					continue;
				else if (table.getType().getFields().get(f).getIdIndex() >= 0) {
					if (withIds)
						columns.addColumn((SerialField<?>) table.theFields.get(f));
				} else if (fieldFilter == null || fieldFilter.test(table.getType().getFields().get(f)))
					columns.addColumn((SerialField<?>) table.theFields.get(f));
			}
			if (withSupers) {
				for (AbstractEntityTable<?> superTable : table.getSupers())
					addTable(superTable, false, true, false, fieldFilter);
			}
			if (withExtensions) {
				for (AbstractEntityTable<?> subTable : table.getExtensions())
					addTable(subTable, false, false, true, fieldFilter);
			}
			if (tableIdx < 0 && !columns.theColumns.isEmpty())
				theTables.add(-tableIdx - 1, columns);
		}

		public StringBuilder toSql(StringBuilder sql) {
			boolean first = true;
			for (TableColumns table : theTables) {
				for (String column : table.theColumns) {
					if (first)
						first = false;
					else
						sql.append(", ");
					sql.append(table.theTable.theNickName).append('.').append(column);
				}
			}
			return sql;
		}

		public List<String> getColumnNames() {
			List<String> columns = new ArrayList<>();
			for (TableColumns table : theTables) {
				for (String column : table.theColumns)
					columns.add(table.theTable.theNickName + "." + column);
			}
			return columns;
		}

		@Override
		public String toString() {
			return toSql(new StringBuilder()).toString();
		}

		private class TableColumns {
			final AbstractEntityTable<?> theTable;
			final List<String> theColumns;

			TableColumns(AbstractEntityTable<?> table, boolean withIds) {
				theTable = table;
				theColumns = new ArrayList<>(table.theColumnCount - (withIds ? 0 : table.theIdColumnCount));
			}

			int getColumnIndex(String columnName) {
				int colIdx = Collections.binarySearch(theColumns, columnName);
				return colIdx;
			}

			void addColumn(SerialField<?> column) {
				for (Column<?> c : column.getColumns()) {
					int colIdx = Collections.binarySearch(theColumns, c.getName());
					if (colIdx < 0)
						theColumns.add(-colIdx - 1, c.getName());
				}
			}
		}
	}
}
