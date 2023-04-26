package org.observe.quick;

import org.observe.expresso.ExpressoInterpretationException;
import org.qommons.Transformer;

public interface QuickInterpretation {
	void configure(Transformer.Builder<ExpressoInterpretationException> tx);
}