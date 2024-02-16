package org.observe.quick.ext;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.swing.Shading;

/** An interface that handles shading for Quick */
public interface QuickShading {
	/**
	 * @param widget The widget to shade
	 * @param repaint The runnable to call to cause the widget to be repainted
	 * @return The shading for the widget
	 * @throws ModelInstantiationException If the shading could not be instantiated
	 */
	Shading createShading(ExElement widget, Runnable repaint) throws ModelInstantiationException;
}
