package org.observe.assoc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * Represents an observable, hierarchical data structure
 *
 * @param <N> The type of nodes (containers) in the tree
 * @param <V> The type of values in the tree
 */
public interface ObservableTree<N, V> extends Transactable {
	/** @return The root node of the tree */
	ObservableValue<N> getRoot();

	/** @return The value type of the tree */
	TypeToken<V> getValueType();

	/**
	 * @param node The node to get the value for
	 * @return The value for the given node
	 */
	ObservableValue<? extends V> getValue(N node);

	/**
	 * @param node The node to get the children of
	 * @return All nodes with the given node as their parent
	 */
	ObservableCollection<? extends N> getChildren(N node);

	/**
	 * Builds a tree from components
	 *
	 * @param root The root for the tree
	 * @param valueType The value type of the tree
	 * @param getValue The node-value getter for the tree
	 * @param getChildren The children getter for the tree
	 * @return The built tree
	 */
	public static <N, V> ObservableTree<N, V> of(ObservableValue<N> root, TypeToken<V> valueType,
		Function<? super N, ? extends ObservableValue<? extends V>> getValue,
			Function<? super N, ? extends ObservableCollection<? extends N>> getChildren) {
		return new ComposedTree<>(root, valueType, getValue, getChildren);
	}

	/**
	 * @param tree The tree to get the value paths of
	 * @param onlyTerminal Whether to include only terminal paths, or also to include intermediate paths (paths ending in a node that also
	 *        has children not in the path)
	 * @return A collection of immutable, constant lists of values. Each list is the values from the root to a node. The number of lists is
	 *         equal to the total number of nodes (if <code>onlyTerminal</code> is false) or leaf nodes in the tree
	 */
	public static <N, V> ObservableCollection<List<V>> valuePathsOf(ObservableTree<N, V> tree, boolean onlyTerminal) {
		return valuePathsOf(tree, null, onlyTerminal);
	}

	/**
	 * @param tree The tree to get the value paths of
	 * @param nodeCreator Used to add values into a tree
	 * @param onlyTerminal Whether to include only terminal paths, or also to include intermediate paths (paths ending in a node that also
	 *        has children not in the path)
	 * @return A collection of immutable, constant lists of values. Each list is the values from the root to a node. The number of lists is
	 *         equal to the total number of nodes (if <code>onlyTerminal</code> is false) or leaf nodes in the tree
	 */
	public static <N, V> ObservableCollection<List<V>> valuePathsOf(ObservableTree<N, V> tree,
		Function<? super V, ? extends N> nodeCreator, boolean onlyTerminal) {
		return new ValuePathCollection<>(tree, nodeCreator, onlyTerminal);
	}

	/**
	 * Implements {@link ObservableTree#of(ObservableValue, TypeToken, Function, Function)}
	 *
	 * @param <N> The type of node
	 * @param <V> The type of value
	 */
	public static class ComposedTree<N, V> implements ObservableTree<N, V> {
		private final ObservableValue<N> theRoot;
		private final TypeToken<V> theValueType;
		private final Function<? super N, ? extends ObservableValue<? extends V>> theValueGetter;
		private final Function<? super N, ? extends ObservableCollection<? extends N>> theChildrenGetter;

		/**
		 * @param root The root for the tree
		 * @param valueType The value type of the tree
		 * @param valueGetter The node-value getter for the tree
		 * @param childrenGetter The children getter for the tree
		 */
		public ComposedTree(ObservableValue<N> root, TypeToken<V> valueType,
			Function<? super N, ? extends ObservableValue<? extends V>> valueGetter,
				Function<? super N, ? extends ObservableCollection<? extends N>> childrenGetter) {
			theRoot = root;
			theValueType = valueType;
			theValueGetter = valueGetter;
			theChildrenGetter = childrenGetter;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE; // Can't think of a good mechanism to do locking on this kind of tree
		}

		@Override
		public ObservableValue<N> getRoot() {
			return theRoot;
		}

		@Override
		public TypeToken<V> getValueType() {
			return theValueType;
		}

		@Override
		public ObservableValue<? extends V> getValue(N node) {
			return theValueGetter.apply(node);
		}

		@Override
		public ObservableCollection<? extends N> getChildren(N node) {
			return theChildrenGetter.apply(node);
		}
	}

	/**
	 * Implements {@link ObservableTree#valuePathsOf(ObservableTree, Function, boolean)}
	 *
	 * @param <N> The node type of the tree
	 * @param <V> The value type of the tree
	 */
	public static class ValuePathCollection<N, V> implements ObservableCollection<List<V>> {
		private final ObservableTree<N, V> theTree;
		private final Function<? super V, ? extends N> theNodeCreator;
		private final boolean isOnlyTerminal;

		/**
		 * @param tree The tree to get the value paths of
		 * @param nodeCreator Used to add values into a tree
		 * @param onlyTerminal Whether to include only terminal paths, or also to include intermediate paths (paths ending in a node that
		 *        also has children not in the path)
		 */
		protected ValuePathCollection(ObservableTree<N, V> tree, Function<? super V, ? extends N> nodeCreator, boolean onlyTerminal) {
			theTree = tree;
			theNodeCreator = nodeCreator;
			isOnlyTerminal = onlyTerminal;
		}

		@Override
		public TypeToken<List<V>> getType() {
			return new TypeToken<List<V>>(){}.where(new TypeParameter<V>(){}, theTree.getValueType());
		}

		@Override
		public int size() {
			return size(theTree.getRoot().get());
		}

		private int size(N node) {
			ObservableCollection<? extends N> children = theTree.getChildren(node);
			if (isOnlyTerminal && children.isEmpty())
				return 1; // The node itself is the terminus of one path
			int size = isOnlyTerminal ? 0 : 1; // If we're not just terminal paths, then this node is the end of a path too
			for (N child : children)
				size += size(child);
			return size;
		}

		@Override
		public Iterator<List<V>> iterator() {
			class PathElement {
				@SuppressWarnings("unused")
				final N node;
				final V value;
				final Iterator<? extends N> children;
				boolean needsToBeReturned;
				boolean hasBeenReturned;

				PathElement(N node) {
					this.node = node;
					value = theTree.getValue(node).get();
					children = theTree.getChildren(node).iterator();
					needsToBeReturned=!isOnlyTerminal || !children.hasNext();
				}
			}
			return new Iterator<List<V>>(){
				private final LinkedList<PathElement> thePath=new LinkedList<>();

				{
					thePath.add(new PathElement(theTree.getRoot().get()));
				}

				@Override
				public boolean hasNext(){
					while(!thePath.isEmpty()){
						PathElement last=thePath.getLast();
						if(last.needsToBeReturned && !last.hasBeenReturned)
							return true;
						else if(last.children.hasNext())
							thePath.add(new PathElement(last.children.next()));
						else
							thePath.removeLast();
					}
					return false;
				}

				@Override
				public List<V> next(){
					if(!hasNext())
						throw new java.util.NoSuchElementException();
					PathElement last=thePath.getLast();
					last.hasBeenReturned=true;
					ArrayList<V> valuePath=new ArrayList<>(thePath.size());
					for(PathElement el : thePath)
						valuePath.add(el.value);
					return Collections.unmodifiableList(valuePath);
				}

				@Override
				public void remove(){
					if(thePath.isEmpty())
						throw new IllegalStateException("remove() must be called immediately after next()");
					PathElement last=thePath.getLast();
					if(!last.hasBeenReturned)
						throw new IllegalStateException("remove() must be called immediately after next()");
					if(thePath.size()==1)
						throw new IllegalStateException("Cannot remove the root");
					Iterator<PathElement> pathIter = thePath.descendingIterator();
					pathIter.next();
					pathIter.remove();
					last = pathIter.next();
					last.children.remove();
				}
			};
		}

		@Override
		public String canAdd(List<V> path) {
			return testAdd(path, false);
		}

		@Override
		public boolean add(List<V> e) {
			return testAdd(e, true);
		}

		@Override
		public boolean addAll(Collection<? extends List<V>> c) {
			boolean changed = false;
			for (List<V> path : c)
				changed |= testAdd(path, true);
			return changed;
		}

		private boolean testAdd(List<V> path, boolean reallyAdd) {
			if (path.isEmpty())
				return false;

			N node = theTree.getRoot().get();
			ObservableCollection<? extends N> children = null;
			V pathValue = null;
			int i;
			for (i = 0; i < path.size() - 1; i++) {
				pathValue = path.get(i);
				children = theTree.getChildren(node);
				for (N child : children) {
					V childValue = theTree.getValue(child).get();
					if (Objects.equals(childValue, pathValue)) {
						node = child;
						break;
					}
				}
			}
			if (i < path.size() - 1)
				return false; // No such path prefix found
			N newNode = theNodeCreator.apply(pathValue);
			if (newNode == null)
				return false;
			// We'll assume the node creator creates nodes of the right sub-type for the collection
			if (reallyAdd)
				return ((ObservableIndexedCollection<N>) children).add(newNode);
			else
				return ((ObservableIndexedCollection<N>) children).canAdd(newNode);
		}

		@Override
		public String canRemove(Object value) {
			return testRemove(value, false);
		}

		@Override
		public boolean remove(Object o) {
			return testRemove(o, true);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean changed = false;
			for (Object o : c)
				changed |= testRemove(o, true);
			return changed;
		}

		private boolean testRemove(Object value, boolean reallyRemove) {
			if (!(value instanceof List))
				return false;
			List<V> path = (List<V>) value;
			if (path.isEmpty())
				return false;

			N node = theTree.getRoot().get();
			ObservableCollection<? extends N> children = null;
			V pathValue = null;
			int i;
			for (i = 0; i < path.size(); i++) {
				pathValue = path.get(i);
				children = theTree.getChildren(node);
				for (N child : children) {
					V childValue = theTree.getValue(child).get();
					if (Objects.equals(childValue, pathValue)) {
						node = child;
						break;
					}
				}
			}
			if (i < path.size())
				return false; // No such path found
			if (isOnlyTerminal && !theTree.getChildren(node).isEmpty())
				return false; // The node is not terminal
			if (reallyRemove)
				return children.remove(pathValue);
			else
				return children.canRemove(pathValue);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return doRetainAll(theTree.getRoot().get(), new LinkedList<>(), null, c);
		}

		private boolean doRetainAll(N node, LinkedList<V> valuePath, Iterator<? extends N> parentChildren, Collection<?> c) {
			valuePath.add(theTree.getValue(node).get());
			if (parentChildren != null && !isOnlyTerminal && !c.contains(Collections.unmodifiableList(valuePath))) {
				parentChildren.remove();
				return true;
			}
			Iterator<? extends N> children = theTree.getChildren(node).iterator();
			if (!children.hasNext()) {
				if (isOnlyTerminal && parentChildren != null && !c.contains(Collections.unmodifiableList(valuePath))) {
					parentChildren.remove();
					return true;
				}
				return false;
			}
			do {
				N child = children.next();
				return doRetainAll(child, valuePath, children, c);
			} while (children.hasNext());
		}

		@Override
		public void clear() {
			theTree.getChildren(theTree.getRoot().get()).clear();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theTree.lock(write, cause);
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<List<V>>> onElement) {
			class PathNode implements ObservableElement<List<V>> {
				private final List<ObservableValue<? extends V>> theValues;

				PathNode(List<ObservableValue<? extends V>> values) {
					theValues = values;
				}

				@Override
				public ObservableValue<List<V>> persistent() {
					return this; // No idea how to make this persistent
				}

				@Override
				public TypeToken<List<V>> getType() {
					return ValuePathCollection.this.getType();
				}

				@Override
				public List<V> get() {
					List<V> pathValues = new ArrayList<>(theValues.size());
					for (ObservableValue<? extends V> value : theValues)
						pathValues.add(value.get());
					return Collections.unmodifiableList(pathValues);
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<List<V>>> observer) {
					Subscription[] subs = new Subscription[theValues.size()];
					boolean[] initialized = new boolean[1];
					for (int i = 0; i < subs.length; i++) {
						int pathIndex = i;
						subs[i] = theValues.get(i).subscribe(new Observer<ObservableValueEvent<? extends V>>() {
							@Override
							public <E extends ObservableValueEvent<? extends V>> void onNext(E valueEvent) {
								if (!initialized[0])
									return;
								List<V> oldValue = new ArrayList<>(theValues.size());
								List<V> newValue = new ArrayList<>(theValues.size());
								for (int j = 0; j < oldValue.size(); j++) {
									if (j == pathIndex) {
										oldValue.add(valueEvent.getOldValue());
										newValue.add(valueEvent.getValue());
									} else {
										V value_i = theValues.get(j).get();
										oldValue.add(value_i);
										newValue.add(value_i);
									}
								}
								Observer.onNextAndFinish(observer, createChangeEvent(Collections.unmodifiableList(oldValue),
									Collections.unmodifiableList(newValue), valueEvent));
							}

							@Override
							public <E extends ObservableValueEvent<? extends V>> void onCompleted(E valueEvent) {
								List<V> value = new ArrayList<>(theValues.size());
								for (int j = 0; j < theValues.size(); j++) {
									if (j == pathIndex) {
										value.add(valueEvent.getOldValue());
									} else {
										V value_i = theValues.get(j).get();
										value.add(value_i);
									}
								}
								value = Collections.unmodifiableList(value);
								Observer.onCompletedAndFinish(observer, createChangeEvent(value, value, valueEvent));
								Subscription.forAll(subs).unsubscribe();
							}
						});
					}
					{
						List<V> initValue = new ArrayList<>(theValues.size());
						for (ObservableValue<? extends V> valueObs : theValues)
							initValue.add(valueObs.get());
						Observer.onNextAndFinish(observer, createInitialEvent(Collections.unmodifiableList(initValue), null));
					}
					return Subscription.forAll(subs);
				}

				@Override
				public boolean isSafe() {
					for (ObservableValue<? extends V> value : theValues)
						if (!value.isSafe())
							return false;
					return true;
				}
			}
			class NodeObserver implements Observer<ObservableValueEvent<? extends N>> {
				private final ObservableValue<? extends N> theNodeObservable;
				private final List<ObservableValue<? extends V>> thePathValues;

				NodeObserver(ObservableValue<? extends N> nodeObservable, List<ObservableValue<? extends V>> parentValues) {
					theNodeObservable = nodeObservable;
					List<ObservableValue<? extends V>> pathValues = new ArrayList<>(parentValues.size() + 1);
					pathValues.addAll(parentValues);
					ObservableValue<? extends V> nodeValue = ObservableValue
						.flatten(theNodeObservable.mapV(node -> theTree.getValue(node)));
					pathValues.add(nodeValue);
					thePathValues = pathValues;
				}

				@Override
				public <E extends ObservableValueEvent<? extends N>> void onNext(E nodeEvent) {
					ObservableCollection<? extends N> children = theTree.getChildren(nodeEvent.getValue());
					if (!isOnlyTerminal) {
						// The easy case
						onElement.accept(new PathNode(thePathValues));
						children.takeUntil(theNodeObservable.noInit()).onElement(el -> {
							el.subscribe(new NodeObserver(el, thePathValues));
						});
					} else {
						AtomicInteger size = new AtomicInteger();
						children.takeUntil(theNodeObservable.noInit()).onElement(el -> {
							el.subscribe(new NodeObserver(el, thePathValues));
							if (size.decrementAndGet() == 0)
								publishNodePath(children);
						});
						if (size.get() == 0)
							publishNodePath(children);
					}
				}

				private void publishNodePath(ObservableCollection<? extends N> children) {
					List<ObservableValue<? extends V>> values = new ArrayList<>(thePathValues.size());
					values.addAll(thePathValues);
					values.set(values.size() - 1, thePathValues.get(thePathValues.size() - 1).takeUntil(children.observeSize().noInit()));
					onElement.accept(new PathNode(values));
				}
			}
			ObservableValue<N> root = theTree.getRoot();
			return root.subscribe(new NodeObserver(root, Collections.emptyList()));
		}

		@Override
		public int hashCode() {
			return ObservableCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return ObservableCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return ObservableCollection.toString(this);
		}
	}
}
