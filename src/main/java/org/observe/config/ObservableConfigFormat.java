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
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.config.EntityConfiguredValueType.EntityConfiguredValueField;
import org.observe.config.ObservableConfig.ObservableConfigEvent;
import org.observe.config.ObservableConfig.ObservableConfigPathElement;
import org.observe.config.ObservableConfigFormat.ReferenceFormat.FormattedField;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.FieldChange;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.QuickSet;
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
	public static ObservableConfigFormat<Instant> DATE = ofQommonFormat(Format.date("ddMMyyyy HH:mm:ss.SSS"), () -> null);

	interface ConfigGetter {
		ObservableConfig getConfig(boolean createIfAbsent);

		default ConfigGetter forChild(String childName) {
			ConfigGetter parent = this;
			return createIfAbsent -> {
				if (createIfAbsent)
					return parent.getConfig(true).getChild(childName, true, null);
				ObservableConfig config = parent.getConfig(false);
				return config == null ? null : config.getChild(childName);
			};
		}
	}

	void format(ObservableConfigParseSession session, E value, E previousValue, ConfigGetter config, Consumer<E> acceptedValue,
		Observable<?> until) throws IllegalArgumentException;

	E parse(ObservableConfigParseContext<E> ctx) throws ParseException;

	interface ObservableConfigParseContext<E> extends ConfigGetter {
		ObservableConfigParseSession getSession();

		ObservableConfig getRoot();

		ObservableValue<? extends ObservableConfig> getConfig();

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

		default <V> ObservableConfigParseContext<V> forChild(ObservableConfig child, V previousValue, Consumer<V> delayedAccept) {
			return forChild((config, create) -> child, null, previousValue, delayedAccept);
		}

		ObservableConfigParseContext<E> withRefFinding(Observable<?> findRefs);
	}

	static <E> ObservableConfigParseContext<E> ctxFor(ObservableConfigParseSession session, ObservableConfig root,
		ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, ObservableConfigEvent change,
		Observable<?> until, E previousValue, Observable<?> findReferences, Consumer<E> delayedAccept) {
		return new DefaultOCParseContext<>(session, root, config, create, change, until, previousValue, findReferences, delayedAccept);
	}

	static class DefaultOCParseContext<E> implements ObservableConfigParseContext<E> {
		private final ObservableConfigParseSession theSession;
		private final ObservableConfig theRoot;
		private final ObservableValue<? extends ObservableConfig> theConfig;
		private final Supplier<? extends ObservableConfig> theCreate;
		private final ObservableConfigEvent theChange;
		private final Observable<?> theUntil;
		private final E thePreviousValue;
		private final Observable<?> findReferences;
		private final Consumer<E> delayedAccept;

		public DefaultOCParseContext(ObservableConfigParseSession session, ObservableConfig root,
			ObservableValue<? extends ObservableConfig> config, Supplier<? extends ObservableConfig> create, ObservableConfigEvent change,
			Observable<?> until, E previousValue, Observable<?> findReferences, Consumer<E> delayedAccept) {
			theSession = session;
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
		public ObservableConfigParseSession getSession() {
			return theSession;
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
			return new DefaultOCParseContext<>(theSession, theRoot, config, create, change, theUntil, previousValue, findReferences,
				delayedAccept);
		}

		@Override
		public ObservableConfigParseContext<E> withRefFinding(Observable<?> findRefs) {
			return new DefaultOCParseContext<>(theSession, theRoot, theConfig, theCreate, theChange, theUntil, thePreviousValue, findRefs,
				delayedAccept);
		}
	}

	static <T> SimpleConfigFormat<T> ofQommonFormat(Format<T> format, Supplier<? extends T> defaultValue) {
		return new SimpleConfigFormat<>(format, defaultValue);
	}

	static <E extends Enum<?>> ObservableConfigFormat<E> enumFormat(Class<E> type, Supplier<? extends E> defaultValue) {
		return new SimpleConfigFormat<>(Format.enumFormat(type), defaultValue);
	}

	public class SimpleConfigFormat<T> implements ObservableConfigFormat<T> {
		public final Format<T> format;
		public final Supplier<? extends T> defaultValue;

		public SimpleConfigFormat(Format<T> format, Supplier<? extends T> defaultValue) {
			this.format = format;
			this.defaultValue = defaultValue;
		}

		@Override
		public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config,
			Consumer<T> acceptedValue, Observable<?> until) {
			acceptedValue.accept(value);
			String formatted;
			if (value == null)
				formatted = null;
			else
				formatted = format.format(value);
			if (!Objects.equals(formatted, config.getConfig(true).getValue()))
				config.getConfig(true).setValue(formatted);
		}

		@Override
		public T parse(ObservableConfigParseContext<T> ctx) {
			ObservableConfig config = ctx.getConfig(false);
			if (config == null)
				return defaultValue.get();
			String value = config == null ? null : config.getValue();
			if (value == null)
				return defaultValue.get();
			if (ctx.getChange() != null && ctx.getChange().relativePath.size() > 1)
				return ctx.getPreviousValue(); // Changing a sub-config doesn't affect this value
			try {
				return format.parse(value);
			} catch (ParseException e) {
				e.printStackTrace();
				return defaultValue == null ? null : defaultValue.get();
			}
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

	public class ReferenceFormat<T> implements ObservableConfigFormat<T> {
		static class FormattedField<T, F> {
			public final Function<? super T, F> field;
			public final ObservableConfigFormat<F> format;

			public FormattedField(Function<? super T, F> field, ObservableConfigFormat<F> format) {
				this.field = field;
				this.format = format;
			}
		}

		private final QuickMap<String, FormattedField<? super T, ?>> theFields;
		private final BooleanSupplier isRetrieverReady;
		private final Function<QuickMap<String, Object>, ? extends T> theRetriever;

		ReferenceFormat(QuickMap<String, FormattedField<? super T, ?>> fields, Function<QuickMap<String, Object>, ? extends T> retriever,
			BooleanSupplier retrieverReady) {
			theFields = fields;
			isRetrieverReady = retrieverReady;
			theRetriever = retriever;
		}

		@Override
		public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config,
			Consumer<T> acceptedValue, Observable<?> until) throws IllegalArgumentException {
			if (value == null) {
				if (config.getConfig(false) != null) {
					for (ObservableConfig child : config.getConfig(true)._getContent()) {
						if (child.getName().equals("null")) {
							child.setValue("true");
							child.getAllContent().getValues().clear();
						} else
							child.remove();
					}
				}
				acceptedValue.accept(value);
				return;
			}
			ObservableConfig nullConfig = config.getConfig(true).getChild("null");
			if (nullConfig != null)
				nullConfig.remove();
			boolean[] added = new boolean[1];
			for (int f = 0; f < theFields.keySize(); f++) {
				FormattedField<? super T, Object> field = (FormattedField<? super T, Object>) theFields.get(f);
				Object fieldValue = field.field.apply(value);
				Object preFieldValue = previousValue == null ? null : field.field.apply(previousValue);
				ObservableConfig fieldConfig = config.getConfig(true).getChild(theFields.keySet().get(f), true, //
					child -> {
						added[0] = true;
						formatField(session, child, fieldValue, preFieldValue, field.format, until);
					});
				if (!added[0])
					formatField(session, fieldConfig, fieldValue, preFieldValue, field.format, until);
			}
			acceptedValue.accept(value);
			config.getConfig(true).withParsedItem(session, value);
		}

		private <F> void formatField(ObservableConfigParseSession session, ObservableConfig child, F fieldValue, F preFieldValue,
			ObservableConfigFormat<F> format, Observable<?> until) {
			format.format(session, fieldValue, preFieldValue, __ -> child, f -> {}, until);
		}

		@Override
		public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
			ObservableConfig c = ctx.getConfig(false);
			if (c == null || "true".equals(c.get("null")))
				return null;
			DelayedExecution<T> exec = new DelayedExecution<>();
			parse(ctx, c, exec);
			if (exec.thrown != null)
				throw exec.thrown;
			else if (exec.found)
				return exec.value;
			ctx.findReferences().act(__ -> parse(ctx, c, exec));
			if (exec.thrown != null)
				throw exec.thrown;
			exec.delayed = true;
			return exec.value;
		}

		private void parse(ObservableConfigParseContext<T> ctx, ObservableConfig c, DelayedExecution<T> exec) {
			if (isRetrieverReady != null && !isRetrieverReady.getAsBoolean()) {
				exec.found = false;
				return;
			}
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
					fieldValue = ((ObservableConfigFormat<Object>) theFields.get(f).format).parse(//
						ctx.forChild(theFields.keySet().get(f), preValue, null).withRefFinding(Observable.constant(null)));
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
			exec.found = true;
			if (usePreValue)
				exec.value = previousValue;
			else
				exec.value = theRetriever.apply(fieldValues.unmodifiable());
			c.withParsedItem(ctx.getSession(), exec.value);
			if (exec.delayed)
				ctx.linkedReference(exec.value);
		}

		@Override
		public int hashCode() {
			return theRetriever.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ReferenceFormat && theRetriever.equals(((ReferenceFormat<?>) obj).theRetriever);
		}

		@Override
		public String toString() {
			return "from " + theRetriever.toString();
		}
	}

	class DelayedExecution<T> {
		boolean delayed;
		boolean found;
		T value;
		ParseException thrown;
	}

	public static class ReferenceFormatBuilder<T> {
		private final Function<QuickMap<String, Object>, ? extends T> theRetriever;
		private final Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> theMultiRetriever;
		private final Supplier<? extends T> theRetreiverDefault;
		private BooleanSupplier isRetrieverReady;
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

		public ReferenceFormatBuilder<T> withRetrieverReady(BooleanSupplier retrieverReady) {
			isRetrieverReady = retrieverReady;
			return this;
		}

		public ReferenceFormat<T> build() {
			QuickMap<String, FormattedField<? super T, ?>> fields = QuickMap.of(theFields, StringUtils.DISTINCT_NUMBER_TOLERANT)
				.unmodifiable();
			Function<QuickMap<String, Object>, ? extends T> retriever;
			if (theRetriever != null)
				retriever = theRetriever;
			else {
				retriever = LambdaUtils.printableFn(fieldValues -> {
					Iterable<? extends T> retrieved = theMultiRetriever.apply(fieldValues);
					// TODO Null check
					for (T value : retrieved) {
						boolean matches = true;
						for (int f = 0; matches && f < fields.keySize(); f++) {
							Object valueF = fields.get(f).field.apply(value);
							matches = Objects.equals(fieldValues.get(f), valueF);
						}
						if (matches) {
							return value;
						}
					}
					return theRetreiverDefault.get();
				}, theMultiRetriever::toString, theMultiRetriever);
			}
			return new ReferenceFormat<>(fields, retriever, isRetrieverReady);
		}
	}

	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Iterable<? extends T> values, Supplier<? extends T> defaultValue) {
		if (values instanceof Identifiable)
			return buildReferenceFormat(LambdaUtils.printableFn(__ -> values, () -> ((Identifiable) values).getIdentity().toString(),
				((Identifiable) values).getIdentity()), defaultValue);
		else
			return buildReferenceFormat(LambdaUtils.printableFn(__ -> values, values::toString, values), defaultValue);
	}

	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Function<QuickMap<String, Object>, ? extends T> retriever) {
		return new ReferenceFormatBuilder<>(retriever);
	}

	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> retriever,
		Supplier<? extends T> defaultValue) {
		return new ReferenceFormatBuilder<>(retriever, defaultValue);
	}

	public class ParentReferenceFormat<T> implements ObservableConfigFormat<T> {
		private final TypeToken<T> theType;

		public ParentReferenceFormat(TypeToken<T> type) {
			theType = type;
		}

		@Override
		public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config,
			Consumer<T> acceptedValue, Observable<?> until) throws IllegalArgumentException {
			// No need to persist at all
		}

		@Override
		public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
			DelayedExecution<T> exec = new DelayedExecution<>();
			ctx.findReferences().act(__ -> {
				ObservableConfig parent = ctx.getRoot();
				while (parent != null) {
					Object item = parent.getParsedItem(ctx.getSession());
					if (item == null)
						break;
					else if (TypeTokens.get().isInstance(theType, item)){
						exec.value= (T) item;
						break;
					}
					parent = parent.getParent();
				}
				if (exec.delayed) {
					if (exec.value == null)
						exec.thrown = new ParseException("Could not find parent reference in config stack", 0);
					else
						ctx.linkedReference(exec.value);
				}
			});
			if (exec.thrown != null)
				throw exec.thrown;
			exec.delayed = true;
			return exec.value;
		}
	}

	public interface ConfigCreator<E> {
		Set<Integer> getRequiredFields();

		ConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

		ConfigCreator<E> with(int fieldIndex, Object value) throws IllegalArgumentException;

		E create(ObservableConfig config, Observable<?> until);
	}

	public interface EntityConfigCreator<E> extends ConfigCreator<E> {
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

	public interface EntityConfigFormat<E> extends ObservableConfigFormat<E> {
		EntityConfiguredValueType<E> getEntityType();

		<F> ObservableConfigFormat<F> getFieldFormat(ConfiguredValueField<E, F> field);

		<E2 extends E> EntityConfigCreator<E2> create(ObservableConfigParseSession session, TypeToken<E2> subType);
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
				if (formats.get(i) == null) {
					if (theEntityType.getFields().get(i).isParentReference())
						formats.put(i, new ParentReferenceFormat<>(theEntityType.getFields().get(i).getFieldType()));
					else
						formats.put(i, theFormatSet.getConfigFormat(theEntityType.getFields().get(i)));
				}
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

	public class EntitySubFormat<E> {
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

	public class PathElementBuilder {
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

	public abstract class AbstractComponentFormat<E> implements ObservableConfigFormat<E> {
		public interface ComponentSetter<E, F> {
			void set(E entity, F field, ObservableConfigEvent cause);
		}

		static class ComponentField<E, F> {
			final String name;
			final TypeToken<F> type;
			final int index;
			final Function<? super E, ? extends F> getter;
			final ComponentSetter<? super E, ? super F> setter;
			final ObservableConfigFormat<F> format;
			final String childName;

			ComponentField(String name, TypeToken<F> type, int index, Function<? super E, ? extends F> getter,
				ComponentSetter<? super E, ? super F> setter, ObservableConfigFormat<F> format, String childName) {
				this.name = name;
				this.type = type;
				this.index = index;
				this.getter = getter;
				this.setter = setter;
				this.format = format;
				this.childName = childName;
			}

			@Override
			public String toString() {
				return name;
			}
		}
		private final QuickMap<String, ComponentField<E, ?>> theFields;
		private final List<EntitySubFormat<? extends E>> theSubFormats;
		private final QuickMap<String, ComponentField<E, ?>> theFieldsByChildName;
		private final ComponentField<E, ?> theDefaultComponent;

		private AbstractComponentFormat(QuickMap<String, ComponentField<E, ?>> fields, List<EntitySubFormat<? extends E>> subFormats) {
			theFields = fields;
			theSubFormats = subFormats;
			Map<String, ComponentField<E, ?>> fcnReverse = new LinkedHashMap<>();
			ComponentField<E, ?> defComponent = null;
			for (int i = 0; i < theFields.keySize(); i++) {
				if (theFields.get(i).childName == null) {
					if (defComponent == null)
						defComponent = theFields.get(i);
					else
						throw new IllegalArgumentException("Multiple default (unnamed fields)");
				} else
					fcnReverse.put(theFields.get(i).childName, theFields.get(i));
			}
			theFieldsByChildName = QuickMap.of(fcnReverse, String::compareTo);
			theDefaultComponent = defComponent;
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
		public void format(ObservableConfigParseSession session, E value, E previousValue, ConfigGetter config,
			Consumer<E> acceptedValue, Observable<?> until) {
			if (value == null) {
				acceptedValue.accept(null);
				ObservableConfig c = config.getConfig(false);
				if (c != null) {
					c.setName("null");
					for (int i = 0; i < theFields.keySize(); i++) {
						if (theFields.get(i).childName == null) {
							Object fieldValue = previousValue == null ? null : theFields.get(i).getter.apply(previousValue);
							formatField(session, value, (ComponentField<E, Object>) theFields.get(i), fieldValue, fieldValue, c, fv -> {},
								until, null);
						} else {
							ObservableConfig cfg = c.getChild(theFields.get(i).childName);
							if (cfg != null)
								cfg.remove();
						}
						c.withParsedItem(session, null);
					}
				}
			} else {
				ObservableConfig c = config.getConfig(true);
				EntitySubFormat<? extends E> subFormat = formatFor(value);
				if (subFormat != null) {
					subFormat.moldConfig(c);
					((EntitySubFormat<E>) subFormat).format.format(session, value, subFormat.applies(previousValue) ? previousValue : null,
						config, acceptedValue, until);
					return;
				}
				acceptedValue.accept(value);
				c.set("null", null);
				for (int i = 0; i < theFields.keySize(); i++) {
					ComponentField<E, ?> field = theFields.get(i);
					Object fieldValue = field.getter.apply(value);
					formatField(session, value, (ComponentField<E, Object>) field, fieldValue, fieldValue, c, fv -> {}, until, null);
				}
				c.withParsedItem(session, value);
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
				return createInstance(ctx.getSession(), c, theFields.keySet().createMap(), ctx.getUntil(), ctx.findReferences(), ctx);
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
						parseUpdatedField(ctx, ctx.getPreviousValue(), c, i, null);
				} else if (ctx.getChange().relativePath.isEmpty()) {
					if (theDefaultComponent != null)
						parseUpdatedField(ctx, ctx.getPreviousValue(), c, theDefaultComponent.index, null);
				} else {
					ObservableConfigEvent change = ctx.getChange();
					ObservableConfig child = change.relativePath.get(0);
					int fieldIdx = theFieldsByChildName.keyIndexTolerant(child.getName());
					if (change.relativePath.size() == 1 && !change.oldName.equals(child.getName())) {
						if (fieldIdx >= 0) {
							ObservableConfigEvent childChange = change.asFromChild();
							try (Transaction ct = childChange.use()) {
								parseUpdatedField(ctx, ctx.getPreviousValue(), c, fieldIdx, childChange);
							}
						}
						fieldIdx = theFieldsByChildName.keyIndexTolerant(change.oldName);
						if (fieldIdx >= 0)
							parseUpdatedField(ctx, ctx.getPreviousValue(), c, fieldIdx, null);
					} else if (fieldIdx >= 0)
						parseUpdatedField(ctx, ctx.getPreviousValue(), c, fieldIdx, change);
				}
				return ctx.getPreviousValue();
			}
		}

		public <E2 extends E> ConfigCreator<E2> create(ObservableConfigParseSession session, TypeToken<E2> subType) {
			if (subType != null && !subType.equals(getType())) {
				for (EntitySubFormat<? extends E> subFormat : theSubFormats) {
					if (subFormat.type.isAssignableFrom(subType))
						return ((EntitySubFormat<? super E2>) subFormat).format.create(session, subType);
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
						SimpleObservable<Void> findRefs = SimpleObservable.build().safe(false).build();
						E inst = createInstance(session, config, theFieldValues, until, findRefs, null);
						findRefs.onNext(null);
						return inst;
					} catch (ParseException e) {
						throw new IllegalStateException("Could not create instance", e);
					}
				}
			};
		}

		E createInstance(ObservableConfigParseSession session, ObservableConfig config, QuickMap<String, Object> fieldValues,
			Observable<?> until, Observable<?> findReferences, ObservableConfigParseContext<E> ctx) throws ParseException {
			for (EntitySubFormat<? extends E> subFormat : theSubFormats) {
				if (subFormat.configFilter.matches(config))
					return subFormat.format
						.parse(ctxFor(session, config, ObservableValue.of(config), null, null, until, null, findReferences, null));
			}
			for (int i = 0; i < fieldValues.keySize(); i++) {
				int fi = i;
				ObservableValue<? extends ObservableConfig> fieldConfig;
				Supplier<ObservableConfig> supply;
				if (theFields.get(i).childName == null) {
					fieldConfig = ObservableValue.of(config);
					supply = () -> config;
				} else {
					fieldConfig = config.observeDescendant(theFields.get(i).childName);
					supply = () -> config.getChild(theFields.get(fi).childName, true, null);
				}
				// If the field has been set, format it into
				ComponentField<E, Object> field = (ComponentField<E, Object>) theFields.get(i);
				if (fieldValues.get(i) != null) {
					field.format.format(session, fieldValues.get(i), null, cia -> {
						ObservableConfig c = fieldConfig.get();
						if (c == null && cia)
							c = supply.get();
						return c;
					}, v -> fieldValues.put(fi, v), until);
				}
				fieldValues.put(i, field.format
					.parse(ctxFor(session, config, fieldConfig, supply, null, until, null, findReferences, fv -> fieldValues.put(fi, fv))));
			}
			E value = create(session, fieldValues, config, until, ctx);
			config.withParsedItem(session, value);
			return value;
		}

		protected abstract E create(ObservableConfigParseSession session, QuickMap<String, Object> fieldValues, ObservableConfig config,
			Observable<?> until, ObservableConfigParseContext<E> ctx);

		protected <F> void formatField(ObservableConfigParseSession session, E value, ComponentField<E, F> field, F previousValue,
			F fieldValue, ObservableConfig entityConfig, Consumer<F> onFieldValue, Observable<?> until, Object cause) {
			boolean[] added = new boolean[1];
			if (fieldValue != null || field.childName == null) {
				ObservableConfig fieldConfig;
				if (field.childName == null) {
					fieldConfig = entityConfig;
				} else {
					fieldConfig = entityConfig.getChild(field.childName, true, fc -> {
						added[0] = true;
						field.format.format(session, fieldValue, fieldValue, __ -> fc, onFieldValue, until);
					});
				}
				if (!added[0])
					field.format.format(session, fieldValue, fieldValue, __ -> fieldConfig, onFieldValue, until);
			} else {
				ObservableConfig fieldConfig = entityConfig.getChild(field.childName);
				if (fieldConfig != null)
					fieldConfig.remove();
			}
		}

		protected <F> void parseUpdatedField(ObservableConfigParseContext<E> ctx, E value, ObservableConfig entityConfig, int fieldIdx,
			ObservableConfigEvent change) throws ParseException {
			ComponentField<? super E, F> field = (ComponentField<? super E, F>) theFields.get(fieldIdx);
			F oldValue = field.getter.apply(ctx.getPreviousValue());
			ObservableValue<? extends ObservableConfig> fieldConfig;
			Supplier<ObservableConfig> supply;
			if (field.childName == null) {
				fieldConfig = ObservableValue.of(entityConfig);
				supply = () -> entityConfig;
				ObservableConfigEvent fChange = change;
				F newValue = field.format.parse(ctxFor(ctx.getSession(), entityConfig, fieldConfig, supply, change, ctx.getUntil(),
					oldValue, ctx.findReferences(), fv -> field.setter.set(ctx.getPreviousValue(), fv, fChange)));
				if (newValue != oldValue)
					field.setter.set(ctx.getPreviousValue(), newValue, change);
				return; // The update does not actually affect the field value
			} else {
				fieldConfig = entityConfig.observeDescendant(field.childName);
				supply = () -> entityConfig.getChild(field.childName, true, null);
				if (change != null) {
					if (change.relativePath.isEmpty() || fieldConfig != change.relativePath.get(0)) {
						ObservableConfigEvent childChange = change.asFromChild();
						F newValue;
						try (Transaction ct = childChange.use()) {
							newValue = field.format
								.parse(ctxFor(ctx.getSession(), entityConfig, fieldConfig, supply, childChange, ctx.getUntil(), oldValue,
									ctx.findReferences(), fv -> field.setter.set(ctx.getPreviousValue(), fv, childChange)));
						}
						if (newValue != oldValue)
							field.setter.set(ctx.getPreviousValue(), newValue, change);
						return; // The update does not actually affect the field value
					}
					change = change.asFromChild();
				}
			}
			ObservableConfigEvent fChange = change;
			F newValue = field.format.parse(ctxFor(ctx.getSession(), entityConfig, fieldConfig, supply, change,
				ctx.getUntil(), oldValue, ctx.findReferences(), fv -> field.setter.set(ctx.getPreviousValue(), fv, fChange)));
			if (oldValue != newValue)
				field.setter.set(ctx.getPreviousValue(), newValue, change);
		}
	}

	static class MapEntry<K, V> {
		K key;
		V value;

		MapEntry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public String toString() {
			return key + "=" + value;
		}
	}

	static class EntryFormat<K, V> extends AbstractComponentFormat<MapEntry<K, V>> {
		private final TypeToken<MapEntry<K, V>> theType;

		public EntryFormat(boolean compressed, String keyName, String valueName, TypeToken<K> keyType, TypeToken<V> valueType,
			ObservableConfigFormat<K> keyFormat, ObservableConfigFormat<V> valueFormat) {
			super(createFields(compressed, keyName, valueName, keyType, valueType, keyFormat, valueFormat), Collections.emptyList());
			theType = TypeTokens.get().keyFor(MapEntry.class).parameterized(keyType, valueType);
		}

		private static <K, V> QuickMap<String, ComponentField<MapEntry<K, V>, ?>> createFields(boolean compressed, String keyName,
			String valueName, //
			TypeToken<K> keyType, TypeToken<V> valueType, //
			ObservableConfigFormat<K> keyFormat, ObservableConfigFormat<V> valueFormat) {
			QuickMap<String, ComponentField<MapEntry<K, V>, ?>> fields = QuickSet.of("key", "value").createMap();
			fields.put("key", new ComponentField<>(keyName, keyType, 0, e -> e.key, (e, k, change) -> e.key = k, keyFormat, keyName));
			fields.put("value", new ComponentField<>(compressed ? null : valueName, valueType, 0, e -> e.value,
				(e, v, change) -> e.value = v, valueFormat, valueName));
			return fields.unmodifiable();
		}

		@Override
		protected TypeToken<MapEntry<K, V>> getType() {
			return theType;
		}

		public ObservableConfigFormat<K> getKeyFormat() {
			return (ObservableConfigFormat<K>) getFields().get(0).format;
		}

		public ObservableConfigFormat<V> getValueFormat() {
			return (ObservableConfigFormat<V>) getFields().get(1).format;
		}

		@Override
		protected MapEntry<K, V> create(ObservableConfigParseSession session, QuickMap<String, Object> fieldValues, ObservableConfig config,
			Observable<?> until, ObservableConfigParseContext<MapEntry<K, V>> ctx) {
			return new MapEntry<>((K) fieldValues.get(0), (V) fieldValues.get(1));
		}
	}

	public class SimpleComponentFormat<E> extends AbstractComponentFormat<E> {
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
		protected E create(ObservableConfigParseSession session, QuickMap<String, Object> fieldValues, ObservableConfig config,
			Observable<?> until, ObservableConfigParseContext<E> ctx) {
			return theCreator.apply(fieldValues);
		}
	}

	public class EntityConfigFormatImpl<E> extends AbstractComponentFormat<E> implements EntityConfigFormat<E> {
		public static class FieldUpdate<F> {
			public final F oldValue;
			public final F newValue;
			public final ObservableConfigEvent cause;

			public FieldUpdate(F oldValue, F newValue, ObservableConfigEvent cause) {
				this.oldValue = oldValue;
				this.newValue = newValue;
				this.cause = cause;
			}
		}

		static class EntityFieldSetter<E, F> implements ComponentSetter<E, F> {
			private final EntityConfiguredValueField<E, F> field;

			public EntityFieldSetter(EntityConfiguredValueField<E, F> field) {
				this.field = field;
			}

			@Override
			public void set(E entity, F fieldValue, ObservableConfigEvent change) {
				ListenerList<Consumer<EntityReflector.FieldChange<F>>> listeners;
				listeners = (ListenerList<Consumer<FieldChange<F>>>) field.getOwnerType().getAssociated(entity, this);
				if (listeners == null) {
					field.set(entity, fieldValue);
				} else {
					F oldValue = field.get(entity);
					field.set(entity, fieldValue);
					FieldChange<F> fieldChange = new FieldChange<>(oldValue, fieldValue, change);
					listeners.forEach(//
						l -> l.accept(fieldChange));
				}
			}
		}

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
				new EntityFieldSetter<>((EntityConfiguredValueField<E, F>) entityType.getFields().get(index)), //
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
		public <F> ObservableConfigFormat<F> getFieldFormat(ConfiguredValueField<E, F> field) {
			return (ObservableConfigFormat<F>) getFields().get(field.getIndex()).format;
		}

		@Override
		public <E2 extends E> EntityConfigCreator<E2> create(ObservableConfigParseSession session, TypeToken<E2> subType) {
			ConfigCreator<E2> creator = super.create(session, subType);
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
		protected E create(ObservableConfigParseSession session, QuickMap<String, Object> fieldValues, ObservableConfig config,
			Observable<?> until, ObservableConfigParseContext<E> ctx) {
			if (!entityType.getIdFields().isEmpty()) {
				if (!tryPopulateNewId(entityType, fieldValues, config, session) && ctx != null) {
					ctx.findReferences().act(__ -> {
						tryPopulateNewId(entityType, fieldValues, config, session);
					});
				}
			}
			Object[] created = new Object[1];
			E instance = entityType.create(new EntityReflector.ObservableEntityInstanceBacking<E>() {
				@Override
				public Object get(int fieldIndex) {
					return fieldValues.get(fieldIndex);
				}

				@Override
				public void set(int fieldIndex, Object newValue) {
					Object oldValue = fieldValues.get(fieldIndex);
					fieldValues.put(fieldIndex, newValue);
					formatField(session, (E) created[0], (ComponentField<E, Object>) getFields().get(fieldIndex), oldValue, newValue,
						config, v -> fieldValues.put(fieldIndex, v), until, null);
				}

				@Override
				public Subscription addListener(E entity, int fieldIndex, Consumer<FieldChange<?>> listener) {
					Object key = getFields().get(fieldIndex).setter;
					try (Transaction t = getLock(fieldIndex).lock(false, null)) {
						ListenerList<Consumer<FieldChange<?>>> listeners = (ListenerList<Consumer<FieldChange<?>>>) entityType
							.getAssociated(entity, key);
						if (listeners == null) {
							listeners = ListenerList.build().build();
							entityType.associate(entity, key, listeners);
						}
						return listeners.add(listener, true)::run;
					}
				}

				@Override
				public Transactable getLock(int fieldIndex) {
					// If we ever try to support hierarchical locks or anything, this should be the field config
					return config;
				}

				@Override
				public long getStamp(int fieldIndex) {
					return config.getChild(getFields().get(fieldIndex).childName, true, null).getStamp();
				}

				@Override
				public String isAcceptable(int fieldIndex, Object value) {
					return null; // No filter mechanism available
				}

				@Override
				public ObservableValue<String> isEnabled(int fieldIndex) {
					return SettableValue.ALWAYS_ENABLED; // No enablement mechanism
				}
			});
			created[0] = instance;
			entityType.associate(instance, "until", until);
			return instance;
		}

		private <E2 extends E> boolean tryPopulateNewId(EntityConfiguredValueType<E2> entityType,
			QuickMap<String, Object> fieldValues, ObservableConfig config, ObservableConfigParseSession session) {
			if (config.getParent() == null)
				return true;
			Object parent = config.getParent().getParsedItem(session);
			if (parent == null)
				return false;
			EntityConfiguredValueField<? super E2, ?> idField = null;
			int fieldIndex = -1;
			Class<?> fieldType = null;
			for (Integer f : entityType.getIdFields()) {
				Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(entityType.getFields().get(f).getFieldType()));
				if (type == int.class || type == long.class) {
					idField = entityType.getFields().get(f);
					fieldIndex = f;
					fieldType = type;
					break;
				}
			}
			if (idField == null)
				return true; // Can't increment, so can't generate ID
			Iterable<? extends E> parentValues;
			if (parent instanceof Iterable)
				parentValues = (Iterable<E>) parent;
			else if (parent instanceof ObservableValueSet)
				parentValues = ((ObservableValueSet<E>) parent).getValues();
			else
				parentValues = null;
			if (parentValues == null)
				return false;
			// This is a bit complicated, but we'll see if we can assign an ID for the new value
			// that is unique in its owning collection
			TypeToken<?> parentTypeToken = null;
			if (parentValues instanceof ObservableCollection)
				parentTypeToken = ((ObservableCollection<?>) parentValues).getType();
			EntityConfiguredValueType<? super E2> parentType = entityType;
			if (parentTypeToken == null) {
				while (parentType.getSupers().size() == 1)
					parentType = parentType.getSupers().get(0);
				parentTypeToken = parentType.getType();
			} else if (parentType.getType().equals(parentTypeToken)) { // Simple, done
			} else if (parentTypeToken != null) {
				while (!parentType.getType().equals(parentTypeToken)) {
					boolean foundSuper = false;
					for (EntityConfiguredValueType<? super E2> superType : parentType.getSupers()) {
						if (parentTypeToken.isAssignableFrom(superType.getType())) {
							parentType = superType;
							foundSuper = true;
						}
					}
					if (!foundSuper)
						break;
				}
			}
			while (idField.getOwnerType() != parentType) {
				boolean found = false;
				for (EntityConfiguredValueField<? super E2, ?> override : idField.getOverrides()) {
					if (parentType.getType().isAssignableFrom(override.getOwnerType().getType())) {
						idField = override;
						found = true;
					}
				}
				if (!found)
					throw new IllegalStateException("No override found for ID field " + idField + " in " + parentType);
			}
			Object thisValue = config.getParsedItem(session);
			Object currentValue = fieldValues.get(fieldIndex);
			boolean distinctValue = currentValue != null;
			long id = 0;
			for (E parentV : parentValues) {
				if (parentV == thisValue)
					continue;
				if (TypeTokens.get().isInstance(parentTypeToken, parentV)) {
					Number idVal = ((EntityConfiguredValueField<E, ? extends Number>) idField).get(parentV);
					if (distinctValue && currentValue.equals(idVal))
						distinctValue = false;
					id = Math.max(id, idVal.longValue() + 1);
				}
			}
			ComponentField<E, ?> field = getFields().get(idField.getName());
			// The child check is to make sure the ID is actually persisted and not defaulted
			if (distinctValue && config.getChild(field.childName) != null)
				return true; // The given value is fine
			if (fieldType == int.class) {
				((ObservableConfigFormat<Integer>) field.format).format(session, (int) id, //
					(Integer) currentValue, cia -> config.getChild(field.childName, cia, null), __ -> {}, Observable.empty());
				fieldValues.put(fieldIndex, (int) id);
			} else {
				((ObservableConfigFormat<Long>) field.format).format(session, id, //
					(Long) currentValue, cia -> config.getChild(field.childName, cia, null), __ -> {}, Observable.empty());
				fieldValues.put(fieldIndex, id);
			}
			return true;
		}

		@Override
		protected <F> void formatField(ObservableConfigParseSession session, E value, ComponentField<E, F> field, F previousValue,
			F fieldValue, ObservableConfig entityConfig, Consumer<F> onFieldValue, Observable<?> until, Object cause) {
			super.formatField(session, value, field, previousValue, fieldValue, entityConfig, onFieldValue, until, cause);
			if (value != null) {
				Object key = field.setter;
				ListenerList<Consumer<FieldChange<?>>> listeners = (ListenerList<Consumer<FieldChange<?>>>) entityType.getAssociated(value,
					key);
				if (listeners != null) {
					FieldChange<F> change = new FieldChange<>(previousValue, fieldValue, cause);
					listeners.forEach(//
						l -> l.accept(change));
				}
			}
		}

		@Override
		protected <F> void parseUpdatedField(ObservableConfigParseContext<E> ctx, E value, ObservableConfig entityConfig, int fieldIdx,
			ObservableConfigEvent change) throws ParseException {
			ComponentField<E, F> field = (ComponentField<E, F>) getFields().get(fieldIdx);
			F oldValue = value == null ? null : field.getter.apply(value);
			super.parseUpdatedField(ctx, value, entityConfig, fieldIdx, change);
			if (value != null) {
				F newValue = value == null ? null : field.getter.apply(value);
				Object key = field.setter;
				ListenerList<Consumer<FieldChange<?>>> listeners = (ListenerList<Consumer<FieldChange<?>>>) entityType.getAssociated(value,
					key);
				if (listeners != null) {
					FieldChange<F> fieldChange = new FieldChange<>(oldValue, newValue, change);
					listeners.forEach(//
						l -> l.accept(fieldChange));
				}
			}
		}

		@Override
		public String toString() {
			return "as " + entityType;
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
			public void format(ObservableConfigParseSession session, C value, C previousValue, ConfigGetter config,
				Consumer<C> acceptedValue, Observable<?> until) {
				// We don't support calling set on a field like this
				// If this is from a copy event, we'll do the initial formatting
				if (value != null && config.getConfig(false) == null) {
					for (E v : value) {
						ObservableConfig newChild = config.getConfig(true).addChild(childName);
						elementFormat.format(session, v, null, __ -> newChild, __ -> {}, until);
					}
				}
				// Otherwise, there's nothing to do
				acceptedValue.accept(value);
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
					return (C) new ObservableConfigTransform.ObservableConfigValues<>(ctx.getSession(), ctx.getRoot(), ctx.getConfig(),
						() -> ctx.getConfig(true), elementType, elementFormat, childName, ctx.getUntil(), false, ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigValues<E>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
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

	static <E> ObservableConfigFormat<SyncValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String childName) {
		return new ObservableConfigFormat<SyncValueSet<E>>() {
			@Override
			public void format(ObservableConfigParseSession session, SyncValueSet<E> value, SyncValueSet<E> preValue,
				ConfigGetter config, Consumer<SyncValueSet<E>> acceptedValue, Observable<?> until) {
				// We don't support calling set on a field like this
				// In the case that the config is currently empty, this is trivial, and we'll support it for the sake of copying values
				if (value != null && config.getConfig(false) == null) {
					for (E v : value.getValues()) {
						ObservableConfig newChild = config.getConfig(true).addChild(childName);
						elementFormat.format(session, v, null, __ -> newChild, __ -> {}, until);
					}
				}
				// Otherwise, there's nothing to do
				acceptedValue.accept(preValue);
			}

			@Override
			public SyncValueSet<E> parse(ObservableConfigParseContext<SyncValueSet<E>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigEntityValues<>(ctx.getSession(), ctx.getRoot(), ctx.getConfig(),
						() -> ctx.getConfig(true), elementFormat, childName, ctx.getUntil(), false, ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigEntityValues<E>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}
		};
	}

	static <K, V> ObservableConfigFormat<ObservableMap<K, V>> ofMap(TypeToken<K> keyType, TypeToken<V> valueType, String keyName,
		String valueName, ObservableConfigFormat<K> keyFormat, ObservableConfigFormat<V> valueFormat) {
		return new ObservableConfigFormat<ObservableMap<K, V>>() {
			@Override
			public void format(ObservableConfigParseSession session, ObservableMap<K, V> value, ObservableMap<K, V> previousValue,
				ConfigGetter config, Consumer<ObservableMap<K, V>> acceptedValue, Observable<?> until) throws IllegalArgumentException {
				// We don't support calling set on a field like this
				// In the case that the config is currently empty, this is trivial, and we'll support it for the sake of copying values
				if (value != null && config.getConfig(false) == null) {
					for (Map.Entry<K, V> entry : value.entrySet()) {
						ObservableConfig newChild = config.getConfig(true).addChild(valueName);
						ObservableConfig keyChild = newChild.addChild(keyName);
						keyFormat.format(session, entry.getKey(), null, __ -> keyChild, __ -> {}, until);
						valueFormat.format(session, entry.getValue(), null, __ -> newChild, __ -> {}, until);
					}
				}
				// Otherwise, there's nothing to do
				acceptedValue.accept(previousValue);
			}

			@Override
			public ObservableMap<K, V> parse(ObservableConfigParseContext<ObservableMap<K, V>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigMap<>(ctx.getSession(), ctx.getRoot(), ctx.getConfig(),
						() -> ctx.getConfig(true), keyName, valueName, keyType, valueType, keyFormat, valueFormat, ctx.getUntil(), false,
						ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigMap<K, V>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}
		};
	}

	static <K, V> ObservableConfigFormat<ObservableMultiMap<K, V>> ofMultiMap(TypeToken<K> keyType, TypeToken<V> valueType, String keyName,
		String valueName, ObservableConfigFormat<K> keyFormat, ObservableConfigFormat<V> valueFormat) {
		return new ObservableConfigFormat<ObservableMultiMap<K, V>>() {
			@Override
			public void format(ObservableConfigParseSession session, ObservableMultiMap<K, V> value, ObservableMultiMap<K, V> previousValue,
				ConfigGetter config, Consumer<ObservableMultiMap<K, V>> acceptedValue, Observable<?> until)
					throws IllegalArgumentException {
				// We don't support calling set on a field like this
				// In the case that the config is currently empty, this is trivial, and we'll support it for the sake of copying values
				if (value != null && config.getConfig(false) == null) {
					for (MultiEntryHandle<K, V> entry : value.entrySet()) {
						for (V v : entry.getValues()) {
							ObservableConfig newChild = config.getConfig(true).addChild(valueName);
							ObservableConfig keyChild = newChild.addChild(keyName);
							keyFormat.format(session, entry.getKey(), null, __ -> keyChild, __ -> {}, until);
							valueFormat.format(session, v, null, __ -> newChild, __ -> {}, until);
						}
					}
				}
				acceptedValue.accept(previousValue);
			}

			@Override
			public ObservableMultiMap<K, V> parse(ObservableConfigParseContext<ObservableMultiMap<K, V>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigMultiMap<>(ctx.getSession(), ctx.getRoot(), ctx.getConfig(),
						() -> ctx.getConfig(true), keyName, valueName, keyType, valueType, keyFormat, valueFormat, ctx.getUntil(), false,
						ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigMultiMap<K, V>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}
		};
	}

	static <T> HeterogeneousFormat.Builder<T> heterogeneous(TypeToken<T> type) {
		return HeterogeneousFormat.build(type);
	}

	public class HeterogeneousFormat<T> implements ObservableConfigFormat<T> {
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

			public ObservableConfig.ObservableConfigPathElement getConfigFilter() {
				return configFilter;
			}

			public ObservableConfigFormat<T> getFormat() {
				return format;
			}

			public TypeToken<T> getType() {
				return type;
			}

			public Predicate<? super T> getValueFilter() {
				return valueFilter;
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

		HeterogeneousFormat(List<SubFormat<? extends T>> subFormats) {
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

		public List<SubFormat<? extends T>> getSubFormats() {
			return theSubFormats;
		}

		@Override
		public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config,
			Consumer<T> acceptedValue, Observable<?> until) throws IllegalArgumentException {
			SubFormat<? extends T> format = formatFor(value);
			if (value != null && format == null)
				throw new IllegalArgumentException("No sub-format found for value " + value.getClass().getName() + " " + value);
			if (value == null) {
				if (config.getConfig(false) != null) {
					config.getConfig(true).set("null", "true");
					config.getConfig(true).getAllContent().getValues().clear();
				}
				acceptedValue.accept(null);
				return;
			} else {
				config.getConfig(true).set("null", null);
				format.moldConfig(config.getConfig(true));
				((SubFormat<T>) format).format.format(session, value, format.applies(previousValue) ? previousValue : null, config,
					acceptedValue, until);
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
	}
}
