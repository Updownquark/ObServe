package org.observe.quick;

import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public abstract class QuickMouseListener extends QuickEventListener.Abstract {
	public static final String MOUSE_LISTENER = "mouse-listener";
	private static final SingleTypeTraceability<QuickMouseListener, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, MOUSE_LISTENER, Def.class, Interpreted.class,
			QuickMouseListener.class);

	public static abstract class Def<L extends QuickMouseListener> extends QuickEventListener.Def.Abstract<L> {
		private ModelComponentId theEventXValue;
		private ModelComponentId theEventYValue;

		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		public ModelComponentId getEventXValue() {
			return theEventXValue;
		}

		public ModelComponentId getEventYValue() {
			return theEventYValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theEventXValue = elModels.getElementValueModelId("x");
			theEventYValue = elModels.getElementValueModelId("y");
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

	private ModelComponentId theEventXValue;
	private ModelComponentId theEventYValue;
	private SettableValue<SettableValue<Integer>> theEventX;
	private SettableValue<SettableValue<Integer>> theEventY;

	protected QuickMouseListener(Object id) {
		super(id);
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

	public enum MouseButton {
		Left, Middle, Right
	}

	public enum MouseMoveEventType {
		Move("on-mouse-move"), Enter("on-mouse-enter"), Exit("on-mouse-exit");

		public final String elementName;
		public final SingleTypeTraceability<QuickMouseMoveListener, QuickMouseMoveListener.Interpreted, QuickMouseMoveListener.Def> traceability;

		private MouseMoveEventType(String elementName) {
			this.elementName = elementName;
			traceability = ElementTypeTraceability.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION,
				elementName, QuickMouseMoveListener.Def.class, QuickMouseMoveListener.Interpreted.class, QuickMouseMoveListener.class);
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

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type, MouseMoveEventType eventType) {
				super(parent, type);
				theEventType = eventType;
			}

			public MouseMoveEventType getEventType() {
				return theEventType;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(theEventType.traceability.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
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
			public QuickMouseMoveListener create() {
				return new QuickMouseMoveListener(getIdentity());
			}
		}

		private MouseMoveEventType theEventType;

		public QuickMouseMoveListener(Object id) {
			super(id);
		}

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

	public static abstract class QuickMouseButtonListener extends QuickMouseListener {
		public static final String MOUSE_BUTTON_LISTENER = "mouse-button-listener";
		private static final SingleTypeTraceability<QuickMouseButtonListener, Interpreted<?>, Def<?>> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, MOUSE_BUTTON_LISTENER, Def.class,
				Interpreted.class, QuickMouseButtonListener.class);

		public static abstract class Def<L extends QuickMouseButtonListener> extends QuickMouseListener.Def<L> {
			private ModelComponentId theButtonValue;
			private MouseButton theButton;

			protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			public ModelComponentId getButtonValue() {
				return theButtonValue;
			}

			@QonfigAttributeGetter("button")
			public MouseButton getButton() {
				return theButton;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
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

		public static abstract class Interpreted<L extends QuickMouseButtonListener> extends QuickMouseListener.Interpreted<L> {
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

		protected QuickMouseButtonListener(Object id) {
			super(id);
			theEventButton = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<MouseButton>> parameterized(MouseButton.class)).build();
		}

		public MouseButton getButton() {
			return theButton;
		}

		public void setListenerContext(MouseButtonListenerContext ctx) throws ModelInstantiationException {
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

	public static class QuickMouseClickListener extends QuickMouseButtonListener {
		public static final String ON_MOUSE_CLICK = "on-click";
		private static final SingleTypeTraceability<QuickMouseClickListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, ON_MOUSE_CLICK, Def.class,
				Interpreted.class, QuickMouseClickListener.class);

		public static class Def extends QuickMouseButtonListener.Def<QuickMouseClickListener> {
			private int theClickCount;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@QonfigAttributeGetter("click-count")
			public int getClickCount() {
				return theClickCount;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

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

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMouseClickListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
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

		public QuickMouseClickListener(Object id) {
			super(id);
		}

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

	public static class QuickMousePressedListener extends QuickMouseButtonListener {
		public static final String ON_MOUSE_PRESSED = "on-mouse-press";
		private static final SingleTypeTraceability<QuickMousePressedListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, ON_MOUSE_PRESSED, Def.class,
				Interpreted.class, QuickMousePressedListener.class);

		public static class Def extends QuickMouseButtonListener.Def<QuickMousePressedListener> {

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMousePressedListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
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

		public QuickMousePressedListener(Object id) {
			super(id);
		}
	}

	public static class QuickMouseReleasedListener extends QuickMouseButtonListener {
		public static final String ON_MOUSE_RELEASED = "on-mouse-release";
		private static final SingleTypeTraceability<QuickMouseReleasedListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, ON_MOUSE_RELEASED, Def.class,
				Interpreted.class, QuickMouseReleasedListener.class);

		public static class Def extends QuickMouseButtonListener.Def<QuickMouseReleasedListener> {

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}

		}

		public static class Interpreted extends QuickMouseButtonListener.Interpreted<QuickMouseReleasedListener> {
			public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
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

		public QuickMouseReleasedListener(Object id) {
			super(id);
		}
	}

	public static class QuickScrollListener extends QuickMouseListener {
		public static final String SCROLL_LISTENER = "on-scroll";
		private static final SingleTypeTraceability<QuickScrollListener, Interpreted, Def> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, SCROLL_LISTENER, Def.class,
				Interpreted.class, QuickScrollListener.class);

		public static class Def extends QuickMouseListener.Def<QuickScrollListener> {
			private ModelComponentId theScrollAmountValue;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			public ModelComponentId getScrollAmountValue() {
				return theScrollAmountValue;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theScrollAmountValue = elModels.getElementValueModelId("scrollAmount");
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
			public QuickScrollListener create() {
				return new QuickScrollListener(getIdentity());
			}
		}

		private ModelComponentId theScrollAmountValue;
		private SettableValue<SettableValue<Integer>> theScrollAmount;

		public QuickScrollListener(Object id) {
			super(id);
			theScrollAmount = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Integer>> parameterized(Integer.class)).build();
		}

		public void setListenerContext(ScrollListenerContext ctx) throws ModelInstantiationException {
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
