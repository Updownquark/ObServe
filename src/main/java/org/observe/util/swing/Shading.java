package org.observe.util.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * A custom shader for components in Swing
 *
 * @see PanelPopulation.PanelPopulator#withShading(Shading)
 */
public interface Shading {
	/**
	 * @param graphics The graphics to draw on
	 * @param size The size of the component to shade
	 * @param background The background color of the component
	 */
	void shade(Graphics2D graphics, Dimension size, Color background);
}
