package org.observe.quick.base;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigAddOn;

public interface QuickLayout extends ExAddOn<QuickBox> {
	public abstract class Def<L extends QuickLayout> extends ExAddOn.Def.Abstract<QuickBox, L> {
		protected Def(QonfigAddOn type, ExElement.Def<? extends QuickBox> element) {
			super(type, element);
		}

		@Override
		public abstract Interpreted<L> interpret(ExElement.Interpreted<?> element);
	}

	public abstract class Interpreted<L extends QuickLayout> extends ExAddOn.Interpreted.Abstract<QuickBox, L> {
		protected Interpreted(Def<L> definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def<L> getDefinition() {
			return (Def<L>) super.getDefinition();
		}
	}

	public abstract class Abstract extends ExAddOn.Abstract<QuickBox> implements QuickLayout {
		protected Abstract(QuickBox element) {
			super(element);
		}
	}
}
