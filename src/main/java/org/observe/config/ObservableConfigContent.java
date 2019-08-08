package org.observe.config;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfig.ObservableConfigPath;
import org.observe.config.ObservableConfig.ObservableConfigPathElement;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/** Contains implementation classes for {@link ObservableConfig} methods */
public class ObservableConfigContent {
	protected static class ObservableConfigChild<C extends ObservableConfig> implements ObservableValue<C> {
		private final TypeToken<C> theType;
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableConfig[] thePathElements;
		private final Subscription[] thePathElSubscriptions;
		private final ListenerList<Observer<? super ObservableValueEvent<C>>> theListeners;

		public ObservableConfigChild(TypeToken<C> type, ObservableConfig root, ObservableConfigPath path) {
			theType = type;
			theRoot = root;
			thePath = path;
			for (ObservableConfigPathElement el : path.getElements()) {
				if (el.isMulti() || el.getName().equals(ObservableConfig.ANY_NAME) || el.getName().equals(ObservableConfig.ANY_DEPTH))
					throw new IllegalArgumentException("Cannot use observeValue with a variable path");
			}
			thePathElements = new ObservableConfig[path.getElements().size() + 1];
			thePathElements[0] = theRoot;
			thePathElSubscriptions = new Subscription[thePathElements.length];
			theListeners = ListenerList.build().withInUse(this::setInUse).build();
		}

		ObservableConfig getRoot() {
			return theRoot;
		}

		void setInUse(boolean inUse) {
			try (Transaction t = theRoot.lock(false, null)) {
				if (inUse) {
					watchPathElement(0);
					if (resolvePath(1, false, i -> watchPathElement(i)))
						watchTerminal();
				} else {
					invalidate(0);
					for (int i = thePathElSubscriptions.length - 1; i >= 0; i--) {
						if (thePathElSubscriptions[i] != null) {
							thePathElSubscriptions[i].unsubscribe();
							thePathElSubscriptions[i] = null;
						}
					}
				}
			}
		}

		private void pathChanged(int pathIndex, CollectionChangeType type, ObservableConfig newValue) {
			boolean reCheck = false;
			switch (type) {
			case add:
				// This can affect the value if the new value matches the path and appears before the currently-used config
				if (thePath.getElements().get(pathIndex - 1).matches(newValue) && (thePathElements[pathIndex] == null//
					|| newValue.getParentChildRef().compareTo(thePathElements[pathIndex].getParentChildRef()) < 0))
					reCheck = true; // The new element needs to replace the current element at the path index
				break;
			case remove:
			case set:
				if (newValue == thePathElements[pathIndex])
					reCheck = true;
				else if (newValue.getParentChildRef().compareTo(thePathElements[pathIndex].getParentChildRef()) < 0//
					&& thePath.getElements().get(pathIndex - 1).matches(newValue))
					break;
			}
			if (reCheck) {
				invalidate(pathIndex);
				if (resolvePath(pathIndex, false, i -> watchPathElement(i)))
					watchTerminal();
			}
		}

		private void watchPathElement(int pathIndex) {
			if (pathIndex >= thePath.getElements().size())
				return;
			thePathElSubscriptions[pathIndex] = thePathElements[pathIndex].getAllContent().getValues()
				.onChange(evt -> pathChanged(pathIndex + 1, evt.getType(), evt.getNewValue()));
		}

		private void watchTerminal() {
			int lastIdx = thePathElements.length - 1;
			thePathElSubscriptions[lastIdx] = thePathElements[lastIdx].watch(ObservableConfig.EMPTY_PATH).act(evt -> {
				fire(createChangeEvent((C) thePathElements[lastIdx], (C) thePathElements[lastIdx], evt));
			});
		}

		private void fire(ObservableValueEvent<C> event) {
			try (Transaction t = Causable.use(event)) {
				theListeners.forEach(//
					listener -> listener.onNext(event));
			}
		}

		@Override
		public TypeToken<C> getType() {
			return theType;
		}

		@Override
		public C get() {
			try (Transaction t = lock()) {
				if (!resolvePath(1, false, null))
					return null;
				return (C) thePathElements[thePathElements.length - 1];
			}
		}

		private void invalidate(int startIndex) {
			for (int i = thePathElSubscriptions.length - 1; i >= startIndex; i--) {
				if (thePathElSubscriptions[i] != null) {
					thePathElSubscriptions[i].unsubscribe();
					thePathElSubscriptions[i] = null;
				}
			}
		}

		boolean resolvePath(int startIndex, boolean createIfAbsent, IntConsumer onResolution) {
			ObservableConfig parent = thePathElements[startIndex - 1];
			boolean resolved = true;
			int i;
			for (i = startIndex; i < thePathElements.length; i++) {
				ObservableConfig child;
				if (thePathElements[i] != null && thePathElements[i].getParent() != null) {
					child = thePathElements[i];
					continue;
				}
				child = parent.getChild(thePath.getElements().get(i - 1), createIfAbsent, null);
				if (child == null) {
					resolved = false;
					break;
				} else {
					thePathElements[i] = parent = child;
					if (onResolution != null)
						onResolution.accept(i);
				}
			}
			if (!resolved)
				Arrays.fill(thePathElements, i, thePathElements.length, null);
			return resolved;
		}

		@Override
		public Observable<ObservableValueEvent<C>> noInitChanges() {
			return new Observable<ObservableValueEvent<C>>() {
				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<C>> observer) {
					return theListeners.add(observer, true)::run;
				}

				@Override
				public boolean isSafe() {
					return theRoot.isLockSupported();
				}

				@Override
				public Transaction lock() {
					return theRoot.lock(false, null);
				}

				@Override
				public Transaction tryLock() {
					return theRoot.tryLock(false, null);
				}
			};
		}
	}

	protected static class ObservableConfigValue<T> implements SettableValue<T> {
		private final ObservableConfigChild<ObservableConfig> theConfigChild;
		private final TypeToken<T> theType;
		private final Function<ObservableConfig, ? extends T> theParser;
		private final BiConsumer<ObservableConfig, ? super T> theFormat;

		public ObservableConfigValue(TypeToken<T> type, ObservableConfig root, ObservableConfigPath path,
			Function<ObservableConfig, ? extends T> parser, BiConsumer<ObservableConfig, ? super T> format) {
			theConfigChild = new ObservableConfigChild<>(ObservableConfig.TYPE, root, path);
			theType = type;
			theParser = parser;
			theFormat = format;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			try (Transaction t = lock()) {
				ObservableConfig config = theConfigChild.get();
				return parse(config);
			}
		}

		private T parse(ObservableConfig config) {
			return theParser.apply(config);
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return theConfigChild.noInitChanges().map(evt -> createChangeEvent(parse(evt.getOldValue()), parse(evt.getNewValue()), evt));
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction t = theConfigChild.getRoot().lock(true, cause)) {
				String msg = isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				theConfigChild.resolvePath(1, true, null);
				T oldValue = parse(theConfigChild.get());
				theFormat.accept(theConfigChild.get(), value);
				return oldValue;
			}
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return TypeTokens.get().isInstance(theType, value) ? null : StdMsg.BAD_TYPE;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_ENABLED;
		}
	}

	protected static class ObservableConfigValues<T> extends ObservableCollectionWrapper<T> {
		private final ObservableValueSet<? extends ObservableConfig> theConfigs;
		private final TypeToken<T> theType;
		private final ObservableConfigFormat<T> theFormat;
		private final Observable<?> theUntil;

		private final ObservableCollection<ConfigValueElement> theValueElements;
		private final ObservableCollection<T> theValues;

		private boolean isAdding;
		private T theAddingValue;

		/**
		 * @param configs The set of observable configs backing each entity
		 * @param type The entity type
		 * @param format The format for this value set's values
		 * @param until The observable on which to release resources
		 */
		public ObservableConfigValues(ObservableValueSet<? extends ObservableConfig> configs, TypeToken<T> type,
			ObservableConfigFormat<T> format, Observable<?> until) {
			theConfigs = configs;
			theType = type;
			theFormat = format;
			theUntil = until == null ? Observable.empty() : until;

			theValueElements = ((ObservableCollection<ObservableConfig>) theConfigs.getValues()).flow()
				.map(new TypeToken<ConfigValueElement>() {}, cfg -> new ConfigValueElement(cfg), //
					opts -> opts.cache(true).reEvalOnUpdate(false))
				.collectActive(theUntil);
			Subscription valueElSub = theValueElements.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					evt.getNewValue().theValueId = evt.getElementId();
					evt.getNewValue().initialize(isAdding, theAddingValue);
					break;
				case remove:
					evt.getNewValue().removed();
					break;
				case set:
					evt.getNewValue().update(evt);
					break;
				}
			}, true);
			theUntil.take(1).act(__ -> valueElSub.unsubscribe());
			theValues = theValueElements.flow().map(type, cve -> cve.theInstance, opts -> opts.cache(false)).collectPassive();
			init(theValues);
		}

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			return null;
		}

		@Override
		public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				isAdding = true;
				theAddingValue = value;
				after = after == null ? null : theValueElements.getElement(after).get().theConfig.getParentChildRef();
				before = before == null ? null : theValueElements.getElement(before).get().theConfig.getParentChildRef();
				return theValueElements.getElementBySource(//
					theConfigs.create(after, before, first).create(cfg -> {
						theFormat.format(value, cfg);
					}).getElementId()).get();
			} finally {
				isAdding = false;
				theAddingValue = null;
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			for (ElementId el : elements)
				theValueElements.getElement(el).get().set(value);
		}

		@Override
		public MutableCollectionElement<T> mutableElement(ElementId id) {
			ConfigValueElement cve = theValueElements.getElement(id).get();
			return new MutableCollectionElement<T>() {
				@Override
				public ElementId getElementId() {
					return id;
				}

				@Override
				public T get() {
					return cve.get();
				}

				@Override
				public BetterCollection<T> getCollection() {
					return ObservableConfigValues.this;
				}

				@Override
				public String isEnabled() {
					return null;
				}

				@Override
				public String isAcceptable(T value) {
					return null;
				}

				@Override
				public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
					cve.set(value);
				}

				@Override
				public String canRemove() {
					return null;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					cve.theConfig.remove();
				}
			};
		}

		class ConfigValueElement implements CollectionElement<T> {
			private final ObservableConfig theConfig;
			private final SimpleObservable<Void> theValueUntil;
			ElementId theValueId;
			private T theInstance;

			public ConfigValueElement(ObservableConfig config) {
				theConfig = config;
				theValueUntil = new SimpleObservable<>(null, false, null, builder -> builder.unsafe().build());
			}

			@Override
			public ElementId getElementId() {
				return theValueId;
			}

			@Override
			public T get() {
				return theInstance;
			}

			void set(T value) {
				theFormat.format(value, theConfig);
				theInstance = value;
			}

			void initialize(boolean added, T addedValue) {
				if (added)
					theInstance = addedValue;
				else {
					try {
						theInstance = theFormat.parse(theConfig, theInstance, null, //
							Observable.or(theUntil, theValueUntil));
					} catch (ParseException e) {
						System.err.println("Could not initialize " + theConfig.getPath() + "(" + theType + ")");
						e.printStackTrace();
					}
				}
			}

			void update(Causable cause) {
				ObservableConfigEvent configCause = cause
					.getCauseLike(c -> c instanceof ObservableConfigEvent ? (ObservableConfigEvent) c : null);
				try {
					theInstance = theFormat.parse(theConfig, theInstance, //
						configCause == null ? null : configCause.asFromChild(), //
							Observable.or(theUntil, theValueUntil));
				} catch (ParseException e) {
					System.err.println("Could not update " + theConfig.getPath() + "(" + theType + ")");
					e.printStackTrace();
				}
			}

			void removed() {
				theValueUntil.onNext(null);
			}

			@Override
			public String toString() {
				return theConfig.toString();
			}
		}
	}

	/**
	 * Implements {@link ObservableConfig#observeEntities(ObservableConfigPath, TypeToken, ConfigEntityFieldParser, Observable)}
	 *
	 * @param <T> The entity type
	 */
	protected static class ObservableConfigEntityValues<T> implements ObservableValueSet<T> {
		private final ObservableValueSet<? extends ObservableConfig> theConfigs;
		private final EntityConfiguredValueType<T> theType;
		private final QuickMap<String, String> theFieldChildNames;
		private final ConfigEntityFieldParser theFieldParser;

		private final ObservableCollection<ConfigValueElement> theValueElements;
		private final ObservableCollection<T> theValues;

		QuickMap<String, Object> theNewInstanceFields;
		Consumer<? super T> thePreAddAction;
		ConfigValueElement theNewElement;
		boolean isUpdating;

		/**
		 * @param configs The set of observable configs backing each entity
		 * @param type The entity type
		 * @param fieldParser The parsers/formatters/default values for each field
		 * @param until The observable on which to release resources
		 */
		public ObservableConfigEntityValues(ObservableValueSet<? extends ObservableConfig> configs, TypeToken<T> type,
			ConfigEntityFieldParser fieldParser, Observable<?> until) {
			theConfigs = configs;
			Map<Method, BiFunction<T, Object[], Object>> customMethods = new HashMap<>();
			try {
				customMethods.put(Object.class.getDeclaredMethod("toString"), (proxy, args) -> {
					ConfigValueElement cve = (ConfigValueElement) ((EntityConfiguredValueType<T>) getType()).getAssociated(proxy);
					return cve.print();
				});
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IllegalStateException(e);
			}
			theType = new EntityConfiguredValueType<>(type, customMethods);
			theFieldChildNames = theType.getFields().keySet().createMap(//
				fieldIndex -> StringUtils.parseByCase(theType.getFields().keySet().get(fieldIndex)).toKebabCase()).unmodifiable();
			theFieldParser = fieldParser;

			theValueElements = ((ObservableCollection<ObservableConfig>) theConfigs.getValues()).flow()
				.map(new TypeToken<ConfigValueElement>() {}, cfg -> new ConfigValueElement(cfg), //
					opts -> opts.cache(true).reEvalOnUpdate(false))
				.collectActive(until);
			Subscription valueElSub = theValueElements.subscribe(evt -> {
				if (isUpdating)
					return;
				switch (evt.getType()) {
				case add:
					evt.getNewValue().theValueId = evt.getElementId();
					evt.getNewValue().initialize(theNewInstanceFields);
					theNewElement = evt.getNewValue();
					if (thePreAddAction != null) {
						try {
							thePreAddAction.accept(evt.getNewValue().get());
						} catch (RuntimeException e) { // Can't allow exceptions to propagate here--could mess up the collection structures
							System.err.println("Exception in pre-add action");
							e.printStackTrace();
						}
					}
					break;
				case set:
					evt.getNewValue().update(evt);
					break;
				default:
					break;
				}
			}, true);
			until.take(1).act(__ -> valueElSub.unsubscribe());
			theValues = theValueElements.flow().map(type, cve -> cve.theInstance, opts -> opts.cache(false)).collectPassive();
		}

		@Override
		public ConfiguredValueType<T> getType() {
			return theType;
		}

		@Override
		public ObservableCollection<? extends T> getValues() {
			return theValues;
		}

		protected final ElementId getConfigElement(ElementId valueElement) {
			if (valueElement == null)
				return null;
			return ((ObservableCollection<ObservableConfig>) theConfigs.getValues())
				.getElement(theValueElements.getElement(valueElement).get().theConfig, true).getElementId();
		}

		@Override
		public ValueCreator<T> create(ElementId after, ElementId before, boolean first) {
			ElementId configAfter = getConfigElement(after);
			ElementId configBefore = getConfigElement(before);
			return new SimpleValueCreator<T>(theType) {
				private final ValueCreator<? extends ObservableConfig> theConfigCreator = theConfigs.create(configAfter, configBefore,
					first);

				@Override
				public <F> ValueCreator<T> with(ConfiguredValueField<? super T, F> field, F value) throws IllegalArgumentException {
					super.with(field, value);
					Format<F> fieldFormat = theFieldParser.getFieldFormat(field);
					String formatted = fieldFormat.format(value);
					theConfigCreator.with(theFieldChildNames.get(field.getIndex()), formatted);
					return this;
				}

				@Override
				public CollectionElement<T> create(Consumer<? super T> preAddAction) {
					ConfigValueElement cve;
					try (Transaction t = theConfigs.getValues().lock(true, null)) {
						thePreAddAction = preAddAction;
						theNewInstanceFields = getFieldValues();
						theConfigCreator.create();
						theNewInstanceFields = null;
						cve = theNewElement;
						theNewElement = null;
					}
					cve.initialize(getFieldValues());
					return cve;
				}
			};
		}

		class ConfigValueElement implements CollectionElement<T> {
			private final ObservableConfig theConfig;
			private QuickMap<String, Object> theFieldValues;
			ElementId theValueId;
			private T theInstance;

			public ConfigValueElement(ObservableConfig config) {
				theConfig = config;
			}

			void initialize(QuickMap<String, Object> fieldValues) {
				theFieldValues = fieldValues == null ? theType.getFields().keySet().createMap() : fieldValues.copy();
				theInstance = theType.create(this::getField, this::setField);
				theType.associate(theInstance, this);
			}

			@Override
			public ElementId getElementId() {
				return theValueId;
			}

			@Override
			public T get() {
				return theInstance;
			}

			void update(Causable cause) {
				ObservableConfigEvent configCause = cause
					.getCauseLike(c -> c instanceof ObservableConfigEvent ? (ObservableConfigEvent) c : null);
				if (configCause != null && configCause.relativePath.size() > 1) {
					ObservableConfig child = configCause.relativePath.get(1);
					int field = theFieldValues.keyIndexTolerant(child.getName());
					if (field >= 0)
						theFieldValues.put(field, null); // Clear the cache for the specific field, re-parse when needed
				} else
					theFieldValues.clear(); // Clear the cache for all fields, re-parse each when needed
			}

			Object getField(int fieldIndex) {
				Object value = theFieldValues.get(fieldIndex);
				if (value == null) {
					String serialized = theConfig.get(theFieldValues.keySet().get(fieldIndex));
					if (serialized != null) {
						try {
							theFieldValues.put(fieldIndex,
								value = theFieldParser.getFieldFormat(theType.getFields().get(fieldIndex)).parse(serialized));
						} catch (ParseException e) {
							throw new IllegalStateException(
								"Could not parse value \"" + serialized + "\" fpr field " + theType.getFields().get(fieldIndex) + ": "
									+ e.getMessage(),
									e);
						}
					} else
						value = theFieldParser.getDefaultValue(theType.getFields().get(fieldIndex));
				}
				return value;
			}

			Object setField(int fieldIndex, Object fieldValue) {
				Format<Object> fieldFormat = (Format<Object>) theFieldParser.getFieldFormat(theType.getFields().get(fieldIndex));
				String formatted = fieldFormat.format(fieldValue);
				try (Transaction t = theConfigs.getValues().lock(true, null)) {
					isUpdating = true;
					theFieldValues.put(fieldIndex, fieldValue);
					theConfig.set(theFieldChildNames.get(fieldIndex), formatted);
				} finally {
					isUpdating = false;
				}
				return null; // Return value doesn't mean anything
			}

			String print() {
				StringBuilder str = new StringBuilder(theConfig.getName()).append('(');
				boolean first = true;
				for (int i = 0; i < theFieldValues.keySet().size(); i++) {
					String value = theConfig.get(theFieldValues.keySet().get(i));
					if (value != null) {
						if (first)
							first = false;
						else
							str.append(", ");
						str.append(theFieldValues.keySet().get(i)).append('=').append(value);
					}
				}
				str.append(')');
				return str.toString();
			}

			@Override
			public String toString() {
				return theConfig.toString();
			}
		}
	}

	/**
	 * Superclass to assist in implementing the collection behind {@link ObservableConfig#getContent(ObservableConfigPath)}
	 *
	 * @param <C> The config sub-type
	 */
	protected static abstract class AbstractObservableConfigContent<C extends ObservableConfig> implements ObservableCollection<C> {
		private final ObservableConfig theConfig;
		private final TypeToken<C> theType;

		/**
		 * @param config The root config
		 * @param type The config sub-type
		 */
		public AbstractObservableConfigContent(ObservableConfig config, TypeToken<C> type) {
			theConfig = config;
			theType = type;
		}

		/** @return The root config */
		public ObservableConfig getConfig() {
			return theConfig;
		}

		@Override
		public TypeToken<C> getType() {
			return theType;
		}

		@Override
		public boolean isLockSupported() {
			return theConfig.isLockSupported();
		}

		@Override
		public boolean isContentControlled() {
			return false;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theConfig.getStamp(structuralOnly);
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theConfig.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theConfig.tryLock(write, structural, cause);
		}

		@Override
		public Equivalence<? super C> equivalence() {
			return Equivalence.DEFAULT;
		}
	}

	/**
	 * Implements the collection behind {@link ObservableConfig#getAllContent()}
	 *
	 * @param <C> The config sub-type
	 */
	protected static class FullObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		/**
		 * @param config The parent config
		 * @param type The config sub-type
		 */
		public FullObservableConfigContent(ObservableConfig config, TypeToken<C> type) {
			super(config, type);
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				ObservableConfig lastChild = getConfig()._getContent().getLast();
				while (lastChild != null) {
					ObservableConfig nextLast = CollectionElement
						.get(getConfig()._getContent().getAdjacentElement(lastChild.getParentChildRef(), false));
					lastChild.remove();
					lastChild = nextLast;
				}
			}
		}

		@Override
		public int size() {
			return getConfig()._getContent().size();
		}

		@Override
		public boolean isEmpty() {
			return getConfig()._getContent().isEmpty();
		}

		@Override
		public CollectionElement<C> getElement(int index) {
			ObservableConfig child = getConfig()._getContent().get(index);
			return new ConfigCollectionElement<>(child);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getConfig()._getContent().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getConfig()._getContent().getElementsAfter(id);
		}

		@Override
		public CollectionElement<C> getElement(C value, boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(value, first));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
			return new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getTerminalElement(boolean first) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getTerminalElement(first));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public CollectionElement<C> getAdjacentElement(ElementId elementId, boolean next) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(elementId, next));
			return config == null ? null : new ConfigCollectionElement<>(config);
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
			return new MutableConfigCollectionElement<>(config, this);
		}

		@Override
		public CollectionElement<C> getElementBySource(ElementId sourceEl) {
			ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElementBySource(sourceEl));
			return new ConfigCollectionElement<>(config);
		}

		@Override
		public String canAdd(C value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<C> addElement(C value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void setValue(Collection<ElementId> elements, C value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends C>> observer) {
			return getConfig().watch(getConfig().createPath(ObservableConfig.ANY_NAME)).act(evt -> {
				C child = (C) evt.relativePath.get(0);
				C oldValue = evt.changeType == CollectionChangeType.add ? null : child;
				ObservableCollectionEvent<C> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(), getType(),
					child.getIndexInParent(), evt.changeType, oldValue, child, evt);
				observer.accept(collEvt);
			});
		}
	}

	private static class ConfigCollectionElement<C extends ObservableConfig> implements CollectionElement<C> {
		final ObservableConfig theConfig;

		ConfigCollectionElement(ObservableConfig config) {
			theConfig = config;
		}

		@Override
		public ElementId getElementId() {
			return theConfig.getParentChildRef();
		}

		@Override
		public C get() {
			return (C) theConfig;
		}

		@Override
		public int hashCode() {
			return theConfig.getParentChildRef().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ConfigCollectionElement && ((ConfigCollectionElement<?>) obj).theConfig == theConfig;
		}

		@Override
		public String toString() {
			return theConfig.toString();
		}
	}

	private static class MutableConfigCollectionElement<C extends ObservableConfig> extends ConfigCollectionElement<C>
	implements MutableCollectionElement<C> {
		private final ObservableCollection<C> theCollection;

		MutableConfigCollectionElement(ObservableConfig config, ObservableCollection<C> collection) {
			super(config);
			theCollection = collection;
		}

		@Override
		public BetterCollection<C> getCollection() {
			return theCollection;
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(C value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(C value) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theConfig.remove();
		}
	}

	/**
	 * Implements the collection behind {@link ObservableConfig#getContent(ObservableConfigPath)} for single-element paths
	 *
	 * @param <C> The sub-type of config
	 */
	protected static class SimpleObservableConfigContent<C extends ObservableConfig> extends AbstractObservableConfigContent<C> {
		private final ObservableConfigPathElement thePathElement;

		/**
		 * @param config The parent config
		 * @param type The config sub-type
		 * @param pathEl The path element
		 */
		public SimpleObservableConfigContent(ObservableConfig config, TypeToken<C> type, ObservableConfigPathElement pathEl) {
			super(config, type);
			thePathElement = pathEl;
		}

		@Override
		public int size() {
			try (Transaction t = getConfig().lock(false, null)) {
				return (int) getConfig()._getContent().stream().filter(thePathElement::matches).count();
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = getConfig().lock(false, null)) {
				return getConfig()._getContent().stream().anyMatch(thePathElement::matches);
			}
		}

		@Override
		public CollectionElement<C> getElement(int index) {
			try (Transaction t = getConfig().lock(false, null)) {
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
					if (thePathElement.matches(el.get())) {
						if (i == index)
							return new ConfigCollectionElement<>(el.get());
						i++;
					}
				}
				throw new IndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				int idx = getConfig()._getContent().getElementsBefore(id);
				int i = 0;
				int matches = 0;
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
					if (i == idx)
						break;
					if (thePathElement.matches(el.get()))
						matches++;
					i++;
				}
				return matches;
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
				if (config == null || !thePathElement.matches(config))
					throw new NoSuchElementException();
				int i = 0;
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().reverse().elements()) {
					if (thePathElement.matches(el.get())) {
						if (el.get() == config)
							return i;
						i++;
					}
				}
				throw new IllegalStateException("Element found but then not found");
			}
		}

		@Override
		public CollectionElement<C> getElement(C value, boolean first) {
			if (!thePathElement.matches(value))
				return null;
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(value, first));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getElementBySource(ElementId sourceEl) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElementBySource(sourceEl));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getTerminalElement(boolean first) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getTerminalElement(first));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(config.getParentChildRef(), first));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public CollectionElement<C> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(elementId, next));
				while (config != null && !thePathElement.matches(config))
					config = CollectionElement.get(getConfig()._getContent().getAdjacentElement(config.getParentChildRef(), next));
				return config == null ? null : new ConfigCollectionElement<>(config);
			}
		}

		@Override
		public MutableCollectionElement<C> mutableElement(ElementId id) {
			try (Transaction t = getConfig().lock(false, null)) {
				ObservableConfig config = CollectionElement.get(getConfig()._getContent().getElement(id));
				if (!thePathElement.matches(config))
					throw new NoSuchElementException();
				return new MutableConfigCollectionElement<>(config, this);
			}
		}

		@Override
		public String canAdd(C value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<C> addElement(C value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void setValue(Collection<ElementId> elements, C value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			try (Transaction t = getConfig().lock(true, null)) {
				for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
					if (thePathElement.matches(el.get()))
						el.get().remove();
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends C>> observer) {
			String watchPath = thePathElement.getName() + ObservableConfig.PATH_SEPARATOR + ObservableConfig.ANY_DEPTH;
			return getConfig().watch(getConfig().createPath(watchPath)).act(evt -> {
				C child = (C) evt.relativePath.get(0);
				boolean postMatches = thePathElement.matches(child);
				boolean preMatches = evt.changeType == CollectionChangeType.set ? thePathElement.matchedBefore(child, evt)
					: postMatches;
				if (preMatches || postMatches) {
					int index;
					if (postMatches)
						index = getElementsBefore(child.getParentChildRef());
					else {
						int i = 0;
						for (CollectionElement<ObservableConfig> el : getConfig()._getContent().elements()) {
							if (el.get() == child) {
								index = i;
								break;
							} else if (thePathElement.matches(el.get())) {
								i++;
							}
						}
						throw new IllegalStateException("Element found but then not found");
					}

					CollectionChangeType changeType;
					if (evt.relativePath.size() > 1)
						changeType = CollectionChangeType.set;
					else if (preMatches && postMatches)
						changeType = evt.changeType;
					else if (preMatches)
						changeType = CollectionChangeType.remove;
					else
						changeType = CollectionChangeType.add;

					C oldValue = changeType == CollectionChangeType.add ? null : child;
					ObservableCollectionEvent<C> collEvt = new ObservableCollectionEvent<>(child.getParentChildRef(), getType(), index,
						changeType, oldValue, child, evt);
					try (Transaction t = Causable.use(collEvt)) {
						observer.accept(collEvt);
					}
				}
			});
		}
	}

	/**
	 * Implements the value set portion of {@link ObservableConfig#getContent(ObservableConfigPath)}
	 *
	 * @param <C> The sub-type of config
	 */
	protected static class ObservableChildSet<C extends ObservableConfig> implements ObservableValueSet<C> {
		private final ObservableConfig theRoot;
		private final ObservableConfigPath thePath;
		private final ObservableCollection<C> theChildren;

		/**
		 * @param root The root config
		 * @param path The path for the values
		 * @param children The child collection
		 */
		public ObservableChildSet(ObservableConfig root, ObservableConfigPath path, ObservableCollection<C> children) {
			theRoot = root;
			thePath = path;
			theChildren = children;
		}

		/** @return The root config */
		protected ObservableConfig getRoot() {
			return theRoot;
		}

		/** @return the path for the values */
		protected ObservableConfigPath getPath() {
			return thePath;
		}

		@Override
		public ConfiguredValueType<C> getType() {
			return new ConfiguredValueType<C>() {
				@Override
				public TypeToken<C> getType() {
					return theChildren.getType();
				}

				@Override
				public QuickMap<String, ConfiguredValueField<? super C, ?>> getFields() {
					return QuickSet.<String> empty().createMap();
				}

				@Override
				public int getFieldIndex(Function<? super C, ?> fieldGetter) {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean allowsCustomFields() {
					return true;
				}
			};
		}

		@Override
		public ObservableCollection<? extends C> getValues() {
			return theChildren;
		}

		@Override
		public ValueCreator<C> create(ElementId after, ElementId before, boolean first) {
			return new ValueCreator<C>() {
				private Map<String, String> theFields;

				@Override
				public ConfiguredValueType<C> getType() {
					return ObservableChildSet.this.getType();
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return Collections.emptySet();
				}

				@Override
				public ValueCreator<C> with(String fieldName, Object value) throws IllegalArgumentException {
					if (theFields == null)
						theFields = new LinkedHashMap<>();
					theFields.put(fieldName, String.valueOf(value));
					return this;
				}

				@Override
				public <F> ValueCreator<C> with(ConfiguredValueField<? super C, F> field, F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public <F> ValueCreator<C> with(Function<? super C, F> field, F value) throws IllegalArgumentException {
					throw new UnsupportedOperationException();
				}

				@Override
				public CollectionElement<C> create(Consumer<? super C> preAddAction) {
					ObservableConfig afterChild = after == null ? null : theChildren.getElement(after).get();
					ObservableConfig beforeChild = after == null ? null : theChildren.getElement(before).get();
					ElementId newChildId;
					try (Transaction t = theRoot.lock(true, null)) {
						ObservableConfig parent = theRoot.getChild(thePath.getParent(), true, null);
						ObservableConfig newChild = parent.addChild(afterChild, beforeChild, first, thePath.getLastElement().getName(),
							cfg -> {
								if (theFields != null)
									for (Map.Entry<String, String> field : theFields.entrySet())
										cfg.set(field.getKey(), field.getValue());
								if (preAddAction != null)
									preAddAction.accept((C) cfg);
							});
						newChildId = theChildren.getElementBySource(newChild.getParentChildRef()).getElementId();
					}
					return theChildren.getElement(newChildId);
				}
			};
		}
	}
}
