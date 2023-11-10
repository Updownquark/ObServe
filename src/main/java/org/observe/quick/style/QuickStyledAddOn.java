package org.observe.quick.style;

import org.observe.expresso.qonfig.ExAddOn;

public interface QuickStyledAddOn<S extends QuickStyledElement, AO extends ExAddOn<? super S>> extends ExAddOn.Def<S, AO> {
	void addStyleAttributes(QuickTypeStyle type, QuickStyledElement.QuickInstanceStyle.Def.StyleDefBuilder builder);
}
