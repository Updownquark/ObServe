package org.observe.config;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.observe.config.ObservableConfigPath.ObservableConfigPathElement;
import org.observe.config.ObservableConfigTransform.ObservableConfigMultiMap;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.FieldChange;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
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

/**
 * A format that knows how to persist values to and parse them from {@link ObservableConfig} elements
 *
 * @param <E> The type of value to persist/parse
 */
public interface ObservableConfigFormat<E> {
	/** Persists text ({@link String}s) */
	public static ObservableConfigFormat<String> TEXT = ofQommonFormat(Format.TEXT, () -> null);
	/** Persists {@link Double}s */
	public static ObservableConfigFormat<Double> DOUBLE = ofQommonFormat(Format.doubleFormat("0.############E0"), () -> 0.0);
	/** Persists {@link Float}s */
	public static ObservableConfigFormat<Float> FLOAT = ofQommonFormat(Format.floatFormat("0.########E0"), () -> 0.0f);
	/** Persists {@link Long}s */
	public static ObservableConfigFormat<Long> LONG = ofQommonFormat(Format.LONG, () -> 0L);
	/** Persists {@link Integer}s */
	public static ObservableConfigFormat<Integer> INT = ofQommonFormat(Format.INT, () -> 0);
	/** Persists {@link Boolean}s */
	public static ObservableConfigFormat<Boolean> BOOLEAN = ofQommonFormat(Format.BOOLEAN, () -> false);
	/** Persists {@link Duration}s */
	public static ObservableConfigFormat<Duration> DURATION = ofQommonFormat(Format.DURATION, () -> Duration.ZERO);
	/** Persists {@link Instant}s */
	public static ObservableConfigFormat<Instant> DATE = ofQommonFormat(Format.date("ddMMyyyy HH:mm:ss.SSS"), () -> null);

	/** An accessor for a config element at a particular location in the hierarchy, even if such an element does not currently exist */
	interface ConfigGetter {
		/**
		 * @param createIfAbsent Whether to create the config (and any required ancestors) if it is not present
		 * @param trivial Whether to mark the config as trivial if it must be created for this call
		 * @return The config, or null if it does not exist (and createIfAbsent is false)
		 */
		ObservableConfig getConfig(boolean createIfAbsent, boolean trivial);

		/**
		 * @param childName The name of the child
		 * @return A getter for a config element that is a child of this getter's element
		 */
		default ConfigGetter forChild(String childName) {
			ConfigGetter parent = this;
			return (createIfAbsent, trivial) -> {
				if (createIfAbsent)
					return parent.getConfig(true, trivial).getChild(childName, true, child -> child.setTrivial(trivial));
				ObservableConfig config = parent.getConfig(false, false);
				return config == null ? null : config.getChild(childName);
			};
		}
	}

	/**
	 * @param session The session to associate the value with
	 * @param value The value to persist
	 * @param previousValue The value that was previously persisted to or parsed from the config
	 * @param config Accessor for the config to persist to
	 * @param acceptedValue Accepts the value after it is persisted--it may be different from the given value
	 * @param until An observable to release all listeners and resources associated with the persistence
	 * @throws IllegalArgumentException If the value could not be persisted
	 */
	void format(ObservableConfigParseSession session, E value, E previousValue, ConfigGetter config, Consumer<E> acceptedValue,
		Observable<?> until) throws IllegalArgumentException;

	/**
	 * @param ctx The context containing all information needed to parse the value
	 * @return The parsed value
	 * @throws ParseException If the value could not be parsed
	 */
	E parse(ObservableConfigParseContext<E> ctx) throws ParseException;

	/**
	 * @param value The value to test
	 * @return Whether the given value may be a value returned by {@link #parse(ObservableConfigParseContext)} with a null config
	 */
	boolean isDefault(E value);

	/** @param copied The config that was just copied, to be adjusted by this format if needed */
	void postCopy(ObservableConfig copied);

	/**
	 * Contains all context information that may be needed to parse values from config elements
	 *
	 * @param <E> The type to parse
	 */
	interface ObservableConfigParseContext<E> extends ConfigGetter {
		/** @return The lock enabling parsed values to interact with the config locking */
		Transactable getLock();

		/** @return The session to associate values with */
		ObservableConfigParseSession getSession();

		/** @return The config containing the information to parse the value from */
		ObservableValue<? extends ObservableConfig> getConfig();

		/** @return The config change that caused the need to re-parse the value */
		ObservableConfigEvent getChange();

		/** @return The observable to release resources and listeners */
		Observable<?> getUntil();

		/** @return The value that was previously persisted to or parsed from the config */
		E getPreviousValue();

		/** @return For reference formats, the observable to fire when parsing has finished and it's time to go retrieve reference values */
		Observable<?> findReferences();

		void linkedReference(E value);

		/**
		 * @param <V> The type to parse
		 * @param config The config value to parse
		 * @param create Creates the config if it does not exist
		 * @param change The change event causing the need for parsing, if any
		 * @param previousValue The previous value associated with the config
		 * @param delayedAccept Will receive the new value after it is parsed
		 * @return The new mapped context
		 */
		<V> ObservableConfigParseContext<V> map(ObservableValue<? extends ObservableConfig> config,
			Function<Boolean, ? extends ObservableConfig> create, ObservableConfigEvent change, V previousValue, Consumer<V> delayedAccept);

		/**
		 * Creates a context with these settings but for a child element of this context's config
		 *
		 * @param <V> The type to parse
		 * @param child Accesses or creates a child of this context's config
		 * @param childChange The change event for the child
		 * @param previousValue The previous value associated with the child
		 * @param delayedAccept Will receive the new value after it is parsed
		 * @return The new child context
		 */
		default <V> ObservableConfigParseContext<V> forChild(ConfigChildGetter child,
			ObservableConfigEvent childChange, V previousValue, Consumer<V> delayedAccept) {
			ObservableValue<? extends ObservableConfig> childConfig = getConfig().transform(ObservableConfig.class,
				tx -> tx.cache(false).map(LambdaUtils.printableFn(c -> {
					return child.getChild(c, false, false);
				}, child::toString, child)));
			Function<Boolean, ? extends ObservableConfig> childCreate = LambdaUtils.printableFn(trivial -> {
				return child.getChild(getConfig(true, trivial), true, trivial);
			}, () -> getConfig() + "." + child, null);
			return map(childConfig, childCreate, childChange, previousValue, delayedAccept);
		}

		/**
		 * Creates a context with these settings but for a child element of this context's config
		 *
		 * @param <V> The type to parse
		 * @param childName The name of the child config element
		 * @param previousValue The previous value associated with the child
		 * @param delayedAccept Will receive the new value after it is parsed
		 * @return The new child context
		 */
		default <V> ObservableConfigParseContext<V> forChild(String childName, V previousValue, Consumer<V> delayedAccept) {
			ObservableConfigEvent childChange;
			if (childName == null)
				childChange = getChange();
			else if (getChange() == null || getChange().relativePath.isEmpty()
				|| !getChange().relativePath.get(0).getName().equals(childName))
				childChange = null;
			else
				childChange = getChange().asFromChild();
			return forChild(new Impl.ConfigChildGetterWrapper((config, create, trivial) -> {
				if (config == null && !create)
					return null;
				return config.getChild(childName, create, child -> child.setTrivial(trivial));
			}, () -> childName), childChange, previousValue, delayedAccept);
		}

		/**
		 * Creates a context with these settings but for a child element of this context's config
		 *
		 * @param <V> The type to parse
		 * @param child The child config element
		 * @param previousValue The previous value associated with the child
		 * @param delayedAccept Will receive the new value after it is parsed
		 * @return The new child context
		 */
		default <V> ObservableConfigParseContext<V> forChild(ObservableConfig child, V previousValue, Consumer<V> delayedAccept) {
			return forChild(new Impl.ConfigChildGetterWrapper((parent, create, trivial) -> child, () -> "child"), null, previousValue,
				delayedAccept);
		}

		/**
		 * @param findRefs The new {@link #findReferences()} observable
		 * @return A context that is the same as this, but with the given reference observable
		 */
		ObservableConfigParseContext<E> withRefFinding(Observable<?> findRefs);
	}

	/**
	 * Used for creating {@link ObservableConfigParseContext#forChild(ConfigChildGetter, ObservableConfigEvent, Object, Consumer) mapped}
	 * contexts
	 */
	interface ConfigChildGetter {
		ObservableConfig getChild(ObservableConfig parent, boolean create, boolean trivial);
	}

	/**
	 * Creates a context for config parsing
	 *
	 * @param <E> The type to parse
	 * @param lock The lock to support transactionality in parsed structures
	 * @param session The session to associate parsed values with
	 * @param root The root config to parse under
	 * @param config The dynamic config element to get information from
	 * @param create Creates the target config if it does not exist. The argument is whether the new config should be trivial or not if it
	 *        must be created.
	 * @param change The config change event that is causing the need for re-parsing, or null if this is an initial parse request
	 * @param until An observable that, when fired, will release all resources and listeners created by the parsing
	 * @param previousValue The previous value associated with the config, or null for initial parsing
	 * @param findReferences An observable to trigger reference matching. This cannot always be done inline, because references may become
	 *        available as a result of parsing.
	 * @param delayedAccept Accepts the parsed value before it is returned. Also used for reference parsing
	 * @return The context to pass to {@link #parse(ObservableConfigParseContext)}
	 */
	static <E> ObservableConfigParseContext<E> ctxFor(Transactable lock, ObservableConfigParseSession session,
		ObservableValue<? extends ObservableConfig> config, Function<Boolean, ? extends ObservableConfig> create,
		ObservableConfigEvent change, Observable<?> until, E previousValue, Observable<?> findReferences, Consumer<E> delayedAccept) {
		return new Impl.DefaultOCParseContext<>(lock, session, config, create, change, until, previousValue, findReferences, delayedAccept);
	}

	/**
	 * @param <T> The type to format
	 * @param format The Qommons format to wrap
	 * @param defaultValue The value to return if the config is missing or value-less
	 * @return A format that simply sets the value of a config to the text-formatted string
	 */
	static <T> Impl.SimpleConfigFormat<T> ofQommonFormat(Format<T> format, Supplier<? extends T> defaultValue) {
		return new Impl.SimpleConfigFormat<>(format, defaultValue);
	}

	/**
	 * @param <E> The enum type to format
	 * @param type The enum class
	 * @param defaultValue The default value to return if the config is missing or value-less
	 * @return A format that simply sets the value of a config to the enum name
	 */
	static <E extends Enum<?>> ObservableConfigFormat<E> enumFormat(Class<E> type, Supplier<? extends E> defaultValue) {
		return new Impl.SimpleConfigFormat<>(Format.enumFormat(type), defaultValue);
	}

	/** @return A format that does not parse, persist or populate a value. E.g. For transient fields. */
	static ObservableConfigFormat<Object> nullFormat() {
		return new Impl.NullFormat();
	}

	/**
	 * Builds a reference format, a format that does not persist all information about its target value, but only identity information used
	 * to retrieve it from a different source.
	 *
	 * @param <T> The type of value to retrieve
	 */
	public static class ReferenceFormatBuilder<T> {
		private final Function<QuickMap<String, Object>, ? extends T> theRetriever;
		private final Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> theMultiRetriever;
		private final Supplier<? extends T> theRetreiverDefault;
		private BooleanSupplier isRetrieverReady;
		private final Map<String, Impl.ReferenceFormat.FormattedField<? super T, ?>> theFields;

		ReferenceFormatBuilder(Function<QuickMap<String, Object>, ? extends T> retriever) {
			theRetriever = retriever;
			theMultiRetriever = null;
			theRetreiverDefault = null;
			theFields = new LinkedHashMap<>();
		}

		ReferenceFormatBuilder(Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> multiRetriever,
			Supplier<? extends T> defaultValue) {
			theRetriever = null;
			theMultiRetriever = multiRetriever;
			theRetreiverDefault = defaultValue == null ? () -> null : defaultValue;
			theFields = new LinkedHashMap<>();
		}

		/**
		 * Configures an identity field to store and use to retrieve the value
		 *
		 * @param <F> The type of the field
		 * @param childName The name of the config element to store the field in
		 * @param field Accessor for the field
		 * @param format The format for the field
		 * @return This builder
		 */
		public <F> ReferenceFormatBuilder<T> withField(String childName, Function<? super T, F> field, ObservableConfigFormat<F> format) {
			if (theFields.put(childName, new Impl.ReferenceFormat.FormattedField<T, F>(field, format)) != null)
				throw new IllegalArgumentException("Multiple fields named " + childName + " are not supported");
			return this;
		}

		/**
		 * @param retrieverReady Tells the format that it can attempt to retrieve the reference
		 * @return This builder
		 */
		public ReferenceFormatBuilder<T> withRetrieverReady(BooleanSupplier retrieverReady) {
			isRetrieverReady = retrieverReady;
			return this;
		}

		/** @return The new reference format */
		public ObservableConfigFormat<T> build() {
			QuickMap<String, Impl.ReferenceFormat.FormattedField<? super T, ?>> fields = QuickMap
				.of(theFields, StringUtils.DISTINCT_NUMBER_TOLERANT).unmodifiable();
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
			return new Impl.ReferenceFormat<>(fields, retriever, isRetrieverReady);
		}
	}

	/**
	 * Builds a reference format, a format that does not persist all information about its target value, but only identity information used
	 * to retrieve it from a different source.
	 *
	 * @param <T> The type of value to retrieve
	 * @param values The collection of values that the referenced value is in
	 * @param defaultValue The value to return if no value in the collection matches the stored reference
	 * @return The reference format
	 */
	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Iterable<? extends T> values, Supplier<? extends T> defaultValue) {
		if (values instanceof Identifiable)
			return buildReferenceFormat(LambdaUtils.printableFn(__ -> values, () -> ((Identifiable) values).getIdentity().toString(),
				((Identifiable) values).getIdentity()), defaultValue);
		else
			return buildReferenceFormat(LambdaUtils.printableFn(__ -> values, values::toString, values), defaultValue);
	}

	/**
	 * Builds a reference format, a format that does not persist all information about its target value, but only identity information used
	 * to retrieve it from a different source.
	 *
	 * @param <T> The type of value to retrieve
	 * @param retriever Retrieves the value from the data source by its identifying fields
	 * @param defaultValue The value to return if no value in the data source matches the stored reference
	 * @return The reference format
	 */
	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Function<QuickMap<String, Object>, ? extends T> retriever) {
		return new ReferenceFormatBuilder<>(retriever);
	}

	/**
	 * Builds a reference format, a format that does not persist all information about its target value, but only identity information used
	 * to retrieve it from a different source.
	 *
	 * @param <T> The type of value to retrieve
	 * @param retriever Retrieves a collection of possible values from the data source, given the value's identifying fields
	 * @param defaultValue The value to return if no value in the data source matches the stored reference
	 * @return The reference format
	 */
	static <T> ReferenceFormatBuilder<T> buildReferenceFormat(Function<QuickMap<String, Object>, ? extends Iterable<? extends T>> retriever,
		Supplier<? extends T> defaultValue) {
		return new ReferenceFormatBuilder<>(retriever, defaultValue);
	}

	/**
	 * @param <T> The type of the parent reference
	 * @param type The type of the parent reference
	 * @return A format the does not persist the value, but instead populates it with the value of the nearest parent entity in the config
	 *         hierarchy matching the given type
	 */
	static <T> ObservableConfigFormat<T> parentReference(TypeToken<T> type) {
		return new Impl.ParentReferenceFormat<>(type);
	}

	/**
	 * A creator for some kind of config-backed object with named fields
	 *
	 * @param <E> The type of value to create
	 */
	public interface ConfigCreator<E> {
		/** @return The indexes of fields that must be set */
		Set<Integer> getRequiredFields();

		/**
		 * @param fieldName The name of the field to set
		 * @param value The value for the field
		 * @return This creator
		 * @throws IllegalArgumentException If the given value cannot be set for the given field in this creator
		 */
		ConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

		/**
		 * @param fieldIndex The index of the field to set
		 * @param value The value for the field
		 * @return This creator
		 * @throws IllegalArgumentException If the given value cannot be set for the given field in this creator
		 */
		ConfigCreator<E> with(int fieldIndex, Object value) throws IllegalArgumentException;

		/**
		 * @param config The config to modify with the field values set in this creator
		 * @param until The until observable to release resources and listeners
		 * @return The new value
		 */
		E create(ObservableConfig config, Observable<?> until);
	}

	/**
	 * Creates a new entity in a config-backed entity set
	 *
	 * @param <E> The type of the entity
	 */
	public interface EntityConfigCreator<E> extends ConfigCreator<E> {
		/** @return The configured entity type of the entity to create */
		EntityConfiguredValueType<E> getEntityType();

		@Override
		Set<Integer> getRequiredFields();

		@Override
		EntityConfigCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

		@Override
		EntityConfigCreator<E> with(int fieldIndex, Object value) throws IllegalArgumentException;

		/**
		 * @param <F> The type of the field
		 * @param field The field to set
		 * @param value The value for the field in the new entity
		 * @return This creator
		 * @throws IllegalArgumentException If the given value cannot be set for the given field in this creator for any reason
		 */
		<F> EntityConfigCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException;

		/**
		 * @param <F> The type of the field
		 * @param fieldGetter The getter for the field to set
		 * @param value The value for the field in the new entity
		 * @return This creator
		 * @throws IllegalArgumentException If the given value cannot be set for the given field in this creator for any reason
		 */
		<F> EntityConfigCreator<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException;
	}

	/**
	 * Persists/parses entity structures from config. This format will work for types administrable by {@link EntityReflector}.
	 *
	 * @param <E> The type of the entity
	 */
	public interface EntityConfigFormat<E> extends ObservableConfigFormat<E> {
		/**
		 * The key by which the config element may be retrieved from a config-backed entity
		 *
		 * @see #getConfig(Object)
		 * @see EntityReflector#getAssociated(Object, Object)
		 */
		Object ENTITY_CONFIG_KEY = new Object() {
			@Override
			public String toString() {
				return "EntityConfig";
			}
		};

		/**
		 * @param entity The config-backed entity
		 * @return The config element where the entity is persisted
		 */
		static ObservableConfig getConfig(Object entity) {
			return (ObservableConfig) EntityReflector.getReflector(entity).getAssociated(entity, ENTITY_CONFIG_KEY);
		}

		/** @return The configured entity type of the entity persisted by this format */
		EntityConfiguredValueType<E> getEntityType();

		/**
		 * @param <F> The type of the field
		 * @param field The field to get the format for
		 * @return The config format used to persist the value of the given field in this format's entities
		 */
		<F> ObservableConfigFormat<F> getFieldFormat(ConfiguredValueField<E, F> field);

		/**
		 * @param field The field to get configuration for
		 * @return The name of the child configs that will be used to store the given field in entity configs
		 */
		String getChildName(ConfiguredValueField<E, ?> field);

		/**
		 * Creates a new entity
		 *
		 * @param <E2> The sub-type of entity to create
		 * @param session The session to associate the entity with
		 * @param subType The sub-type of entity to create
		 * @return An entity creator to create the new entity
		 */
		<E2 extends E> EntityConfigCreator<E2> create(ObservableConfigParseSession session, TypeToken<E2> subType);
	}

	/**
	 * Builds an entity config format
	 *
	 * @param <E> The type of entity to format/parse
	 */
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

		/**
		 * @param <E2> The sub-type of entity to format specially
		 * @param type The sub-type of entity to format specially
		 * @param subFormat Builds a format for the entity sub-type
		 * @return This builder
		 */
		public <E2 extends E> EntityFormatBuilder<E> withSubType(TypeToken<E2> type,
			Function<EntitySubFormatBuilder<E2>, EntitySubFormat<E2>> subFormat) {
			if (!theEntityType.getType().isAssignableFrom(type))
				throw new IllegalArgumentException(type + " is not a sub-type of " + theEntityType);
			theSubFormats.add(subFormat.apply(new EntitySubFormatBuilder<>(type, theFormatSet)));
			return this;
		}

		/**
		 * @param fieldIndex The index of the field
		 * @param format The format to use for the field
		 * @return This builder
		 */
		public EntityFormatBuilder<E> withFieldFormat(int fieldIndex, ObservableConfigFormat<?> format) {
			theFieldFormats.put(fieldIndex, format);
			return this;
		}

		/**
		 * @param fieldName The name of the field
		 * @param format The format to use for the field
		 * @return This builder
		 */
		public EntityFormatBuilder<E> withFieldFormat(String fieldName, ObservableConfigFormat<?> format) {
			return withFieldFormat(theFieldFormats.keyIndex(fieldName), format);
		}

		/**
		 * @param <F> The type of the field
		 * @param field The getter of the field
		 * @param format The format to use for the field
		 * @return This builder
		 */
		public <F> EntityFormatBuilder<E> withFieldFormat(Function<? super E, F> field, ObservableConfigFormat<F> format) {
			return withFieldFormat(theEntityType.getField(field).getIndex(), format);
		}

		/**
		 * @param fieldIndex The index of the field
		 * @param childName The name of the child element in which the field will be stored
		 * @return This builder
		 */
		public EntityFormatBuilder<E> withFieldChildName(int fieldIndex, String childName) {
			theFieldChildNames.put(fieldIndex, childName);
			return this;
		}

		/**
		 * @param fieldName The name of the field
		 * @param childName The name of the child element in which the field will be stored
		 * @return This builder
		 */
		public EntityFormatBuilder<E> withFieldChildName(String fieldName, String childName) {
			return withFieldChildName(theFieldChildNames.keyIndex(fieldName), childName);
		}

		/**
		 * @param field The getter of the field
		 * @param childName The name of the child element in which the field will be stored
		 * @return This builder
		 */
		public EntityFormatBuilder<E> withFieldChildName(Function<? super E, ?> field, String childName) {
			return withFieldChildName(theEntityType.getField(field).getIndex(), childName);
		}

		/** @return The new entity format */
		public EntityConfigFormat<E> build() {
			QuickMap<String, ObservableConfigFormat<?>> formats = theFieldFormats.copy();
			for (int i = 0; i < formats.keySize(); i++) {
				if (formats.get(i) == null) {
					if (theEntityType.getFields().get(i).isTransient())
						formats.put(i, new Impl.NullFormat());
					else if (theEntityType.getFields().get(i).isParentReference())
						formats.put(i, new Impl.ParentReferenceFormat<>(theEntityType.getFields().get(i).getFieldType()));
					else
						formats.put(i, theFormatSet.getConfigFormat(theEntityType.getFields().get(i)));
				}
			}
			QuickMap<String, String> childNames = theFieldChildNames.copy();
			for (int i = 0; i < childNames.keySize(); i++) {
				if (childNames.get(i) == null)
					childNames.put(i, StringUtils.parseByCase(theEntityType.getFields().keySet().get(i), true).toKebabCase());
			}
			return new Impl.EntityConfigFormatImpl<>(theEntityType, QommonsUtils.unmodifiableCopy(theSubFormats), formats.unmodifiable(),
				childNames.unmodifiable());
		}
	}

	/**
	 * Builds a sub-format for a sub-type of entity in an entity format
	 *
	 * @param <T> The sub-type of entity to format/parse
	 * @see EntityFormatBuilder#withSubType(TypeToken, Function)
	 */
	public static class EntitySubFormatBuilder<T> {
		private final TypeToken<T> theType;
		private final ObservableConfigFormatSet theFormats;
		private Predicate<? super T> theValueFilter;
		private EntityConfigFormat<T> theFormat;

		EntitySubFormatBuilder(TypeToken<T> type, ObservableConfigFormatSet formats) {
			theType = type;
			theFormats = formats;
		}

		/**
		 * @param valueFilter The filter for entities to use this format for
		 * @return This builder
		 */
		public EntitySubFormatBuilder<T> withValueFilter(Predicate<? super T> valueFilter) {
			theValueFilter = valueFilter;
			return this;
		}

		/**
		 * @param format The format to use for entities of this sub-type
		 * @return This builder
		 */
		public EntitySubFormatBuilder<T> withFormat(EntityConfigFormat<T> format) {
			theFormat = format;
			return this;
		}

		/**
		 * @param configName The name of the config to use for entities of this sub-type
		 * @return The new sub-format
		 */
		public EntitySubFormat<T> build(String configName) {
			if (theFormat == null)
				theFormat = theFormats.getEntityFormat(theType);
			return new EntitySubFormat<>(ObservableConfigPath.parsePathElement(configName), theFormat, theType, theValueFilter);
		}

		/**
		 * @param configName The name of the config to use for entities of this sub-type
		 * @param path Configures additional attributes to set on config elements representing entities of this sub-type
		 * @return The new sub-format
		 */
		public EntitySubFormat<T> build(String configName, Function<PathElementBuilder, ObservableConfigPathElement> path) {
			if (theFormat == null)
				theFormat = theFormats.getEntityFormat(theType);
			return new EntitySubFormat<>(path.apply(new PathElementBuilder(configName)), theFormat, theType, theValueFilter);
		}
	}

	/**
	 * Represents a sub-type in an entity config format. Entities matching this sub-format's filter will be formatted by this format instead
	 * of by its parent
	 *
	 * @param <E> The sub-type of entity to format/parse
	 */
	public class EntitySubFormat<E> {
		final ObservableConfigPathElement configFilter;
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

	/**
	 * Configures attributes that the element representing a value must have. These attributes will not contribute information to the value
	 * itself, but only serve as a marker/filter for the config element.
	 */
	public class PathElementBuilder {
		private final String theName;
		private final Map<String, String> theAttributes;

		PathElementBuilder(String name) {
			theName = name;
			theAttributes = new LinkedHashMap<>();
		}

		/**
		 * Adds a static attribute to the config element
		 *
		 * @param attributeName The name of the attribute
		 * @param attributeValue The value of the attribute
		 * @return This builder
		 */
		public PathElementBuilder withAttribute(String attributeName, String attributeValue) {
			theAttributes.put(attributeName, attributeValue);
			return this;
		}

		/** @return The configured path element */
		public ObservableConfigPathElement build() {
			boolean multi = theName.equals(ObservableConfigPath.ANY_NAME);
			return new ObservableConfigPathElement(multi ? "" : theName, theAttributes, multi, false);
		}
	}

	/**
	 * Builds an entity format
	 *
	 * @param <E> The type of entity to parse
	 * @param entityType The run-time type of entity to parse
	 * @param formats The format set to use to get formats for entity fields
	 * @return The entity format builder
	 */
	static <E> EntityFormatBuilder<E> buildEntities(TypeToken<E> entityType, ObservableConfigFormatSet formats) {
		return buildEntities(formats.getEntityType(entityType), formats);
	}

	/**
	 * Builds an entity format
	 *
	 * @param <E> The type of entity to parse
	 * @param entityType The configured entity type of the entity to parse
	 * @param formats The format set to use to get formats for entity fields
	 * @return The entity format builder
	 */
	static <E> EntityFormatBuilder<E> buildEntities(EntityConfiguredValueType<E> entityType, ObservableConfigFormatSet formats) {
		return new EntityFormatBuilder<>(entityType, formats);
	}

	/**
	 * Simple map entry value for formatting
	 *
	 * @param <K> The key type of the map entry
	 * @param <V> the value type of the map entry
	 */
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

	/**
	 * Creates a format for a {@link Collection}
	 *
	 * @param <E> The type of elements in the collection
	 * @param <C> The collection type
	 * @param collectionType The collection type
	 * @param elementFormat The format for the collection elements
	 * @param parentName The name of the collection element
	 * @param childName The name for config elements representing each collection element
	 * @return The collection format
	 */
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
				if (value != null && config.getConfig(false, true) == null) {
					for (E v : value) {
						ObservableConfig newChild = config.getConfig(true, true).addChild(childName);
						elementFormat.format(session, v, null, (__, ___) -> newChild, __ -> {
						}, until);
					}
				}
				// Otherwise, there's nothing to do
				acceptedValue.accept(value);
			}

			@Override
			public C parse(ObservableConfigParseContext<C> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return (C) new ObservableConfigTransform.ObservableConfigValues<>(ctx.getLock(), ctx.getSession(), ctx.getConfig(),
						trivial -> ctx.getConfig(true, trivial), elementType, elementFormat, childName, ctx.getUntil(), false,
						ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigValues<E>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}

			@Override
			public boolean isDefault(C value) {
				return value instanceof ObservableConfigTransform.ObservableConfigValue;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
				for (ObservableConfig child : copied.getContent()) {
					if (child.getName().equals(childName))
						elementFormat.postCopy(child);
				}
			}
		};
	}

	/**
	 * Creates a format for {@link SyncValueSet}s
	 *
	 * @param <E> The type of entity in the set
	 * @param elementFormat The format for the set elements
	 * @param childName The name for config elements representing each element in the set
	 * @return The value set format
	 */
	static <E> ObservableConfigFormat<SyncValueSet<E>> ofEntitySet(EntityConfigFormat<E> elementFormat, String childName) {
		return new ObservableConfigFormat<SyncValueSet<E>>() {
			@Override
			public void format(ObservableConfigParseSession session, SyncValueSet<E> value, SyncValueSet<E> preValue, ConfigGetter config,
				Consumer<SyncValueSet<E>> acceptedValue, Observable<?> until) {
				// We don't support calling set on a field like this
				// In the case that the config is currently empty, this is trivial, and we'll support it for the sake of copying values
				if (value != null && config.getConfig(false, true) == null) {
					for (E v : value.getValues()) {
						ObservableConfig newChild = config.getConfig(true, true).addChild(childName);
						elementFormat.format(session, v, null, (__, ___) -> newChild, __ -> {
						}, until);
					}
				}
				// Otherwise, there's nothing to do
				acceptedValue.accept(preValue);
			}

			@Override
			public SyncValueSet<E> parse(ObservableConfigParseContext<SyncValueSet<E>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigEntityValues<>(ctx.getLock(), ctx.getSession(), ctx.getConfig(),
						trivial -> ctx.getConfig(true, trivial), elementFormat, childName, ctx.getUntil(), false, ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigEntityValues<E>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}

			@Override
			public boolean isDefault(SyncValueSet<E> value) {
				return value instanceof ObservableConfigTransform.ObservableConfigEntityValues;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
				for (ObservableConfig child : copied.getContent()) {
					if (child.getName().equals(childName))
						elementFormat.postCopy(child);
				}
			}
		};
	}

	/**
	 * Creates a format for {@link ObservableMap}s
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @param keyName The name of the key element
	 * @param valueName The name of the value element
	 * @param keyFormat The format for map keys
	 * @param valueFormat The format for map values
	 * @return The map format for
	 */
	static <K, V> ObservableConfigFormat<ObservableMap<K, V>> ofMap(TypeToken<K> keyType, TypeToken<V> valueType, String keyName,
		String valueName, ObservableConfigFormat<K> keyFormat, ObservableConfigFormat<V> valueFormat) {
		Impl.EntryFormat<K, V> entryFormat = new Impl.EntryFormat<>(true, keyName, valueName, keyType, valueType, keyFormat, valueFormat);
		return new ObservableConfigFormat<ObservableMap<K, V>>() {
			@Override
			public void format(ObservableConfigParseSession session, ObservableMap<K, V> value, ObservableMap<K, V> previousValue,
				ConfigGetter config, Consumer<ObservableMap<K, V>> acceptedValue, Observable<?> until) throws IllegalArgumentException {
				// We don't support calling set on a field like this
				// In the case that the config is currently empty, this is trivial, and we'll support it for the sake of copying values
				if (value != null && config.getConfig(false, true) == null) {
					for (Map.Entry<K, V> entry : value.entrySet()) {
						ObservableConfig newChild = config.getConfig(true, true).addChild(valueName);
						ObservableConfig keyChild = newChild.addChild(keyName);
						keyFormat.format(session, entry.getKey(), null, (__, ___) -> keyChild, __ -> {
						}, until);
						valueFormat.format(session, entry.getValue(), null, (__, ___) -> newChild, __ -> {
						}, until);
					}
				}
				// Otherwise, there's nothing to do
				acceptedValue.accept(previousValue);
			}

			@Override
			public ObservableMap<K, V> parse(ObservableConfigParseContext<ObservableMap<K, V>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigMap<>(ctx.getLock(), ctx.getSession(), ctx.getConfig(),
						trivial -> ctx.getConfig(true, trivial), entryFormat, ctx.getUntil(), false, ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigMap<K, V>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}

			@Override
			public boolean isDefault(ObservableMap<K, V> value) {
				return value instanceof ObservableConfigTransform.ObservableConfigMap;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
				for (ObservableConfig child : copied.getContent()) {
					if (child.getName().equals(valueName))
						entryFormat.postCopy(child);
				}
			}
		};
	}

	/**
	 * Creates a format for {@link ObservableMultiMap}s
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @param keyName The name of the key element
	 * @param valueName The name of the value element
	 * @param keyFormat The format for map keys
	 * @param valueFormat The format for map values
	 * @return The multi-map format for
	 */
	static <K, V> ObservableConfigFormat<ObservableMultiMap<K, V>> ofMultiMap(TypeToken<K> keyType, TypeToken<V> valueType, String keyName,
		String valueName, ObservableConfigFormat<K> keyFormat, ObservableConfigFormat<V> valueFormat) {
		Impl.EntryFormat<K, V> entryFormat = new Impl.EntryFormat<>(true, keyName, valueName, keyType, valueType, keyFormat, valueFormat);
		return new ObservableConfigFormat<ObservableMultiMap<K, V>>() {
			@Override
			public void format(ObservableConfigParseSession session, ObservableMultiMap<K, V> value, ObservableMultiMap<K, V> previousValue,
				ConfigGetter config, Consumer<ObservableMultiMap<K, V>> acceptedValue, Observable<?> until)
					throws IllegalArgumentException {
				// We don't support calling set on a field like this
				// In the case that the config is currently empty, this is trivial, and we'll support it for the sake of copying values
				if (value != null && config.getConfig(false, true) == null) {
					for (MultiEntryHandle<K, V> entry : value.entrySet()) {
						for (V v : entry.getValues()) {
							ObservableConfig newChild = config.getConfig(true, true).addChild(valueName);
							ObservableConfig keyChild = newChild.addChild(keyName);
							keyFormat.format(session, entry.getKey(), null, (__, ___) -> keyChild, __ -> {
							}, until);
							valueFormat.format(session, v, null, (__, ___) -> newChild, __ -> {
							}, until);
						}
					}
				}
				acceptedValue.accept(previousValue);
			}

			@Override
			public ObservableMultiMap<K, V> parse(ObservableConfigParseContext<ObservableMultiMap<K, V>> ctx) throws ParseException {
				if (ctx.getPreviousValue() == null) {
					return new ObservableConfigTransform.ObservableConfigMultiMap<>(ctx.getLock(), ctx.getSession(), ctx.getConfig(),
						trivial -> ctx.getConfig(true, trivial), entryFormat, ctx.getUntil(), false, ctx.findReferences());
				} else {
					((ObservableConfigTransform.ObservableConfigMultiMap<K, V>) ctx.getPreviousValue()).onChange(ctx.getChange());
					return ctx.getPreviousValue();
				}
			}

			@Override
			public boolean isDefault(ObservableMultiMap<K, V> value) {
				return value instanceof ObservableConfigMultiMap;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
				for (ObservableConfig child : copied.getContent()) {
					if (child.getName().equals(valueName))
						entryFormat.postCopy(child);
				}
			}
		};
	}

	/**
	 * Builds a {@link HeterogeneousFormat}
	 *
	 * @param <T> The parent type
	 * @param type The parent type of all values that the format should handle
	 * @return The builder for the format
	 */
	static <T> HeterogeneousFormat.Builder<T> heterogeneous(TypeToken<T> type) {
		return HeterogeneousFormat.build(type);
	}

	/**
	 * A format that can handle formatting of values of various types independently
	 *
	 * @param <T> The parent type of all values that this format can handle
	 */
	public class HeterogeneousFormat<T> implements ObservableConfigFormat<T> {
		/**
		 * @param <T> The parent type
		 * @param type The parent type of all values that the format should handle
		 * @return The builder for the format
		 */
		public static <T> Builder<T> build(TypeToken<T> type) {
			return new Builder<>(type);
		}

		/**
		 * A builder for a {@link HeterogeneousFormat}
		 *
		 * @param <T> The parent type of all values that the format should handle
		 */
		public static class Builder<T> {
			private final TypeToken<T> theType;
			private final List<SubFormat<? extends T>> theSubFormats;

			Builder(TypeToken<T> type) {
				theType = type;
				theSubFormats = new LinkedList<>();
			}

			/**
			 * @param <T2> The sub-type to handle specially
			 * @param type The sub-type to handle specially
			 * @param subFormat Configures the sub-format for the given type
			 * @return This builder
			 */
			public <T2 extends T> Builder<T> with(TypeToken<T2> type, Function<SubFormatBuilder<T2>, SubFormat<T2>> subFormat) {
				if (!theType.isAssignableFrom(type))
					throw new IllegalArgumentException(type + " is not a sub-type of " + theType);
				theSubFormats.add(subFormat.apply(new SubFormatBuilder<>(type)));
				return this;
			}

			/** @return The heterogeneous format */
			public HeterogeneousFormat<T> build() {
				return new HeterogeneousFormat<>(QommonsUtils.unmodifiableCopy(theSubFormats));
			}
		}

		/**
		 * Configures a sub-format in a builder for a {@link HeterogeneousFormat}
		 *
		 * @param <T> The sub-type that the format should handle
		 */
		public static class SubFormatBuilder<T> {
			private final TypeToken<T> theType;
			private Predicate<? super T> theValueFilter;

			SubFormatBuilder(TypeToken<T> type) {
				theType = type;
			}

			/**
			 * @param valueFilter A filter for values that should be handled by this sub-format
			 * @return This builder
			 */
			public SubFormatBuilder<T> withValueFilter(Predicate<? super T> valueFilter) {
				theValueFilter = valueFilter;
				return this;
			}

			/**
			 * @param config The config name of the sub-type
			 * @param format The format for the sub-type
			 * @return The sub-format
			 */
			public SubFormat<T> build(String config, ObservableConfigFormat<T> format) {
				return new SubFormat<>(ObservableConfigPath.parsePathElement(config), format, theType, theValueFilter);
			}

			/**
			 * @param configName The config name of the sub-type
			 * @param path Configures attributes for all members of this sub-type
			 * @param format The format for the sub-type
			 * @return The sub-format
			 */
			public SubFormat<T> build(String configName, Function<PathElementBuilder, ObservableConfigPathElement> path,
				ObservableConfigFormat<T> format) {
				return new SubFormat<>(path.apply(new PathElementBuilder(configName)), format, theType, theValueFilter);
			}
		}

		/**
		 * A sub-format in a {@link HeterogeneousFormat}
		 *
		 * @param <T> The sub-type that this sub-format is for
		 */
		public static class SubFormat<T> {
			final ObservableConfigPathElement configFilter;
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

			/** @return Determines whether a config element should be parsed by this sub-format */
			public ObservableConfigPathElement getConfigFilter() {
				return configFilter;
			}

			/** @return The format for this sub-format */
			public ObservableConfigFormat<T> getFormat() {
				return format;
			}

			/** @return The type of values handled by this sub-format */
			public TypeToken<T> getType() {
				return type;
			}

			/** @return The filter for values handled by this sub-format */
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

		/** @return All sub-formats in this format */
		public List<SubFormat<? extends T>> getSubFormats() {
			return theSubFormats;
		}

		@Override
		public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config, Consumer<T> acceptedValue,
			Observable<?> until) throws IllegalArgumentException {
			SubFormat<? extends T> format = formatFor(value);
			if (value != null && format == null)
				throw new IllegalArgumentException("No sub-format found for value " + value.getClass().getName() + " " + value);
			if (value == null) {
				if (config.getConfig(false, false) != null) {
					config.getConfig(true, false).set("null", "true");
					config.getConfig(true, false).getAllContent().getValues().clear();
				}
				acceptedValue.accept(null);
				return;
			} else {
				config.getConfig(true, false).set("null", null);
				format.moldConfig(config.getConfig(true, false));
				((SubFormat<T>) format).format.format(session, value, format.applies(previousValue) ? previousValue : null, config,
					acceptedValue, until);
			}
		}

		@Override
		public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
			ObservableConfig c = ctx.getConfig(false, false);
			if (c == null || "true".equals(c.get("null")))
				return null;
			SubFormat<? extends T> format = formatFor(c);
			if (format == null)
				throw new ParseException("No sub-format found matching " + c, 0);
			return ((SubFormat<T>) format).format.parse(ctx.forChild((parent, create, trivial) -> {
				if (create)
					format.moldConfig(parent);
				return parent;
			}, ctx.getChange(), format.applies(ctx.getPreviousValue()) ? ctx.getPreviousValue() : null, ctx::linkedReference));
		}

		@Override
		public boolean isDefault(T value) {
			return value == null;
		}

		@Override
		public void postCopy(ObservableConfig copied) {
			SubFormat<? extends T> format = formatFor(copied);
			if (format != null)
				((SubFormat<T>) format).format.postCopy(copied);
		}
	}

	/** Implementations used by static factory methods in {@link ObservableConfigFormat} */
	static class Impl {
		static class DefaultOCParseContext<E> implements ObservableConfigParseContext<E> {
			private final Transactable theLock;
			private final ObservableConfigParseSession theSession;
			private final ObservableValue<? extends ObservableConfig> theConfig;
			private final Function<Boolean, ? extends ObservableConfig> theCreate;
			private final ObservableConfigEvent theChange;
			private final Observable<?> theUntil;
			private final E thePreviousValue;
			private final Observable<?> findReferences;
			private final Consumer<E> theDelayedAccept;

			public DefaultOCParseContext(Transactable lock, ObservableConfigParseSession session,
				ObservableValue<? extends ObservableConfig> config, Function<Boolean, ? extends ObservableConfig> create,
				ObservableConfigEvent change, Observable<?> until, E previousValue, Observable<?> findReferences,
				Consumer<E> delayedAccept) {
				theLock = lock;
				theSession = session;
				theConfig = config;
				theCreate = create;
				theChange = change;
				theUntil = until;
				thePreviousValue = previousValue;
				this.findReferences = findReferences;
				this.theDelayedAccept = delayedAccept;
			}

			@Override
			public Transactable getLock() {
				return theLock;
			}

			@Override
			public ObservableConfigParseSession getSession() {
				return theSession;
			}

			@Override
			public ObservableValue<? extends ObservableConfig> getConfig() {
				return theConfig;
			}

			@Override
			public ObservableConfig getConfig(boolean createIfAbsent, boolean trivial) {
				ObservableConfig c = theConfig.get();
				if (c == null && createIfAbsent)
					c = theCreate.apply(trivial);
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
				if (theDelayedAccept != null)
					theDelayedAccept.accept(value);
			}

			@Override
			public <V> ObservableConfigParseContext<V> map(ObservableValue<? extends ObservableConfig> config,
				Function<Boolean, ? extends ObservableConfig> create, ObservableConfigEvent change, V previousValue,
				Consumer<V> delayedAccept) {
				return new DefaultOCParseContext<>(theLock, theSession, config, create, change, theUntil, previousValue, findReferences,
					delayedAccept);
			}

			@Override
			public ObservableConfigParseContext<E> withRefFinding(Observable<?> findRefs) {
				return new DefaultOCParseContext<>(theLock, theSession, theConfig, theCreate, theChange, theUntil, thePreviousValue,
					findRefs, theDelayedAccept);
			}
		}

		static class ConfigChildGetterWrapper implements ConfigChildGetter {
			private final ConfigChildGetter theWrapped;
			private final Supplier<String> toString;

			ConfigChildGetterWrapper(ConfigChildGetter wrapped, Supplier<String> toString) {
				theWrapped = wrapped;
				this.toString = toString;
			}

			@Override
			public ObservableConfig getChild(ObservableConfig parent, boolean create, boolean trivial) {
				return theWrapped.getChild(parent, create, trivial);
			}

			@Override
			public int hashCode() {
				return theWrapped.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (obj instanceof ConfigChildGetterWrapper)
					return equals(((ConfigChildGetterWrapper) obj).theWrapped);
				else if (obj instanceof ConfigChildGetter)
					return theWrapped.equals(obj);
				else
					return false;
			}

			@Override
			public String toString() {
				return toString.get();
			}
		}

		static class SimpleConfigFormat<T> implements ObservableConfigFormat<T> {
			public final Format<T> format;
			public final Supplier<? extends T> defaultValue;

			SimpleConfigFormat(Format<T> format, Supplier<? extends T> defaultValue) {
				this.format = format;
				this.defaultValue = defaultValue;
			}

			@Override
			public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config,
				Consumer<T> acceptedValue, Observable<?> until) {
				acceptedValue.accept(value);
				String formatted;
				if (value == ConfigurableValueCreator.NOT_SET) {
					formatted = format.format(defaultValue == null ? null : defaultValue.get());
					if (config.getConfig(false, true) != null)
						config.getConfig(false, true).setTrivial(true);
				} else {
					config.getConfig(true, true);
					if (value == null)
						formatted = null;
					else
						formatted = format.format(value);
				}
				if (!Objects.equals(formatted, config.getConfig(true, false).getValue()))
					config.getConfig(true, false).setValue(formatted);
			}

			@Override
			public T parse(ObservableConfigParseContext<T> ctx) {
				ObservableConfig config = ctx.getConfig(false, false);
				if (config == null)
					return defaultValue == null ? null : defaultValue.get();
				String value = config == null ? null : config.getValue();
				if (value == null)
					return defaultValue == null ? null : defaultValue.get();
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
			public boolean isDefault(T value) {
				if (defaultValue == null)
					return value == null;
				return Objects.equals(defaultValue.get(), value);
			}

			@Override
			public void postCopy(ObservableConfig copied) {
			}

			@Override
			public int hashCode() {
				return format.hashCode() * 7 + defaultValue.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof SimpleConfigFormat && format.equals(((SimpleConfigFormat<?>) obj).format)
					&& Objects.equals(defaultValue, ((SimpleConfigFormat<?>) obj).defaultValue);
			}

			@Override
			public String toString() {
				return format.toString();
			}
		}

		static class DelayedExecution<T> {
			boolean delayed;
			boolean found;
			T value;
			ParseException thrown;
		}

		static class ReferenceFormat<T> implements ObservableConfigFormat<T> {
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

			ReferenceFormat(QuickMap<String, FormattedField<? super T, ?>> fields,
				Function<QuickMap<String, Object>, ? extends T> retriever, BooleanSupplier retrieverReady) {
				theFields = fields;
				isRetrieverReady = retrieverReady;
				theRetriever = retriever;
			}

			@Override
			public void format(ObservableConfigParseSession session, T value, T previousValue, ConfigGetter config,
				Consumer<T> acceptedValue, Observable<?> until) throws IllegalArgumentException {
				if (value == null) {
					if (config.getConfig(false, false) != null) {
						for (ObservableConfig child : config.getConfig(true, false).getContent()) {
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
				ObservableConfig nullConfig = config.getConfig(true, false).getChild("null");
				if (nullConfig != null)
					nullConfig.remove();
				boolean[] added = new boolean[1];
				for (int f = 0; f < theFields.keySize(); f++) {
					FormattedField<? super T, Object> field = (FormattedField<? super T, Object>) theFields.get(f);
					Object fieldValue = field.field.apply(value);
					Object preFieldValue = previousValue == null ? null : field.field.apply(previousValue);
					ObservableConfig fieldConfig = config.getConfig(true, false).getChild(theFields.keySet().get(f), true, //
						child -> {
							added[0] = true;
							formatField(session, child, fieldValue, preFieldValue, field.format, until);
						});
					if (!added[0])
						formatField(session, fieldConfig, fieldValue, preFieldValue, field.format, until);
				}
				acceptedValue.accept(value);
				config.getConfig(true, false).withParsedItem(session, value);
			}

			private <F> void formatField(ObservableConfigParseSession session, ObservableConfig child, F fieldValue, F preFieldValue,
				ObservableConfigFormat<F> format, Observable<?> until) {
				format.format(session, fieldValue, preFieldValue, (__, ___) -> child, f -> {
				}, until);
			}

			@Override
			public T parse(ObservableConfigParseContext<T> ctx) throws ParseException {
				ObservableConfig c = ctx.getConfig(false, false);
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
			public boolean isDefault(T value) {
				return value == null;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
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

		/**
		 * Not actually a persisting format, but facilitates the @{@link ParentReference} annotation's functionality
		 *
		 * @param <T> The type of the parent value
		 */
		static class ParentReferenceFormat<T> implements ObservableConfigFormat<T> {
			private final TypeToken<T> theType;

			/** @param type The type of the parent value */
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
				// Even though this format doesn't persist anything, it needs the config to find the parent reference
				ctx.getConfig(true, true).setTrivial(true);
				ctx.findReferences().act(__ -> {
					ObservableConfig parent = ctx.getConfig(true, true);
					if (parent != null)
						parent = parent.getParent();
					while (parent != null) {
						Object item = parent.getParsedItem(ctx.getSession());
						if (item == null)
							break;
						else if (TypeTokens.get().isInstance(theType, item)) {
							exec.value = (T) item;
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

			@Override
			public boolean isDefault(T value) {
				return value == null;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
			}
		}

		static class NullFormat implements ObservableConfigFormat<Object> {
			@Override
			public void format(ObservableConfigParseSession session, Object value, Object previousValue, ConfigGetter config,
				Consumer<Object> acceptedValue, Observable<?> until) throws IllegalArgumentException {
				if (config.getConfig(false, true) != null)
					config.getConfig(false, true).setTrivial(true);
			}

			@Override
			public Object parse(ObservableConfigParseContext<Object> ctx) throws ParseException {
				if (ctx.getConfig(false, true) != null)
					ctx.getConfig(false, true).setTrivial(true);
				return ctx.getPreviousValue();
			}

			@Override
			public boolean isDefault(Object value) {
				return value == null;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
			}
		}

		abstract static class AbstractComponentFormat<E> implements ObservableConfigFormat<E> {
			interface ComponentSetter<E, F> {
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

			AbstractComponentFormat(QuickMap<String, ComponentField<E, ?>> fields, List<EntitySubFormat<? extends E>> subFormats) {
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
					ObservableConfig c = config.getConfig(false, false);
					if (c != null) {
						c.setName("null");
						for (int i = 0; i < theFields.keySize(); i++) {
							if (theFields.get(i).childName == null) {
								Object fieldValue = previousValue == null ? null : theFields.get(i).getter.apply(previousValue);
								formatField(session, value, (ComponentField<E, Object>) theFields.get(i), fieldValue, fieldValue, c, fv -> {
								}, until, null);
							} else {
								ObservableConfig cfg = c.getChild(theFields.get(i).childName);
								if (cfg != null)
									cfg.remove();
							}
							c.withParsedItem(session, null);
						}
					}
				} else {
					ObservableConfig c = config.getConfig(true, false);
					EntitySubFormat<? extends E> subFormat = formatFor(value);
					if (subFormat != null) {
						subFormat.moldConfig(c);
						((EntitySubFormat<E>) subFormat).format.format(session, value,
							subFormat.applies(previousValue) ? previousValue : null, config, acceptedValue, until);
						return;
					}
					acceptedValue.accept(value);
					c.set("null", null);
					for (int i = 0; i < theFields.keySize(); i++) {
						ComponentField<E, ?> field = theFields.get(i);
						Object fieldValue = field.getter.apply(value);
						formatField(session, value, (ComponentField<E, Object>) field, fieldValue, fieldValue, c, fv -> {
						}, until, null);
					}
					c.withParsedItem(session, value);
				}
			}

			@Override
			public E parse(ObservableConfigParseContext<E> ctx) throws ParseException {
				ObservableConfig c = ctx.getConfig(false, false);
				if (c != null && "true".equalsIgnoreCase(c.get("null")))
					return null;
				else if (ctx.getPreviousValue() == null) {
					return createInstance(ctx, theFields.keySet().createMap().fill(ConfigurableValueCreator.NOT_SET));
				} else {
					EntitySubFormat<? extends E> subFormat = formatFor(c);
					if (subFormat != null) {
						return ((EntitySubFormat<E>) subFormat).format.parse(ctx.forChild((parent, create, trivial) -> {
							if (create)
								subFormat.moldConfig(parent);
							return parent;
						}, ctx.getChange(), subFormat.applies(ctx.getPreviousValue()) ? ctx.getPreviousValue() : null,
							ctx::linkedReference));
					}

					if (ctx.getChange() == null) {
						for (int i = 0; i < theFields.keySize(); i++)
							parseUpdatedField(ctx, i);
					} else if (ctx.getChange().relativePath.isEmpty()) {
						if (theDefaultComponent != null)
							parseUpdatedField(ctx, theDefaultComponent.index);
					} else {
						ObservableConfigEvent change = ctx.getChange();
						ObservableConfig child = change.relativePath.get(0);
						int fieldIdx = theFieldsByChildName.keyIndexTolerant(child.getName());
						if (change.relativePath.size() == 1 && !change.oldName.equals(child.getName())) {
							if (fieldIdx >= 0) {
								ObservableConfigEvent childChange = change.asFromChild();
								try (Transaction ct = childChange.use()) {
									parseUpdatedField(ctx, fieldIdx);
								}
							}
							fieldIdx = theFieldsByChildName.keyIndexTolerant(change.oldName);
							if (fieldIdx >= 0)
								parseUpdatedField(ctx, fieldIdx);
						} else if (fieldIdx >= 0)
							parseUpdatedField(ctx, fieldIdx);
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
					private final QuickMap<String, Object> theFieldValues = theFields.keySet().createMap()
						.fill(ConfigurableValueCreator.NOT_SET);

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
							E inst = createInstance(
								ctxFor(config, session, ObservableValue.of(config), trivial -> config, null, until, null, findRefs, null),
								theFieldValues.copy());
							findRefs.onNext(null);
							return inst;
						} catch (ParseException e) {
							throw new IllegalStateException("Could not create instance", e);
						}
					}
				};
			}

			E createInstance(ObservableConfigParseContext<E> ctx, QuickMap<String, Object> fieldValues) throws ParseException {
				ObservableConfig config = ctx.getConfig(false, false);
				if (config != null) {
					for (EntitySubFormat<? extends E> subFormat : theSubFormats) {
						if (subFormat.configFilter.matches(config))
							return ((EntitySubFormat<E>) subFormat).format.parse(ctx);
					}
				}
				for (int i = 0; i < fieldValues.keySize(); i++) {
					int fi = i;
					ComponentField<E, Object> field = (ComponentField<E, Object>) theFields.get(i);
					ObservableConfigParseContext<?> fieldContext;
					ObservableConfig fieldConfig;
					if (field.childName == null) {
						fieldContext = ctx;
						fieldConfig = config;
					} else {
						fieldContext = ctx.forChild(field.childName, null, fv -> fieldValues.put(fi, fv));
						fieldConfig = config == null ? null : config.getChild(field.childName);
					}
					// If the field has been set, format it into
					if (fieldConfig != null)
						fieldValues.put(i, field.format.parse((ObservableConfigParseContext<Object>) fieldContext));
					else if (fieldValues.get(i) == ConfigurableValueCreator.NOT_SET) {
						fieldValues.put(i, field.format.parse((ObservableConfigParseContext<Object>) fieldContext));
					} else if (!field.format.isDefault(fieldValues.get(i))) {
						if (config == null)
							config = ctx.getConfig(true, false);
						config.getChild(field.childName, true, fc -> {
							field.format.format(ctx.getSession(), fieldValues.get(fi), null, (cia, trivial) -> fc, //
								v -> fieldValues.put(fi, v), ctx.getUntil());
						});
					}
				}
				E value = create(ctx, fieldValues);
				if (config != null)
					config.withParsedItem(ctx.getSession(), value);
				else
					ctx.getConfig().noInitChanges().take(1).takeUntil(ctx.getUntil())
					.act(evt -> evt.getNewValue().withParsedItem(ctx.getSession(), value));
				return value;
			}

			protected abstract E create(ObservableConfigParseContext<E> ctx, QuickMap<String, Object> fieldValues);

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
							field.format.format(session, fieldValue, fieldValue, (__, ___) -> fc, onFieldValue, until);
						});
					}
					if (!added[0])
						field.format.format(session, fieldValue, fieldValue, (__, ___) -> fieldConfig, onFieldValue, until);
				} else {
					ObservableConfig fieldConfig = entityConfig.getChild(field.childName);
					if (fieldConfig != null)
						fieldConfig.remove();
				}
			}

			protected <F> void parseUpdatedField(ObservableConfigParseContext<E> entityCtx, int fieldIdx) throws ParseException {
				ComponentField<? super E, F> field = (ComponentField<? super E, F>) theFields.get(fieldIdx);
				E entity = entityCtx.getPreviousValue();
				F oldValue = entity == null ? null : field.getter.apply(entity);
				ObservableConfigEvent[] fieldChange = new ObservableConfigEvent[1];
				ObservableConfigParseContext<F> fieldCtx = entityCtx.forChild(field.childName, oldValue, fv -> {
					if (entity != null)
						field.setter.set(entity, fv, fieldChange[0]);
				});
				fieldChange[0] = fieldCtx.getChange();
				try (Transaction t = Causable.use(fieldChange[0])) {
					F newValue = field.format.parse(fieldCtx);
					if (entity != null && newValue != oldValue)
						field.setter.set(entity, newValue, fieldCtx.getChange());
				}
			}
		}

		static class EntryFormat<K, V> extends Impl.AbstractComponentFormat<MapEntry<K, V>> {
			private final TypeToken<MapEntry<K, V>> theType;

			EntryFormat(boolean compressed, String keyName, String valueName, TypeToken<K> keyType, TypeToken<V> valueType,
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

			public ComponentField<MapEntry<K, V>, K> getKeyField() {
				return (ComponentField<MapEntry<K, V>, K>) getFields().get(0);
			}

			public ComponentField<MapEntry<K, V>, V> getValueField() {
				return (ComponentField<MapEntry<K, V>, V>) getFields().get(1);
			}

			@Override
			public boolean isDefault(MapEntry<K, V> value) {
				return value != null;
			}

			@Override
			public void postCopy(ObservableConfig copied) {
				ObservableConfig keyConfig = copied.getChild(getKeyField().childName);
				if (keyConfig != null)
					getKeyFormat().postCopy(keyConfig);
				ObservableConfig valueConfig = copied.getChild(getValueField().childName);
				if (valueConfig != null)
					getValueFormat().postCopy(valueConfig);
			}

			@Override
			protected MapEntry<K, V> create(ObservableConfigParseContext<MapEntry<K, V>> ctx, QuickMap<String, Object> fieldValues) {
				return new MapEntry<>((K) fieldValues.get(0), (V) fieldValues.get(1));
			}
		}

		static class EntityConfigFormatImpl<E> extends Impl.AbstractComponentFormat<E> implements EntityConfigFormat<E> {
			static class EntityFieldSetter<E, F> implements ComponentSetter<E, F> {
				final EntityConfiguredValueField<E, F> field;

				public EntityFieldSetter(EntityConfiguredValueField<E, F> field) {
					this.field = field;
				}

				@Override
				public void set(E entity, F fieldValue, ObservableConfigEvent change) {
					((EntityConfigFormatImpl<E>.EntityConfigInstanceBacking) field.getOwnerType().getAssociated(entity,
						EntityConfigFormatImpl.EntityConfigInstanceBacking.class))//
					.invokeSet(field, fieldValue, change);
				}
			}

			class EntityConfigInstanceBacking implements EntityReflector.ObservableEntityInstanceBacking<E> {
				private final ObservableConfigParseContext<E> theContext;
				private final QuickMap<String, Object> theFieldValues;
				private QuickMap<String, ListenerList<? extends Consumer<EntityReflector.FieldChange<?>>>> theListeners;
				private E theEntity;

				EntityConfigInstanceBacking(ObservableConfigParseContext<E> ctx, QuickMap<String, Object> fieldValues) {
					theContext = ctx;
					theFieldValues = fieldValues;
				}

				void setEntity(E entity) {
					theEntity = entity;
					theEntityType.associate(theEntity, EntityConfigInstanceBacking.class, this);
				}

				<F> void invokeSet(EntityConfiguredValueField<E, F> field, F fieldValue, ObservableConfigEvent change) {
					ListenerList<Consumer<FieldChange<F>>> listeners = theListeners == null ? null
						: (ListenerList<Consumer<FieldChange<F>>>) theListeners.get(field.getIndex());
					FieldChange<F> fieldChange;
					if (listeners != null) {
						F oldValue = field.get(theEntity);
						fieldChange = new FieldChange<>(oldValue, fieldValue, change);
					} else
						fieldChange = null;
					if (field.isSettable(theEntity))
						field.set(theEntity, fieldValue);
					else {
						if (fieldValue != null && !TypeTokens.get().isInstance(field.getFieldType(), fieldValue))
							throw new IllegalArgumentException(
								"Cannot set field " + field + " with value " + fieldValue + ", type " + fieldValue.getClass());
						theFieldValues.put(field.getIndex(), fieldValue);
					}
					if (fieldChange != null)
						listeners.forEach(//
							l -> l.accept(fieldChange));
				}

				@Override
				public Object get(int fieldIndex) {
					return theFieldValues.get(fieldIndex);
				}

				@Override
				public void set(int fieldIndex, Object newValue) {
					Object oldValue = theFieldValues.get(fieldIndex);
					theFieldValues.put(fieldIndex, newValue);
					formatField(theContext.getSession(), theEntity, (ComponentField<E, Object>) getFields().get(fieldIndex), oldValue,
						newValue, theContext.getConfig(true, false), v -> theFieldValues.put(fieldIndex, v), theContext.getUntil(), null);
				}

				@Override
				public Subscription addListener(E entity, int fieldIndex, Consumer<FieldChange<?>> listener) {
					Object key = getFields().get(fieldIndex).setter;
					try (Transaction t = getLock(fieldIndex).lock(false, null)) {
						ListenerList<Consumer<FieldChange<?>>> listeners = (ListenerList<Consumer<FieldChange<?>>>) theEntityType
							.getAssociated(entity, key);
						if (listeners == null) {
							listeners = ListenerList.build().build();
							theEntityType.associate(entity, key, listeners);
						}
						return listeners.add(listener, true)::run;
					}
				}

				@Override
				public Transactable getLock(int fieldIndex) {
					// If we ever try to support hierarchical locks or anything, this should be the field config's lock
					return theContext.getLock();
				}

				@Override
				public long getStamp(int fieldIndex) {
					ObservableConfig config = theContext.getConfig(false, false);
					if (config == null)
						return 0;
					else if (getFields().get(fieldIndex).childName == null)
						return config.getStamp();
					else
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
			}

			private final EntityConfiguredValueType<E> theEntityType;

			EntityConfigFormatImpl(EntityConfiguredValueType<E> entityType, List<EntitySubFormat<? extends E>> subFormats,
				QuickMap<String, ObservableConfigFormat<?>> fieldFormats, QuickMap<String, String> childNames) {
				super(buildFields(entityType, fieldFormats, childNames), subFormats);
				this.theEntityType = entityType;
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

				return new ComponentField<>(fieldFormats.keySet().get(index),
					(TypeToken<F>) entityType.getFields().get(index).getFieldType(), index,
					e -> (F) entityType.getFields().get(index).get(e),
					new EntityFieldSetter<>((EntityConfiguredValueField<E, F>) entityType.getFields().get(index)), //
					(ObservableConfigFormat<F>) fieldFormats.get(index), childNames.get(index));
			}

			@Override
			protected TypeToken<E> getType() {
				return getEntityType().getType();
			}

			@Override
			public EntityConfiguredValueType<E> getEntityType() {
				return theEntityType;
			}

			@Override
			public <F> ObservableConfigFormat<F> getFieldFormat(ConfiguredValueField<E, F> field) {
				return (ObservableConfigFormat<F>) getFields().get(field.getIndex()).format;
			}

			@Override
			public String getChildName(ConfiguredValueField<E, ?> field) {
				return getFields().get(field.getIndex()).childName;
			}

			@Override
			public <E2 extends E> EntityConfigCreator<E2> create(ObservableConfigParseSession session, TypeToken<E2> subType) {
				ConfigCreator<E2> creator = super.create(session, subType);
				if (creator instanceof EntityConfigCreator)
					return (EntityConfigCreator<E2>) creator;
				return new EntityConfigCreator<E2>() {
					@Override
					public EntityConfiguredValueType<E2> getEntityType() {
						return (EntityConfiguredValueType<E2>) theEntityType;
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
						ConfiguredValueField<? super E, F> field = theEntityType.getField((Function<E, F>) fieldGetter);
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
			public boolean isDefault(E value) {
				return theEntityType.isInstance(value);
			}

			@Override
			public void postCopy(ObservableConfig copied) {
				for (AbstractComponentFormat.ComponentField<E, ?> field : getFields().allValues()) {
					ObservableConfig fieldConfig = copied.getChild(field.childName);
					if (fieldConfig == null)
						continue;
					if (theEntityType.getIdFields().contains(field.index))
						fieldConfig.remove();
					else
						field.format.postCopy(fieldConfig);
				}
			}

			@Override
			protected E create(ObservableConfigParseContext<E> ctx, QuickMap<String, Object> fieldValues) {
				if (!theEntityType.getIdFields().isEmpty()) {
					ObservableConfig config = ctx.getConfig(false, false);
					if (config != null && !tryPopulateNewId(theEntityType, fieldValues, config, ctx.getSession()) && ctx != null) {
						ctx.findReferences().act(__ -> {
							tryPopulateNewId(theEntityType, fieldValues, config, ctx.getSession());
						});
					}
				}
				EntityConfigInstanceBacking backing = new EntityConfigInstanceBacking(ctx, fieldValues);
				E instance = theEntityType.create(backing);
				backing.setEntity(instance);
				Consumer<ObservableConfig> register = config -> {
					theEntityType.associate(instance, EntityConfigFormat.ENTITY_CONFIG_KEY, config);
					theEntityType.associate(instance, "until", ctx.getUntil());
				};
				ObservableConfig config = ctx.getConfig(false, false);
				if (config != null)
					register.accept(config);
				else
					ctx.getConfig().noInitChanges().take(1).takeUntil(ctx.getUntil()).act(evt -> register.accept(evt.getNewValue()));
				return instance;
			}

			private <E2 extends E> boolean tryPopulateNewId(EntityConfiguredValueType<E2> entityType, QuickMap<String, Object> fieldValues,
				ObservableConfig config, ObservableConfigParseSession session) {
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
						(Integer) currentValue, (cia, trivial) -> config.getChild(field.childName, cia, child -> child.setTrivial(trivial)),
						__ -> {
						}, Observable.empty());
					fieldValues.put(fieldIndex, (int) id);
				} else {
					((ObservableConfigFormat<Long>) field.format).format(session, id, //
						(Long) currentValue, (cia, trivial) -> config.getChild(field.childName, cia, child -> child.setTrivial(trivial)),
						__ -> {
						}, Observable.empty());
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
					ListenerList<Consumer<FieldChange<?>>> listeners = (ListenerList<Consumer<FieldChange<?>>>) theEntityType
						.getAssociated(value, key);
					if (listeners != null) {
						FieldChange<F> change = new FieldChange<>(previousValue, fieldValue, cause);
						listeners.forEach(//
							l -> l.accept(change));
					}
				}
			}

			@Override
			protected <F> void parseUpdatedField(ObservableConfigParseContext<E> entityCtx, int fieldIdx) throws ParseException {
				ComponentField<E, F> field = (ComponentField<E, F>) getFields().get(fieldIdx);
				E value = entityCtx.getPreviousValue();
				F oldValue = value == null ? null : field.getter.apply(value);
				super.parseUpdatedField(entityCtx, fieldIdx);
				if (value != null) {
					F newValue = value == null ? null : field.getter.apply(value);
					Object key = field.setter;
					ListenerList<Consumer<FieldChange<?>>> listeners = (ListenerList<Consumer<FieldChange<?>>>) theEntityType
						.getAssociated(value, key);
					if (listeners != null) {
						ObservableConfigEvent fieldEvent;
						if (entityCtx.getChange() == null) {
							fieldEvent = null;
						} else if (field.childName == null)
							fieldEvent = entityCtx.getChange();
						else if (entityCtx.getChange().relativePath.isEmpty()//
							|| !entityCtx.getChange().relativePath.get(0).getName().equals(field.childName))
							fieldEvent = null;
						else
							fieldEvent = entityCtx.getChange().asFromChild();
						try (Transaction t = Causable.use(fieldEvent)) {
							FieldChange<F> fieldChange = new FieldChange<>(oldValue, newValue, fieldEvent);
							listeners.forEach(//
								l -> l.accept(fieldChange));
						}
					}
				}
			}

			@Override
			public String toString() {
				return "as " + theEntityType;
			}
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

		static ObservableValue<? extends ObservableConfig> asChild(ObservableValue<? extends ObservableConfig> config, String child) {
			return config.map(c -> c == null ? null : c.getChild(child));
		}
	}
}
