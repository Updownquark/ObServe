package org.observe.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfigFormat.EntityConfigFormat;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class ObservableConfigFormatSet {
	public interface ConfigFormatGenerator<T> {
		ObservableConfigFormat<T> formatFor(TypeToken<T> type);
	}

	public static <T> ConfigFormatGenerator<T> generate(ObservableConfigFormat<T> format) {
		return type -> format;
	}

	private final Map<Class<?>, ConfigFormatGenerator<?>> theFormats;
	private final Map<TypeToken<?>, ObservableConfigFormat<?>> theFormatCache;
	private final Map<TypeToken<?>, EntityReflector<?>> theReflectors;

	public ObservableConfigFormatSet() {
		theFormats = new ConcurrentHashMap<>();
		theFormatCache = new ConcurrentHashMap<>();
		theReflectors = new ConcurrentHashMap<>();
	}

	public <T> ObservableConfigFormatSet forSimpleType(Class<T> type, Format<T> format, Supplier<? extends T> defaultValue) {
		theFormatCache.put(TypeTokens.get().of(type), ObservableConfigFormat.ofQommonFormat(format, defaultValue));
		return this;
	}

	public <T> ObservableConfigFormatSet withFormat(TypeToken<T> type, ObservableConfigFormat<T> format) {
		theFormatCache.put(type, format);
		return this;
	}

	public <E> EntityConfiguredValueType<E> getEntityType(TypeToken<E> type) {
		return getEntityFormat(type).getEntityType();
	}

	public <E> EntityConfigFormat<E> getEntityFormat(TypeToken<E> type) {
		ObservableConfigFormat<?> format = theFormatCache.get(type);
		if (format == null) {
			EntityReflector<E> reflector = (EntityReflector<E>) theReflectors.computeIfAbsent(type, t2 -> {
				return EntityReflector.build(type).withSupers(theReflectors).build();
			});
			format = ObservableConfigFormat.ofEntity(new EntityConfiguredValueType<>(reflector, theReflectors), this);
			ObservableConfigFormat<?> fFormat = format;
			theFormatCache.computeIfAbsent(type, __ -> fFormat);
		}
		if (!(format instanceof EntityConfigFormat))
			throw new IllegalArgumentException(type + " is not formatted as an entity");
		return (EntityConfigFormat<E>) format;
	}

	public <T> ObservableConfigFormat<T> getConfigFormat(TypeToken<T> type, String configName) {
		Class<T> raw = TypeTokens.getRawType(TypeTokens.get().unwrap(type));
		ObservableConfigFormat<T> format = (ObservableConfigFormat<T>) theFormatCache.get(type);
		if (format != null)
			return format;
		ConfigFormatGenerator<T> gen = (ConfigFormatGenerator<T>) theFormats.get(raw);
		if (gen != null) {
			format = gen.formatFor(type);
			theFormatCache.put(type, format);
			return format;
		}
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
		else if (raw == Duration.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.DURATION;
		else if (raw == Instant.class)
			return (ObservableConfigFormat<T>) ObservableConfigFormat.DATE;
		else if (raw.isAssignableFrom(ObservableCollection.class)) {
			String childName = StringUtils.singularize(configName);
			ObservableConfigFormat<?> elementFormat = getConfigFormat(type.resolveType(Collection.class.getTypeParameters()[0]), childName);
			return (ObservableConfigFormat<T>) ObservableConfigFormat.ofCollection((TypeToken<Collection<Object>>) type,
				(ObservableConfigFormat<Object>) elementFormat, this, configName, childName);
		} else if (raw.isAssignableFrom(ObservableValueSet.class)) {
			String childName = StringUtils.singularize(configName);
			TypeToken<?> elementType = type.resolveType(ObservableValueSet.class.getTypeParameters()[0]);
			EntityConfigFormat<Object> elementFormat = (EntityConfigFormat<Object>) getEntityFormat(elementType);
			format = (ObservableConfigFormat<T>) ObservableConfigFormat.<Object> ofEntitySet(elementFormat, childName);
			theFormatCache.put(type, format);
			return format;
		} else if (EntityReflector.isEntityType(raw)) {
			return getEntityFormat(type);
		} else
			throw new IllegalArgumentException("No custom or default format available for type " + raw.getName());
	}

	public <T> ObservableConfigFormat<T> getConfigFormat(ConfiguredValueField<?, T> field) {
		return getConfigFormat(field.getFieldType(), StringUtils.parseByCase(field.getName(), true).toKebabCase());
	}
}