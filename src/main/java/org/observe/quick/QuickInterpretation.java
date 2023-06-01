package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.qommons.Transformer;

/**
 * Interprets {@link QuickDocument documents}, {@link QuickWidget widgets}, and other Quick structures into application-specific structures
 */
public interface QuickInterpretation {
	/** @param tx The transformation builder to populate with Quick-related transformation capabilities */
	void configure(Transformer.Builder<ExpressoInterpretationException> tx);
}