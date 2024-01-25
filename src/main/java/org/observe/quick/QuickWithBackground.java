package org.observe.quick;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
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
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** A quick element with a background that can be styled */
public interface QuickWithBackground extends QuickStyledElement {
	/**
	 * The definition of a {@link QuickWithBackground}
	 *
	 * @param <E> The sub-type of the element to create
	 */
	public interface Def<E extends QuickWithBackground> extends QuickStyledElement.Def<E> {
		@Override
		QuickBackgroundStyle.Def getStyle();

		/**
		 * @return The model ID of the boolean value in this element's model describing whether the mouse pointer is currently positioned
		 *         over this element
		 */
		ModelComponentId getHoveredValue();

		/**
		 * @return The model ID of the boolean value in this element's model describing whether this element currently has the user's focus
		 *         within the application
		 */
		ModelComponentId getFocusedValue();

		/**
		 * @return The model ID of the boolean value in this element's model describing whether the mouse pointer is currently positioned
		 *         over this element with the left mouse button pressed
		 */
		ModelComponentId getPressedValue();

		/**
		 * @return The model ID of the boolean value in this element's model describing whether the mouse pointer is currently positioned
		 *         over this element with the right mouse button pressed
		 */
		ModelComponentId getRightPressedValue();

		/**
		 * Abstract {@link QuickWithBackground} definition implementation
		 *
		 * @param <E> The sub-type of element to create
		 */
		public static abstract class Abstract<E extends QuickWithBackground> extends QuickStyledElement.Def.Abstract<E> implements Def<E> {
			private ModelComponentId theHoveredValue;
			private ModelComponentId theFocusedValue;
			private ModelComponentId thePressedValue;
			private ModelComponentId theRightPressedValue;

			/**
			 * @param parent The parent element for this element
			 * @param type The Qonfig type of this element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public QuickBackgroundStyle.Def getStyle() {
				return (QuickBackgroundStyle.Def) super.getStyle();
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
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));
				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theHoveredValue = elModels.getElementValueModelId("hovered");
				theFocusedValue = elModels.getElementValueModelId("focused");
				thePressedValue = elModels.getElementValueModelId("pressed");
				theRightPressedValue = elModels.getElementValueModelId("rightPressed");
			}

			@Override
			protected QuickBackgroundStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickBackgroundStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	/**
	 * The interpretation of a {@link QuickWithBackground}
	 *
	 * @param <E> The sub-type of the element to create
	 */
	public interface Interpreted<E extends QuickWithBackground> extends QuickStyledElement.Interpreted<E> {
		@Override
		Def<? super E> getDefinition();

		@Override
		QuickBackgroundStyle.Interpreted getStyle();

		/**
		 * Abstract {@link QuickWithBackground} interpretation implementation
		 *
		 * @param <E> The sub-type of element to create
		 */
		public static class Abstract<E extends QuickWithBackground> extends QuickStyledElement.Interpreted.Abstract<E>
		implements Interpreted<E> {
			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super E> getDefinition() {
				return (Def<? super E>) super.getDefinition();
			}

			@Override
			public QuickBackgroundStyle.Interpreted getStyle() {
				return (QuickBackgroundStyle.Interpreted) super.getStyle();
			}
		}
	}

	/** Context for a {@link QuickWithBackground} */
	public interface BackgroundContext {
		/** @return Whether the mouse pointer is currently positioned over the element */
		SettableValue<Boolean> isHovered();

		/** @return Whether the element currently has the user's focus within the application */
		SettableValue<Boolean> isFocused();

		/** @return Whether the mouse pointer is currently positioned over the element and the left mouse button is down */
		SettableValue<Boolean> isPressed();

		/** @return Whether the mouse pointer is currently positioned over the element and the right mouse button is down */
		SettableValue<Boolean> isRightPressed();

		/** Default {@link BackgroundContext} implementation */
		public class Default implements BackgroundContext {
			private final SettableValue<Boolean> isHovered;
			private final SettableValue<Boolean> isFocused;
			private final SettableValue<Boolean> isPressed;
			private final SettableValue<Boolean> isRightPressed;

			/**
			 * @param hovered Whether the mouse pointer is currently positioned over the element
			 * @param focused Whether the element currently has the user's focus within the application
			 * @param pressed Whether the mouse pointer is currently positioned over the element and the left mouse button is down
			 * @param rightPressed Whether the mouse pointer is currently positioned over the element and the right mouse button is down
			 */
			public Default(SettableValue<Boolean> hovered, SettableValue<Boolean> focused, SettableValue<Boolean> pressed,
				SettableValue<Boolean> rightPressed) {
				isHovered = hovered;
				isFocused = focused;
				isPressed = pressed;
				isRightPressed = rightPressed;
			}

			/** Creates context with default value containers */
			public Default() {
				this(SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(boolean.class).withValue(false).build(), //
					SettableValue.build(boolean.class).withValue(false).build());
			}

			@Override
			public SettableValue<Boolean> isHovered() {
				return isHovered;
			}

			@Override
			public SettableValue<Boolean> isFocused() {
				return isFocused;
			}

			@Override
			public SettableValue<Boolean> isPressed() {
				return isPressed;
			}

			@Override
			public SettableValue<Boolean> isRightPressed() {
				return isRightPressed;
			}
		}
	}

	@Override
	QuickBackgroundStyle getStyle();

	/** @return Whether the mouse pointer is currently positioned over this element */
	SettableValue<Boolean> isHovered();

	/** @return Whether this element currently has the user's focus within the application */
	SettableValue<Boolean> isFocused();

	/** @return Whether the mouse pointer is currently positioned over this element and the left mouse button is down */
	SettableValue<Boolean> isPressed();

	/** @return Whether the mouse pointer is currently positioned over this element and the right mouse button is down */
	SettableValue<Boolean> isRightPressed();

	/**
	 * @param ctx The background context for this element from the Quick implementation
	 * @throws ModelInstantiationException If the context could not be installed in the element's models
	 */
	void setContext(BackgroundContext ctx) throws ModelInstantiationException;

	/** An abstract {@link QuickWithBackground} implementation */
	public abstract class Abstract extends QuickStyledElement.Abstract implements QuickWithBackground {
		private SettableValue<SettableValue<Boolean>> isHovered;
		private SettableValue<SettableValue<Boolean>> isFocused;
		private SettableValue<SettableValue<Boolean>> isPressed;
		private SettableValue<SettableValue<Boolean>> isRightPressed;

		private ModelComponentId theHoveredValue;
		private ModelComponentId theFocusedValue;
		private ModelComponentId thePressedValue;
		private ModelComponentId theRightPressedValue;

		/** @param id The element identifier for this element */
		protected Abstract(Object id) {
			super(id);
			isHovered = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
			isFocused = SettableValue.build(isHovered.getType()).build();
			isPressed = SettableValue.build(isHovered.getType()).build();
			isRightPressed = SettableValue.build(isHovered.getType()).build();
		}

		@Override
		public QuickBackgroundStyle getStyle() {
			return (QuickBackgroundStyle) super.getStyle();
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
		public void setContext(BackgroundContext ctx) throws ModelInstantiationException {
			isHovered.set(ctx.isHovered(), null);
			isFocused.set(ctx.isFocused(), null);
			isPressed.set(ctx.isPressed(), null);
			isRightPressed.set(ctx.isRightPressed(), null);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);

			QuickWithBackground.Interpreted<?> myInterpreted = (QuickWithBackground.Interpreted<?>) interpreted;

			theHoveredValue = myInterpreted.getDefinition().getHoveredValue();
			theFocusedValue = myInterpreted.getDefinition().getFocusedValue();
			thePressedValue = myInterpreted.getDefinition().getPressedValue();
			theRightPressedValue = myInterpreted.getDefinition().getRightPressedValue();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			ExFlexibleElementModelAddOn.satisfyElementValue(theHoveredValue, myModels, SettableValue.flatten(isHovered));
			ExFlexibleElementModelAddOn.satisfyElementValue(theFocusedValue, myModels, SettableValue.flatten(isFocused));
			ExFlexibleElementModelAddOn.satisfyElementValue(thePressedValue, myModels, SettableValue.flatten(isPressed));
			ExFlexibleElementModelAddOn.satisfyElementValue(theRightPressedValue, myModels, SettableValue.flatten(isRightPressed));
		}

		@Override
		public QuickWithBackground.Abstract copy(ExElement parent) {
			QuickWithBackground.Abstract copy = (QuickWithBackground.Abstract) super.copy(parent);

			copy.isHovered = SettableValue.build(isHovered.getType()).build();
			copy.isFocused = SettableValue.build(isFocused.getType()).build();
			copy.isPressed = SettableValue.build(isPressed.getType()).build();
			copy.isRightPressed = SettableValue.build(isRightPressed.getType()).build();

			return copy;
		}
	}

	/** Style for a {@link QuickWithBackground} */
	public interface QuickBackgroundStyle extends QuickInstanceStyle {
		/** Definition for a {@link QuickBackgroundStyle} */
		public interface Def extends QuickInstanceStyle.Def {
			/** @return The style attribute for the element's background color */
			QuickStyleAttributeDef getColor();

			/** @return The style attribute for the mouse cursor appearance when hovered over the element */
			QuickStyleAttributeDef getMouseCursor();

			/** Default {@link QuickBackgroundStyle} definition implementation */
			public class Default extends QuickInstanceStyle.Def.Abstract implements QuickBackgroundStyle.Def {
				private final QuickStyleAttributeDef theColor;
				private final QuickStyleAttributeDef theMouseCursor;

				/**
				 * @param parent The parent style for this style to inherit from
				 * @param styledElement The element being styled
				 * @param wrapped The generic compiled style that this style class wraps
				 */
				public Default(QuickInstanceStyle.Def parent, QuickWithBackground.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, "with-background");
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
				public QuickBackgroundStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, (QuickWithBackground.Interpreted<?>) parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		/** Interpretation for a {@link QuickBackgroundStyle} */
		public interface Interpreted extends QuickInstanceStyle.Interpreted {
			/** @return The style attribute for the element's background color */
			QuickElementStyleAttribute<Color> getColor();

			/** @return The style attribute for the mouse cursor appearance when hovered over the element */
			QuickElementStyleAttribute<MouseCursor> getMouseCursor();

			@Override
			QuickBackgroundStyle create(QuickStyledElement styledElement);

			/** Default {@link QuickBackgroundStyle} interpretation implementation */
			public class Default extends QuickInstanceStyle.Interpreted.Abstract implements QuickBackgroundStyle.Interpreted {
				private QuickElementStyleAttribute<Color> theColor;
				private QuickElementStyleAttribute<MouseCursor> theMouseCursor;

				/**
				 * @param definition The style definition to interpret
				 * @param styledElement The element being styled
				 * @param parent The parent style for this style to inherit from
				 * @param wrapped The generic interpreted style that this style class wraps
				 */
				public Default(Def definition, QuickWithBackground.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
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
				public QuickBackgroundStyle create(QuickStyledElement styledElement) {
					return new QuickBackgroundStyle.Default();
				}
			}
		}

		/** @return The background color for the element */
		ObservableValue<Color> getColor();

		/** @return The the mouse cursor appearance when hovered over the element */
		ObservableValue<MouseCursor> getMouseCursor();

		/** Default {@link QuickBackgroundStyle} implementation */
		public class Default extends QuickInstanceStyle.Abstract implements QuickBackgroundStyle {
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

				QuickBackgroundStyle.Interpreted myInterpreted = (QuickBackgroundStyle.Interpreted) interpreted;

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
