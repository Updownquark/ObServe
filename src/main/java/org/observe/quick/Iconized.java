package org.observe.quick;

import javax.swing.Icon;

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
import org.observe.quick.style.QuickStyledElement.QuickInstanceStyle.Def.StyleDefBuilder;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

public class Iconized extends ExAddOn.Abstract<QuickStyledElement> {
	public static final String ICONIZED = "iconized";

	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = ICONIZED,
		interpretation = Interpreted.class,
		instance = Iconized.class)
	public static class Def extends ExAddOn.Def.Abstract<QuickStyledElement, Iconized>
	implements QuickStyledAddOn<QuickStyledElement, Iconized> {
		private QuickStyleAttributeDef theIconAttr;
		private CompiledExpression theIcon;

		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		public QuickStyleAttributeDef getIconAttr() {
			return theIconAttr;
		}

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

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickStyledElement, Iconized> {
		private QuickElementStyleAttribute<Icon> theIconAttr;
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> theIcon;

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

		public QuickElementStyleAttribute<Icon> getIconAttr() {
			return theIconAttr;
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> getIcon() {
			return theIcon;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends QuickStyledElement> element) throws ExpressoInterpretationException {
			super.postUpdate(element);
			QuickStyledElement.QuickInstanceStyle.Interpreted styled = ((QuickStyledElement.Interpreted<?>) element).getStyle();
			InterpretedExpressoEnv env = element.getExpressoEnv();
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theIconAttr = styled.get(cache.getAttribute(getDefinition().getIconAttr(), Icon.class, env));
			theIcon = getDefinition().getIcon() == null ? null : QuickCoreInterpretation.evaluateIcon(getDefinition().getIcon(),
				getElement(), getElement().getDefinition().getElement().getDocument().getLocation());
		}

		@Override
		public Iconized create(QuickStyledElement element) {
			return new Iconized(element);
		}
	}

	private ModelValueInstantiator<SettableValue<Icon>> theIconInstantiator;
	private QuickStyleAttribute<Icon> theIconAttr;
	private ObservableValue<Icon> theIconStyle;
	private SettableValue<ObservableValue<Icon>> theIconValue;

	Iconized(QuickStyledElement element) {
		super(element);
		theIconValue = SettableValue.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<Icon>> parameterized(Icon.class))
			.build();
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	public ObservableValue<Icon> getIcon() {
		return ObservableValue.flatten(theIconValue);
	}

	@Override
	public void update(ExAddOn.Interpreted<? extends QuickStyledElement, ?> interpreted, QuickStyledElement element) {
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
	public void instantiated() {
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
		copy.theIconValue = SettableValue.build(theIconValue.getType()).build();

		return copy;
	}
}
