package org.observe.quick.base;

import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.qommons.config.QonfigAddOn;

public interface QuickLayout extends QuickAddOn<QuickBox> {
	public abstract class Def<L extends QuickLayout> extends QuickAddOn.Def.Abstract<QuickBox, QuickLayout> {
		protected Def(QonfigAddOn type, QuickElement.Def<? extends QuickBox> element) {
			super(type, element);
		}

		@Override
		public abstract Interpreted<L> interpret(QuickElement.Interpreted<? extends QuickBox> element);
	}

	public abstract class Interpreted<L extends QuickLayout> extends QuickAddOn.Interpreted.Abstract<QuickBox, L> {
		protected Interpreted(Def<L> definition, QuickElement.Interpreted<? extends QuickBox> element) {
			super(definition, element);
		}

		@Override
		public Def<L> getDefinition() {
			return (Def<L>) super.getDefinition();
		}
	}

	public abstract class Abstract extends QuickAddOn.Abstract<QuickBox> implements QuickLayout {
		protected Abstract(QuickLayout.Interpreted<?> interpreted, QuickBox element) {
			super(interpreted, element);
		}
	}
}
