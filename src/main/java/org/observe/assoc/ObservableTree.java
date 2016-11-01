package org.observe.assoc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableOrderedElement;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public interface ObservableTree<N, V> {
	ObservableValue<N> getRoot();

	TypeToken<V> getValueType();

	ObservableValue<? extends V> getValue(N node);

	ObservableOrderedCollection<? extends N> getChildren(N node);

	boolean isSafe();

	ObservableValue<CollectionSession> getSession();

	public static <N, V> ObservableTree<N, V> of(ObservableValue<N> root, TypeToken<V> valueType,
		Function<? super N, ? extends ObservableValue<? extends V>> getValue,
			Function<? super N, ? extends ObservableOrderedCollection<? extends N>> getChildren) {
		return new ComposedTree<>(root, valueType, getValue, getChildren);
	}

	public static <N, V> ObservableOrderedCollection<List<V>> valuePathsOf(ObservableTree<N, V> tree, boolean onlyTerminal) {
		return valuePathsOf(tree, null, onlyTerminal);
	}

	public static <N, V> ObservableOrderedCollection<List<V>> valuePathsOf(ObservableTree<N, V> tree, Function<V, N> nodeCreator,
		boolean onlyTerminal) {
		return new ValuePathCollection<>(tree, nodeCreator, onlyTerminal);
	}

	public static class ComposedTree<N, V> implements ObservableTree<N, V> {
		private final ObservableValue<N> theRoot;
		private final TypeToken<V> theValueType;
		private final Function<? super N, ? extends ObservableValue<? extends V>> theValueGetter;
		private final Function<? super N, ? extends ObservableOrderedCollection<? extends N>> theChildrenGetter;

		protected ComposedTree(ObservableValue<N> root, TypeToken<V> valueType,
			Function<? super N, ? extends ObservableValue<? extends V>> valueGetter,
				Function<? super N, ? extends ObservableOrderedCollection<? extends N>> childrenGetter) {
			theRoot = root;
			theValueType = valueType;
			theValueGetter = valueGetter;
			theChildrenGetter = childrenGetter;
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
		public ObservableOrderedCollection<? extends N> getChildren(N node) {
			return theChildrenGetter.apply(node);
		}
	}

	public static class ValuePathCollection<N, V>
	implements ObservableOrderedCollection<List<V>>, ObservableCollection.PartialCollectionImpl<List<V>> {
		private final ObservableTree<N, V> theTree;
		private final Function<? super V, ? extends N> theNodeCreator;
		private final boolean isOnlyTerminal;

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
		public ObservableValue<CollectionSession> getSession() {
			return theTree.getSession();
		}

		@Override
		public boolean isSafe() {
			return theTree.isSafe();
		}

		@Override
		public boolean canRemove(Object value) {
			if(!(value instanceof List))
				return false;
			List<V> path=(List<V>) value;
			if(path.isEmpty())
				return false;

			N node = theTree.getRoot().get();
			ObservableOrderedCollection<? extends N> children = null;
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
			return children.canRemove(pathValue);
		}

		@Override
		public boolean canAdd(List<V> path) {
			if (path.isEmpty())
				return false;

			N node = theTree.getRoot().get();
			ObservableOrderedCollection<? extends N> children = null;
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
			return ((ObservableOrderedCollection<N>) children).canAdd(newNode);
		}

		@Override
		public int size() {
			return size(theTree.getRoot().get());
		}

		private int size(N node) {
			ObservableOrderedCollection<? extends N> children = theTree.getChildren(node);
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
					thePath.descendingIterator()
				}
			};
		}

		@Override
		public boolean add(List<V> e) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean remove(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends List<V>> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void clear() {
			theTree.getChildren(theTree.getRoot().get()).clear();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<List<V>>> onElement) {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
