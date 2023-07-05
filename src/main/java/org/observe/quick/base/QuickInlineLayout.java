package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.swing.JustifiedBoxLayout;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickInlineLayout extends QuickLayout.Abstract {
	public static final String INLINE_LAYOUT = "inline-layout";
	private static final ElementTypeTraceability<QuickBox, ?, ?> TRACEABILITY = ElementTypeTraceability
		.<QuickBox, QuickInlineLayout, Interpreted, Def> buildAddOn(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION,
			INLINE_LAYOUT, Def.class, Interpreted.class, QuickInlineLayout.class)//
		.reflectAddOnMethods()//
		.build();

	public static class Def extends QuickLayout.Def<QuickInlineLayout> {
		private boolean isVertical;
		private JustifiedBoxLayout.Alignment theMainAlign;
		private JustifiedBoxLayout.Alignment theCrossAlign;
		private int thePadding;

		public Def(QonfigAddOn type, ExElement.Def<? extends QuickBox> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		@QonfigAttributeGetter("main-align")
		public JustifiedBoxLayout.Alignment getMainAlign() {
			return theMainAlign;
		}

		@QonfigAttributeGetter("cross-align")
		public JustifiedBoxLayout.Alignment getCrossAlign() {
			return theCrossAlign;
		}

		@QonfigAttributeGetter("padding")
		public int getPadding() {
			return thePadding;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickBox> element) throws QonfigInterpretationException {
			element.withTraceability(TRACEABILITY.validate(getType(), session.reporting()));
			super.update(session, element);
			isVertical = "vertical".equals(session.getAttributeText("orientation"));
			theMainAlign = jblAlign("main-align", session.getAttributeText("main-align"), session);
			theCrossAlign = jblAlign("cross-align", session.getAttributeText("cross-align"), session);
			thePadding = Integer.parseInt(session.getAttributeText("padding"));
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
		public Interpreted interpret(ExElement.Interpreted<? extends QuickBox> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends QuickLayout.Interpreted<QuickInlineLayout> {
		public Interpreted(Def definition, ExElement.Interpreted<? extends QuickBox> element) {
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
	private int thePadding;

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

	public int getPadding() {
		return thePadding;
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		QuickInlineLayout.Interpreted myInterpreted = (QuickInlineLayout.Interpreted) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		theMainAlign = myInterpreted.getDefinition().getMainAlign();
		theCrossAlign = myInterpreted.getDefinition().getCrossAlign();
		thePadding = myInterpreted.getDefinition().getPadding();
	}
}
