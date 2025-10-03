package org.observe.expresso.ops;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.Equivalence;
import org.observe.Observable.CoreChangeSources;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.Stamped;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableListElement;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;

import com.google.common.reflect.TypeToken;

/** An expression representing access to an array value by index */
public class ArrayInitializerExpression implements ObservableExpression {
	private final List<ObservableExpression> theValues;

	/** @param values The values in the array initializer */
	public ArrayInitializerExpression(List<ObservableExpression> values) {
		theValues = values;
	}

	/** @return The values in the array initializer */
	public List<ObservableExpression> getValues() {
		return theValues;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		if (childIndex < 0 || childIndex >= theValues.size())
			throw new IndexOutOfBoundsException(childIndex + " of " + theValues.size());

		int length = 1;
		for (int i = 0; i < childIndex; i++)
			length += theValues.get(i).getExpressionLength() + 1;
		return length;
	}

	@Override
	public int getExpressionLength() {
		int length = 2;// '{' and '}'
		if (theValues.size() > 1)
			length += theValues.size() - 1; // intermediate ','s
		for (ObservableExpression value : theValues)
			length += value.getExpressionLength();
		return length;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return theValues;
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		List<ObservableExpression> replacementValues = null;
		for (int i = 0; i < theValues.size(); i++) {
			replacement = theValues.get(i).replaceAll(replace);
			if (replacementValues == null && replacement != theValues.get(i)) {
				replacementValues = new ArrayList<>(theValues.size());
				replacementValues.addAll(theValues.subList(0, i));
			}
			if (replacementValues != null)
				replacementValues.add(replacement);
		}
		if (replacementValues != null)
			return new ArrayInitializerExpression(Collections.unmodifiableList(replacementValues));
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		TypeToken<?> elementType;
		if (type.getModelType() == ModelTypes.Collection)
			elementType = type.getType(0);
		else if (type.getModelType() == ModelTypes.Value) {
			TypeToken<?> collectionType = type.getType(0);
			if (collectionType.isArray())
				elementType = collectionType.getComponentType();
			else if (TypeTokens.getRawType(collectionType).isAssignableFrom(ObservableCollection.class))
				elementType = collectionType.resolveType(ObservableCollection.class.getTypeParameters()[0]);
			else {
				exHandler.handle1(
					() -> new ExpressoInterpretationException("An array initializer expression can only be evaluated as a collection",
						env.reporting().getPosition(), getExpressionLength()));
				return null;
			}
		} else if (type.getModelType() == ModelTypes.Action) {
			List<EvaluatedExpression<ObservableAction, ObservableAction>> components = new ArrayList<>(theValues.size());
			for (ObservableExpression component : theValues) {
				ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
					.holder2(exHandler.isInstantiating());
				EvaluatedExpression<ObservableAction, ObservableAction> componentAction = component.evaluate(ModelTypes.Action.instance(),
					env, expressionOffset, tce);
				if (componentAction == null) {
					if (tce.hasException1())
						exHandler.handle1(() -> new ExpressoInterpretationException(tce.get1().getMessage(), tce.get1().getPosition(),
							component.getExpressionLength(), tce.get1()));
					else
						exHandler.handle1(() -> new ExpressoInterpretationException(tce.get2().getMessage(), env.reporting().getPosition(),
							getExpressionLength(), tce.get2()));
					return null;
				}
				components.add(componentAction);
			}
			return (EvaluatedExpression<M, MV>) ObservableExpression.evEx(expressionOffset, getExpressionLength(),
				new InterpretedMultiAction(components), null, components);
		} else {
			exHandler
			.handle1(() -> new ExpressoInterpretationException("An array initializer expression can only be evaluated as a collection",
				env.reporting().getPosition(), getExpressionLength()));
			return null;
		}
		return (EvaluatedExpression<M, MV>) doEval(elementType, env, expressionOffset, exHandler);
	}

	private <T, EX extends Throwable> EvaluatedExpression<ObservableCollection<?>, ObservableCollection<T>> doEval(TypeToken<T> elType,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		List<EvaluatedExpression<SettableValue<?>, SettableValue<T>>> values = new ArrayList<>(theValues.size());
		ModelInstanceType<SettableValue<?>, SettableValue<T>> elModelType = ModelTypes.Value.forType(elType);
		int addlOffset = 1;
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
			.holder2(exHandler.isInstantiating());
		for (ObservableExpression value : theValues) {
			EvaluatedExpression<SettableValue<?>, SettableValue<T>> evaldValue = value.evaluate(elModelType, env.at(addlOffset),
				expressionOffset + addlOffset, tce.use());
			if (evaldValue == null) {
				if (tce.hasException1())
					exHandler.handle1(() -> new ExpressoInterpretationException(tce.get1().getMessage(), tce.get1().getPosition(),
						value.getExpressionLength(), tce.get1()));
				else
					exHandler.handle1(() -> new ExpressoInterpretationException(tce.get2().getMessage(), env.reporting().getPosition(),
						getExpressionLength(), tce.get2()));
				return null;
			}
			values.add(evaldValue);
		}
		if (!values.isEmpty()) {
			elType = (TypeToken<T>) TypeTokens.get()
				.getCommonType(values.stream().map(v -> v.getType().getType(0)).collect(Collectors.toList()));
		}
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
			new Interpreted<>(ModelTypes.Collection.forType(elType), values), null, values);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("{");
		boolean first = true;
		for (ObservableExpression value : theValues) {
			if (first)
				first = false;
			else
				str.append(',');
			str.append(value);
		}
		return str.append('}').toString();
	}

	static class Interpreted<T> implements InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<T>> {
		private final ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> theType;
		private final List<EvaluatedExpression<SettableValue<?>, SettableValue<T>>> theValues;

		Interpreted(ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> type,
			List<EvaluatedExpression<SettableValue<?>, SettableValue<T>>> values) {
			theType = type;
			theValues = Collections.unmodifiableList(values);
		}

		@Override
		public ModelInstanceType<ObservableCollection<?>, ObservableCollection<T>> getType() {
			return theType;
		}

		@Override
		public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
			return theValues;
		}

		@Override
		public ModelValueInstantiator<ObservableCollection<T>> instantiate() throws ModelInstantiationException {
			List<ModelValueInstantiator<SettableValue<T>>> values = new ArrayList<>(theValues.size());
			for (EvaluatedExpression<SettableValue<?>, SettableValue<T>> value : theValues)
				values.add(value.instantiate());
			return new Instantiator<>(values);
		}
	}

	static class Instantiator<T> implements ModelValueInstantiator<ObservableCollection<T>> {
		private final List<ModelValueInstantiator<SettableValue<T>>> theValues;

		Instantiator(List<ModelValueInstantiator<SettableValue<T>>> values) {
			theValues = values;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			for (ModelValueInstantiator<SettableValue<T>> value : theValues)
				value.instantiate();
		}

		@Override
		public ObservableCollection<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<T>[] values = new SettableValue[theValues.size()];
			for (int i = 0; i < values.length; i++)
				values[i] = theValues.get(i).get(models);
			return new Instance<>(BetterList.of(values));
		}

		@Override
		public ObservableCollection<T> forModelCopy(ObservableCollection<T> value, ModelSetInstance sourceModels,
			ModelSetInstance newModels) throws ModelInstantiationException {
			Instance<T> instance = (Instance<T>) value;
			SettableValue<T>[] values = null;
			for (int i = 0; i < instance.getValues().size(); i++) {
				SettableValue<T> copy = theValues.get(i).forModelCopy(instance.getValues().get(i), sourceModels, newModels);
				if (values == null && copy != instance.getValues().get(i)) {
					values = new SettableValue[theValues.size()];
					for (int j = 0; j < i; j++)
						values[j] = instance.getValues().get(j);
				}
				if (values != null)
					values[i] = copy;
			}
			if (values != null)
				return new Instance<>(BetterList.of(values));
			return value;
		}
	}

	static class Instance<T> extends AbstractIdentifiable implements ObservableCollection<T> {
		class Element implements ListElement<T> {
			protected final ListElement<SettableValue<T>> theValueElement;

			Element(ListElement<SettableValue<T>> valueElement) {
				theValueElement = valueElement;
			}

			Instance<T> getInstance() {
				return Instance.this;
			}

			@Override
			public ElementId getElementId() {
				return theValueElement.getElementId();
			}

			@Override
			public T get() {
				return theValueElement.get().get();
			}

			@Override
			public ListElement<T> getAdjacent(boolean next) {
				ListElement<SettableValue<T>> adj = theValueElement.getAdjacent(next);
				return adj == null ? null : new Element(adj);
			}

			@Override
			public int getElementsBefore() {
				return theValueElement.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theValueElement.getElementsAfter();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof CollectionElement && getElementId().equals(((CollectionElement<?>) obj).getElementId());
			}

			@Override
			public String toString() {
				return theValueElement.toString();
			}
		}

		class MutableElement extends Element implements MutableListElement<T> {
			MutableElement(ListElement<SettableValue<T>> valueElement) {
				super(valueElement);
			}

			@Override
			public MutableListElement<T> getAdjacent(boolean next) {
				ListElement<SettableValue<T>> adj = theValueElement.getAdjacent(next);
				return adj == null ? null : new MutableElement(adj);
			}

			@Override
			public String isEnabled() {
				return theValueElement.get().isEnabled().get();
			}

			@Override
			public String isAcceptable(T value) {
				return theValueElement.get().isAcceptable(value);
			}

			@Override
			public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
				theValueElement.get().set(value, null);
			}

			@Override
			public String canRemove() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}

		public Element element(ListElement<SettableValue<T>> valueElement) {
			return valueElement == null ? null : new Element(valueElement);
		}

		private final BetterList<SettableValue<T>> theValues;

		Instance(BetterList<SettableValue<T>> values) {
			theValues = values;
		}

		BetterList<SettableValue<T>> getValues() {
			return theValues;
		}

		@Override
		public ListElement<T> getElement(int index) throws IndexOutOfBoundsException {
			return element(theValues.getElement(index));
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public ListElement<T> getElement(T value, boolean first) {
			for (ListElement<SettableValue<T>> valueEl : theValues.elements()) {
				if (Objects.equals(valueEl.get().get(), value))
					return element(valueEl);
			}
			return null;
		}

		@Override
		public ListElement<T> getElement(ElementId id) {
			return element(theValues.getElement(id));
		}

		@Override
		public ListElement<T> getTerminalElement(boolean first) {
			return element(theValues.getTerminalElement(first));
		}

		@Override
		public MutableListElement<T> mutableElement(ElementId id) {
			return new MutableElement(theValues.getElement(id));
		}

		@Override
		public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return BetterList.of(
				theValues.getElementsBySource(sourceEl, sourceCollection).stream().map(el -> element((ListElement<SettableValue<T>>) el)));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			return theValues.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theValues.getEquivalentElement(equivalentEl);
		}

		@Override
		public String canAdd(T value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return null;
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
				return getElement(valueEl);
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.baseId("ArrayInitializer", this);
		}

		@Override
		public Instance<T> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEmpty() {
			return theValues.isEmpty();
		}

		@Override
		public long getStamp() {
			return Stamped.compositeStamp(theValues);
		}

		@Override
		public boolean isEventing() {
			for (SettableValue<T> value : theValues) {
				if (value.isEventing())
					return true;
			}
			return false;
		}

		@Override
		public int size() {
			return theValues.size();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(null, () -> theValues, //
				v -> Lockable.lockable(v, write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(null, () -> theValues, //
				v -> Lockable.lockable(v, write, cause));
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return CollectionUtils.concat(QommonsUtils.filterMap(theValues, null, v -> v.getCurrentCauses()));
		}

		@Override
		public CoreId getCoreId() {
			CoreId core = CoreId.EMPTY;
			for (SettableValue<T> value : theValues)
				core = core.and(value.getCoreId());
			return core;
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return CoreChangeSources.of(theValues, SettableValue::noInitChanges);
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstrained.getThreadConstraint(theValues);
		}

		@Override
		public boolean isLockSupported() {
			for (SettableValue<T> value : theValues) {
				if (value.isLockSupported())
					return true;
			}
			return false;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends T>> observer) {
			List<Subscription> subs = new ArrayList<>(theValues.size());
			int i = 0;
			for (CollectionElement<SettableValue<T>> value : theValues.elements()) {
				int index = i;
				subs.add(value.get().noInitChanges().act(evt -> {
					ObservableCollectionEvent<T> cce = ObservableCollectionEvent.createCollectionEvent(value.getElementId(), index,
						CollectionChangeType.set, evt.getOldValue(), evt.getNewValue(), evt);
					try (Transaction t = cce.use()) {
						observer.accept(cce);
					}
				}));
				i++;
			}
			return Subscription.forAll(subs);
		}

		@Override
		public void clear() {
		}

		@Override
		public Equivalence<? super T> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public void setValue(Collection<ElementId> elements, T value) {
			for (ElementId element : elements)
				theValues.getElement(element).get().set(value, null);
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}

	static class InterpretedMultiAction implements InterpretedValueSynth<ObservableAction, ObservableAction> {
		private final List<EvaluatedExpression<ObservableAction, ObservableAction>> theComponents;

		InterpretedMultiAction(List<EvaluatedExpression<ObservableAction, ObservableAction>> components) {
			theComponents = components;
		}

		@Override
		public ModelInstanceType<ObservableAction, ObservableAction> getType() {
			return ModelTypes.Action.instance();
		}

		@Override
		public List<EvaluatedExpression<ObservableAction, ObservableAction>> getComponents() {
			return Collections.unmodifiableList(theComponents);
		}

		@Override
		public ModelValueInstantiator<ObservableAction> instantiate() throws ModelInstantiationException {
			return new MultiActionInstantiator(this);
		}
	}

	static class MultiActionInstantiator implements ModelValueInstantiator<ObservableAction> {
		private final List<ModelValueInstantiator<ObservableAction>> theComponents;

		MultiActionInstantiator(InterpretedMultiAction interpreted) throws ModelInstantiationException {
			theComponents = new ArrayList<>(interpreted.getComponents().size());
			for (EvaluatedExpression<ObservableAction, ObservableAction> component : interpreted.getComponents())
				theComponents.add(component.instantiate());
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			for (ModelValueInstantiator<ObservableAction> component : theComponents)
				component.instantiate();
		}

		@Override
		public ObservableAction get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			List<ObservableAction> components = new ArrayList<>(theComponents.size());
			for (ModelValueInstantiator<ObservableAction> component : theComponents)
				components.add(component.get(models));
			return new MultiAction(Collections.unmodifiableList(components));
		}

		@Override
		public ObservableAction forModelCopy(ObservableAction value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			MultiAction action = (MultiAction) value;
			List<ObservableAction> newComponents = null;
			for (int i = 0; i < action.getComponents().size(); i++) {
				ObservableAction component = action.getComponents().get(i);
				ObservableAction newComponent = theComponents.get(i).forModelCopy(component, sourceModels, newModels);
				if (newComponents == null && newComponent != component)
					newComponents = new ArrayList<>(action.getComponents().subList(0, i));
				if (newComponents != null)
					newComponents.add(newComponent);
			}
			if (newComponents != null)
				return new MultiAction(Collections.unmodifiableList(newComponents));
			else
				return action;
		}
	}

	static class MultiAction extends AbstractIdentifiable implements ObservableAction {
		private final List<ObservableAction> theComponents;

		MultiAction(List<ObservableAction> components) {
			theComponents = components;
		}

		List<ObservableAction> getComponents() {
			return theComponents;
		}

		@Override
		protected Object createIdentity() {
			Identifiable.CustomIdentityBuilder builder = Identifiable.buildId()//
				.append("{");
			boolean first = true;
			for (ObservableAction component : theComponents) {
				if (first)
					first = false;
				else
					builder.append(", ");
				builder.withPrintedId(component instanceof Identifiable ? ((Identifiable) component).getIdentity() : component);
			}
			return builder.append("}").build();
		}

		@Override
		public boolean isEventing() {
			for (ObservableAction component : theComponents) {
				if (component.isEventing())
					return true;
			}
			return false;
		}

		@Override
		public void act(Object cause) throws IllegalStateException {
			for (ObservableAction component : theComponents)
				component.act(cause);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			ObservableValue<String>[] components = new ObservableValue[theComponents.size()];
			for (int i = 0; i < components.length; i++)
				components[i] = theComponents.get(i).isEnabled();
			return ObservableValue.firstValue(msg -> msg != null, null, components);
		}
	}
}
