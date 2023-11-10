package org.observe.quick.base;

import javax.swing.Icon;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.qommons.config.QonfigElementOrAddOn;

public class QuickToggleButton extends QuickCheckBox {
	public static final String TOGGLE_BUTTON = "toggle-button";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = TOGGLE_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickToggleButton.class)
	public static class Def extends QuickCheckBox.Def<QuickToggleButton> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickCheckBox.Interpreted<QuickToggleButton> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;

		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickToggleButton create() {
			return new QuickToggleButton(getIdentity());
		}
	}

	public QuickToggleButton(Object id) {
		super(id);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
	}

	@Override
	public QuickToggleButton copy(ExElement parent) {
		return (QuickToggleButton) super.copy(parent);
	}
}
