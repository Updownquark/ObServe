package org.observe.quick;

import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.qommons.config.QonfigElement;

public abstract class AbstractQuickField extends AbstractQuickComponentDef {
	public AbstractQuickField(QonfigElement element) {
		super(element);
	}

	@Override
	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (getFieldName() != null && component instanceof PanelPopulation.FieldEditor)
			((PanelPopulation.FieldEditor<?, ?>) component).withFieldName(getFieldName().apply(builder.getModels()));
		super.modify(component, builder);
	}
}