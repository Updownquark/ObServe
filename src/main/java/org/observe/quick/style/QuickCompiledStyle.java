package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableExpression;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSortedList;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.tree.SortedTreeList;

/** A compiled structure of all style values that may under any circumstance apply to a particular {@link QonfigElement element} */
public interface QuickCompiledStyle {
	/** @return This element's {@link QonfigElement#getParent() parent}'s style */
	QuickCompiledStyle getParent();

	/** @return The element this style is for */
	QonfigElement getElement();

	/** @return All style values that may apply to this style */
	List<CompiledStyleValue<?>> getDeclaredValues();

	/** @return All style attributes that apply to this element */
	Set<QuickStyleAttribute<?>> getAttributes();

	/** @return A multi-map of all style values applicable to this style, keyed by name */
	BetterMultiMap<String, QuickStyleAttribute<?>> getAttributesByName();

	/**
	 * Interprets this compiled structure
	 *
	 * @param parent The interpreted style of this style's element's {@link QonfigElement#getParent() parent}
	 * @param applications A cache of interpreted style applications for re-use
	 * @return The interpreted style for this style's element
	 * @throws ExpressoInterpretationException If this structure's expressions could not be
	 *         {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, org.observe.expresso.ExpressoEnv, int)
	 *         evaluated}
	 */
	QuickInterpretedStyle interpret(QuickInterpretedStyle parent, Map<CompiledStyleApplication, InterpretedStyleApplication> applications)
		throws ExpressoInterpretationException;

	/** Default {@link QuickCompiledStyle} implementation */
	public class Default implements QuickCompiledStyle {
		private final QuickCompiledStyle theParent;
		private final QonfigElement theElement;
		private final List<CompiledStyleValue<?>> theDeclaredValues;
		private final Map<QuickStyleAttribute<?>, QuickCompiledStyleAttribute<?>> theValues;
		private final BetterMultiMap<String, QuickStyleAttribute<?>> theAttributesByName;

		/**
		 * @param declaredValues All style values declared specifically on this element
		 * @param parent The element style for the {@link QonfigElement#getParent() parent} element
		 * @param styleSheet The style sheet applying to the element
		 * @param element The element this style is for
		 * @param session The interpretation session to get the {@link ExpressoQIS#getExpressoEnv() expresso environment} from and for
		 *        {@link AbstractQIS#error(String) error reporting}
		 * @param style The toolkit inheriting Quick-Style
		 * @param applications A cache of compiled style applications for re-use
		 * @throws QonfigInterpretationException If an error occurs evaluating all the style information for the element
		 */
		public Default(List<QuickStyleValue<?>> declaredValues, QuickCompiledStyle parent, QuickStyleSheet styleSheet,
			QonfigElement element, ExpressoQIS session, QonfigToolkit style,
			Map<StyleApplicationDef, CompiledStyleApplication> applications) throws QonfigInterpretationException {
			theParent = parent;
			theElement = element;
			List<CompiledStyleValue<?>> evaldValues = new ArrayList<>(declaredValues.size());
			theDeclaredValues = Collections.unmodifiableList(evaldValues);
			for (QuickStyleValue<?> dv : declaredValues)
				evaldValues.add(dv.compile(session.getExpressoEnv(), applications));

			Map<QuickStyleAttribute<?>, BetterSortedList<? extends CompiledStyleValue<?>>> values = new HashMap<>();
			// Compile all attributes applicable to this element
			QuickTypeStyle type = QuickTypeStyle.getOrCompile(element.getType(), session, style);
			for (QuickStyleAttribute<?> attr : type.getAttributes().values())
				values.computeIfAbsent(attr,
					__ -> SortedTreeList.<CompiledStyleValue<?>> buildTreeList(CompiledStyleValue::compareTo).build());
			for (QonfigAddOn inh : element.getInheritance().getExpanded(QonfigAddOn::getInheritance)) {
				type = QuickTypeStyle.getOrCompile(inh, session, style);
				if (type == null)
					continue;
				for (QuickStyleAttribute<?> attr : type.getAttributes().values())
					values.computeIfAbsent(attr,
						__ -> SortedTreeList.<CompiledStyleValue<?>> buildTreeList(CompiledStyleValue::compareTo).build());
			}
			for (CompiledStyleValue<?> sv : theDeclaredValues)
				((List<CompiledStyleValue<?>>) values.computeIfAbsent(sv.getStyleValue().getAttribute(),
					__ -> SortedTreeList.<CompiledStyleValue<?>> buildTreeList(CompiledStyleValue::compareTo).build())).add(sv);
			if (styleSheet != null) {
				for (QuickStyleValue<?> sv : styleSheet.getValues(element))
					((List<CompiledStyleValue<?>>) values.computeIfAbsent(sv.getAttribute(),
						__ -> SortedTreeList.<CompiledStyleValue<?>> buildTreeList(CompiledStyleValue::compareTo).build()))
					.add(sv.compile(session.getExpressoEnv(), applications));
			}
			Map<QuickStyleAttribute<?>, QuickCompiledStyleAttribute<?>> styleValues = new HashMap<>();
			for (Map.Entry<QuickStyleAttribute<?>, BetterSortedList<? extends CompiledStyleValue<?>>> v : values.entrySet()) {
				QuickStyleAttribute<Object> attr = (QuickStyleAttribute<Object>) v.getKey();
				styleValues.put(attr, new QuickCompiledStyleAttribute<>(attr, this,
					QommonsUtils.unmodifiableCopy((List<CompiledStyleValue<Object>>) v.getValue())));
			}
			theValues = Collections.unmodifiableMap(styleValues);
			BetterMultiMap<String, QuickStyleAttribute<?>> attrsByName = BetterHashMultiMap.<String, QuickStyleAttribute<?>> buildHashed()//
				.withDistinctValues().buildMultiMap();
			for (QuickStyleAttribute<?> attr : theValues.keySet())
				attrsByName.add(attr.getName(), attr);
			theAttributesByName = BetterCollections.unmodifiableMultiMap(attrsByName);
		}

		@Override
		public QuickCompiledStyle getParent() {
			return theParent;
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		@Override
		public List<CompiledStyleValue<?>> getDeclaredValues() {
			return theDeclaredValues;
		}

		@Override
		public Set<QuickStyleAttribute<?>> getAttributes() {
			return theValues.keySet();
		}

		@Override
		public BetterMultiMap<String, QuickStyleAttribute<?>> getAttributesByName() {
			return theAttributesByName;
		}

		@Override
		public QuickInterpretedStyle interpret(QuickInterpretedStyle parent,
			Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
			List<InterpretedStyleValue<?>> declaredValues = new ArrayList<>(theValues.size());
			Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> values = new HashMap<>();
			QuickInterpretedStyle style = new QuickInterpretedStyle.Default(parent, this, Collections.unmodifiableList(declaredValues),
				Collections.unmodifiableMap(values));
			for (CompiledStyleValue<?> value : theDeclaredValues)
				declaredValues.add(value.interpret(applications));
			for (Map.Entry<QuickStyleAttribute<?>, QuickCompiledStyleAttribute<?>> value : theValues.entrySet()) {
				QuickStyleAttribute<Object> attr = (QuickStyleAttribute<Object>) value.getKey();
				QuickInterpretedStyle.QuickElementStyleAttribute<Object> inherited;
				inherited = parent != null && attr.isTrickleDown() ? getInherited(parent, attr) : null;
				values.put(attr, //
					((QuickCompiledStyleAttribute<Object>) value.getValue()).interpret(style, inherited, applications));
			}
			return style;
		}

		private static <T> QuickElementStyleAttribute<T> getInherited(QuickInterpretedStyle parent, QuickStyleAttribute<T> attr) {
			while (parent != null && !parent.getAttributes().contains(attr))
				parent = parent.getParent();
			return parent == null ? null : parent.get(attr);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(theElement.getType().getName() + " style:");
			for (CompiledStyleValue<?> value : theDeclaredValues)
				str.append("\n\t").append(value);
			return str.toString();
		}
	}

	/** A wrapper around another compiled style */
	public abstract class Wrapper implements QuickCompiledStyle {
		private final QuickCompiledStyle theParent;
		private final QuickCompiledStyle theWrapped;

		/**
		 * @param parent The parent style
		 * @param wrapped The style to wrap
		 */
		protected Wrapper(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
			theParent = parent;
			theWrapped = wrapped;
		}

		/** @return The wrapped style */
		protected QuickCompiledStyle getWrapped() {
			return theWrapped;
		}

		@Override
		public QuickCompiledStyle getParent() {
			return theParent;
		}

		@Override
		public QonfigElement getElement() {
			return theWrapped.getElement();
		}

		@Override
		public List<CompiledStyleValue<?>> getDeclaredValues() {
			return theWrapped.getDeclaredValues();
		}

		@Override
		public Set<QuickStyleAttribute<?>> getAttributes() {
			return theWrapped.getAttributes();
		}

		@Override
		public BetterMultiMap<String, QuickStyleAttribute<?>> getAttributesByName() {
			return theWrapped.getAttributesByName();
		}

		@Override
		public QuickInterpretedStyle interpret(QuickInterpretedStyle parent,
			Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
			return theWrapped.interpret(parent, applications);
		}
	}

	/**
	 * A structure containing all information necessary to get the value of a style attribute for an element
	 *
	 * @param <T> The type of the attribute
	 */
	public static class QuickCompiledStyleAttribute<T> {
		private final QuickStyleAttribute<T> theAttribute;
		private final QuickCompiledStyle theStyle;
		private final List<CompiledStyleValue<T>> theValues;

		/**
		 * @param attribute The attribute this structure is for
		 * @param style The element style this structure is for
		 * @param values All style values that may apply to the element for the attribute
		 */
		public QuickCompiledStyleAttribute(QuickStyleAttribute<T> attribute, QuickCompiledStyle style, List<CompiledStyleValue<T>> values) {
			theAttribute = attribute;
			theStyle = style;
			theValues = values;
		}

		/** @return The attribute this structure is for */
		public QuickStyleAttribute<T> getAttribute() {
			return theAttribute;
		}

		/** @return The element style this structure is for */
		public QuickCompiledStyle getStyle() {
			return theStyle;
		}

		/** @return All style values that may apply to the element for the attribute */
		public List<CompiledStyleValue<T>> getValues() {
			return theValues;
		}

		/**
		 * @param elementStyle The element style to interpret this attribute value into
		 * @param inherited The inherited style for the attribute
		 * @param applications A cache of interpreted style applications for re-use
		 * @return The interpreted value for this style attribute on the element
		 * @throws ExpressoInterpretationException If the condition or the value could not be interpreted
		 */
		public QuickElementStyleAttribute<T> interpret(QuickInterpretedStyle elementStyle, QuickElementStyleAttribute<T> inherited,
			Map<CompiledStyleApplication, InterpretedStyleApplication> applications) throws ExpressoInterpretationException {
			List<InterpretedStyleValue<T>> values = new ArrayList<>(theValues.size());
			for (CompiledStyleValue<T> v : theValues)
				values.add(v.interpret(applications));
			return new QuickElementStyleAttribute<>(theAttribute, elementStyle, Collections.unmodifiableList(values), inherited);
		}

		@Override
		public String toString() {
			return theStyle.getElement() + "." + theAttribute;
		}
	}
}
