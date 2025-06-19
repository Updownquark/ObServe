package org.observe.quick.draw;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

public class QuickEllipse extends QuickSimpleShape.Abstract {
	public static final String ELLIPSE = "ellipse";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = ELLIPSE,
		interpretation = Interpreted.class,
		instance = QuickEllipse.class)
	public static class Def<E extends QuickEllipse> extends QuickSimpleShape.Def.Abstract<E> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<E extends QuickEllipse> extends QuickSimpleShape.Interpreted.Abstract<E> {
		protected Interpreted(QuickEllipse.Def<? super E> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public QuickEllipse.Def<? super E> getDefinition() {
			return (QuickEllipse.Def<? super E>) super.getDefinition();
		}

		@Override
		public E create() {
			return (E) new QuickEllipse(getIdentity());
		}
	}

	protected QuickEllipse(Object id) {
		super(id);
	}
}
