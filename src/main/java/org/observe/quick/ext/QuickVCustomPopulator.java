package org.observe.quick.ext;

import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigElementOrAddOn;

/** A {@link QuickAbstractCustomPopulator} that builds its content like a &lt;field-panel> */
public class QuickVCustomPopulator extends QuickAbstractCustomPopulator {
	/** The XML name of this element */
	public static final String V_CUSTOM_POPULATOR = "v-custom-populator";

	/** Definition for a {@link QuickVCustomPopulator} */
	public static class Def extends QuickAbstractCustomPopulator.Def<QuickVCustomPopulator> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** Interpretation for a {@link QuickVCustomPopulator} */
	public static class Interpreted extends QuickAbstractCustomPopulator.Interpreted<QuickVCustomPopulator> {
		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickVCustomPopulator create() {
			return new QuickVCustomPopulator(getIdentity());
		}
	}

	QuickVCustomPopulator(Object id) {
		super(id);
	}
}
