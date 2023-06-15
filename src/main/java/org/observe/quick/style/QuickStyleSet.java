package org.observe.quick.style;

import java.util.Collections;
import java.util.List;

import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExElement.Interpreted;
import org.qommons.Named;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigInterpretationException;

public class QuickStyleSet extends ExElement.Def.Abstract<ExElement> implements Named {
	private static final ExElement.AttributeValueGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSet> NAME//
	= ExElement.AttributeValueGetter.of(QuickStyleSet::getName, null, null,
		"The name by which the style set may be referred to from documents using the style sheet");
	private static final ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSet> STYLE_ELEMENTS = new ExElement.ChildElementGetter<ExElement, ExElement.Interpreted<?>, QuickStyleSet>() {
		@Override
		public String getDescription() {
			return "Style values for this style set";
		}

		@Override
		public List<? extends Def<?>> getChildrenFromDef(QuickStyleSet def) {
			return def.getStyleElements();
		}

		@Override
		public List<? extends Interpreted<?>> getChildrenFromInterpreted(Interpreted<?> interp) {
			return Collections.emptyList();
		}

		@Override
		public List<? extends ExElement> getChildrenFromElement(ExElement element) {
			return Collections.emptyList();
		}
	};

	private final String theName;
	private final List<QuickStyleValue<?>> theValues;
	private final List<QuickStyleElement.Def> theStyleElements;

	public QuickStyleSet(QuickStyleSheet styleSheet, AbstractQIS<?> session, String name, List<QuickStyleValue<?>> values,
		List<QuickStyleElement.Def> styleElements) {
		super(styleSheet, session.getElement());
		checkElement(session.getFocusType(), StyleSessionImplV0_1.NAME, StyleSessionImplV0_1.VERSION, "style-set");
		theName = name;
		theValues = values;
		theStyleElements = styleElements;
		forAttribute(session.getAttributeDef(null, null, "name"), NAME);
	}

	@Override
	public String getName() {
		return theName;
	}

	public List<QuickStyleValue<?>> getValues() {
		return theValues;
	}

	public List<QuickStyleElement.Def> getStyleElements() {
		return theStyleElements;
	}

	@Override
	public void update(ExpressoQIS session) throws QonfigInterpretationException {
		super.update(session);
		int i = 0;
		for (ExpressoQIS valueS : session.forChildren("style"))
			theStyleElements.get(i++).update(valueS);
	}
}
