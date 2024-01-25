package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExMultiElementTraceable;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** Listens for mouse events on a widget */
public abstract class QuickMouseListener extends QuickEventListener.Abstract {
	/** The XML name of this type */
	public static final String MOUSE_LISTENER = "mouse-listener";

	/**
	 * Definition of a {@link QuickMouseListener}
	 *
	 * @param <L> The sub-type of listener to create
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = MOUSE_LISTENER,
		interpretation = Interpreted.class,
		instance = QuickMouseListener.class)
	public static abstract class Def<L extends QuickMouseListener> extends QuickEventListener.Def.Abstract<L> {
		private ModelComponentId theEventXValue;
		private ModelComponentId theEventYValue;

		/**
		 * @param parent The parent element of this listener
		 * @param type The Qonfig type of this listener
		 */
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/**
		 * @return The model ID of the model value containing the X-coordinate of the mouse's position relative to the upper-left corner of
		 *         the widget for the current event
		 */
		public ModelComponentId getEventXValue() {
			return theEventXValue;
		}

		/**
		 * @return The model ID of the model value containing the Y-coordinate of the mouse's position relative to the upper-left corner of
		 *         the widget for the current event
		 */
		public ModelComponentId getEventYValue() {
			return theEventYValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theEventXValue = elModels.getElementValueModelId("x");
			theEventYValue = elModels.getElementValueModelId("y");
		}

		@Override
		public abstract QuickMouseListener.Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);
	}

	/**
	 * Interpretation of a {@link QuickMouseListener}
	 *
	 * @param <L> The sub-type of listener to create
	 */
	public static abstract class Interpreted<L extends QuickMouseListener> extends QuickEventListener.Interpreted.Abstract<L> {
		/**
		 * @param definition The definition to interpret
		 * @param parent The parent element for this listener
		 */
		protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super L> getDefinition() {
			return (Def<? super L>) super.getDefinition();
		}
	}

	/** Context for a mouse listener */
	public interface MouseListenerContext extends ListenerContext {
		/** @return The X-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event */
		SettableValue<Integer> getX();

		/** @return The Y-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event */
		SettableValue<Integer> getY();

		/** Default {@link MouseListenerContext} implementation */
		public class Default extends ListenerContext.Default implements MouseListenerContext {
			private final SettableValue<Integer> theX;
			private final SettableValue<Integer> theY;

			/**
			 * @param altPressed Whether the user is currently pressing the ALT key
			 * @param ctrlPressed Whether the user is currently pressing the CTRL key
			 * @param shiftPressed Whether the user is currently pressing the SHIFT key
			 * @param x The X-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event
			 * @param y The Y-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event
			 */
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y) {
				super(altPressed, ctrlPressed, shiftPressed);
				theX = x;
				theY = y;
			}

			Default() {
				theX = SettableValue.build(int.class).withValue(0).build();
				theY = SettableValue.build(int.class).withValue(0).build();
			}

			@Override
			public SettableValue<Integer> getX() {
				return theX;
			}

			@Override
			public SettableValue<Integer> getY() {
				return theY;
			}
		}
	}

	private ModelComponentId theEventXValue;
	private ModelComponentId theEventYValue;
	private SettableValue<SettableValue<Integer>> theEventX;
	private SettableValue<SettableValue<Integer>> theEventY;

	/** @param id The element ID of this listener */
	protected QuickMouseListener(Object id) {
		super(id);
		theEventX = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class))
			.build();
		theEventY = SettableValue.build(theEventX.getType()).build();
	}

	/** @param ctx The listener context from the Quick implementation */
	public void setListenerContext(MouseListenerContext ctx) {
		setListenerContext((ListenerContext) ctx);
		theEventX.set(ctx.getX(), null);
		theEventY.set(ctx.getY(), null);
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		QuickMouseListener.Interpreted<?> myInterpreted = (QuickMouseListener.Interpreted<?>) interpreted;
		theEventXValue = myInterpreted.getDefinition().getEventXValue();
		theEventYValue = myInterpreted.getDefinition().getEventYValue();
	}

	@Override
	protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		super.doInstantiate(myModels);
		ExFlexibleElementModelAddOn.satisfyElementValue(theEventXValue, myModels, SettableValue.flatten(theEventX));
		ExFlexibleElementModelAddOn.satisfyElementValue(theEventYValue, myModels, SettableValue.flatten(theEventY));
	}

	@Override
	protected QuickMouseListener clone() {
		QuickMouseListener copy = (QuickMouseListener) super.clone();

		copy.theEventX = SettableValue.build(theEventX.getType()).build();
		copy.theEventY = SettableValue.build(theEventX.getType()).build();

		return copy;
	}

	/** Recognized mouse button types in Quick */
	public enum MouseButton {
		/** The left mouse button */
		Left,
		/** The middle mouse button (often the scroll wheel) */
		Middle,
		/** The right mouse button */
		Right
	}

	/** Recognized types of mouse movement in Quick */
	public enum MouseMoveEventType {
		/** Simple movement event */
		Move("on-mouse-move"),
		/** When the mouse enters a widget from outside the widget's bounds or from the bounds of one of its opaque components */
		Enter("on-mouse-enter"),
		/** When the mouse leaves a widget to a place outside the widget's bounds or into the bounds of one of its opaque components */
		Exit("on-mouse-exit");

		/** The XML name of this event type */
		public final String elementName;

		private MouseMoveEventType(String elementName) {
			this.elementName = elementName;
		}
	}

	/** Context for a {@link QuickMouseButtonListener} */
	public interface MouseButtonListenerContext extends MouseListenerContext {
		/** @return The mouse button that was pressed for the current event */
		SettableValue<MouseButton> getMouseButton();

		/** Default {@link MouseButtonListenerContext} implementation */
		public class Default extends MouseListenerContext.Default implements MouseButtonListenerContext {
			private final SettableValue<MouseButton> theMouseButton;

			/**
			 * @param altPressed Whether the user is currently pressing the ALT key
			 * @param ctrlPressed Whether the user is currently pressing the CTRL key
			 * @param shiftPressed Whether the user is currently pressing the SHIFT key
			 * @param x The X-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event
			 * @param y The Y-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event
			 * @param mouseButton The mouse button that was pressed for the current event
			 */
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y, SettableValue<MouseButton> mouseButton) {
				super(altPressed, ctrlPressed, shiftPressed, x, y);
				theMouseButton = mouseButton;
			}

			/** Creates context with default value containers */
			public Default() {
				theMouseButton = SettableValue.build(MouseButton.class).build();
			}

			@Override
			public SettableValue<MouseButton> getMouseButton() {
				return theMouseButton;
			}
		}
	}

	/** Context for a {@link QuickScrollListener} */
	public interface ScrollListenerContext extends MouseListenerContext {
		/** @return The amount that the scroll event intends to be scrolled */
		SettableValue<Integer> getScrollAmount();

		/** Default {@link ScrollListenerContext} implementation */
		public class Default extends MouseListenerContext.Default implements ScrollListenerContext {
			private final SettableValue<Integer> theScrollAmount;

			/**
			 * @param altPressed Whether the user is currently pressing the ALT key
			 * @param ctrlPressed Whether the user is currently pressing the CTRL key
			 * @param shiftPressed Whether the user is currently pressing the SHIFT key
			 * @param x The X-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event
			 * @param y The Y-coordinate of the mouse's position relative to the upper-left corner of the widget for the current event
			 * @param scrollAmount The amount that the scroll event intends to be scrolled
			 */
			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y, SettableValue<Integer> scrollAmount) {
				super(altPressed, ctrlPressed, shiftPressed, x, y);
				theScrollAmount = scrollAmount;
			}

			@Override
			public SettableValue<Integer> getScrollAmount() {
				return theScrollAmount;
			}
		}
	}

	/** Listens for events of the mouse moving over a widget */
	public static class QuickMouseMoveListener extends QuickMouseListener {
		/** Definition for a {@link QuickMouseMoveListener} */
		@ExMultiElementTraceable({
			@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
				qonfigType = "on-mouse-move",
				interpretation = Interpreted.class,
				instance = QuickMouseMoveListener.class),
			@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = "on-mouse-enter",
			interpretation = Interpreted.class,
			instance = QuickMouseMoveListener.class),
			@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = "on-mouse-exit",
			interpretation = Interpreted.class,
			instance = QuickMouseMoveListener.class) })
		public static class Def extends QuickMouseListener.Def<QuickMouseMoveListener> {
			private final MouseMoveEventType theEventType;

			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 * @param eventType The movement event type that this listener will listen for
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type, MouseMoveEventType eventType) {
				super(parent, type);
				theEventType = eventType;
			}

			/** @return The movement event type that this listener will listen for */
			public MouseMoveEventType getEventType() {
				return theEventType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation for a {@link QuickMouseMoveListener} */
		public static class Interpreted extends QuickMouseListener.Interpreted<QuickMouseMoveListener> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseMoveListener create() {
				return new QuickMouseMoveListener(getIdentity());
			}
		}

		private MouseMoveEventType theEventType;

		QuickMouseMoveListener(Object id) {
			super(id);
		}

		/** @return The movement event type that this listener will listen for */
		public MouseMoveEventType getEventType() {
			return theEventType;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			QuickMouseMoveListener.Interpreted myInterpreted = (QuickMouseMoveListener.Interpreted) interpreted;
			theEventType = myInterpreted.getDefinition().getEventType();
		}
	}

	/** Listens for events related to mouse buttons over a widget */
	public static abstract class QuickMouseButtonListener extends QuickMouseListener {
		/** The XML name of this type */
		public static final String MOUSE_BUTTON_LISTENER = "mouse-button-listener";

		/**
		 * Definition for a {@link QuickMouseButtonListener}
		 *
		 * @param <L> The sub-type of listener to create
		 */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = MOUSE_BUTTON_LISTENER,
			interpretation = Interpreted.class,
			instance = QuickMouseButtonListener.class)
		public static abstract class Def<L extends QuickMouseButtonListener> extends QuickMouseListener.Def<L> {
			private ModelComponentId theButtonValue;
			private MouseButton theButton;

			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 */
			protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return The model ID of the model value containing the type of the button that the current event is for */
			public ModelComponentId getButtonValue() {
				return theButtonValue;
			}

			/**
			 * @return The button type that an event must be for for this listener's action to be called, or null if the action will be
			 *         called for any button event
			 */
			@QonfigAttributeGetter("button")
			public MouseButton getButton() {
				return theButton;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theButtonValue = elModels.getElementValueModelId("button");

				String button = session.getAttributeText("button");
				if (button == null)
					theButton = null;
				else {
					switch (button) {
					case "left":
						theButton = MouseButton.Left;
						break;
					case "right":
						theButton = MouseButton.Right;
						break;
					case "middle":
						theButton = MouseButton.Middle;
						break;
					default:
						throw new IllegalStateException("Unrecognized mouse button: '" + button + "'");
					}
				}
				getAddOn(ExWithElementModel.Def.class).satisfyElementValueType(theButtonValue, ModelTypes.Value.forType(MouseButton.class));
			}
		}

		/**
		 * Interpretation for a {@link QuickMouseButtonListener}
		 *
		 * @param <L> The sub-type of listener to create
		 */
		public static abstract class Interpreted<L extends QuickMouseButtonListener> extends QuickMouseListener.Interpreted<L> {
			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element for this listener
			 */
			protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super L> getDefinition() {
				return (Def<? super L>) super.getDefinition();
			}
		}

		private ModelComponentId theEventButtonValue;
		private SettableValue<SettableValue<MouseButton>> theEventButton;
		private MouseButton theButton;

		/** @param id The element ID for this listener */
		protected QuickMouseButtonListener(Object id) {
			super(id);
			theEventButton = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MouseButton>> parameterized(MouseButton.class)).build();
		}

		/**
		 * @return The button type that an event must be for for this listener's action to be called, or null if the action will be called
		 *         for any button event
		 */
		public MouseButton getButton() {
			return theButton;
		}

		/** @param ctx The context for this listener from the Quick implementation */
		public void setListenerContext(MouseButtonListenerContext ctx) {
			setListenerContext((MouseListenerContext) ctx);
			theEventButton.set(ctx.getMouseButton(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
			theEventButtonValue = myInterpreted.getDefinition().getButtonValue();
			theButton = myInterpreted.getDefinition().getButton();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theEventButtonValue, myModels, SettableValue.flatten(theEventButton));
		}

		@Override
		public QuickMouseButtonListener copy(ExElement parent) {
			QuickMouseButtonListener copy = (QuickMouseButtonListener) super.copy(parent);

			copy.theEventButton = SettableValue.build(theEventButton.getType()).build();

			return copy;
		}
	}

	/** Listens for mouse clicks on a widget */
	public static class QuickMouseClickListener extends QuickMouseButtonListener {
		/** The XML name of this type */
		public static final String ON_MOUSE_CLICK = "on-click";

		/** Definition for a {@link QuickMouseClickListener} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = ON_MOUSE_CLICK,
			interpretation = Interpreted.class,
			instance = QuickMouseClickListener.class)
		public static class Def extends QuickMouseButtonListener.Def<QuickMouseClickListener> {
			private int theClickCount;

			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/**
			 * @return The click count an event must have for this listener's action to be called, or 0 if the action will be called for all
			 *         click events
			 */
			@QonfigAttributeGetter("click-count")
			public int getClickCount() {
				return theClickCount;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

				String txt = session.getAttributeText("click-count");
				if (txt == null)
					theClickCount = 0;
				else {
					theClickCount = Integer.parseInt(txt);
					if (theClickCount < 1)
						session.reporting().at(session.attributes().get("click-count").getLocatedContent())
						.error("click-count must be greater than zero");
				}
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		/** Interpretation for a {@link QuickMouseClickListener} */
		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMouseClickListener> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseClickListener create() {
				return new QuickMouseClickListener(getIdentity());
			}
		}

		private int theClickCount;

		QuickMouseClickListener(Object id) {
			super(id);
		}

		/**
		 * @return The click count an event must have for this listener's action to be called, or 0 if the action will be called for all
		 *         click events
		 */
		public int getClickCount() {
			return theClickCount;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			Interpreted myInterpreted = (Interpreted) interpreted;
			theClickCount = myInterpreted.getDefinition().getClickCount();
		}
	}

	/** Listens for the user pressing a mouse button over a widget */
	public static class QuickMousePressedListener extends QuickMouseButtonListener {
		/** The XML name of this type */
		public static final String ON_MOUSE_PRESSED = "on-mouse-press";

		/** Definition for a {@link QuickMousePressedListener} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = ON_MOUSE_PRESSED,
			interpretation = Interpreted.class,
			instance = QuickMousePressedListener.class)
		public static class Def extends QuickMouseButtonListener.Def<QuickMousePressedListener> {
			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		/** Interpretation for a {@link QuickMousePressedListener} */
		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMousePressedListener> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMousePressedListener create() {
				return new QuickMousePressedListener(getIdentity());
			}
		}

		QuickMousePressedListener(Object id) {
			super(id);
		}
	}

	/** Listens for the user releasing a mouse button over a widget */
	public static class QuickMouseReleasedListener extends QuickMouseButtonListener {
		/** The XML name of this type */
		public static final String ON_MOUSE_RELEASED = "on-mouse-release";

		/** Definition for a {@link QuickMouseReleasedListener} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = ON_MOUSE_RELEASED,
			interpretation = Interpreted.class,
			instance = QuickMouseReleasedListener.class)
		public static class Def extends QuickMouseButtonListener.Def<QuickMouseReleasedListener> {
			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation for a {@link QuickMouseReleasedListener} */
		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMouseReleasedListener> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseReleasedListener create() {
				return new QuickMouseReleasedListener(getIdentity());
			}
		}

		QuickMouseReleasedListener(Object id) {
			super(id);
		}
	}

	/** Listens for scroll events on a widget */
	public static class QuickScrollListener extends QuickMouseListener {
		/** The XML name of this type */
		public static final String SCROLL_LISTENER = "on-scroll";

		/** Definition for a {@link QuickScrollListener} */
		@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
			qonfigType = SCROLL_LISTENER,
			interpretation = Interpreted.class,
			instance = QuickScrollListener.class)
		public static class Def extends QuickMouseListener.Def<QuickScrollListener> {
			private ModelComponentId theScrollAmountValue;

			/**
			 * @param parent The parent element of this listener
			 * @param type The Qonfig type of this listener
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			/** @return The model ID of the model value containing the scroll amount for the current scroll event */
			public ModelComponentId getScrollAmountValue() {
				return theScrollAmountValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theScrollAmountValue = elModels.getElementValueModelId("scrollAmount");
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation for a {@link QuickScrollListener} */
		public static class Interpreted extends QuickMouseListener.Interpreted<QuickScrollListener> {
			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickScrollListener create() {
				return new QuickScrollListener(getIdentity());
			}
		}

		private ModelComponentId theScrollAmountValue;
		private SettableValue<SettableValue<Integer>> theScrollAmount;

		QuickScrollListener(Object id) {
			super(id);
			theScrollAmount = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class)).build();
		}

		/** @param ctx The listener context from the Quick implementation */
		public void setListenerContext(ScrollListenerContext ctx) {
			setListenerContext((MouseListenerContext) ctx);
			theScrollAmount.set(ctx.getScrollAmount(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickScrollListener.Interpreted myInterpreted = (QuickScrollListener.Interpreted) interpreted;
			theScrollAmountValue = myInterpreted.getDefinition().getScrollAmountValue();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theScrollAmountValue, myModels, SettableValue.flatten(theScrollAmount));
		}

		@Override
		protected QuickScrollListener clone() {
			QuickScrollListener copy = (QuickScrollListener) super.clone();

			copy.theScrollAmount = SettableValue.build(theScrollAmount.getType()).build();

			return copy;
		}
	}
}
