package org.observe.quick.style;

import org.observe.expresso.qonfig.ExAddOn;

/**
 * Definition of an add-on that defines style attributes
 *
 * @param <S> The type of styled element this add-on can be applied to
 * @param <AO> The sub-type of add-on to create
 */
public interface QuickStyledAddOn<S extends QuickStyledElement, AO extends ExAddOn<? super S>> extends ExAddOn.Def<S, AO> {
	/** Accepts style attributes for {@link QuickStyledAddOn#addStyleAttributes(QuickTypeStyle, StyleDefBuilder)} */
	public interface StyleDefBuilder {
		/**
		 * @param attr The style attribute found for the type
		 * @return The style attribute
		 */
		QuickStyleAttributeDef addApplicableAttribute(QuickStyleAttributeDef attr);
	}

	/**
	 * Adds style attributes declared on this add on
	 *
	 * @param type The type style to get attributes from
	 * @param builder The builder to accept the style attributes
	 */
	void addStyleAttributes(QuickTypeStyle type, QuickStyledAddOn.StyleDefBuilder builder);
}
