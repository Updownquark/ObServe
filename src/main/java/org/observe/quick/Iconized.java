package org.observe.quick;

import java.awt.Image;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyledAddOn;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickStyledElement.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add-on for a quick element that may have an icon */
public class Iconized extends ExAddOn.Abstract<QuickStyledElement> {
	/** The XML name of this type */
	public static final String ICONIZED = "iconized";

	/** The defintion to create an {@link Iconized} element */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = ICONIZED,
		interpretation = Interpreted.class,
		instance = Iconized.class)
	public static class Def extends ExAddOn.Def.Abstract<QuickStyledElement, Iconized>
	implements QuickStyledAddOn<QuickStyledElement, Iconized> {
		private QuickStyleAttributeDef theIconAttr;
		private CompiledExpression theIcon;

		/**
		 * @param type The Qonfig type of this element
		 * @param element The Qonfig element to interpret
		 */
		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		/** @return The style attribute that the icon may be specified with */
		public QuickStyleAttributeDef getIconAttr() {
			return theIconAttr;
		}

		/** @return The icon expression as specified via a Qonfig attribute value */
		@QonfigAttributeGetter("icon")
		public CompiledExpression getIcon() {
			return theIcon;
		}

		@Override
		public void addStyleAttributes(QuickTypeStyle type, StyleDefBuilder builder) {
			theIconAttr = builder.addApplicableAttribute(type.getAttribute("icon"));
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends QuickStyledElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theIcon = getElement().getAttributeExpression("icon", session);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}
	}

	/** The interpretation to create an {@link Iconized} element */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickStyledElement, Iconized> {
		private QuickElementStyleAttribute<Image> theIconAttr;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theIcon;

		Interpreted(Def definition, ExElement.Interpreted<?> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public Class<Iconized> getInstanceType() {
			return Iconized.class;
		}

		/** @return The style attribute that the icon may be specified with */
		public QuickElementStyleAttribute<Image> getIconAttr() {
			return theIconAttr;
		}

		/** @return The icon expression as specified via a Qonfig attribute value */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> getIcon() {
			return theIcon;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends QuickStyledElement> element) throws ExpressoInterpretationException {
			super.postUpdate(element);
			QuickStyledElement.QuickInstanceStyle.Interpreted styled = ((QuickStyledElement.Interpreted<?>) element).getStyle();
			InterpretedExpressoEnv env = element.getExpressoEnv();
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theIconAttr = styled.get(cache.getAttribute(getDefinition().getIconAttr(), Image.class, env));
			theIcon = getDefinition().getIcon() == null ? null : QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(),
				getElement(), getElement().getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public Iconized create(QuickStyledElement element) {
			return new Iconized(element);
		}
	}

	private ModelValueInstantiator<SettableValue<Image>> theIconInstantiator;
	private QuickStyleAttribute<Image> theIconAttr;
	private ObservableValue<Image> theIconStyle;
	private SettableValue<ObservableValue<Image>> theIconValue;

	Iconized(QuickStyledElement element) {
		super(element);
		theIconValue = SettableValue.<ObservableValue<Image>> build().build();
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** @return The icon specified for the element, either by Qonfig attribute or via styles */
	public ObservableValue<Image> getIcon() {
		return ObservableValue.flatten(theIconValue);
	}

	@Override
	public void update(ExAddOn.Interpreted<? extends QuickStyledElement, ?> interpreted, QuickStyledElement element)
		throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void postUpdate(ExAddOn.Interpreted<? extends QuickStyledElement, ?> interpreted, QuickStyledElement element) {
		super.postUpdate(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theIconAttr = myInterpreted.getIconAttr().getAttribute();
		theIconStyle = element.getStyle().getApplicableAttribute(theIconAttr);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
	}

	@Override
	public void instantiate(ModelSetInstance models) throws ModelInstantiationException {
		super.instantiate(models);

		theIconValue.set(theIconInstantiator != null ? theIconInstantiator.get(models) : theIconStyle, null);
	}

	@Override
	public Iconized copy(QuickStyledElement element) {
		Iconized copy = (Iconized) super.copy(element);

		QuickInstanceStyle style = element.getStyle();

		copy.theIconStyle = style.getApplicableAttribute(theIconAttr);
		copy.theIconValue = SettableValue.<ObservableValue<Image>> build().build();

		return copy;
	}
}
