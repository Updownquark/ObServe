package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickBox extends QuickContainer.Abstract<QuickWidget> {
	public static final String BOX = "box";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = BOX,
		interpretation = Interpreted.class,
		instance = QuickBox.class)
	public static class Def<W extends QuickBox> extends QuickContainer.Def.Abstract<W, QuickWidget> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			if (getAddOn(QuickLayout.Def.class) == null) {
				String layout = session.getAttributeText("layout");
				throw new QonfigInterpretationException("No Quick interpretation for layout " + layout,
					session.attributes().get("layout").getLocatedContent());
			}
		}

		@Override
		public Interpreted<W> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<W extends QuickBox> extends QuickContainer.Interpreted.Abstract<W, QuickWidget> {
		public Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		@Override
		public W create() {
			return (W) new QuickBox(getIdentity());
		}
	}

	public QuickBox(Object id) {
		super(id);
	}

	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}
}
