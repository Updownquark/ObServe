package org.observe.quick;

import org.qommons.config.QonfigAddOn;

public class QuickRenderer extends QuickAddOn.Abstract<QuickWidget> {
	public static class Def extends QuickAddOn.Def.Abstract<QuickWidget, QuickRenderer> {
		public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
			super(type, element);
		}

		@Override
		public QuickWidget.Def<?> getElement() {
			return (QuickWidget.Def<?>) super.getElement();
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<? extends QuickWidget> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends QuickAddOn.Interpreted.Abstract<QuickWidget, QuickRenderer> {
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