package org.observe.quick.base;

import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.util.swing.JustifiedBoxLayout;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A simple layout arranging contents in a line either from the top of the widget down, or from the left side of the widget across */
public class QuickInlineLayout extends QuickLayout.Abstract {
	/** The XML name of this add-on */
	public static final String INLINE_LAYOUT = "inline-layout";

	/**
	 * Parses a {@link JustifiedBoxLayout} {@link org.observe.util.swing.JustifiedBoxLayout.Alignment Alignment} from an attribute
	 *
	 * @param attributeName The name of the attribute
	 * @param attributeText The value of the attribute
	 * @param session The session the attribute came from
	 * @return The parsed alignment
	 * @throws QonfigInterpretationException If the alignment couldn't be parsed
	 */
	public static JustifiedBoxLayout.Alignment jblAlign(String attributeName, String attributeText, ExpressoQIS session)
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
				session.attributes().get(attributeName).getLocatedContent());
		}
	}

	/** {@link QuickInlineLayout} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = INLINE_LAYOUT,
		interpretation = Interpreted.class,
		instance = QuickInlineLayout.class)
	public static class Def extends QuickLayout.Def<QuickInlineLayout> {
		private boolean isVertical;
		private JustifiedBoxLayout.Alignment theMainAlign;
		private JustifiedBoxLayout.Alignment theCrossAlign;
		private int thePadding;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container widget whose contents to manage
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		/** @return Whether the layout will stack components top-to-bottom, or left-to-right */
		@QonfigAttributeGetter("orientation")
		public boolean isVertical() {
			return isVertical;
		}

		/** @return The alignment for components along the main axis of the layout */
		@QonfigAttributeGetter("main-align")
		public JustifiedBoxLayout.Alignment getMainAlign() {
			return theMainAlign;
		}

		/** @return The alignment for components along the cross axis of the layout */
		@QonfigAttributeGetter("cross-align")
		public JustifiedBoxLayout.Alignment getCrossAlign() {
			return theCrossAlign;
		}

		/** @return The space to leave between components */
		@QonfigAttributeGetter("padding")
		public int getPadding() {
			return thePadding;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
			super.update(session, element);
			isVertical = "vertical".equals(session.getAttributeText("orientation"));
			theMainAlign = jblAlign("main-align", session.getAttributeText("main-align"), session);
			theCrossAlign = jblAlign("cross-align", session.getAttributeText("cross-align"), session);
			thePadding = Integer.parseInt(session.getAttributeText("padding"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickInlineLayout} interpretation */
	public static class Interpreted extends QuickLayout.Interpreted<QuickInlineLayout> {
		/**
		 * @param definition The definition to interpret
		 * @param element The container widget whose contents to manage
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public Class<QuickInlineLayout> getInstanceType() {
			return QuickInlineLayout.class;
		}

		@Override
		public QuickInlineLayout create(QuickWidget element) {
			return new QuickInlineLayout(element);
		}
	}

	private boolean isVertical;
	private JustifiedBoxLayout.Alignment theMainAlign;
	private JustifiedBoxLayout.Alignment theCrossAlign;
	private int thePadding;

	/** @param element The container whose contents to manage */
	protected QuickInlineLayout(QuickWidget element) {
		super(element);
	}

	/** @return Whether the layout will stack components top-to-bottom, or left-to-right */
	public boolean isVertical() {
		return isVertical;
	}

	/** @return The alignment for components along the main axis of the layout */
	public JustifiedBoxLayout.Alignment getMainAlign() {
		return theMainAlign;
	}

	/** @return The alignment for components along the cross axis of the layout */
	public JustifiedBoxLayout.Alignment getCrossAlign() {
		return theCrossAlign;
	}

	/** @return The space to leave between components */
	public int getPadding() {
		return thePadding;
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? extends QuickWidget, ?> interpreted, QuickWidget element) throws ModelInstantiationException {
		super.update(interpreted, element);
		QuickInlineLayout.Interpreted myInterpreted = (QuickInlineLayout.Interpreted) interpreted;
		isVertical = myInterpreted.getDefinition().isVertical();
		theMainAlign = myInterpreted.getDefinition().getMainAlign();
		theCrossAlign = myInterpreted.getDefinition().getCrossAlign();
		thePadding = myInterpreted.getDefinition().getPadding();
	}
}
