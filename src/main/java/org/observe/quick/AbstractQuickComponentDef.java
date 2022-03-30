package org.observe.quick;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.qommons.config.QonfigElement;

public abstract class AbstractQuickComponentDef implements QuickComponentDef {
	private final QonfigElement theElement;
	private Function<ModelSetInstance, ? extends ObservableValue<String>> theFieldName;
	private BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> theModifications;

	public AbstractQuickComponentDef(QonfigElement element) {
		theElement = element;
	}

	@Override
	public QonfigElement getElement() {
		return theElement;
	}

	@Override
	public Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName() {
		return theFieldName;
	}

	@Override
	public void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName) {
		theFieldName = fieldName;
	}

	@Override
	public AbstractQuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> fieldModification) {
		if (theModifications == null)
			theModifications = fieldModification;
		else {
			BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> old = theModifications;
			theModifications = (field, builder) -> {
				old.accept(field, builder);
				fieldModification.accept(field, builder);
			};
		}
		return this;
	}

	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (theModifications != null)
			theModifications.accept(component, builder);
	}
}