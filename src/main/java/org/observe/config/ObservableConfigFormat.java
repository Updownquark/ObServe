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
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
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

	void format(T value, T previousValue, ObservableConfig config, Consumer<T> acceptedValue, Observable<?> until)
		throws IllegalArgumentException;

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
		public void format(T value, T previousValue, ObservableConfig config, Consumer<T> acceptedValue, Observable<?> until) {
			acceptedValue.accept(value);
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
				return defaultValue.get();
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
		public void format(E value, E previousValue, ObservableConfig config, Consumer<E> acceptedValue, Observable<?> until) {
			if (value == null) {
				acceptedValue.accept(null);
				config.set("null", "true");
				for (int i = 0; i < entityType.getFields().keySize(); i++) {
					ObservableConfig cfg = config.getChild(theFieldChildNames.get(i));
					if (cfg != null)
						cfg.remove();
				}
			} else {
				if (previousValue == null) {
					QuickMap<String, Object> fields = entityType.getFields().keySet().createMap();
					try {
						previousValue = createInstance(config, fields, until);
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
				acceptedValue.accept(previousValue);
				config.set("null", null);
				for (int i = 0; i < entityType.getFields().keySize(); i++) {
					ConfiguredValueField<? super E, ?> field = entityType.getFields().get(i);
					Object fieldValue = field.get(value);
					formatField(field, fieldValue, config, fv -> {}, until);
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
				int fi = i;
				ObservableValue<? extends ObservableConfig> fieldConfig = config.observeDescendant(theFieldChildNames.get(i));
				if (fieldValues.get(i) == null)
					fieldValues.put(i,
						fieldFormats[i].parse(fieldConfig, () -> config.addChild(theFieldChildNames.get(fi)), null, null, until));
				else
					formatField(entityType.getFields().get(i), fieldValues.get(i), config, f -> {}, until);
			}
			return entityType.create(//
				idx -> fieldValues.get(idx), //
				(idx, value) -> {
					fieldValues.put(idx, value);
					formatField(entityType.getFields().get(idx), value, config, v -> fieldValues.put(idx, v), until);
				});
		}

		public void formatField(ConfiguredValueField<? super E, ?> field, Object fieldValue, ObservableConfig entityConfig,
			Consumer<Object> onFieldValue, Observable<?> until) {
			boolean[] added = new boolean[1];
			if (fieldValue != null) {
				ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(field.getIndex()), true, fc -> {
					added[0] = true;
					((ObservableConfigFormat<Object>) fieldFormats[field.getIndex()]).format(fieldValue, fieldValue, fc, onFieldValue,
						until);
				});
				if (!added[0])
					((ObservableConfigFormat<Object>) fieldFormats[field.getIndex()]).format(fieldValue, fieldValue, fieldConfig,
						onFieldValue, until);
			} else {
				ObservableConfig fieldConfig = entityConfig.getChild(theFieldChildNames.get(field.getIndex()));
				if (fieldConfig != null)
					fieldConfig.remove();
			}
		}

		public void parseUpdatedField(ObservableConfig entityConfig, int fieldIdx, E previousValue,
			ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException {
			ConfiguredValueField<? super E, Object> field = (ConfiguredValueField<? super E, Object>) entityType.getFields().get(fieldIdx);
			Object oldValue = field.get(previousValue);
			ObservableValue<? extends ObservableConfig> fieldConfig = entityConfig.observeDescendant(theFieldChildNames.get(fieldIdx));
			if (change != null) {
				if (change.relativePath.isEmpty() || fieldConfig != change.relativePath.get(0)) {
					field.set(previousValue, //
						((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(fieldConfig,
							() -> entityConfig.getChild(theFieldChildNames.get(fieldIdx), true, null), oldValue, change.asFromChild(),
							until));
					return; // The update does not actually affect the field value
				}
				change = change.asFromChild();
			}
			Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(fieldConfig,
				() -> entityConfig.addChild(configName), oldValue, change, until);
			if (oldValue != newValue)
				((ConfiguredValueField<E, Object>) field).set(previousValue, newValue);
		}
	}

	static <E, C extends Collection<? extends E>> ObservableConfigFormat<C> ofCollection(TypeToken<C> collectionType,
		ObservableConfigFormat<E> elementFormat, ConfigEntityFieldParser fieldParser, String parentName, String childName) {
		if (!TypeTokens.getRawType(collectionType).isAssignableFrom(ObservableCollection.class))
			throw new IllegalArgumentException("This class can only produce instances of " + ObservableCollection.class.getName()
				+ ", which is not compatible with type " + collectionType);
		TypeToken<E> elementType = (TypeToken<E>) collectionType.resolveType(Collection.class.getTypeParameters()[0]);
		return new ObservableConfigFormat<C>() {
			@Override
			public void format(C value, C previousValue, ObservableConfig config, Consumer<C> acceptedValue, Observable<?> until) {
				if (value == null) {
					acceptedValue.accept(null);
					config.remove();
					return;
				}
				if (previousValue == null)
					previousValue = (C) new ObservableConfigTransform.ObservableConfigValues<>(ObservableValue.of(config), null,
						elementType, elementFormat, childName, fieldParser, until, false);
				acceptedValue.accept(previousValue);
				try (Transaction t = config.lock(true, null)) {
					ArrayUtils.adjust((List<E>) previousValue, asList(value), new ArrayUtils.DifferenceListener<E, E>() {
						@Override
						public boolean identity(E o1, E o2) {
							return Objects.equals(o1, o2);
						}

						@Override
						public E added(E o, int mIdx, int retIdx) {
							return o;
						}

						@Override
						public E removed(E o, int oIdx, int incMod, int retIdx) {
							return null;
						}

						@Override
						public E set(E o1, int idx1, int incMod, E o2, int idx2, int retIdx) {
							return o2;
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
				if (previousValue == null) {
					return (C) new ObservableConfigTransform.ObservableConfigValues<>(config, create::get, elementType, elementFormat,
						childName, fieldParser, until, false);
				} else {
					((ObservableConfigTransform.ObservableConfigValues<E>) previousValue).onChange(change);
					return previousValue;
				}
			}
		};
	}

	static <E> ObservableConfigFormat<ObservableValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String parentName,
		String childName, ConfigEntityFieldParser fieldParser) {
		return new ObservableConfigFormat<ObservableValueSet<E>>() {
			@Override
			public void format(ObservableValueSet<E> value, ObservableValueSet<E> preValue, ObservableConfig config,
				Consumer<ObservableValueSet<E>> acceptedValue, Observable<?> until) {
				// Nah, we don't support calling set on a field like this, nothing to do
				acceptedValue.accept(preValue);
			}

			@Override
			public ObservableValueSet<E> parse(ObservableValue<? extends ObservableConfig> config,
				Supplier<? extends ObservableConfig> create, ObservableValueSet<E> previousValue, ObservableConfigEvent change,
				Observable<?> until) throws ParseException {
				if (previousValue == null) {
					return new ObservableConfigTransform.ObservableConfigEntityValues<>(config, create::get, elementFormat, childName,
						fieldParser, until, false);
				} else {
					((ObservableConfigTransform.ObservableConfigEntityValues<E>) previousValue).onChange(change);
					return previousValue;
				}
			}
		};
	}
}
