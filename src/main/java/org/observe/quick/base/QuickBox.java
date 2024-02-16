package org.observe.quick.base;

import javax.swing.Box;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple container that requires a {@link QuickLayout} add-on to arrange its content widgets */
public class QuickBox extends QuickContainer.Abstract<QuickWidget> {
	/** The XML name of this element */
	public static final String BOX = "box";

	/**
	 * {@link Box} definition
	 *
	 * @param <W> The sub-type of box to create
	 */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = BOX,
		interpretation = Interpreted.class,
		instance = QuickBox.class)
	public static class Def<W extends QuickBox> extends QuickContainer.Def.Abstract<W, QuickWidget> {
		/**
		 * @param parent The parent element of the widget
		 * @param type The Qonfig type of the widget
		 */
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The layout to arrange the contents of this box */
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

	/**
	 * {@link Box} interpretation
	 *
	 * @param <W> The sub-type of box to create
	 */
	public static class Interpreted<W extends QuickBox> extends QuickContainer.Interpreted.Abstract<W, QuickWidget> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for the widget
		 */
		protected Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		/** @return The layout to arrange the contents of this box */
		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		@Override
		public W create() {
			return (W) new QuickBox(getIdentity());
		}
	}

	/** @param id The element ID for this widget */
	protected QuickBox(Object id) {
		super(id);
	}

	/** @return The layout to arrange the contents of this box */
	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}
}
