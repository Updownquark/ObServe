package org.observe.quick;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickModelValue;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleSet;
import org.observe.quick.style.QuickStyleSheet;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.SpecialSession;

public class QuickQIS implements SpecialSession<QuickQIS> {
	public static final String STYLE_ELEMENT = "quick-style-element";

	public static final String STYLED_PROP = "quick-interpreter-styled";
	public static final String WIDGET_PROP = "quick-interpreter-widget";
	public static final String STYLE_PROP = "quick-interpreter-style";
	public static final String STYLE_SHEET_PROP = "quick-interpreter-style-sheet";
	public static final String MODEL_VALUES_PROP = "quick-interpreter-model-values";

	private final CoreSession theWrapped;
	private final QuickStyleSet theStyleSet;

	public QuickQIS(CoreSession session, QuickStyleSet styleSet) {
		theWrapped = session;
		theStyleSet=styleSet;
		if (session.getElement().getParent() == null || session.getElement().getParent() != session.getElement()) {
			boolean styled = session.getElement().isInstance(theStyleSet.getStyled());
			session.put(STYLED_PROP, styled);
			session.put(WIDGET_PROP, styled && session.getElement().isInstance(theStyleSet.getWidget()));
			if (styled) {
				session.put(MODEL_VALUES_PROP, new HashSet<>());
			} else
				session.put(MODEL_VALUES_PROP, Collections.emptySet());
		}
	}

	@Override
	public CoreSession getWrapped() {
		return theWrapped;
	}

	QuickQIS wrap(CoreSession session) throws QonfigInterpretationException {
		return session.as(QuickQIS.class);
	}

	boolean isStyled() {
		return (Boolean) getWrapped().get(STYLED_PROP);
	}

	boolean isWidget() {
		return (Boolean) getWrapped().get(WIDGET_PROP);
	}

	public QuickStyleSet getStyleSet() {
		return theStyleSet;
	}

	public Set<QuickModelValue<?>> getModelValues() {
		return Collections.unmodifiableSet((Set<QuickModelValue<?>>) getWrapped().get(MODEL_VALUES_PROP));
	}

	public QuickStyleSheet getStyleSheet() {
		return (QuickStyleSheet) getWrapped().get(STYLE_SHEET_PROP);
	}

	public QuickQIS setStyleSheet(QuickStyleSheet styleSheet) {
		getWrapped().put(STYLE_SHEET_PROP, styleSheet);
		return this;
	}

	public QuickElementStyle getStyle() {
		return (QuickElementStyle) getWrapped().get(STYLE_PROP);
	}

	public <T> QuickStyleAttribute<? extends T> getStyleAttribute(String element, String name, Class<T> type)
		throws QonfigInterpretationException {
		if (element != null) {
			QonfigElementOrAddOn el = getElement().getDocument().getDocToolkit().getElementOrAddOn(element);
			if (el == null)
				throw new QonfigInterpretationException("No such element or add-on '" + element + "'");
			else if (!getElement().isInstance(el))
				throw new QonfigInterpretationException("This element is not an instance of  '" + element + "'");
			return theStyleSet.styled(el, as(ExpressoQIS.class)).getAttribute(name, type);
		}
		return theStyleSet.styled(getType(), as(ExpressoQIS.class)).getAttribute(name, type);
	}

	public <T> QuickModelValue<T> getStyleModelValue(String element, String name, Class<T> type)
		throws QonfigInterpretationException {
		if (element != null) {
			QonfigElementOrAddOn el = getElement().getDocument().getDocToolkit().getElementOrAddOn(element);
			if (el == null)
				throw new QonfigInterpretationException("No such element or add-on '" + element + "'");
			else if (!getElement().isInstance(el))
				throw new QonfigInterpretationException("This element is not an instance of  '" + element + "'");
			return theStyleSet.styled(el, as(ExpressoQIS.class)).getModelValue(name, type);
		}
		return theStyleSet.styled(getType(), as(ExpressoQIS.class)).getModelValue(name, type);
	}

	@Override
	public String toString() {
		return "quick:" + getWrapped().toString();
	}
}
