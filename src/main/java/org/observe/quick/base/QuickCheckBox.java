package org.observe.quick.base;

import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickCheckBox extends QuickValueWidget.Abstract<Boolean> {
	public static final String CHECK_BOX = "check-box";

	public static class Def extends QuickValueWidget.Def.Abstract<Boolean, QuickCheckBox> {
		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, CHECK_BOX);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<Boolean, QuickCheckBox> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public TypeToken<QuickCheckBox> getWidgetType() {
			return TypeTokens.get().of(QuickCheckBox.class);
		}

		@Override
		public QuickCheckBox create(ExElement parent) {
			return new QuickCheckBox(this, parent);
		}
	}

	public QuickCheckBox(Interpreted interpreted, ExElement parent) {
		super(interpreted, parent);
	}
}
