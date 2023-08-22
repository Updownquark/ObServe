package org.observe.quick.base;

import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickFieldPanel extends QuickContainer.Abstract<QuickWidget> {
	public static final String FIELD_PANEL = "field-panel";
	private static final SingleTypeTraceability<QuickFieldPanel, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, FIELD_PANEL, Def.class, Interpreted.class,
			QuickFieldPanel.class);

	public static class Def extends QuickContainer.Def.Abstract<QuickFieldPanel, QuickWidget> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
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
		public QuickFieldPanel create() {
			return new QuickFieldPanel(getIdentity());
		}
	}

	public QuickFieldPanel(Object id) {
		super(id);
	}
}
