package org.observe.quick.base;

import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickAddOn;
import org.observe.quick.QuickElement;
import org.observe.util.swing.JustifiedBoxLayout;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickInlineLayout extends QuickLayout.Abstract {
	public static class Def extends QuickLayout.Def<QuickInlineLayout> {
		private boolean isVertical;
		private JustifiedBoxLayout.Alignment theMainAlign;
		private JustifiedBoxLayout.Alignment theCrossAlign;

		public Def(QonfigAddOn type, QuickElement.Def<? extends QuickBox> element) {
			super(type, element);
		}

		public boolean isVertical() {
			return isVertical;
		}

		public JustifiedBoxLayout.Alignment getMainAlign() {
			return theMainAlign;
		}

		public JustifiedBoxLayout.Alignment getCrossAlign() {
			return theCrossAlign;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			super.update(session);
			isVertical = "vertical".equals(session.getAttributeText("orientation"));
			theMainAlign = jblAlign("main-align", session.getAttributeText("main-align"), session);
			theCrossAlign = jblAlign("cross-align", session.getAttributeText("cross-align"), session);
		}

		public JustifiedBoxLayout.Alignment jblAlign(String attributeName, String attributeText, ExpressoQIS session)
			throws QonfigInterpretationException {
			switch (attributeText) {
			case "leading":
				return JustifiedBoxLayout.Alignment.LEADING;
			case "trailing":
				return JustifiedBoxLayout.Alignment.TRAILING;
			case "center":
				return JustifiedBoxLayout.Alignment.CENTER;
			case "justify":
				return JustifiedBoxLayout.Alignment.JUSTIFIED;
			default:
				throw new QonfigInterpretationException("Unrecognized " + attributeName + ": '" + attributeText + "'",
					session.getAttributeValuePosition(attributeName, 0), attributeText.length());
			}
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<? extends QuickBox> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends QuickLayout.Interpreted<QuickInlineLayout> {
		public Interpreted(Def definition, QuickElement.Interpreted<? extends QuickBox> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickInlineLayout create(QuickBox element) {
			return new QuickInlineLayout(this, element);
		}
	}

	private boolean isVertical;
	private JustifiedBoxLayout.Alignment theMainAlign;
	private JustifiedBoxLayout.Alignment theCrossAlign;

	public QuickInlineLayout(Interpreted interpreted, QuickBox element) {
		super(interpreted, element);
	}

	public boolean isVertical() {
		return isVertical;
	}

	public JustifiedBoxLayout.Alignment getMainAlign() {
		return theMainAlign;
	}

	public JustifiedBoxLayout.Alignment getCrossAlign() {
		return theCrossAlign;
	}

	@Override
	public void update(QuickAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		QuickInlineLayout.Interpreted myInterpreted = (QuickInlineLayout.Interpreted) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		theMainAlign = myInterpreted.getDefinition().getMainAlign();
		theCrossAlign = myInterpreted.getDefinition().getCrossAlign();
	}
}
