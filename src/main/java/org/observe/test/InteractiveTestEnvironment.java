package org.observe.test;

import java.awt.Image;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.ImageIcon;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/** The global environment in which {@link InteractiveTestingService} testing occurs */
public interface InteractiveTestEnvironment {
	/**
	 * @param name The name of the value to get
	 * @return The value with the given name, or null if none has been {@link InteractiveTestingService#addValue(String, ObservableValue)
	 *         added}
	 */
	ObservableValue<?> getValueIfExists(String name);

	/**
	 * @param name The name of the value to get
	 * @return The value with the given name
	 * @throws IllegalArgumentException IF no such test has been {@link InteractiveTestingService#addValue(String, ObservableValue) added}
	 */
	default ObservableValue<?> getValue(String name) throws IllegalArgumentException {
		ObservableValue<?> value = getValueIfExists(name);
		if (value == null)
			throw new IllegalArgumentException("No such value named " + name);
		return value;
	}

	/**
	 * @param <T> The type of the value to get
	 * @param name The name of the value to get
	 * @param type The type of the value to get
	 * @return The value with the given name
	 * @throws IllegalArgumentException IF no such test has been {@link InteractiveTestingService#addValue(String, ObservableValue) added}
	 *         or the value is of the wrong type
	 */
	default <T> ObservableValue<? extends T> getValue(String name, TypeToken<T> type) throws IllegalArgumentException {
		ObservableValue<?> value = getValue(name);
		if (!TypeTokens.get().isAssignable(type, value.getType()))
			throw new IllegalArgumentException("Value " + name + "(" + value.getType() + ") cannot be assigned as type " + type);
		return (ObservableValue<? extends T>) value;
	}

	/**
	 * @param <T> The type of the value to get
	 * @param name The name of the value to get
	 * @param type The type of the value to get
	 * @return The value with the given name
	 * @throws IllegalArgumentException IF no such test has been {@link InteractiveTestingService#addValue(String, ObservableValue) added}
	 *         or the value is of the wrong type
	 */
	default <T> ObservableValue<? extends T> getValue(String name, Class<T> type) throws IllegalArgumentException {
		return getValue(name, TypeTokens.get().of(type));
	}

	/**
	 * @param <T> The type of the value to get
	 * @param name The name of the value to get
	 * @param type The type of the value to get
	 * @return The settable value with the given name
	 * @throws IllegalArgumentException IF no such test has been {@link InteractiveTestingService#addValue(String, ObservableValue) added},
	 *         the value is of the wrong type, or the value is not settable
	 */
	default <T> SettableValue<T> getSettableValue(String name, Class<T> type) throws IllegalArgumentException {
		return getSettableValue(name, TypeTokens.get().of(type));
	}

	/**
	 * @param <T> The type of the value to get
	 * @param name The name of the value to get
	 * @param type The type of the value to get
	 * @return The settable value with the given name
	 * @throws IllegalArgumentException IF no such test has been {@link InteractiveTestingService#addValue(String, ObservableValue) added},
	 *         the value is of the wrong type, or the value is not settable
	 */
	default <T> SettableValue<T> getSettableValue(String name, TypeToken<T> type) throws IllegalArgumentException {
		ObservableValue<?> value = getValue(name);
		if (!(value instanceof SettableValue))
			throw new IllegalArgumentException("Value " + name + " is not settable");
		if (!TypeTokens.get().isAssignable(type, value.getType()) || !TypeTokens.get().isAssignable(value.getType(), type))
			throw new IllegalArgumentException("Value " + name + "(" + value.getType() + ") cannot be used as type " + type);
		return (SettableValue<T>) value;
	}

	/**
	 * @param location The location of the resource
	 * @return The resource as a stream
	 * @throws IOException If the resource could not be found or read
	 */
	InputStream getResource(String location) throws IOException;

	/**
	 * @param location The location of the resource
	 * @return The image at the given location
	 * @throws IOException If the image could not be found or read
	 */
	default Image getImage(String location) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (InputStream in = getResource(location)) {
			int read = in.read();
			while (read >= 0) {
				bytes.write(read);
				read = in.read();
			}
		}
		return new ImageIcon(bytes.toByteArray()).getImage();
	}
}
