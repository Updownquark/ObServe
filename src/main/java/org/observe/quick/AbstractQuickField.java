package org.observe.quick;

import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.qommons.config.QonfigInterpretationException;

public abstract class AbstractQuickField extends AbstractQuickComponentDef {
	public AbstractQuickField(QuickQIS session) throws QonfigInterpretationException {
		super(session);
	}

	@Override
	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (getFieldName() != null && component instanceof PanelPopulation.FieldEditor)
			((PanelPopulation.FieldEditor<?, ?>) component).withFieldName(getFieldName().apply(builder.getModels()));
		super.modify(component, builder);
	}
}