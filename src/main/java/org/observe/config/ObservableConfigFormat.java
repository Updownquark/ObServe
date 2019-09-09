package org.observe.config;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.DefaultObservableSortedSet;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Lockable;
import org.qommons.StringUtils;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public interface ObservableConfigFormat<T> {
	public static ObservableConfigFormat<String> TEXT = ofQommonFormat(Format.TEXT, () -> null);
	public static ObservableConfigFormat<Double> DOUBLE = ofQommonFormat(Format.doubleFormat("0.############E0"), () -> 0.0);
	public static ObservableConfigFormat<Float> FLOAT = ofQommonFormat(Format.floatFormat("0.########E0"), () -> 0.0f);
	public static ObservableConfigFormat<Long> LONG = ofQommonFormat(Format.LONG, () -> 0L);
	public static ObservableConfigFormat<Integer> INT = ofQommonFormat(Format.INT, () -> 0);
	public static ObservableConfigFormat<Boolean> BOOLEAN = ofQommonFormat(Format.BOOLEAN, () -> false);
	public static ObservableConfigFormat<Duration> DURATION = ofQommonFormat(Format.DURATION, () -> Duration.ZERO);
	public static ObservableConfigFormat<Instant> DATE = ofQommonFormat(Format.date("ddMMyyyy HH:mm:ss.SSS"), () -> Instant.now());

	void format(T value, ObservableConfig config);

	T parse(ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, T previousValue,
		ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException;

	static <T> ObservableConfigFormat<T> ofQommonFormat(Format<T> format, Supplier<? extends T> defaultValue) {
		return new SimpleConfigFormat<>(format, defaultValue);
	}

	class SimpleConfigFormat<T> implements ObservableConfigFormat<T> {
		public final Format<T> format;
		public final Supplier<? extends T> defaultValue;

		public SimpleConfigFormat(Format<T> format, Supplier<? extends T> defaultValue) {
			this.format = format;
			this.defaultValue = defaultValue;
		}

		@Override
		public void format(T value, ObservableConfig config) {
			String formatted;
			if (value == null)
				formatted = null;
			else
				formatted = format.format(value);
			if (!Objects.equals(formatted, config.getValue()))
				config.setValue(formatted);
		}

		@Override
		public T parse(ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, T previousValue,
			ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException {
			if (config == null)
				return defaultValue.get();
			if (change != null && change.relativePath.size() > 1)
				return previousValue; // Changing a sub-config doesn't affect this value
			ObservableConfig c = config.get();
			String value = c == null ? null : c.getValue();
			if (value == null)
				return null;
			return format.parse(value);
		}
	}

	static <E> EntityConfigFormat<E> ofEntity(EntityConfiguredValueType<E> entityType, ConfigEntityFieldParser formats, String configName) {
		return new EntityConfigFormat<>(entityType, formats, configName);
	}

	class EntityConfigFormat<E> implements ObservableConfigFormat<E> {
		public final EntityConfiguredValueType<E> entityType;
		public final String configName;
		public final ConfigEntityFieldParser formats;
		private final ObservableConfigFormat<?>[] fieldFormats;
		private QuickMap<String, String> theFieldChildNames;
		private QuickMap<String, String> theFieldsByChildName;

		public EntityConfigFormat(EntityConfiguredValueType<E> entityType, ConfigEntityFieldParser formats, String configName) {
			this.entityType = entityType;
			this.formats = formats;
			this.configName = configName;
			fieldFormats = new ObservableConfigFormat[entityType.getFields().keySet().size()];
			for (int i = 0; i < fieldFormats.length; i++)
				fieldFormats[i] = formats.getConfigFormat(entityType.getFields().get(i));
			theFieldChildNames = entityType.getFields().keySet().createMap(//
				fieldIndex -> StringUtils.parseByCase(entityType.getFields().keySet().get(fieldIndex)).toKebabCase()).unmodifiable();
			Map<String, String> fcnReverse = new LinkedHashMap<>();
			for (int i = 0; i < theFieldChildNames.keySize(); i++)
				fcnReverse.put(theFieldChildNames.get(i), theFieldChildNames.keySet().get(i));
			theFieldsByChildName = QuickMap.of(fcnReverse, String::compareTo);
		}

		public EntityConfiguredValueType<E> getEntityType() {
			return entityType;
		}

		@Override
		public void format(E value, ObservableConfig config) {
			if (value == null) {
				config.set("null", "true");
				for (int i = 0; i < entityType.getFields().keySize(); i++)
					config.set(theFieldChildNames.get(i), null);
			} else {
				config.set("null", null);
				for (int i = 0; i < entityType.getFields().keySize(); i++) {
					ConfiguredValueField<? super E, ?> field = entityType.getFields().get(i);
					Object fieldValue = field.get(value);
					formatField(field, fieldValue, config);
				}
			}
		}

		@Override
		public E parse(ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, E previousValue,
			ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException {
			ObservableConfig c = config.get();
			if (c != null && "true".equalsIgnoreCase(c.get("null")))
				return null;
			else if (previousValue == null) {
				if (c == null)
					c = create.get();
				return createInstance(c, entityType.getFields().keySet().createMap(), until);
			} else {
				if (change == null) {
					for (int i = 0; i < entityType.getFields().keySize(); i++)
						parseUpdatedField(c, i, previousValue, null, until);
				} else if (change.relativePath.isEmpty()) {
					// Change to the value doesn't change any fields
				} else {
					ObservableConfig child = change.relativePath.get(0);
					int fieldIdx = theFieldsByChildName.keyIndexTolerant(child.getName());
					if (change.relativePath.size() == 1 && !change.oldName.equals(child.getName())) {
						if (fieldIdx >= 0)
							parseUpdatedField(c, fieldIdx, previousValue, change.asFromChild(), until);
						fieldIdx = theFieldsByChildName.keyIndexTolerant(change.oldName);
						if (fieldIdx >= 0)
							parseUpdatedField(c, fieldIdx, previousValue, null, until);
					} else if (fieldIdx >= 0)
						parseUpdatedField(c, fieldIdx, previousValue, change, until);
				}
				return previousValue;
			}
		}

		public String getChildName(int fieldIndex) {
			return theFieldChildNames.get(fieldIndex);
		}

		public ObservableConfigFormat<?> getFieldFormat(int fieldIndex) {
			return fieldFormats[fieldIndex];
		}

		public E createInstance(ObservableConfig config, QuickMap<String, Object> fieldValues, Observable<?> until) throws ParseException {
			for (int i = 0; i < fieldValues.keySize(); i++) {
				ObservableValue<? extends ObservableConfig> fieldConfig = config.observeDescendant(theFieldChildNames.get(i));
				if (fieldValues.get(i) == null)
					fieldValues.put(i, fieldFormats[i].parse(fieldConfig, () -> config.addChild(configName), null, null, until));
				else
					formatField(entityType.getFields().get(i), fieldValues.get(i), config);
			}
			return entityType.create(//
				idx -> fieldValues.get(idx), //
				(idx, value) -> {
					fieldValues.put(idx, value);
					formatField(entityType.getFields().get(idx), value, config);
				});
		}

		public void formatField(ConfiguredValueField<? super E, ?> field, Object fieldValue, ObservableConfig entityConfig) {
			boolean[] added = new boolean[1];
			if (fieldValue != null) {
				ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(field.getIndex()), true, fc -> {
					added[0] = true;
					((ObservableConfigFormat<Object>) fieldFormats[field.getIndex()]).format(fieldValue, fc);
				});
				if (!added[0])
					((ObservableConfigFormat<Object>) fieldFormats[field.getIndex()]).format(fieldValue, fieldConfig);
			} else {
				ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(field.getIndex()));
				if (fieldConfig != null)
					fieldConfig.remove();
			}
		}

		public void parseUpdatedField(ObservableConfig entityConfig, int fieldIdx, E previousValue,
			ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException {
			ConfiguredValueField<? super E, ?> field = entityType.getFields().get(fieldIdx);
			Object oldValue = field.get(previousValue);
			ObservableValue<? extends ObservableConfig> fieldConfig = entityConfig.observeDescendant(theFieldChildNames.get(fieldIdx));
			if (change != null) {
				if (change.relativePath.isEmpty() || fieldConfig != change.relativePath.get(0))
					return; // The update does not actually affect the field value
				change = change.asFromChild();
			}
			Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(fieldConfig,
				() -> entityConfig.addChild(configName), oldValue, change, until);
			if (oldValue != newValue)
				((ConfiguredValueField<E, Object>) field).set(previousValue, newValue);
		}
	}

	static <E, C extends Collection<? extends E>> ObservableConfigFormat<C> ofCollection(TypeToken<C> collectionType,
		ObservableConfigFormat<E> elementFormat, String parentName, String childName) {
		if (!TypeTokens.getRawType(collectionType).isAssignableFrom(ObservableCollection.class))
			throw new IllegalArgumentException("This class can only produce instances of " + ObservableCollection.class.getName()
				+ ", which is not compatible with type " + collectionType);
		TypeToken<E> elementType = (TypeToken<E>) collectionType.resolveType(Collection.class.getTypeParameters()[0]);
		return new ObservableConfigFormat<C>() {
			@Override
			public void format(C value, ObservableConfig config) {
				if (value == null) {
					config.remove();
					return;
				}
				List<ObservableConfig> content = new ArrayList<>(config.getContent(childName).getValues());
				try (Transaction t = config.lock(true, null)) {
					ArrayUtils.adjust(content, asList(value), new ArrayUtils.DifferenceListener<ObservableConfig, E>() {
						@Override
						public boolean identity(ObservableConfig o1, E o2) {
							if (elementFormat instanceof SimpleConfigFormat) {
								return Objects.equals(o1.getValue(),
									o2 == null ? null : ((SimpleConfigFormat<E>) elementFormat).format.format(o2));
							} else if (elementFormat instanceof EntityConfigFormat
								&& !((EntityConfigFormat<?>) elementFormat).getEntityType().getIdFields().isEmpty()) {
								EntityConfigFormat<?> entityFormat = (EntityConfigFormat<?>) elementFormat;
								EntityConfiguredValueType<?> entityType = entityFormat.getEntityType();
								boolean canFind = true;
								for (int i : entityType.getIdFields()) {
									ConfiguredValueField<?, ?> f = entityType.getFields().get(i);
									ObservableConfigFormat<?> fieldFormat = entityFormat.getFieldFormat(i);
									if (!(fieldFormat instanceof SimpleConfigFormat)) {
										canFind = false;
										break;
									}
									if (!Objects.equals(o1.get(entityFormat.getChildName(i)), //
										((SimpleConfigFormat<Object>) fieldFormat).format
										.format(((ConfiguredValueField<Object, ?>) f).get(o1))))
										return false;
								}
								if (canFind)
									return false;
								else
									return true; // No way to tell different values apart, just gotta reformat
							} else
								return true; // No way to tell different values apart, just gotta reformat
						}

						@Override
						public ObservableConfig added(E o, int mIdx, int retIdx) {
							ObservableConfig before = retIdx == content.size() ? null : content.get(retIdx);
							return config.addChild(null, before, false, childName, cfg -> elementFormat.format(o, cfg));
						}

						@Override
						public ObservableConfig removed(ObservableConfig o, int oIdx, int incMod, int retIdx) {
							o.remove();
							return null;
						}

						@Override
						public ObservableConfig set(ObservableConfig o1, int idx1, int incMod, E o2, int idx2, int retIdx) {
							ObservableConfig result;
							if (incMod != retIdx) {
								o1.remove();
								ObservableConfig before = retIdx == content.size() ? null : content.get(retIdx);
								result = config.addChild(null, before, false, childName, cfg -> {
									cfg.copyFrom(o1, true);
									elementFormat.format(o2, cfg);
								});
							} else {
								result = o1;
								elementFormat.format(o2, result);
							}
							return result;
						}
					});
				}
			}

			private List<E> asList(C value) {
				if (value instanceof List)
					return (List<E>) value;
				else {
					List<E> list = new ArrayList<>(value.size());
					list.addAll(value);
					return list;
				}
			}

			@Override
			public C parse(ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, C previousValue,
				ObservableConfigEvent change, Observable<?> until) throws ParseException {
				class ConfigContentValueWrapper extends ObservableCollectionWrapper<E> {
					private final ObservableCollection<E> theValues;
					private final SimpleObservable<Void> theContentUntil;

					ConfigContentValueWrapper(ObservableCollection<E> values, SimpleObservable<Void> contentUntil) {
						theValues = values;
						theContentUntil = contentUntil;
						init(theValues);
					}

					ObservableConfig getConfig() {
						return config.get();
					}
				}
				if (previousValue == null) {
					ObservableConfig child = config != null ? config : create.get();
					SimpleObservable<Void> contentUntil = new SimpleObservable<>(null, false, null, b -> b.unsafe());
					return (C) new ConfigContentValueWrapper(child.observeValues(childName, elementType, elementFormat, //
						Observable.or(until, contentUntil)), contentUntil);
				} else {
					ConfigContentValueWrapper wrapper = (ConfigContentValueWrapper) previousValue;
					if (wrapper.getConfig() != config) {
						wrapper.theContentUntil.onNext(null);
						return (C) new ConfigContentValueWrapper(config.observeValues(childName, elementType, elementFormat, //
							Observable.or(until, wrapper.theContentUntil)), wrapper.theContentUntil);
					} else
						return previousValue;
				}
			}
		};
	}

	static <E> ObservableConfigFormat<ObservableValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String parentName,
		String childName, ConfigEntityFieldParser fieldParser) {
		return new ObservableConfigFormat<ObservableValueSet<E>>() {
			@Override
			public void format(ObservableValueSet<E> value, ObservableConfig config) {
				// Nah, we don't support calling set on a field like this, nothing to do
			}

			@Override
			public ObservableValueSet<E> parse(ObservableValue<? extends ObservableConfig> config,
				Supplier<? extends ObservableConfig> create, ObservableValueSet<E> previousValue, ObservableConfigEvent change,
				Observable<?> until) throws ParseException {
				if (previousValue == null) {
					return new ObservableConfigEntityValues<>(config, create::get, elementFormat, childName, fieldParser, until, false);
				} else {
					((ObservableConfigEntityValues<E>) previousValue).onChange(change);
					return previousValue;
				}
			}
		};
	}

	abstract class ObservableConfigBackedCollection<E> implements StructuredTransactable{
		private final ObservableValue<? extends ObservableConfig> theCollectionElement;
		private final Runnable theCECreate;
		final TypeToken<E> theType;
		private final ObservableConfigFormat<E> theFormat;
		private final String theChildName;
		private final Observable<?> theUntil;

		private final BetterSortedMap<ElementId, ConfigElement> theElements;
		private final ListenerList<Consumer<? super ObservableCollectionEvent<? extends E>>> theListeners;
		private final OCBCCollection theCollection;

		ConfigElement theNewElement;
		Consumer<? super ConfigElement> thePreAddAction;
		boolean isModifying;

		public ObservableConfigBackedCollection(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
			String childName, Observable<?> until, boolean listen){
			theCollectionElement = collectionElement;
			theCECreate = ceCreate;
			theChildName = childName;
			if (until == null)
				theUntil = theCollectionElement.noInitChanges().filter(evt -> evt.getOldValue() != evt.getNewValue());
			else
				theUntil = Observable.or(until, //
					theCollectionElement.noInitChanges().takeUntil(until).filter(evt -> evt.getOldValue() != evt.getNewValue()));

			theElements=new BetterTreeMap<>(false, ElementId::compareTo);
			theListeners=ListenerList.build().allowReentrant().build();

			theCollectionElement.changes().takeUntil(until).act(//
				evt -> {
					if (evt.getNewValue() == evt.getOldValue())
						return;
					initConfig(evt.getNewValue(), evt, listen);
				});

			theCollection=createCollection();
		}

		private void initConfig(ObservableConfig collectionElement, Object cause, boolean listen) {
			try (Transaction ceT = collectionElement==null ? Transaction.NONE : collectionElement.lock(false, null)) {
				Iterator<ConfigElement> cveIter = theElements.values().reverse().iterator();
				while (cveIter.hasNext()) {
					ConfigElement cve = cveIter.next();
					cveIter.remove();
					cve.dispose();
				}
				theElements.clear();
				if(collectionElement!=null){
					for (ObservableConfig child : collectionElement.getContent(theChildName).getValues()) {
						theElements.put(child.getParentChildRef(), createElement(child));
					}
					if (listen)
						collectionElement.watch(theChildName).takeUntil(theUntil).act(this::onChange);
				}
			}
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange.relativePath.isEmpty() || collectionChange.eventTarget != theCollectionElement.get())
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				CollectionElement<ElementId> el = theElements.keySet().search(config.getParentChildRef(), SortedSearchFilter.PreferLess);
				ConfigElement newEl;
				if(theNewElement!=null){
					newEl=theNewElement;
					theNewElement=null;
				} else
					newEl= createElement(config);
				if (thePreAddAction != null){
					thePreAddAction.accept(newEl);
					thePreAddAction=null;
				}
				ElementId newElId;
				if (el == null)// Must be empty
					newElId = theElements.putEntry(config.getParentChildRef(), newEl, false).getElementId();
				else if (el.get().compareTo(config.getParentChildRef()) < 0)
					newElId = theElements.putEntry(config.getParentChildRef(), newEl, el.getElementId(), null, true).getElementId();
				else
					newElId = theElements.putEntry(config.getParentChildRef(), newEl, null, el.getElementId(), false).getElementId();
				newEl.theElement=newElId;
				fire(new ObservableCollectionEvent<>(newElId, theType, theElements.keySet().getElementsBefore(newElId),
					CollectionChangeType.add, null, newEl.get(), collectionChange));
			} else if(!isModifying){
				CollectionElement<ConfigElement> el = theElements.getEntry(config.getParentChildRef());
				if (el == null) // Must be a different child
					return;
				if (collectionChange.relativePath.size() == 1 && collectionChange.changeType == CollectionChangeType.remove) {
					theElements.mutableEntry(el.getElementId()).remove();
					el.get().dispose();
					fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
						theElements.keySet().getElementsBefore(el.getElementId()),
						CollectionChangeType.remove, el.get().get(), el.get().get(), collectionChange));
				} else {
					try {
						E newValue=theFormat.parse(ObservableValue.of(el.get().theConfig), () -> collectionChange.eventTarget.addChild(theChildName),
							el.get().get(), collectionChange.asFromChild(), theUntil);
						E oldValue=el.get().get();
						if(newValue!=oldValue){
							el.get().theValue=newValue;
						}
						fire(new ObservableCollectionEvent<>(el.getElementId(), theType,
							theElements.keySet().getElementsBefore(el.getElementId()),
							CollectionChangeType.set, oldValue, newValue, collectionChange));
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
		}

		private void fire(ObservableCollectionEvent<E> event){
			try(Transaction t=Causable.use(event)){
				theListeners.forEach(//
					listener->listener.accept(event));
			}
		}

		public Subscription addListener(Consumer<? super ObservableCollectionEvent<? extends E>> listener){
			return theListeners.add(listener, true)::run;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return Lockable.lock(theCollectionElement, ()->Lockable.lockable(theCollectionElement.get(), write, structural, cause));
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return Lockable.tryLock(theCollectionElement, ()->Lockable.lockable(theCollectionElement.get(), write, structural, cause));
		}

		public ObservableCollection<E> getCollection(){
			return theCollection;
		}

		protected void add(Function<ObservableConfig, E> value, ElementId after, ElementId before, boolean first, Consumer<ConfigElement> preAddAction){
			ElementId cve;
			Transaction obsT = theCollectionElement.lock();
			try {
				ObservableConfig collectionElement = theCollectionElement.get();
				if (collectionElement == null && theCECreate == null)
					throw new IllegalStateException("No collection element to create child of");
				while (collectionElement == null) {
					obsT.close();
					obsT = null;
					theCECreate.run();
					obsT = theCollectionElement.lock();
					collectionElement = theCollectionElement.get();
				}
				try (Transaction t = collectionElement.lock(true, null)) {
					ObservableConfig configAfter = after == null ? null : theElements.getEntryById(after).get().getConfig();
					ObservableConfig configBefore = before == null ? null : theElements.getEntryById(before).get().getConfig();
					thePreAddAction = preAddAction;
					collectionElement.addChild(configAfter, configBefore, first, theChildName, cfg -> {
						try {
							theNewInstance = theFormat.createInstance(cfg, getFieldValues(), theUntil);
						} catch (ParseException e) {
							throw new IllegalStateException("Could not create instance", e);
						}
					});
					cve = theNewElement;
					theNewElement = null;
				}
			} finally {
				if (obsT != null)
					obsT.close();
			}
			return theValues.getElement(cve);
		}

		protected abstract OCBCCollection createCollection();

		protected abstract ConfigElement createElement(ObservableConfig config);

		protected abstract class ConfigElement implements MutableCollectionElement<E>{
			private final ObservableConfig theConfig;
			private final SimpleObservable<Void> theElementObservable;
			private ElementId theElement;
			private E theValue;
			private CollectionElement<E> immutable;

			public ConfigElement(ObservableConfig config) {
				this.theConfig = config;
				theElementObservable = new SimpleObservable<>(null, false, null, b -> b.unsafe());

				E value = null;
				try {
					value = theFormat.parse(ObservableValue.of(this.theConfig), () -> this.theConfig.getParent().addChild(theChildName), null,
						null, Observable.or(theUntil, theElementObservable));
				} catch (ParseException e) {
					System.err.println("Could not parse instance for " + this.theConfig);
					e.printStackTrace();
					value = null;
				}
				theValue = value;
			}

			protected ObservableConfig getConfig(){
				return theConfig;
			}

			@Override
			public BetterCollection<E> getCollection() {
				return ObservableConfigBackedCollection.this.getCollection();
			}

			@Override
			public ElementId getElementId() {
				return theElement;
			}

			@Override
			public E get() {
				return theValue;
			}

			protected void _set(E value){
				theValue=value;
			}

			protected void setOp(E value) throws UnsupportedOperationException, IllegalArgumentException {
				try(Transaction t=lock(true, false, null)){
					if(!theConfig.getParentChildRef().isPresent())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					isModifying=true;
					_set(value);
					theFormat.format(value, theConfig);
				} finally{
					isModifying=false;
				}
			}

			protected void removeOp() throws UnsupportedOperationException {
				try(Transaction t=lock(true, null)){
					isModifying=true;
					theConfig.remove();
				} finally{
					isModifying=false;
				}
			}

			void dispose() {
				theElementObservable.onNext(null);
			}

			@Override
			public CollectionElement<E> immutable() {
				if(immutable==null){
					immutable=new CollectionElement<E>(){
						@Override
						public ElementId getElementId() {
							return theElement;
						}

						@Override
						public E get() {
							return theValue;
						}
					};
				}
				return immutable;
			}
		}

		private abstract class OCBCCollection implements ObservableCollection<E>{
			@Override
			public long getStamp(boolean structuralOnly) {
			}

			@Override
			public boolean isLockSupported() {
				return true;
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				return ObservableConfigBackedCollection.this.lock(write, structural, cause);
			}

			@Override
			public Transaction tryLock(boolean write, boolean structural, Object cause) {
				return ObservableConfigBackedCollection.this.tryLock(write, structural, cause);
			}

			@Override
			public TypeToken<E> getType() {
				return theType;
			}

			@Override
			public boolean isContentControlled() {
				return false;
			}

			@Override
			public int size() {
				return theElements.size();
			}

			@Override
			public boolean isEmpty() {
				return theElements.isEmpty();
			}

			@Override
			public int getElementsBefore(ElementId id) {
				try(Transaction t=lock(false, null)){
					theElements.keySet().getElementsBefore(id);
				}
			}

			@Override
			public int getElementsAfter(ElementId id) {
				try(Transaction t=lock(false, null)){
					theElements.keySet().getElementsAfter(id);
				}
			}

			@Override
			public CollectionElement<E> getElement(int index) {
				try(Transaction t=lock(false, null)){
					return theElements.getEntryById(theElements.keySet().getElement(index).getElementId()).get();
				}
			}

			@Override
			public CollectionElement<E> getElement(E value, boolean first) {
				try(Transaction t=lock(false, null)){
					CollectionElement<E> el=getTerminalElement(first);
					while(el!=null && !Objects.equals(el.get(), value))
						el=getAdjacentElement(el.getElementId(), first);
					return el;
				}
			}

			@Override
			public CollectionElement<E> getElement(ElementId id) {
				return theElements.getEntryById(id).get().immutable();
			}

			@Override
			public CollectionElement<E> getTerminalElement(boolean first) {
				CollectionElement<ConfigElement> el=theElements.values().getTerminalElement(first);
				return el==null ? null : el.get().immutable();
			}

			@Override
			public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
				CollectionElement<ConfigElement> el=theElements.values().getAdjacentElement(elementId, next);
				return el==null ? null : el.get().immutable();
			}

			@Override
			public MutableCollectionElement<E> mutableElement(ElementId id) {
				return theElements.getEntryById(id).get();
			}

			@Override
			public CollectionElement<E> getElementBySource(ElementId sourceEl) {
				CollectionElement<ConfigElement> el=theElements.values().getElementBySource(sourceEl);
				if(el!=null)
					return el.get().immutable();
				return MutableCollectionElement.immutable(theElements.get(sourceEl));
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
				return addListener(observer);
			}

			@Override
			public void clear() {
				try(Transaction t=lock(true, null)){
					for(CollectionElement<ConfigElement> el : theElements.values().reverse().elements()){
						el.get().remove();
					}
				}
			}

			@Override
			public Equivalence<? super E> equivalence() {
				return Equivalence.DEFAULT;
			}

			@Override
			public void setValue(Collection<ElementId> elements, E value) {
				try(Transaction t=lock(true, null)){
					for(ElementId el : elements){
						theElements.getEntryById(el).get().set(value);
					}
				}
			}
		}
	}

	class ObservableConfigValues<E> extends ObservableCollectionWrapper<E>{
		private final ObservableValue<? extends ObservableConfig> theCollectionElement;
		private final Runnable theCECreate;
		private final ObservableConfigFormat<E> theFormat;
		private final String theChildName;
		private final ConfigEntityFieldParser theFieldParser;
		private final Observable<?> theUntil;

		private final ObservableCollection<ConfigValueElement> theValueElements;
		// private final ObservableCollection<ConfigValueElement> theValueElements;
		private final ObservableCollection<E> theValues;

		ConfigValueElement theNewElement;
		Consumer<? super E> thePreAddAction;
		ElementId theNewElementId;
		boolean isModifying;

		ObservableConfigValues(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
			EntityConfigFormat<E> format, String childName, ConfigEntityFieldParser fieldParser, Observable<?> until, boolean listen) {
			theCollectionElement = collectionElement;
			theCECreate = ceCreate;
			theFormat = format;
			theChildName = childName;
			theFieldParser = fieldParser;
			if (until == null)
				theUntil = theCollectionElement.noInitChanges().filter(evt -> evt.getOldValue() != evt.getNewValue());
			else
				theUntil = Observable.or(until, //
					theCollectionElement.noInitChanges().takeUntil(until).filter(evt -> evt.getOldValue() != evt.getNewValue()));

			ObservableSortedSet<ConfigValueElement>[] valueElsRef = new ObservableSortedSet[1];
			theValueElements = new DefaultObservableSortedSet<>(new TypeToken<ConfigValueElement>() {},
				new BetterTreeSet<>(false, ConfigValueElement::compareTo), element -> {
					return valueElsRef[0].search(cve -> element.compareTo(cve.config.getParentChildRef()), SortedSearchFilter.OnlyMatch)
						.getElementId();
				});
			valueElsRef[0] = theValueElements;

			theCollectionElement.changes().takeUntil(until).act(//
				evt -> {
					if (evt.getNewValue() == evt.getOldValue())
						return;
					initConfig(evt.getNewValue(), evt, listen);
				});

			theValues = theValueElements.flow().map(format.getEntityType().getType(), cve -> cve.instance, opts -> opts.cache(false))
				.collectPassive();

			init(theValues);
		}

		private void initConfig(ObservableConfig collectionElement, Object cause, boolean listen) {
			try (Transaction veT = theValueElements.lock(true, cause)) {
				Iterator<ConfigValueElement> cveIter = theValueElements.reverse().iterator();
				while (cveIter.hasNext()) {
					ConfigValueElement cve = cveIter.next();
					cveIter.remove();
					cve.dispose();
				}
				theValueElements.clear();
				try (Transaction ceT = collectionElement.lock(false, null)) {
					for (ObservableConfig child : collectionElement.getContent(theChildName).getValues()) {
						theValueElements.add(new ConfigValueElement(child));
					}
					if (listen)
						collectionElement.watch(theChildName).takeUntil(theUntil).act(this::onChange);
				}
			}
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange.relativePath.isEmpty() || collectionChange.eventTarget != theCollectionElement.get())
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				CollectionElement<ConfigValueElement> el = theValueElements.search(//
					cve -> config.getParentChildRef().compareTo(cve.config.getParentChildRef()), SortedSearchFilter.PreferLess);
				ConfigValueElement newEl;
				if(theNewElement!=null){
					newEl=theNewElement;
					theNewElement=null;
				} else
					newEl= new ConfigValueElement(config);
				if (thePreAddAction != null){
					thePreAddAction.accept(newEl.instance);
					thePreAddAction=null;
				}
				if (el == null)// Must be empty
					theNewElementId = theValueElements.addElement(newEl, false).getElementId();
				else if (el.get().config.getParentChildRef().compareTo(config.getParentChildRef()) < 0)
					theNewElementId = theValueElements.addElement(newEl, el.getElementId(), null, true).getElementId();
				else
					theNewElementId = theValueElements.addElement(newEl, null, el.getElementId(), false).getElementId();
			} else if(!isModifying){
				CollectionElement<ConfigValueElement> el = theValueElements.search(//
					cve -> config.getParentChildRef().compareTo(cve.config.getParentChildRef()), SortedSearchFilter.OnlyMatch);
				if (el == null) // Must be a different child
					return;
				if (collectionChange.relativePath.size() == 1 && collectionChange.changeType == CollectionChangeType.remove) {
					theValueElements.mutableElement(el.getElementId()).remove();
					el.get().dispose();
				}

				else {
					try {
						theFormat.parse(ObservableValue.of(el.get().config), () -> collectionChange.eventTarget.addChild(theChildName),
							el.get().instance, collectionChange.asFromChild(), theUntil);
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			return belongs(value) ? null : StdMsg.ILLEGAL_ELEMENT;
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ObservableConfig configAfter = after == null ? null : theValueElements.getElement(after).get().config;
			ObservableConfig configBefore = before == null ? null : theValueElements.getElement(before).get().config;
			ElementId cve;
			Transaction obsT = theCollectionElement.lock();
			try {
				ObservableConfig collectionElement = theCollectionElement.get();
				if (collectionElement == null && theCECreate == null)
					throw new IllegalStateException("No collection element to create child of");
				while (collectionElement == null) {
					obsT.close();
					obsT = null;
					theCECreate.run();
					obsT = theCollectionElement.lock();
					collectionElement = theCollectionElement.get();
				}
				try (Transaction t = collectionElement.lock(true, null)) {
					if (after != null && !after.isPresent())
						throw new IllegalStateException("Collection has changed: " + after + " is no longer present");
					if (before != null && !before.isPresent())
						throw new IllegalStateException("Collection has changed: " + before + " is no longer present");
					collectionElement.addChild(configAfter, configBefore, first, theChildName, cfg -> {
						try {
							theNewElement = new ConfigValueElement(cfg);
						} catch (ParseException e) {
							throw new IllegalStateException("Could not create instance", e);
						}
					});
					cve = theNewElementId;
					theNewElementId = null;
				}
			} finally {
				if (obsT != null)
					obsT.close();
			}
			return theValues.getElement(cve);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {

		}

		@Override
		public void setValue(Collection<ElementId> elements, E value) {
			//TODO
		}

		private class ConfigValueElement implements MutableCollectionElement<E> {
			final ObservableConfig config;
			final E instance;
			final SimpleObservable<Void> elementObservable;

			public ConfigValueElement(ObservableConfig config) {
				this.config = config;
				elementObservable = new SimpleObservable<>(null, false, null, b -> b.unsafe());
				E inst = null;
				try {
					inst = theFormat.parse(ObservableValue.of(this.config), () -> this.config.getParent().addChild(theChildName), null,
						null, Observable.or(theUntil, elementObservable));
				} catch (ParseException e) {
					System.err.println("Could not parse instance for " + this.config);
					e.printStackTrace();
					inst = null;
				}
				instance = inst;
			}

			@Override
			public ElementId getElementId() {

				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public E get() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public BetterCollection<E> getCollection() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String isEnabled() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String isAcceptable(E value) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String canRemove() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				// TODO Auto-generated method stub

			}

			@Override
			public int compareTo(ConfigValueElement o) {
				return config.getParentChildRef().compareTo(o.config.getParentChildRef());
			}

			@Override
			void set(E instance){
				this.instance=instance;
				isModifying=true;
				try{
					theFormat.format(instance, config);
				} finally{
					isModifying=false;
				}
			}

			void dispose() {
				elementObservable.onNext(null);
			}
		}
	}
}

class ObservableConfigEntityValues<E> implements ObservableValueSet<E> {
	private final ObservableValue<? extends ObservableConfig> theCollectionElement;
	private final Runnable theCECreate;
	private final EntityConfigFormat<E> theFormat;
	private final String theChildName;
	private final ConfigEntityFieldParser theFieldParser;
	private final Observable<?> theUntil;

	private final ObservableSortedSet<ConfigValueElement> theValueElements;
	// private final ObservableCollection<ConfigValueElement> theValueElements;
	private final ObservableCollection<E> theValues;

	E theNewInstance;
	Consumer<? super E> thePreAddAction;
	ElementId theNewElement;

	ObservableConfigEntityValues(ObservableValue<? extends ObservableConfig> collectionElement, Runnable ceCreate,
		EntityConfigFormat<E> format, String childName, ConfigEntityFieldParser fieldParser, Observable<?> until, boolean listen) {
		theCollectionElement = collectionElement;
		theCECreate = ceCreate;
		theFormat = format;
		theChildName = childName;
		theFieldParser = fieldParser;
		if (until == null)
			theUntil = theCollectionElement.noInitChanges().filter(evt -> evt.getOldValue() != evt.getNewValue());
		else
			theUntil = Observable.or(until, //
				theCollectionElement.noInitChanges().takeUntil(until).filter(evt -> evt.getOldValue() != evt.getNewValue()));

		ObservableSortedSet<ConfigValueElement>[] valueElsRef = new ObservableSortedSet[1];
		theValueElements = new DefaultObservableSortedSet<>(new TypeToken<ConfigValueElement>() {},
			new BetterTreeSet<>(false, ConfigValueElement::compareTo), element -> {
				return valueElsRef[0].search(cve -> element.compareTo(cve.config.getParentChildRef()), SortedSearchFilter.OnlyMatch)
					.getElementId();
			});
		valueElsRef[0] = theValueElements;

		theCollectionElement.changes().takeUntil(until).act(//
			evt -> {
				if (evt.getNewValue() == evt.getOldValue())
					return;
				init(evt.getNewValue(), evt, listen);
			});

		theValues = theValueElements.flow().map(format.getEntityType().getType(), cve -> cve.instance, opts -> opts.cache(false))
			.collectPassive();
	}

	private void init(ObservableConfig collectionElement, Object cause, boolean listen) {
		try (Transaction veT = theValueElements.lock(true, cause)) {
			Iterator<ConfigValueElement> cveIter = theValueElements.reverse().iterator();
			while (cveIter.hasNext()) {
				ConfigValueElement cve = cveIter.next();
				cveIter.remove();
				cve.dispose();
			}
			theValueElements.clear();
			try (Transaction ceT = collectionElement.lock(false, null)) {
				for (ObservableConfig child : collectionElement.getContent(theChildName).getValues()) {
					theValueElements.add(new ConfigValueElement(child));
				}
				if (listen)
					collectionElement.watch(theChildName).takeUntil(theUntil).act(this::onChange);
			}
		}
	}

	protected void onChange(ObservableConfigEvent collectionChange) {
		if (collectionChange.relativePath.isEmpty() || collectionChange.eventTarget != theCollectionElement.get())
			return; // Doesn't affect us
		boolean elementChange = collectionChange.relativePath.size() == 1;
		ObservableConfig config = collectionChange.relativePath.get(0);
		if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
			CollectionElement<ConfigValueElement> el = theValueElements.search(//
				cve -> config.getParentChildRef().compareTo(cve.config.getParentChildRef()), SortedSearchFilter.PreferLess);
			ConfigValueElement newEl = new ConfigValueElement(config);
			if (thePreAddAction != null)
				thePreAddAction.accept(newEl.instance);
			if (el == null)// Must be empty
				theNewElement = theValueElements.addElement(newEl, false).getElementId();
			else if (el.get().config.getParentChildRef().compareTo(config.getParentChildRef()) < 0)
				theNewElement = theValueElements.addElement(newEl, el.getElementId(), null, true).getElementId();
			else
				theNewElement = theValueElements.addElement(newEl, null, el.getElementId(), false).getElementId();
		} else {
			CollectionElement<ConfigValueElement> el = theValueElements.search(//
				cve -> config.getParentChildRef().compareTo(cve.config.getParentChildRef()), SortedSearchFilter.OnlyMatch);
			if (el == null) // Must be a different child
				return;
			if (collectionChange.relativePath.size() == 1 && collectionChange.changeType == CollectionChangeType.remove) {
				theValueElements.mutableElement(el.getElementId()).remove();
				el.get().dispose();
			}

			else {
				try {
					theFormat.parse(ObservableValue.of(el.get().config), () -> collectionChange.eventTarget.addChild(theChildName),
						el.get().instance, collectionChange.asFromChild(), theUntil);
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public ConfiguredValueType<E> getType() {
		return theFormat.getEntityType();
	}

	@Override
	public ObservableCollection<? extends E> getValues() {
		return theValues;
	}

	@Override
	public ValueCreator<E> create(ElementId after, ElementId before, boolean first) {
		ObservableConfig configAfter = after == null ? null : theValueElements.getElement(after).get().config;
		ObservableConfig configBefore = before == null ? null : theValueElements.getElement(before).get().config;
		return new SimpleValueCreator<E>(getType()) {
			@Override
			public <F> ValueCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException {
				super.with(field, value);
				theFieldParser.getConfigFormat(field); // Throws an exception if not supported
				getFieldValues().put(field.getIndex(), value);
				return this;
			}

			@Override
			public CollectionElement<E> create(Consumer<? super E> preAddAction) {
				ElementId cve;
				Transaction obsT = theCollectionElement.lock();
				try {
					ObservableConfig collectionElement = theCollectionElement.get();
					if (collectionElement == null && theCECreate == null)
						throw new IllegalStateException("No collection element to create child of");
					while (collectionElement == null) {
						obsT.close();
						obsT = null;
						theCECreate.run();
						obsT = theCollectionElement.lock();
						collectionElement = theCollectionElement.get();
					}
					try (Transaction t = collectionElement.lock(true, null)) {
						if (after != null && !after.isPresent())
							throw new IllegalStateException("Collection has changed: " + after + " is no longer present");
						if (before != null && !before.isPresent())
							throw new IllegalStateException("Collection has changed: " + before + " is no longer present");
						thePreAddAction = preAddAction;
						collectionElement.addChild(configAfter, configBefore, first, theChildName, cfg -> {
							try {
								theNewInstance = theFormat.createInstance(cfg, getFieldValues(), theUntil);
							} catch (ParseException e) {
								throw new IllegalStateException("Could not create instance", e);
							}
						});
						cve = theNewElement;
						theNewElement = null;
					}
				} finally {
					if (obsT != null)
						obsT.close();
				}
				return theValues.getElement(cve);
			}
		};
	}

	private class ConfigValueElement implements Comparable<ConfigValueElement> {
		final ObservableConfig config;
		final E instance;
		final SimpleObservable<Void> elementObservable;

		public ConfigValueElement(ObservableConfig config) {
			this.config = config;
			elementObservable = new SimpleObservable<>(null, false, null, b -> b.unsafe());
			E inst = null;
			if (theNewInstance != null) {
				inst = theNewInstance;
				theNewInstance = null;
			} else {
				try {
					inst = theFormat.parse(ObservableValue.of(this.config), () -> this.config.getParent().addChild(theChildName), null,
						null, Observable.or(theUntil, elementObservable));
				} catch (ParseException e) {
					System.err.println("Could not parse instance for " + this.config);
					e.printStackTrace();
					inst = null;
				}
			}
			instance = inst;
			if (instance != null)
				theFormat.getEntityType().associate(instance, ObservableConfigEntityValues.this, this);
		}

		@Override
		public int compareTo(ConfigValueElement o) {
			return config.getParentChildRef().compareTo(o.config.getParentChildRef());
		}

		void dispose() {
			elementObservable.onNext(null);
		}
	}
}
}
