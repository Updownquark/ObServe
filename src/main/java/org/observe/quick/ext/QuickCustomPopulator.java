package org.observe.quick.ext;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.base.QuickLayout;
import org.qommons.config.QonfigElementOrAddOn;

/** A {@link QuickAbstractCustomPopulator} that builds its content using a specified layout */
public class QuickCustomPopulator extends QuickAbstractCustomPopulator {
	/** The XML name of this element */
	public static final String CUSTOM_POPULATOR = "custom-populator";

	/** Definition for a {@link QuickCustomPopulator} */
	public static class Def extends QuickAbstractCustomPopulator.Def<QuickCustomPopulator> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The layout to arrange the contents of the components populated by this populator */
		@QonfigAttributeGetter("layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	/** Interpretation for a {@link QuickCustomPopulator} */
	public static class Interpreted extends QuickAbstractCustomPopulator.Interpreted<QuickCustomPopulator> {
		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The layout to arrange the contents of the components populated by this populator */
		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		@Override
		public QuickCustomPopulator create() {
			return new QuickCustomPopulator(getIdentity());
		}
	}

	QuickCustomPopulator(Object id) {
		super(id);
	}

	/** @return The layout to arrange the contents of the components populated by this populator */
	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}
}
