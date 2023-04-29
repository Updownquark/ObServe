package org.observe.quick.base;

import org.observe.quick.QuickElement;
import org.observe.quick.QuickValueWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;

import com.google.common.reflect.TypeToken;

public class QuickCheckBox extends QuickValueWidget.Abstract<Boolean> {
	public static class Def extends QuickValueWidget.Def.Abstract<Boolean, QuickCheckBox> {
		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<Boolean, QuickCheckBox> {
		public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public TypeToken<QuickCheckBox> getWidgetType() {
			return TypeTokens.get().of(QuickCheckBox.class);
		}

		@Override
		public QuickCheckBox create(QuickElement parent) {
			return new QuickCheckBox(this, parent);
		}
	}

	public QuickCheckBox(Interpreted interpreted, QuickElement parent) {
		super(interpreted, parent);
	}
}
