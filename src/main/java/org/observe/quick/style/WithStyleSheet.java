package org.observe.quick.style;

import org.observe.expresso.qonfig.ExElement;

public interface WithStyleSheet extends ExElement {
	public interface Def<E extends WithStyleSheet> extends ExElement.Def<E> {
		QuickStyleSheet getStyleSheet();
	}

	public interface Interpreted<E extends WithStyleSheet> extends ExElement.Interpreted<E> {
		QuickStyleSheet.Interpreted getStyleSheet();
	}
}
