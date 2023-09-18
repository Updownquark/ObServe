package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;

import com.google.common.reflect.TypeToken;

public class QuickComboBox<T> extends CollectionSelectorWidget<T> {
	public static final String COMBO_BOX = "combo";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = COMBO_BOX,
		interpretation = Interpreted.class,
		instance = QuickComboBox.class)
	public static class Def extends CollectionSelectorWidget.Def<QuickComboBox<?>> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<T> extends CollectionSelectorWidget.Interpreted<T, QuickComboBox<T>> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<QuickComboBox<T>> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().keyFor(QuickComboBox.class).<QuickComboBox<T>> parameterized(getValueType());
		}

		@Override
		public QuickComboBox<T> create() {
			return new QuickComboBox<>(getIdentity());
		}
	}

	public QuickComboBox(Object id) {
		super(id);
	}

	@Override
	protected QuickComboBox<T> clone() {
		return (QuickComboBox<T>) super.clone();
	}
}
