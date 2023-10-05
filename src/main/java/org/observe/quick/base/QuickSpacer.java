package org.observe.quick.base;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class QuickSpacer extends QuickWidget.Abstract {
	public static final String SPACER = "spacer";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = SPACER,
		interpretation = Interpreted.class,
		instance = QuickSpacer.class)
	public static class Def extends QuickWidget.Def.Abstract<QuickSpacer> {
		private int theLength;

		public Def(ExElement.Def parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("length")
		public int getLength() {
			return theLength;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theLength = Integer.parseInt(session.getAttributeText("length"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickWidget.Interpreted.Abstract<QuickSpacer> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public TypeToken<? extends QuickSpacer> getWidgetType() throws ExpressoInterpretationException {
			return TypeTokens.get().of(QuickSpacer.class);
		}

		@Override
		public QuickSpacer create() {
			return new QuickSpacer(getIdentity());
		}
	}

	private int theLength;

	public QuickSpacer(Object id) {
		super(id);
	}

	public int getLength() {
		return theLength;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		theLength = myInterpreted.getDefinition().getLength();
	}
}
