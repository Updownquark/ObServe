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

	private ModelComponentId theEventXValue;
	private ModelComponentId theEventYValue;
	private SettableValue<Integer> theEventX;
	private SettableValue<Integer> theEventY;

	/** @param id The element ID of this listener */
	protected QuickMouseListener(Object id) {
		super(id);
		theEventX = SettableValue.create();
		theEventY = SettableValue.create();
	}

	/** @return The X-coordinate of the screen location relative to is listener's owning widget where the event occurred */
	public SettableValue<Integer> getEventX() {
		return theEventX;
	}

	/** @return The Y-coordinate of the screen location relative to is listener's owning widget where the event occurred */
	public SettableValue<Integer> getEventY() {
		return theEventY;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);
		QuickMouseListener.Interpreted<?> myInterpreted = (QuickMouseListener.Interpreted<?>) interpreted;
		theEventXValue = myInterpreted.getDefinition().getEventXValue();
		theEventYValue = myInterpreted.getDefinition().getEventYValue();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);
		ExFlexibleElementModelAddOn.satisfyElementValue(theEventXValue, myModels, theEventX);
		ExFlexibleElementModelAddOn.satisfyElementValue(theEventYValue, myModels, theEventY);
		return myModels;
	}

	@Override
	protected QuickMouseListener clone() {
		QuickMouseListener copy = (QuickMouseListener) super.clone();

		copy.theEventX = SettableValue.create();
		copy.theEventY = SettableValue.create();

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
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
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
		private SettableValue<MouseButton> theEventButton;
		private MouseButton theButton;

		/** @param id The element ID for this listener */
		protected QuickMouseButtonListener(Object id) {
			super(id);
			theEventButton = SettableValue.create();
		}

		/**
		 * @return The button type that an event must be for for this listener's action to be called, or null if the action will be called
		 *         for any button event
		 */
		public MouseButton getButton() {
			return theButton;
		}

		/** @return The mouse button the user operated to result in this event */
		public SettableValue<MouseButton> getEventButton() {
			return theEventButton;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
			Interpreted<?> myInterpreted = (Interpreted<?>) interpreted;
			theEventButtonValue = myInterpreted.getDefinition().getButtonValue();
			theButton = myInterpreted.getDefinition().getButton();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theEventButtonValue, myModels, theEventButton);
			return myModels;
		}

		@Override
		public QuickMouseButtonListener copy(ExElement parent) {
			QuickMouseButtonListener copy = (QuickMouseButtonListener) super.copy(parent);

			copy.theEventButton = SettableValue.create();

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
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
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
		private SettableValue<Integer> theScrollAmount;

		QuickScrollListener(Object id) {
			super(id);
			theScrollAmount = SettableValue.create();
		}

		/** @return The amount the user scrolled for this event */
		public SettableValue<Integer> getScrollAmount() {
			return theScrollAmount;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			QuickScrollListener.Interpreted myInterpreted = (QuickScrollListener.Interpreted) interpreted;
			theScrollAmountValue = myInterpreted.getDefinition().getScrollAmountValue();
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theScrollAmountValue, myModels, theScrollAmount);
			return myModels;
		}

		@Override
		protected QuickScrollListener clone() {
			QuickScrollListener copy = (QuickScrollListener) super.clone();

			copy.theScrollAmount = SettableValue.create();

			return copy;
		}
	}
}
