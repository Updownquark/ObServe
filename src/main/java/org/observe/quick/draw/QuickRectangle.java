package org.observe.quick.draw;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

public class QuickRectangle extends QuickSimpleShape.Abstract {
	public static final String RECTANGLE = "rectangle";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = RECTANGLE,
		interpretation = Interpreted.class,
		instance = QuickRectangle.class)
	public static class Def<E extends QuickRectangle> extends QuickSimpleShape.Def.Abstract<E> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<E extends QuickRectangle> extends QuickSimpleShape.Interpreted.Abstract<E> {
		protected Interpreted(QuickRectangle.Def<? super E> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public QuickRectangle.Def<? super E> getDefinition() {
			return (QuickRectangle.Def<? super E>) super.getDefinition();
		}

		@Override
		public E create() {
			return (E) new QuickRectangle(getIdentity());
		}
	}

	protected QuickRectangle(Object id) {
		super(id);
	}
}
