package org.observe.entity.ui;

import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.OperationResult;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityCollectionResult;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseAdapter;
import org.observe.util.swing.Dragging;
import org.observe.util.swing.ModelCell;
import org.observe.util.swing.ObservableTextField;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

public class ObservableEntityExplorer extends JPanel {
	private final ObservableConfig theConfig;
	private final ObservableValue<ObservableEntityDataSet> theDataSet;
	private final ObservableSortedSet<EntityTypeData<?>> theEntityTypes;
	private final ObservableCollection<Object> theEventLog;
	private final SettableValue<Integer> theLogLength;

	private final SettableValue<EntityTypeData<?>> theSelectedEntityType;

	public ObservableEntityExplorer(ObservableConfig config, ObservableValue<ObservableEntityDataSet> dataSet) {
		theConfig = config;
		theDataSet = dataSet;
		TypeToken<ObservableEntityType<?>> oetType = new TypeToken<ObservableEntityType<?>>() {};
		TypeToken<EntityTypeData<?>> oetHolderType = new TypeToken<EntityTypeData<?>>() {};
		TypeToken<ObservableCollection<ObservableEntityType<?>>> oetListType = ObservableCollection.TYPE_KEY
			.<ObservableCollection<ObservableEntityType<?>>> getCompoundType(oetType);
		ObservableValue<ObservableCollection<ObservableEntityType<?>>> typeListValue = theDataSet.map(oetListType,
			ds -> ObservableCollection.of(oetType, ds == null ? Collections.emptyList() : ds.getEntityTypes()));
		theEntityTypes = ObservableCollection.flattenValue(typeListValue)//
			.flow().distinctSorted(Named.DISTINCT_NUMBER_TOLERANT, false)//
			.mapEquivalent(oetHolderType, (entity, holder) -> {
				if (holder != null) {
					if (holder.entity == entity)
						return holder;
					holder.count.cancel(true);
				}
				return new EntityTypeData<>(entity);
			}, holder -> holder.entity, opts -> opts.cache(true).reEvalOnUpdate(false))//
			.refreshEach(e -> Observable.or(e.count.watchStatus(), e.count.getResult().noInitChanges()))//
			.collect();
		theEventLog = ObservableCollection.build(Object.class).build();

		theLogLength = config.asValue(int.class).at("log-length").withFormat(Format.INT, () -> 1000).buildValue(null);

		theSelectedEntityType = SettableValue.build(oetHolderType).build();
		initComponents(PanelPopulation.populateVPanel(this, null));

		Observable.flatten(theDataSet.value().map(ds -> ds == null ? null : ds.changes())).act(changes -> log(changes));
		theDataSet.noInitChanges().act(evt -> {
			if (evt.getOldValue() == evt.getNewValue())
				return;
			List<Object> events = new ArrayList<>(2);
			if (evt.getOldValue() != null)
				events.add("Disconnected");
			if (evt.getNewValue() != null)
				events.add("Connected");
			log(events);
		});
	}

	private void log(Collection<?> events) {
		try (Transaction t1 = theLogLength.lock(); Transaction t2 = theEventLog.lock(true, null)) {
			int length = theLogLength.get();
			if (length == 0)
				return;
			if (events.size() >= length)
				theEventLog.clear();
			else if (events.size() == 1 && theEventLog.size() == length)
				theEventLog.removeFirst();
			else if (theEventLog.size() + events.size() > length) {
				int remove = theEventLog.size() + events.size() - length;
				theEventLog.subList(0, remove).clear();
			}
			theEventLog.addAll(events);
		}
	}

	protected void initComponents(PanelPopulator<?, ?> panel) {
		panel.fill().fillV().visibleWhen(theDataSet.map(ds -> ds != null))//
		.addSplit(false, split -> {
			split.firstV(mainPanel -> mainPanel.addSplit(true, secondSplit -> {
				secondSplit.firstV(this::initEntityTypeTable).lastV(this::initStatusTable);
			})).lastV(this::initEntityTypePanel).withSplitProportion(.5);
		});
	}

	protected void initEntityTypeTable(PanelPopulator<?, ?> panel) {
		panel.addTable(theEntityTypes, table -> {
			table.fill().fillV()//
			.withNameColumn(e -> e.entity.getName(), null, true, null)//
			.withColumn("ID", String.class, e -> e.printIdFields(), null)//
			.withColumn("Fields", String.class, e -> e.printOtherFields(), null)//
			.withColumn("Count", String.class, e -> e.printCount(),
				countCol -> countCol.withValueTooltip((e, c) -> e.printCountStatus())//
				.withMouseListener(new CategoryMouseAdapter<EntityTypeData<?>, String>() {
					@Override
					public void mouseClicked(ModelCell<? extends EntityTypeData<?>, ? extends String> cell, MouseEvent e) {
						cell.getModelValue().printError();
					}
				}))//
			.withSelection(theSelectedEntityType, true);
		});
	}

	protected void initStatusTable(PanelPopulator<?, ?> panel) {
		// TODO
	}

	private static TypeToken<ObservableEntityFieldType<?, ?>> FIELD_TYPE = new TypeToken<ObservableEntityFieldType<?, ?>>() {};

	protected void initEntityTypePanel(PanelPopulator<?, ?> panel) {
		ObservableCollection<ObservableEntityFieldType<?, ?>> fields = ObservableCollection.flattenValue(theSelectedEntityType
			.map(e -> ObservableCollection.of(FIELD_TYPE, e == null ? Collections.emptyList() : e.entity.getFields().allValues())));
		SettableValue<EntityCondition<?>> condition = SettableValue.build(new TypeToken<EntityCondition<?>>() {}).safe(false).build();
		ObservableTextField<EntityCondition<?>>[] conditionTF = new ObservableTextField[1];
		theSelectedEntityType.noInitChanges()
		.act(evt -> condition.set(evt.getNewValue() == null ? null : evt.getNewValue().entity.select(), evt));
		panel.fill().fillV().visibleWhen(theSelectedEntityType.map(e -> e != null))//
		.addLabel("Name:", theSelectedEntityType.map(e -> e == null ? "" : e.entity.getName()), Format.TEXT,
			f -> f.decorate(d -> d.bold().withFontSize(18)))//
		.addLabel("Count:", theSelectedEntityType.map(e -> e == null ? "" : e.printCount()), Format.TEXT, null)//
		.addTable(fields, this::configureFieldTable).addHPanel(null, "box", buttonPanel -> {
			buttonPanel.addButton("Create", __ -> showCreatePanel(theSelectedEntityType.get().entity.create()), null)//
			.addTextField("Select", condition, new ConditionFormat(() -> theSelectedEntityType.get().entity),
				tf -> tf.modifyEditor(tf2 -> {
					conditionTF[0] = tf2;
					tf2.withColumns(35);
				}).withPostButton("Query", __ -> showQueryPanel(condition.get()),
					button -> button.disableWith(conditionTF[0].getErrorState())));
		});
	}

	static class ConditionFormat implements Format<EntityCondition<?>> {
		private final Supplier<ObservableEntityType<?>> theEntityType;

		ConditionFormat(Supplier<ObservableEntityType<?>> entityType) {
			theEntityType = entityType;
		}

		@Override
		public void append(StringBuilder text, EntityCondition<?> value) {
			if (value instanceof EntityCondition.CompositeCondition) {
				String join = value instanceof EntityCondition.AndCondition ? " AND " : " OR ";
				boolean first = true;
				for (EntityCondition<?> component : ((EntityCondition.CompositeCondition<?>) value).getConditions()) {
					if (first)
						first = false;
					else
						text.append(join);
					boolean complex = component instanceof EntityCondition.CompositeCondition;
					if (complex)
						text.append('(');
					append(text, component);
					if (complex)
						text.append(')');
				}
			} else
				text.append(value);
		}

		@Override
		public EntityCondition<?> parse(CharSequence text) throws ParseException {
			ObservableEntityType<?> type = theEntityType.get();
			return _parse(type, text, 0, null);
		}

		private <E> EntityCondition<E> _parse(ObservableEntityType<E> type, CharSequence text, int c, IntConsumer post)
			throws ParseException {
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (c == text.length())
				return type.select();
			EntityCondition<E> first;
			if (text.charAt(c) == ')') {
				int endParen = c + 1;
				while (endParen < text.length() && text.charAt(endParen) != ')')
					endParen++;
				if (endParen == text.length())
					throw new ParseException("Unmatched parenthesis", c);
				first = _parse(type, text.subSequence(0, endParen), c + 1, null);
			} else {
				int[] postI = new int[1];
				first = parseSimple(type, text, c, i -> postI[0] = i);
				c = postI[0];
			}
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (c == text.length())
				return first;
			// I realize that this completely disregards order of operations. I'm just trying to do something very simple here.
			// Maybe I'll come back around and use ANTLR later
			if (text.length() - c < 3)
				throw new ParseException("Unrecognized condition join", c);
			String join = text.subSequence(c, c + 3).toString().toUpperCase();
			int[] postI = new int[1];
			if (join.equals("AND")) {
				EntityCondition<E> next = _parse(type, text, c + 3, i -> postI[0] = i);
				first = first.and(all -> next);
			} else if (join.equals("OR")) {
				EntityCondition<E> next = _parse(type, text, c + 3, i -> postI[0] = i);
				first = first.or(all -> next);
			} else
				throw new ParseException("Unrecognized condition join", c);
			c = postI[0];
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (post != null)
				post.accept(c);
			else if (c != text.length())
				throw new ParseException("Unrecognized content after condition", c);
			return first;
		}

		private <E, F> EntityCondition.ValueCondition<E, F> parseSimple(ObservableEntityType<E> type, CharSequence text, int c,
			IntConsumer post) throws ParseException {
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (c == text.length())
				throw new ParseException("Empty condition", c);
			int f = c;
			while (f < text.length() && isFieldChar(text.charAt(f)))
				f++;
			String fieldName = text.subSequence(c, f).toString();
			ObservableEntityFieldType<E, F> field = (ObservableEntityFieldType<E, F>) type.getFields().getIfPresent(fieldName);
			if (field == null)
				throw new ParseException("No such field " + type.getName() + "." + fieldName, c);
			c = f;
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (c + 1 >= text.length())
				throw new ParseException("No condition on field " + field, c);
			// TODO use dot
			if (text.charAt(c) == '=')
				return type.select().where(field).eq().value(parseValue(field, text, c + 1, post));
			if (Character.toUpperCase(text.charAt(c)) == 'I' && Character.toUpperCase(text.charAt(c + 1)) == 'S') {
				c += 2;
				while (c < text.length() && Character.isWhitespace(text.charAt(c)))
					c++;
				if (c + 4 >= text.length())
					throw new ParseException("Invalid condition on field " + field, c);
				boolean not = false;
				if ("NOT".equals(text.subSequence(c, c + 3).toString().toUpperCase())) {
					not = true;
					c += 3;
					while (c < text.length() && Character.isWhitespace(text.charAt(c)))
						c++;
					if (c + 4 >= text.length())
						throw new ParseException("Invalid condition on field " + field, c);
				}
				if ("NULL".equals(text.subSequence(c, c + 4).toString().toUpperCase()))
					return type.select().where(field).NULL(!not);
				else
					throw new ParseException("Invalid condition on field " + field, c);
			}
			boolean eq = text.charAt(c + 1) == '=';
			int offset = eq ? 2 : 1;
			switch (text.charAt(c)) {
			case '<':
				return type.select().where(field).compare(-1, eq).value(parseValue(field, text, c + offset, post));
			case '>':
				return type.select().where(field).compare(1, eq).value(parseValue(field, text, c + offset, post));
			case '!':
				if (!eq)
					throw new ParseException("Unrecognized operator", c);
				return type.select().where(field).neq().value(parseValue(field, text, c + offset, post));
			default:
				throw new ParseException("Unrecognized operator", c);
			}
		}

		private boolean isFieldChar(char c) {
			if (c >= '0' && c <= '9')
				return true;
			else if (c >= 'a' && c <= 'z')
				return true;
			else if (c >= 'A' && c <= 'Z')
				return true;
			switch (c) {
			case '_':
			case '-':
				return true;
			}
			return false;
		}

		private <E, F> F parseValue(ObservableEntityFieldType<E, F> field, CharSequence text, int c, IntConsumer post)
			throws ParseException {
			while (c < text.length() && Character.isWhitespace(text.charAt(c)))
				c++;
			if (c == text.length())
				throw new ParseException("Empty value", c);

			Class<F> raw = TypeTokens.getRawType(field.getFieldType().unwrap());
			if (raw == String.class) {
				char quote = text.charAt(c);
				if (quote != '"' && quote != '\'')
					throw new ParseException("String must begin with a single or double quote", c);
				c++;
				boolean escape = false;
				StringBuilder str = new StringBuilder();
				while (c < text.length() && (escape || text.charAt(c) != quote)) {
					if (escape) {
						switch (text.charAt(c)) {
						case '\\':
						case '\'':
						case '"':
							str.append(text.charAt(c));
							break;
						case 'n':
							str.append('\n');
							break;
						case 'r':
							str.append('\r');
							break;
						case 't':
							str.append('\t');
							break;
						case 'u':
							c++;
							if (c + 4 >= text.length())
								throw new ParseException("Invalid unicode char expression", c);
							try {
								str.append((char) Integer.parseInt(text.subSequence(c, c + 4).toString(), 16));
							} catch (NumberFormatException e) {
								throw new ParseException("Invalid unicode char expression", c);
							}
							break;
						default:
							throw new ParseException("Invalid escaped character", c);
						}
					} else if (text.charAt(c) == '\\')
						escape = true;
					else
						str.append(text.charAt(c));
				}
				if (c == text.length())
					throw new ParseException("String must end with " + quote, c);
				return (F) str.toString();
			}
			if (raw == int.class || raw == long.class) {
				StringBuilder str = new StringBuilder();
				if (text.charAt(c) == '-') {
					str.append('-');
					c++;
					while (c < text.length() && Character.isWhitespace(text.charAt(c)))
						c++;
					if (c == text.length())
						throw new ParseException("Invalid int value", c);
				}
				while (c < text.length()) {
					if (text.charAt(c) == '_')
						continue;
					else if (text.charAt(c) >= '0' && text.charAt(c) <= '9')
						str.append(text.charAt(c));
				}
				if (raw == int.class)
					return (F) Integer.valueOf(str.toString());
				else
					return (F) Long.valueOf(str.toString());
			} else if (Enum.class.isAssignableFrom(raw)) {
				StringBuilder str = new StringBuilder();
				int enumStart = c;
				while (c < text.length() && isFieldChar(text.charAt(c))) {
					str.append(text.charAt(c));
					c++;
				}
				String enumName = str.toString();
				for (F val : raw.getEnumConstants()) {
					if (((Enum<?>) val).name().equals(enumName))
						return val;
				}
				throw new ParseException("Unrecognized " + raw.getSimpleName() + " constant " + enumName, enumStart);
			} else
				throw new ParseException("Unable to parse type " + raw.getName(), c);
		}
	}

	protected void configureFieldTable(PanelPopulation.TableBuilder<ObservableEntityFieldType<?, ?>, ?> table) {
		table.fill().withNameColumn(ObservableEntityFieldType::getName, null, true, null)//
		.withColumn("Type", new TypeToken<TypeToken<?>>() {}, ObservableEntityFieldType::getFieldType, null);
	}

	<E> void showCreatePanel(ConfigurableCreator<?, E> initCreator) {
		ConfigurableCreator<?, E>[] creator = new ConfigurableCreator[] { initCreator };
		EntityRowUpdater<Object, E> updater = new EntityRowUpdater<Object, E>() {
			@Override
			public String isEnabled(Object entity, int fieldIndex) {
				return creator[0].isEnabled(initCreator.getEntityType().getFields().get(fieldIndex));
			}

			@Override
			public <F> String isAcceptable(Object entity, int fieldIndex, F value) {
				return creator[0].isAcceptable((ObservableEntityFieldType<E, F>) initCreator.getEntityType().getFields().get(fieldIndex),
					value);
			}

			@Override
			public <F> void set(Object entity, int fieldIndex, F value) {
				creator[0] = creator[0].with((ObservableEntityFieldType<E, F>) initCreator.getEntityType().getFields().get(fieldIndex),
					value);
			}
		};
		ObservableCollection<Object> row = ObservableCollection.build(Object.class).safe(false).build().with((Object) null);
		JPanel createPanel = PanelPopulation.populateVPanel((JPanel) null, null)//
			.<Object> addTable(row, fieldTable -> {
				for (ObservableEntityFieldType<E, ?> field : initCreator.getEntityType().getFields().allValues()) {
					ObservableEntityFieldType<E, Object> f = (ObservableEntityFieldType<E, Object>) field;
					fieldTable.withColumn(field.getName(), (TypeToken<Object>) field.getFieldType(), __ -> {
						return creator[0].getFieldValues().get(field.getIndex());
					}, fieldCol -> configureFieldColumn(f.getIndex(), f.getFieldType(), f.getTargetEntity(), fieldCol, updater, null));
				}
			})//
			.addButton("Create", __ -> {
				try {
					creator[0].create();
				} catch (EntityOperationException e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(ObservableEntityExplorer.this,
						"Could not create " + initCreator.getEntityType() + ": " + e.getMessage(), "Creation Failed",
						JOptionPane.ERROR_MESSAGE);
				}
			}, button -> button
				.disableWith(ObservableValue.of(TypeTokens.get().STRING, () -> creator[0].canCreate(), row::getStamp, row.simpleChanges())))
			.getContainer();
		JDialog createDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Create " + initCreator.getEntityType().getName(),
			ModalityType.MODELESS);
		createDialog.getContentPane().add(createPanel);
		createDialog.pack();
		createDialog.setVisible(true);
	}

	public static class EntityFlavor extends DataFlavor {
		public final ObservableEntityType<?> entity;

		public EntityFlavor(ObservableEntityType<?> entity) {
			super("application/x-entity-" + entity.getName(), entity.getName());
			this.entity = entity;
		}
	}

	private <E> void showQueryPanel(EntityCondition<E> condition) {
		EntityCollectionResult<E> results;
		try {
			results = condition.query().collect(true);
		} catch (EntityOperationException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, e.getMessage(), "Query Failure", JOptionPane.ERROR_MESSAGE);
			return;
		}
		SimpleObservable<Void> close = SimpleObservable.build().safe(false).build();
		EntityRowUpdater<ObservableEntity<? extends E>, E> updater = new EntityRowUpdater<ObservableEntity<? extends E>, E>() {
			@Override
			public String isEnabled(ObservableEntity<? extends E> entity, int fieldIndex) {
				return entity.getField(fieldIndex).isEnabled().get();
			}

			@Override
			public <F> String isAcceptable(ObservableEntity<? extends E> entity, int fieldIndex, F value) {
				return entity.isAcceptable(fieldIndex, value);
			}

			@Override
			public <F> void set(ObservableEntity<? extends E> entity, int fieldIndex, F value) {
				entity.set(fieldIndex, value, null);
			}
		};
		SettableValue<String> deleteText = SettableValue.build(String.class).safe(false).withValue("Delete").build();
		JPanel queryPanel = PanelPopulation.populateVPanel((JPanel) null, close)//
			.addTable((ObservableCollection<ObservableEntity<E>>) results.getResult().getEntities(), entityTable -> {
				entityTable.withIndexColumn("Row #", col -> col.formatText(idx -> String.valueOf(idx + 1)));
				for (ObservableEntityFieldType<E, ?> field : condition.getEntityType().getFields().allValues()) {
					ObservableEntityFieldType<E, Object> f = (ObservableEntityFieldType<E, Object>) field;
					entityTable.withColumn(field.getName(), (TypeToken<Object>) field.getFieldType(), entity -> {
						return entity.get(field);
					}, //
						fieldCol -> configureFieldColumn(f.getIndex(), f.getFieldType(), f.getTargetEntity(), fieldCol, updater, close));
				}
				entityTable.dragSourceRow(rowDrag -> rowDrag.advertiseFlavor(new EntityFlavor(condition.getEntityType()))//
					.toFlavorLike(
						flavor -> flavor instanceof EntityFlavor
						&& ((EntityFlavor) flavor).entity.isAssignableFrom(condition.getEntityType()),
						new Dragging.DataSourceTransform<ObservableEntity<E>>() {
							@Override
							public boolean canTransform(Object value, DataFlavor flavor) {
								return true;
							}

							@Override
							public Object transform(ObservableEntity<E> value, DataFlavor flavor) throws IOException {
								return value;
							}
						}));
				entityTable.withRemove(null, null);
			})//
			.addHPanel(null, "box", buttons -> {
				buttons.addButton("Create...", __ -> showCreatePanel(results.getResult().create()), null);
				buttons.addButton("Delete", cause -> {
					int count = results.getResult().getValues().size();
					String msg = "Delete " + count + " "
						+ (count == 1 ? condition.getEntityType().getName() : StringUtils.pluralize(condition.getEntityType().getName()))
						+ "?";
					if (!buttons.alert(msg, "Are you sure you want to delete " + (count == 1 ? "this entity" : "these entities") + "?")//
						.confirm(true))
						return;
					try {
						deleteText.set("Waiting...", null);
						EntityModificationResult<E> deleteResult = condition.delete().execute(false, cause);
						deleteResult.watchStatus().act(__ -> {
							EventQueue.invokeLater(() -> {
								switch (deleteResult.getStatus()) {
								case WAITING:
									break;
								case EXECUTING:
									deleteText.set("Executing...", null);
									break;
								case FAILED:
									buttons.alert("Delete Failed", deleteResult.getFailure().getMessage()).error().display();
									deleteResult.getFailure().printStackTrace();
									deleteText.set("Delete", null);
									break;
								case CANCELLED:
									buttons.alert("Delete Cancelled", "Canceled");
									deleteText.set("Delete", null);
									break;
								case FULFILLED:
									buttons.alert("Delete Successful",
										deleteResult.getResult() + " "//
										+ (deleteResult.getResult() == 1 ? condition.getEntityType().getName()
											: StringUtils.pluralize(condition.getEntityType().getName()))
										+ " deleted")
									.display();
									deleteText.set("Delete", null);
									break;
								}
							});
						});
					} catch (EntityOperationException e) {
						buttons.alert("Delete Failed", e.getMessage()).error().display();
						e.printStackTrace();
					}
				}, button -> button.withText(deleteText)
					.disableWith(results.getResult().getValues().observeSize().map(sz -> sz == 0 ? "No entities to delete" : null)));
			})//
			.getContainer();
		JDialog createDialog = new JDialog(SwingUtilities.getWindowAncestor(this), condition.getEntityType() + ": " + condition.toString(),
			ModalityType.MODELESS);
		createDialog.getContentPane().add(queryPanel);
		createDialog.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				close.onNext(null);
			}
		});
		createDialog.pack();
		createDialog.setVisible(true);
	}

	interface EntityRowUpdater<T, E> {
		String isEnabled(T entity, int fieldIndex);

		<F> String isAcceptable(T entity, int fieldIndex, F value);

		<F> void set(T entity, int fieldIndex, F value);
	}

	protected <T, E, F> void configureFieldColumn(int fieldIndex, TypeToken<F> fieldType, ObservableEntityType<F> targetEntity,
		CategoryRenderStrategy<T, F> fieldColumn, EntityRowUpdater<? super T, E> creator, Observable<?> expire) {
		boolean editable = configureFieldFormat(fieldType, targetEntity, fieldColumn, expire);
		if (targetEntity != null) {
			fieldColumn.withKeyListener(new CategoryRenderStrategy.CategoryKeyAdapter<T, F>() {
				@Override
				public void keyTyped(ModelCell<? extends T, ? extends F> cell, KeyEvent e) {
					if (e.getKeyChar() == KeyEvent.VK_DELETE && creator.isAcceptable(cell.getModelValue(), fieldIndex, null) == null)
						creator.set(cell.getModelValue(), fieldIndex, null);
				}
			});
		}
		if (editable)
			fieldColumn.withMutation(mutator -> mutator//
				.mutateAttribute((t, f) -> creator.set(t, fieldIndex, f))//
				.editableIf((t, f) -> creator.isEnabled(t, fieldIndex) == null)//
				.filterAccept((t, f) -> creator.isAcceptable(t.get(), fieldIndex, f))//
				);
	}

	protected <R, F> boolean configureFieldFormat(TypeToken<F> fieldType, ObservableEntityType<F> targetEntity,
		CategoryRenderStrategy<R, F> column, Observable<?> expire) {
		Class<F> raw = TypeTokens.getRawType(fieldType.unwrap());
		Function<Object, String> fmt = getFormatter(fieldType, targetEntity);
		Function<F, String> formatter = v -> {
			if (v == null)
				return "null";
			else if (v == EntityUpdate.NOT_SET)
				return v.toString();
			else
				return fmt.apply(v);
		};
		column.formatText(v -> formatter.apply(v));
		boolean editable = false;
		if (Enum.class.isAssignableFrom(raw)) {
			editable = true;
			column.withMutation(mutation -> mutation.asCombo(formatter,
				ObservableCollection.of(fieldType, ArrayUtils.add(raw.getEnumConstants(), 0, (F) null))));
			column.dragSource(drag -> drag.toObject());
		} else if (Collection.class.isAssignableFrom(raw) && expire != null) {
			column.withMouseListener(new CategoryRenderStrategy.CategoryClickAdapter<R, F>() {
				@Override
				public void mouseClicked(ModelCell<? extends R, ? extends F> cell, MouseEvent e) {
					showCollectionEditor((ObservableEntity<? extends R>) cell.getModelValue(), column.getName(),
						(ObservableCollection<?>) cell.getCellValue(), e.getLocationOnScreen(), expire);
				}
			});
		} else {
			Format<F> format = getFormat(raw);
			if (format != null) {
				Format<F> fFormat;
				if (format instanceof SpinnerFormat) {
					fFormat = new SpinnerFormat<F>() {
						@Override
						public void append(StringBuilder text, F value) {
							if (value == null || value == EntityUpdate.NOT_SET)
								text.append(value);
							else
								format.append(text, value);
						}

						@Override
						public F parse(CharSequence text) throws ParseException {
							if (text.length() == 0)
								return (F) EntityUpdate.NOT_SET;
							return format.parse(text);
						}

						@Override
						public boolean supportsAdjustment(boolean withContext) {
							return ((SpinnerFormat<F>) format).supportsAdjustment(withContext);
						}

						@Override
						public BiTuple<F, String> adjust(F value, String formatted, int cursor, boolean up) {
							if (formatted.length() == 0)
								return new BiTuple<>(value, formatted);
							return ((SpinnerFormat<F>) format).adjust(value, formatted, cursor, up);
						}
					};
				} else {
					fFormat = new Format<F>() {
						@Override
						public void append(StringBuilder text, F value) {
							if (value != null)
								format.append(text, value);
						}

						@Override
						public F parse(CharSequence text) throws ParseException {
							if (text.length() == 0)
								return null;
							return format.parse(text);
						}
					};
				}
				editable = true;
				column.dragSource(drag -> drag.toObject());
				column.withMutation(mutator -> {
					mutator.asText(fFormat);
				});
			} else if (targetEntity != null) {
				editable = true;
				column.withMutation(mutator -> mutator//
					.dragAccept(drag -> drag.fromFlavor(new EntityFlavor(targetEntity), new Dragging.DataAccepterTransform<F>() {
						@Override
						public boolean canAccept(Object value, DataFlavor flavor) {
							return true;
						}

						@Override
						public F transform(Object value, DataFlavor flavor) throws IOException {
							return (F) value;
						}
					})));
			} else
				column.dragSource(drag -> drag.toObject());
		}
		return editable;
	}

	static <F> Function<Object, String> getFormatter(TypeToken<F> fieldType, ObservableEntityType<F> targetEntity) {
		if (targetEntity != null) {
			return e -> {
				if (e instanceof ObservableEntity)
					return ((ObservableEntity<?>) e).getId().toString();
				else if (e instanceof EntityIdentity)
					return e.toString();
				else
					return targetEntity.observableEntity((F) e).getId().toString();
			};
		}
		Class<?> type = TypeTokens.getRawType(fieldType);
		if (type == Instant.class) {
			return inst -> QommonsUtils.print(((Instant) inst).toEpochMilli());
		} else if (type == Duration.class) {
			return d -> QommonsUtils.printDuration((Duration) d, true);
		} else
			return String::valueOf;
	}

	static <V> Format<V> getFormat(Class<V> type) {
		if (type == int.class)
			return (Format<V>) SpinnerFormat.INT;
		else if (type == long.class)
			return (Format<V>) SpinnerFormat.LONG;
		else if (type == double.class)
			return (Format<V>) Format.doubleFormat(6).build();
		else if (type == String.class)
			return (Format<V>) SpinnerFormat.NUMERICAL_TEXT;
		else if (type == boolean.class)
			return (Format<V>) SpinnerFormat.BOOLEAN;
		else if (type == Instant.class)
			return (Format<V>) SpinnerFormat.flexDate(Instant::now, "ddMMMyyyy", TimeZone.getDefault());
		else if (type == Duration.class)
			return (Format<V>) SpinnerFormat.flexDuration();
		else
			return null;
		/* TODO More types, e.g.
		 * float
		 * byte
		 * short
		 * binaries? long character streams?
		 * ...
		 */
	}

	private <E, V> void showCollectionEditor(ObservableEntity<E> entity, String fieldName, ObservableCollection<V> collection,
		Point locationOnScreen, Observable<?> expire) {
		ObservableEntityType<V> targetEntity = theDataSet.get().getEntityType(TypeTokens.getRawType(collection.getType()));
		SimpleObservable<Void> close = SimpleObservable.build().safe(false).build();
		Observable<?> expire2 = Observable.or(expire, close);
		PanelPopulation.PanelPopulator<?, ?> queryPanel = PanelPopulation.populateVPanel((JPanel) null, expire2)//
			.addTable(collection, table -> {
				table.fill().fillV();
				table.withIndexColumn("Index", null);
				table.withColumn("Value", collection.getType(), v -> v, //
					fieldCol -> configureFieldFormat(collection.getType(), targetEntity, fieldCol, expire2));
				if (targetEntity != null) {
					Predicate<DataFlavor> toFilter = flavor -> flavor instanceof EntityFlavor
						&& ((EntityFlavor) flavor).entity.isAssignableFrom(targetEntity);
					table.dragSourceRow(rowDrag -> rowDrag.advertiseFlavor(new EntityFlavor(targetEntity))//
						.toFlavorLike(toFilter, new Dragging.DataSourceTransform<V>() {
							@Override
							public boolean canTransform(Object value, DataFlavor flavor) {
								return true;
							}

							@Override
							public Object transform(V value, DataFlavor flavor) throws IOException {
								if (value instanceof ObservableEntity)
									return value;
								else
									return targetEntity.observableEntity(value);
							}
						}));
					table.dragAcceptRow(
						rowDrag -> rowDrag.fromFlavor(new EntityFlavor(targetEntity), new Dragging.DataAccepterTransform<V>() {
							@Override
							public boolean canAccept(Object value, DataFlavor flavor) {
								if (value == null || !(flavor instanceof EntityFlavor)
									|| !targetEntity.isAssignableFrom(((EntityFlavor) flavor).entity))
									return false;
								return collection.canAdd((V) value) == null;
							}

							@Override
							public V transform(Object value, DataFlavor flavor) throws IOException {
								return (V) value;
							}
						}));
				} else {
					table.dragSourceRow(rowDrag -> rowDrag.toObject());
					table.dragAcceptRow(rowDrag -> rowDrag.fromObject());
				}
				table.withRemove(null, null);
			});
		if (targetEntity == null) {//
			SettableValue<V> toAdd = SettableValue.build(collection.getType()).safe(false).build();
			Class<V> raw = TypeTokens.getRawType(collection.getType());
			if (raw.isEnum()) {
				queryPanel.addComboField("Add Value:", toAdd, c -> c.withPostButton("Add", __ -> {
					collection.add(toAdd.get());
				}, null), ArrayUtils.add(raw.getEnumConstants(), null));
			} else {
				Format<V> format = getFormat(raw);
				if (format != null)
					queryPanel.addTextField("Add Value:", toAdd, format, tf -> tf.fill().withPostButton("Add", __ -> {
						collection.add(toAdd.get());
					}, null));
			}
		}
		JDialog createDialog = new JDialog(SwingUtilities.getWindowAncestor(this), entity.getId() + "." + fieldName, ModalityType.MODELESS);
		createDialog.getContentPane().add(queryPanel.getComponent());
		createDialog.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentHidden(ComponentEvent e) {
				close.onNext(null);
			}
		});
		createDialog.pack();
		createDialog.setVisible(true);
	}

	static class EntityTypeData<E> {
		final ObservableEntityType<E> entity;
		final EntityCountResult<E> count;
		final List<? extends ObservableEntityFieldType<E, ?>> idFields;

		EntityTypeData(ObservableEntityType<E> entity) {
			this.entity = entity;
			try {
				count = entity.select().all().query().count();
			} catch (IllegalStateException | EntityOperationException e) {
				throw new IllegalStateException("Could not query entity count for " + entity, e);
			}
			idFields = entity.getIdentityFields().allValues();
		}

		String printCount() {
			switch (count.getStatus()) {
			case FULFILLED:
				return String.valueOf(count.getResult().get());
			case WAITING:
			case EXECUTING:
				return "...";
			case FAILED:
				return "XX";
			case CANCELLED:
				break;
			}
			return "";
		}

		String printCountStatus() {
			switch (count.getStatus()) {
			case WAITING:
				return "Waiting to query";
			case EXECUTING:
				return "Executing query";
			case CANCELLED:
				return "Query is cancelled";
			case FULFILLED:
				return "";
			case FAILED:
				return printException(new StringBuilder().append("<html>"), count.getFailure(), 0).toString();
			}
			return "Unrecognized result status: " + count.getStatus();
		}

		String printIdFields() {
			return StringUtils.print(", ", idFields, f -> f.getName() + " (" + TypeTokens.getSimpleName(f.getFieldType()) + ")").toString();
		}

		String printOtherFields() {
			int printedFields = 0, extraFields = entity.getFields().keySize() - idFields.size();
			StringBuilder str = new StringBuilder();
			for (ObservableEntityFieldType<E, ?> field : entity.getFields().allValues()) {
				if (field.getIdIndex() < 0) {
					if (str.length() > 0)
						str.append(", ");
					str.append(field.getName()).append('(');
					TypeTokens.getSimpleName(field.getFieldType(), str);
					str.append(')');
					printedFields++;
					extraFields--;
					if (printedFields + extraFields > 2 && printedFields == 1)
						break;
				}
			}
			if (extraFields > 0)
				str.append(" +").append(extraFields).append(" field").append(extraFields == 1 ? "" : "s");
			return str.toString();
		}

		void printError() {
			if (count.getStatus() == OperationResult.ResultStatus.FAILED)
				count.getFailure().printStackTrace();
		}
	}

	static StringBuilder printException(StringBuilder str, Throwable e, int indent) {
		indent(str, indent);
		str.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("<br>\n");
		if (e.getCause() != null)
			printException(str, e.getCause(), indent + 1);
		return str;
	}

	private static void indent(StringBuilder str, int indent) {
		for (int i = 0; i < indent; i++)
			str.append('\t');
	}
}
