package org.observe.quick.style;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ops.BinaryOperator;
import org.observe.expresso.ops.NameExpression;
import org.qommons.LambdaUtils;
import org.qommons.MultiInheritanceSet;
import org.qommons.collect.MultiMap;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class StyleValueApplication {
	public static MultiInheritanceSet.Inheritance<QonfigElementOrAddOn> STYLE_INHERITANCE = QonfigElementOrAddOn::isAssignableFrom;
	public static final StyleValueApplication ALL = new StyleValueApplication(null, null, MultiInheritanceSet.empty(), null, null, 0,
		Collections.emptyList());
	public static final ObservableValue<Boolean> TRUE = ObservableValue.of(boolean.class, true);

	private final StyleValueApplication theParent;
	private final QonfigChildDef theRole;
	private final MultiInheritanceSet<QonfigElementOrAddOn> theTypes;
	private final int theTypeComplexity;
	private final ObservableExpression theCondition;
	private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> theConditionValue;
	private final int theConditionComplexity;
	private final List<QuickModelValue<?>> theModelValues;

	private StyleValueApplication(StyleValueApplication parent, QonfigChildDef role, MultiInheritanceSet<QonfigElementOrAddOn> types,
		ObservableExpression condition, ValueContainer<SettableValue<?>, SettableValue<Boolean>> conditionValue, int conditionComplexity,
		List<QuickModelValue<?>> modelValues) {
		if ((parent != null) != (role != null))
			throw new IllegalArgumentException("A role must be accompanied by a parent style application and vice-versa");
		theParent = parent;
		theRole = role;
		theTypes = MultiInheritanceSet.unmodifiable(types);
		theCondition = condition;
		theConditionValue = conditionValue;
		theConditionComplexity = conditionComplexity;
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

	public static int findModelValues(ObservableExpression ex, Collection<QuickModelValue<?>> modelValues,
		MultiMap<String, QuickModelValue<?>> availableModelValues) throws QonfigInterpretationException {
		if (ex instanceof NameExpression && ((NameExpression) ex).getContext() == null) {
			String name = ((NameExpression) ex).getNames().getFirst();
			Collection<QuickModelValue<?>> values = availableModelValues.get(name);
			if (values.isEmpty())
				return 1;
			else if (values.size() > 1)
				throw new QonfigInterpretationException("Multiple model values named '" + name + "': " + values);
			modelValues.addAll(values);
			return 1;
		} else {
			int complexity = 1; // 1 for this expression
			for (ObservableExpression child : ex.getChildren())
				complexity += findModelValues(child, modelValues, availableModelValues);
			return complexity;
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

	public List<QuickModelValue<?>> getModelValues() {
		return theModelValues;
	}

	public ObservableValue<Boolean> getCondition(ModelSetInstance model) {
		ObservableValue<Boolean> parentCond;
		if (theParent == null)
			parentCond = TRUE;
		else
			parentCond = theParent.getCondition(ExpressoQIS.getParentModels(model));

		ObservableValue<Boolean> localCond;
		if (theCondition != null)
			localCond = theConditionValue.get(model);
		else
			localCond = TRUE;

		if (TRUE.equals(parentCond))
			return localCond;
		else if (TRUE.equals(localCond))
			return parentCond;
		else
			return parentCond.transform(boolean.class, tx -> tx.combineWith(localCond)//
				.combine(LambdaUtils.printableBiFn((c1, c2) -> c1 && c2, "||", "||")));
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
		return new StyleValueApplication(theParent, theRole, newTypes, theCondition, theConditionValue, theConditionComplexity,
			theModelValues);
	}

	public StyleValueApplication forChild(QonfigChildDef child) {
		MultiInheritanceSet<QonfigElementOrAddOn> types = MultiInheritanceSet.create(STYLE_INHERITANCE);
		types.add(child.getType());
		types.addAll(child.getInheritance());
		return new StyleValueApplication(this, child, types, null, null, theConditionComplexity, theModelValues);
	}

	public StyleValueApplication forCondition(ObservableExpression condition, ExpressoEnv env,
		MultiMap<String, QuickModelValue<?>> availableModelValues) throws QonfigInterpretationException {
		ObservableExpression newCondition;
		if (theCondition == null)
			newCondition = condition;
		else
			newCondition = new BinaryOperator("&&", theCondition, condition);

		Set<QuickModelValue<?>> mvs = new LinkedHashSet<>();
		// We don't need to worry about satisfying anything here. The model values just need to be available for the link level.
		int complexity = findModelValues(newCondition, mvs, availableModelValues);
		List<QuickModelValue<?>> mvList = new ArrayList<>(mvs.size() + (theParent == null ? 0 : theParent.getModelValues().size()));
		mvList.addAll(mvs);
		if (theParent != null) {
			complexity = theParent.getConditionComplexity();
			mvList.addAll(theParent.getModelValues());
		}
		if (!mvs.isEmpty()) {
			// We don't need to worry about satisfying anything here. The model values just need to be available for the link level.
			ObservableModelSet.WrappedBuilder wrappedBuilder = env.getModels().wrap();
			for (QuickModelValue<?> mv : mvs)
				wrappedBuilder.withCustomValue(mv.getName(), mv);
			env = env.with(wrappedBuilder.build(), null);
		}
		Collections.sort(mvList, (mv1, mv2) -> -Integer.compare(mv1.getPriority(), mv2.getPriority()));
		return new StyleValueApplication(theParent, theRole, theTypes, newCondition, //
			newCondition.evaluate(ModelTypes.Value.forType(boolean.class), env), complexity, Collections.unmodifiableList(mvList));
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

	public int getConditionComplexity() {
		return theConditionComplexity;
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
