package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.util.swing.Shading;

public interface QuickShading {
	Shading createShading(QuickBox box, Runnable repaint) throws ModelInstantiationException;
}
