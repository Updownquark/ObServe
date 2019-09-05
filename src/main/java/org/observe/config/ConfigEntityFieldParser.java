package org.observe.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.collect.ObservableCollection;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class ConfigEntityFieldParser {
	public interface ConfigFormatGenerator<T> {
		ObservableConfigFormat<T> formatFor(TypeToken<T> type);
	}

	public static <T> ConfigFormatGenerator<T> generate(ObservableConfigFormat<T> format) {
		return type -> format;
	}

	private final Map<Class<?>, ConfigFormatGenerator<?>> theFormats;
	private final Map<TypeToken<?>, ObservableConfigFormat<?>> theFormatCache;

	public ConfigEntityFieldParser() {
		theFormats = new HashMap<>();
		theFormatCache = new HashMap<>();
	}

	public <T> ConfigEntityFieldParser forSimpleType(Class<T> type, Format<T> format, Supplier<? extends T> defaultValue) {
		theFormatCache.put(TypeTokens.get().of(type), ObservableConfigFormat.ofQommonFormat(format, defaultValue));
		return this;
	}

	public <T> ConfigEntityFieldParser withFormat(TypeToken<T> type, ObservableConfigFormat<T> format) {
		theFormatCache.put(type, format);
		return this;
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
				(ObservableConfigFormat<Object>) elementFormat, configName, childName);
		} else if (raw.isAssignableFrom(ObservableValueSet.class)) {
			String childName = StringUtils.singularize(configName);
			TypeToken<?> elementType = type.resolveType(ObservableValueSet.class.getTypeParameters()[0]);
			ObservableConfigFormat<?> elementFormat = getConfigFormat(elementType, childName);
			if (!(elementFormat instanceof ObservableConfigFormat.EntityConfigFormat))
				throw new IllegalArgumentException(
					"Cannot create an " + ObservableValueSet.class.getSimpleName() + " for element type " + elementType);
			return (ObservableConfigFormat<T>) ObservableConfigFormat
				.ofEntitySet((ObservableConfigFormat.EntityConfigFormat<Object>) elementFormat, configName, childName, this);
		} else if (configName != null && EntityReflector.isEntityType(raw))
			return ObservableConfigFormat.ofEntity(new EntityConfiguredValueType<>(EntityReflector.build(type).build()), this, configName);
		else
			throw new IllegalArgumentException("No custom or default format available for type " + raw.getName());
	}

	public <T> ObservableConfigFormat<T> getConfigFormat(ConfiguredValueField<?, T> field) {
		return getConfigFormat(field.getFieldType(), StringUtils.parseByCase(field.getName()).toKebabCase());
	}
}
