package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWidget;
import org.observe.util.swing.JustifiedBoxLayout;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/**
 * Arranges components next to each other up to a maximum number of components before wrapping to a new row, similarly to a
 * {@link QuickInlineLayout} for a container containing a series of containers with {@link QuickInlineLayout}s of opposite
 * {@link QuickInlineLayout#isVertical() orientation}.
 */
public class QuickGridFlowLayout extends QuickLayout.Abstract {
	/** The XML name of this add-on */
	public static final String GRID_FLOW_LAYOUT = "grid-flow-layout";

	/** An edge of a container */
	public enum Edge {
		/** The left edge of the container */
		Left(false, false),
		/** The right edge of the container */
		Right(false, true),
		/** The top edge of the container */
		Top(true, false),
		/** The bottom edge of the container */
		Bottom(true, true);

		/** Whether this edge is a vertical edge (top or bottom) */
		public final boolean vertical;
		/** Whether this edge is a trailing edge (bottom or right) */
		public final boolean reversed;

		private Edge(boolean vertical, boolean reversed) {
			this.vertical = vertical;
			this.reversed = reversed;
		}
	}

	/** {@link QuickGridFlowLayout} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = GRID_FLOW_LAYOUT,
		interpretation = Interpreted.class,
		instance = QuickGridFlowLayout.class)
	public static class Def extends QuickLayout.Def<QuickGridFlowLayout> {
		private Edge thePrimaryStart;
		private Edge theSecondaryStart;
		private CompiledExpression theMaxRowCount;
		private JustifiedBoxLayout.Alignment theMainAlign;
		private JustifiedBoxLayout.Alignment theCrossAlign;
		private JustifiedBoxLayout.Alignment theRowAlign;
		private int thePadding;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container widget whose contents to manage
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		/** @return The starting edge for widget in a row of the layout */
		@QonfigAttributeGetter("primary-start")
		public Edge getPrimaryStart() {
			return thePrimaryStart;
		}

		/** @return The starting edge for rows in the container */
		@QonfigAttributeGetter("secondary-start")
		public Edge getSecondaryStart() {
			return theSecondaryStart;
		}

		/** @return The maximum number of widgets in a row of the layout */
		@QonfigAttributeGetter("max-row-count")
		public CompiledExpression getMaxRowCount() {
			return theMaxRowCount;
		}

		/** @return The alignment of widgets along each row */
		@QonfigAttributeGetter("main-align")
		public JustifiedBoxLayout.Alignment getMainAlign() {
			return theMainAlign;
		}

		/** @return How widget are aligned in the cross dimension of each row */
		@QonfigAttributeGetter("cross-align")
		public JustifiedBoxLayout.Alignment getCrossAlign() {
			return theCrossAlign;
		}

		/** @return The alignment of rows in the container */
		@QonfigAttributeGetter("row-align")
		public JustifiedBoxLayout.Alignment getRowAlign() {
			return theRowAlign;
		}

		/** @return The spacing to leave between widgets */
		@QonfigAttributeGetter("padding")
		public int getPadding() {
			return thePadding;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickWidget> element) throws QonfigInterpretationException {
			super.update(session, element);
			thePrimaryStart = parseEdge(session.getAttributeText("primary-start"));
			theSecondaryStart = parseEdge(session.getAttributeText("secondary-start"));
			theMaxRowCount = element.getAttributeExpression("max-row-count", session);
			theMainAlign = QuickInlineLayout.jblAlign("main-align", session.getAttributeText("main-align"), session);
			theCrossAlign = QuickInlineLayout.jblAlign("cross-align", session.getAttributeText("cross-align"), session);
			theRowAlign = QuickInlineLayout.jblAlign("row-align", session.getAttributeText("row-align"), session);
			thePadding = Integer.parseInt(session.getAttributeText("padding"));
		}

		private Edge parseEdge(String startStr) {
			switch (startStr) {
			case "left":
				return Edge.Left;
			case "right":
				return Edge.Right;
			case "top":
				return Edge.Top;
			default:
				return Edge.Bottom;
			}
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickGridFlowLayout} interpretation */
	public static class Interpreted extends QuickLayout.Interpreted<QuickGridFlowLayout> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaxRowCount;

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

		/** @return The maximum number of widgets in a row of the layout */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getMaxRowCount() {
			return theMaxRowCount;
		}

		@Override
		public Class<QuickGridFlowLayout> getInstanceType() {
			return QuickGridFlowLayout.class;
		}

		@Override
		public void update(ExElement.Interpreted<? extends QuickWidget> element) throws ExpressoInterpretationException {
			super.update(element);

			theMaxRowCount = getElement().interpret(getDefinition().getMaxRowCount(), ModelTypes.Value.INT);
		}

		@Override
		public QuickGridFlowLayout create(QuickWidget element) {
			return new QuickGridFlowLayout(element);
		}
	}

	private Edge thePrimaryStart;
	private Edge theSecondaryStart;
	private ModelValueInstantiator<SettableValue<Integer>> theMaxRowCountInstantiator;
	private SettableValue<SettableValue<Integer>> theMaxRowCount;
	private JustifiedBoxLayout.Alignment theMainAlign;
	private JustifiedBoxLayout.Alignment theCrossAlign;
	private JustifiedBoxLayout.Alignment theRowAlign;
	private int thePadding;

	/** @param element The container whose contents to manage */
	protected QuickGridFlowLayout(QuickWidget element) {
		super(element);
		theMaxRowCount = SettableValue.<SettableValue<Integer>> build().build();
	}

	/** @return The starting edge for widget in a row of the layout */
	public Edge getPrimaryStart() {
		return thePrimaryStart;
	}

	/** @return The starting edge for rows in the container */
	public Edge getSecondaryStart() {
		return theSecondaryStart;
	}

	/** @return The maximum number of widgets in a row of the layout */
	public SettableValue<Integer> getMaxRowCount() {
		return SettableValue.flatten(theMaxRowCount, () -> 1);
	}

	/** @return The alignment of widgets along each row */
	public JustifiedBoxLayout.Alignment getMainAlign() {
		return theMainAlign;
	}

	/** @return How widget are aligned in the cross dimension of each row */
	public JustifiedBoxLayout.Alignment getCrossAlign() {
		return theCrossAlign;
	}

	/** @return The alignment of rows in the container */
	public JustifiedBoxLayout.Alignment getRowAlign() {
		return theRowAlign;
	}

	/** @return The spacing to leave between widgets */
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
		QuickGridFlowLayout.Interpreted myInterpreted = (QuickGridFlowLayout.Interpreted) interpreted;
		thePrimaryStart = myInterpreted.getDefinition().getPrimaryStart();
		theSecondaryStart = myInterpreted.getDefinition().getSecondaryStart();
		theMaxRowCountInstantiator = myInterpreted.getMaxRowCount().instantiate();
		theMainAlign = myInterpreted.getDefinition().getMainAlign();
		theCrossAlign = myInterpreted.getDefinition().getCrossAlign();
		theRowAlign = myInterpreted.getDefinition().getRowAlign();
		thePadding = myInterpreted.getDefinition().getPadding();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		theMaxRowCountInstantiator.instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);
		theMaxRowCount.set(theMaxRowCountInstantiator.get(models), null);
	}

	@Override
	public QuickGridFlowLayout copy(QuickWidget element) {
		QuickGridFlowLayout copy = (QuickGridFlowLayout) super.copy(element);

		copy.theMaxRowCount = SettableValue.<SettableValue<Integer>> build().build();

		return copy;
	}
}
