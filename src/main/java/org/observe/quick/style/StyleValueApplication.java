package org.observe.quick.style;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.DynamicModelValue.Identity;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.BinaryOperator;
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

public class StyleValueApplication implements Comparable<StyleValueApplication> {
	public static MultiInheritanceSet.Inheritance<QonfigElementOrAddOn> STYLE_INHERITANCE = QonfigElementOrAddOn::isAssignableFrom;
	public static final StyleValueApplication ALL = new StyleValueApplication(null, null, MultiInheritanceSet.empty(), null,
		Collections.emptyMap());

	private static final Map<DynamicModelValue.Identity, Integer> MODEL_VALUE_PRIORITY = new HashMap<>();

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

	public static Map<DynamicModelValue.Identity, Integer> prioritizeModelValues(Collection<DynamicModelValue.Identity> modelValues,
		Map<DynamicModelValue.Identity, Integer> known, QonfigAttributeDef.Declared priorityAttr) {
		List<BiTuple<DynamicModelValue.Identity, Integer>> mvList = new ArrayList<>(modelValues.size());
		for (DynamicModelValue.Identity mv : modelValues) {
			Integer priority = known.get(mv);
			mvList.add(new BiTuple<>(mv, priority != null ? priority.intValue() : getPriority(mv, priorityAttr)));
		}
		Collections.sort(mvList, (mv1, mv2) -> -mv1.getValue2().compareTo(mv2.getValue2()));

		Map<DynamicModelValue.Identity, Integer> prioritizedMVs = new LinkedHashMap<>();
		int complexity = 0;
		for (BiTuple<DynamicModelValue.Identity, Integer> mv : mvList) {
			prioritizedMVs.put(mv.getValue1(), mv.getValue2());
			complexity += mv.getValue2();
		}
		return Collections.unmodifiableMap(prioritizedMVs);
	}

	private final StyleValueApplication theParent;
	private final QonfigChildDef theRole;
	private final MultiInheritanceSet<QonfigElementOrAddOn> theTypes;
	private final int theTypeComplexity;
	private final ObservableExpression theCondition;
	private final Map<DynamicModelValue.Identity, Integer> theModelValues;

	private StyleValueApplication(StyleValueApplication parent, QonfigChildDef role, MultiInheritanceSet<QonfigElementOrAddOn> types,
		ObservableExpression condition, Map<DynamicModelValue.Identity, Integer> modelValues) {
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

	public ObservableExpression findModelValues(ObservableExpression ex, Collection<DynamicModelValue.Identity> modelValues,
		ObservableModelSet models, QonfigToolkit expresso, boolean styleSheet) throws QonfigInterpretationException {
		if (ex instanceof NameExpression && ((NameExpression) ex).getContext() == null) {
			String name = ((NameExpression) ex).getNames().getFirst();
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
						throw new QonfigInterpretationException(
							"Cannot use model value " + mv + " from a style-sheet, as its type is not defined here");
					modelValues.add(mv);
					return new WrappingObservableExpression(ex, mv);
				}
			}
		} else {
			IdentityHashMap<ObservableExpression, ObservableExpression>[] replace = new IdentityHashMap[1];
			for (ObservableExpression child : ex.getChildren()) {
				ObservableExpression newChild = findModelValues(child, modelValues, models, expresso, styleSheet);
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

	public static class WrappingObservableExpression implements ObservableExpression {
		private final ObservableExpression theWrapped;
		private final DynamicModelValue.Identity theModelValue;

		public WrappingObservableExpression(ObservableExpression wrapped, Identity modelValue) {
			theWrapped = wrapped;
			theModelValue = modelValue;
		}

		public ObservableExpression getWrapped() {
			return theWrapped;
		}

		public DynamicModelValue.Identity getModelValue() {
			return theModelValue;
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
				return new WrappingObservableExpression(newW, theModelValue);
			return this;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
			throws QonfigInterpretationException {
			ModelComponentNode<?, ?> node = env.getModels().getIdentifiedValue(theModelValue);
			return node.as(type);
		}

		@Override
		public int hashCode() {
			return Objects.hash(theWrapped, theModelValue);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof WrappingObservableExpression && theWrapped.equals(((WrappingObservableExpression) obj).theWrapped)
				&& theModelValue.equals(((WrappingObservableExpression) obj).theModelValue);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public boolean applies(QonfigElement element) {
		QonfigElement parentApply = appliesLocal(element);
		if (parentApply == null)
			return false;
		StyleValueApplication parent = getParent();
		if (parent != null && !parent.applies(parentApply))
			return false;
		return true;
	}

	public QonfigElement appliesLocal(QonfigElement element) {
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

	public StyleValueApplication getParent() {
		return theParent;
	}

	public MultiInheritanceSet<QonfigElementOrAddOn> getTypes() {
		return theTypes;
	}

	public QonfigChildDef getRole() {
		return theRole;
	}

	public ObservableExpression getCondition() {
		return theCondition;
	}

	public Map<DynamicModelValue.Identity, Integer> getModelValues() {
		return theModelValues;
	}

	@Override
	public int compareTo(StyleValueApplication o) {
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

	public StyleValueApplication forType(QonfigElementOrAddOn... types) {
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
		return new StyleValueApplication(theParent, theRole, newTypes, theCondition, theModelValues);
	}

	public StyleValueApplication forChild(QonfigChildDef child) {
		MultiInheritanceSet<QonfigElementOrAddOn> types = MultiInheritanceSet.create(STYLE_INHERITANCE);
		types.add(child.getType());
		types.addAll(child.getInheritance());
		return new StyleValueApplication(this, child, types, null, theModelValues);
	}

	public StyleValueApplication forCondition(ObservableExpression condition, ExpressoEnv env, QonfigAttributeDef.Declared priorityAttr,
		boolean styleSheet) throws QonfigInterpretationException {
		ObservableExpression newCondition;
		if (theCondition == null)
			newCondition = condition;
		else
			newCondition = new BinaryOperator("&&", theCondition, condition);

		Set<DynamicModelValue.Identity> mvs = new LinkedHashSet<>();
		// We don't need to worry about satisfying anything here. The model values just need to be available for the link level.
		newCondition = findModelValues(newCondition, mvs, env.getModels(), priorityAttr.getDeclarer(), styleSheet);
		mvs.addAll(theModelValues.keySet());
		return new StyleValueApplication(theParent, theRole, theTypes, newCondition,
			prioritizeModelValues(mvs, theModelValues, priorityAttr));
	}

	public int getDepth() {
		if (theParent == null)
			return 1;
		else
			return 1 + theParent.getDepth();
	}

	public int getTypeComplexity() {
		return theTypeComplexity;
	}

	public EvaluatedStyleApplication evaluate(ExpressoEnv expressoEnv) throws QonfigInterpretationException {
		EvaluatedStyleApplication parent = theParent == null ? null : theParent.evaluate(//
			expressoEnv.with(getParentModel(expressoEnv.getModels()), null));
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> conditionV = theCondition == null ? null
			: theCondition.evaluate(ModelTypes.Value.BOOLEAN, expressoEnv);
		return new EvaluatedStyleApplication(parent, this, conditionV);
	}

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
