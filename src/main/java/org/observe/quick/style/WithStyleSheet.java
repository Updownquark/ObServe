package org.observe.quick.style;

import org.observe.expresso.qonfig.ExElement;

/** An Expresso element that may have a style sheet */
public interface WithStyleSheet extends ExElement {
	/**
	 * Definition for {@link WithStyleSheet}
	 *
	 * @param <E> The sub type of element to create
	 */
	public interface Def<E extends WithStyleSheet> extends ExElement.Def<E> {
		/** @return This element's style sheet */
		QuickStyleSheet getStyleSheet();
	}

	/**
	 * Interpretation for {@link WithStyleSheet}
	 *
	 * @param <E> The sub type of element to create
	 */
	public interface Interpreted<E extends WithStyleSheet> extends ExElement.Interpreted<E> {
		/** @return This element's style sheet */
		QuickStyleSheet.Interpreted getStyleSheet();
	}
}
