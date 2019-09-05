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
import org.observe.collect.CollectionChangeType;
import org.observe.collect.DefaultObservableSortedSet;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;
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
		public E parse(ObservableConfig parent, ObservableConfig config, E previousValue, ObservableConfig.ObservableConfigEvent change,
			Observable<?> until)
				throws ParseException {
			if (config != null && "true".equalsIgnoreCase(config.get("null")))
				return null;
			else if (previousValue == null) {
				if (config == null)
					config = parent.addChild(configName);
				return createInstance(config, entityType.getFields().keySet().createMap(), until);
			} else {
				if (change == null) {
					for (int i = 0; i < entityType.getFields().keySize(); i++)
						parseUpdatedField(config, i, previousValue, null, until);
				} else if (change.relativePath.isEmpty()) {
					// Change to the value doesn't change any fields
				} else {
					ObservableConfig child = change.relativePath.get(0);
					int fieldIdx = theFieldsByChildName.keyIndexTolerant(child.getName());
					if (change.relativePath.size() == 1 && !change.oldName.equals(child.getName())) {
						if (fieldIdx >= 0)
							parseUpdatedField(config, fieldIdx, previousValue, change.asFromChild(), until);
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
				if (fieldValues.get(i) == null)
					fieldValues.put(i, fieldFormats[i].parse(config, fieldConfig, null, null, until));
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
			ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(fieldIdx));
			if (change != null) {
				if (change.relativePath.isEmpty() || fieldConfig != change.relativePath.get(0))
					return; // The update does not actually affect the field value
				change = change.asFromChild();
			}
			Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(entityConfig, fieldConfig, oldValue,
				change, until);
			if (oldValue != newValue)
				((ConfiguredValueField<E, Object>) field).set(previousValue, newValue);
		}
	}

	static <E, C extends Collection<? extends E>> ObservableConfigFormat<C> ofCollection(TypeToken<C> collectionType,
		ObservableConfigFormat<E> elementFormat, String parentName, String childName) {
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
					ObservableConfig child = config != null ? config : parent.addChild(parentName);
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
			public ObservableValueSet<E> parse(ObservableConfig parent, ObservableConfig config, ObservableValueSet<E> previousValue,
				ObservableConfigEvent change, Observable<?> until) throws ParseException {
				if (previousValue == null) {
					if (config == null)
						config = parent.addChild(parentName);
					return new ObservableConfigEntityValues<>(config, elementFormat, childName, fieldParser, until);
				} else {
					((ObservableConfigEntityValues<E>) previousValue).onChange(change);
					return previousValue;
				}
			}
		};
	}

	class ObservableConfigEntityValues<E> implements ObservableValueSet<E> {
		private final ObservableConfig theCollectionElement;
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

		ObservableConfigEntityValues(ObservableConfig collectionElement, EntityConfigFormat<E> format, String childName,
			ConfigEntityFieldParser fieldParser, Observable<?> until) {
			theCollectionElement = collectionElement;
			theFormat = format;
			theChildName = childName;
			theFieldParser = fieldParser;
			theUntil = until;

			try (Transaction t = theCollectionElement.lock(false, null)) {
				ObservableSortedSet<ConfigValueElement>[] valueElsRef = new ObservableSortedSet[1];
				theValueElements = new DefaultObservableSortedSet<>(new TypeToken<ConfigValueElement>() {},
					new BetterTreeSet<>(false, ConfigValueElement::compareTo), element -> {
						return valueElsRef[0]
							.search(cve -> element.compareTo(cve.config.getParentChildRef()), SortedSearchFilter.OnlyMatch)
							.getElementId();
					});
				valueElsRef[0] = theValueElements;
				for (ObservableConfig child : theCollectionElement.getContent(theChildName).getValues()) {
					theValueElements.add(new ConfigValueElement(child));
				}
			}

			// theValueElements = theCollectionElement.getContent(childName).getValues().flow()
			// .map(new TypeToken<ConfigValueElement>() {}, cfg -> new ConfigValueElement(cfg), //
			// opts -> opts.cache(true).reEvalOnUpdate(false))
			// .collectActive(theUntil);

			theValues = theValueElements.flow().map(format.getEntityType().getType(), cve -> cve.instance, opts -> opts.cache(false))
				.collectPassive();
		}

		protected void onChange(ObservableConfigEvent collectionChange) {
			if (collectionChange.relativePath.isEmpty())
				return; // Doesn't affect us
			boolean elementChange = collectionChange.relativePath.size() == 1;
			ObservableConfig config = collectionChange.relativePath.get(0);
			if (elementChange && collectionChange.changeType == CollectionChangeType.add) {
				CollectionElement<ConfigValueElement> el = theValueElements.search(//
					cve -> config.getParentChildRef().compareTo(cve.config.getParentChildRef()), SortedSearchFilter.PreferLess);
				ConfigValueElement newEl = new ConfigValueElement(config);
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
				if (collectionChange.relativePath.size() == 1 && collectionChange.changeType == CollectionChangeType.remove)
					theValueElements.mutableElement(el.getElementId()).remove();

				else {
					try {
						theFormat.parse(theCollectionElement, el.get().config, el.get().instance, collectionChange.asFromChild(),
							theUntil);
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
					try (Transaction t = theCollectionElement.lock(true, null)) {
						thePreAddAction = preAddAction;
						theCollectionElement.addChild(configAfter, configBefore, first, theChildName, cfg -> {
							try {
								theNewInstance = theFormat.createInstance(cfg, getFieldValues(), theUntil);
							} catch (ParseException e) {
								throw new IllegalStateException("Could not create instance", e);
							}
						});
						cve = theNewElement;
						theNewElement = null;
					}
					return theValues.getElement(cve);
				}
			};
		}

		private class ConfigValueElement implements Comparable<ConfigValueElement> {
			final ObservableConfig config;
			final E instance;

			public ConfigValueElement(ObservableConfig config) {
				this.config = config;
				E inst = null;
				if (theNewInstance != null) {
					inst = theNewInstance;
					theNewInstance = null;
				} else {
					try {
						inst = theFormat.parse(theCollectionElement, this.config, null, null, theUntil);
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
		}
	}
}
