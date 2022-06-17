package org.observe.quick;

import org.observe.quick.QuickInterpreter.QuickSession;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;

public abstract class AbstractQuickField extends AbstractQuickComponentDef {
	public AbstractQuickField(QuickSession<?> session) {
		super(session);
	}

	@Override
	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (getFieldName() != null && component instanceof PanelPopulation.FieldEditor)
			((PanelPopulation.FieldEditor<?, ?>) component).withFieldName(getFieldName().apply(builder.getModels()));
		super.modify(component, builder);
	}
}