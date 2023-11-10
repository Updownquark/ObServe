package org.observe.quick.ext;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.quick.base.QuickSize;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyledAddOn;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickStyledElement.QuickInstanceStyle;
import org.observe.quick.style.QuickStyledElement.QuickInstanceStyle.Def.StyleDefBuilder;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigAddOn;

public class QuickShaded extends ExAddOn.Abstract<QuickStyledElement> {
	public static final String SHADED = "shaded";

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

		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@Override
		public void addStyleAttributes(QuickTypeStyle type, StyleDefBuilder builder) {
			theLightSource = builder.addApplicableAttribute(type.getAttribute("light-source"));
			theLightColor = builder.addApplicableAttribute(type.getAttribute("light-color"));
			theShadowColor = builder.addApplicableAttribute(type.getAttribute("shadow-color"));
			theCornerRadius = builder.addApplicableAttribute(type.getAttribute("corner-radius"));
			theMaxShadeAmount = builder.addApplicableAttribute(type.getAttribute("max-shade-amount"));
			theShading = builder.addApplicableAttribute(type.getAttribute("shading"));
		}

		public QuickStyleAttributeDef getLightSource() {
			return theLightSource;
		}

		public QuickStyleAttributeDef getLightColor() {
			return theLightColor;
		}

		public QuickStyleAttributeDef getShadowColor() {
			return theShadowColor;
		}

		public QuickStyleAttributeDef getCornerRadius() {
			return theCornerRadius;
		}

		public QuickStyleAttributeDef getMaxShadeAmount() {
			return theMaxShadeAmount;
		}

		public QuickStyleAttributeDef getShading() {
			return theShading;
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickStyledElement, QuickShaded> {
		private QuickElementStyleAttribute<Float> theLightSource;
		private QuickElementStyleAttribute<Color> theLightColor;
		private QuickElementStyleAttribute<Color> theShadowColor;
		private QuickElementStyleAttribute<QuickSize> theCornerRadius;
		private QuickElementStyleAttribute<Float> theMaxShadeAmount;
		private QuickElementStyleAttribute<QuickShading> theShading;

		public Interpreted(Def def, ExElement.Interpreted<?> element) {
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

		public QuickElementStyleAttribute<Float> getLightSource() {
			return theLightSource;
		}

		public QuickElementStyleAttribute<Color> getLightColor() {
			return theLightColor;
		}

		public QuickElementStyleAttribute<Color> getShadowColor() {
			return theShadowColor;
		}

		public QuickElementStyleAttribute<QuickSize> getCornerRadius() {
			return theCornerRadius;
		}

		public QuickElementStyleAttribute<Float> getMaxShadeAmount() {
			return theMaxShadeAmount;
		}

		public QuickElementStyleAttribute<QuickShading> getShading() {
			return theShading;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends QuickStyledElement> element) throws ExpressoInterpretationException {
			// We have to use a post-update here because the style object isn't created until after ExElement.doUpdate() finishes
			super.postUpdate(element);

			QuickStyledElement.QuickInstanceStyle.Interpreted styled = ((QuickStyledElement.Interpreted<?>) element).getStyle();
			InterpretedExpressoEnv env = element.getExpressoEnv();
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theLightSource = styled.get(cache.getAttribute(getDefinition().getLightSource(), Float.class, env));
			theLightColor = styled.get(cache.getAttribute(getDefinition().getLightColor(), Color.class, env));
			theShadowColor = styled.get(cache.getAttribute(getDefinition().getShadowColor(), Color.class, env));
			theCornerRadius = styled.get(cache.getAttribute(getDefinition().getCornerRadius(), QuickSize.class, env));
			theMaxShadeAmount = styled.get(cache.getAttribute(getDefinition().getMaxShadeAmount(), Float.class, env));
			theShading = styled.get(cache.getAttribute(getDefinition().getShading(), QuickShading.class, env));
		}

		@Override
		public QuickShaded create(QuickStyledElement element) {
			return new QuickShaded(element);
		}
	}

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

	QuickShaded(QuickStyledElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	public ObservableValue<Float> getLightSource() {
		return theLightSource;
	}

	public ObservableValue<Color> getLightColor() {
		return theLightColor;
	}

	public ObservableValue<Color> getShadowColor() {
		return theShadowColor;
	}

	public ObservableValue<QuickSize> getCornerRadius() {
		return theCornerRadius;
	}

	public ObservableValue<Float> getMaxShadeAmount() {
		return theMaxShadeAmount;
	}

	public ObservableValue<QuickShading> getShading() {
		return theShading;
	}

	@Override
	public void postUpdate(ExAddOn.Interpreted<? extends QuickStyledElement, ?> interpreted, QuickStyledElement element) {
		super.postUpdate(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;

		theLightSourceAttr = myInterpreted.getLightSource().getAttribute();
		theLightColorAttr = myInterpreted.getLightColor().getAttribute();
		theShadowColorAttr = myInterpreted.getShadowColor().getAttribute();
		theCornerRadiusAttr = myInterpreted.getCornerRadius().getAttribute();
		theMaxShadeAmountAttr = myInterpreted.getMaxShadeAmount().getAttribute();
		theShadingAttr = myInterpreted.getShading().getAttribute();

		QuickInstanceStyle style = element.getStyle();

		theLightSource = style.getApplicableAttribute(theLightSourceAttr);
		theLightColor = style.getApplicableAttribute(theLightColorAttr);
		theShadowColor = style.getApplicableAttribute(theShadowColorAttr);
		theCornerRadius = style.getApplicableAttribute(theCornerRadiusAttr);
		theMaxShadeAmount = style.getApplicableAttribute(theMaxShadeAmountAttr);
		theShading = style.getApplicableAttribute(theShadingAttr);
	}

	@Override
	public QuickShaded copy(QuickStyledElement element) {
		QuickShaded copy = (QuickShaded) super.copy(element);

		QuickInstanceStyle style = element.getStyle();

		copy.theLightSource = style.getApplicableAttribute(theLightSourceAttr);
		copy.theLightColor = style.getApplicableAttribute(theLightColorAttr);
		copy.theShadowColor = style.getApplicableAttribute(theShadowColorAttr);
		copy.theCornerRadius = style.getApplicableAttribute(theCornerRadiusAttr);
		copy.theMaxShadeAmount = style.getApplicableAttribute(theMaxShadeAmountAttr);
		copy.theShading = style.getApplicableAttribute(theShadingAttr);

		return copy;
	}
}
