package org.observe.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfigFormat.EntityConfigFormat;
import org.observe.config.ObservableConfigFormat.EntityFormatBuilder;
import org.observe.util.EntityReflector;
import org.observe.util.EntityReflector.EntityReflectionMessage;
import org.observe.util.EntityReflector.EntityReflectionMessageLevel;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/** A set of formats to be used for persisting/parsing complex types like entities, collections, maps, etc. */
public class ObservableConfigFormatSet {
	private static final ThreadLocal<Map<TypeToken<?>, String>> CYCLE_DETECTION = ThreadLocal.withInitial(LinkedHashMap::new);

	private final Map<TypeToken<?>, ObservableConfigFormat<?>> theFormatCache;
	private final Map<TypeToken<?>, EntityReflector<?>> theReflectors;

	/** Creates a new format set */
	public ObservableConfigFormatSet() {
		theFormatCache = new ConcurrentHashMap<>();
		theReflectors = new ConcurrentHashMap<>();
	}

	/**
	 * @param <T> The type to parse
	 * @param type the class to parse
	 * @param format The format to use for given type
	 * @param defaultValue The value to use for the given type when the config does not exist or is value-less
	 * @return This format set
	 */
	public <T> ObservableConfigFormatSet forSimpleType(Class<T> type, Format<T> format, Supplier<? extends T> defaultValue) {
		theFormatCache.put(TypeTokens.get().of(type), ObservableConfigFormat.ofQommonFormat(format, defaultValue));
		return this;
	}

	/**
	 * @param <T> The type to parse
	 * @param type The type to parse
	 * @param format The format to use for the given type
	 * @return This format set
	 */
	public <T> ObservableConfigFormatSet withFormat(TypeToken<T> type, ObservableConfigFormat<T> format) {
		theFormatCache.put(type, format);
		return this;
	}

	/**
	 * @param <E> The entity type
	 * @param type The entity type
	 * @return The configured entity type for the given java type
	 */
	public <E> EntityConfiguredValueType<E> getEntityType(TypeToken<E> type) {
		return getEntityFormat(type).getEntityType();
	}

	/**
	 * @param <E> The entity type
	 * @param type The entity type
	 * @return The format to use for the given entity type
	 */
	public <E> EntityConfigFormat<E> getEntityFormat(TypeToken<E> type) {
		Map<TypeToken<?>, String> cycles = CYCLE_DETECTION.get();
		if (cycles.get(type) != null)
			throw new IllegalArgumentException("Entity cycle detected: " + printCycle(cycles, type));
		cycles.put(type, "");
		ObservableConfigFormat<?> format = theFormatCache.get(type);
		if (format == null) {
			EntityReflector<E> reflector = (EntityReflector<E>) theReflectors.get(type);
			if (reflector == null) {
				reflector = EntityReflector.build(type, true).withSupers(theReflectors).build();
				if (theReflectors.putIfAbsent(type, reflector) != null)
					reflector = (EntityReflector<E>) theReflectors.get(type);
			}
			format = ObservableConfigFormat.buildEntities(
				new EntityConfiguredValueType<>(reflector,
					QommonsUtils.map(reflector.getSuper(), spr -> getEntityType(spr.getType()), true), theReflectors), //
				this).build();
			theFormatCache.putIfAbsent(type, format);
		}
		cycles.remove(type);
		if (!(format instanceof EntityConfigFormat))
			throw new IllegalArgumentException(type + " is not formatted as an entity");
		return (EntityConfigFormat<E>) format;
	}

	/**
	 * @param <E> The entity type
	 * @param type The entity type
	 * @param build Configures the format for the given entity type
	 * @return The format to use for the given entity type
	 */
	public <E> EntityConfigFormat<E> buildEntityFormat(TypeToken<E> type, Consumer<EntityFormatBuilder<E>> build) {
		EntityReflector<E> reflector = (EntityReflector<E>) theReflectors.get(type);
		if (reflector == null) {
			reflector = EntityReflector.build(type, true).withSupers(theReflectors).build();
			if (theReflectors.putIfAbsent(type, reflector) != null)
				reflector = (EntityReflector<E>) theReflectors.get(type);
		}
		EntityFormatBuilder<E> builder = ObservableConfigFormat.buildEntities(
			new EntityConfiguredValueType<>(reflector, //
				QommonsUtils.map(reflector.getSuper(), spr -> getEntityType(spr.getType()), true), theReflectors), //
			this);
		if (build != null)
			build.accept(builder);
		EntityConfigFormat<E> format = builder.build();
		theFormatCache.putIfAbsent(type, format);
		return format;
	}

	/**
	 * @param <T> The type to format
	 * @param type The type to format
	 * @param configName The name of the config element to parse from/format to
	 * @return The format to use
	 */
	public <T> ObservableConfigFormat<T> getConfigFormat(TypeToken<T> type, String configName) {
		Class<T> raw = TypeTokens.get().unwrap(TypeTokens.getRawType(type));
		ObservableConfigFormat<T> format = (ObservableConfigFormat<T>) theFormatCache.get(type);
		if (format != null)
			return format;
		if (raw == String.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.TEXT;
		else if (raw == double.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.DOUBLE;
		else if (raw == float.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.FLOAT;
		else if (raw == boolean.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.BOOLEAN;
		else if (raw == long.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.LONG;
		else if (raw == int.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.INT;
		else if (raw.isEnum())
			return (ObservableConfigFormat<T>) ObservableConfigFormat.enumFormat((Class<Enum<?>>) raw, () -> null);
		else if (raw == Duration.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.DURATION;
		else if (raw == Instant.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.DATE;
		else if (raw.isAssignableFrom(ObservableCollection.class)) {
			String childName = StringUtils.singularize(configName);
			ObservableConfigFormat<?> elementFormat = getConfigFormat(type.resolveType(Collection.class.getTypeParameters()[0]), childName);
			return (ObservableConfigFormat<T>) ObservableConfigFormat.ofCollection((TypeToken<Collection<Object>>) type,
				(ObservableConfigFormat<Object>) elementFormat, configName, childName);
		} else if (raw.isAssignableFrom(SyncValueSet.class)) {
			String childName = StringUtils.singularize(configName);
			TypeToken<?> elementType = type.resolveType(SyncValueSet.class.getTypeParameters()[0]);
			EntityConfigFormat<Object> elementFormat = (EntityConfigFormat<Object>) getEntityFormat(elementType);
			format = (ObservableConfigFormat<T>) ObservableConfigFormat.<Object> ofEntitySet(elementFormat, childName);
			theFormatCache.put(type, format);
			return format;
		} else {
			// System.out.println("Building entity format for " + type);
			EntityReflector.Builder<T> builder = EntityReflector.build(type, false).withSupers(theReflectors);
			builder.buildNoPrint();
			if (!builder.getMessages().isEmpty()) {
				StringBuilder msgs = new StringBuilder();
				for (EntityReflectionMessage msg : builder.getMessages()) {
					if (msg.getLevel().compareTo(EntityReflectionMessageLevel.ERROR) >= 0) {
						if (msgs.length() > 0)
							msgs.append(", ");
						msgs.append(msg.getMessage());
					}
				}
				if (msgs.length() > 0)
					throw new IllegalArgumentException("No custom or default format available for type " + raw.getName() + ": " + msgs);
			}
			return getEntityFormat(type);
		}
	}

	/**
	 * @param <T> The type of the field
	 * @param field The field to get the format for
	 * @return The format to use for the given entity field
	 */
	public <T> ObservableConfigFormat<T> getConfigFormat(ConfiguredValueField<?, T> field) {
		Map<TypeToken<?>, String> cycles = CYCLE_DETECTION.get();
		TypeToken<?> entity = field.getOwnerType().getType();
		String old = cycles.put(entity, field.getName());
		try {
			return getConfigFormat(field.getFieldType(), StringUtils.parseByCase(field.getName(), true).toKebabCase());
		} finally {
			if (old == null)
				cycles.remove(entity);
			else
				cycles.put(entity, old);
		}
	}

	private static String printCycle(Map<TypeToken<?>, String> cycles, TypeToken<?> terminal) {
		StringBuilder str = new StringBuilder();
		boolean print = false;
		for (Map.Entry<TypeToken<?>, String> entry : cycles.entrySet()) {
			if (!print && !entry.getKey().equals(terminal))
				continue;
			print = true;
			str.append(entry.getKey());
			if (!entry.getValue().isEmpty())
				str.append('.').append(entry.getValue());
			str.append(':');
		}
		str.append(terminal);
		return str.toString();
	}
}
