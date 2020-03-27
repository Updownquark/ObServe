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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.config.EntityConfiguredValueType.EntityConfiguredValueField;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfig.ObservableConfigPathElement;
import org.observe.config.ObservableConfigFormat.ReferenceFormat.FormattedField;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transactable;
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

	E parse(ObservableConfigParseContext<E> ctx) throws ParseException;

	E copy(E source, E copy, ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create,
		Observable<?> until);

	interface ObservableConfigParseContext<E> {
		ObservableConfig getRoot();

		ObservableValue<? extends ObservableConfig> getConfig();

		ObservableConfig getConfig(boolean createIfAbsent);

		ObservableConfigEvent getChange();

		Observable<?> getUntil();

		E getPreviousValue();

		Observable<?> findReferences();

		void linkedReference(E value);

		<V> ObservableConfigParseContext<V> map(ObservableValue<? extends ObservableConfig> config,
			Supplier<? extends ObservableConfig> create, ObservableConfigEvent change, V previousValue, Consumer<V> delayedAccept);

		default <V> ObservableConfigParseContext<V> forChild(BiFunction<ObservableConfig, Boolean, ObservableConfig> child,
			ObservableConfigEvent childChange, V previousValue, Consumer<V> delayedAccept) {
			ObservableValue<? extends ObservableConfig> childConfig = getConfig().map(c -> child.apply(c, false));
			Supplier<? extends ObservableConfig> childCreate = () -> child.apply(getConfig(true), true);
			return map(childConfig, childCreate, childChange, previousValue, delayedAccept);
		}

		default <V> ObservableConfigParseContext<V> forChild(String childName, V previousValue, Consumer<V> delayedAccept) {
			ObservableConfigEvent childChange;
			if (getChange() == null || getChange().relativePath.isEmpty() || !getChange().relativePath.get(0).getName().equals(childName))
				childChange = null;
			else
				childChange = getChange().asFromChild();
			return forChild((config, create) -> config.getChild(childName, create, null), childChange, previousValue, delayedAccept);
		}

		ObservableConfigParseContext<E> withRefFinding(Observable<?> findRefs);
	}

	static <E> ObservableConfigParseContext<E> ctxFor(ObservableConfig root, ObservableValue<? extends ObservableConfig> config,
		Supplier<? extends ObservableConfig> create, ObservableConfigEvent change, Observable<?> until, E previousValue,
		Observable<?> findReferences, Consumer<E> delayedAccept) {
		return new DefaultOCParseContext<>(root, config, create, change, until, previousValue, findReferences, delayedAccept);
	}

	static class DefaultOCParseContext<E> implements ObservableConfigParseContext<E> {
		private final ObservableConfig theRoot;
		private final ObservableValue<? extends ObservableConfig> theConfig;
		private final Supplier<? extends ObservableConfig> theCreate;
		private final ObservableConfigEvent theChange;
		private final Observable<?> theUntil;
		private final E thePreviousValue;
		private final Observable<?> findReferences;
		private final Consumer<E> delayedAccept;

		public DefaultOCParseContext(ObservableConfig root, ObservableValue<? extends ObservableConfig> config,
			Supplier<? extends ObservableConfig> create, ObservableConfigEvent change, Observable<?> until, E previousValue,
			Observable<?> findReferences, Consumer<E> delayedAccept) {
			theRoot = root;
			theConfig = config;
			theCreate = create;
			theChange = change;
			theUntil = until;
			thePreviousValue = previousValue;
			this.findReferences = findReferences;
			this.delayedAccept = delayedAccept;
		}

		@Override
		public ObservableConfig getRoot() {
			return theRoot;
		}

		@Override
		public ObservableValue<? extends ObservableConfig> getConfig() {
			return theConfig;
		}

		@Override
		public ObservableConfig getConfig(boolean createIfAbsent) {
			ObservableConfig c = theConfig.get();
			if (c == null && createIfAbsent)
				c = theCreate.get();
			return c;
		}

		@Override
		public ObservableConfigEvent getChange() {
			return theChange;
		}

		@Override
		public Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public E getPreviousValue() {
			return thePreviousValue;
		}

		@Override
		public Observable<?> findReferences() {
			return findReferences;
		}

		@Override
		public void linkedReference(E value) {
			if (delayedAccept != null)
				delayedAccept.accept(value);
		}

		@Override
		public <V> ObservableConfigParseContext<V> map(ObservableValue<? extends ObservableConfig> config,
			Supplier<? extends ObservableConfig> create, ObservableConfigEvent change, V previousValue, Consumer<V> delayedAccept) {
			return new DefaultOCParseContext<>(theRoot, config, create, change, theUntil, previousValue, findReferences, delayedAccept);
		}

		@Override
		public ObservableConfigParseContext<E> withRefFinding(Observable<?> findRefs) {
			return new DefaultOCParseContext<>(theRoot, theConfig, theCreate, theChange, theUntil, thePreviousValue, findRefs,
				delayedAccept);
		}
	}

	static <T> ObservableConfigFormat<T> ofQommonFormat(Format<T> format, Supplier<? extends T> defaultValue) {
		return new SimpleConfigFormat<>(format, defaultValue);
	}

	static <E extends Enum<?>> ObservableConfigFormat<E> enumFormat(Class<E> type, Supplier<? extends E> defaultValue) {
		return new SimpleConfigFormat<>(Format.enumFormat(type), defaultValue);
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
		public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
			ObservableConfig config = ctx.getConfig(false);
			if (config == null)
				return defaultValue.get();
			String value = config == null ? null : config.getValue();
			if (value == null)
				return defaultValue.get();
			if (ctx.getChange() != null && ctx.getChange().relativePath.size() > 1)
				return ctx.getPreviousValue(); // Changing a sub-config doesn't affect this value
			return format.parse(value);
		}

		@Override
		public T copy(T source, T copy, ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create,
			Observable<?> until) {
			return source;
		}

		@Override
		public int hashCode() {
			return format.hashCode() * 7 + defaultValue.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof SimpleConfigFormat && format.equals(((SimpleConfigFormat<?>) obj).format)
				&& defaultValue.equals(((SimpleConfigFormat<?>) obj).defaultValue);
		}

		@Override
		public String toString() {
			return format.toString();
		}
	}

	class ReferenceFormat<T> implements ObservableConfigFormat<T> {
		static class FormattedField<T, F> {
			public final Function<? super T, F> field;
			public final ObservableConfigFormat<F> format;

			public FormattedField(Function<? super T, F> field, ObservableConfigFormat<F> format) {
				this.field = field;
				this.format = format;
			}
		}

		private final QuickMap<String, FormattedField<? super T, ?>> theFields;
		private final Function<QuickMap<String, Object>, ? extends T> theRetriever;

		ReferenceFormat(QuickMap<String, FormattedField<? super T, ?>> fields, Function<QuickMap<String, Object>, ? extends T> retriever) {
			theFields = fields;
			theRetriever = retriever;
		}

		@Override
		public void format(T value, T previousValue, ObservableConfig config, Consumer<T> acceptedValue, Observable<?> until)
			throws IllegalArgumentException {
			if (value == null) {
				for (ObservableConfig child : config._getContent()) {
					if (child.getName().equals("null")) {
						child.setValue("true");
						child.getAllContent().getValues().clear();
					} else
						child.remove();
				}
				acceptedValue.accept(value);
				return;
			}
			ObservableConfig nullConfig = config.getChild("null");
			if (nullConfig != null)
				nullConfig.remove();
			boolean[] added = new boolean[1];
			for (int f = 0; f < theFields.keySize(); f++) {
				FormattedField<? super T, Object> field = (FormattedField<? super T, Object>) theFields.get(f);
				Object fieldValue = field.field.apply(value);
				Object preFieldValue = previousValue == null ? null : field.field.apply(previousValue);
				ObservableConfig fieldConfig = config.getChild(theFields.keySet().get(f), true, //
					child -> {
						added[0] = true;
						formatField(child, fieldValue, preFieldValue, field.format, until);
					});
				if (!added[0])
					formatField(fieldConfig, fieldValue, preFieldValue, field.format, until);
			}
			acceptedValue.accept(value);
		}

		private <F> void formatField(ObservableConfig child, F fieldValue, F preFieldValue, ObservableConfigFormat<F> format,
			Observable<?> until) {
			format.format(fieldValue, preFieldValue, child, f -> {}, until);
		}

		@Override
		public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
			ObservableConfig c = ctx.getConfig(false);
			if (c == null || "true".equals(c.get("null")))
				return null;
			class DelayedExecution {
				boolean delayed;
				T value;
				ParseException thrown;
			}
			DelayedExecution exec = new DelayedExecution();
			ctx.findReferences().act(__ -> {
				QuickMap<String, Object> fieldValues = theFields.keySet().createMap();
				T previousValue = ctx.getPreviousValue();
				QuickMap<String, Object> preFieldValues = previousValue == null ? null : theFields.keySet().createMap();
				boolean usePreValue = previousValue != null;
				for (int f = 0; f < theFields.keySize(); f++) {
					Object preValue;
					if (previousValue != null)
						preFieldValues.put(f, preValue = theFields.get(f).field.apply(previousValue));
					else
						preValue = null;
					Object fieldValue;
					try {
						fieldValue = ((ObservableConfigFormat<Object>) theFields.get(f).format)
							.parse(ctx.forChild(theFields.keySet().get(f), preValue, null).withRefFinding(Observable.constant(null)));
					} catch (ParseException e) {
						if (exec.delayed)
							e.printStackTrace();
						exec.thrown = e;
						return;
					}
					fieldValues.put(f, fieldValue);
					if (usePreValue && !Objects.equals(preValue, fieldValue))
						usePreValue = false;
				}
				if (usePreValue)
					exec.value = previousValue;
				else
					exec.value = theRetriever.apply(fieldValues.unmodifiable());
				if (exec.delayed)
					ctx.linkedReference(exec.value);
			});
			if (exec.thrown != null)
				throw exec.thrown;
			exec.delayed = true;
			return exec.value;
		}

		@Override
		public T copy(T source, T copy, ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create,
			Observable<?> until) {
			return source;
		}
	}

	public static class ReferenceFormatBuilder<T> {
		private final Function<QuickMap<String, Object>, ? extends T> theRetriever;
		private final Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> theMultiRetriever;
		private final Supplier<? extends T> theRetreiverDefault;
		private final Map<String, FormattedField<? super T, ?>> theFields;

		ReferenceFormatBuilder(Function<QuickMap<String, Object>, ? extends T> retriever) {
			theRetriever = retriever;
			theMultiRetriever = null;
			theRetreiverDefault = null;
			theFields = new LinkedHashMap<>();
		}

		public ReferenceFormatBuilder(Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> multiRetriever,
			Supplier<? extends T> defaultValue) {
			theRetriever = null;
			theMultiRetriever = multiRetriever;
			theRetreiverDefault = defaultValue == null ? () -> null : defaultValue;
			theFields = new LinkedHashMap<>();
		}

		public <F> ReferenceFormatBuilder<T> withField(String childName, Function<? super T, F> field, ObservableConfigFormat<F> format) {
			if (theFields.put(childName, new FormattedField<T, F>(field, format)) != null)
				throw new IllegalArgumentException("Multiple fields named " + childName + " are not supported");
			return this;
		}

		public ReferenceFormat<T> build() {
			QuickMap<String, FormattedField<? super T, ?>> fields = QuickMap.of(theFields, StringUtils.DISTINCT_NUMBER_TOLERANT)
				.unmodifiable();
			Function<QuickMap<String, Object>, ? extends T> retriever;
			if (theRetriever != null)
				retriever = theRetriever;
			else {
				retriever = fieldValues -> {
					Iterable<? extends T> retrieved = theMultiRetriever.apply(fieldValues);
					for (T value : retrieved) {
						boolean matches = true;
						for (int f = 0; matches && f < fields.keySize(); f++) {
							Object valueF = fields.get(f).field.apply(value);
							matches = Objects.equals(fieldValues.get(f), valueF);
						}
						if (matches)
							return value;
					}
					return theRetreiverDefault.get();
				};
			}
			return new ReferenceFormat<>(fields, retriever);
		}
	}

	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Function<QuickMap<String, Object>, ? extends T> retriever) {
		return new ReferenceFormatBuilder<>(retriever);
	}

	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> retriever,
		Supplier<? extends T> defaultValue) {
		return new ReferenceFormatBuilder<>(retriever, defaultValue);
	}

	interface ConfigCreator<E> {
		Set<Integer> getRequiredFields();

		ConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

		ConfigCreator<E> with(int fieldIndex, Object value) throws IllegalArgumentException;

		E create(ObservableConfig config, Observable<?> until);
	}

	interface EntityConfigCreator<E> extends ConfigCreator<E> {
		EntityConfiguredValueType<E> getEntityType();

		@Override
		Set<Integer> getRequiredFields();

		@Override
		EntityConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

		@Override
		EntityConfigCreator<E> with(int fieldIndex, Object value) throws IllegalArgumentException;

		<F> EntityConfigCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException;

		<F> EntityConfigCreator<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException;
	}

	interface EntityConfigFormat<E> extends ObservableConfigFormat<E> {
		EntityConfiguredValueType<E> getEntityType();

		<E2 extends E> EntityConfigCreator<E2> create(TypeToken<E2> subType);
	}

	// TODO
	// public static class SimpleComponentFormatBuilder<E>{
	// private final TypeToken<E> theType;
	// private final Map<String, ComponentField<E, ?>> theFields;
	//
	// SimpleComponentFormatBuilder(TypeToken<E> type) {
	// theType = type;
	// theFields=new LinkedHashMap<>();
	// }
	//
	// public <F> SimpleComponentFormatBuilder<E> withField(String fieldName, Function<? super E, F> getter, Consumer<
	// }

	public static class EntityFormatBuilder<E> {
		private final EntityConfiguredValueType<E> theEntityType;
		private final ObservableConfigFormatSet theFormatSet;
		private final List<EntitySubFormat<? extends E>> theSubFormats;
		private final QuickMap<String, ObservableConfigFormat<?>> theFieldFormats;
		private final QuickMap<String, String> theFieldChildNames;

		EntityFormatBuilder(EntityConfiguredValueType<E> entityType, ObservableConfigFormatSet formatSet) {
			theEntityType = entityType;
			theFormatSet = formatSet;
			theSubFormats = new LinkedList<>();
			theFieldFormats = entityType.getFields().keySet().createMap();
			theFieldChildNames = entityType.getFields().keySet().createMap();
		}

		public EntityFormatBuilder<E> withSubFormat(EntitySubFormat<? extends E> subFormat) {
			if (!theSubFormats.contains(subFormat))
				theSubFormats.add(subFormat);
			return this;
		}

		public <E2 extends E> EntityFormatBuilder<E> withSubType(TypeToken<E2> type,
			Function<EntitySubFormatBuilder<E2>, EntitySubFormat<E2>> subFormat) {
			if (!theEntityType.getType().isAssignableFrom(type))
				throw new IllegalArgumentException(type + " is not a sub-type of " + theEntityType);
			theSubFormats.add(subFormat.apply(new EntitySubFormatBuilder<>(type, theFormatSet)));
			return this;
		}

		public EntityFormatBuilder<E> withFieldFormat(int fieldIndex, ObservableConfigFormat<?> format) {
			theFieldFormats.put(fieldIndex, format);
			return this;
		}

		public EntityFormatBuilder<E> withFieldFormat(String fieldName, ObservableConfigFormat<?> format) {
			return withFieldFormat(theFieldFormats.keyIndex(fieldName), format);
		}

		public <F> EntityFormatBuilder<E> withFieldFormat(Function<? super E, F> field, ObservableConfigFormat<F> format) {
			return withFieldFormat(theEntityType.getField(field).getIndex(), format);
		}

		public EntityFormatBuilder<E> withFieldChildName(int fieldIndex, String childName) {
			theFieldChildNames.put(fieldIndex, childName);
			return this;
		}

		public EntityFormatBuilder<E> withFieldChildName(String fieldName, String childName) {
			return withFieldChildName(theFieldChildNames.keyIndex(fieldName), childName);
		}

		public EntityFormatBuilder<E> withFieldChildName(Function<? super E, ?> field, String childName) {
			return withFieldChildName(theEntityType.getField(field).getIndex(), childName);
		}

		public EntityConfigFormat<E> build() {
			QuickMap<String, ObservableConfigFormat<?>> formats = theFieldFormats.copy();
			for (int i = 0; i < formats.keySize(); i++) {
				if (formats.get(i) == null)
					formats.put(i, theFormatSet.getConfigFormat(theEntityType.getFields().get(i)));
			}
			QuickMap<String, String> childNames = theFieldChildNames.copy();
			for (int i = 0; i < childNames.keySize(); i++) {
				if (childNames.get(i) == null)
					childNames.put(i, StringUtils.parseByCase(theEntityType.getFields().keySet().get(i), true).toKebabCase());
			}
			return new EntityConfigFormatImpl<>(theEntityType, QommonsUtils.unmodifiableCopy(theSubFormats), formats.unmodifiable(),
				childNames.unmodifiable());
		}
	}

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
			if (!configFilter.isMulti() && !config.getName().equals(configFilter.getName()))
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

	static <E> EntityFormatBuilder<E> buildEntities(TypeToken<E> entityType, ObservableConfigFormatSet formats) {
		return buildEntities(formats.getEntityType(entityType), formats);
	}

	static <E> EntityFormatBuilder<E> buildEntities(EntityConfiguredValueType<E> entityType, ObservableConfigFormatSet formats) {
		return new EntityFormatBuilder<>(entityType, formats);
	}

	abstract class AbstractComponentFormat<E> implements ObservableConfigFormat<E> {
		static class ComponentField<E, F> {
			final String name;
			final TypeToken<F> type;
			final int index;
			final Function<? super E, ? extends F> getter;
			final BiConsumer<? super E, ? super F> setter;
			final ObservableConfigFormat<F> format;
			final String childName;

			ComponentField(String name, TypeToken<F> type, int index, Function<? super E, ? extends F> getter,
				BiConsumer<? super E, ? super F> setter, ObservableConfigFormat<F> format, String childName) {
				this.name = name;
				this.type = type;
				this.index = index;
				this.getter = getter;
				this.setter = setter;
				this.format = format;
				this.childName = childName;
			}
		}
		private final QuickMap<String, ComponentField<E, ?>> theFields;
		private final List<EntitySubFormat<? extends E>> theSubFormats;
		private QuickMap<String, ComponentField<E, ?>> theFieldsByChildName;

		private AbstractComponentFormat(QuickMap<String, ComponentField<E, ?>> fields, List<EntitySubFormat<? extends E>> subFormats) {
			theFields = fields;
			theSubFormats = subFormats;
			Map<String, ComponentField<E, ?>> fcnReverse = new LinkedHashMap<>();
			for (int i = 0; i < theFields.keySize(); i++)
				fcnReverse.put(theFields.get(i).childName, theFields.get(i));
			theFieldsByChildName = QuickMap.of(fcnReverse, String::compareTo);
		}

		protected abstract TypeToken<E> getType();

		public QuickMap<String, ComponentField<E, ?>> getFields() {
			return theFields;
		}

		protected Set<Integer> getRequiredFields() {
			return Collections.emptySet();
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
				for (int i = 0; i < theFields.keySize(); i++) {
					ObservableConfig cfg = config.getChild(theFields.get(i).childName);
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
				acceptedValue.accept(value);
				config.set("null", null);
				for (int i = 0; i < theFields.keySize(); i++) {
					ComponentField<E, ?> field = theFields.get(i);
					Object fieldValue = field.getter.apply(value);
					formatField((ComponentField<E, Object>) field, fieldValue, config, fv -> {}, until);
				}
			}
		}

		@Override
		public E parse(ObservableConfigParseContext<E> ctx) throws ParseException {
			ObservableConfig c = ctx.getConfig(false);
			if (c != null && "true".equalsIgnoreCase(c.get("null")))
				return null;
			else if (ctx.getPreviousValue() == null) {
				if (c == null)
					c = ctx.getConfig(true);
				return createInstance(c, theFields.keySet().createMap(), ctx.getUntil(), ctx.findReferences());
			} else {
				EntitySubFormat<? extends E> subFormat = formatFor(c);
				if (subFormat != null) {
					return ((EntitySubFormat<E>) subFormat).format.parse(ctx.forChild((config, create) -> {
						if (create)
							subFormat.moldConfig(config);
						return config;
					}, ctx.getChange(), subFormat.applies(ctx.getPreviousValue()) ? ctx.getPreviousValue() : null, ctx::linkedReference));
				}
				if (ctx.getChange() == null) {
					for (int i = 0; i < theFields.keySize(); i++)
						parseUpdatedField(ctx, c, i, null);
				} else if (ctx.getChange().relativePath.isEmpty()) {
					// Change to the value doesn't change any fields
				} else {
					ObservableConfigEvent change = ctx.getChange();
					ObservableConfig child = change.relativePath.get(0);
					int fieldIdx = theFieldsByChildName.keyIndexTolerant(child.getName());
					if (change.relativePath.size() == 1 && !change.oldName.equals(child.getName())) {
						if (fieldIdx >= 0) {
							ObservableConfigEvent childChange = change.asFromChild();
							try (Transaction ct = Causable.use(childChange)) {
								parseUpdatedField(ctx, c, fieldIdx, childChange);
							}
						}
						fieldIdx = theFieldsByChildName.keyIndexTolerant(change.oldName);
						if (fieldIdx >= 0)
							parseUpdatedField(ctx, c, fieldIdx, null);
					} else if (fieldIdx >= 0)
						parseUpdatedField(ctx, c, fieldIdx, change);
				}
				return ctx.getPreviousValue();
			}
		}

		@Override
		public E copy(E source, E copy, ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create,
			Observable<?> until) {
			for (EntitySubFormat<? extends E> sub : theSubFormats) {
				if (sub.rawType.isInstance(source) && ((Predicate<E>) sub.valueFilter).test(source)//
					&& (copy == null || (sub.rawType.isInstance(copy) && ((Predicate<E>) sub.valueFilter).test(copy)))) {
					return ((ObservableConfigFormat<E>) sub.format).copy(source, copy, config, create, until);
				}
			}
			if (copy == null)
				throw new IllegalArgumentException("Cannot create copies in this format");
			for (int i = 0; i < theFields.keySize(); i++)
				copyField(source, copy, i, config, create, until);
			return copy;
		}

		private <F> void copyField(E source, E copy, int fieldIndex, ObservableValue<? extends ObservableConfig> config,
			Supplier<? extends ObservableConfig> create, Observable<?> until) {
			ComponentField<E, F> field = (ComponentField<E, F>) theFields.get(fieldIndex);
			F sourceField = field.getter.apply(source);
			F copyField = field.getter.apply(copy);
			F newCopy = field.format.copy(sourceField, copyField, asChild(config, field.childName),
				asChild(config, create, field.childName), until);
			if (newCopy != copyField)
				field.setter.accept(copy, newCopy);
		}

		public <E2 extends E> ConfigCreator<E2> create(TypeToken<E2> subType) {
			if (subType != null && !subType.equals(getType())) {
				for (EntitySubFormat<? extends E> subFormat : theSubFormats) {
					if (subFormat.type.isAssignableFrom(subType))
						return ((EntitySubFormat<? super E2>) subFormat).format.create(subType);
				}
			}
			return (ConfigCreator<E2>) new ConfigCreator<E>() {
				private final QuickMap<String, Object> theFieldValues = theFields.keySet().createMap();

				@Override
				public Set<Integer> getRequiredFields() {
					return AbstractComponentFormat.this.getRequiredFields();
				}

				@Override
				public ConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException {
					return with(theFields.get(fieldName), value);
				}

				@Override
				public ConfigCreator<E> with(int fieldIndex, Object value) throws IllegalArgumentException {
					return with(theFields.get(fieldIndex), value);
				}

				private ConfigCreator<E> with(ComponentField<E, ?> field, Object value) {
					if (value == null) {
						if (field.type.isPrimitive())
							throw new IllegalArgumentException("Null value is not allowed for primitive field " + field);
					} else {
						if (!TypeTokens.get().isInstance(field.type, value))
							throw new IllegalArgumentException(
								"Value of type " + value.getClass().getName() + " is not allowed for field " + field);
					}
					theFieldValues.put(field.index, value);
					return this;
				}

				@Override
				public E create(ObservableConfig config, Observable<?> until) {
					try {
						E inst = createInstance(config, theFieldValues, until, Observable.constant(null));
						return inst;
					} catch (ParseException e) {
						throw new IllegalStateException("Could not create instance", e);
					}
				}
			};
		}

		E createInstance(ObservableConfig config, QuickMap<String, Object> fieldValues, Observable<?> until, Observable<?> findReferences)
			throws ParseException {
			for (EntitySubFormat<? extends E> subFormat : theSubFormats) {
				if (subFormat.configFilter.matches(config))
					return subFormat.format
						.parse(ctxFor(config, ObservableValue.of(config), null, null, until, null, findReferences, null));
			}
			for (int i = 0; i < fieldValues.keySize(); i++) {
				int fi = i;
				ObservableValue<? extends ObservableConfig> fieldConfig = config.observeDescendant(theFields.get(i).childName);
				if (fieldValues.get(i) == null)
					fieldValues.put(i,
						theFields.get(i).format.parse(ctxFor(config, fieldConfig, () -> config.addChild(theFields.get(fi).childName), null,
							until, null, findReferences, fv -> fieldValues.put(fi, fv))));
				else
					formatField((ComponentField<E, Object>) theFields.get(i), fieldValues.get(i), config, f -> {}, until);
			}
			return create(fieldValues, config, until);
		}

		protected abstract E create(QuickMap<String, Object> fieldValues, ObservableConfig config, Observable<?> until);

		protected <F> void formatField(ComponentField<E, F> field, F fieldValue, ObservableConfig entityConfig, Consumer<F> onFieldValue,
			Observable<?> until) {
			boolean[] added = new boolean[1];
			if (fieldValue != null) {
				ObservableConfig fieldConfig = entityConfig.getChild(field.childName, true, fc -> {
					added[0] = true;
					field.format.format(fieldValue, fieldValue, fc, onFieldValue, until);
				});
				if (!added[0])
					field.format.format(fieldValue, fieldValue, fieldConfig, onFieldValue, until);
			} else {
				ObservableConfig fieldConfig = entityConfig.getChild(field.childName);
				if (fieldConfig != null)
					fieldConfig.remove();
			}
		}

		private <F> void parseUpdatedField(ObservableConfigParseContext<E> ctx, ObservableConfig entityConfig, int fieldIdx,
			ObservableConfigEvent change) throws ParseException {
			ComponentField<? super E, F> field = (ComponentField<? super E, F>) theFields.get(fieldIdx);
			F oldValue = field.getter.apply(ctx.getPreviousValue());
			ObservableValue<? extends ObservableConfig> fieldConfig = entityConfig.observeDescendant(field.childName);
			if (change != null) {
				if (change.relativePath.isEmpty() || fieldConfig != change.relativePath.get(0)) {
					ObservableConfigEvent childChange = change.asFromChild();
					F newValue;
					try (Transaction ct = Causable.use(childChange)) {
						newValue = field.format
							.parse(ctxFor(entityConfig, fieldConfig, () -> entityConfig.getChild(field.childName, true, null), childChange,
								ctx.getUntil(), oldValue, ctx.findReferences(), fv -> field.setter.accept(ctx.getPreviousValue(), fv)));
					}
					if (newValue != oldValue)
						field.setter.accept(ctx.getPreviousValue(), newValue);
					return; // The update does not actually affect the field value
				}
				change = change.asFromChild();
			}
			F newValue = field.format.parse(ctxFor(entityConfig, fieldConfig, () -> entityConfig.addChild(field.childName), change,
				ctx.getUntil(), oldValue, ctx.findReferences(), fv -> field.setter.accept(ctx.getPreviousValue(), fv)));
			if (oldValue != newValue)
				field.setter.accept(ctx.getPreviousValue(), newValue);
		}
	}

	class SimpleComponentFormat<E> extends AbstractComponentFormat<E> {
		private final TypeToken<E> theType;
		private final Function<QuickMap<String, Object>, E> theCreator;

		private SimpleComponentFormat(TypeToken<E> type, QuickMap<String, ComponentField<E, ?>> fields,
			List<EntitySubFormat<? extends E>> subFormats, Function<QuickMap<String, Object>, E> creator) {
			super(fields, subFormats);
			theType = type;
			theCreator = creator;
		}

		@Override
		protected TypeToken<E> getType() {
			return theType;
		}

		@Override
		protected E create(QuickMap<String, Object> fieldValues, ObservableConfig config, Observable<?> until) {
			return theCreator.apply(fieldValues);
		}
	}

	class EntityConfigFormatImpl<E> extends AbstractComponentFormat<E> implements EntityConfigFormat<E> {
		public final EntityConfiguredValueType<E> entityType;

		private EntityConfigFormatImpl(EntityConfiguredValueType<E> entityType, List<EntitySubFormat<? extends E>> subFormats,
			QuickMap<String, ObservableConfigFormat<?>> fieldFormats, QuickMap<String, String> childNames) {
			super(buildFields(entityType, fieldFormats, childNames), subFormats);
			this.entityType = entityType;
		}

		private static <E> QuickMap<String, ComponentField<E, ?>> buildFields(EntityConfiguredValueType<E> entityType,
			QuickMap<String, ObservableConfigFormat<?>> fieldFormats, QuickMap<String, String> childNames) {
			QuickMap<String, ComponentField<E, ?>> fields = entityType.getFields().keySet().createMap();
			for (int i = 0; i < fields.keySize(); i++)
				fields.put(i, buildField(entityType, fieldFormats, childNames, i));
			return fields.unmodifiable();
		}

		private static <E, F> ComponentField<E, F> buildField(EntityConfiguredValueType<E> entityType,
			QuickMap<String, ObservableConfigFormat<?>> fieldFormats, QuickMap<String, String> childNames, int index) {
			return new ComponentField<>(fieldFormats.keySet().get(index), (TypeToken<F>) entityType.getFields().get(index).getFieldType(),
				index, e -> (F) entityType.getFields().get(index).get(e),
				(e, f) -> ((EntityConfiguredValueField<E, F>) entityType.getFields().get(index)).set(e, f), //
				(ObservableConfigFormat<F>) fieldFormats.get(index), childNames.get(index));
		}

		@Override
		protected TypeToken<E> getType() {
			return getEntityType().getType();
		}

		@Override
		public EntityConfiguredValueType<E> getEntityType() {
			return entityType;
		}

		@Override
		public <E2 extends E> EntityConfigCreator<E2> create(TypeToken<E2> subType) {
			ConfigCreator<E2> creator = super.create(subType);
			if (creator instanceof EntityConfigCreator)
				return (EntityConfigCreator<E2>) creator;
			return new EntityConfigCreator<E2>() {
				@Override
				public EntityConfiguredValueType<E2> getEntityType() {
					return (EntityConfiguredValueType<E2>) entityType;
				}

				@Override
				public EntityConfigCreator<E2> with(int fieldIndex, Object value) throws IllegalArgumentException {
					creator.with(fieldIndex, value);
					return this;
				}

				@Override
				public Set<Integer> getRequiredFields() {
					return creator.getRequiredFields();
				}

				@Override
				public EntityConfigCreator<E2> with(String fieldName, Object value) throws IllegalArgumentException {
					creator.with(fieldName, value);
					return this;
				}

				@Override
				public <F> EntityConfigCreator<E2> with(ConfiguredValueField<? super E2, F> field, F value)
					throws IllegalArgumentException {
					creator.with(field.getIndex(), value);
					return this;
				}

				@Override
				public <F> EntityConfigCreator<E2> with(Function<? super E2, F> fieldGetter, F value) throws IllegalArgumentException {
					ConfiguredValueField<? super E, F> field = entityType.getField((Function<E, F>) fieldGetter);
					creator.with(field.getIndex(), value);
					return this;
				}

				@Override
				public E2 create(ObservableConfig config, Observable<?> until) {
					return creator.create(config, until);
				}
			};
		}

		@Override
		protected E create(QuickMap<String, Object> fieldValues, ObservableConfig config, Observable<?> until) {
			E instance = entityType.create(//
				idx -> fieldValues.get(idx), //
				(idx, value) -> {
					fieldValues.put(idx, value);
					formatField((ComponentField<E, Object>) getFields().get(idx), value, config, v -> fieldValues.put(idx, v), until);
				});
			entityType.associate(instance, "until", until);
			return instance;
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
					previousValue = (C) new ObservableConfigTransform.ObservableConfigValues<>(config, ObservableValue.of(config), null,
						elementType, elementFormat, childName, fieldParser, until, false, Observable.constant(null));
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
			public C parse(ObservableConfigParseContext<C> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return (C) new ObservableConfigTransform.ObservableConfigValues<>(ctx.getRoot(), ctx.getConfig(),
						() -> ctx.getConfig(true), elementType, elementFormat, childName, fieldParser, ctx.getUntil(), false,
						ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigValues<E>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}

			@Override
			public C copy(C source, C copy, ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create,
				Observable<?> until) {
				try (Transaction t2 = Transactable.lock(copy, true, null); Transaction t1 = Transactable.lock(source, false, null)) {
					for (E value : source) {
						((Collection<E>) copy)
						.add(elementFormat.copy(value, null, asChild(config, childName), asChild(config, create, childName), until));
					}
				}
				return copy;
			}
		};
	}

	static ObservableValue<? extends ObservableConfig> asChild(ObservableValue<? extends ObservableConfig> config, String child) {
		return config.map(c -> c == null ? null : c.getChild(child));
	}

	static Supplier<ObservableConfig> asChild(ObservableValue<? extends ObservableConfig> config,
		Supplier<? extends ObservableConfig> create, String child) {
		return () -> {
			ObservableConfig c = config.get();
			if (c == null)
				c = create.get();
			return c.getChild(child, true, null);
		};
	}

	static <E> ObservableConfigFormat<ObservableValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String childName) {
		return new ObservableConfigFormat<ObservableValueSet<E>>() {
			@Override
			public void format(ObservableValueSet<E> value, ObservableValueSet<E> preValue, ObservableConfig config,
				Consumer<ObservableValueSet<E>> acceptedValue, Observable<?> until) {
				// Nah, we don't support calling set on a field like this, nothing to do
				acceptedValue.accept(preValue);
			}

			@Override
			public ObservableValueSet<E> parse(ObservableConfigParseContext<ObservableValueSet<E>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigEntityValues<>(ctx.getRoot(), ctx.getConfig(),
						() -> ctx.getConfig(true), elementFormat, childName, ctx.getUntil(), false, ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigEntityValues<E>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}

			@Override
			public ObservableValueSet<E> copy(ObservableValueSet<E> source, ObservableValueSet<E> copy,
				ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, Observable<?> until) {
				for (E value : source.getValues())
					copy.copy(value);
				return copy;
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
				if (!configFilter.isMulti() && !config.getName().equals(configFilter.getName()))
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
		public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
			ObservableConfig c = ctx.getConfig(false);
			if (c == null || "true".equals(c.get("null")))
				return null;
			SubFormat<? extends T> format = formatFor(c);
			if (format == null)
				throw new ParseException("No sub-format found matching " + c, 0);
			return ((SubFormat<T>) format).format.parse(ctx.forChild((config, create) -> {
				if (create)
					format.moldConfig(config);
				return config;
			}, ctx.getChange(), format.applies(ctx.getPreviousValue()) ? ctx.getPreviousValue() : null, ctx::linkedReference));
		}

		@Override
		public T copy(T source, T copy, ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create,
			Observable<?> until) {
			SubFormat<? extends T> sub = null;
			for (SubFormat<? extends T> subFormat : theSubFormats)
				if (subFormat.applies(source) && (copy == null || subFormat.applies(copy))) {
					sub = subFormat;
					break;
				}
			return ((ObservableConfigFormat<T>) sub.format).copy(source, copy, config, create, until);
		}
	}
}
