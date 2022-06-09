package org.observe.quick.style;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ClassView;
import org.observe.expresso.DefaultExpressoParser;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.StringUtils;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** Represents a conditional value for a style attribute in quick */
public class QuickStyleValue<T> implements Comparable<QuickStyleValue<?>> {
	private final QuickStyleSheet theStyleSheet;
	private final QuickStyleAttribute<T> theAttribute;
	private final QonfigElementOrAddOn theElement;
	private final List<QonfigChildDef> theRolePath;
	private final ObservableExpression theConditionExpression;
	private final ValueContainer<SettableValue, SettableValue<Boolean>> theCondition;
	private final ObservableExpression theValueExpression;
	private final ValueContainer<SettableValue, ? extends SettableValue<? extends T>> theValue;
	private final Set<QuickModelValue<?, ?>> theUsedModelValues;

	public QuickStyleValue(QuickStyleSheet styleSheet, QuickStyleAttribute<T> attribute, QonfigElementOrAddOn element,
		List<QonfigChildDef> rolePath, ObservableExpression condition, ObservableExpression value, //
		ObservableModelSet models, ClassView classView) throws QonfigInterpretationException {
		theStyleSheet = styleSheet;
		theAttribute = attribute;
		theElement = element;
		theRolePath = rolePath;
		theConditionExpression = condition;
		theValueExpression = value;
		theCondition = condition == null ? null : condition.evaluate(ModelTypes.Value.forType(boolean.class), models, classView);
		theValue = value.evaluate(ModelTypes.Value.forType(attribute.getType()), models, classView);
		Set<QuickModelValue<?, ?>> modelValues = new LinkedHashSet<>();
		if (theConditionExpression != null)
			findModelValues(theConditionExpression, modelValues, models);
		findModelValues(theValueExpression, modelValues, models);
		theUsedModelValues = Collections.unmodifiableSet(modelValues);
	}

	private static void findModelValues(ObservableExpression ex, Set<QuickModelValue<?, ?>> modelValues, ObservableModelSet models)
		throws QonfigInterpretationException {
		if (ex instanceof DefaultExpressoParser.NameExpression && ((DefaultExpressoParser.NameExpression) ex).getContext() == null) {
			String name = ((DefaultExpressoParser.NameExpression) ex).getNames().getFirst();
			ValueContainer<?, ?> value = models.get(name, false);
			if (value instanceof QuickModelValue)
				modelValues.add((QuickModelValue<?, ?>) value);
		} else {
			for (ObservableExpression child : ex.getChildren())
				findModelValues(child, modelValues, models);
		}
	}

	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}

	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	public QonfigElementOrAddOn getElement() {
		return theElement;
	}

	public List<QonfigChildDef> getRolePath() {
		return theRolePath;
	}

	public ObservableExpression getConditionExpression() {
		return theConditionExpression;
	}

	public ValueContainer<SettableValue, SettableValue<Boolean>> getCondition() {
		return theCondition;
	}

	public ObservableExpression getValueExpression() {
		return theValueExpression;
	}

	public ValueContainer<SettableValue, ? extends SettableValue<? extends T>> getValue() {
		return theValue;
	}

	public Set<QuickModelValue<?, ?>> getUsedModelValues() {
		return theUsedModelValues;
	}

	@Override
	public int compareTo(QuickStyleValue<?> o) {
		// Most importantly, compare the priority of model values used in the condition and value
		Iterator<QuickModelValue<?, ?>> iter1 = theUsedModelValues.iterator();
		Iterator<QuickModelValue<?, ?>> iter2 = o.theUsedModelValues.iterator();
		int comp = 0;
		while (comp == 0) {
			if (iter1.hasNext()) {
				if (iter2.hasNext())
					comp = -Integer.compare(iter1.next().getPriority(), iter2.next().getPriority());
				else
					comp = -1; // We use more model values--higher priority
			} else if (iter2.hasNext()) {
				comp = 1; // We user fewer model values--lower priority
			} else {
				break;
			}
		}
		// Compare the specificity of the role path
		if (comp == 0)
			comp = -Integer.compare(theRolePath.size(), o.theRolePath.size());
		// Compare the specificity of the element type
		if (comp == 0 && theElement != o.theElement) {
			int thisDepth = 0;
			QonfigElementOrAddOn el = theElement;
			while (el != null) {
				thisDepth++;
				el = el.getSuperElement();
			}
			int oDepth = 0;
			el = o.theElement;
			while (el != null) {
				thisDepth++;
				el = el.getSuperElement();
			}
			comp = -Integer.compare(thisDepth, oDepth);
		}
		// Compare the source style sheets
		if (comp == 0) {
			if (theStyleSheet == null) {
				if (o.theStyleSheet != null)
					comp = -1; // Style values specified on the element have highest priority
			} else if (o.theStyleSheet == null)
				comp = 1; // Style values specified on the element have highest priority
		}
		// Last, compare the attributes, just multiple attribute values with identical priority
		// can live in the same style sheet together
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(//
				theAttribute.getDeclarer().getElement().getDeclarer().getLocation().toString(), //
				o.theAttribute.getDeclarer().getElement().getDeclarer().getLocation().toString(), true, true);
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(//
				theAttribute.getDeclarer().getElement().getName(), //
				o.theAttribute.getDeclarer().getElement().getName(), true, true);
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(theAttribute.getName(), o.theAttribute.getName(), true, true);
		return comp;
	}

	public boolean applies(QonfigElement element) {
		if (theElement != null && !element.isInstance(theElement))
			return false;
		if (!theRolePath.isEmpty()) {
			QonfigElement el = element;
			for (int i = theRolePath.size() - 1; i >= 0; i--) {
				QonfigChildDef role = theRolePath.get(i);
				if (!el.getDeclaredRoles().contains(role.getDeclared()))
					return false;
				el = el.getParent();
				if (!el.isInstance(role.getOwner()))
					return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (!theRolePath.isEmpty()) {
			printRolePath(theRolePath, str);
			if (theElement != null && theElement != theRolePath.get(theRolePath.size() - 1).getType())
				str.append('[').append(theElement).append(']');
		} else
			str.append(theElement);
		str.append('.').append(theAttribute.getName());
		if (theConditionExpression != null && !(theConditionExpression instanceof ObservableExpression.LiteralExpression))
			str.append('(').append(theCondition).append(')');
		str.append('=');
		str.append(theValueExpression);
		return str.toString();
	}

	public static StringBuilder printRolePath(List<QonfigChildDef> rolePath, StringBuilder str) {
		if (str == null)
			str = new StringBuilder();
		str.append(rolePath.get(0).getOwner().getName());
		for (QonfigChildDef role : rolePath)
			str.append('.').append(role.getName());
		return str;
	}
}