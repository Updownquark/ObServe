package org.observe.quick.ext;

import java.awt.Color;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.quick.QuickSize;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickStyledAddOn;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigAddOn;

/** Add-on allowing specification of styled shading on boxes */
public class QuickShaded extends ExAddOn.Abstract<QuickStyledElement> {
	/** The XML name of this add-on */
	public static final String SHADED = "shaded";

	/** {@link QuickShaded} definition */
	@ExElementTraceable(toolkit = QuickXInterpretation.X,
		qonfigType = SHADED,
		interpretation = Interpreted.class,
		instance = QuickShaded.class)
	public static class Def extends ExAddOn.Def.Abstract<QuickStyledElement, QuickShaded>
	implements QuickStyledAddOn<QuickStyledElement, QuickShaded> {
		private QuickStyleAttributeDef theLightSource;
		private QuickStyleAttributeDef theLightColor;
		private QuickStyleAttributeDef theShadowColor;
		private QuickStyleAttributeDef theCornerRadius;
		private QuickStyleAttributeDef theMaxShadeAmount;
		private QuickStyleAttributeDef theShading;

		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The widget to shade
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickStyledElement> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return QommonsUtils.unmodifiableDistinctCopy((Class<ExAddOn.Def<?, ?>>) (Class<?>) ExModelAugmentation.Def.class,
				QuickStyled.Def.class);
		}

		@Override
		public void addStyleAttributes(QuickTypeStyle type, QuickStyledAddOn.StyleDefBuilder builder) {
			theLightSource = builder.addApplicableAttribute(type.getAttribute("light-source"));
			theLightColor = builder.addApplicableAttribute(type.getAttribute("light-color"));
			theShadowColor = builder.addApplicableAttribute(type.getAttribute("shadow-color"));
			theCornerRadius = builder.addApplicableAttribute(type.getAttribute("corner-radius"));
			theMaxShadeAmount = builder.addApplicableAttribute(type.getAttribute("max-shade-amount"));
			theShading = builder.addApplicableAttribute(type.getAttribute("shading"));
		}

		/** @return The style attribute for the direction the light is coming from, in degrees East of North */
		public QuickStyleAttributeDef getLightSource() {
			return theLightSource;
		}

		/** @return The style attribute for the color of the light to use in shading */
		public QuickStyleAttributeDef getLightColor() {
			return theLightColor;
		}

		/** @return The style attribute for the color of the shadow to use in shading */
		public QuickStyleAttributeDef getShadowColor() {
			return theShadowColor;
		}

		/** @return The style attribute for the radius of corners */
		public QuickStyleAttributeDef getCornerRadius() {
			return theCornerRadius;
		}

		/** @return The style attribute for the maximum shading amount */
		public QuickStyleAttributeDef getMaxShadeAmount() {
			return theMaxShadeAmount;
		}

		/** @return The style attribute for the shading implementation to use */
		public QuickStyleAttributeDef getShading() {
			return theShading;
		}

		@Override
		public <E2 extends QuickStyledElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickShaded} interpretation */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickStyledElement, QuickShaded> {
		private QuickElementStyleAttribute<Float> theLightSource;
		private QuickElementStyleAttribute<Color> theLightColor;
		private QuickElementStyleAttribute<Color> theShadowColor;
		private QuickElementStyleAttribute<QuickSize> theCornerRadius;
		private QuickElementStyleAttribute<Float> theMaxShadeAmount;
		private QuickElementStyleAttribute<QuickShading> theShading;

		/**
		 * @param def The definition to interpret
		 * @param element The widget to shade
		 */
		protected Interpreted(Def def, ExElement.Interpreted<? extends QuickStyledElement> element) {
			super(def, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public Class<QuickShaded> getInstanceType() {
			return QuickShaded.class;
		}

		/** @return The style attribute for the direction the light is coming from, in degrees East of North */
		public QuickElementStyleAttribute<Float> getLightSource() {
			return theLightSource;
		}

		/** @return The style attribute for the color of the light to use in shading */
		public QuickElementStyleAttribute<Color> getLightColor() {
			return theLightColor;
		}

		/** @return The style attribute for the color of the shadow to use in shading */
		public QuickElementStyleAttribute<Color> getShadowColor() {
			return theShadowColor;
		}

		/** @return The style attribute for the radius of corners */
		public QuickElementStyleAttribute<QuickSize> getCornerRadius() {
			return theCornerRadius;
		}

		/** @return The style attribute for the maximum shading amount */
		public QuickElementStyleAttribute<Float> getMaxShadeAmount() {
			return theMaxShadeAmount;
		}

		/** @return The style attribute for the shading implementation to use */
		public QuickElementStyleAttribute<QuickShading> getShading() {
			return theShading;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends QuickStyledElement> element) throws ExpressoInterpretationException {
			// We have to use a post-update here because the style object isn't created until after ExElement.doUpdate() finishes
			super.postUpdate(element);

			QuickInstanceStyle.Interpreted styled = element.getAddOnValue(QuickStyled.Interpreted.class, QuickStyled.Interpreted::getStyle);
			InterpretedExpressoEnv env = element.getDefaultEnv();
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theLightSource = styled.get(cache.getAttribute(getDefinition().getLightSource(), Float.class, env));
			theLightColor = styled.get(cache.getAttribute(getDefinition().getLightColor(), Color.class, env));
			theShadowColor = styled.get(cache.getAttribute(getDefinition().getShadowColor(), Color.class, env));
			theCornerRadius = styled.get(cache.getAttribute(getDefinition().getCornerRadius(), QuickSize.class, env));
			theMaxShadeAmount = styled.get(cache.getAttribute(getDefinition().getMaxShadeAmount(), Float.class, env));
			theShading = styled.get(cache.getAttribute(getDefinition().getShading(), QuickShading.class, env));
		}

		@Override
		public QuickShaded create(ExElement element) {
			return new QuickShaded(element);
		}
	}

	private QuickStyledElement theStyledElement;
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

	/** @param element The widget to shade */
	protected QuickShaded(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** @return The direction the light is coming from, in degrees East of North */
	public ObservableValue<Float> getLightSource() {
		return theLightSource;
	}

	/** @return The color of the light to use in shading */
	public ObservableValue<Color> getLightColor() {
		return theLightColor;
	}

	/** @return The color of the shadow to use in shading */
	public ObservableValue<Color> getShadowColor() {
		return theShadowColor;
	}

	/** @return The radius of corners */
	public ObservableValue<QuickSize> getCornerRadius() {
		return theCornerRadius;
	}

	/** @return The maximum shading amount */
	public ObservableValue<Float> getMaxShadeAmount() {
		return theMaxShadeAmount;
	}

	/** @return The shading implementation to use */
	public ObservableValue<QuickShading> getShading() {
		return theShading;
	}

	@Override
	public void update(ExAddOn.Interpreted<? super QuickStyledElement, ?> interpreted, ExElement element)
		throws ModelInstantiationException {
		super.update(interpreted, element);
		theStyledElement = element.as(QuickStyledElement.class, null);
	}

	@Override
	public void postUpdate(ExAddOn.Interpreted<? super QuickStyledElement, ?> interpreted, ExElement element) {
		super.postUpdate(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;

		theLightSourceAttr = myInterpreted.getLightSource().getAttribute();
		theLightColorAttr = myInterpreted.getLightColor().getAttribute();
		theShadowColorAttr = myInterpreted.getShadowColor().getAttribute();
		theCornerRadiusAttr = myInterpreted.getCornerRadius().getAttribute();
		theMaxShadeAmountAttr = myInterpreted.getMaxShadeAmount().getAttribute();
		theShadingAttr = myInterpreted.getShading().getAttribute();

		QuickInstanceStyle style = theStyledElement.getStyle();

		theLightSource = style.getApplicableAttribute(theLightSourceAttr);
		theLightColor = style.getApplicableAttribute(theLightColorAttr);
		theShadowColor = style.getApplicableAttribute(theShadowColorAttr);
		theCornerRadius = style.getApplicableAttribute(theCornerRadiusAttr);
		theMaxShadeAmount = style.getApplicableAttribute(theMaxShadeAmountAttr);
		theShading = style.getApplicableAttribute(theShadingAttr);
	}

	@Override
	public QuickShaded copy(ExElement element) {
		QuickShaded copy = (QuickShaded) super.copy(element);

		try {
			copy.theStyledElement = element.as(QuickStyledElement.class, null);
		} catch (ModelInstantiationException e) {
			throw new IllegalStateException("This shouldn't happen!", e);
		}
		QuickInstanceStyle style = copy.theStyledElement.getStyle();
		copy.theLightSource = style.getApplicableAttribute(theLightSourceAttr);
		copy.theLightColor = style.getApplicableAttribute(theLightColorAttr);
		copy.theShadowColor = style.getApplicableAttribute(theShadowColorAttr);
		copy.theCornerRadius = style.getApplicableAttribute(theCornerRadiusAttr);
		copy.theMaxShadeAmount = style.getApplicableAttribute(theMaxShadeAmountAttr);
		copy.theShading = style.getApplicableAttribute(theShadingAttr);

		return copy;
	}
}
