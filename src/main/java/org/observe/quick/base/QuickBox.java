package org.observe.quick.base;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickContainer;
import org.observe.quick.QuickWidget;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickBox extends QuickContainer.Abstract<QuickWidget> {
	public static final String BOX = "box";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = BOX,
		interpretation = Interpreted.class,
		instance = QuickBox.class)
	public static class Def<W extends QuickBox> extends QuickContainer.Def.Abstract<W, QuickWidget> {
		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("layout")
		public QuickLayout.Def<?> getLayout() {
			return getAddOn(QuickLayout.Def.class);
		}

		@Override
		public QuickBoxStyle.Def getStyle() {
			return (QuickBoxStyle.Def) super.getStyle();
		}

		@Override
		protected QuickBoxStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
			return new QuickBoxStyle.Def.Default(parentStyle, this, style);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			if (getAddOn(QuickLayout.Def.class) == null) {
				String layout = session.getAttributeText("layout");
				throw new QonfigInterpretationException("No Quick interpretation for layout " + layout,
					session.attributes().get("layout").getLocatedContent());
			}
		}

		@Override
		public Interpreted<W> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}
	}

	public static class Interpreted<W extends QuickBox> extends QuickContainer.Interpreted.Abstract<W, QuickWidget> {
		public Interpreted(Def<? super W> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super W> getDefinition() {
			return (Def<? super W>) super.getDefinition();
		}

		public QuickLayout.Interpreted<?> getLayout() {
			return getAddOn(QuickLayout.Interpreted.class);
		}

		@Override
		public QuickBoxStyle.Interpreted getStyle() {
			return (QuickBoxStyle.Interpreted) super.getStyle();
		}

		@Override
		public W create() {
			return (W) new QuickBox(getIdentity());
		}
	}

	public QuickBox(Object id) {
		super(id);
	}

	public QuickLayout getLayout() {
		return getAddOn(QuickLayout.class);
	}

	@Override
	public QuickBoxStyle getStyle() {
		return (QuickBoxStyle) super.getStyle();
	}

	public static interface QuickBoxStyle extends QuickWidget.QuickWidgetStyle {
		public interface Def extends QuickWidget.QuickWidgetStyle.Def {
			QuickStyleAttributeDef getLightSource();

			QuickStyleAttributeDef getLightColor();

			QuickStyleAttributeDef getShadowColor();

			QuickStyleAttributeDef getCornerRadius();

			QuickStyleAttributeDef getMaxShadeAmount();

			QuickStyleAttributeDef getShading();

			public class Default extends QuickWidgetStyle.Def.Default implements QuickBoxStyle.Def {
				private final QuickStyleAttributeDef theLightSource;
				private final QuickStyleAttributeDef theLightColor;
				private final QuickStyleAttributeDef theShadowColor;
				private final QuickStyleAttributeDef theCornerRadius;
				private final QuickStyleAttributeDef theMaxShadeAmount;
				private final QuickStyleAttributeDef theShading;

				public Default(QuickInstanceStyle.Def parent, QuickBox.Def<?> styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), getElement(),
						QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, "box");
					theLightSource = addApplicableAttribute(typeStyle.getAttribute("light-source"));
					theLightColor = addApplicableAttribute(typeStyle.getAttribute("light-color"));
					theShadowColor = addApplicableAttribute(typeStyle.getAttribute("shadow-color"));
					theCornerRadius = addApplicableAttribute(typeStyle.getAttribute("corner-radius"));
					theMaxShadeAmount = addApplicableAttribute(typeStyle.getAttribute("max-shade-amount"));
					theShading = addApplicableAttribute(typeStyle.getAttribute("shading"));
				}

				@Override
				public QuickStyleAttributeDef getLightSource() {
					return theLightSource;
				}

				@Override
				public QuickStyleAttributeDef getLightColor() {
					return theLightColor;
				}

				@Override
				public QuickStyleAttributeDef getShadowColor() {
					return theShadowColor;
				}

				@Override
				public QuickStyleAttributeDef getCornerRadius() {
					return theCornerRadius;
				}

				@Override
				public QuickStyleAttributeDef getMaxShadeAmount() {
					return theMaxShadeAmount;
				}

				@Override
				public QuickStyleAttributeDef getShading() {
					return theShading;
				}

				@Override
				public QuickBoxStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new Interpreted.Default(this, (QuickBox.Interpreted<?>) parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public interface Interpreted extends QuickWidgetStyle.Interpreted {
			QuickElementStyleAttribute<Float> getLightSource();

			QuickElementStyleAttribute<Color> getLightColor();

			QuickElementStyleAttribute<Color> getShadowColor();

			QuickElementStyleAttribute<QuickSize> getCornerRadius();

			QuickElementStyleAttribute<Float> getMaxShadeAmount();

			QuickElementStyleAttribute<QuickShading> getShading();

			@Override
			QuickBoxStyle create(QuickStyledElement styledElement);

			public class Default extends QuickWidgetStyle.Interpreted.Default implements QuickBoxStyle.Interpreted {
				private QuickElementStyleAttribute<Float> theLightSource;
				private QuickElementStyleAttribute<Color> theLightColor;
				private QuickElementStyleAttribute<Color> theShadowColor;
				private QuickElementStyleAttribute<QuickSize> theCornerRadius;
				private QuickElementStyleAttribute<Float> theMaxShadeAmount;
				private QuickElementStyleAttribute<QuickShading> theShading;

				public Default(QuickBoxStyle.Def definition, QuickBox.Interpreted<?> styledElement, QuickInstanceStyle.Interpreted parent,
					QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Float> getLightSource() {
					return theLightSource;
				}

				@Override
				public QuickElementStyleAttribute<Color> getLightColor() {
					return theLightColor;
				}

				@Override
				public QuickElementStyleAttribute<Color> getShadowColor() {
					return theShadowColor;
				}

				@Override
				public QuickElementStyleAttribute<QuickSize> getCornerRadius() {
					return theCornerRadius;
				}

				@Override
				public QuickElementStyleAttribute<Float> getMaxShadeAmount() {
					return theMaxShadeAmount;
				}

				@Override
				public QuickElementStyleAttribute<QuickShading> getShading() {
					return theShading;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theLightSource = get(cache.getAttribute(getDefinition().getLightSource(), Float.class, env));
					theLightColor = get(cache.getAttribute(getDefinition().getLightColor(), Color.class, env));
					theShadowColor = get(cache.getAttribute(getDefinition().getShadowColor(), Color.class, env));
					theCornerRadius = get(cache.getAttribute(getDefinition().getCornerRadius(), QuickSize.class, env));
					theMaxShadeAmount = get(cache.getAttribute(getDefinition().getMaxShadeAmount(), Float.class, env));
					theShading = get(cache.getAttribute(getDefinition().getShading(), QuickShading.class, env));
				}

				@Override
				public QuickBoxStyle create(QuickStyledElement styledElement) {
					return new QuickBoxStyle.Default();
				}
			}
		}

		ObservableValue<Float> getLightSource();

		ObservableValue<Color> getLightColor();

		ObservableValue<Color> getShadowColor();

		ObservableValue<QuickSize> getCornerRadius();

		ObservableValue<Float> getMaxShadeAmount();

		ObservableValue<QuickShading> getShading();

		public class Default extends QuickWidgetStyle.Default implements QuickBoxStyle {
			private QuickStyleAttribute<Float> theLightSourceAttr;
			private QuickStyleAttribute<Color> theLightColorAttr;
			private QuickStyleAttribute<Color> theShadowColorAttr;
			private QuickStyleAttribute<QuickSize> theCornerRadiusAttr;
			private QuickStyleAttribute<Float> theMaxShadeAmountAttr;
			private QuickStyleAttribute<QuickShading> theShadingAttr;
			private ObservableValue<Float> theLightSource;
			private ObservableValue<Color> theLightColor;
			private ObservableValue<Color> theShadowColor;
			private ObservableValue<QuickSize> theCornerRadius;
			private ObservableValue<Float> theMaxShadeAmount;
			private ObservableValue<QuickShading> theShading;

			@Override
			public ObservableValue<Float> getLightSource() {
				return theLightSource;
			}

			@Override
			public ObservableValue<Color> getLightColor() {
				return theLightColor;
			}

			@Override
			public ObservableValue<Color> getShadowColor() {
				return theShadowColor;
			}

			@Override
			public ObservableValue<QuickSize> getCornerRadius() {
				return theCornerRadius;
			}

			@Override
			public ObservableValue<Float> getMaxShadeAmount() {
				return theMaxShadeAmount;
			}

			@Override
			public ObservableValue<QuickShading> getShading() {
				return theShading;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyledElement styledElement) {
				super.update(interpreted, styledElement);

				QuickBoxStyle.Interpreted myInterpreted = (QuickBoxStyle.Interpreted) interpreted;

				theLightSourceAttr = myInterpreted.getLightSource().getAttribute();
				theLightColorAttr = myInterpreted.getLightColor().getAttribute();
				theShadowColorAttr = myInterpreted.getShadowColor().getAttribute();
				theCornerRadiusAttr = myInterpreted.getCornerRadius().getAttribute();
				theMaxShadeAmountAttr = myInterpreted.getMaxShadeAmount().getAttribute();
				theShadingAttr = myInterpreted.getShading().getAttribute();

				theLightSource = getApplicableAttribute(theLightSourceAttr);
				theLightColor = getApplicableAttribute(theLightColorAttr);
				theShadowColor = getApplicableAttribute(theShadowColorAttr);
				theCornerRadius = getApplicableAttribute(theCornerRadiusAttr);
				theMaxShadeAmount = getApplicableAttribute(theMaxShadeAmountAttr);
				theShading = getApplicableAttribute(theShadingAttr);
			}

			@Override
			public QuickBoxStyle.Default copy(QuickStyledElement styledElement) {
				QuickBoxStyle.Default copy = (QuickBoxStyle.Default) super.copy(styledElement);

				copy.theLightSource = copy.getApplicableAttribute(theLightSourceAttr);
				copy.theLightColor = copy.getApplicableAttribute(theLightColorAttr);
				copy.theShadowColor = copy.getApplicableAttribute(theShadowColorAttr);
				copy.theCornerRadius = copy.getApplicableAttribute(theCornerRadiusAttr);
				copy.theMaxShadeAmount = copy.getApplicableAttribute(theMaxShadeAmountAttr);
				copy.theShading = copy.getApplicableAttribute(theShadingAttr);

				return copy;
			}
		}
	}
}
