package org.observe.quick.style;

import java.util.ArrayList;
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
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterSortedList;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElement} */
public class QuickElementStyle {
	private final QuickElementStyle theParent;
	private final QonfigElement theElement;
	private final List<EvaluatedStyleValue<?>> theDeclaredValues;
	private final Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> theValues;

	/**
	 * @param declaredValues All style values declared specifically on this element
	 * @param parent The element style for the {@link QonfigElement#getParent() parent} element
	 * @param styleSheet The style sheet applying to the element
	 * @param element The element this style is for
	 * @param session The interpretation session to get the {@link ExpressoQIS#getExpressoEnv() expresso environment} from and for
	 *        {@link AbstractQIS#error(String) error reporting}
	 * @param style The toolkit inheriting Quick-Style
	 * @throws QonfigInterpretationException If an error occurs evaluating all the style information for the element
	 */
	public QuickElementStyle(List<QuickStyleValue<?>> declaredValues, QuickElementStyle parent, QuickStyleSheet styleSheet,
		QonfigElement element, ExpressoQIS session, QonfigToolkit style) throws QonfigInterpretationException {
		theParent = parent;
		theElement = element;
		List<EvaluatedStyleValue<?>> evaldValues = new ArrayList<>(declaredValues.size());
		theDeclaredValues = Collections.unmodifiableList(evaldValues);
		for (QuickStyleValue<?> dv : declaredValues)
			evaldValues.add(dv.evaluate(session.getExpressoEnv()));

		Map<QuickStyleAttribute<?>, BetterSortedList<? extends EvaluatedStyleValue<?>>> values = new HashMap<>();
		// Compile all attributes applicable to this element
		QuickStyleType type = QuickStyleType.of(element.getType(), session, style);
		for (QuickStyleAttribute<?> attr : type.getAttributes().values())
			values.computeIfAbsent(attr,
				__ -> SortedTreeList.<EvaluatedStyleValue<?>> buildTreeList(EvaluatedStyleValue::compareTo).build());
		for (QonfigAddOn inh : element.getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
			type = QuickStyleType.of(inh, session, style);
			if (type == null)
				continue;
			for (QuickStyleAttribute<?> attr : type.getAttributes().values())
				values.computeIfAbsent(attr,
					__ -> SortedTreeList.<EvaluatedStyleValue<?>> buildTreeList(EvaluatedStyleValue::compareTo).build());
		}
		for (EvaluatedStyleValue<?> sv : theDeclaredValues)
			((List<EvaluatedStyleValue<?>>) values.computeIfAbsent(sv.getStyleValue().getAttribute(),
				__ -> SortedTreeList.<EvaluatedStyleValue<?>> buildTreeList(EvaluatedStyleValue::compareTo).build())).add(sv);
		for (QuickStyleValue<?> sv : styleSheet.getValues(element))
			((List<EvaluatedStyleValue<?>>) values.computeIfAbsent(sv.getAttribute(),
				__ -> SortedTreeList.<EvaluatedStyleValue<?>> buildTreeList(EvaluatedStyleValue::compareTo).build()))
			.add(sv.evaluate(session.getExpressoEnv()));
		Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> styleValues = new HashMap<>();
		for (Map.Entry<QuickStyleAttribute<?>, BetterSortedList<? extends EvaluatedStyleValue<?>>> v : values.entrySet()) {
			QuickStyleAttribute<Object> attr = (QuickStyleAttribute<Object>) v.getKey();
			styleValues.put(attr,
				new QuickElementStyleAttribute<>(attr, this,
					QommonsUtils.unmodifiableCopy((List<EvaluatedStyleValue<Object>>) v.getValue()), //
					theParent != null && attr.isTrickleDown() ? getInherited(theParent, attr) : null));
		}
		theValues = Collections.unmodifiableMap(styleValues);
	}

	private static <T> QuickElementStyleAttribute<T> getInherited(QuickElementStyle parent, QuickStyleAttribute<T> attr) {
		while (parent != null && !parent.getAttributes().contains(attr))
			parent = parent.getParent();
		return parent == null ? null : parent.get(attr);
	}

	/** @return This element's {@link QonfigElement#getParent() parent}'s style */
	public QuickElementStyle getParent() {
		return theParent;
	}

	/** @return The element this style is for */
	public QonfigElement getElement() {
		return theElement;
	}

	/** @return All style values that may apply to this style */
	public List<EvaluatedStyleValue<?>> getDeclaredValues() {
		return theDeclaredValues;
	}

	/** @return All style attributes that apply to this element */
	public Set<QuickStyleAttribute<?>> getAttributes() {
		return theValues.keySet();
	}

	/**
	 * @param <T> The type of the attribute
	 * @param attr The attribute to get the value for
	 * @return A structure containing all information necessary to get the value of a style attribute for this element
	 */
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

	/**
	 * A structure containing all information necessary to get the value of a style attribute for an element
	 *
	 * @param <T> The type of the attribute
	 */
	public static class QuickElementStyleAttribute<T> {
		private final QuickStyleAttribute<T> theAttribute;
		private final QuickElementStyle theStyle;
		private final List<EvaluatedStyleValue<T>> theValues;
		private final QuickElementStyleAttribute<T> theInherited;

		/**
		 * @param attribute The attribute this structure is for
		 * @param style The element style this structure is for
		 * @param values All style values that may apply to the element for teh attribute
		 * @param inherited The structure for the same attribute for the {@link QuickElementStyle#getParent() parent} style
		 */
		public QuickElementStyleAttribute(QuickStyleAttribute<T> attribute, QuickElementStyle style, List<EvaluatedStyleValue<T>> values,
			QuickElementStyleAttribute<T> inherited) {
			theAttribute = attribute;
			theStyle = style;
			theValues = values;
			theInherited = inherited;
		}

		/** @return The attribute this structure is for */
		public QuickStyleAttribute<T> getAttribute() {
			return theAttribute;
		}

		/** @return The element style this structure is for */
		public QuickElementStyle getStyle() {
			return theStyle;
		}

		/** @return All style values that may apply to the element for the attribute */
		public List<EvaluatedStyleValue<T>> getValues() {
			return theValues;
		}

		/** @return The structure for the same attribute for the {@link QuickElementStyle#getParent() parent} style */
		public QuickElementStyleAttribute<T> getInherited() {
			return theInherited;
		}

		/**
		 * @param models The model instance to get the value for
		 * @return The value for this style attribute on the element
		 * @throws QonfigEvaluationException If the condition or the value could not be evaluated
		 */
		public ObservableValue<T> evaluate(ModelSetInstance models) throws QonfigEvaluationException {
			ObservableValue<ConditionalValue<T>>[] values = new ObservableValue[theValues.size() + (theInherited == null ? 0 : 1)];
			for (int i = 0; i < theValues.size(); i++) {
				ObservableValue<Boolean> condition = theValues.get(i).getApplication().getCondition(models);
				SettableValue<? extends T> value = theValues.get(i).getValue().get(models);
				values[i] = condition
					.map(LambdaUtils.printableFn(pass -> new ConditionalValue<>(pass, value), "ifPass(" + value + ")", null));
			}
			if (theInherited != null) {
				ObservableValue<T> value = theInherited.evaluate(StyleQIS.getParentModels(models));
				values[theValues.size()] = ObservableValue.of(new ConditionalValue<>(true, value));
			}
			ConditionalValue<T> defaultCV = new ConditionalValue<>(true, null);
			ObservableValue<ConditionalValue<T>> conditionalValue = ObservableValue.firstValue(
				(TypeToken<ConditionalValue<T>>) (TypeToken<?>) TypeTokens.get().of(ConditionalValue.class), //
				LambdaUtils.printablePred(cv -> cv.pass, "pass", null), //
				LambdaUtils.printableSupplier(() -> defaultCV, () -> "null", null), values);
			ObservableValue<T> value = ObservableValue
				.flatten(conditionalValue.map(LambdaUtils.printableFn(cv -> cv.value, "value", null)));
			return value;
		}

		private static class ConditionalValue<T> {
			final boolean pass;
			final ObservableValue<? extends T> value;

			ConditionalValue(boolean pass, ObservableValue<? extends T> value) {
				this.pass = pass;
				this.value = value;
			}

			@Override
			public String toString() {
				return value.toString();
			}
		}

		@Override
		public String toString() {
			return theStyle.getElement() + "." + theAttribute;
		}
	}
}
