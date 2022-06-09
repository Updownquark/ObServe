package org.observe.quick;

import org.observe.expresso.ObservableModelSet;
import org.observe.quick.style.QuickElementStyle;
import org.qommons.config.QonfigElement;

public abstract class AbstractQuickValueEditor extends AbstractQuickField implements QuickValueEditorDef {
	public AbstractQuickValueEditor(QonfigElement element, ObservableModelSet.Wrapped models, QuickElementStyle style) {
		super(element, models, style);
	}
}