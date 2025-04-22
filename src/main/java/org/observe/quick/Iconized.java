package org.observe.quick;

import java.awt.Image;
import java.util.Set;

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
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
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
import org.qommons.config.QonfigInterpretationException;

/** An add-on for a quick element that may have an icon */
public class Iconized extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this type */
	public static final String ICONIZED = "iconized";

	/** The defintion to create an {@link Iconized} element */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = ICONIZED,
		interpretation = Interpreted.class,
		instance = Iconized.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, Iconized> implements QuickStyledAddOn<ExElement, Iconized> {
		private QuickStyleAttributeDef theIconAttr;
		private CompiledExpression theIcon;

		/**
		 * @param type The Qonfig type of this element
		 * @param element The Qonfig element to interpret
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickStyledElement> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) QommonsUtils.unmodifiableDistinctCopy(ExModelAugmentation.Def.class,
				QuickStyled.Def.class);
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
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theIcon = getElement().getAttributeExpression("icon", session);
		}

		@Override
		public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** The interpretation to create an {@link Iconized} element */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, Iconized> {
		private QuickElementStyleAttribute<Image> theIconAttr;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> theIcon;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
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
		public void postUpdate(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
			super.postUpdate(element);
			QuickInstanceStyle.Interpreted style = element.getAddOn(QuickStyled.Interpreted.class).getStyle();
			InterpretedExpressoEnv env = element.getDefaultEnv();
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theIconAttr = style.get(cache.getAttribute(getDefinition().getIconAttr(), Image.class, env));
			theIcon = getDefinition().getIcon() == null ? null : QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(),
				getElement(), getElement().getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public Iconized create(ExElement element) {
			return new Iconized(element);
		}
	}

	private QuickStyled theStyled;
	private ModelValueInstantiator<SettableValue<Image>> theIconInstantiator;
	private QuickStyleAttribute<Image> theIconAttr;
	private ObservableValue<Image> theIconStyle;
	private SettableValue<ObservableValue<Image>> theIconValue;

	Iconized(ExElement element) {
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
	public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element)
		throws ModelInstantiationException {
		super.update(interpreted, element);
		theStyled = element.getAddOn(QuickStyled.class);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theIconInstantiator = myInterpreted.getIcon() == null ? null : myInterpreted.getIcon().instantiate();
	}

	@Override
	public void postUpdate(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) {
		super.postUpdate(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theIconAttr = myInterpreted.getIconAttr().getAttribute();
		theIconStyle = theStyled.getStyle().getApplicableAttribute(theIconAttr);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theIconInstantiator != null)
			theIconInstantiator.instantiate();
	}

	@Override
	public ModelSetInstance instantiate(ModelSetInstance models) throws ModelInstantiationException {
		models = super.instantiate(models);

		theIconValue.set(theIconInstantiator != null ? theIconInstantiator.get(models) : theIconStyle, null);
		return models;
	}

	@Override
	public Iconized copy(ExElement element) {
		Iconized copy = (Iconized) super.copy(element);

		copy.theStyled = element.getAddOn(QuickStyled.class);
		QuickInstanceStyle style = copy.theStyled.getStyle();
		copy.theIconStyle = style.getApplicableAttribute(theIconAttr);
		copy.theIconValue = SettableValue.<ObservableValue<Image>> build().build();

		return copy;
	}
}
