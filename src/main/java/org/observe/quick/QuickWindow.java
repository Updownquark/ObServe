package org.observe.quick;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelComponentInstantiator;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.AppEnvironment;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoConfigV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on for an element that is to be a window */
public class QuickWindow extends QuickAbstractWindow.Default implements AppEnvironment {
	/** The name of the Qonfig add-on this implements */
	public static final String WINDOW = "window";

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
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = WINDOW,
		interpretation = Interpreted.class,
		instance = QuickWindow.class)
	public static class Def extends QuickAbstractWindow.Def.Default<QuickWindow> {
		private CompiledExpression theX;
		private CompiledExpression theY;
		private CompiledExpression theWidth;
		private CompiledExpression theHeight;
		private CompiledExpression theWindowIcon;
		private CloseAction theCloseAction;
		private final List<ModelComponentId> theConfigVariables;

		/**
		 * @param type The add-on that the Qonfig toolkit uses to represent this type
		 * @param element The element that this add-on is added onto
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
			super(type, element);
			theConfigVariables = new ArrayList<>();
		}

		/** @return The expression defining the x-coordinate of the window--to move and be updated when the user moves it */
		@QonfigAttributeGetter("x")
		public CompiledExpression getX() {
			return theX;
		}

		/** @return The expression defining the y-coordinate of the window--to move and be updated when the user moves it */
		@QonfigAttributeGetter("y")
		public CompiledExpression getY() {
			return theY;
		}

		/** @return The expression defining the width of the window--to size and be updated when the user resizes it */
		@QonfigAttributeGetter("width")
		public CompiledExpression getWidth() {
			return theWidth;
		}

		/** @return The expression defining the height of the window--to size and be updated when the user resizes it */
		@QonfigAttributeGetter("height")
		public CompiledExpression getHeight() {
			return theHeight;
		}

		/** @return The expression defining the icon of the window */
		@QonfigAttributeGetter("window-icon")
		public CompiledExpression getWindowIcon() {
			return theWindowIcon;
		}

		/** @return The action to perform when the user closes the window */
		@QonfigAttributeGetter("close-action")
		public CloseAction getCloseAction() {
			return theCloseAction;
		}

		List<ModelComponentId> getConfigVariables() {
			return Collections.unmodifiableList(theConfigVariables);
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theX = element.getAttributeExpression("x", session);
			theY = element.getAttributeExpression("y", session);
			theWidth = element.getAttributeExpression("width", session);
			theHeight = element.getAttributeExpression("height", session);
			theWindowIcon = element.getAttributeExpression("window-icon", session);
			String closeAction = session.getAttributeText("close-action");
			switch (closeAction) {
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
			default:
				throw new QonfigInterpretationException("Unrecognized close action: " + closeAction,
					session.attributes().get("close-action").getLocatedContent());
			}
			theConfigVariables.clear();
			if (getElement() instanceof QuickDocument.Def) // Only set the environment if we're the root window
				findConfigVariables(session.getExpressoEnv().getModels());
		}

		private void findConfigVariables(ObservableModelSet models) {
			ModelComponentNode<?> configMV = models.getComponentIfExists(ExpressoConfigV0_1.CONFIG_NAME);
			if (configMV != null)
				theConfigVariables.add(configMV.getIdentity());
			else {
				for (String comp : models.getComponentNames()) {
					ModelComponentNode<?> compMV = models.getComponentIfExists(comp);
					if (compMV.getModel() != null)
						findConfigVariables(compMV.getModel());
				}
			}
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	/** An interpretation of a {@link QuickWindow} */
	public static class Interpreted extends QuickAbstractWindow.Interpreted.Default<QuickWindow> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theX;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theY;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theWidth;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theHeight;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theWindowIcon;

		/**
		 * @param definition The definition producing this interpretation
		 * @param element The element interpretation that this add-on is added onto
		 */
		public Interpreted(Def definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
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

		/** @return The expression defining the icon to display for the window */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getWindowIcon() {
			return theWindowIcon;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);
			theX = getElement().interpret(getDefinition().getX(), ModelTypes.Value.INT);
			theY = getElement().interpret(getDefinition().getY(), ModelTypes.Value.INT);
			theWidth = getElement().interpret(getDefinition().getWidth(), ModelTypes.Value.INT);
			theHeight = getElement().interpret(getDefinition().getHeight(), ModelTypes.Value.INT);
			theWindowIcon = getDefinition().getWindowIcon() == null ? null : QuickCoreInterpretation.evaluateIcon(
				getDefinition().getWindowIcon(), getElement(), getDefinition().getElement().getElement().getDocument().getLocation());
		}

		@Override
		public Class<QuickWindow> getInstanceType() {
			return QuickWindow.class;
		}

		@Override
		public QuickWindow create(ExElement element) {
			return new QuickWindow(element);
		}
	}

	private ModelValueInstantiator<SettableValue<Integer>> theXInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theYInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theWidthInstantiator;
	private ModelValueInstantiator<SettableValue<Integer>> theHeightInstantiator;
	private ModelValueInstantiator<SettableValue<Image>> theWindowIconInstantiator;
	private CloseAction theCloseAction;
	private final SettableValue<SettableValue<Integer>> theX;
	private SettableValue<SettableValue<Integer>> theY;
	private SettableValue<SettableValue<Integer>> theWidth;
	private SettableValue<SettableValue<Integer>> theHeight;
	private SettableValue<SettableValue<Image>> theWindowIcon;
	private final List<ModelComponentId> theConfigVariables;

	/** @param element The element that this add-on is added onto */
	public QuickWindow(ExElement element) {
		super(element);
		theX = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class)).build();
		theY = SettableValue.build(theX.getType()).build();
		theWidth = SettableValue.build(theX.getType()).build();
		theHeight = SettableValue.build(theX.getType()).build();
		theWindowIcon = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Image>> parameterized(Image.class))
			.build();
		theConfigVariables = new ArrayList<>();
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** @return The action to take when the user closes the window */
	public CloseAction getCloseAction() {
		return theCloseAction;
	}

	/** @return The value defining the x-coordinate of the window--to move and be updated when the user moves it */
	public SettableValue<Integer> getX() {
		return SettableValue.flatten(theX);
	}

	/** @return The value defining the y-coordinate of the window--to move and be updated when the user moves it */
	public SettableValue<Integer> getY() {
		return SettableValue.flatten(theY);
	}

	/** @return The value defining the width of the window--to size and be updated when the user resizes it */
	public SettableValue<Integer> getWidth() {
		return SettableValue.flatten(theWidth);
	}

	/** @return The value defining the height of the window--to size and be updated when the user resizes it */
	public SettableValue<Integer> getHeight() {
		return SettableValue.flatten(theHeight);
	}

	/** @return The icon to display for the window */
	public SettableValue<Image> getWindowIcon() {
		return SettableValue.flatten(theWindowIcon);
	}

	@Override
	public String getApplicationTitle() throws ModelInstantiationException {
		tryInstantiateTitle();
		return getTitle().get();
	}

	@Override
	public Image getApplicationIcon() throws ModelInstantiationException {
		try {
			theWindowIcon.set(theWindowIconInstantiator == null ? null : theWindowIconInstantiator.get(null), null);
		} catch (NullPointerException e) {
			throw new ModelInstantiationException("Title is not a literal--could not evaluate", getElement().reporting().getPosition(), 0);
		}
		return getWindowIcon().get();
	}

	@Override
	public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);
		QuickWindow.Interpreted myInterpreted = (QuickWindow.Interpreted) interpreted;
		theCloseAction = myInterpreted.getDefinition().getCloseAction();
		theXInstantiator = myInterpreted.getX() == null ? null : myInterpreted.getX().instantiate();
		theYInstantiator = myInterpreted.getY() == null ? null : myInterpreted.getY().instantiate();
		theWidthInstantiator = myInterpreted.getWidth() == null ? null : myInterpreted.getWidth().instantiate();
		theHeightInstantiator = myInterpreted.getHeight() == null ? null : myInterpreted.getHeight().instantiate();
		theWindowIconInstantiator = myInterpreted.getWindowIcon() == null ? null : myInterpreted.getWindowIcon().instantiate();
		theConfigVariables.clear();
		theConfigVariables.addAll(myInterpreted.getDefinition().getConfigVariables());
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theXInstantiator != null)
			theXInstantiator.instantiate();
		if (theYInstantiator != null)
			theYInstantiator.instantiate();
		if (theWidthInstantiator != null)
			theWidthInstantiator.instantiate();
		if (theHeightInstantiator != null)
			theHeightInstantiator.instantiate();
		if (theWindowIconInstantiator != null)
			theWindowIconInstantiator.instantiate();
		// If there is a <config> model in the environment (and we're the root window),
		// provide the config with the application environment in case of load failure.
		// This will allow the application's title and icon to appear in the window asking the user to choose a backup.
		// Since the model instances are not available at this point (and won't be when the error is encountered),
		// only literal values for title and icon are supported. If either is not a literal, a default will be used in its place.
		for (ModelComponentId configV : theConfigVariables) {
			ModelComponentInstantiator<?> configMV = getElement().getModels().getComponent(configV);
			if (configMV.getBacking() instanceof AppEnvironment.EnvironmentConfigurable)
				((AppEnvironment.EnvironmentConfigurable) configMV.getBacking()).setAppEnvironment(this);
		}
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);

		theX.set(theXInstantiator == null ? null : theXInstantiator.get(models), null);
		theY.set(theYInstantiator == null ? null : theYInstantiator.get(models), null);
		theWidth.set(theWidthInstantiator == null ? null : theWidthInstantiator.get(models), null);
		theHeight.set(theHeightInstantiator == null ? null : theHeightInstantiator.get(models), null);
		theWindowIcon.set(theWindowIconInstantiator == null ? null : theWindowIconInstantiator.get(models), null);
	}
}
