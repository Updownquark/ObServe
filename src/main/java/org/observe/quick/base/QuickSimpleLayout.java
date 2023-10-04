package org.observe.quick.base;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;

public class QuickSimpleLayout extends QuickLayout.Abstract {
	public static class Def extends QuickLayout.Def<QuickSimpleLayout> {
		public Def(QonfigAddOn type, QuickBox.Def<?> element) {
			super(type, element);
		}

		@Override
		public QuickBox.Def<?> getElement() {
			return (QuickBox.Def<?>) super.getElement();
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends QuickBox> element) {
			return new Interpreted(this, (QuickBox.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends QuickLayout.Interpreted<QuickSimpleLayout> {
		public Interpreted(Def definition, QuickBox.Interpreted<? extends QuickBox> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickBox.Interpreted<?> getElement() {
			return (QuickBox.Interpreted<?>) super.getElement();
		}

		@Override
		public Class<QuickSimpleLayout> getInstanceType() {
			return QuickSimpleLayout.class;
		}

		@Override
		public QuickSimpleLayout create(QuickBox element) {
			return new QuickSimpleLayout(element);
		}
	}

	public QuickSimpleLayout(QuickBox element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	public static class Child extends ExAddOn.Abstract<QuickWidget> {
		public static class Def extends ExAddOn.Def.Abstract<QuickWidget, Child> {
			public Def(QonfigAddOn type, QuickWidget.Def<?> element) {
				super(type, element);
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends QuickWidget> element) {
				return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
			}
		}

		public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, Child> {
			public Interpreted(Def definition, QuickWidget.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Class<Child> getInstanceType() {
				return Child.class;
			}

			@Override
			public Child create(QuickWidget element) {
				return new Child(element);
			}
		}

		public Child(QuickWidget element) {
			super(element);
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}
	}
}
