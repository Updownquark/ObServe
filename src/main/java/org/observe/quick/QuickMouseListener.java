package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public abstract class QuickMouseListener extends QuickEventListener.Abstract {
	public static final String MOUSE_LISTENER = "mouse-listener";

	public static abstract class Def<L extends QuickMouseListener> extends QuickEventListener.Def.Abstract<L> {
		protected Def(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
		}

		@Override
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, MOUSE_LISTENER);
			super.update(session.asElement(session.getFocusType().getSuperElement()));
		}

		@Override
		public abstract QuickMouseListener.Interpreted<? extends L> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<L extends QuickMouseListener> extends QuickEventListener.Interpreted.Abstract<L> {
		protected Interpreted(Def<? super L> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super L> getDefinition() {
			return (Def<? super L>) super.getDefinition();
		}
	}

	public interface MouseListenerContext extends ListenerContext {
		SettableValue<Integer> getX();

		SettableValue<Integer> getY();

		public class Default extends ListenerContext.Default implements MouseListenerContext {
			private final SettableValue<Integer> theX;
			private final SettableValue<Integer> theY;
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

	private final SettableValue<SettableValue<Integer>> theEventX;
	private final SettableValue<SettableValue<Integer>> theEventY;

	protected QuickMouseListener(QuickMouseListener.Interpreted<?> interpreted, ExElement parent) {
		super(interpreted, parent);
		theEventX = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(int.class))
			.build();
		theEventY = SettableValue.build(theEventX.getType()).build();
	}

	public void setListenerContext(MouseListenerContext ctx) throws ModelInstantiationException {
		setListenerContext((ListenerContext) ctx);
		theEventX.set(ctx.getX(), null);
		theEventY.set(ctx.getY(), null);
	}

	@Override
	protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
		super.updateModel(interpreted, myModels);
		satisfyContextValue("x", ModelTypes.Value.INT, SettableValue.flatten(theEventX), myModels);
		satisfyContextValue("y", ModelTypes.Value.INT, SettableValue.flatten(theEventY), myModels);
	}

	public enum MouseButton {
		Left, Middle, Right
	}

	public enum MouseMoveEventType {
		Move("on-mouse-move"), Enter("on-mouse-enter"), Exit("on-mouse-exit");

		public final String elementName;

		private MouseMoveEventType(String elementName) {
			this.elementName = elementName;
		}
	}

	public enum MouseButtonEventType {
		Press("on-mouse-press"), Release("on-mouse-release"), Click("on-click");

		public final String elementName;

		private MouseButtonEventType(String elementName) {
			this.elementName = elementName;
		}
	}

	public interface MouseButtonListenerContext extends MouseListenerContext {
		SettableValue<MouseButton> getMouseButton();

		public class Default extends MouseListenerContext.Default implements MouseButtonListenerContext {
			private final SettableValue<MouseButton> theMouseButton;

			public Default(SettableValue<Boolean> altPressed, SettableValue<Boolean> ctrlPressed, SettableValue<Boolean> shiftPressed,
				SettableValue<Integer> x, SettableValue<Integer> y, SettableValue<MouseButton> mouseButton) {
				super(altPressed, ctrlPressed, shiftPressed, x, y);
				theMouseButton = mouseButton;
			}

			public Default() {
				theMouseButton = SettableValue.build(MouseButton.class).build();
			}

			@Override
			public SettableValue<MouseButton> getMouseButton() {
				return theMouseButton;
			}
		}
	}

	public interface ScrollListenerContext extends MouseListenerContext {
		SettableValue<Integer> getScrollAmount();

		public class Default extends MouseListenerContext.Default implements ScrollListenerContext {
			private final SettableValue<Integer> theScrollAmount;

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

	public static class QuickMouseMoveListener extends QuickMouseListener {
		public static class Def extends QuickMouseListener.Def<QuickMouseMoveListener> {
			private final MouseMoveEventType theEventType;

			public Def(ExElement.Def<?> parent, QonfigElement element, MouseMoveEventType eventType) {
				super(parent, element);
				theEventType = eventType;
			}

			public MouseMoveEventType getEventType() {
				return theEventType;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
					theEventType.elementName);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickMouseMoveListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseMoveListener create(ExElement parent) {
				return new QuickMouseMoveListener(this, parent);
			}
		}

		private MouseMoveEventType theEventType;

		public QuickMouseMoveListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		public MouseMoveEventType getEventType() {
			return theEventType;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickMouseMoveListener.Interpreted myInterpreted = (QuickMouseMoveListener.Interpreted) interpreted;
			theEventType = myInterpreted.getDefinition().getEventType();
		}
	}

	public static class QuickMouseButtonListener extends QuickMouseListener {
		public static final String MOUSE_PRESS_LISTENER = "on-mouse-press";

		public static final ExElement.AttributeValueGetter<QuickMouseButtonListener, Interpreted, Def> BUTTON = ExElement.AttributeValueGetter
			.of(d -> d.getButton(), i -> i.getDefinition().getButton(), QuickMouseButtonListener::getButton,
				"The button that this listener will listen to.  If not specified, the listener will trigger for any button.");

		public static final ExElement.AttributeValueGetter<QuickMouseButtonListener, Interpreted, Def> CLICK_COUNT = ExElement.AttributeValueGetter
			.of(d -> d.getClickCount(), i -> i.getDefinition().getClickCount(), QuickMouseButtonListener::getClickCount,
				"The number of consecutive clicks necessary to trigger this listener.  If not specified, the listener will trigger for any number of clicks.");

		public static class Def extends QuickMouseListener.Def<QuickMouseButtonListener> {
			private final MouseButtonEventType theEventType;
			private MouseButton theButton;
			private int theClickCount;

			public Def(ExElement.Def<?> parent, QonfigElement element, MouseButtonEventType eventType) {
				super(parent, element);
				theEventType = eventType;
			}

			public MouseButtonEventType getEventType() {
				return theEventType;
			}

			public MouseButton getButton() {
				return theButton;
			}

			public int getClickCount() {
				return theClickCount;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
					theEventType.elementName);
				forAttribute(session.getAttributeDef(null, null, "button"), BUTTON);
				if (theEventType == MouseButtonEventType.Click) {
					forAttribute(session.getAttributeDef(null, null, "click-count"), CLICK_COUNT);
					String txt = session.getAttributeText("click-count");
					if (txt == null)
						theClickCount = 0;
					else {
						theClickCount = Integer.parseInt(txt);
						if (theClickCount < 1)
							session.reporting().at(session.getAttributeValuePosition("click-count"))
							.error("click-count must be greater than zero");
					}
				}
				super.update(session.asElement(session.getFocusType().getSuperElement() // mouse-button-listener
					.getSuperElement() // mouse-listener
					));
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
				DynamicModelValue.satisfyDynamicValueType("button", getModels(), ModelTypes.Value.forType(MouseButton.class));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickMouseButtonListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickMouseButtonListener create(ExElement parent) {
				return new QuickMouseButtonListener(this, parent);
			}
		}

		private final SettableValue<SettableValue<MouseButton>> theEventButton;
		private MouseButtonEventType theEventType;
		private MouseButton theButton;
		private int theClickCount;

		public QuickMouseButtonListener(QuickMouseButtonListener.Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
			theEventButton = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MouseButton>> parameterized(MouseButton.class)).build();
		}

		public MouseButtonEventType getEventType() {
			return theEventType;
		}

		public MouseButton getButton() {
			return theButton;
		}

		public int getClickCount() {
			return theClickCount;
		}

		public void setListenerContext(MouseButtonListenerContext ctx) throws ModelInstantiationException {
			setListenerContext((MouseListenerContext) ctx);
			theEventButton.set(ctx.getMouseButton(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			satisfyContextValue("button", ModelTypes.Value.forType(MouseButton.class), SettableValue.flatten(theEventButton), myModels);
			QuickMouseButtonListener.Interpreted myInterpreted = (QuickMouseButtonListener.Interpreted) interpreted;
			theEventType = myInterpreted.getDefinition().getEventType();
			theButton = myInterpreted.getDefinition().getButton();
			theClickCount = myInterpreted.getDefinition().getClickCount();
		}
	}

	public static class QuickScrollListener extends QuickMouseListener {
		public static final String SCROLL_LISTENER = "on-scroll";

		public static class Def extends QuickMouseListener.Def<QuickScrollListener> {
			public Def(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				ExElement.checkElement(session.getFocusType(), QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
					SCROLL_LISTENER);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		public static class Interpreted extends QuickMouseListener.Interpreted<QuickScrollListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public QuickScrollListener create(ExElement parent) {
				return new QuickScrollListener(this, parent);
			}
		}

		private final SettableValue<SettableValue<Integer>> theScrollAmount;

		public QuickScrollListener(Interpreted interpreted, ExElement parent) {
			super(interpreted, parent);
			theScrollAmount = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class)).build();
		}

		public void setListenerContext(ScrollListenerContext ctx) throws ModelInstantiationException {
			setListenerContext((MouseListenerContext) ctx);
			theScrollAmount.set(ctx.getScrollAmount(), null);
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			satisfyContextValue("scrollAmount", ModelTypes.Value.INT, SettableValue.flatten(theScrollAmount), myModels);
		}
	}
}
