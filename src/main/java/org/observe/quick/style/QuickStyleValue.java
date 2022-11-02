package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.NameExpression;
import org.qommons.StringUtils;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigInterpretationException;

/** Represents a conditional value for a style attribute in quick */
public class QuickStyleValue<T> implements Comparable<QuickStyleValue<?>> {
	public static final String WIDGET_MODEL_VALUE = "widget-model-value";
	private static final Map<DynamicModelValue<?, ?>, Integer> MODEL_VALUE_PRIORITY = new HashMap<>();

	public static synchronized int getPriority(DynamicModelValue<?, ?> modelValue, QonfigAttributeDef.Declared priorityAttr) {
		Integer priority=MODEL_VALUE_PRIORITY.get(modelValue);
		if(priority!=null)
			return priority;
		if (!modelValue.getDeclaration().isInstance(priorityAttr.getOwner()))
			priority = 0;
		else
			priority = Integer.parseInt(modelValue.getDeclaration().getAttributeText(priorityAttr));
		MODEL_VALUE_PRIORITY.put(modelValue, priority);
		return priority;
	}

	private final QuickStyleSheet theStyleSheet;
	private final StyleValueApplication theApplication;
	private final QuickStyleAttribute<T> theAttribute;
	private final ObservableExpression theValueExpression;
	private final ValueContainer<SettableValue<?>, ? extends SettableValue<? extends T>> theValue;
	private final List<DynamicModelValue<?, ?>> theUsedModelValues;

	public QuickStyleValue(QuickStyleSheet styleSheet, StyleValueApplication application, QuickStyleAttribute<T> attribute,
		ObservableExpression value, ExpressoEnv env) throws QonfigInterpretationException {
		theStyleSheet = styleSheet;
		theApplication = application;
		theAttribute = attribute;
		theValueExpression = value;
		theValue = value.evaluate(ModelTypes.Value.forType(attribute.getType()), env);
		Set<DynamicModelValue<?, ?>> modelValues = new LinkedHashSet<>();
		modelValues.addAll(theApplication.getModelValues());
		findModelValues(theValueExpression, modelValues, env.getModels());
		List<DynamicModelValue<?, ?>> sortedModelValues = new ArrayList<>(modelValues.size());
		sortedModelValues.addAll(modelValues);
		QonfigAttributeDef.Declared priorityAttr = attribute.getDeclarer().getPriorityAttr();
		Collections.sort(sortedModelValues, (mv1, mv2) -> -Integer.compare(getPriority(mv1, priorityAttr), getPriority(mv2, priorityAttr)));
		theUsedModelValues = Collections.unmodifiableList(sortedModelValues);
	}

	private static void findModelValues(ObservableExpression ex, Set<DynamicModelValue<?, ?>> modelValues, ObservableModelSet models)
		throws QonfigInterpretationException {
		if (ex instanceof NameExpression && ((NameExpression) ex).getContext() == null) {
			String name = ((NameExpression) ex).getNames().getFirst();
			ModelComponentNode<?, ?> component = models.getComponentIfExists(name);
			if (component != null) {
				for (ValueContainer<?, ?> core : component.getCores()) {
					if (core instanceof DynamicModelValue)
						modelValues.add((DynamicModelValue<?, ?>) core);
				}
			}
		} else {
			for (ObservableExpression child : ex.getChildren())
				findModelValues(child, modelValues, models);
		}
	}

	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}

	public StyleValueApplication getApplication() {
		return theApplication;
	}

	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	public ObservableExpression getValueExpression() {
		return theValueExpression;
	}

	public ValueContainer<SettableValue<?>, ? extends SettableValue<? extends T>> getValue() {
		return theValue;
	}

	public List<DynamicModelValue<?, ?>> getUsedModelValues() {
		return theUsedModelValues;
	}

	@Override
	public int compareTo(QuickStyleValue<?> o) {
		// Most importantly, compare the priority of model values used in the condition and value
		Iterator<DynamicModelValue<?, ?>> iter1 = theUsedModelValues.iterator();
		Iterator<DynamicModelValue<?, ?>> iter2 = o.theUsedModelValues.iterator();
		int comp = 0;
		QonfigAttributeDef.Declared priorityAttr = theAttribute.getDeclarer().getPriorityAttr();
		while (comp == 0) {
			if (iter1.hasNext()) {
				if (iter2.hasNext())
					comp = -Integer.compare(getPriority(iter1.next(), priorityAttr), getPriority(iter2.next(), priorityAttr));
				else
					comp = -1; // We use more model values--higher priority
			} else if (iter2.hasNext()) {
				comp = 1; // We user fewer model values--lower priority
			} else {
				break;
			}
		}
		// Compare the complexity of the condition
		if (comp == 0)
			comp = -Integer.compare(theApplication.getConditionComplexity(), o.theApplication.getConditionComplexity());
		// Compare the complexity of the role path
		if (comp == 0)
			comp = -Integer.compare(theApplication.getDepth(), o.theApplication.getDepth());
		// Compare the complexity of the element type
		if (comp == 0)
			comp = -Integer.compare(theApplication.getTypeComplexity(), o.theApplication.getTypeComplexity());
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

	@Override
	public String toString() {
		return new StringBuilder(theApplication.toString()).append(':').append(theAttribute.getName()).append('=')
			.append(theValueExpression).toString();
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