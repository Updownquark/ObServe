package org.observe.quick;

import javax.swing.Icon;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
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

public class Iconized extends ExAddOn.Abstract<QuickStyledElement> {
	public static final String ICONIZED = "iconized";

	public static class Def extends ExAddOn.Def.Abstract<QuickStyledElement, Iconized>
	implements QuickStyledAddOn<QuickStyledElement, Iconized> {
		private QuickStyleAttributeDef theIcon;

		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		public QuickStyleAttributeDef getIcon() {
			return theIcon;
		}

		@Override
		public void addStyleAttributes(QuickTypeStyle type, StyleDefBuilder builder) {
			theIcon = builder.addApplicableAttribute(type.getAttribute("icon"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> element) {
			return new Interpreted(this, element);
		}
	}

	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickStyledElement, Iconized> {
		private QuickElementStyleAttribute<Icon> theIcon;

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

		public QuickElementStyleAttribute<Icon> getIcon() {
			return theIcon;
		}

		@Override
		public void postUpdate(ExElement.Interpreted<? extends QuickStyledElement> element) throws ExpressoInterpretationException {
			super.postUpdate(element);
			QuickStyledElement.QuickInstanceStyle.Interpreted styled = ((QuickStyledElement.Interpreted<?>) element).getStyle();
			InterpretedExpressoEnv env = element.getExpressoEnv();
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			theIcon = styled.get(cache.getAttribute(getDefinition().getIcon(), Icon.class, env));
		}

		@Override
		public Iconized create(QuickStyledElement element) {
			return new Iconized(element);
		}
	}

	private QuickStyleAttribute<Icon> theIconAttr;
	private ObservableValue<Icon> theIcon;

	Iconized(QuickStyledElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	public ObservableValue<Icon> getIcon() {
		return theIcon;
	}

	@Override
	public void postUpdate(ExAddOn.Interpreted<? extends QuickStyledElement, ?> interpreted, QuickStyledElement element) {
		super.postUpdate(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theIconAttr = myInterpreted.getIcon().getAttribute();
		theIcon = element.getStyle().getApplicableAttribute(theIconAttr);
	}

	@Override
	public Iconized copy(QuickStyledElement element) {
		Iconized copy = (Iconized) super.copy(element);

		QuickInstanceStyle style = element.getStyle();

		copy.theIcon = style.getApplicableAttribute(theIconAttr);

		return copy;
	}
}
