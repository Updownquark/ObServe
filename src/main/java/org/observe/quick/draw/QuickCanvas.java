package org.observe.quick.draw;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

public class QuickCanvas extends ExElement.Abstract {
	public static final String CANVAS = "canvas";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = CANVAS,
		interpretation = Interpreted.class,
		instance = QuickCanvas.class)
	public static class Def extends ExElement.Def.Abstract<QuickCanvas> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}
	}

	public static class Interpreted extends ExElement.Interpreted.Abstract<QuickCanvas> {
	}
}
