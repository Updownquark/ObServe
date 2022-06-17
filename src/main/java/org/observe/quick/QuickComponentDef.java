package org.observe.quick;

import java.awt.Component;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickModelValue;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface QuickComponentDef {
	QonfigElement getElement();

	ObservableModelSet.Wrapped getModels();

	QuickElementStyle getStyle();

	Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName();

	void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName);

	QuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> modification);

	ObservableModelSet.RuntimeValuePlaceholder<SettableValue, SettableValue<QuickModelValue.Satisfier>> getSatisfierPlaceholder();

	<T> QuickComponentDef support(QuickModelValue<T> modelValue, Function<Component, ObservableValue<T>> value)
		throws QonfigInterpretationException;

	<T> Function<Component, ObservableValue<T>> getSupport(QuickModelValue<T> modelValue);

	QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder);

	static class SimpleModelValueSupport<T> implements Function<Component, ObservableValue<T>> {
		private final TypeToken<T> theType;
		private final T theInitialValue;
		private SettableValue<T> theValue;

		public SimpleModelValueSupport(TypeToken<T> type, T initialValue) {
			theType = type;
			theInitialValue = initialValue;
		}

		public SimpleModelValueSupport(Class<T> type, T initialValue) {
			this(TypeTokens.get().of(type), initialValue);
		}

		@Override
		public ObservableValue<T> apply(Component component) {
			return getValue().unsettable();
		}

		public SettableValue<T> getValue() {
			if (theValue == null)
				theValue = createValue();
			return theValue;
		}

		protected SettableValue<T> createValue() {
			return SettableValue.build(theType).withValue(theInitialValue).build();
		}
	}
}