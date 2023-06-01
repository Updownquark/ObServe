package org.observe.quick;

import org.observe.quick.style.StyleQIS;
import org.qommons.config.QonfigInterpretationException;

public abstract class AbstractQuickValueEditor extends AbstractQuickField implements QuickValueEditorDef {
	public AbstractQuickValueEditor(StyleQIS session) throws QonfigInterpretationException {
		super(session);
	}
}