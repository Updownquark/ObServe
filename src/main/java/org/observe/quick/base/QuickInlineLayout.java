package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.swing.JustifiedBoxLayout;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickInlineLayout extends QuickLayout.Abstract {
	public static final ExAddOn.AddOnAttributeGetter<QuickBox, QuickInlineLayout, Interpreted, Def> VERTICAL = ExAddOn.AddOnAttributeGetter
		.<QuickBox, QuickInlineLayout, Interpreted, Def> of(Def.class, Def::isVertical, Interpreted.class,
			i -> i.getDefinition().isVertical(), QuickInlineLayout.class, QuickInlineLayout::isVertical,
			"Whether the components are arranged top-to-bottom or left-to-right");
	public static final ExAddOn.AddOnAttributeGetter<QuickBox, QuickInlineLayout, Interpreted, Def> MAIN_ALIGN = ExAddOn.AddOnAttributeGetter
		.<QuickBox, QuickInlineLayout, Interpreted, Def> of(Def.class, Def::getMainAlign, Interpreted.class,
			i -> i.getDefinition().getMainAlign(), QuickInlineLayout.class, QuickInlineLayout::getMainAlign,
			"The alignment strategy determining how all the components will share space along the main axis of the layout");
	public static final ExAddOn.AddOnAttributeGetter<QuickBox, QuickInlineLayout, Interpreted, Def> CROSS_ALIGN = ExAddOn.AddOnAttributeGetter
		.<QuickBox, QuickInlineLayout, Interpreted, Def> of(Def.class, Def::getCrossAlign, Interpreted.class,
			i -> i.getDefinition().getCrossAlign(), QuickInlineLayout.class, QuickInlineLayout::getCrossAlign,
			"The alignment strategy for each component along the cross axis of the layout");

	public static class Def extends QuickLayout.Def<QuickInlineLayout> {
		private boolean isVertical;
		private JustifiedBoxLayout.Alignment theMainAlign;
		private JustifiedBoxLayout.Alignment theCrossAlign;
		private int thePadding;

		public Def(QonfigAddOn type, ExElement.Def<? extends QuickBox> element) {
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

		public int getPadding() {
			return thePadding;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickBox> element) throws QonfigInterpretationException {
			element.forAttribute(session.getAttributeDef(null, null, "orientation"), VERTICAL);
			element.forAttribute(session.getAttributeDef(null, null, "main-align"), MAIN_ALIGN);
			element.forAttribute(session.getAttributeDef(null, null, "cross-align"), CROSS_ALIGN);
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
