package org.observe.quick;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigAddOn;

public class TextMouseListener extends ExAddOn.Abstract<QuickMouseListener> {
	public static final String TEXT_MOUSE_LISTENER = "text-widget-mouse-listener";

	public static class Def extends ExAddOn.Def.Abstract<QuickMouseListener, TextMouseListener> {
		public Def(QonfigAddOn type, QuickMouseListener.Def<?> element) {
			super(type, element);
		}

		@Override
		public QuickMouseListener.Def<?> getElement() {
			return (org.observe.quick.QuickMouseListener.Def<?>) super.getElement();
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends QuickMouseListener> element) {
			return new Interpreted(this, (QuickMouseListener.Interpreted<?>) element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickMouseListener, TextMouseListener> {
		public Interpreted(Def definition, QuickMouseListener.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public QuickMouseListener.Interpreted<?> getElement() {
			return (QuickMouseListener.Interpreted<?>) super.getElement();
		}

		@Override
		public TextMouseListener create(QuickMouseListener element) {
			return new TextMouseListener(this, element);
		}
	}

	public TextMouseListener(Interpreted interpreted, QuickMouseListener element) {
		super(interpreted, element);
	}
}
