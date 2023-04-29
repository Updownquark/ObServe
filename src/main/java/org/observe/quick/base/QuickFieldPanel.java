package org.observe.quick.base;

import org.observe.quick.QuickContainer2;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;

import com.google.common.reflect.TypeToken;

public class QuickFieldPanel extends QuickContainer2.Abstract<QuickWidget> {
	public static class Def extends QuickContainer2.Def.Abstract<QuickFieldPanel, QuickWidget> {
		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContainer2.Interpreted.Abstract<QuickFieldPanel, QuickWidget> {
		public Interpreted(Def definition, QuickElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public TypeToken<QuickFieldPanel> getWidgetType() {
			return TypeTokens.get().of(QuickFieldPanel.class);
		}

		@Override
		public QuickFieldPanel create(QuickElement parent) {
			return new QuickFieldPanel(this, parent);
		}
	}

	public QuickFieldPanel(Interpreted interpreted, QuickElement parent) {
		super(interpreted, parent);
	}
}
