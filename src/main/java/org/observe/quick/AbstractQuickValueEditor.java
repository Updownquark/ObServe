package org.observe.quick;

import org.qommons.config.QonfigElement;

public abstract class AbstractQuickValueEditor extends AbstractQuickField implements QuickValueEditorDef {
	public AbstractQuickValueEditor(QonfigElement element) {
		super(element);
	}
}