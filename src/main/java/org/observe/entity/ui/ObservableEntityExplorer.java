package org.observe.entity.ui;

import java.awt.Dialog.ModalityType;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.function.Function;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableOperationResult.ResultStatus;
import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.CategoryRenderStrategy.CategoryMouseAdapter;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.ArrayUtils;
import org.qommons.BiTuple;
import org.qommons.Named;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
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
					holder.count.dispose().cancel(true);
				}
				return new EntityTypeData<>(entity);
			}, holder -> holder.entity, opts -> opts.cache(true).reEvalOnUpdate(false))//
			.refreshEach(e -> Observable.or(e.count.statusChanges(), e.count.noInitChanges()))//
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
					public void mouseClicked(CollectionElement<? extends EntityTypeData<?>> row, String category, MouseEvent e) {
						row.get().printError();
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
		panel.fill().fillV().visibleWhen(theSelectedEntityType.map(e -> e != null))//
		.addLabel("Name:", theSelectedEntityType.map(e -> e == null ? "" : e.entity.getName()), Format.TEXT,
			f -> f.decorate(d -> d.bold().withFontSize(18)))//
		.addLabel("Count:", theSelectedEntityType.map(e -> e == null ? "" : e.printCount()), Format.TEXT, null)//
		.addTable(fields, this::configureFieldTable)// ;
		.addHPanel(null, "box", buttonPanel -> {
			buttonPanel.addButton("Create", __ -> showCreatePanel(theSelectedEntityType.get().entity), null)//
			.addButton("Select", __ -> showSelectPanel(theSelectedEntityType.get().entity), null);
		});
	}

	protected void configureFieldTable(PanelPopulation.TableBuilder<ObservableEntityFieldType<?, ?>, ?> table) {
		table.fill().withNameColumn(ObservableEntityFieldType::getName, null, true, null)//
		.withColumn("Type", new TypeToken<TypeToken<?>>() {}, ObservableEntityFieldType::getFieldType, null);
	}

	<E> void showCreatePanel(ObservableEntityType<E> type) {
		ConfigurableCreator<E, E> creator = type.create();
		ObservableCollection<Object> row = ObservableCollection.build(Object.class).safe(false).build().with((Object) null);
		JPanel createPanel = PanelPopulation.populateVPanel((JPanel) null, null)//
			.<Object> addTable(row, fieldTable -> {
				for (ObservableEntityFieldType<E, ?> field : type.getFields().allValues()) {
					fieldTable.withColumn(field.getName(), (TypeToken<Object>) field.getFieldType(),
						__ -> creator.getFieldValues().get(field.getIndex()), //
						fieldCol -> configureCreateColumn((ObservableEntityFieldType<E, Object>) field, fieldCol, creator));
				}
			})//
			.addButton("Create", __ -> {
				try {
					creator.create();
				} catch (EntityOperationException e) {
					e.printStackTrace(); // TODO
				}
			}, button -> button
				.disableWith(ObservableValue.of(TypeTokens.get().STRING, creator::canCreate, row::getStamp, row.simpleChanges())))
			.getContainer();
		JDialog createDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Create " + type.getName(), ModalityType.MODELESS);
		createDialog.getContentPane().add(createPanel);
		createDialog.pack();
		createDialog.setVisible(true);
	}

	<E> void showSelectPanel(ObservableEntityType<E> type) {}

	protected <E, F> void configureCreateColumn(ObservableEntityFieldType<E, F> field, CategoryRenderStrategy<?, F> fieldColumn,
		ConfigurableCreator<E, E> creator) {
		Class<F> raw = TypeTokens.getRawType(field.getFieldType().unwrap());
		Function<Object, String> fmt = getFormatter(raw);
		Function<F, String> formatter = v -> v == null ? "" : fmt.apply(v);
		fieldColumn.formatText(v -> formatter.apply(v));
		boolean editable = false;
		if (Enum.class.isAssignableFrom(raw)) {
			editable = true;
			fieldColumn.withMutation(mutation -> mutation.asCombo(formatter,
				ObservableCollection.of(field.getFieldType(), ArrayUtils.add(raw.getEnumConstants(), 0, (F) null))));
		} else {
			Format<F> format = (Format<F>) getFormat(raw);
			if (format != null) {
				Format<F> fFormat;
				if (format instanceof SpinnerFormat) {
					fFormat = new SpinnerFormat<F>() {
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
				fieldColumn.withMutation(mutator -> {
					mutator.mutateAttribute((__, f) -> creator.withField(field, f)).asText(fFormat);
				});
			}
		}
		if (editable)
			fieldColumn.withMutation(mutator -> mutator//
				.mutateAttribute((__, f) -> creator.withField(field, f))//
				.filterAccept((__, f) -> creator.isAcceptable(field, f))//
				.withRowUpdate(true)//
				);
	}

	static Function<Object, String> getFormatter(Class<?> type) {
		if (type == Instant.class) {
			return inst -> QommonsUtils.print(((Instant) inst).toEpochMilli());
		} else if (type == Duration.class) {
			return d -> QommonsUtils.printDuration((Duration) d, true);
		} else
			return String::valueOf;
	}

	static Format<?> getFormat(Class<?> type) {
		if (type == int.class)
			return SpinnerFormat.INT;
		else if (type == long.class)
			return SpinnerFormat.LONG;
		else if (type == double.class)
			return Format.doubleFormat(6).build();
		else if (type == String.class)
			return SpinnerFormat.NUMERICAL_TEXT;
		else if (type == boolean.class)
			return SpinnerFormat.BOOLEAN;
		else if (type == Instant.class)
			return SpinnerFormat.flexDate(Instant::now, "ddMMMyyyy", TimeZone.getDefault());
		else if (type == Duration.class)
			return SpinnerFormat.flexDuration();
		else
			return null;
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
				return String.valueOf(count.get());
			case WAITING:
			case EXECUTING:
				return "...";
			case FAILED:
				return "XX";
			case CANCELLED:
			case DISPOSED:
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
			case DISPOSED:
				return "Query is disposed";
			case FULFILLED:
				return "";
			case FAILED:
				return printException(new StringBuilder().append("<html>"), count.getFailure(), 0).toString();
			}
			return "Unrecognized result status: " + count.getStatus();
		}

		String printIdFields() {
			return StringUtils.print(", ", idFields, f -> f.getName() + " (" + f.getFieldType() + ")").toString();
		}

		String printOtherFields() {
			int fieldCount = entity.getFields().keySize() - idFields.size();
			if (fieldCount <= 2) {
				StringBuilder str = new StringBuilder();
				for (ObservableEntityFieldType<E, ?> field : entity.getFields().allValues()) {
					if (field.getIdIndex() < 0) {
						if (str.length() > 0)
							str.append(", ");
						str.append(field.getName()).append('(').append(field.getFieldType()).append(')');
					}
				}
				return str.toString();
			} else
				return String.valueOf(fieldCount);
		}

		void printError() {
			if (count.getStatus() == ResultStatus.FAILED)
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
