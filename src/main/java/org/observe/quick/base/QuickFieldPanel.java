package org.observe.quick.base;

import org.observe.expresso.ExpressoQIS;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickFieldPanel extends QuickContainer.Abstract<QuickWidget> {
	public static final String FIELD_PANEL = "field-panel";

	public static class Def extends QuickContainer.Def.Abstract<QuickFieldPanel, QuickWidget> {
		public Def(QuickElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, FIELD_PANEL);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContainer.Interpreted.Abstract<QuickFieldPanel, QuickWidget> {
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
