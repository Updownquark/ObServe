package org.observe.config;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.util.TypeTokens;
import org.qommons.io.Format;

public class ConfigEntityFieldParser {
	private final Map<Class<?>, Format<?>> theCustomFormats;
	private final Map<Class<?>, Supplier<?>> theCustomDefaultValues;

	public ConfigEntityFieldParser() {
		theCustomFormats = new HashMap<>();
		theCustomDefaultValues = new HashMap<>();
	}

	public <T> ConfigEntityFieldParser forType(Class<T> type, Format<T> format, Supplier<? extends T> defaultValue) {
		theCustomFormats.put(type, format);
		theCustomDefaultValues.put(type, defaultValue);
		return this;
	}

	public <T> Format<T> getFieldFormat(ConfiguredValueField<?, T> field) {
		Class<?> rawType = TypeTokens.get().wrap(TypeTokens.getRawType(field.getFieldType()));
		Format<T> format = (Format<T>) theCustomFormats.get(rawType);
		if (format != null)
			return format;
		if (rawType == String.class)
			return (Format<T>) Format.TEXT;
		else if (rawType == Boolean.class)
			return (Format<T>) Format.BOOLEAN;
		else if (rawType == Integer.class)
			return (Format<T>) Format.INT;
		else if (rawType == Double.class)
			return (Format<T>) Format.doubleFormat("0.0############E0");
		else if (rawType == Float.class)
			return (Format<T>) Format.floatFormat("0.0########");
		else if (rawType == Long.class)
			return (Format<T>) Format.LONG;
		else if (rawType == Duration.class)
			return (Format<T>) Format.DURATION;
		else if (rawType == Instant.class)
			return (Format<T>) Format.date(new SimpleDateFormat("ddMMyyyy HH:mm:ss.SSS"));
		else
			throw new IllegalArgumentException("Cannot format type " + rawType.getName() + " of field " + field + " by default");
	}

	public <T> T getDefaultValue(ConfiguredValueField<?, T> field) {
		Class<?> rawType = TypeTokens.get().wrap(TypeTokens.getRawType(field.getFieldType()));
		Supplier<T> dv = (Supplier<T>) theCustomDefaultValues.get(rawType);
		if (dv != null)
			return dv.get();
		if (rawType == String.class)
			return (T) "";
		else if (rawType == Boolean.class)
			return (T) Boolean.FALSE;
		else if (rawType == Integer.class)
			return (T) Integer.valueOf(0);
		else if (rawType == Double.class)
			return (T) Double.valueOf(0);
		else if (rawType == Float.class)
			return (T) Float.valueOf(0);
		else if (rawType == Long.class)
			return (T) Long.valueOf(0);
		else if (rawType == Duration.class)
			return (T) Duration.ZERO;
		else if (rawType == Instant.class)
			return (T) Instant.now();
		else
			throw new IllegalArgumentException("Cannot format type " + rawType.getName() + " of field " + field + " by default");
	}
}