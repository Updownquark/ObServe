package org.observe.quick;

import org.observe.quick.QuickInterpreter.QuickSession;

public abstract class AbstractQuickValueEditor extends AbstractQuickField implements QuickValueEditorDef {
	public AbstractQuickValueEditor(QuickSession<?> session) {
		super(session);
	}
}