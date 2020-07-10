package org.observe.entity.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntityDataSet;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.io.Format;

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
			.refreshEach(e -> Observable.or(e.count.statusChanges(), e.count.changes()))//
			.collect();
		theEventLog = ObservableCollection.build(Object.class).build();

		theLogLength = config.asValue(int.class).at("log-length").withFormat(Format.INT, () -> 1000).buildValue(null);

		initComponents(PanelPopulation.populateVPanel(this, null));
		theSelectedEntityType = SettableValue.build(oetHolderType).build();

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
			})).lastV(this::initEntityTypePanel);
		});
	}

	protected void initEntityTypeTable(PanelPopulator<?, ?> panel) {
		panel.addTable(theEntityTypes, table -> {
			table.fill().fillV()//
			.withNameColumn(e -> e.entity.getName(), null, true, null)//
			.withColumn("ID", String.class, e -> e.printIdFields(), null)//
			.withColumn("Fields", String.class, e -> e.printOtherFields(), null)//
			.withColumn("Count", String.class, e -> e.printCount(), null)//
			.withSelection(theSelectedEntityType, true);
		});
	}

	protected void initStatusTable(PanelPopulator<?, ?> panel) {
		// TODO
	}

	protected void initEntityTypePanel(PanelPopulator<?, ?> panel) {
		TypeToken<ObservableEntityFieldType<?, ?>> FIELD_TYPE = new TypeToken<ObservableEntityFieldType<?, ?>>() {};
		ObservableCollection<ObservableEntityFieldType<?, ?>> fields = ObservableCollection.flattenValue(theSelectedEntityType
			.map(e -> ObservableCollection.of(FIELD_TYPE, e == null ? Collections.emptyList() : e.entity.getFields().allValues())));
		panel.fill().fillV().visibleWhen(theSelectedEntityType.map(e -> e != null))//
		.addLabel("Name:", theSelectedEntityType.map(e -> e.entity.getName()), Format.TEXT,
			f -> f.decorate(d -> d.bold().withFontSize(18)))//
		.addLabel("Count:", theSelectedEntityType.map(e -> e.printCount()), Format.TEXT, null)//
		.addTable(fields, this::configureFieldTable);
	}

	protected void configureFieldTable(PanelPopulation.TableBuilder<ObservableEntityFieldType<?, ?>, ?> table) {
		table.withNameColumn(ObservableEntityFieldType::getName, null, true, null)//
		.withColumn("Type", new TypeToken<TypeToken<?>>() {}, ObservableEntityFieldType::getFieldType, null);
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

		String printIdFields() {
			return StringUtils.print(", ", idFields, f -> f.getName() + "(" + f.getFieldType() + ")").toString();
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
	}
}
