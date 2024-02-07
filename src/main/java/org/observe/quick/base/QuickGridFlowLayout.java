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
import org.observe.util.TypeTokens;
import org.observe.util.swing.JustifiedBoxLayout;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickGridFlowLayout extends QuickLayout.Abstract {
	public static final String GRID_FLOW_LAYOUT = "grid-flow-layout";

	public enum Edge {
		Left(false, false), Right(false, true), Top(true, false), Bottom(true, true);

		public final boolean vertical;
		public final boolean reversed;

		private Edge(boolean vertical, boolean reversed) {
			this.vertical = vertical;
			this.reversed = reversed;
		}
	}

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

		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("primary-start")
		public Edge getPrimaryStart() {
			return thePrimaryStart;
		}

		@QonfigAttributeGetter("secondary-start")
		public Edge getSecondaryStart() {
			return theSecondaryStart;
		}

		@QonfigAttributeGetter("max-row-count")
		public CompiledExpression getMaxRowCount() {
			return theMaxRowCount;
		}

		@QonfigAttributeGetter("main-align")
		public JustifiedBoxLayout.Alignment getMainAlign() {
			return theMainAlign;
		}

		@QonfigAttributeGetter("cross-align")
		public JustifiedBoxLayout.Alignment getCrossAlign() {
			return theCrossAlign;
		}

		@QonfigAttributeGetter("row-align")
		public JustifiedBoxLayout.Alignment getRowAlign() {
			return theRowAlign;
		}

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

	public static class Interpreted extends QuickLayout.Interpreted<QuickGridFlowLayout> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaxRowCount;

		public Interpreted(Def definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

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

	public QuickGridFlowLayout(QuickWidget element) {
		super(element);
		theMaxRowCount = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class))
			.build();
	}

	public Edge getPrimaryStart() {
		return thePrimaryStart;
	}

	public Edge getSecondaryStart() {
		return theSecondaryStart;
	}

	public SettableValue<Integer> getMaxRowCount() {
		return SettableValue.flatten(theMaxRowCount, () -> 1);
	}

	public JustifiedBoxLayout.Alignment getMainAlign() {
		return theMainAlign;
	}

	public JustifiedBoxLayout.Alignment getCrossAlign() {
		return theCrossAlign;
	}

	public JustifiedBoxLayout.Alignment getRowAlign() {
		return theRowAlign;
	}

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

		copy.theMaxRowCount = SettableValue.build(theMaxRowCount.getType()).build();

		return copy;
	}
}
