package org.observe.quick;

import org.qommons.config.QonfigInterpretationException;

public abstract class AbstractQuickValueEditor extends AbstractQuickField implements QuickValueEditorDef {
	public AbstractQuickValueEditor(QuickQIS session) throws QonfigInterpretationException {
		super(session);
	}
}