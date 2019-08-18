package org.observe.config;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.util.TypeTokens;
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

	public <T> ObservableConfigFormat<T> getConfigFormat(TypeToken<T> type) {
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
		else
			throw new IllegalArgumentException("No custom or default format available for type " + raw.getName());
	}

	public <T> ObservableConfigFormat<T> getConfigFormat(ConfiguredValueField<?, T> field) {
		return getConfigFormat(field.getFieldType());
	}
}
