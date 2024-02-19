package org.observe.quick.swing;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickDocument;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** The &lt;quick-swing> add-on, allowing the user to select the Swing look and feel for the UI */
@ExElementTraceable(toolkit = QuickSwingInterpretation.SWING, qonfigType = "quick-swing", interpretation = QuickSwing.Interpreted.class)
public class QuickSwing extends ExAddOn.Def.Abstract<QuickDocument, ExAddOn.Void<QuickDocument>> {
	private String theLookAndFeel;

	/**
	 * @param type The Qonfig type of this add-on
	 * @param element The Quick document
	 */
	public QuickSwing(QonfigAddOn type, ExElement.Def<? extends QuickDocument> element) {
		super(type, element);
	}

	/** @return The name of the look and feel */
	@QonfigAttributeGetter("look-and-feel")
	public String getLookAndFeel() {
		return theLookAndFeel;
	}

	@Override
	public void update(ExpressoQIS session, ExElement.Def<? extends QuickDocument> element) throws QonfigInterpretationException {
		super.update(session, element);

		theLookAndFeel = session.getAttributeText(("look-and-feel"));
	}

	@Override
	public Interpreted interpret(ExElement.Interpreted<?> element) {
		return new Interpreted(this, element);
	}

	/** {@link QuickSwing} interpretation */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickDocument, ExAddOn.Void<QuickDocument>> {
		/**
		 * @param definition The definition to interpret
		 * @param element The interpreted Quick document
		 */
		protected Interpreted(QuickSwing definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public QuickSwing getDefinition() {
			return (QuickSwing) super.getDefinition();
		}

		@Override
		public void update(ExElement.Interpreted<? extends QuickDocument> element) throws ExpressoInterpretationException {
			super.update(element);

			String lAndFClass;
			switch (getDefinition().getLookAndFeel()) {
			case "system":
				lAndFClass = UIManager.getSystemLookAndFeelClassName();
				break;
			case "cross-platform":
				lAndFClass = UIManager.getCrossPlatformLookAndFeelClassName();
				break;
			default:
				lAndFClass = getDefinition().getLookAndFeel();
				break;
			}
			try {
				UIManager.setLookAndFeel(lAndFClass);
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
				getElement().reporting().warn("Could not load look-and-feel " + getDefinition().getLookAndFeel(), e);
			}
		}

		@Override
		public Class<ExAddOn.Void<QuickDocument>> getInstanceType() {
			return (Class<ExAddOn.Void<QuickDocument>>) (Class<?>) ExAddOn.Void.class;
		}

		@Override
		public ExAddOn.Void<QuickDocument> create(QuickDocument element) {
			return null;
		}
	}
}
