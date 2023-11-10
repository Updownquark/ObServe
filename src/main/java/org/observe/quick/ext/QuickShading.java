package org.observe.quick.ext;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.swing.Shading;

public interface QuickShading {
	Shading createShading(ExElement widget, Runnable repaint) throws ModelInstantiationException;
}
