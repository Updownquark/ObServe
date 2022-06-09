package org.observe.quick;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickElementStyle;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigElement;

public interface QuickComponentDef {
	QonfigElement getElement();

	ObservableModelSet.Wrapped getModels();

	QuickElementStyle getStyle();

	Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName();

	void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName);

	QuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> modification);

	QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder);
}