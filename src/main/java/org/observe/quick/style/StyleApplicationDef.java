package org.observe.quick.style;

import java.util.*;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.*;
import org.observe.expresso.DynamicModelValue.Identity;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.ops.BinaryOperator;
import org.observe.expresso.ops.BufferedExpression;
import org.observe.expresso.ops.NameExpression;
import org.qommons.BiTuple;
import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.LocatedFilePosition;

/**
 * Definition structure parsed from a &lt;style> element determining what &lt;styled> {@link QonfigElement}s a {@link QuickStyleValue}
 * applies to
 */
public class StyleApplicationDef implements Comparable<StyleApplicationDef> {
	/** Inheritance scheme for {@link QonfigElementOrAddOn}s */
	public static MultiInheritanceSet.Inheritance<QonfigElementOrAddOn> STYLE_INHERITANCE = QonfigElementOrAddOn::isAssignableFrom;
	/** An {@link StyleApplicationDef} that applies to all {@link QonfigElement}s */
	public static final StyleApplicationDef ALL = new StyleApplicationDef(null, null, MultiInheritanceSet.empty(), null,
		Collections.emptyMap());

	private static final Map<DynamicModelValue.Identity, Integer> MODEL_VALUE_PRIORITY = new HashMap<>();

	/**
	 * @param modelValue The model value definition
	 * @param priorityAttr The style-model-value.priority attribute from the Quick-Style toolkit
	 * @return The priority of the given model value
	 */
	public static synchronized int getPriority(DynamicModelValue.Identity modelValue, QonfigAttributeDef.Declared priorityAttr) {
		Integer priority = MODEL_VALUE_PRIORITY.get(modelValue);
		if (priority != null)
			return priority;
		if (!modelValue.getDeclaration().isInstance(priorityAttr.getOwner()))
			priority = 0;
		else
			priority = Integer.parseInt(modelValue.getDeclaration().getAttributeText(priorityAttr));
		MODEL_VALUE_PRIORITY.put(modelValue, priority);
		return priority;
	}

	/**
	 * @param modelValues The model values to prioritize
	 * @param known Model values for which the priority is already known, an optimization
	 * @param priorityAttr The style-model-value.priority attribute from the Quick-Style toolkit
	 * @return All of the given model values, sorted by priority (in the key set, highest first) and mapped to their priority
	 */
	public static Map<DynamicModelValue.Identity, Integer> prioritizeModelValues(Collection<DynamicModelValue.Identity> modelValues,
		Map<DynamicModelValue.Identity, Integer> known, QonfigAttributeDef.Declared priorityAttr) {
		List<BiTuple<DynamicModelValue.Identity, Integer>> mvList = new ArrayList<>(modelValues.size());
		for (DynamicModelValue.Identity mv : modelValues) {
			Integer priority = known.get(mv);
			mvList.add(new BiTuple<>(mv, priority != null ? priority.intValue() : getPriority(mv, priorityAttr)));
		}
		Collections.sort(mvList, (mv1, mv2) -> -mv1.getValue2().compareTo(mv2.getValue2()));

		Map<DynamicModelValue.Identity, Integer> prioritizedMVs = new LinkedHashMap<>();
		for (BiTuple<DynamicModelValue.Identity, Integer> mv : mvList)
			prioritizedMVs.put(mv.getValue1(), mv.getValue2());
		return Collections.unmodifiableMap(prioritizedMVs);
	}

	private final StyleApplicationDef theParent;
	private final QonfigChildDef theRole;
	private final MultiInheritanceSet<QonfigElementOrAddOn> theTypes;
	private final int theTypeComplexity;
	private final LocatedExpression theCondition;
	private final Map<DynamicModelValue.Identity, Integer> theModelValues;

	private StyleApplicationDef(StyleApplicationDef parent, QonfigChildDef role, MultiInheritanceSet<QonfigElementOrAddOn> types,
		LocatedExpression condition, Map<DynamicModelValue.Identity, Integer> modelValues) {
		if ((parent != null) != (role != null))
			throw new IllegalArgumentException("A role must be accompanied by a parent style application and vice-versa");
		theParent = parent;
		theRole = role;
		theTypes = MultiInheritanceSet.unmodifiable(types);
		theCondition = condition;
		theModelValues = modelValues;

		// Put together derived fields
		int localComplexity;
		if (theTypes.isEmpty() && theRole == null)
			localComplexity = 0;
		else {
			Set<QonfigElementOrAddOn> visited = new HashSet<>();
			if (theRole != null)
				addHierarchy(theRole.getType(), visited);
			for (QonfigElementOrAddOn type : theTypes.values())
				addHierarchy(type, visited);
			localComplexity = visited.size();
		}
		theTypeComplexity = localComplexity + (theParent == null ? 0 : theParent.getTypeComplexity());
	}

	private static void addHierarchy(QonfigElementOrAddOn type, Set<QonfigElementOrAddOn> visited) {
		if (!visited.add(type))
			return;
		if (type.getSuperElement() != null)
			addHierarchy(type.getSuperElement(), visited);
		for (QonfigAddOn inh : type.getInheritance())
			addHierarchy(inh, visited);
	}

	/**
	 * @param ex The expression to find model values in
	 * @param modelValues The collection to add the model values into
	 * @param models The models to use to find referenced model values
	 * @param expresso A toolkit inheriting Expresso-Core
	 * @param styleSheet Whether the expression is from a style sheet. Model values that do not declare their
	 *        {@link org.observe.expresso.DynamicModelValue.Identity#getType() type} cannot be used as conditions in style sheets.
	 * @return The expression to use in place of the given condition
	 * @throws QonfigInterpretationException If a type-less condition is used from a style sheet
	 */
	public LocatedExpression findModelValues(LocatedExpression ex, Collection<DynamicModelValue.Identity> modelValues,
		ObservableModelSet models, QonfigToolkit expresso, boolean styleSheet) throws QonfigInterpretationException {
		ObservableExpression expression;
		try {
			expression = _findModelValues(ex.getExpression(), modelValues, models, expresso, styleSheet, 0);
		} catch (ExpressoEvaluationException e) {
			throw new QonfigInterpretationException(e.getMessage(), ex.getFilePosition(e.getErrorOffset()), e.getErrorLength(), e);
		}
		return new LocatedExpression() {
			@Override
			public int length() {
				return ex.length();
			}

			@Override
			public ObservableExpression getExpression() {
				return expression;
			}

			@Override
			public LocatedFilePosition getFilePosition(int offset) {
				return ex.getFilePosition(offset);
			}

			@Override
			public <M, MV extends M> ModelValueSynth<M, MV> evaluate(ModelInstanceType<M, MV> type, ExpressoEnv env)
				throws ExpressoInterpretationException {
				try {
					return expression.evaluate(type, env, 0);
				} catch (TypeConversionException e) {
					throw new ExpressoInterpretationException(e.getMessage(), ex.getFilePosition(0), 0, e);
				} catch (ExpressoEvaluationException e) {
					throw new ExpressoInterpretationException(e.getMessage(), ex.getFilePosition(e.getErrorOffset()), e.getErrorLength(),
						e);
				}
			}

			@Override
			public int hashCode() {
				return ex.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return ex.equals(obj);
			}

			@Override
			public String toString() {
				return ex.toString();
			}
		};
	}

	private ObservableExpression _findModelValues(ObservableExpression ex, Collection<DynamicModelValue.Identity> modelValues,
		ObservableModelSet models, QonfigToolkit expresso, boolean styleSheet, int expressionOffset) throws ExpressoEvaluationException {
		if (ex instanceof NameExpression && ((NameExpression) ex).getContext() == null) {
			String name = ((NameExpression) ex).getNames().getFirst().getName();
			ModelComponentNode<?, ?> node = models.getComponentIfExists(name);
			if (node != null) {
				if (node.getValueIdentity() instanceof DynamicModelValue.Identity)
					modelValues.add((DynamicModelValue.Identity) node.getValueIdentity());
			} else if (styleSheet) {
				Map<String, DynamicModelValue.Identity> typeValues = null;
				for (QonfigElementOrAddOn type : theTypes.values())
					typeValues = DynamicModelValue.getDynamicValues(expresso, type, typeValues);
				DynamicModelValue.Identity mv = typeValues.get(name);
				if (mv != null) {
					if (mv.getType() == null)
						throw new ExpressoEvaluationException(expressionOffset, ex.getExpressionLength(),
							"Cannot use model value " + mv + " from a style-sheet, as its type is not defined here");
					modelValues.add(mv);
					return new ModelValueExpression(ex, mv);
				}
			}
		} else {
			IdentityHashMap<ObservableExpression, ObservableExpression>[] replace = new IdentityHashMap[1];
			int c = 0;
			for (ObservableExpression child : ex.getChildren()) {
				ObservableExpression newChild = _findModelValues(child, modelValues, models, expresso, styleSheet, //
					ex.getChildOffset(c));
				if (newChild != child) {
					if (replace[0] == null)
						replace[0] = new IdentityHashMap<>();
					replace[0].put(child, newChild);
				}
				c++;
			}
			if (replace[0] != null) {
				return ex.replaceAll(child -> {
					ObservableExpression newChild = replace[0].get(child);
					return newChild != null ? newChild : child;
				});
			}
		}
		return ex;
	}

	/** Replacement expression for a {@link DynamicModelValue} in a spreadsheet condition */
	public static class ModelValueExpression implements ObservableExpression {
		private final ObservableExpression theWrapped;
		private final DynamicModelValue.Identity theModelValue;

		/**
		 * @param wrapped The expression from the style sheet referring to the model value
		 * @param modelValue The model value referred to
		 */
		public ModelValueExpression(ObservableExpression wrapped, Identity modelValue) {
			theWrapped = wrapped;
			theModelValue = modelValue;
		}

		/** @return The expression from the style sheet referring to the model value */
		public ObservableExpression getWrapped() {
			return theWrapped;
		}

		/** @return The model value referred to */
		public DynamicModelValue.Identity getModelValue() {
			return theModelValue;
		}

		@Override
		public int getChildOffset(int childIndex) {
			if (childIndex == 0)
				return 0;
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		}

		@Override
		public int getExpressionLength() {
			return theWrapped.getExpressionLength();
		}

		@Override
		public ModelType<?> getModelType(ExpressoEnv env) {
			return theWrapped.getModelType(env);
		}

		@Override
		public List<? extends ObservableExpression> getChildren() {
			return Collections.singletonList(theWrapped);
		}

		@Override
		public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
			ObservableExpression newThis = replace.apply(this);
			if (newThis != this)
				return newThis;
			ObservableExpression newW = theWrapped.replaceAll(replace);
			if (newW != theWrapped)
				return new ModelValueExpression(newW, theModelValue);
			return this;
		}

		@Override
		public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env,
			int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
			ModelComponentNode<?, ?> node;
			try {
				node = env.getModels().getIdentifiedValue(theModelValue);
			} catch (ModelException e) {
				throw new ExpressoEvaluationException(expressionOffset, theModelValue.getName().length(), "No such model value found", e);
			}
			try {
				return node.as(type);
			} catch (TypeConversionException e) {
				throw new ExpressoEvaluationException(expressionOffset, theModelValue.getName().length(), e.getMessage(), e);
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(theWrapped, theModelValue);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ModelValueExpression && theWrapped.equals(((ModelValueExpression) obj).theWrapped)
				&& theModelValue.equals(((ModelValueExpression) obj).theModelValue);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Conditionless application test
	 *
	 * @param element The element to test
	 * @return Whether the given element should use values from {@link QuickStyleValue style values} with this application
	 */
	public boolean applies(QonfigElement element) {
		QonfigElement parentApply = appliesLocal(element);
		if (parentApply == null)
			return false;
		StyleApplicationDef parent = getParent();
		if (parent != null && !parent.applies(parentApply))
			return false;
		return true;
	}

	/**
	 * Conditionless, local application test. Tests whether this application applies to the given element, regardless of its
	 * {@link #getParent()} condition
	 *
	 * @param element The element to test
	 * @return Whether the given element should use values from {@link QuickStyleValue style values} with this application
	 */
	protected QonfigElement appliesLocal(QonfigElement element) {
		for (QonfigElementOrAddOn type : theTypes.values()) {
			if (!element.isInstance(type))
				return null;
		}
		if (theRole != null) {
			if (!element.isInstance(theRole.getType()))
				return null;
			if (!element.getDeclaredRoles().contains(theRole.getDeclared()))
				return null;
			QonfigElement parent = element.getParent();
			if (parent == null || !parent.isInstance(theRole.getOwner()))
				return null;
			return parent;
		} else
			return element;
	}

	/**
	 * @return The application that an {@link QonfigElement element}'s {@link QonfigElement#getParent() parent} must pass for this
	 *         application to {@link #applies(QonfigElement) apply} to it
	 */
	public StyleApplicationDef getParent() {
		return theParent;
	}

	/**
	 * @return All the types that an {@link QonfigElement element} must be an {@link QonfigElement#isInstance(QonfigElementOrAddOn)
	 *         instance} of for this application to {@link #applies(QonfigElement) apply} to it
	 */
	public MultiInheritanceSet<QonfigElementOrAddOn> getTypes() {
		return theTypes;
	}

	/**
	 * @return The child role that an {@link QonfigElement element} must {@link QonfigElement#getParentRoles() fulfill} for this application
	 *         to {@link #applies(QonfigElement) apply} to it
	 */
	public QonfigChildDef getRole() {
		return theRole;
	}

	/** @return The condition that an element's model must pass for this application to {@link #applies(QonfigElement) apply} to it */
	public ObservableExpression getCondition() {
		return theCondition == null ? null : theCondition.getExpression();
	}

	/**
	 * @return All model values that this application's {@link #getCondition() condition} references, sorted by priority (in the key set,
	 *         highest first) and mapped to their priority
	 */
	public Map<DynamicModelValue.Identity, Integer> getModelValues() {
		return theModelValues;
	}

	@Override
	public int compareTo(StyleApplicationDef o) {
		int comp = 0;
		// Compare the complexity of the role path
		if (comp == 0)
			comp = -Integer.compare(getDepth(), o.getDepth());

		// Compare the complexity of the element type
		if (comp == 0)
			comp = -Integer.compare(getTypeComplexity(), o.getTypeComplexity());

		// Compare the priority of model values used in the condition and value
		Iterator<Integer> iter1 = theModelValues.values().iterator();
		Iterator<Integer> iter2 = o.theModelValues.values().iterator();
		while (comp == 0) {
			if (iter1.hasNext()) {
				if (iter2.hasNext())
					comp = -iter1.next().compareTo(iter2.next());
				else
					comp = -1; // We use more model values--higher priority
			} else if (iter2.hasNext()) {
				comp = 1; // We user fewer model values--lower priority
			} else {
				break;
			}
		}
		return comp;
	}

	/**
	 * @param types The element types to apply to
	 * @return A {@link StyleApplicationDef} that applies to {@link QonfigElement}s that this application applies to <b>AND</b> that are
	 *         {@link QonfigElement#isInstance(QonfigElementOrAddOn) instances} of <b>ALL</b> of the given types
	 */
	public StyleApplicationDef forType(QonfigElementOrAddOn... types) {
		MultiInheritanceSet<QonfigElementOrAddOn> newTypes = null;
		for (QonfigElementOrAddOn type : types) {
			if (theRole != null && type.isAssignableFrom(theRole.getType()))
				continue;
			if (!theTypes.contains(type)) {
				if (newTypes == null) {
					newTypes = MultiInheritanceSet.create(STYLE_INHERITANCE);
					newTypes.addAll(theTypes.values());
				}
				newTypes.add(type);
				break;
			}
		}
		if (newTypes == null)
			return this; // All types already contained
		return new StyleApplicationDef(theParent, theRole, newTypes, theCondition, theModelValues);
	}

	/**
	 * @param child The child role to apply to
	 * @return A {@link StyleApplicationDef} that applies to {@link QonfigElement}s that this application applies to <b>AND</b> that
	 *         {@link QonfigElement#getParentRoles() fulfills} the given child role
	 */
	public StyleApplicationDef forChild(QonfigChildDef child) {
		MultiInheritanceSet<QonfigElementOrAddOn> types = MultiInheritanceSet.create(STYLE_INHERITANCE);
		types.add(child.getType());
		types.addAll(child.getInheritance());
		return new StyleApplicationDef(this, child, types, null, theModelValues);
	}

	/**
	 * @param condition The condition to apply to
	 * @param env The Expresso environment containing model values that the condition may use
	 * @param priorityAttr The style-model-value.priority attribute from the Quick-Style toolkit
	 * @param styleSheet Whether the application is defined from a style-sheet, as opposed to inline under the element
	 * @return A {@link StyleApplicationDef} that applies to {@link QonfigElement}s that this application applies to <b>AND</b> whose
	 *         model passes the given condition
	 * @throws QonfigInterpretationException If the condition uses any unusable model values, such as un-typed {@link DynamicModelValue
	 *         model values} from a style-sheet
	 */
	public StyleApplicationDef forCondition(LocatedExpression condition, ExpressoEnv env, QonfigAttributeDef.Declared priorityAttr,
		boolean styleSheet) throws QonfigInterpretationException {
		LocatedExpression newCondition;
		if (theCondition == null)
			newCondition = condition;
		else
			newCondition = new LocatedAndExpression(theCondition, condition);

		Set<DynamicModelValue.Identity> mvs = new LinkedHashSet<>();
		// We don't need to worry about satisfying anything here. The model values just need to be available for the link level.
		newCondition = findModelValues(newCondition, mvs, env.getModels(), priorityAttr.getDeclarer(), styleSheet);
		mvs.addAll(theModelValues.keySet());
		return new StyleApplicationDef(theParent, theRole, theTypes, newCondition,
			prioritizeModelValues(mvs, theModelValues, priorityAttr));
	}

	private static class LocatedAndExpression implements LocatedExpression {
		private final LocatedExpression theLeft;
		private final LocatedExpression theRight;
		private final BinaryOperator theExpression;

		public LocatedAndExpression(LocatedExpression left, LocatedExpression right) {
			theLeft = left;
			theRight = right;
			theExpression = new BinaryOperator("&&", //
				BufferedExpression.buffer(0, left.getExpression(), 1), //
				BufferedExpression.buffer(1, right.getExpression(), 0));
		}

		@Override
		public ObservableExpression getExpression() {
			return theExpression;
		}

		@Override
		public int length() {
			return theExpression.getExpressionLength();
		}

		@Override
		public LocatedFilePosition getFilePosition(int offset) {
			if (offset < theLeft.length())
				return theLeft.getFilePosition(offset);
			else if (offset < theLeft.length() + 4)
				return new LocatedFilePosition("StyleApplicationDef.java", 0, 0, 0);
			else
				return theRight.getFilePosition(offset - theLeft.length() - 4);
		}
	}

	/** @return 1 if this application has no {@link #getParent()}, else its parent's depth plus 1 */
	public int getDepth() {
		if (theParent == null)
			return 1;
		else
			return 1 + theParent.getDepth();
	}

	/**
	 * @return A measure of the specificity of this application's {@link #getTypes() types}. A greater value means this application will
	 *         {@link #applies(QonfigElement) apply} fewer {@link QonfigElement}s
	 */
	public int getTypeComplexity() {
		return theTypeComplexity;
	}

	/**
	 *
	 * @param expressoEnv The Expresso environment in which to {@link ObservableExpression#evaluate(ModelInstanceType, ExpressoEnv, int)
	 *        evaluate} {@link #getCondition() conditions}
	 * @param applications A cache of compiled applications for re-use
	 * @return An {@link InterpretedStyleApplication} for this application in the given environment
	 * @throws QonfigInterpretationException If a condition could not be
	 *         {@link ObservableExpression#evaluate(ModelInstanceType, ExpressoEnv, int) evaluated}
	 */
	public CompiledStyleApplication compile(ExpressoEnv expressoEnv, Map<StyleApplicationDef, CompiledStyleApplication> applications)
		throws QonfigInterpretationException {
		CompiledStyleApplication parent;
		if (theParent == null)
			parent = null;
		else {
			parent = applications.get(theParent);
			if (parent == null) {
				parent = theParent.compile(expressoEnv.with(getParentModel(expressoEnv.getModels()), null), applications);
				applications.put(theParent, parent);
			}
		}
		CompiledModelValue<SettableValue<?>, SettableValue<Boolean>> conditionV = theCondition == null ? null
			: CompiledModelValue.of(theCondition.toString(), ModelTypes.Value, //
				() -> theCondition.evaluate(ModelTypes.Value.BOOLEAN, expressoEnv));
		return new CompiledStyleApplication(parent, this, conditionV);
	}

	// /**
	// *
	// * @param expressoEnv The Expresso environment in which to {@link ObservableExpression}{@link #evaluate(ExpressoEnv) evaluate}
	// * {@link #getCondition() conditions}
	// * @return An {@link InterpretedStyleApplication} for this application in the given environment
	// * @throws ExpressoInterpretationException If a condition could not be
	// * {@link ObservableExpression#evaluate(ModelInstanceType, ExpressoEnv, int) evaluated}
	// */
	// public InterpretedStyleApplication evaluate(ExpressoEnv expressoEnv) throws ExpressoInterpretationException {
	// InterpretedStyleApplication parent = theParent == null ? null : theParent.evaluate(//
	// expressoEnv.with(getParentModel(expressoEnv.getModels()), null));
	// ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> conditionV = theCondition == null ? null
	// : theCondition.evaluate(ModelTypes.Value.BOOLEAN, expressoEnv, 0);
	// return new InterpretedStyleApplication(parent, this, conditionV);
	// }

	private static ObservableModelSet getParentModel(ObservableModelSet models) {
		// Get the models for the most recent styled ancestor element
		QonfigElement element = models.getTagValue(StyleQIS.STYLED_ELEMENT_TAG);
		if (element == null)
			return models;
		ObservableModelSet inh = models;
		// Only consider direct descendants for now. See if we need to do better later.
		while (true) {
			if (inh.getInheritance().isEmpty())
				return models;
			inh = inh.getInheritance().values().iterator().next();
			QonfigElement parentEl = inh.getTagValue(StyleQIS.STYLED_ELEMENT_TAG);
			if (parentEl == null)
				return models;
			else if (parentEl == element)
				continue;
			else
				return inh;
		}
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (theParent != null)
			str.append(theParent).append('.').append(theRole.getName());
		if (!theTypes.isEmpty()) {
			int count = 0;
			String lastType = null;
			for (QonfigElementOrAddOn type : theTypes.values()) {
				boolean isInRole = theRole != null;
				if (isInRole) {
					isInRole = type.isAssignableFrom(theRole.getType());
					for (QonfigAddOn inh : theRole.getInheritance()) {
						if (isInRole)
							break;
						isInRole = type.isAssignableFrom(inh);
					}
				}
				if (!isInRole) {// Otherwise, the type is implied, and there's no need to print it
					if (count == 0)
						lastType = type.getName();
					else if (count == 1) {
						lastType = null;
						str.append('[').append(lastType).append(", ").append(type.getName());
					} else
						str.append(", ").append(type.getName());
					count++;
				}
			}
			if (lastType != null) {
				if (theRole != null)
					str.append('[');
				str.append(lastType);
				if (theRole != null)
					str.append(']');
			} else if (count > 1)
				str.append(']');
		}
		if (theCondition != null)
			str.append('(').append(theCondition).append(')');
		return str.toString();
	}
}
