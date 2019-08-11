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

	public ConfigEntityFieldParser() {
		theFormats = new HashMap<>();
	}

	public <T> ConfigEntityFieldParser forSimpleType(Class<T> type, Format<T> format, Supplier<? extends T> defaultValue) {
		theFormats.put(type, generate(ObservableConfigFormat.ofQommonFormat(format, defaultValue)));
		return this;
	}

	public <T> ObservableConfigFormat<T> getConfigFormat(ConfiguredValueField<?, T> field) {
		Class<T> raw = TypeTokens.getRawType(TypeTokens.get().unwrap(field.getFieldType()));
		ConfigFormatGenerator<T> gen = (ConfigFormatGenerator<T>) theFormats.get(raw);
		if (gen != null)
			return gen.formatFor(field.getFieldType());
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
			throw new IllegalArgumentException("No custom or default format available for type " + raw.getName() + " of field " + field);
	}
}
