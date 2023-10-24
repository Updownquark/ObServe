package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;

import com.google.common.reflect.TypeToken;

public class QuickToggleButtons<T> extends CollectionSelectorWidget<T> {
	public static final String TOGGLE_BUTTONS = "toggle-buttons";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TOGGLE_BUTTONS,
		interpretation = Interpreted.class,
		instance = QuickToggleButtons.class)
	public static class Def extends CollectionSelectorWidget.Def<QuickToggleButtons<?>> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickToggleButtons<T>> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickToggleButtons<T> create() {
			return new QuickToggleButtons<>(getIdentity());
		}
	}

	public QuickToggleButtons(Object id) {
		super(id);
	}

	@Override
	protected QuickToggleButtons<T> clone() {
		return (QuickToggleButtons<T>) super.clone();
	}
}
