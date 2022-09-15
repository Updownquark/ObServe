package org.observe.quick.style;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterSortedList;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElement} */
public class QuickElementStyle {
	private final QuickElementStyle theParent;
	private final QonfigElement theElement;
	private final List<QuickStyleValue<?>> theDeclaredValues;
	private final Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> theValues;

	public QuickElementStyle(List<QuickStyleValue<?>> declaredValues, QuickElementStyle parent, QuickStyleSheet styleSheet,
		QonfigElement element, ExpressoQIS session, QonfigToolkit style) throws QonfigInterpretationException {
		theParent = parent;
		theElement = element;
		theDeclaredValues = declaredValues;

		Map<QuickStyleAttribute<?>, BetterSortedList<? extends QuickStyleValue<?>>> values = new HashMap<>();
		// Compile all attributes applicable to this element
		QuickStyleType type = QuickStyleType.of(element.getType(), session, style);
		for (QuickStyleAttribute<?> attr : type.getAttributes().values())
			values.computeIfAbsent(attr, __ -> SortedTreeList.<QuickStyleValue<?>> buildTreeList(QuickStyleValue::compareTo).build());
		for (QonfigAddOn inh : element.getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
			type = QuickStyleType.of(inh, session, style);
			if (type == null)
				continue;
			for (QuickStyleAttribute<?> attr : type.getAttributes().values())
				values.computeIfAbsent(attr, __ -> SortedTreeList.<QuickStyleValue<?>> buildTreeList(QuickStyleValue::compareTo).build());
		}
		for (QuickStyleValue<?> sv : theDeclaredValues)
			((List<QuickStyleValue<?>>) values.computeIfAbsent(sv.getAttribute(),
				__ -> SortedTreeList.<QuickStyleValue<?>> buildTreeList(QuickStyleValue::compareTo).build())).add(sv);
		for (QuickStyleValue<?> sv : styleSheet.getValues(element))
			((List<QuickStyleValue<?>>) values.computeIfAbsent(sv.getAttribute(),
				__ -> SortedTreeList.<QuickStyleValue<?>> buildTreeList(QuickStyleValue::compareTo).build())).add(sv);
		Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> styleValues = new HashMap<>();
		for (Map.Entry<QuickStyleAttribute<?>, BetterSortedList<? extends QuickStyleValue<?>>> v : values.entrySet()) {
			QuickStyleAttribute<Object> attr = (QuickStyleAttribute<Object>) v.getKey();
			styleValues.put(attr, new QuickElementStyleAttribute<>(attr, this,
				QommonsUtils.unmodifiableCopy((List<QuickStyleValue<Object>>) v.getValue()), //
				theParent != null && attr.isTrickleDown() ? getInherited(theParent, attr) : null));
		}
		theValues = Collections.unmodifiableMap(styleValues);
	}

	private static <T> QuickElementStyleAttribute<T> getInherited(QuickElementStyle parent, QuickStyleAttribute<T> attr) {
		while (parent != null && !parent.getAttributes().contains(attr))
			parent = parent.getParent();
		return parent == null ? null : parent.get(attr);
	}

	public QuickElementStyle getParent() {
		return theParent;
	}

	public QonfigElement getElement() {
		return theElement;
	}

	public List<QuickStyleValue<?>> getDeclaredValues() {
		return theDeclaredValues;
	}

	public Set<QuickStyleAttribute<?>> getAttributes() {
		return theValues.keySet();
	}

	public <T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr) {
		QuickElementStyleAttribute<T> value = (QuickElementStyleAttribute<T>) theValues.get(attr);
		if (value != null)
			return value;
		else if (!theElement.isInstance(attr.getDeclarer().getElement()))
			throw new IllegalArgumentException(
				"Attribute " + attr + " is not valid for this element (" + theElement.getType().getName() + ")");
		return new QuickElementStyleAttribute<>(attr, this, Collections.emptyList(), //
			theParent != null && attr.isTrickleDown() ? theParent.get(attr) : null);
	}

	public static class QuickElementStyleAttribute<T> {
		private final QuickStyleAttribute<T> theAttribute;
		private final QuickElementStyle theStyle;
		private final List<QuickStyleValue<T>> theValues;
		private final QuickElementStyleAttribute<T> theInherited;

		public QuickElementStyleAttribute(QuickStyleAttribute<T> attribute, QuickElementStyle style, List<QuickStyleValue<T>> values,
			QuickElementStyleAttribute<T> inherited) {
			theAttribute = attribute;
			theStyle = style;
			theValues = values;
			theInherited = inherited;
		}

		public QuickStyleAttribute<T> getAttribute() {
			return theAttribute;
		}

		public QuickElementStyle getStyle() {
			return theStyle;
		}

		public List<QuickStyleValue<T>> getValues() {
			return theValues;
		}

		public QuickElementStyleAttribute<T> getInherited() {
			return theInherited;
		}

		public ObservableValue<T> evaluate(ModelSetInstance models) {
			ObservableValue<ConditionalValue<T>>[] values = new ObservableValue[theValues.size() + (theInherited == null ? 0 : 1)];
			for (int i = 0; i < theValues.size(); i++) {
				ObservableValue<Boolean> condition = theValues.get(i).getApplication().getCondition(models);
				SettableValue<? extends T> value = theValues.get(i).getValue().get(models);
				values[i] = condition.map(pass -> new ConditionalValue<>(pass, value));
			}
			if (theInherited != null) {
				ObservableValue<T> value = theInherited.evaluate(StyleQIS.getParentModels(models));
				values[theValues.size()] = ObservableValue.of(new ConditionalValue<>(true, value));
			}
			ObservableValue<ConditionalValue<T>> conditionalValue = ObservableValue.firstValue(
				(TypeToken<ConditionalValue<T>>) (TypeToken<?>) TypeTokens.get().of(ConditionalValue.class), cv -> cv.pass, () -> null,
				values);
			return ObservableValue.flatten(conditionalValue.map(cv -> cv == null ? null : cv.value));
		}

		private static class ConditionalValue<T> {
			final boolean pass;
			final ObservableValue<? extends T> value;

			ConditionalValue(boolean pass, ObservableValue<? extends T> value) {
				this.pass = pass;
				this.value = value;
			}
		}

		@Override
		public String toString() {
			return theStyle.getElement() + "." + theAttribute;
		}
	}
}
