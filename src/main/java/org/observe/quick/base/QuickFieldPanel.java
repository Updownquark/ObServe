package org.observe.quick.base;

import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickFieldPanel extends QuickContainer.Abstract<QuickWidget> {
	public static final String FIELD_PANEL = "field-panel";
	private static final ElementTypeTraceability<QuickFieldPanel, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
		.<QuickFieldPanel, Interpreted, Def> build(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, FIELD_PANEL)//
		.reflectMethods(Def.class, Interpreted.class, QuickFieldPanel.class)//
		.build();

	public static class Def extends QuickContainer.Def.Abstract<QuickFieldPanel, QuickWidget> {
		public Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickContainer.Interpreted.Abstract<QuickFieldPanel, QuickWidget> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public TypeToken<QuickFieldPanel> getWidgetType() {
			return TypeTokens.get().of(QuickFieldPanel.class);
		}

		@Override
		public QuickFieldPanel create(ExElement parent) {
			return new QuickFieldPanel(this, parent);
		}
	}

	public QuickFieldPanel(Interpreted interpreted, ExElement parent) {
		super(interpreted, parent);
	}
}
