package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;

import com.google.common.reflect.TypeToken;

public class QuickRadioButtons<T> extends CollectionSelectorWidget<T> {
	public static final String RADIO_BUTTONS = "radio-buttons";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = RADIO_BUTTONS,
		interpretation = Interpreted.class,
		instance = QuickRadioButtons.class)
	public static class Def extends CollectionSelectorWidget.Def<QuickRadioButtons<?>> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickRadioButtons<T>> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<QuickRadioButtons<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickRadioButtons.class).<QuickRadioButtons<T>> parameterized(getValueType());
		}

		@Override
		public QuickRadioButtons<T> create() {
			return new QuickRadioButtons<>(getIdentity());
		}
	}

	public QuickRadioButtons(Object id) {
		super(id);
	}

	@Override
	protected QuickRadioButtons<T> clone() {
		return (QuickRadioButtons<T>) super.clone();
	}
}
