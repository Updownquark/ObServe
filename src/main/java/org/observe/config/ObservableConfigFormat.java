package org.observe.config;

import java.text.ParseException;

import org.qommons.collect.QuickSet.QuickMap;
import org.qommons.io.Format;

public interface ObservableConfigFormat<T> {
	void format(T value, ObservableConfig config);

	T parse(ObservableConfig config, T previousValue, ObservableConfig.ObservableConfigEvent change) throws ParseException;

	static <T> ObservableConfigFormat<T> ofQommonFormat(Format<T> format) {
		return new ObservableConfigFormat<T>() {
			@Override
			public void format(T value, ObservableConfig config) {
				config.setValue(format.format(value));
			}

			@Override
			public T parse(ObservableConfig config, T previousValue, ObservableConfig.ObservableConfigEvent change) throws ParseException {
				if (change != null && !change.relativePath.isEmpty())
					return previousValue; // Changing a sub-config doesn't affect this value
				String value = config.getValue();
				if (value == null)
					return null;
				return format.parse(value);
			}
		};
	}

	static <E> ObservableConfigFormat<E> ofEntity(EntityConfiguredValueType<E> entityType, ConfigEntityFieldParser formats) {
		ObservableConfigFormat<?>[] fieldFormats = new ObservableConfigFormat[entityType.getFields().keySet().size()];
		for (int i = 0; i < fieldFormats.length; i++)
			fieldFormats[i] = formats.getConfigFormat(entityType.getFields().get(i));
		return new ObservableConfigFormat<E>() {
			@Override
			public void format(E value, ObservableConfig config) {
				for (int i = 0; i < entityType.getFields().keySize(); i++) {
					ConfiguredValueField<? super E, ?> field = entityType.getFields().get(i);
					Object fieldValue = field.get(value);
					formatField(field, fieldValue, config);
				}
			}

			@Override
			public E parse(ObservableConfig config, E previousValue, ObservableConfig.ObservableConfigEvent change) throws ParseException {
				if (change == null) {
					QuickMap<String, Object> fieldValues = entityType.getFields().keySet().createMap();
					for (int i = 0; i < fieldValues.keySize(); i++) {
						ObservableConfig fieldConfig = config.getChild(entityType.getFields().get(i).getName());
						if (fieldConfig != null)
							fieldValues.put(i, fieldFormats[i].parse(fieldConfig, null, null));
					}
					return entityType.create(//
						idx -> fieldValues.get(idx), //
						(idx, value) -> {
							fieldValues.put(idx, value);
							formatField(entityType.getFields().get(idx), value, config);
						});
				} else {

				}
			}

			private void formatField(ConfiguredValueField<? super E, ?> field, Object fieldValue, ObservableConfig entityConfig) {
				boolean[] added = new boolean[1];
				if (fieldValue != null) {
					ObservableConfig fieldConfig = entityConfig.getChild(field.getName(), true, fc -> {
						added[0] = true;
						((ObservableConfigFormat<Object>) fieldFormats[field.getIndex()]).format(fieldValue, fc);
					});
					if (!added[0])
						((ObservableConfigFormat<Object>) fieldFormats[field.getIndex()]).format(fieldValue, fieldConfig);
				} else {
					ObservableConfig fieldConfig = entityConfig.getChild(field.getName());
					if (fieldConfig != null)
						fieldConfig.remove();
				}
			}
		};
	}
}
