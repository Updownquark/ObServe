package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigInterpretationException;

public class QuickWindow extends QuickAddOn.Abstract<QuickDocument2> {
	public enum CloseAction {
		DoNothing, Hide, Dispose, Exit
	}

	public static class Def extends QuickAddOn.Def.Abstract<QuickDocument2, QuickWindow> {
		private CompiledExpression theX;
		private CompiledExpression theY;
		private CompiledExpression theWidth;
		private CompiledExpression theHeight;
		private CompiledExpression theTitle;
		private CompiledExpression theVisible;
		private CloseAction theCloseAction;

		public CompiledExpression getX() {
			return theX;
		}

		public CompiledExpression getY() {
			return theY;
		}

		public CompiledExpression getWidth() {
			return theWidth;
		}

		public CompiledExpression getHeight() {
			return theHeight;
		}

		public CompiledExpression getTitle() {
			return theTitle;
		}

		public CompiledExpression getVisible() {
			return theVisible;
		}

		public CloseAction getCloseAction() {
			return theCloseAction;
		}

		@Override
		public Def update(ExpressoQIS session) throws QonfigInterpretationException {
			theX = session.getAttributeExpression("x");
			theY = session.getAttributeExpression("y");
			theWidth = session.getAttributeExpression("width");
			theHeight = session.getAttributeExpression("height");
			theTitle = session.getAttributeExpression("title");
			theVisible = session.getAttributeExpression("visible");
			switch (session.getAttributeText("close-action")) {
			case "do-nothing":
				theCloseAction = CloseAction.DoNothing;
				break;
			case "hide":
				theCloseAction = CloseAction.Hide;
				break;
			case "dispose":
				theCloseAction = CloseAction.Dispose;
				break;
			case "exit":
				theCloseAction = CloseAction.Exit;
				break;
			}
			return this;
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<?> element) {
			return new Interpreted(this, (QuickDocument2.Interpreted) element);
		}
	}

	public static class Interpreted extends QuickAddOn.Interpreted.Abstract<QuickDocument2, QuickWindow> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theHeight;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theVisible;

		public Interpreted(Def definition, QuickDocument2.Interpreted element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickDocument2.Interpreted getElement() {
			return (QuickDocument2.Interpreted) super.getElement();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getX() {
			return theX;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getY() {
			return theY;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
			return theTitle;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getVisible() {
			return theVisible;
		}

		@Override
		public Interpreted update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theX = getDefinition().getX() == null ? null : getDefinition().getX().evaluate(ModelTypes.Value.INT).interpret();
			theY = getDefinition().getY() == null ? null : getDefinition().getY().evaluate(ModelTypes.Value.INT).interpret();
			theWidth = getDefinition().getWidth() == null ? null : getDefinition().getWidth().evaluate(ModelTypes.Value.INT).interpret();
			theHeight = getDefinition().getHeight() == null ? null : getDefinition().getHeight().evaluate(ModelTypes.Value.INT).interpret();
			theTitle = getDefinition().getTitle() == null ? null : getDefinition().getTitle().evaluate(ModelTypes.Value.STRING).interpret();
			theVisible = getDefinition().getVisible() == null ? null
				: getDefinition().getVisible().evaluate(ModelTypes.Value.BOOLEAN).interpret();
			return this;
		}

		@Override
		public QuickWindow create(QuickDocument2 element) {
			return new QuickWindow(this, element);
		}
	}

	private SettableValue<Integer> theX;
	private SettableValue<Integer> theY;
	private SettableValue<Integer> theWidth;
	private SettableValue<Integer> theHeight;
	private SettableValue<String> theTitle;
	private SettableValue<Boolean> theVisible;

	public QuickWindow(Interpreted interpreted, QuickDocument2 element) {
		super(interpreted, element);
	}

	@Override
	public Interpreted getInterpreted() {
		return (Interpreted) super.getInterpreted();
	}

	public SettableValue<Integer> getX() {
		return theX;
	}

	public SettableValue<Integer> getY() {
		return theY;
	}

	public SettableValue<Integer> getWidth() {
		return theWidth;
	}

	public SettableValue<Integer> getHeight() {
		return theHeight;
	}

	public SettableValue<String> getTitle() {
		return theTitle;
	}

	public SettableValue<Boolean> getVisible() {
		return theVisible;
	}

	@Override
	public QuickWindow update(ModelSetInstance models) throws ModelInstantiationException {
		theX = getInterpreted().getX() == null ? null : getInterpreted().getX().get(models);
		theY = getInterpreted().getY() == null ? null : getInterpreted().getY().get(models);
		theWidth = getInterpreted().getWidth() == null ? null : getInterpreted().getWidth().get(models);
		theHeight = getInterpreted().getHeight() == null ? null : getInterpreted().getHeight().get(models);
		theTitle = getInterpreted().getTitle() == null ? null : getInterpreted().getTitle().get(models);
		theVisible = getInterpreted().getVisible() == null ? null : getInterpreted().getVisible().get(models);
		return this;
	}
}
