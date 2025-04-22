package org.observe.util.swing;

import java.awt.EventQueue;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.qommons.ArrayUtils;
import org.qommons.BreakpointHere;
import org.qommons.IdentityKey;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

/**
 * A swing tree model well suited to visualizing observable structures
 *
 * @param <T> The type of values in the tree
 */
public abstract class ObservableTreeModel<T> implements TreeModel {
	private final ObservableValue<? extends T> theRoot;
	private TreeNode theRootNode;

	private final Map<IdentityKey<T>, TreeNode[]> theNodes;
	private final List<TreeModelListener> theListeners;
	private CategoryRenderStrategy<BetterList<T>, ?> theRenderer;
	private ToIntFunction<TreePath> theRowModel;
	private Predicate<TreePath> theExpansionModel;
	private Predicate<TreePath> theSelectionModel;

	/** @param rootValue The root of the model */
	protected ObservableTreeModel(T rootValue) {
		this(ObservableValue.of(rootValue));
	}

	/** @param root The observable value for the root of the model */
	protected ObservableTreeModel(ObservableValue<? extends T> root) {
		theRoot = root;

		theNodes = new ConcurrentHashMap<>();
		theListeners = new ArrayList<>();

		theRootNode = new TreeNode(null, root.get());
		theNodes.put(new IdentityKey<>(theRootNode.get()), (TreeNode[]) new ObservableTreeModel<?>.TreeNode[] { theRootNode });

		root.noInitChanges().safe(ThreadConstraint.EDT).act(evt -> doRootChanged(evt.getNewValue()));
	}

	public CategoryRenderStrategy<BetterList<T>, ?> getRenderer() {
		return theRenderer;
	}

	public ObservableTreeModel<T> withRenderer(CategoryRenderStrategy<BetterList<T>, ?> renderer) {
		theRenderer = renderer;
		return this;
	}

	public ObservableTreeModel<T> withStatus(ToIntFunction<TreePath> rowModel, Predicate<TreePath> expansionModel,
		Predicate<TreePath> selectionModel) {
		theRowModel = rowModel;
		theExpansionModel = expansionModel;
		theSelectionModel = selectionModel;
		return this;
	}

	/** @return The observable value that is the root of this tree */
	public ObservableValue<? extends T> observeRoot() {
		return theRoot;
	}

	@Override
	public TreeNode getRoot() {
		return theRootNode;
	}

	/**
	 * @param value The value to get the tree node for
	 * @param searchDeeply Whether to search for values in this tree that may be in collapsed parents
	 * @return The tree node for the given value, or null if the value could not be found in the tree
	 */
	public TreeNode getNode(T value, boolean searchDeeply) {
		TreeNode[] found = theNodes.get(new IdentityKey<>(value));
		if (found != null)
			return found[0];
		else if (!searchDeeply || !ThreadConstraint.EDT.isEventThread()) // Can't do the search off the EDT
			return null;
		LinkedList<TreeNode> queue = new LinkedList<>();
		queue.add(theRootNode);
		while (!queue.isEmpty()) {
			TreeNode node = queue.poll();
			for (TreeNode child : node.initChildren().getChildNodes()) {
				if (child.get() == value)
					return child;
				queue.add(child);
			}
		}
		return null;
	}

	/**
	 * @param value The value to get the path for
	 * @param searchDeeply Whether to search for values in this tree that may be in collapsed parents
	 * @return The path to the given node, or null if the node could not be found
	 */
	public BetterList<TreeNode> getTreePath(T value, boolean searchDeeply) {
		TreeNode node = getNode(value, searchDeeply);
		return node == null ? null : node.getTreePath();
	}

	public BetterList<T> getValuePath(T value, boolean searchDeeply) {
		TreeNode node = getNode(value, searchDeeply);
		return node == null ? null : node.getValuePath();
	}

	/** Fires a change event on the root (but not the entire tree) */
	public void rootChanged() {
		rootChanged(theRoot.get());
	}

	private void rootChanged(T newRoot) {
		ObservableSwingUtils.onEQ(() -> doRootChanged(newRoot));
	}

	private void doRootChanged(T newRoot) {
		theRootNode.set(newRoot);
		TreeModelEvent event = new TreeModelEvent(this, new Object[] { theRootNode }, null, null);

		for (TreeModelListener listener : theListeners) {
			try {
				listener.treeNodesChanged(event);
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
		}

		theRootNode.changed();
	}

	@Override
	public TreeNode getChild(Object parent, int index) {
		return ((TreeNode) parent).getChildNode(index);
	}

	@Override
	public int getChildCount(Object parent) {
		return ((TreeNode) parent).getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		return ((TreeNode) parent).indexOfChild((TreeNode) child);
	}

	public boolean isPathEditable(TreePath path) {
		return _isPathEditable(path);
	}

	private <C> boolean _isPathEditable(TreePath path) {
		CategoryRenderStrategy<BetterList<T>, C> renderer = (CategoryRenderStrategy<BetterList<T>, C>) theRenderer;
		if (renderer == null)
			return false;
		TreeNode node = (TreeNode) path.getLastPathComponent();
		int rowIndex = theRowModel == null ? 0 : theRowModel.applyAsInt(path);
		boolean expanded = theExpansionModel != null && theExpansionModel.test(path);
		boolean selected = theSelectionModel != null && theSelectionModel.test(path);
		ModelRow<BetterList<T>> modelRow = new ModelRow.Default<>(node::getValuePath, rowIndex, false, selected, selected, expanded,
			isLeaf(node));
		C currentValue = renderer.getCategoryValue(modelRow);
		ModelCell<BetterList<T>, C> cell = new ModelCell.RowWrapper<>(modelRow, currentValue, 0, false, false);
		return renderer.getMutator().isEditable(cell);
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
		editValue(path, (TreeNode) path.getLastPathComponent(), newValue);
	}

	private <C> void editValue(TreePath path, TreeNode node, C newValue) {
		CategoryRenderStrategy<BetterList<T>, C> renderer = (CategoryRenderStrategy<BetterList<T>, C>) theRenderer;
		if (renderer == null)
			return;
		int rowIndex = theRowModel == null ? 0 : theRowModel.applyAsInt(path);
		boolean expanded = theExpansionModel != null && theExpansionModel.test(path);
		boolean selected = theSelectionModel != null && theSelectionModel.test(path);
		ModelRow<BetterList<T>> modelRow = new ModelRow.Default<>(node::getValuePath, rowIndex, false, selected, selected, expanded,
			isLeaf(node));
		C currentValue = renderer.getCategoryValue(modelRow);
		ModelCell<BetterList<T>, C> cell = new ModelCell.RowWrapper<>(modelRow, currentValue, 0, false, false);
		if (!renderer.getMutator().isEditable(cell))
			return;
		// This is problematic, as it was designed for tables, but it's what I've got to work with
		MutableCollectionElement<BetterList<T>> element = node.getSyntheticMutableElement();
		renderer.getMutator().mutate(element, newValue);
	}

	/**
	 * @param child The child to get the mutable element for
	 * @return The mutable collection element of the given child in its parent's children collection
	 */
	public MutableCollectionElement<T> getElementOfChild(T child) {
		TreeNode childNode = getNode(child, false);
		if (childNode == null)
			return null;
		ElementId element = childNode.getParent().getChildren().getElement(//
			childNode.getParent().getChildNodes().indexOf(childNode)).getElementId();
		return (MutableCollectionElement<T>) childNode.getParent().getChildren().mutableElement(element);
	}

	public TreePath getTreePath(List<? extends T> valuePath, Equivalence<? super T> equivalence) {
		TreeNode found = null;
		List<TreeNode> nodes = Collections.singletonList(theRootNode);
		Object[] path = new Object[valuePath.size()];
		int pathIndex = 0;
		for (T value : valuePath) {
			found = null;
			for (TreeNode node : nodes) {
				if (equivalence.elementEquals(node.get(), value)) {
					found = node;
					break;
				}
			}
			if (found == null)
				return null;
			path[pathIndex++] = found;
			nodes = found.getChildNodes();
		}
		return new TreePath(path);
	}

	@Override
	public void addTreeModelListener(TreeModelListener l) {
		theListeners.add(l);
	}

	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		theListeners.remove(l);
	}

	/**
	 * @param parentPath The path from the root to the parent node to get the children of
	 * @param nodeUntil An observable that will fire when the node is removed from the tree or its value changes such that the returned
	 *        collection is no longer needed
	 * @return An observable collection representing the parent's children
	 */
	protected abstract ObservableCollection<? extends T> getChildren(BetterList<T> parentPath, Observable<?> nodeUntil);

	/** Releases this model's observers placed on child collections */
	public void dispose() {
		ObservableSwingUtils.onEQ(() -> {
			TreeNode node = getNode(theRoot.get(), false);
			node.dispose();
			theNodes.clear();
			theListeners.clear();
		});
	}

	/** A tree node for a value in an {@link ObservableTreeModel} */
	public class TreeNode {
		private final TreeNode theParent;
		private T theValue;
		private final int theDepth;
		private final List<TreeNode> theChildNodes;
		private ObservableCollection<? extends T> theUnsafeChildren;
		private ObservableCollection<? extends T> theChildren;
		private SimpleObservable<Void> unsubscribe;
		private boolean areChildrenInitialized;

		TreeNode(TreeNode parent, T value) {
			theParent = parent;
			theValue = value;
			theDepth = parent == null ? 0 : parent.getDepth() + 1;
			theChildNodes = new ArrayList<>();
		}

		/** @return The parent of this tree node */
		public TreeNode getParent() {
			return theParent;
		}

		/** @return This tree node's current value */
		public T get() {
			return theValue;
		}

		public ModelRow<BetterList<T>> getModelRow() {
			if (theRenderer == null)
				return new ModelRow.Bare<>(getValuePath());
			TreePath path = getPath();
			int rowIndex = theRowModel == null ? 0 : theRowModel.applyAsInt(path);
			boolean expanded = theExpansionModel != null && theExpansionModel.test(path);
			boolean selected = theSelectionModel != null && theSelectionModel.test(path);
			return new ModelRow.Default<>(this::getValuePath, rowIndex, false, selected, selected, expanded, isLeaf(this));
		}

		public Object getRenderValue() {
			if (theRenderer == null)
				return theValue;
			return theRenderer.getCategoryValue(getModelRow());
		}

		<C> void editValue(C value) {

		}

		void set(T newValue) {
			if (theValue != newValue) {
				removeNode();
				theValue = newValue;
				addNode();
			}
		}

		/** @return The depth of this tree node--the number of ancestors it has */
		public int getDepth() {
			return theDepth;
		}

		public BetterList<TreeNode> getTreePath() {
			if (theParent == null)
				return BetterList.of(this);
			List<TreeNode> path = new ArrayList<>(theDepth + 1);
			TreeNode node = this;
			while (node != null) {
				path.add(node);
				node = node.theParent;
			}
			Collections.reverse(path);
			return BetterList.of(path);
		}

		/** @return The collection of child values for this node's value */
		public ObservableCollection<? extends T> getChildren() {
			return theChildren;
		}

		/** @return The collection of child values for this node's value--the actual collection returned by the model */
		public ObservableCollection<? extends T> getUnsafeChildren() {
			return theUnsafeChildren;
		}

		public MutableCollectionElement<BetterList<T>> getSyntheticMutableElement() {
			BetterList<T> valuePath = getValuePath();
			MutableCollectionElement<BetterList<T>> element;
			if (getParent() == null) {
				element = new MutableCollectionElement<BetterList<T>>() {
					@Override
					public ElementId getElementId() {
						return null;
					}

					@Override
					public BetterList<T> get() {
						return valuePath;
					}

					@Override
					public BetterCollection<BetterList<T>> getCollection() {
						return BetterList.<BetterList<T>> of(valuePath);
					}

					@Override
					public String isEnabled() {
						return theRoot instanceof SettableValue ? ((SettableValue<T>) theRoot).isEnabled().get()
							: StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public String isAcceptable(BetterList<T> value) {
						return theRoot instanceof SettableValue ? ((SettableValue<T>) theRoot).isAcceptable(value.getLast())
							: StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void set(BetterList<T> value) throws UnsupportedOperationException, IllegalArgumentException {
						if (theRoot instanceof SettableValue)
							((SettableValue<T>) theRoot).set(value.getLast());
						else
							throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public String canRemove() {
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void remove() throws UnsupportedOperationException {
						throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
					}
				};
			} else {
				CollectionElement<? extends T> el = getParent().getChildren().getElement(getParent().getChildNodes().indexOf(this));
				MutableCollectionElement<? extends T> backing = getParent().getChildren().mutableElement(el.getElementId());
				element = new MutableCollectionElement<BetterList<T>>() {
					@Override
					public ElementId getElementId() {
						return backing.getElementId();
					}

					@Override
					public BetterList<T> get() {
						return valuePath;
					}

					@Override
					public BetterCollection<BetterList<T>> getCollection() {
						return (BetterCollection<BetterList<T>>) backing.getCollection();
					}

					@Override
					public String isEnabled() {
						return backing.isEnabled();
					}

					@Override
					public String isAcceptable(BetterList<T> value) {
						return ((MutableCollectionElement<T>) backing).isAcceptable(value.getLast());
					}

					@Override
					public void set(BetterList<T> value) throws UnsupportedOperationException, IllegalArgumentException {
						((MutableCollectionElement<T>) backing).set(value.getLast());
					}

					@Override
					public String canRemove() {
						return backing.canRemove();
					}

					@Override
					public void remove() throws UnsupportedOperationException {
						backing.remove();
					}
				};
			}
			return element;
		}

		/** @return The current list of child nodes under this node in the tree */
		public List<TreeNode> getChildNodes() {
			return Collections.unmodifiableList(theChildNodes);
		}

		private TreeNode initChildren() {
			if (areChildrenInitialized) {
				return this;
			}
			areChildrenInitialized = true;

			unsubscribe = SimpleObservable.build().withThreadConstraint(ThreadConstraint.EDT).build();
			theUnsafeChildren = ObservableTreeModel.this.getChildren(getValuePath(), unsubscribe.readOnly());
			theChildren = theUnsafeChildren == null ? null : theUnsafeChildren.safe(ThreadConstraint.EDT, unsubscribe);
			init(false);
			return this;
		}

		void init(boolean withEvent) {
			if (theChildren == null)
				return;
			try (Transaction t = Transactable.lock(theChildren, false, null)) {
				for (T value : theChildren)
					theChildNodes.add(newChild(value));

				if (withEvent) {
					int[] indexes = new int[theChildNodes.size()];
					Object[] values = new Object[indexes.length];
					for (int i = 0; i < indexes.length; i++) {
						indexes[i] = i;
						values[i] = theChildNodes.get(i).get();
					}
					if (indexes.length == 0)
						indexes = null;
					TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);
					for (TreeModelListener listener : theListeners) {
						try {
							listener.treeNodesInserted(event);
						} catch (RuntimeException e) {
							e.printStackTrace();
						}
					}
				}

				boolean[] unsubscribed = new boolean[1];
				unsubscribe.take(1).act(__ -> unsubscribed[0] = true);
				theChildren.changes().takeUntil(unsubscribe).act(event -> { // theChildren is already safe
					if (unsubscribed[0])
						return;
					int[] indexes = event.getIndexes();
					switch (event.type) {
					case add:
						added(indexes, event.getValues());
						break;
					case remove:
						removed(indexes);
						break;
					case set:
						changed(indexes, event.getValues());
						break;
					}
				});
			}
		}

		private TreeNode newChild(T value) {
			TreeNode ret = new TreeNode(this, value);
			ret.addNode();
			return ret;
		}

		int getChildCount() {
			if (!areChildrenInitialized)
				initChildren();
			return theChildNodes.size();
		}

		public TreeNode getChildNode(int index) {
			if (!areChildrenInitialized)
				initChildren();
			return theChildNodes.get(index);
		}

		Object getChild(int index) {
			if (!areChildrenInitialized)
				initChildren();
			return theChildNodes.get(index).theValue;
		}

		int indexOfChild(TreeNode child) {
			if (!areChildrenInitialized)
				initChildren();
			for (int i = 0; i < theChildNodes.size(); i++) {
				if (theChildNodes.get(i) == child) {
					return i;
				}
			}
			return -1;
		}

		private void dispose() {
			removeNode();
			if (unsubscribe != null)
				unsubscribe.onNext(null);
			for (TreeNode child : theChildNodes)
				child.dispose();
		}

		private void addNode() {
			theNodes.compute(new IdentityKey<>(theValue), (__, nodes) -> {
				if (nodes == null)
					return (TreeNode[]) new ObservableTreeModel<?>.TreeNode[] { this };
					else
						return ArrayUtils.add(nodes, this, 0);
			});
		}

		private void removeNode() {
			theNodes.compute(new IdentityKey<>(theValue), (__, nodes) -> {
				if (nodes == null || nodes.length == 1)
					return null;
				else
					return ArrayUtils.remove(nodes, this);
			});
		}

		public TreePath getPath() {
			ArrayList<TreeNode> path = new ArrayList<>(theDepth + 1);
			TreeNode node = this;
			do {
				path.add(node);
				node = node.theParent;
			} while (node != null);
			Collections.reverse(path);
			return new TreePath(path.toArray());
		}

		public BetterList<T> getValuePath() {
			Object[] pathArray = new Object[theDepth + 1];
			TreeNode node = this;
			for (int i = theDepth; node != null; i--) {
				pathArray[i] = node.get();
				node = node.theParent;
			}
			return (BetterList<T>) (BetterList<?>) BetterList.of(pathArray);
		}

		private void changed() {
			if (!areChildrenInitialized)
				return;
			ObservableCollection<? extends T> children = ObservableTreeModel.this.getChildren(getValuePath(), unsubscribe.readOnly());
			if (theUnsafeChildren == children
				|| (children != null && theUnsafeChildren != null && children.getIdentity().equals(theUnsafeChildren.getIdentity())))
				return;
			try (Transaction t = Transactable.lock(theChildren, false, null); //
				Transaction t2 = Transactable.lock(children, false, null)) {
				unsubscribe.onNext(null);
				if (theChildren != null) {
					int[] indexes = new int[theChildNodes.size()];
					TreeNode[] nodes = new ObservableTreeModel.TreeNode[indexes.length];
					for (int i = 0; i < indexes.length; i++) {
						indexes[i] = i;
						nodes[i] = theChildNodes.get(i);
					}
					if (indexes.length == 0)
						indexes = null;
					TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, nodes);
					for (TreeModelListener listener : theListeners) {
						try {
							listener.treeNodesRemoved(event);
						} catch (RuntimeException e) {
							e.printStackTrace();
						}
					}

					for (TreeNode child : theChildNodes)
						child.dispose();
					theChildNodes.clear();
				}
				theUnsafeChildren = children;
				theChildren = theUnsafeChildren == null ? null : theUnsafeChildren.safe(ThreadConstraint.EDT, unsubscribe);
				init(true);
			}
		}

		private void added(int[] indexes, List<? extends T> values) {
			TreeNode[] newNodes = new ObservableTreeModel.TreeNode[values.size()];
			int i = 0;
			for (T value : values) {
				TreeNode newNode = newChild(value);
				newNodes[i++] = newNode;
			}
			// Swing expects indexes to be in ascending order, plus we need them in order so we can add in the right places.
			sort(indexes, newNodes);
			for (i = 0; i < indexes.length; i++)
				theChildNodes.add(indexes[i], newNodes[i]);

			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, newNodes);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesInserted(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}

		private void removed(int[] indexes) {
			TreeNode[] nodes = new ObservableTreeModel.TreeNode[indexes.length];
			// Swing expects indexes to be in ascending order.
			// Also, the indexes being sorted means we can pull them off the list correctly.
			sort(indexes, nodes);
			for (int i = indexes.length - 1; i >= 0; i--) {
				nodes[i] = theChildNodes.remove(indexes[i]);
				nodes[i].dispose();
			}

			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, nodes);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesRemoved(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}

		private void changed(int[] indexes, List<? extends T> values) {
			TreeNode[] nodes = new ObservableTreeModel.TreeNode[indexes.length];
			int i = 0;
			for (T value : values) {
				TreeNode node = theChildNodes.get(indexes[i]);
				node.theValue = value;
				nodes[i] = node;
				i++;
			}
			// Swing expects indexes to be in ascending order
			sort(indexes, nodes);
			if (indexes.length == 0)
				indexes = null;
			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, nodes);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesChanged(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}

			for (TreeNode node : nodes)
				node.changed();
		}

		private void sort(int[] indexes, TreeNode[] nodes) {
			Integer[] indexList = new Integer[indexes.length];
			for (int i = 0; i < indexes.length; i++)
				indexList[i] = indexes[i];
			ArrayUtils.sort(indexList, new ArrayUtils.SortListener<Integer>() {
				@Override
				public int compare(Integer o1, Integer o2) {
					return o1.compareTo(o2);
				}

				@Override
				public void swapped(Integer o1, int idx1, Integer o2, int idx2) {
					int tempIdx = indexes[idx1];
					indexes[idx1] = indexes[idx2];
					indexes[idx2] = tempIdx;
					TreeNode tempVal = nodes[idx1];
					nodes[idx1] = nodes[idx2];
					nodes[idx2] = tempVal;
				}
			});
		}

		@Override
		public String toString() {
			if (theParent != null)
				return theParent + "/" + theValue;
			else
				return String.valueOf(theValue);
		}
	}

	/**
	 * @param <T> The type of values in the tree model
	 * @param treePath The tree path of a value in the tree
	 * @return A BetterList containing the same data as the given tree path
	 */
	public static <T> BetterList<T> betterPath(TreePath treePath) {
		if (treePath == null)
			return null;
		return ((ObservableTreeModel<T>.TreeNode) treePath.getLastPathComponent()).getValuePath();
	}

	/**
	 * Synchronizes a tree's selection model with a SettableValue whose value is a tree path (BetterList) of items in the tree
	 *
	 * @param tree The tree whose selection to synchronize
	 * @param selection The selected path value
	 * @param singularOnly Whether, when multiple items are selected in the tree, the selected value should be set to null (as opposed to
	 *        the lead value)
	 * @param equivalence The equivalence to use for the tree
	 * @param until An observable that, when fired, will release all resources and undo all subscriptions made by this method
	 */
	public static <T> void syncSelection(JTree tree, SettableValue<BetterList<T>> selection, boolean singularOnly,
		Equivalence<? super T> equivalence, Observable<?> until) {
		ObservableTreeModel<T> model = (ObservableTreeModel<T>) tree.getModel();
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel[] selectionModel = new TreeSelectionModel[] { tree.getSelectionModel() };
		TreeSelectionListener selListener = e -> {
			if (callbackLock[0])
				return;
			ObservableSwingUtils.flushEQCache();
			TreeSelectionModel selModel = selectionModel[0];
			TreePath path;
			if (selModel.isSelectionEmpty())
				path = null;
			else if (singularOnly && selModel.getSelectionCount() > 1)
				path = null;
			else
				path = selModel.getLeadSelectionPath();
			callbackLock[0] = true;
			try {
				if (path != null) {
					BetterList<T> list = betterPath(path);
					if (selection.isAcceptable(list) == null)
						selection.set(list, e);
				} else if (selection.get() != null) {
					if (selection.isAcceptable(null) == null)
						selection.set(null, e);
				}
			} finally {
				callbackLock[0] = false;
			}
		};
		TreeModelListener modelListener = new TreeModelListener() {
			@Override
			public void treeNodesInserted(TreeModelEvent e) {
			}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {
			}

			@Override
			public void treeNodesChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				ObservableSwingUtils.flushEQCache();
				int parentRow = tree.getRowForPath(e.getTreePath());
				if (parentRow < 0 || !tree.isExpanded(parentRow))
					return;
				TreeSelectionModel selModel = selectionModel[0];
				if (selModel.isSelectionEmpty())
					return;
				else if (singularOnly && selModel.getSelectionCount() > 1)
					return;
				TreePath selPath = selModel.getSelectionPath();
				int selectionDepth = selPath.getPathCount();
				if (!e.getTreePath().isDescendant(selPath) || selectionDepth == e.getTreePath().getPathCount())
					return;
				ObservableTreeModel<T>.TreeNode selNode = (ObservableTreeModel<T>.TreeNode) selPath
					.getPathComponent(e.getTreePath().getPathCount());
				int found = -1;
				for (int c = 0; c < e.getChildren().length; c++) {
					if (e.getChildren()[c] == selNode) {
						found = c;
						break;
					}
				}
				if (found < 0) {// Changed path is not relevant to the selection
					return;
				} // else Selected path has been affected. Fire an update with fresh values.
				callbackLock[0] = true;
				try {
					List<T> list = new ArrayList<>(selectionDepth);
					for (Object node : e.getPath())
						list.add(((ObservableTreeModel<T>.TreeNode) node).get());
					list.add(((ObservableTreeModel<T>.TreeNode) e.getChildren()[found]).get());
					while (list.size() < selectionDepth)
						list.add(((ObservableTreeModel<T>.TreeNode) selPath.getPathComponent(list.size())).get());
					BetterList<T> newSelection = BetterList.of(list);
					if (selection.isAcceptable(newSelection) == null)
						selection.set(newSelection, e);
				} finally {
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				BreakpointHere.breakpoint();
				TreeSelectionModel selModel = selectionModel[0];
				List<T> list;
				if (selModel.isSelectionEmpty())
					list = null;
				else if (singularOnly && selModel.getSelectionCount() > 1)
					list = null;
				else {
					TreePath path = tree.getPathForRow(selModel.getLeadSelectionRow());
					list = new ArrayList<>(path.getPathCount());
					for (Object node : path.getPath())
						list.add(((ObservableTreeModel<T>.TreeNode) node).get());
				}
				BetterList<T> betterList = list == null ? null : BetterList.of(list);
				if (!Objects.equals(list, selection.get()) && selection.isAcceptable(betterList) == null) {
					callbackLock[0] = true;
					try {
						selection.set(betterList, e);
					} finally {
						callbackLock[0] = false;
					}
				}
			}
		};
		PropertyChangeListener selModelListener = evt -> {
			((TreeSelectionModel) evt.getOldValue()).removeTreeSelectionListener(selListener);
			((TreeSelectionModel) evt.getNewValue()).addTreeSelectionListener(selListener);
		};
		selectionModel[0].addTreeSelectionListener(selListener);
		tree.addPropertyChangeListener("selectionModel", selModelListener);
		model.addTreeModelListener(modelListener);
		selection.changes().takeUntil(until).safe(ThreadConstraint.EDT).act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				TreeSelectionModel selModel = selectionModel[0];
				if (evt.getNewValue() == null) {
					selModel.clearSelection();
				} else if (evt.getOldValue() == evt.getNewValue() && !selModel.isSelectionEmpty()//
					&& (selModel.getSelectionCount() == 1 || !singularOnly)//
					&& isSamePath(evt.getNewValue(), selModel.getLeadSelectionPath(), equivalence)) {
					if (selModel.getLeadSelectionRow() == 0)
						model.rootChanged();
					else {
						TreePath parentPath = selModel.getLeadSelectionPath().getParentPath();
						int parentRow = tree.getRowForPath(parentPath);
						int childIdx = tree.getRowForPath(selModel.getLeadSelectionPath()) - parentRow - 1;
						ObservableCollection<? extends T> children = ((ObservableTreeModel<T>.TreeNode) parentPath.getLastPathComponent())
							.getChildren();
						MutableCollectionElement<T> el = (MutableCollectionElement<T>) children
							.mutableElement(children.getElement(childIdx).getElementId());
						if (el.isAcceptable(evt.getNewValue().getLast()) == null) {
							try (Transaction t = children.lock(true, evt)) {
								el.set(evt.getNewValue().getLast());
							}
						}
					}
				} else {
					Runnable select = new Runnable() {
						TreePath path;
						int tries = 0;

						@Override
						public void run() {
							if (callbackLock[0])
								return;
							callbackLock[0] = true;
							try {
								if (path == null)
									path = model.getTreePath(evt.getNewValue(), equivalence);
								if (path == null)
									EventQueue.invokeLater(this);
								else if (tree.isExpanded(path.getParentPath()))
									selModel.setSelectionPath(path);
								else if (++tries < path.getPathCount() + 5) {
									for (TreePath p = path.getParentPath(); p != null; p = p.getParentPath()) {
										if (!tree.isExpanded(p))
											tree.expandPath(p);
										break;
									}
									EventQueue.invokeLater(this);
								}
							} finally {
								callbackLock[0] = false;
							}
						}
					};
					EventQueue.invokeLater(select);
				}
			} finally {
				callbackLock[0] = false;
			}
		});

		until.take(1).act(__ -> {
			tree.removePropertyChangeListener("selectionModel", selModelListener);
			selectionModel[0].removeTreeSelectionListener(selListener);
			model.removeTreeModelListener(modelListener);
		});
	}

	/**
	 * Synchronizes selection between nodes in a tree and an observable collection of tree paths
	 *
	 * @param <T> The type of nodes in the tree
	 * @param tree The tree to synchronize selection for
	 * @param multiSelection The tree paths to synchronize the tree selection with
	 * @param until The observable to stop all listening
	 */
	public static <T> void syncSelection(JTree tree, ObservableCollection<BetterList<T>> multiSelection, Equivalence<? super T> equivalence,
		Observable<?> until) {
		// This method assumes multiSelection is already safe for the EDT

		// Tree selection->collection
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel selectionModel = tree.getSelectionModel();
		TreeSelectionListener selListener = e -> {
			if (callbackLock[0])
				return;
			ObservableSwingUtils.flushEQCache();
			callbackLock[0] = true;
			try (Transaction t = multiSelection.lock(true, e)) {
				CollectionUtils
				.synchronize(multiSelection, Arrays.asList(selectionModel.getSelectionPaths()),
					(better, treePath) -> isSamePath(better, treePath, equivalence))//
				.adjust(new CollectionUtils.CollectionSynchronizer<BetterList<T>, TreePath>() {
					@Override
					public boolean getOrder(ElementSyncInput<BetterList<T>, TreePath> element) {
						return true;
					}

					@Override
					public ElementSyncAction leftOnly(ElementSyncInput<BetterList<T>, TreePath> element) {
						return element.remove();
					}

					@Override
					public ElementSyncAction rightOnly(ElementSyncInput<BetterList<T>, TreePath> element) {
						return element.useValue(ObservableTreeModel.betterPath(element.getRightValue()));
					}

					@Override
					public ElementSyncAction common(ElementSyncInput<BetterList<T>, TreePath> element) {
						return element.preserve();
					}

				}, CollectionUtils.AdjustmentOrder.RightOrder);
			} finally {
				callbackLock[0] = false;
			}
		};
		selectionModel.addTreeSelectionListener(selListener);
		// Tree model->update collection
		TreeModelListener modelListener = new TreeModelListener() {
			@Override
			public void treeNodesInserted(TreeModelEvent e) {
			}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {
			}

			@Override
			public void treeNodesChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				int parentRow = tree.getRowForPath(e.getTreePath());
				if (parentRow < 0 || !tree.isExpanded(parentRow))
					return;

				Transaction t = multiSelection.tryLock(true, e);
				if (t == null)
					return;
				callbackLock[0] = true;
				try {
					for (CollectionElement<BetterList<T>> selected : multiSelection.elements()) {
						if (eventApplies(e, selected.get(), equivalence, () -> multiSelection.getElementsBefore(selected.getElementId())))
							multiSelection.mutableElement(selected.getElementId()).set(selected.get());
					}
				} finally {
					t.close();
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				// Update the entire selection
				Transaction t = multiSelection.tryLock(true, e);
				if (t == null)
					return;
				try {
					for (CollectionElement<BetterList<T>> selected : multiSelection.elements())
						multiSelection.mutableElement(selected.getElementId()).set(selected.get());
				} finally {
					t.close();
				}
			}
		};
		ObservableTreeModel<T> treeModel = (ObservableTreeModel<T>) tree.getModel();
		treeModel.addTreeModelListener(modelListener);
		// collection->tree selection
		Subscription msSub = multiSelection.simpleChanges().act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try (Transaction t = multiSelection.lock(false, modelListener)) {
				TreePath[] selection = new TreePath[multiSelection.size()];
				int i = 0;
				for (BetterList<T> path : multiSelection)
					selection[i++] = treeModel.getTreePath(path, equivalence);
				selectionModel.setSelectionPaths(selection);
			} finally {
				callbackLock[0] = false;
			}
		});

		until.take(1).act(__ -> {
			selectionModel.removeTreeSelectionListener(selListener);
			tree.getModel().removeTreeModelListener(modelListener);
			msSub.unsubscribe();
		});
	}

	public static <T> boolean isSamePath(BetterList<T> valuePath, TreePath treePath, Equivalence<? super T> equivalence) {
		for (T value : valuePath.reverse()) {
			if (treePath == null)
				return false;
			if (!equivalence.elementEquals(((ObservableTreeModel<T>.TreeNode) treePath.getLastPathComponent()).get(), value))
				return false;
			treePath = treePath.getParentPath();
		}
		return treePath == null;
	}

	public static <T> boolean eventApplies(TreeModelEvent e, BetterList<T> path, Equivalence<? super T> equivalence, IntSupplier index) {
		if (!isSamePath(path.subList(0, e.getTreePath().getPathCount()), e.getTreePath(), equivalence))
			return false;
		return Arrays.binarySearch(e.getChildIndices(), index.getAsInt()) >= 0;
	}
}
