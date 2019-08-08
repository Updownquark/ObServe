package org.observe.config;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public interface ObservableConfigFormat<T> {
	void format(T value, ObservableConfig config);

	T parse(ObservableConfig config, T previousValue, ObservableConfig.ObservableConfigEvent change, Observable<?> until)
		throws ParseException;

	static <T> ObservableConfigFormat<T> ofQommonFormat(Format<T> format) {
		return new SimpleConfigFormat<>(format);
	}

	class SimpleConfigFormat<T> implements ObservableConfigFormat<T> {
		public final Format<T> format;

		public SimpleConfigFormat(Format<T> format) {
			this.format = format;
		}

		@Override
		public void format(T value, ObservableConfig config) {
			if (value == null)
				config.setValue(null);
			else
				config.setValue(format.format(value));
		}

		@Override
		public T parse(ObservableConfig config, T previousValue, ObservableConfig.ObservableConfigEvent change, Observable<?> until)
			throws ParseException {
			if (change != null && !change.relativePath.isEmpty())
				return previousValue; // Changing a sub-config doesn't affect this value
			String value = config.getValue();
			if (value == null)
				return null;
			return format.parse(value);
		}
	}

	static <E> ObservableConfigFormat<E> ofEntity(EntityConfiguredValueType<E> entityType, ConfigEntityFieldParser formats) {
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
		public E parse(ObservableConfig config, E previousValue, ObservableConfig.ObservableConfigEvent change, Observable<?> until)
			throws ParseException {
			if ("true".equalsIgnoreCase(config.get("null")))
				return null;
			else if (previousValue == null) {
				QuickMap<String, Object> fieldValues = entityType.getFields().keySet().createMap();
				for (int i = 0; i < fieldValues.keySize(); i++) {
					ObservableConfig fieldConfig = config.getChild(theFieldChildNames.get(i));
					fieldValues.put(i, fieldFormats[i].parse(fieldConfig, null, null, until));
				}
				return entityType.create(//
					idx -> fieldValues.get(idx), //
					(idx, value) -> {
						fieldValues.put(idx, value);
						formatField(entityType.getFields().get(idx), value, config);
					});
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

		private void formatField(ConfiguredValueField<? super E, ?> field, Object fieldValue, ObservableConfig entityConfig) {
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

		private void parseUpdatedField(ObservableConfig entityConfig, int fieldIdx, E previousValue,
			ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException {
			ConfiguredValueField<? super E, ?> field = entityType.getFields().get(fieldIdx);
			Object oldValue = field.get(previousValue);
			ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(fieldIdx));
			if (change != null && fieldConfig != change.relativePath.get(0))
				return; // The update does not actually affect the field value
			Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(fieldConfig, oldValue,
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
			public C parse(ObservableConfig config, C previousValue, ObservableConfigEvent change, Observable<?> until)
				throws ParseException {
				class ConfigContentValueWrapper implements ObservableCollection<E> {
					private final ObservableCollection<E> theValues;
					private final SimpleObservable<Void> theContentUntil;

					ConfigContentValueWrapper(ObservableCollection<E> values, SimpleObservable<Void> contentUntil) {
						theValues = values;
						theContentUntil = contentUntil;
					}

					ObservableConfig getConfig() {
						return config;
					}

					@Override
					public CollectionElement<E> getElement(int index) {
						return theValues.getElement(index);
					}

					@Override
					public boolean isContentControlled() {
						return theValues.isContentControlled();
					}

					@Override
					public int getElementsBefore(ElementId id) {
						return theValues.getElementsBefore(id);
					}

					@Override
					public int getElementsAfter(ElementId id) {
						return theValues.getElementsAfter(id);
					}

					@Override
					public long getStamp(boolean structuralOnly) {
						return theValues.getStamp(structuralOnly);
					}

					@Override
					public CollectionElement<E> getElement(E value, boolean first) {
						return theValues.getElement(value, first);
					}

					@Override
					public CollectionElement<E> getElement(ElementId id) {
						return theValues.getElement(id);
					}

					@Override
					public CollectionElement<E> getTerminalElement(boolean first) {
						return theValues.getTerminalElement(first);
					}

					@Override
					public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
						return theValues.getAdjacentElement(elementId, next);
					}

					@Override
					public MutableCollectionElement<E> mutableElement(ElementId id) {
						return theValues.mutableElement(id);
					}

					@Override
					public CollectionElement<E> getElementBySource(ElementId sourceEl) {
						return theValues.getElementBySource(sourceEl);
					}

					@Override
					public String canAdd(E value, ElementId after, ElementId before) {
						return theValues.canAdd(value, after, before);
					}

					@Override
					public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
						throws UnsupportedOperationException, IllegalArgumentException {
						return theValues.addElement(value, after, before, first);
					}

					@Override
					public int size() {
						return theValues.size();
					}

					@Override
					public Transaction lock(boolean write, boolean structural, Object cause) {
						return theValues.lock(write, structural, cause);
					}

					@Override
					public Transaction tryLock(boolean write, boolean structural, Object cause) {
						return theValues.tryLock(write, structural, cause);
					}

					@Override
					public boolean isEmpty() {
						return theValues.isEmpty();
					}

					@Override
					public TypeToken<E> getType() {
						return theValues.getType();
					}

					@Override
					public boolean isLockSupported() {
						return theValues.isLockSupported();
					}

					@Override
					public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
						return theValues.onChange(observer);
					}

					@Override
					public void clear() {
						theValues.clear();
					}

					@Override
					public Equivalence<? super E> equivalence() {
						return theValues.equivalence();
					}

					@Override
					public void setValue(Collection<ElementId> elements, E value) {
						theValues.setValue(elements, value);
					}

					@Override
					public int hashCode() {
						return ObservableCollection.hashCode(this);
					}

					@Override
					public boolean equals(Object obj) {
						return ObservableCollection.equals(this, obj);
					}

					@Override
					public String toString() {
						return ObservableCollection.toString(this);
					}
				}
				if (previousValue == null) {
					SimpleObservable<Void> contentUntil = new SimpleObservable<>(null, false, null, b -> b.unsafe());
					return (C) new ConfigContentValueWrapper(config, config.observeValues(childName, elementType, elementFormat, //
						Observable.or(until, contentUntil)), contentUntil);
				} else {
					ConfigContentValueWrapper wrapper = (ConfigContentValueWrapper) previousValue;
					if (wrapper.getConfig() != config) {
						wrapper.theContentUntil.onNext(null);
						return (C) new ConfigContentValueWrapper(config, config.observeValues(childName, elementType, elementFormat, //
							Observable.or(until, wrapper.theContentUntil)), wrapper.theContentUntil);
					} else
						return previousValue;
				}
			}
		};
	}
}
