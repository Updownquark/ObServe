package org.observe.quick.style;

import java.util.*;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceConverter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableExpression.EvaluatedExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretableModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelComponentNode;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelTag;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.ops.BinaryOperator;
import org.observe.expresso.ops.BufferedExpression;
import org.observe.expresso.ops.NameExpression;
import org.observe.expresso.qonfig.ElementModelValue;
import org.observe.expresso.qonfig.ElementModelValue.Identity;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.LocatedExpression;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.MultiInheritanceSet;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

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
	/** An {@link StyleApplicationDef} that applies to no {@link QonfigElement}s */
	public static final StyleApplicationDef NONE = new StyleApplicationDef(null, null, MultiInheritanceSet.empty(),
		new LocatedExpression() {
		@Override
		public int length() {
			return "false".length();
		}

		@Override
		public LocatedPositionedContent getFilePosition() {
			return null;
		}

		@Override
		public ObservableExpression getExpression() {
			return new ObservableExpression.LiteralExpression<>("false", false);
		}
	}, Collections.emptyMap());

	/**
	 * The {@link ObservableModelSet#getTagValue(ModelTag) model tag} that the styled {@link QonfigElement} will be stored in to enable
	 * hierarchical style values
	 */
	public static final ModelTag<QonfigElement> STYLED_ELEMENT_TAG = ModelTag.of(QonfigElement.class.getSimpleName(),
		TypeTokens.get().of(QonfigElement.class));
	private static final Map<ElementModelValue.Identity, Integer> MODEL_VALUE_PRIORITY = new WeakHashMap<>();

	/**
	 * @param modelValue The model value definition
	 * @param priorityAttr The style-model-value.priority attribute from the Quick-Style toolkit
	 * @return The priority of the given model value
	 */
	public static synchronized int getPriority(ElementModelValue.Identity modelValue, QonfigAttributeDef.Declared priorityAttr) {
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
	 * @param into The map into which to put all of the given model values, sorted by priority (in the key set, highest first) and mapped to
	 *        their priority
	 */
	public static void prioritizeModelValues(Collection<ElementModelValue.Identity> modelValues,
		Map<ElementModelValue.Identity, Integer> known, QonfigAttributeDef.Declared priorityAttr,
		Map<ElementModelValue.Identity, Integer> into) {
		List<BiTuple<ElementModelValue.Identity, Integer>> mvList = new ArrayList<>(modelValues.size());
		for (ElementModelValue.Identity mv : modelValues) {
			Integer priority = known.get(mv);
			mvList.add(new BiTuple<>(mv, priority != null ? priority.intValue() : getPriority(mv, priorityAttr)));
		}
		Collections.sort(mvList, (mv1, mv2) -> -mv1.getValue2().compareTo(mv2.getValue2()));

		for (BiTuple<ElementModelValue.Identity, Integer> mv : mvList)
			into.put(mv.getValue1(), mv.getValue2());
	}

	private final StyleApplicationDef theParent;
	private final QonfigChildDef theRole;
	private final MultiInheritanceSet<QonfigElementOrAddOn> theTypes;
	private final int theTypeComplexity;
	private final LocatedExpression theCondition;
	private final Map<ElementModelValue.Identity, Integer> theModelValues;
	private final long thePriority;
	private final int theConditionComplexity;

	private StyleApplicationDef(StyleApplicationDef parent, QonfigChildDef role, MultiInheritanceSet<QonfigElementOrAddOn> types,
		LocatedExpression condition, Map<ElementModelValue.Identity, Integer> modelValues) {
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
		thePriority = modelValues.values().stream().mapToLong(Integer::longValue).sum();
		theConditionComplexity = condition == null ? 0 : getComplexity(condition.getExpression());
	}

	private static void addHierarchy(QonfigElementOrAddOn type, Set<QonfigElementOrAddOn> visited) {
		if (type == null || !visited.add(type))
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
	 *        {@link org.observe.expresso.qonfig.ElementModelValue.Identity#getType() type} cannot be used as conditions in style sheets.
	 * @param dmvCache The model value cache to use
	 * @param reporting The error reporting to report errors to
	 * @return The expression to use in place of the given condition
	 * @throws QonfigInterpretationException If a type-less condition is used from a style sheet
	 */
	public LocatedExpression findModelValues(LocatedExpression ex, Collection<ElementModelValue.Identity> modelValues,
		ObservableModelSet models, QonfigToolkit expresso, boolean styleSheet, ElementModelValue.Cache dmvCache, ErrorReporting reporting)
			throws QonfigInterpretationException {
		ObservableExpression expression;
		expression = _findModelValues(ex.getExpression(), modelValues, models, expresso, styleSheet, dmvCache, reporting);
		if (expression == ex.getExpression())
			return ex;
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
			public LocatedPositionedContent getFilePosition() {
				return ex.getFilePosition();
			}

			@Override
			public <M, MV extends M> EvaluatedExpression<M, MV> interpret(ModelInstanceType<M, MV> type, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, ExpressoInterpretationException, NeverThrown> doubleX = ExceptionHandler
					.<ExpressoInterpretationException> thrower().stack(//
						ExceptionHandler.<TypeConversionException> holder());
				EvaluatedExpression<M, MV> result = expression.evaluate(type, env.at(getFilePosition()), 0, doubleX);
				if (doubleX.get2() != null)
					throw new ExpressoInterpretationException(doubleX.get2().getMessage(), ex.getFilePosition(0), 0, doubleX.get2());
				return result;
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

	private ObservableExpression _findModelValues(ObservableExpression ex, Collection<ElementModelValue.Identity> modelValues,
		ObservableModelSet models, QonfigToolkit expresso, boolean styleSheet, ElementModelValue.Cache dmvCache, ErrorReporting reporting)
			throws QonfigInterpretationException {
		if (ex instanceof ModelValueExpression) {
			modelValues.add(((ModelValueExpression) ex).getModelValue());
		} else if (ex instanceof NameExpression && ((NameExpression) ex).getContext() == null) {
			NameExpression nameEx = (NameExpression) ex;
			String name = nameEx.getNames().getFirst().getName();
			ModelComponentNode<?> node = models.getComponentIfExists(name);
			if (node != null) {
				if (node.getValueIdentity() instanceof ElementModelValue.Identity)
					modelValues.add((ElementModelValue.Identity) node.getValueIdentity());
			} else if (styleSheet) {
				Map<String, ElementModelValue.Identity> typeValues = getTypeValues(dmvCache, expresso, null, reporting);
				ElementModelValue.Identity mv = typeValues == null ? null : typeValues.get(name);
				if (mv != null) {
					modelValues.add(mv);
					return new ModelValueExpression(ex, mv);
				} // else
				// I'd love to throw an exception here if the name we found isn't an element value,
				// but it could be e.g. a static call to something that's perfectly valid.
				// Unfortunately, this will just have to throw an exception in each place it can't be interpreted.
			}
		} else {
			IdentityHashMap<ObservableExpression, ObservableExpression>[] replace = new IdentityHashMap[1];
			for (ObservableExpression child : ex.getComponents()) {
				ObservableExpression newChild = _findModelValues(child, modelValues, models, expresso, styleSheet, dmvCache, reporting);
				if (newChild != child) {
					if (replace[0] == null)
						replace[0] = new IdentityHashMap<>();
					replace[0].put(child, newChild);
				}
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

	private Map<String, ElementModelValue.Identity> getTypeValues(ElementModelValue.Cache dmvCache, QonfigToolkit expresso,
		Map<String, ElementModelValue.Identity> values, ErrorReporting reporting) throws QonfigInterpretationException {
		for (QonfigElementOrAddOn type : theTypes.values())
			values = dmvCache.getDynamicValues(expresso, type, values, reporting);
		if (theRole != null && theRole.getType() != null)
			values = dmvCache.getDynamicValues(expresso, theRole.getType(), values, reporting);
		if (theParent != null)
			values = theParent.getTypeValues(dmvCache, expresso, values, reporting);
		return values;
	}

	/** Replacement expression for a {@link ElementModelValue} in a spreadsheet condition */
	public static class ModelValueExpression implements ObservableExpression {
		private final ObservableExpression theWrapped;
		private final ElementModelValue.Identity theModelValue;

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
		public ElementModelValue.Identity getModelValue() {
			return theModelValue;
		}

		@Override
		public int getComponentOffset(int childIndex) {
			if (childIndex == 0)
				return 0;
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		}

		@Override
		public int getExpressionLength() {
			return theWrapped.getExpressionLength();
		}

		@Override
		public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) throws ExpressoCompilationException {
			return theWrapped.getModelType(env, expressionOffset);
		}

		@Override
		public List<? extends ObservableExpression> getComponents() {
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
		public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
			InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
				throws ExpressoInterpretationException, EX {

			InterpretableModelComponentNode<?> interpretableNode = env.getModels().getIdentifiedComponentIfExists(theModelValue);
			if (interpretableNode != null) {// If we already have a handle on it, use that
				InterpretedModelComponentNode<?, ?> node = interpretableNode.interpreted();
				ExceptionHandler.Single<TypeConversionException, NeverThrown> tce = ExceptionHandler.holder();
				InterpretedValueSynth<M, MV> nodeX = node.as(type, env, tce);
				if (nodeX != null)
					return ObservableExpression.evEx(expressionOffset, getExpressionLength(), nodeX, theModelValue);
				exHandler.handle1(
					new ExpressoInterpretationException(tce.get1().getMessage(), env.reporting().getPosition(), getExpressionLength()));
				return null;
			}
			// Otherwise, we may just not be in the right environment yet
			if (!"value".equals(theModelValue.getDeclaration().getType().getName())
				|| !ExpressoBaseV0_1.NAME.equals(theModelValue.getDeclaration().getType().getDeclarer().getName()))
				throw new ExpressoInterpretationException(
					"Cannot use model value of type " + theModelValue.getDeclaration().getType() + " in this style",
					env.reporting().getPosition(), getExpressionLength());
			else if (theModelValue.getType() == null)
				throw new ExpressoInterpretationException("Cannot use a variable-type model value in this style",
					env.reporting().getPosition(), getExpressionLength());
			ModelInstanceConverter<SettableValue<?>, M> converter = ModelTypes.Value.forType(//
				(TypeToken<Object>) theModelValue.getType().getType(env))//
				.convert(type, env);
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
				new Interpreted<>(theModelValue, (ModelInstanceConverter<SettableValue<?>, MV>) converter), theModelValue);
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

		static class Interpreted<MS, M, MV extends M> implements InterpretedValueSynth<M, MV> {
			private final ElementModelValue.Identity theModelValue;
			private final ModelInstanceConverter<MS, MV> theConverter;

			Interpreted(Identity modelValue, ModelInstanceConverter<MS, MV> converter) {
				theModelValue = modelValue;
				theConverter = converter;
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return (ModelInstanceType<M, MV>) theConverter.getType();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() {
				return new Instantiator<>(theModelValue, theConverter);
			}
		}

		static class Instantiator<MV1, MV2> implements ModelValueInstantiator<MV2> {
			private final ElementModelValue.Identity theModelValue;
			private final ModelInstanceConverter<MV1, MV2> theConverter;

			Instantiator(Identity modelValue, ModelInstanceConverter<MV1, MV2> converter) {
				theModelValue = modelValue;
				theConverter = converter;
			}

			@Override
			public void instantiate() {}

			@Override
			public MV2 get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				MV1 modelValue = (MV1) models.getByValueId(theModelValue);
				return theConverter.convert(modelValue);
			}

			@Override
			public MV2 forModelCopy(MV2 value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return get(newModels);
			}
		}
	}

	private static int getComplexity(ObservableExpression ex) {
		int complexity = 1;
		for (ObservableExpression child : ex.getComponents())
			complexity += getComplexity(child);
		return complexity;
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
			if (theRole.getType() != null && !element.isInstance(theRole.getType()))
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
	public Map<ElementModelValue.Identity, Integer> getModelValues() {
		return theModelValues;
	}

	/** @return A heuristic of the complexity of this application's {@link #getCondition() condition} */
	public int getConditionComplexity() {
		return theConditionComplexity;
	}

	@Override
	public int compareTo(StyleApplicationDef o) {
		int comp;
		// Compare the priority of model values used in the condition and value
		comp = -Long.compare(thePriority, o.thePriority);

		// Compare the complexity of the role path
		if (comp == 0)
			comp = -Integer.compare(getDepth(), o.getDepth());

		// Compare the complexity of the element type
		if (comp == 0)
			comp = -Integer.compare(getTypeComplexity(), o.getTypeComplexity());

		// Compare the logical complexity of the condition.
		// This is somewhat fuzzy (e.g. a parenthetic expression "(expr)" will show as more complex than its content alone "expr"),
		// but still valuable.
		if (comp == 0)
			comp = -Integer.compare(getConditionComplexity(), o.getConditionComplexity());

		return comp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(theParent, theRole, theTypes, theCondition);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof StyleApplicationDef))
			return false;
		StyleApplicationDef other = (StyleApplicationDef) obj;
		return Objects.equals(theParent, other.theParent)//
			&& Objects.equals(theRole, other.theRole)//
			&& theTypes.equals(other.theTypes)//
			&& Objects.equals(theCondition, other.theCondition);
	}

	/**
	 * @param types The types to test
	 * @return Whether the given types are compatible with this application. E.g. 2 unrelated {@link QonfigElementDef}s cannot be part of
	 *         the same application, as a single {@link QonfigElement} cannot possibly be of both types.
	 */
	public boolean isCompatible(QonfigElementOrAddOn... types) {
		for (QonfigElementOrAddOn type : types) {
			QonfigElementDef typeEl;
			if (type instanceof QonfigElementDef)
				typeEl = (QonfigElementDef) type;
			else if (type.getSuperElement() != null)
				typeEl = type.getSuperElement();
			else
				continue; // Compatible with anything

			if (theRole != null && theRole.getType() != null//
				&& !typeEl.isAssignableFrom(theRole.getType()) && !theRole.getType().isAssignableFrom(typeEl))
				return false;
			for (QonfigElementOrAddOn myType : theTypes.values()) {
				QonfigElementDef myTypeEl;
				if (myType instanceof QonfigElementDef)
					myTypeEl = (QonfigElementDef) myType;
				else if (myType.getSuperElement() != null)
					myTypeEl = myType.getSuperElement();
				else
					continue; // Compatible with anything

				if (!typeEl.isAssignableFrom(myTypeEl) && !myTypeEl.isAssignableFrom(typeEl))
					return false;
			}
		}
		return true;
	}

	/**
	 * @param types The element types to apply to
	 * @return A {@link StyleApplicationDef} that applies to {@link QonfigElement}s that this application applies to <b>AND</b> that are
	 *         {@link QonfigElement#isInstance(QonfigElementOrAddOn) instances} of <b>ALL</b> of the given types
	 * @throws IllegalArgumentException If one of the given types is {{@link #isCompatible(QonfigElementOrAddOn...) incompatible} with
	 *         another or one of the types in this application
	 */
	public StyleApplicationDef forType(QonfigElementOrAddOn... types) throws IllegalArgumentException {
		MultiInheritanceSet<QonfigElementOrAddOn> newTypes = null;
		for (QonfigElementOrAddOn type : types) {
			if (theTypes.contains(type))
				continue;

			QonfigElementDef typeEl;
			if (type instanceof QonfigElementDef)
				typeEl = (QonfigElementDef) type;
			else if (type.getSuperElement() != null)
				typeEl = type.getSuperElement();
			else
				typeEl = null; // Compatible with anything

			if (theRole != null && theRole.getType() != null) {
				if (type.isAssignableFrom(theRole.getType()))
					continue;
				else if (typeEl != null && !typeEl.isAssignableFrom(theRole.getType()) && !theRole.getType().isAssignableFrom(typeEl))
					throw new IllegalArgumentException(
						"Type " + type + " is incompatible with type " + theRole.getType() + " of role " + theRole);
			}
			if (typeEl != null) {
				for (QonfigElementOrAddOn myType : theTypes.values()) {
					QonfigElementDef myTypeEl;
					if (myType instanceof QonfigElementDef)
						myTypeEl = (QonfigElementDef) myType;
					else if (myType.getSuperElement() != null)
						myTypeEl = myType.getSuperElement();
					else
						continue; // Compatible with anything

					if (!typeEl.isAssignableFrom(myTypeEl) && !myTypeEl.isAssignableFrom(typeEl))
						throw new IllegalArgumentException("Type " + type + " is incompatible with type " + myType);
				}
			}
			if (newTypes == null) {
				newTypes = MultiInheritanceSet.create(STYLE_INHERITANCE);
				newTypes.addAll(theTypes.values());
			}
			newTypes.add(type);
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
		if (child.getType() != null)
			types.add(child.getType());
		types.addAll(child.getInheritance());
		return new StyleApplicationDef(this, child, types, null, theModelValues);
	}

	/**
	 * @param condition The condition to apply to
	 * @param models The model set containing model values that the condition may use
	 * @param priorityAttr The style-model-value.priority attribute from the Quick-Style toolkit
	 * @param styleSheet Whether the application is defined from a style-sheet, as opposed to inline under the element
	 * @param dmvCache The model value cache to use
	 * @param reporting The error reporting to report errors to
	 * @return A {@link StyleApplicationDef} that applies to {@link QonfigElement}s that this application applies to <b>AND</b> whose model
	 *         passes the given condition
	 * @throws QonfigInterpretationException If the condition uses any unusable model values, such as un-typed {@link ElementModelValue
	 *         model values} from a style-sheet
	 */
	public StyleApplicationDef forCondition(LocatedExpression condition, ObservableModelSet models,
		QonfigAttributeDef.Declared priorityAttr, boolean styleSheet, ElementModelValue.Cache dmvCache, ErrorReporting reporting)
			throws QonfigInterpretationException {
		LocatedExpression newCondition;
		Map<ElementModelValue.Identity, Integer> modelValues = new HashMap<>();
		if (theCondition == null) {
			newCondition = condition;
			modelValues.putAll(theModelValues);
		} else
			newCondition = new LocatedAndExpression(theCondition, condition);

		newCondition = getPrioritizedModelValues(newCondition, models, priorityAttr, styleSheet, dmvCache, modelValues, reporting);
		return new StyleApplicationDef(theParent, theRole, theTypes, newCondition, modelValues);
	}

	private LocatedExpression getPrioritizedModelValues(LocatedExpression newCondition, ObservableModelSet models,
		QonfigAttributeDef.Declared priorityAttr, boolean styleSheet, ElementModelValue.Cache dmvCache,
		Map<ElementModelValue.Identity, Integer> modelValues, ErrorReporting reporting) throws QonfigInterpretationException {
		Set<ElementModelValue.Identity> mvs = new LinkedHashSet<>();
		// We don't need to worry about satisfying anything here. The model values just need to be available for the link level.
		newCondition = findModelValues(newCondition, mvs, models, priorityAttr.getDeclarer(), styleSheet, dmvCache, reporting);
		mvs.addAll(theModelValues.keySet());
		prioritizeModelValues(mvs, theModelValues, priorityAttr, modelValues);
		return newCondition;
	}

	/**
	 * @param other The other application to combine with this one
	 * @return An application that {@link #applies(QonfigElement) applies} to an element if and only if both this application and
	 *         <code>other</code> apply to it
	 */
	public StyleApplicationDef and(StyleApplicationDef other) {
		if (!isCompatible(other.getTypes().values().toArray(new QonfigElementOrAddOn[0])))
			return NONE;

		StyleApplicationDef parent;
		if (theParent != null) {
			if (other.theParent != null) {
				parent = theParent.and(other.theParent);
				if (parent == NONE)
					return NONE;
			} else
				parent = theParent;
		} else
			parent = other.theParent;

		QonfigChildDef role;
		if (theRole != null) {
			if (other.theRole != null) {
				if (theRole.isFulfilledBy(other.theRole))
					role = other.theRole;
				else if (other.theRole.isFulfilledBy(theRole))
					role = theRole;
				else {// Although it might be possible for a child to fulfill 2 unrelated roles,
					// this class doesn't currently support it
					return NONE;
				}
			} else
				role = theRole;
		} else
			role = other.theRole;

		MultiInheritanceSet<QonfigElementOrAddOn> types = MultiInheritanceSet.create(STYLE_INHERITANCE);
		types.addAll(theTypes.values());
		types.addAll(other.theTypes.values());

		LocatedExpression condition;
		Map<Identity, Integer> modelValues;
		if (theCondition != null) {
			if (other.theCondition != null) {
				// Would theoretically be possible here to search for impossible conditions
				// like b && !b, but that's a lot of work
				condition = new LocatedAndExpression(theCondition, other.theCondition);
				modelValues = new LinkedHashMap<>();
				modelValues.putAll(theModelValues);
				modelValues.putAll(other.theModelValues);
			} else {
				condition = theCondition;
				modelValues = theModelValues;
			}
		} else {
			condition = other.theCondition;
			modelValues = other.theModelValues;
		}

		return new StyleApplicationDef(parent, role, types, condition, modelValues);
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
		public LocatedPositionedContent getFilePosition() {
			return new LocatedAndLocation(theLeft.getFilePosition(), " && ", theRight.getFilePosition());
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

		@Override
		public String toString() {
			return theLeft + " && " + theRight;
		}
	}

	private static class LocatedAndLocation implements LocatedPositionedContent {
		private final LocatedPositionedContent theLeft;
		private final String theOperator;
		private final LocatedPositionedContent theRight;

		LocatedAndLocation(LocatedPositionedContent left, String operator, LocatedPositionedContent right) {
			theLeft = left;
			theOperator = operator;
			theRight = right;
		}

		@Override
		public int length() {
			return theLeft.length() + theOperator.length() + theRight.length();
		}

		@Override
		public char charAt(int index) {
			if (index < theLeft.length())
				return theLeft.charAt(index);
			else if (index < theLeft.length() + theOperator.length())
				return theOperator.charAt(index - theLeft.length());
			else
				return theRight.charAt(index - theLeft.length() - theOperator.length());
		}

		@Override
		public String getFileLocation() {
			return theLeft.getFileLocation();
		}

		@Override
		public LocatedFilePosition getPosition(int index) {
			if (index < theLeft.length())
				return theLeft.getPosition(index);
			else if (index < theLeft.length() + theOperator.length())
				return theRight.getPosition(0);
			else
				return theRight.getPosition(index);
		}

		@Override
		public int getSourceLength(int from, int to) {
			int leftLen = theLeft.length();
			if (from < leftLen) {
				if (to <= leftLen)
					return theLeft.getSourceLength(from, to);
				else if (to <= leftLen + theOperator.length())
					return theLeft.getSourceLength(from, theLeft.length());
				else
					return theLeft.getSourceLength(from, theLeft.length()) + theOperator.length()
					+ theRight.getSourceLength(0, to - leftLen - theOperator.length());
			} else if (from < theLeft.length() + theOperator.length()) {
				if (to < leftLen + theOperator.length())
					return 0;
				else
					return theRight.getSourceLength(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
			} else
				return theRight.getSourceLength(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
		}

		@Override
		public CharSequence getSourceContent(int from, int to) {
			int leftLen = theLeft.length();
			if (from < leftLen) {
				if (to <= leftLen)
					return theLeft.getSourceContent(from, to);
				else if (to <= leftLen + theOperator.length())
					return theLeft.getSourceContent(from, theLeft.length());
				else
					return theLeft.getSourceContent(from, theLeft.length()).toString() + theOperator
						+ theRight.getSourceContent(0, to - leftLen - theOperator.length());
			} else if (from < theLeft.length() + theOperator.length()) {
				if (to < leftLen + theOperator.length())
					return "";
				else
					return theRight.getSourceContent(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
			} else
				return theRight.getSourceContent(from - leftLen - theOperator.length(), to - leftLen - theOperator.length());
		}

		@Override
		public LocatedPositionedContent subSequence(int startIndex) {
			if (startIndex < theLeft.length())
				return new LocatedAndLocation(theLeft.subSequence(startIndex), theOperator, theRight);
			else if (startIndex < theLeft.length() + theOperator.length())
				return theRight;
			else
				return theRight.subSequence(startIndex - theLeft.length() - theOperator.length());
		}

		@Override
		public LocatedPositionedContent subSequence(int startIndex, int endIndex) {
			int leftLen = theLeft.length();
			if (startIndex < leftLen) {
				if (endIndex <= leftLen)
					return theLeft.subSequence(startIndex, endIndex);
				else if (endIndex <= leftLen + theOperator.length())
					return theLeft.subSequence(startIndex);
				else
					return new LocatedAndLocation(theLeft.subSequence(startIndex), theOperator,
						theRight.subSequence(0, endIndex - leftLen - theOperator.length()));
			} else if (startIndex < theLeft.length() + theOperator.length()) {
				if (endIndex < leftLen + theOperator.length())
					return theRight.subSequence(0, 0);
				else
					return theRight.subSequence(startIndex - leftLen - theOperator.length(), endIndex - leftLen - theOperator.length());
			} else
				return theRight.subSequence(startIndex - leftLen - theOperator.length(), endIndex - leftLen - theOperator.length());
		}

		@Override
		public String toString() {
			return theLeft.toString();
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
	 * @param env The Expresso environment in which to
	 *        {@link ObservableExpression#evaluate(ModelInstanceType, InterpretedExpressoEnv, int, org.qommons.ex.ExceptionHandler.Double)
	 *        evaluate} {@link #getCondition() conditions}
	 * @param appCache A cache of compiled applications for re-use
	 * @return An {@link InterpretedStyleApplication} for this application in the given environment
	 * @throws ExpressoInterpretationException If a condition could not be
	 *         {@link ObservableExpression#evaluate(ModelInstanceType, InterpretedExpressoEnv, int, org.qommons.ex.ExceptionHandler.Double)
	 *         evaluated}
	 */
	public InterpretedStyleApplication interpret(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
		throws ExpressoInterpretationException {
		InterpretedStyleApplication parent;
		if (theParent == null)
			parent = null;
		else {
			parent = appCache.getApplication(theParent, env.with(getParentModel(env.getModels())));
		}
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> condition = theCondition == null ? null : //
			theCondition.interpret(ModelTypes.Value.BOOLEAN, env);
		return new InterpretedStyleApplication(parent, this, condition);
	}

	/**
	 * @param models The models to get the parent of
	 * @return The models that are the parent of the given model, to get inherited style values from
	 */
	public static InterpretedModelSet getParentModel(InterpretedModelSet models) {
		// Get the models for the most recent styled ancestor element
		QonfigElement element = models.getTagValue(STYLED_ELEMENT_TAG);
		if (element == null)
			return models;
		InterpretedModelSet inh = models;
		// Only consider direct descendants for now. See if we need to do better later.
		while (true) {
			if (inh.getInheritance().isEmpty())
				return models;
			inh = inh.getInheritance().values().iterator().next();
			QonfigElement parentEl = inh.getTagValue(STYLED_ELEMENT_TAG);
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
					isInRole = theRole.getType() == null || type.isAssignableFrom(theRole.getType());
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
						str.append('[').append(lastType).append(", ").append(type.getName());
						lastType = null;
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
