package org.observe.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

/** Allows dynamic field formatting */
public class CsvEntityFormatSet {
	private final Map<TypeToken<?>, Format<?>> theFormatCache;
	
	/** Creates the format set */
	public CsvEntityFormatSet() {
		theFormatCache = new ConcurrentHashMap<>();
	}

	/**
	 * @param entity The entity type whose field to format
	 * @param fieldName The name of the field in the entity type to format
	 * @return The format to use for values of the given field
	 */
	public Format<?> getFormat(CsvEntitySet.EntityFormat entity, String fieldName) {
		return getFormat(entity.getFields().get(fieldName));
	}

	/**
	 * @param <T> The type to format
	 * @param fieldType The type to format
	 * @return The format to use
	 */
	public <T> Format<T> getFormat(TypeToken<T> fieldType) {
		Class<T> raw = TypeTokens.get().unwrap(TypeTokens.getRawType(fieldType));
		Format<T> format = (Format<T>) theFormatCache.get(fieldType);
		if (format != null) {
			return format;
		}
		if (raw == String.class) {
			return (Format<T>) Format.TEXT;
		} else if (raw == double.class) {
			return (Format<T>) Format.doubleFormat(9).build();
		} else if (raw == float.class) {
			return (Format<T>) Format.doubleFormat(9).buildFloat();
		} else if (raw == boolean.class) {
			return (Format<T>) Format.BOOLEAN;
		} else if (raw == long.class) {
			return (Format<T>) Format.LONG;
		} else if (raw == int.class) {
			return (Format<T>) Format.INT;
		} else if (raw.isEnum()) {
			return (Format<T>) Format.enumFormat((Class<Enum<?>>) raw);
		} else if (raw == Duration.class) {
			return (Format<T>) Format.DURATION;
		} else if (raw == Instant.class) {
			return (Format<T>) Format.flexibleDate("ddMMMyyyy", TimeZone.getTimeZone("GMT"));
		} else if (raw.isAssignableFrom(List.class)) {
			Format<?> elementFormat = getFormat(fieldType.resolveType(Collection.class.getTypeParameters()[0]));
			return (Format<T>) new Format.ListFormat<>(elementFormat, ",", null);
		} else {
			throw new IllegalArgumentException("No custom or default format available for type " + raw.getName());
		}
	}

	/**
	 * @param <T> The type to parse
	 * @param type The type to parse
	 * @param format The format to use for the given type
	 * @return This format set
	 */
	public <T> CsvEntityFormatSet withFormat(TypeToken<T> type, Format<T> format) {
		theFormatCache.put(type, format);
		return this;
	}
}
