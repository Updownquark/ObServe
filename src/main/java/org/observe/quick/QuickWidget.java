package org.observe.quick;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** The base class for widgets in Quick */
public interface QuickWidget extends QuickTextElement, QuickWithBackground {
	public static final String WIDGET = "widget";

	/**
	 * The definition of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this definition is for
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = WIDGET,
		interpretation = Interpreted.class,
		instance = QuickWidget.class)
	public interface Def<W extends QuickWidget> extends QuickTextElement.Def<W>, QuickWithBackground.Def<W> {
		@Override
		QuickWidgetStyle.Def getStyle();

		/** @return The container definition that this widget is a child of */
		QuickContainer.Def<?, ?> getParent();

		/** @return This widget's name, typically for debugging */
		@QonfigAttributeGetter("name")
		String getName();

		/** @return The tool tip to display when the user hovers over this widget */
		@QonfigAttributeGetter("tooltip")
		CompiledExpression getTooltip();

		/** @return The expression determining when this widget is to be visible */
		@QonfigAttributeGetter("visible")
		CompiledExpression isVisible();

		/** @return This widget's border */
		@QonfigChildGetter("border")
		QuickBorder.Def<?> getBorder();

		@QonfigChildGetter("event-listener")
		List<QuickEventListener.Def<?>> getEventListeners();

		@QonfigChildGetter("dialog")
		List<QuickDialog.Def<?>> getDialogs();

		/**
		 * @param parent The parent container interpretation
		 * @return The new widget interpretation
		 */
		Interpreted<? extends W> interpret(ExElement.Interpreted<?> parent);

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <W> The type of widget that this definition is for
		 */
		public abstract class Abstract<W extends QuickWidget> extends QuickStyledElement.Def.Abstract<W> implements Def<W> {
			private String theName;
			private CompiledExpression theTooltip;
			private CompiledExpression isVisible;

			private ModelComponentId theHoveredValue;
			private ModelComponentId theFocusedValue;
			private ModelComponentId thePressedValue;
			private ModelComponentId theRightPressedValue;

			private QuickBorder.Def<?> theBorder;
			private final List<QuickEventListener.Def<?>> theEventListeners;
			private final List<QuickDialog.Def<?>> theDialogs;

			/**
			 * @param parent The parent container definition
			 * @param type The element type that this widget is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
				theEventListeners = new ArrayList<>();
				theDialogs = new ArrayList<>();
			}

			@Override
			public QuickWidgetStyle.Def getStyle() {
				return (QuickWidgetStyle.Def) super.getStyle();
			}

			@Override
			public QuickContainer.Def<?, ?> getParent() {
				ExElement.Def<?> parent = getParentElement();
				return parent instanceof QuickContainer.Def ? (QuickContainer.Def<?, ?>) parent : null;
			}

			@Override
			public String getName() {
				return theName;
			}

			@Override
			public CompiledExpression getTooltip() {
				return theTooltip;
			}

			@Override
			public CompiledExpression isVisible() {
				return isVisible;
			}

			@Override
			public ModelComponentId getHoveredValue() {
				return theHoveredValue;
			}

			@Override
			public ModelComponentId getFocusedValue() {
				return theFocusedValue;
			}

			@Override
			public ModelComponentId getPressedValue() {
				return thePressedValue;
			}

			@Override
			public ModelComponentId getRightPressedValue() {
				return theRightPressedValue;
			}

			@Override
			public QuickBorder.Def<?> getBorder() {
				return theBorder;
			}

			@Override
			public List<QuickEventListener.Def<?>> getEventListeners() {
				return Collections.unmodifiableList(theEventListeners);
			}

			@Override
			public List<QuickDialog.Def<?>> getDialogs() {
				return Collections.unmodifiableList(theDialogs);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));
				theName = session.getAttributeText("name");
				theTooltip = getAttributeExpression("tooltip", session);
				isVisible = getAttributeExpression("visible", session);
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theHoveredValue = elModels.getElementValueModelId("hovered");
				theFocusedValue = elModels.getElementValueModelId("focused");
				thePressedValue = elModels.getElementValueModelId("pressed");
				theRightPressedValue = elModels.getElementValueModelId("rightPressed");

				theBorder = ExElement.useOrReplace(QuickBorder.Def.class, theBorder, session, "border");
				CollectionUtils.synchronize(theEventListeners, session.forChildren("event-listener"),
					(l, s) -> ExElement.typesEqual(l.getElement(), s.getElement())).simpleE(s -> {
						QuickEventListener.Def<?> listener = s.interpret(QuickEventListener.Def.class);
						listener.update(s);
						return listener;
					})//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue())).adjust();

				CollectionUtils
				.synchronize(theDialogs, session.forChildren("dialog"), (l, s) -> ExElement.typesEqual(l.getElement(), s.getElement()))
				.simpleE(s -> {
					QuickDialog.Def<?> listener = s.interpret(QuickDialog.Def.class);
					listener.update(s);
					return listener;
				})//
				.onCommonX(el -> el.getLeftValue().update(el.getRightValue())).adjust();
			}

			@Override
			protected QuickWidgetStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickWidgetStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	/**
	 * An interpretation of a {@link QuickWidget}
	 *
	 * @param <W> The type of widget that this interpretation is for
	 */
	public interface Interpreted<W extends QuickWidget> extends QuickTextElement.Interpreted<W>, QuickWithBackground.Interpreted<W> {
		@Override
		Def<? super W> getDefinition();

		default String getName() {
			return getDefinition().getName();
		}

		@Override
		QuickWidgetStyle.Interpreted getStyle();

		/** @return The parent container of this widget interpretation, if any */
		QuickContainer.Interpreted<?, ?> getParent();

		/** @return This widget's border */
		QuickBorder.Interpreted<?> getBorder();

		/** @return The type of the widget produced by this interpretation */
		TypeToken<? extends W> getWidgetType() throws ExpressoInterpretationException;

		/** @return The tool tip to display when the user hovers over this widget */
		InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip();

		/** @return The value determining when this widget is to be visible */
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible();

		List<QuickEventListener.Interpreted<?>> getEventListeners();

		List<QuickDialog.Interpreted<?>> getDialogs();

		/**
		 * Produces a widget instance
		 *
		 * @return The new widget
		 */
		W create();

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <W> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<W extends QuickWidget> extends QuickStyledElement.Interpreted.Abstract<W> implements Interpreted<W> {
			private QuickBorder.Interpreted<?> theBorder;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTooltip;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible;
			private final List<QuickEventListener.Interpreted<?>> theEventListeners;
			private final List<QuickDialog.Interpreted<?>> theDialogs;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super W> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theEventListeners = new ArrayList<>();
				theDialogs = new ArrayList<>();
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public QuickWidgetStyle.Interpreted getStyle() {
				return (QuickWidgetStyle.Interpreted) super.getStyle();
			}

			@Override
			public QuickContainer.Interpreted<?, ?> getParent() {
				ExElement.Interpreted<?> parent = getParentElement();
				return parent instanceof QuickContainer.Interpreted ? (QuickContainer.Interpreted<?, ?>) parent : null;
			}

			@Override
			public QuickBorder.Interpreted<?> getBorder() {
				return theBorder;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTooltip() {
				return theTooltip;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isVisible() {
				return isVisible;
			}

			@Override
			public List<QuickEventListener.Interpreted<?>> getEventListeners() {
				return Collections.unmodifiableList(theEventListeners);
			}

			@Override
			public List<QuickDialog.Interpreted<?>> getDialogs() {
				return Collections.unmodifiableList(theDialogs);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				if (getDefinition().getBorder() == null)
					theBorder = null;
				else if (theBorder == null || theBorder.getDefinition() != getDefinition().getBorder())
					theBorder = getDefinition().getBorder().interpret(this);
				if (theBorder != null)
					theBorder.updateBorder(env);
				theTooltip = getDefinition().getTooltip() == null ? null
					: getDefinition().getTooltip().interpret(ModelTypes.Value.STRING, env);
				isVisible = getDefinition().isVisible() == null ? null
					: getDefinition().isVisible().interpret(ModelTypes.Value.BOOLEAN, env);

				CollectionUtils
				.synchronize(theEventListeners, getDefinition().getEventListeners(), (l, d) -> l.getIdentity() == d.getIdentity())//
				.simpleE(l -> {
					QuickEventListener.Interpreted<?> listener = l.interpret(this);
					listener.updateListener(env);
					return listener;
				})//
				.onCommonX(el -> el.getLeftValue().updateListener(env))//
				.adjust();

				CollectionUtils.synchronize(theDialogs, getDefinition().getDialogs(), (l, d) -> l.getIdentity() == d.getIdentity())//
				.simpleE(l -> {
					QuickDialog.Interpreted<?> listener = l.interpret(this);
					listener.updateDialog(env);
					return listener;
				})//
				.onCommonX(el -> el.getLeftValue().updateDialog(env))//
				.adjust();
			}

			@Override
			public void destroy() {
				if (theBorder != null) {
					theBorder.destroy();
					theBorder = null;
				}
				for (QuickEventListener.Interpreted<?> listener : theEventListeners)
					listener.destroy();
				theEventListeners.clear();
				super.destroy();
			}
		}
	}

	@Override
	QuickWidgetStyle getStyle();

	/** @return The parent container, if any */
	QuickContainer<?> getParent();

	ObservableValue<String> getName();

	/** @return This widget's border */
	QuickBorder getBorder();

	/** @return The tool tip to display when the user hovers over this widget */

	SettableValue<String> getTooltip();

	/** @return The value determining when this widget is to be visible */
	SettableValue<Boolean> isVisible();

	ObservableCollection<QuickEventListener> getEventListeners();

	ObservableCollection<QuickDialog> getDialogs();

	@Override
	QuickWidget copy(ExElement parent);

	/** An abstract {@link QuickWidget} implementation */
	public abstract class Abstract extends QuickStyledElement.Abstract implements QuickWidget {
		private SettableValue<String> theName;
		private SettableValue<SettableValue<Boolean>> isHovered;
		private SettableValue<SettableValue<Boolean>> isFocused;
		private SettableValue<SettableValue<Boolean>> isPressed;
		private SettableValue<SettableValue<Boolean>> isRightPressed;

		private ModelValueInstantiator<SettableValue<String>> theTooltipInstantiator;
		private ModelValueInstantiator<SettableValue<Boolean>> theVisibleInstantiator;
		private ModelComponentId theHoveredValue;
		private ModelComponentId theFocusedValue;
		private ModelComponentId thePressedValue;
		private ModelComponentId theRightPressedValue;

		private SettableValue<SettableValue<String>> theTooltip;
		private SettableValue<SettableValue<Boolean>> isVisible;

		private QuickBorder theBorder;
		private ObservableCollection<QuickEventListener> theEventListeners;
		private ObservableCollection<QuickDialog> theDialogs;

		protected Abstract(Object id) {
			super(id);
			theName = SettableValue.build(String.class).build();
			theTooltip = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			isVisible = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			theEventListeners = ObservableCollection.build(QuickEventListener.class).build();
			theDialogs = ObservableCollection.build(QuickDialog.class).build();

			isHovered = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			isFocused = SettableValue.build(isHovered.getType()).build();
			isPressed = SettableValue.build(isHovered.getType()).build();
			isRightPressed = SettableValue.build(isHovered.getType()).build();
		}

		@Override
		public QuickWidgetStyle getStyle() {
			return (QuickWidgetStyle) super.getStyle();
		}

		@Override
		public QuickContainer<?> getParent() {
			ExElement parent = getParentElement();
			return parent instanceof QuickContainer ? (QuickContainer<?>) parent : null;
		}

		@Override
		public ObservableValue<String> getName() {
			return theName.unsettable();
		}

		@Override
		public QuickBorder getBorder() {
			return theBorder;
		}

		@Override
		public SettableValue<String> getTooltip() {
			return SettableValue.flatten(theTooltip);
		}

		@Override
		public SettableValue<Boolean> isVisible() {
			return SettableValue.flatten(isVisible, () -> true);
		}

		@Override
		public SettableValue<Boolean> isHovered() {
			return SettableValue.flatten(isHovered);
		}

		@Override
		public SettableValue<Boolean> isFocused() {
			return SettableValue.flatten(isFocused);
		}

		@Override
		public SettableValue<Boolean> isPressed() {
			return SettableValue.flatten(isPressed);
		}

		@Override
		public SettableValue<Boolean> isRightPressed() {
			return SettableValue.flatten(isRightPressed);
		}

		@Override
		public ObservableCollection<QuickEventListener> getEventListeners() {
			return theEventListeners.flow().unmodifiable(false).collect();
		}

		@Override
		public ObservableCollection<QuickDialog> getDialogs() {
			return theDialogs.flow().unmodifiable(false).collect();
		}

		@Override
		public void setContext(BackgroundContext ctx) throws ModelInstantiationException {
			isHovered.set(ctx.isHovered(), null);
			isFocused.set(ctx.isFocused(), null);
			isPressed.set(ctx.isPressed(), null);
			isRightPressed.set(ctx.isRightPressed(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickWidget.Interpreted<?> myInterpreted = (QuickWidget.Interpreted<?>) interpreted;

			theHoveredValue = myInterpreted.getDefinition().getHoveredValue();
			theFocusedValue = myInterpreted.getDefinition().getFocusedValue();
			thePressedValue = myInterpreted.getDefinition().getPressedValue();
			theRightPressedValue = myInterpreted.getDefinition().getRightPressedValue();

			theName.set(myInterpreted.getName(), null);
			theTooltipInstantiator = myInterpreted.getTooltip() == null ? null : myInterpreted.getTooltip().instantiate();
			theVisibleInstantiator = myInterpreted.isVisible() == null ? null : myInterpreted.isVisible().instantiate();

			theBorder = myInterpreted.getBorder() == null ? null : myInterpreted.getBorder().create();
			if (theBorder != null)
				theBorder.update(myInterpreted.getBorder(), this);

			try (Transaction t = theEventListeners.lock(true, null)) {
				CollectionUtils
				.synchronize(theEventListeners, myInterpreted.getEventListeners(), (l, i) -> l.getIdentity() == i.getIdentity())//
				.simple(l -> {
					QuickEventListener listener = l.create();
					listener.update(l, this);
					return listener;
				})//
				.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
				.adjust();
			}

			try (Transaction t = theDialogs.lock(true, null)) {
				CollectionUtils.synchronize(theDialogs, myInterpreted.getDialogs(), (l, i) -> l.getIdentity() == i.getIdentity())//
				.simple(l -> {
					QuickDialog listener = l.create();
					listener.update(l, this);
					return listener;
				})//
				.onCommon(el -> el.getLeftValue().update(el.getRightValue(), this))//
				.adjust();
			}
		}

		@Override
		public void instantiated() {
			super.instantiated();

			if (theTooltipInstantiator != null)
				theTooltipInstantiator.instantiate();
			if (theVisibleInstantiator != null)
				theVisibleInstantiator.instantiate();

			if (theBorder != null)
				theBorder.instantiated();
			for (QuickEventListener listener : theEventListeners)
				listener.instantiated();
			for (QuickDialog dialog : theDialogs)
				dialog.instantiated();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theHoveredValue, myModels, SettableValue.flatten(isHovered));
			ExFlexibleElementModelAddOn.satisfyElementValue(theFocusedValue, myModels, SettableValue.flatten(isFocused));
			ExFlexibleElementModelAddOn.satisfyElementValue(thePressedValue, myModels, SettableValue.flatten(isPressed));
			ExFlexibleElementModelAddOn.satisfyElementValue(theRightPressedValue, myModels, SettableValue.flatten(isRightPressed));

			theTooltip.set(theTooltipInstantiator == null ? null : theTooltipInstantiator.get(myModels), null);
			isVisible.set(theVisibleInstantiator == null ? null : theVisibleInstantiator.get(myModels), null);

			if (theBorder != null)
				theBorder.instantiate(myModels);

			for (QuickEventListener listener : theEventListeners)
				listener.instantiate(myModels);

			for (QuickDialog dialog : theDialogs)
				dialog.instantiate(myModels);
		}

		@Override
		public QuickWidget.Abstract copy(ExElement parent) {
			QuickWidget.Abstract copy = (QuickWidget.Abstract) super.copy(parent);

			copy.theName = SettableValue.build(String.class).build();
			copy.theTooltip = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			copy.isVisible = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			copy.theEventListeners = ObservableCollection.build(QuickEventListener.class).build();

			copy.isHovered = SettableValue.build(isHovered.getType()).build();
			copy.isFocused = SettableValue.build(isFocused.getType()).build();
			copy.isPressed = SettableValue.build(isPressed.getType()).build();
			copy.isRightPressed = SettableValue.build(isRightPressed.getType()).build();

			if (theBorder != null)
				copy.theBorder = theBorder.copy(copy);
			for (QuickEventListener listener : theEventListeners)
				copy.theEventListeners.add(listener.copy(copy));
			for (QuickDialog dialog : theDialogs)
				copy.theDialogs.add(dialog.copy(copy));

			return copy;
		}
	}

	public interface QuickWidgetStyle extends QuickTextStyle, QuickBackgroundStyle {
		public interface Def extends QuickTextStyle.Def, QuickBackgroundStyle.Def {
			public class Default extends QuickTextStyle.Def.Abstract implements QuickWidgetStyle.Def {
				private final QuickStyleAttributeDef theColor;
				private final QuickStyleAttributeDef theMouseCursor;

				public Default(QuickInstanceStyle.Def parent, QuickWidget.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "widget");
					theColor = addApplicableAttribute(typeStyle.getAttribute("color"));
					theMouseCursor = addApplicableAttribute(typeStyle.getAttribute("mouse-cursor"));
				}

				@Override
				public QuickStyleAttributeDef getColor() {
					return theColor;
				}

				@Override
				public QuickStyleAttributeDef getMouseCursor() {
					return theMouseCursor;
				}

				@Override
				public QuickWidgetStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, (QuickWidget.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public interface Interpreted extends QuickTextStyle.Interpreted, QuickBackgroundStyle.Interpreted {
			@Override
			QuickWidgetStyle create(QuickStyledElement styledElement);

			public class Default extends QuickTextStyle.Interpreted.Abstract implements QuickWidgetStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theColor;
				private QuickElementStyleAttribute<MouseCursor> theMouseCursor;

				public Default(Def definition, QuickWidget.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getColor() {
					return theColor;
				}

				@Override
				public QuickElementStyleAttribute<MouseCursor> getMouseCursor() {
					return theMouseCursor;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theColor = get(cache.getAttribute(getDefinition().getColor(), Color.class, env));
					theMouseCursor = get(cache.getAttribute(getDefinition().getMouseCursor(), MouseCursor.class, env));
				}

				@Override
				public QuickWidgetStyle create(QuickStyledElement styledElement) {
					return new QuickWidgetStyle.Default();
				}
			}
		}

		public class Default extends QuickTextStyle.Abstract implements QuickWidgetStyle {
			private QuickStyleAttribute<Color> theColorAttr;
			private QuickStyleAttribute<MouseCursor> theMouseCursorAttr;
			private ObservableValue<Color> theColor;
			private ObservableValue<MouseCursor> theMouseCursor;

			@Override
			public ObservableValue<Color> getColor() {
				return theColor;
			}

			@Override
			public ObservableValue<MouseCursor> getMouseCursor() {
				return theMouseCursor;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement) {
				super.update(interpreted, styledElement);

				QuickWidgetStyle.Interpreted myInterpreted = (QuickWidgetStyle.Interpreted) interpreted;

				theColorAttr = myInterpreted.getColor().getAttribute();
				theMouseCursorAttr = myInterpreted.getMouseCursor().getAttribute();

				theColor = getApplicableAttribute(theColorAttr);
				theMouseCursor = getApplicableAttribute(theMouseCursorAttr);
			}

			@Override
			public Default copy(QuickStyledElement styledElement) {
				Default copy = (Default) super.copy(styledElement);

				copy.theColor = copy.getApplicableAttribute(theColorAttr);
				copy.theMouseCursor = copy.getApplicableAttribute(theMouseCursorAttr);

				return copy;
			}
		}
	}
}
