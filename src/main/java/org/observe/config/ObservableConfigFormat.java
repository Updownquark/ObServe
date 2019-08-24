package org.observe.config;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfig.ObservableConfigPath;
import org.observe.util.EntityReflector;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;

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

	T parse(ObservableConfig parent, ObservableConfig config, T previousValue, ObservableConfig.ObservableConfigEvent change,
		Observable<?> until)
			throws ParseException;

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
		public T parse(ObservableConfig parent, ObservableConfig config, T previousValue, ObservableConfig.ObservableConfigEvent change,
			Observable<?> until)
				throws ParseException {
			if (config == null)
				return defaultValue.get();
			if (change != null && change.relativePath.size() > 1)
				return previousValue; // Changing a sub-config doesn't affect this value
			String value = config.getValue();
			if (value == null)
				return null;
			return format.parse(value);
		}
	}

	static <E> EntityConfigFormat<E> ofEntity(EntityConfiguredValueType<E> entityType, ConfigEntityFieldParser formats) {
		return new EntityConfigFormat<>(entityType, formats);
	}

	class EntityConfigFormat<E> implements ObservableConfigFormat<E> {
		public final EntityConfiguredValueType<E> entityType;
		public final ConfigEntityFieldParser formats;
		private final ObservableConfigFormat<?>[] fieldFormats;
		private QuickMap<String, String> theFieldChildNames;
		private QuickMap<String, String> theFieldsByChildName;

		public EntityConfigFormat(EntityConfiguredValueType<E> entityType, ConfigEntityFieldParser formats) {
			this.entityType = entityType;
			this.formats = formats;
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
		public E parse(ObservableConfig parent, ObservableConfig config, E previousValue, ObservableConfig.ObservableConfigEvent change,
			Observable<?> until)
				throws ParseException {
			if (config != null && "true".equalsIgnoreCase(config.get("null")))
				return null;
			else if (previousValue == null)
				return createInstance(config, entityType.getFields().keySet().createMap(), until);
			else {
				if (change == null) {
					for (int i = 0; i < entityType.getFields().keySize(); i++)
						parseUpdatedField(config, i, previousValue, null, until);
				} else if (change.relativePath.isEmpty()) {
					// Change to the value doesn't change any fields
				} else {
					ObservableConfig child = change.relativePath.get(1);
					int fieldIdx = theFieldsByChildName.keyIndexTolerant(child.getName());
					if (change.relativePath.size() == 2 && !change.oldName.equals(child.getName())) {
						if (fieldIdx >= 0)
							parseUpdatedField(config, fieldIdx, previousValue, change, until);
						fieldIdx = theFieldsByChildName.keyIndexTolerant(change.oldName);
						if (fieldIdx >= 0)
							parseUpdatedField(config, fieldIdx, previousValue, null, until);
					} else if (fieldIdx >= 0)
						parseUpdatedField(config, fieldIdx, previousValue, change, until);
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
				ObservableConfig fieldConfig = config.getChild(theFieldChildNames.get(i));
				fieldValues.put(i, fieldFormats[i].parse(config, fieldConfig, null, null, until));
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
			ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(fieldIdx));
			if (change != null && fieldConfig != change.relativePath.get(1))
				return; // The update does not actually affect the field value
			Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(entityConfig, fieldConfig, oldValue,
				change == null ? null : change.asFromChild(), until);
			if (oldValue != newValue)
				((ConfiguredValueField<E, Object>) field).set(previousValue, newValue);
		}
	}

	static <E, C extends Collection<? extends E>> ObservableConfigFormat<C> ofCollection(TypeToken<C> collectionType,
		ObservableConfigFormat<E> elementFormat, String childName) {
		if(!TypeTokens.getRawType(collectionType).isAssignableFrom(ObservableCollection.class))
			throw new IllegalArgumentException("This class can only produce instances of "+ObservableCollection.class.getName()
				+", which is not compatible with type "+collectionType);
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
			public C parse(ObservableConfig parent, ObservableConfig config, C previousValue, ObservableConfigEvent change,
				Observable<?> until)
					throws ParseException {
				class ConfigContentValueWrapper extends ObservableCollectionWrapper<E> {
					private final ObservableCollection<E> theValues;
					private final SimpleObservable<Void> theContentUntil;

					ConfigContentValueWrapper(ObservableCollection<E> values, SimpleObservable<Void> contentUntil) {
						theValues = values;
						theContentUntil = contentUntil;
						init(theValues);
					}

					ObservableConfig getConfig() {
						return config;
					}
				}
				if (previousValue == null) {
					SimpleObservable<Void> contentUntil = new SimpleObservable<>(null, false, null, b -> b.unsafe());
					return (C) new ConfigContentValueWrapper(config.observeValues(childName, elementType, elementFormat, //
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

	static <E> ObservableConfigFormat<ObservableValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String childName,
		ConfigEntityFieldParser fieldParser) {
		return new ObservableConfigFormat<ObservableValueSet<E>>() {
			@Override
			public void format(ObservableValueSet<E> value, ObservableConfig config) {
				// Nah, we don't support calling set on a field like this
				throw new UnsupportedOperationException();// TODO Should we throw an exception?
			}

			@Override
			public ObservableValueSet<E> parse(ObservableConfig parent, ObservableConfig config, ObservableValueSet<E> previousValue,
				ObservableConfigEvent change, Observable<?> until) throws ParseException {
				if (previousValue != null)
					return previousValue; // The entity set knows how to handle the events, we don't need to
				if (previousValue == null) // TODO config can be null
					return config.observeEntities(config.createPath(childName), elementFormat.entityType.getType(), fieldParser, until);
			}
		};
	}

	class ObservableConfigEntityValues2<E> implements ObservableValueSet<E> {
		private final ObservableConfig theCollectionElement;
		private final EntityConfigFormat<E> theFormat;
		private final String theChildName;
		private final ConfigEntityFieldParser theFieldParser;
		private final Observable<?> theUntil;

		private final ObservableCollection<ConfigValueElement> theValueElements;
		private final ObservableCollection<E> theValues;

		QuickMap<String, Object> theNewInstanceFields;
		Consumer<? super E> thePreAddAction;
		ConfigValueElement theNewElement;

		ObservableConfigEntityValues2(ObservableConfig collectionElement, EntityConfigFormat<E> format, String childName,
			ConfigEntityFieldParser fieldParser, Observable<?> until) {
			theCollectionElement = collectionElement;
			theFormat = format;
			theChildName = childName;
			theFieldParser = fieldParser;
			theUntil = until;

			theValueElements = theCollectionElement.getContent(childName).getValues().flow()
				.map(new TypeToken<ConfigValueElement>() {}, cfg -> new ConfigValueElement(cfg), //
					opts -> opts.cache(true).reEvalOnUpdate(false))
				.collectActive(theUntil);
			theValues = theValueElements.flow().map(format.getEntityType().getType(), cve -> cve.theInstance, opts -> opts.cache(false))
				.collectPassive();
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			if (theNewInstanceFields != null)
				return; // Yeah, we already know--we're causing the change
			if (collectionChange.relativePath.isEmpty())
				return; // Doesn't affect us
			CollectionElement<ConfigValueElement> el = theValueElements
				.getElementBySource(collectionChange.relativePath.get(0).getParentChildRef());
			if (el == null) // Must be a different child
				return;
			try {
				theFormat.parse(theCollectionElement, el.get().theConfig, el.get().theInstance, collectionChange.asFromChild(), theUntil);
				theValueElements.mutableElement(el.getElementId()).set(el.get());
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}

		private class ConfigValueElement implements CollectionElement<E> {
			private final ObservableConfig theConfig;
			ElementId theValueId;
			private E theInstance;

			public ConfigValueElement(ObservableConfig config) {
				theConfig = config;
			}

			void initialize(QuickMap<String, Object> fieldValues) {
				fieldValues = fieldValues == null ? theFormat.getEntityType().getFields().keySet().createMap() : fieldValues.copy();
				try {
					theInstance = theFormat.createInstance(theConfig, fieldValues, theUntil);
					theFormat.getEntityType().associate(theInstance, ObservableConfigEntityValues2.this, this);
				} catch (ParseException e) {
					System.err.println("Could not parse instance for " + theConfig);
					e.printStackTrace();
				}
			}

			@Override
			public ElementId getElementId() {
				return theValueId;
			}

			@Override
			public E get() {
				return theInstance;
			}
		}
	}

	/**
	 * Implements {@link ObservableConfig#observeEntities(ObservableConfigPath, TypeToken, ConfigEntityFieldParser, Observable)}
	 *
	 * @param <T> The entity type
	 */
	static class ObservableConfigEntityValues<T> implements ObservableValueSet<T> {
		private final ObservableValueSet<? extends ObservableConfig> theConfigs;
		private final ConfigEntityFieldParser theFieldParser;
		private final EntityConfiguredValueType<T> theType;
		private final ObservableConfigFormat.EntityConfigFormat<T> theEntityFormat;

		private final ObservableCollection<ConfigValueElement> theValueElements;
		private final ObservableCollection<T> theValues;

		private final Observable<?> theUntil;
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
		ObservableConfigEntityValues(ObservableValueSet<? extends ObservableConfig> configs, TypeToken<T> type,
			ConfigEntityFieldParser fieldParser, Observable<?> until) {
			theConfigs = configs;
			EntityReflector.Builder<T> typeBuilder = EntityReflector.build(type);
			try {
				typeBuilder.withCustomMethod(Object.class.getDeclaredMethod("toString"), (proxy, args) -> {
					ConfigValueElement cve = (ConfigValueElement) ((EntityConfiguredValueType<T>) getType()).getAssociated(proxy, this);
					return cve.print();
				});
			} catch (NoSuchMethodException | SecurityException e) {
				throw new IllegalStateException(e);
			}
			ObservableConfigFormat<T> entityFormat;
			try {
				entityFormat = fieldParser.getConfigFormat(type, null);
			} catch (IllegalArgumentException e) {
				entityFormat = null;
			}
			if (entityFormat instanceof ObservableConfigFormat.EntityConfigFormat) {
				theEntityFormat = (ObservableConfigFormat.EntityConfigFormat<T>) entityFormat;
				theType = theEntityFormat.entityType;
			} else {
				theType = new EntityConfiguredValueType<>(typeBuilder.build());
				theEntityFormat = ObservableConfigFormat.ofEntity(theType, fieldParser);
				if (entityFormat == null)
					fieldParser.withFormat(type, theEntityFormat);
			}
			theFieldParser = fieldParser;

			theUntil = until == null ? Observable.empty() : until;
			theValueElements = ((ObservableCollection<ObservableConfig>) theConfigs.getValues()).flow()
				.map(new TypeToken<ConfigValueElement>() {}, cfg -> new ConfigValueElement(cfg), //
					opts -> opts.cache(true).reEvalOnUpdate(false))
				.collectActive(theUntil);

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
			theUntil.take(1).act(__ -> valueElSub.unsubscribe());
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
					theFieldParser.getConfigFormat(field); // Throws an exception if not supported
					getFieldValues().put(field.getName(), value);
					return this;
				}

				@Override
				public CollectionElement<T> create(Consumer<? super T> preAddAction) {
					ConfigValueElement cve;
					try (Transaction t = theConfigs.getValues().lock(true, null)) {
						thePreAddAction = preAddAction;
						theNewInstanceFields = getFieldValues();
						theConfigCreator.create(cfg -> {
							for (int i = 0; i < getFieldValues().keySize(); i++) {
								Object value = getFieldValues().get(i);
								if (value != null)
									theEntityFormat.formatField(theType.getFields().get(i), value, cfg);
							}
						});
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
			ElementId theValueId;
			private T theInstance;

			public ConfigValueElement(ObservableConfig config) {
				theConfig = config;
			}

			void initialize(QuickMap<String, Object> fieldValues) {
				fieldValues = fieldValues == null ? theType.getFields().keySet().createMap() : fieldValues.copy();
				try {
					theInstance = theEntityFormat.createInstance(theConfig, fieldValues, theUntil);
					theType.associate(theInstance, ObservableConfigEntityValues.this, this);
				} catch (ParseException e) {
					System.err.println("Could not parse instance for " + theConfig);
					e.printStackTrace();
				}
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
				try {
					theEntityFormat.parse(5, theConfig, theInstance, configCause, theUntil);
				} catch (ParseException e) {
					System.err.println("Could not update instance for " + theConfig);
					e.printStackTrace();
				}
			}

			String print() {
				StringBuilder str = new StringBuilder(theConfig.getName()).append('(');
				boolean first = true;
				for (int i = 0; i < theType.getFields().keySet().size(); i++) {
					String value = theConfig.get(theEntityFormat.getChildName(i));
					if (value != null) {
						if (first)
							first = false;
						else
							str.append(", ");
						str.append(theType.getFields().keySet().get(i)).append('=').append(value);
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
}
