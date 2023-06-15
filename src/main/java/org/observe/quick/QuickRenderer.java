package org.observe.quick;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigAddOn;

/** Tag for a widget that is only used as a renderer, instead of being a first class child of its parent */
public class QuickRenderer extends ExAddOn.Abstract<QuickWidget> {
	public static class Def extends ExAddOn.Def.Abstract<QuickWidget, QuickRenderer> {
		public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
			super(type, element);
		}

		@Override
		public QuickWidget.Def<?> getElement() {
			return (QuickWidget.Def<?>) super.getElement();
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends QuickWidget> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, QuickRenderer> {
		public Interpreted(Def def, QuickWidget.Interpreted<?> element) {
			super(def, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickWidget.Interpreted<?> getElement() {
			return (QuickWidget.Interpreted<?>) super.getElement();
		}

		@Override
		public QuickRenderer create(QuickWidget element) {
			return new QuickRenderer(this, element);
		}
	}

	public QuickRenderer(Interpreted interpreted, QuickWidget element) {
		super(interpreted, element);
	}
}
