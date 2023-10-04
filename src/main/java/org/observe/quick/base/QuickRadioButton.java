package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;

import com.google.common.reflect.TypeToken;

public class QuickRadioButton extends QuickCheckBox {
	public static final String RADIO_BUTTON = "radio-button";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = RADIO_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickRadioButton.class)
	public static class Def extends QuickCheckBox.Def<QuickRadioButton> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickCheckBox.Interpreted<QuickRadioButton> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<QuickRadioButton> getWidgetType() {
			return TypeTokens.get().of(QuickRadioButton.class);
		}

		@Override
		public QuickRadioButton create() {
			return new QuickRadioButton(getIdentity());
		}
	}

	public QuickRadioButton(Object id) {
		super(id);
	}

	@Override
	public QuickRadioButton copy(ExElement parent) {
		return (QuickRadioButton) super.copy(parent);
	}
}
