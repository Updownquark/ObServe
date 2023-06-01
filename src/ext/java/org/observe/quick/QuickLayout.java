package org.observe.quick;

import java.awt.LayoutManager;

/** A layout defined in Quick */
public interface QuickLayout {
	/** @return The swing layout for the container (box) */
	LayoutManager create();
}