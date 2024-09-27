package org.observe.quick.ext;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.quick.base.QuickFieldPanel;
import org.qommons.config.QonfigElementOrAddOn;

/** A settings menu that can be expanded to view contained widgets */
public class QuickSettingsMenu extends QuickFieldPanel {
	/** The XML name of this widget */
	public static final String SETTINGS_MENU = "settings-menu";

	/** Definition for a {@link QuickSettingsMenu} */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = SETTINGS_MENU,
		interpretation = Interpreted.class,
		instance = QuickSettingsMenu.class)
	public static class Def extends QuickFieldPanel.Def<QuickSettingsMenu> {
		/**
		 * @param parent The parent definition
		 * @param type The Qonfig type
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** Interpretation for a {@link QuickSettingsMenu} */
	public static class Interpreted extends QuickFieldPanel.Interpreted<QuickSettingsMenu> {
		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickSettingsMenu create() {
			return new QuickSettingsMenu(getIdentity());
		}
	}

	QuickSettingsMenu(Object id) {
		super(id);
	}
}
