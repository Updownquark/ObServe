package org.observe.quick.swing;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickDocument;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickSwing extends ExAddOn.Def.Abstract<QuickDocument, ExAddOn.Void<QuickDocument>> {
	private String theLookAndFeel;

	public QuickSwing(QonfigAddOn type, ExElement.Def<? extends QuickDocument> element) {
		super(type, element);
	}

	public String getLookAndFeel() {
		return theLookAndFeel;
	}

	@Override
	public void update(ExpressoQIS session, ExElement.Def<? extends QuickDocument> element) throws QonfigInterpretationException {
		super.update(session, element);

		theLookAndFeel = session.getAttributeText(("look-and-feel"));
	}

	@Override
	public Interpreted interpret(ExElement.Interpreted<? extends QuickDocument> element) {
		return new Interpreted(this, element);
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickDocument, ExAddOn.Void<QuickDocument>> {
		public Interpreted(QuickSwing definition, ExElement.Interpreted<? extends QuickDocument> element) {
			super(definition, element);
		}

		@Override
		public QuickSwing getDefinition() {
			return (QuickSwing) super.getDefinition();
		}

		@Override
		public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			super.update(env);

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
		public ExAddOn.Void<QuickDocument> create(QuickDocument element) {
			return null;
		}
	}
}
