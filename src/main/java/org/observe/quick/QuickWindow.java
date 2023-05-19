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
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on for an element that is to be a window */
public class QuickWindow extends QuickAddOn.Abstract<QuickElement> {
	/** An action to perform when the user closes the window (e.g. clicks the "X") */
	public enum CloseAction {
		/** Do nothing when the user attempts to close */
		DoNothing,
		/** Hide the window, but keep it ready */
		Hide,
		/** Dispose of the window, releasing all its resources */
		Dispose,
		/** Exit the application/process */
		Exit
	}

	/** The definition of a {@link QuickWindow} */
	public static class Def extends QuickAddOn.Def.Abstract<QuickElement, QuickWindow> {
		private CompiledExpression theX;
		private CompiledExpression theY;
		private CompiledExpression theWidth;
		private CompiledExpression theHeight;
		private CompiledExpression theTitle;
		private CompiledExpression theVisible;
		private CloseAction theCloseAction;

		/**
		 * @param type The add-on that the Qonfig toolkit uses to represent this type
		 * @param element The element that this add-on is added onto
		 */
		public Def(QonfigAddOn type, QuickElement.Def<? extends QuickDocument2> element) {
			super(type, element);
		}

		/** @return The expression defining the x-coordinate of the window--to move and be updated when the user moves it */
		public CompiledExpression getX() {
			return theX;
		}

		/** @return The expression defining the y-coordinate of the window--to move and be updated when the user moves it */
		public CompiledExpression getY() {
			return theY;
		}

		/** @return The expression defining the width of the window--to size and be updated when the user resizes it */
		public CompiledExpression getWidth() {
			return theWidth;
		}

		/** @return The expression defining the height of the window--to size and be updated when the user resizes it */
		public CompiledExpression getHeight() {
			return theHeight;
		}

		/** @return The expression defining the title for the window */
		public CompiledExpression getTitle() {
			return theTitle;
		}

		/** @return The expression defining when the window is visible--to hide/show and to be updated when the user closes the window */
		public CompiledExpression getVisible() {
			return theVisible;
		}

		/** @return The action to perform when the user closes the window */
		public CloseAction getCloseAction() {
			return theCloseAction;
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
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
		}

		@Override
		public Interpreted interpret(QuickElement.Interpreted<? extends QuickElement> element) {
			return new Interpreted(this, (QuickDocument2.Interpreted) element);
		}
	}

	/** An interpretation of a {@link QuickWindow} */
	public static class Interpreted extends QuickAddOn.Interpreted.Abstract<QuickElement, QuickWindow> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theHeight;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTitle;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theVisible;

		/**
		 * @param definition The definition producing this interpretation
		 * @param element The element interpretation that this add-on is added onto
		 */
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

		/** @return The expression defining the x-coordinate of the window--to move and be updated when the user moves it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getX() {
			return theX;
		}

		/** @return The expression defining the y-coordinate of the window--to move and be updated when the user moves it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getY() {
			return theY;
		}

		/** @return The expression defining the width of the window--to size and be updated when the user resizes it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getWidth() {
			return theWidth;
		}

		/** @return The expression defining the height of the window--to size and be updated when the user resizes it */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getHeight() {
			return theHeight;
		}

		/** @return The expression defining the title for the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTitle() {
			return theTitle;
		}

		/** @return The expression defining when the window is visible--to hide/show and to be updated when the user closes the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getVisible() {
			return theVisible;
		}

		@Override
		public void update(InterpretedModelSet models) throws ExpressoInterpretationException {
			theX = getDefinition().getX() == null ? null : getDefinition().getX().evaluate(ModelTypes.Value.INT).interpret();
			theY = getDefinition().getY() == null ? null : getDefinition().getY().evaluate(ModelTypes.Value.INT).interpret();
			theWidth = getDefinition().getWidth() == null ? null : getDefinition().getWidth().evaluate(ModelTypes.Value.INT).interpret();
			theHeight = getDefinition().getHeight() == null ? null : getDefinition().getHeight().evaluate(ModelTypes.Value.INT).interpret();
			theTitle = getDefinition().getTitle() == null ? null : getDefinition().getTitle().evaluate(ModelTypes.Value.STRING).interpret();
			theVisible = getDefinition().getVisible() == null ? null
				: getDefinition().getVisible().evaluate(ModelTypes.Value.BOOLEAN).interpret();
		}

		@Override
		public QuickWindow create(QuickElement element) {
			return new QuickWindow(this, element);
		}
	}

	private CloseAction theCloseAction;
	private SettableValue<Integer> theX;
	private SettableValue<Integer> theY;
	private SettableValue<Integer> theWidth;
	private SettableValue<Integer> theHeight;
	private SettableValue<String> theTitle;
	private SettableValue<Boolean> theVisible;

	/**
	 * @param interpreted The interpretation producing this window
	 * @param element The element that this add-on is added onto
	 */
	public QuickWindow(Interpreted interpreted, QuickElement element) {
		super(interpreted, element);
	}

	public CloseAction getCloseAction() {
		return theCloseAction;
	}

	/** @return The value defining the x-coordinate of the window--to move and be updated when the user moves it */
	public SettableValue<Integer> getX() {
		return theX;
	}

	/** @return The value defining the y-coordinate of the window--to move and be updated when the user moves it */
	public SettableValue<Integer> getY() {
		return theY;
	}

	/** @return The value defining the width of the window--to size and be updated when the user resizes it */
	public SettableValue<Integer> getWidth() {
		return theWidth;
	}

	/** @return The value defining the height of the window--to size and be updated when the user resizes it */
	public SettableValue<Integer> getHeight() {
		return theHeight;
	}

	/** @return The value defining the title for the window */
	public SettableValue<String> getTitle() {
		return theTitle;
	}

	/** @return The value defining when the window is visible--to hide/show and to be updated when the user closes the window */
	public SettableValue<Boolean> getVisible() {
		return theVisible;
	}

	@Override
	public void update(QuickAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
		super.update(interpreted, models);
		QuickWindow.Interpreted myInterpreted = (QuickWindow.Interpreted) interpreted;
		theCloseAction = myInterpreted.getDefinition().getCloseAction();
		theX = myInterpreted.getX() == null ? null : myInterpreted.getX().get(models);
		theY = myInterpreted.getY() == null ? null : myInterpreted.getY().get(models);
		theWidth = myInterpreted.getWidth() == null ? null : myInterpreted.getWidth().get(models);
		theHeight = myInterpreted.getHeight() == null ? null : myInterpreted.getHeight().get(models);
		theTitle = myInterpreted.getTitle() == null ? null : myInterpreted.getTitle().get(models);
		theVisible = myInterpreted.getVisible() == null ? null : myInterpreted.getVisible().get(models);
	}
}
