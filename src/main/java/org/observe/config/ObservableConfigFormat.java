package org.observe.config;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfig.ObservableConfigPathElement;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public interface ObservableConfigFormat<E> {
	public static ObservableConfigFormat<String> TEXT = ofQommonFormat(Format.TEXT, () -> null);
	public static ObservableConfigFormat<Double> DOUBLE = ofQommonFormat(Format.doubleFormat("0.############E0"), () -> 0.0);
	public static ObservableConfigFormat<Float> FLOAT = ofQommonFormat(Format.floatFormat("0.########E0"), () -> 0.0f);
	public static ObservableConfigFormat<Long> LONG = ofQommonFormat(Format.LONG, () -> 0L);
	public static ObservableConfigFormat<Integer> INT = ofQommonFormat(Format.INT, () -> 0);
	public static ObservableConfigFormat<Boolean> BOOLEAN = ofQommonFormat(Format.BOOLEAN, () -> false);
	public static ObservableConfigFormat<Duration> DURATION = ofQommonFormat(Format.DURATION, () -> Duration.ZERO);
	public static ObservableConfigFormat<Instant> DATE = ofQommonFormat(Format.date("ddMMyyyy HH:mm:ss.SSS"), () -> Instant.now());

	void format(E value, E previousValue, ObservableConfig config, Consumer<E> acceptedValue, Observable<?> until)
		throws IllegalArgumentException;

	E parse(ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, E previousValue,
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

	interface EntityConfigCreator<E> {
		EntityConfiguredValueType<E> getEntityType();

		Set<Integer> getRequiredFields();

		EntityConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

		<F> EntityConfigCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException;

		<F> EntityConfigCreator<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException;

		E create(ObservableConfig config, Observable<?> until);
	}

	interface EntityConfigFormat<E> extends ObservableConfigFormat<E> {
		EntityConfiguredValueType<E> getEntityType();
		<E2 extends E> EntityConfigCreator<E2> create(TypeToken<E2> subType);
	}

	class EntitySubFormat<E> {
		final ObservableConfig.ObservableConfigPathElement configFilter;
		final EntityConfigFormat<E> format;
		final TypeToken<E> type;
		final Class<E> rawType;
		final Predicate<? super E> valueFilter;

		EntitySubFormat(ObservableConfigPathElement configFilter, EntityConfigFormat<E> format, TypeToken<E> type,
			Predicate<? super E> valueFilter) {
			this.configFilter = configFilter;
			this.format = format;
			this.type = type;
			rawType = TypeTokens.getRawType(type);
			this.valueFilter = valueFilter;
		}

		void moldConfig(ObservableConfig config) {
			if (!config.getName().equals(configFilter.getName()))
				config.setName(configFilter.getName());
			for (Map.Entry<String, String> attr : configFilter.getAttributes().entrySet()) {
				if (attr.getValue() == null)
					config.getChild(attr.getKey(), true, null);
				else
					config.set(attr.getKey(), attr.getValue());
			}
		}

		boolean applies(Object value) {
			return rawType.isInstance(value) && (valueFilter == null || valueFilter.test((E) value));
		}
	}

	class PathElementBuilder {
		private final String theName;
		private final Map<String, String> theAttributes;

		PathElementBuilder(String name) {
			theName = name;
			theAttributes = new LinkedHashMap<>();
		}

		public PathElementBuilder withAttribute(String attributeName, String attributeValue) {
			theAttributes.put(attributeName, attributeValue);
			return this;
		}

		public ObservableConfigPathElement build() {
			boolean multi = theName.equals(ObservableConfig.ANY_NAME);
			return new ObservableConfigPathElement(multi ? "" : theName, theAttributes, multi, false);
		}
	}

	class EntityFormatBuilder<E> {
		public static class EntitySubFormatBuilder<T> {
			private final TypeToken<T> theType;
			private final ObservableConfigFormatSet theFormats;
			private Predicate<? super T> theValueFilter;
			private EntityConfigFormat<T> theFormat;

			EntitySubFormatBuilder(TypeToken<T> type, ObservableConfigFormatSet formats) {
				theType = type;
				theFormats = formats;
			}

			public EntitySubFormatBuilder<T> withValueFilter(Predicate<? super T> valueFilter) {
				theValueFilter = valueFilter;
				return this;
			}

			public EntitySubFormatBuilder<T> withFormat(EntityConfigFormat<T> format) {
				theFormat = format;
				return this;
			}

			public EntitySubFormat<T> build(String config) {
				if (theFormat == null)
					theFormat = theFormats.getEntityFormat(theType);
				return new EntitySubFormat<>(ObservableConfig.parsePathElement(config), theFormat, theType, theValueFilter);
			}

			public EntitySubFormat<T> build(String configName, Function<PathElementBuilder, ObservableConfigPathElement> path) {
				if (theFormat == null)
					theFormat = theFormats.getEntityFormat(theType);
				return new EntitySubFormat<>(path.apply(new PathElementBuilder(configName)), theFormat, theType, theValueFilter);
			}
		}

		private final EntityConfiguredValueType<E> theType;
		private final ObservableConfigFormatSet theFormats;
		private final List<EntitySubFormat<? extends E>> theSubFormats;

		EntityFormatBuilder(EntityConfiguredValueType<E> type, ObservableConfigFormatSet formats) {
			theType = type;
			theFormats = formats;
			theSubFormats = new LinkedList<>();
		}

		public <E2 extends E> EntityFormatBuilder<E> withSubType(TypeToken<E2> type,
			Function<EntitySubFormatBuilder<E2>, EntitySubFormat<E2>> subFormat) {
			if (!theType.getType().isAssignableFrom(type))
				throw new IllegalArgumentException(type + " is not a sub-type of " + theType);
			theSubFormats.add(subFormat.apply(new EntitySubFormatBuilder<>(type, theFormats)));
			return this;
		}

		public EntityConfigFormat<E> build() {
			return new EntityConfigFormatImpl<>(theType, theFormats, QommonsUtils.unmodifiableCopy(theSubFormats));
		}
	}

	static <E> EntityFormatBuilder<E> buildEntities(TypeToken<E> entityType, ObservableConfigFormatSet formats) {
		return buildEntities(formats.getEntityType(entityType), formats);
	}

	static <E> EntityFormatBuilder<E> buildEntities(EntityConfiguredValueType<E> entityType, ObservableConfigFormatSet formats) {
		return new EntityFormatBuilder<>(entityType, formats);
	}

	static <E> EntityConfigFormat<E> ofEntity(EntityConfiguredValueType<E> entityType, ObservableConfigFormatSet formats) {
		return new EntityConfigFormatImpl<>(entityType, formats, Collections.emptyList());
	}

	class EntityConfigFormatImpl<E> implements EntityConfigFormat<E> {
		public final EntityConfiguredValueType<E> entityType;
		public final ObservableConfigFormatSet formats;
		private final List<EntitySubFormat<? extends E>> theSubFormats;
		private final ObservableConfigFormat<?>[] fieldFormats;
		private QuickMap<String, String> theFieldChildNames;
		private QuickMap<String, String> theFieldsByChildName;

		public EntityConfigFormatImpl(EntityConfiguredValueType<E> entityType, ObservableConfigFormatSet formats,
			List<EntitySubFormat<? extends E>> subFormats) {
			this.entityType = entityType;
			this.formats = formats;
			theSubFormats = subFormats;
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
		public EntityConfiguredValueType<E> getEntityType() {
			return entityType;
		}

		private EntitySubFormat<? extends E> formatFor(E value) {
			if (value == null)
				return null;
			for (EntitySubFormat<? extends E> subFormat : theSubFormats)
				if (subFormat.applies(value))
					return subFormat;
			return null;
		}

		private EntitySubFormat<? extends E> formatFor(ObservableConfig config) {
			if (config == null)
				return null;
			for (EntitySubFormat<? extends E> subFormat : theSubFormats)
				if (subFormat.configFilter.matches(config))
					return subFormat;
			return null;
		}

		@Override
		public void format(E value, E previousValue, ObservableConfig config, Consumer<E> acceptedValue, Observable<?> until) {
			if (value == null) {
				acceptedValue.accept(null);
				config.setName("null");
				for (int i = 0; i < entityType.getFields().keySize(); i++) {
					ObservableConfig cfg = config.getChild(theFieldChildNames.get(i));
					if (cfg != null)
						cfg.remove();
				}
			} else {
				EntitySubFormat<? extends E> subFormat = formatFor(value);
				if (subFormat != null) {
					subFormat.moldConfig(config);
					((EntitySubFormat<E>) subFormat).format.format(value, subFormat.applies(previousValue) ? previousValue : null, config,
						acceptedValue, until);
					return;
				}
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
				EntitySubFormat<? extends E> subFormat = formatFor(c);
				if (subFormat != null) {
					return ((EntitySubFormat<E>) subFormat).format.parse(config, () -> {
						ObservableConfig newCfg = create.get();
						subFormat.moldConfig(newCfg);
						return newCfg;
					}, subFormat.applies(previousValue) ? previousValue : null, change, until);
				}
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

		@Override
		public <E2 extends E> EntityConfigCreator<E2> create(TypeToken<E2> subType) {
			if (subType != null && !subType.equals(entityType.getType()))
				throw new IllegalArgumentException("Unrecognized sub-type " + subType);
			return (EntityConfigCreator<E2>) new EntityConfigCreator<E>() {
				private final QuickMap<String, Object> theFieldValues = entityType.getFields().keySet().createMap();

				@Override
				public EntityConfiguredValueType<E> getEntityType() {
					return entityType;
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return entityType.getIdFields();
				}

				@Override
				public EntityConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException {
					return with((ConfiguredValueField<E, Object>) entityType.getFields().get(fieldName), value);
				}

				@Override
				public <F> EntityConfigCreator<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException {
					return with(entityType.getField(fieldGetter), value);
				}

				@Override
				public <F> EntityConfigCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException {
					if (value == null) {
						if (field.getFieldType().isPrimitive())
							throw new IllegalArgumentException("Null value is not allowed for primitive field " + field);
					} else {
						if (!TypeTokens.get().isInstance(field.getFieldType(), value))
							throw new IllegalArgumentException(
								"Value of type " + value.getClass().getName() + " is not allowed for field " + field);
					}
					theFieldValues.put(field.getIndex(), value);
					return this;
				}

				@Override
				public E create(ObservableConfig config, Observable<?> until) {
					try {
						return createInstance(config, theFieldValues, until);
					} catch (ParseException e) {
						throw new IllegalStateException("Could not create instance", e);
					}
				}
			};
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

		void formatField(ConfiguredValueField<? super E, ?> field, Object fieldValue, ObservableConfig entityConfig,
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

		private void parseUpdatedField(ObservableConfig entityConfig, int fieldIdx, E previousValue,
			ObservableConfig.ObservableConfigEvent change, Observable<?> until) throws ParseException {
			ConfiguredValueField<? super E, Object> field = (ConfiguredValueField<? super E, Object>) entityType.getFields().get(fieldIdx);
			Object oldValue = field.get(previousValue);
			ObservableValue<? extends ObservableConfig> fieldConfig = entityConfig.observeDescendant(theFieldChildNames.get(fieldIdx));
			if (change != null) {
				if (change.relativePath.isEmpty() || fieldConfig != change.relativePath.get(0)) {
					Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(fieldConfig,
						() -> entityConfig.getChild(theFieldChildNames.get(fieldIdx), true, null), oldValue, change.asFromChild(), until);
					if (newValue != oldValue)
						field.set(previousValue, newValue);
					return; // The update does not actually affect the field value
				}
				change = change.asFromChild();
			}
			Object newValue = ((ObservableConfigFormat<Object>) fieldFormats[fieldIdx]).parse(fieldConfig,
				() -> entityConfig.addChild(theFieldChildNames.get(fieldIdx)), oldValue, change, until);
			if (oldValue != newValue)
				((ConfiguredValueField<E, Object>) field).set(previousValue, newValue);
		}
	}

	static <E, C extends Collection<? extends E>> ObservableConfigFormat<C> ofCollection(TypeToken<C> collectionType,
		ObservableConfigFormat<E> elementFormat, ObservableConfigFormatSet fieldParser, String parentName, String childName) {
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
				if (previousValue == value)
					return;
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

	static <E> ObservableConfigFormat<ObservableValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String childName,
		ObservableConfigFormatSet fieldParser) {
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

	static <T> HeterogeneousFormat.Builder<T> heterogeneous(TypeToken<T> type) {
		return HeterogeneousFormat.build(type);
	}

	class HeterogeneousFormat<T> implements ObservableConfigFormat<T> {
		public static <T> Builder<T> build(TypeToken<T> type) {
			return new Builder<>(type);
		}

		public static class Builder<T> {
			private final TypeToken<T> theType;
			private final List<SubFormat<? extends T>> theSubFormats;

			public Builder(TypeToken<T> type) {
				theType = type;
				theSubFormats = new LinkedList<>();
			}

			public <T2 extends T> Builder<T> with(TypeToken<T2> type, Function<SubFormatBuilder<T2>, SubFormat<T2>> subFormat) {
				if (!theType.isAssignableFrom(type))
					throw new IllegalArgumentException(type + " is not a sub-type of " + theType);
				theSubFormats.add(subFormat.apply(new SubFormatBuilder<>(type)));
				return this;
			}

			public HeterogeneousFormat<T> build() {
				return new HeterogeneousFormat<>(QommonsUtils.unmodifiableCopy(theSubFormats));
			}
		}

		public static class SubFormatBuilder<T> {
			private final TypeToken<T> theType;
			private Predicate<? super T> theValueFilter;

			SubFormatBuilder(TypeToken<T> type) {
				theType = type;
			}

			public SubFormatBuilder<T> withValueFilter(Predicate<? super T> valueFilter) {
				theValueFilter = valueFilter;
				return this;
			}

			public SubFormat<T> build(String config, ObservableConfigFormat<T> format) {
				return new SubFormat<>(ObservableConfig.parsePathElement(config), format, theType, theValueFilter);
			}

			public SubFormat<T> build(String configName, Function<PathElementBuilder, ObservableConfigPathElement> path,
				ObservableConfigFormat<T> format) {
				return new SubFormat<>(path.apply(new PathElementBuilder(configName)), format, theType, theValueFilter);
			}
		}

		public static class SubFormat<T> {
			final ObservableConfig.ObservableConfigPathElement configFilter;
			final ObservableConfigFormat<T> format;
			final TypeToken<T> type;
			final Class<T> rawType;
			final Predicate<? super T> valueFilter;

			SubFormat(ObservableConfigPathElement configFilter, ObservableConfigFormat<T> format, TypeToken<T> type,
				Predicate<? super T> valueFilter) {
				this.configFilter = configFilter;
				this.format = format;
				this.type = type;
				rawType = TypeTokens.getRawType(type);
				this.valueFilter = valueFilter;
			}

			void moldConfig(ObservableConfig config) {
				if (!config.getName().equals(configFilter.getName()))
					config.setName(configFilter.getName());
				for (Map.Entry<String, String> attr : configFilter.getAttributes().entrySet()) {
					if (attr.getValue() == null)
						config.getChild(attr.getKey(), true, null);
					else
						config.set(attr.getKey(), attr.getValue());
				}
			}

			boolean applies(Object value) {
				return rawType.isInstance(value) && (valueFilter == null || valueFilter.test((T) value));
			}
		}

		private final List<SubFormat<? extends T>> theSubFormats;

		public HeterogeneousFormat(List<SubFormat<? extends T>> subFormats) {
			theSubFormats = subFormats;
		}

		private SubFormat<? extends T> formatFor(T value) {
			if (value == null)
				return null;
			for (SubFormat<? extends T> subFormat : theSubFormats)
				if (subFormat.applies(value))
					return subFormat;
			return null;
		}

		private SubFormat<? extends T> formatFor(ObservableConfig config) {
			if (config == null)
				return null;
			for (SubFormat<? extends T> subFormat : theSubFormats)
				if (subFormat.configFilter.matches(config))
					return subFormat;
			return null;
		}

		@Override
		public void format(T value, T previousValue, ObservableConfig config, Consumer<T> acceptedValue, Observable<?> until)
			throws IllegalArgumentException {
			SubFormat<? extends T> format = formatFor(value);
			if (value != null && format == null)
				throw new IllegalArgumentException("No sub-format found for value " + value.getClass().getName() + " " + value);
			if (value == null) {
				config.set("null", "true");
				config.getAllContent().getValues().clear();
				acceptedValue.accept(null);
				return;
			} else {
				config.set("null", null);
				format.moldConfig(config);
				((SubFormat<T>) format).format.format(value, format.applies(previousValue) ? previousValue : null, config, acceptedValue,
					until);
			}
		}

		@Override
		public T parse(ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, T previousValue,
			ObservableConfigEvent change, Observable<?> until) throws ParseException {
			ObservableConfig c = config.get();
			if (c == null || "true".equals(c.get("null")))
				return null;
			SubFormat<? extends T> format = formatFor(c);
			if (format == null)
				throw new ParseException("No sub-format found matching " + c, 0);
			return ((SubFormat<T>) format).format.parse(config, create, format.applies(previousValue) ? previousValue : null, change,
				until);
		}
	}
}
