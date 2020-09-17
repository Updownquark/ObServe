package org.observe.util.swing;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;

import javax.swing.ToolTipManager;

public class ToolTipHelper {
	private final Component theComponent;

	public ToolTipHelper(Component component) {
		theComponent = component;
	}

	public void setTooltipVisible(boolean visible) {
		// Super hacky, but not sure how else to do this. Swing's tooltip system doesn't have many hooks into it.
		// Overall, this approach may be somewhat flawed, but it's about the best I can do,
		// the potential negative consequences are small, and I think it's a very good feature
		Point mousePos = theComponent.getMousePosition();
		if (visible) {
			System.out.println("HACK Displaying tooltip");
			MouseEvent me = new MouseEvent(theComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, //
				mousePos == null ? 0 : mousePos.x, mousePos == null ? 0 : mousePos.y, 0, false);
			theComponent.dispatchEvent(me);
		} else if (mousePos == null) { // If the mouse isn't over the component, it can't be displaying a tooltip, right?
			int prevDelay = ToolTipManager.sharedInstance().getDismissDelay();
			ToolTipManager.sharedInstance().setDismissDelay(1);
			ToolTipManager.sharedInstance().setDismissDelay(prevDelay);
		}
	}
}
