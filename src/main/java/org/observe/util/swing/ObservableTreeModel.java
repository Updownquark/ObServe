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
import org.qommons.IdentityKey;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
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

	private final Map<IdentityKey<T>, TreeNode> theNodes;
	private final List<TreeModelListener> theListeners;

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
		theNodes.put(new IdentityKey<>(theRootNode.get()), theRootNode);

		root.noInitChanges().safe(ThreadConstraint.EDT).act(evt -> doRootChanged(evt.getNewValue()));
	}

	/** @return The observable value that is the root of this tree */
	public ObservableValue<? extends T> observeRoot() {
		return theRoot;
	}

	@Override
	public Object getRoot() {
		return theRoot.get();
	}

	TreeNode getNode(T value, boolean searchDeeply) {
		if (value == null)
			return null;
		TreeNode found = theNodes.get(new IdentityKey<>(value));
		if (found != null || !searchDeeply || !ThreadConstraint.EDT.isEventThread()) // Can't do the search off the EDT
			return found;
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
	public BetterList<T> getBetterPath(T value, boolean searchDeeply) {
		TreeNode node = getNode(value, searchDeeply);
		return node == null ? null : node.getBetterPath();
	}

	/** Fires a change event on the root (but not the entire tree) */
	public void rootChanged() {
		rootChanged(theRoot.get());
	}

	private void rootChanged(T newRoot) {
		ObservableSwingUtils.onEQ(() -> doRootChanged(newRoot));
	}

	private void doRootChanged(T newRoot) {
		if (newRoot == theRootNode.get()) {
			theRootNode.changed();
			TreeModelEvent event = new TreeModelEvent(this, new Object[] { theRoot.get() }, null, null);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesChanged(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		} else {
			theRootNode.dispose();
			if (!theNodes.isEmpty())
				System.err.println("Discard missed nodes: " + theNodes.keySet());
			theRootNode = new TreeNode(null, newRoot);
			theNodes.put(new IdentityKey<>(newRoot), theRootNode);
			TreeModelEvent event = new TreeModelEvent(this, new Object[] { theRoot.get() }, null, null);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeStructureChanged(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public Object getChild(Object parent, int index) {
		TreeNode node = getNode((T) parent, false);
		if (node == null)
			System.err.println("Asking for child of node " + parent + ", which does not exist");
		return node == null ? null : node.getChild(index);
	}

	@Override
	public int getChildCount(Object parent) {
		TreeNode node = getNode((T) parent, false);
		return node == null ? 0 : node.getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		TreeNode node = getNode((T) parent, false);
		return node == null ? -1 : node.indexOfChild(child);
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

	class TreeNode {
		private final TreeNode theParent;
		private final T theValue;
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

		TreeNode getParent() {
			return theParent;
		}

		T get() {
			return theValue;
		}

		int getDepth() {
			return theDepth;
		}

		ObservableCollection<? extends T> getChildren() {
			return theChildren;
		}

		List<TreeNode> getChildNodes() {
			return theChildNodes;
		}

		private TreeNode initChildren() {
			if (areChildrenInitialized) {
				return this;
			}
			areChildrenInitialized = true;

			unsubscribe = SimpleObservable.build().build();
			theUnsafeChildren = ObservableTreeModel.this.getChildren(getBetterPath(), unsubscribe.readOnly());
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
				unsubscribe.act(__ -> unsubscribed[0] = true);
				theChildren.changes().takeUntil(unsubscribe).act(event -> { // theChildren is already safe
					if (unsubscribed[0])
						return;
					int[] indexes = event.getIndexes();
					switch (event.type) {
					case add:
						added(indexes, event.getValues().toArray());
						break;
					case remove:
						removed(indexes, event.getValues().toArray());
						break;
					case set:
						boolean justChanges = true;
						for (int i = 0; i < indexes.length && justChanges; i++)
							justChanges &= event.elements.get(i).oldValue == event.elements.get(i).newValue;
						if (justChanges)
							changed(indexes, event.getValues().toArray());
						else {
							for (int i = 0; i < indexes.length; i++) {
								removed(new int[] { indexes[i] }, new Object[] { event.elements.get(i).oldValue });
								added(new int[] { indexes[i] }, new Object[] { event.elements.get(i).newValue });
							}
						}
						break;
					}
				});
			}
		}

		private TreeNode newChild(T value) {
			TreeNode ret = new TreeNode(this, value);
			TreeNode old = theNodes.put(new IdentityKey<>(value), ret);
			if (old != null) {
				System.err.println("Multiple identical values in the tree!\n" + value + " moved from " + old.theParent.theValue + " to "
					+ theValue + "\n" + "Tree will be corrupt!");
			}
			return ret;
		}

		int getChildCount() {
			if (!areChildrenInitialized)
				initChildren();
			return theChildNodes.size();
		}

		Object getChild(int index) {
			if (!areChildrenInitialized)
				initChildren();
			return theChildNodes.get(index).theValue;
		}

		int indexOfChild(Object child) {
			if (!areChildrenInitialized)
				initChildren();
			for (int i = 0; i < theChildNodes.size(); i++) {
				if (theChildNodes.get(i).theValue == child) {
					return i;
				}
			}
			return -1;
		}

		private void dispose() {
			theNodes.remove(new IdentityKey<>(theValue));
			if (unsubscribe != null)
				unsubscribe.onNext(null);
			for (TreeNode child : theChildNodes)
				child.dispose();
		}

		TreePath getPath() {
			ArrayList<Object> path = new ArrayList<>(theDepth + 1);
			TreeNode node = this;
			do {
				path.add(node.theValue);
				node = node.theParent;
			} while (node != null);
			Collections.reverse(path);
			return new TreePath(path.toArray());
		}

		BetterList<T> getBetterPath() {
			Object[] pathArray = new Object[theDepth + 1];
			TreeNode node = this;
			for (int i = theDepth; node != null; i--) {
				pathArray[i] = node.get();
				node = node.theParent;
			}
			return (BetterList<T>) (BetterList<?>) BetterList.of(pathArray);
		}

		void changed() {
			if (!areChildrenInitialized)
				return;
			ObservableCollection<? extends T> children = ObservableTreeModel.this.getChildren(getBetterPath(), unsubscribe.readOnly());
			if (theUnsafeChildren == children
				|| (children != null && theUnsafeChildren != null && children.getIdentity().equals(theUnsafeChildren.getIdentity())))
				return;
			try (Transaction t = Transactable.lock(theChildren, false, null); //
				Transaction t2 = Transactable.lock(children, false, null)) {
				unsubscribe.onNext(null);
				if (theChildren != null) {
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

		private void added(int[] indexes, Object[] values) {
			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			for (int i = 0; i < indexes.length; i++) {
				TreeNode newNode = newChild((T) values[i]);
				theChildNodes.add(indexes[i], newNode);
			}

			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesInserted(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}

		private void removed(int[] indexes, Object[] values) {
			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			for (int i = indexes.length - 1; i >= 0; i--) {
				TreeNode node = theChildNodes.get(indexes[i]);
				theChildNodes.remove(indexes[i]);
				node.dispose();
				values[i] = node.theValue;
			}

			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesRemoved(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}

		private void changed(int[] indexes, Object[] values) {
			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			if (indexes.length == 0)
				indexes = null;
			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners) {
				try {
					listener.treeNodesChanged(event);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}

			if (indexes != null) {
				for (int i : indexes)
					theChildNodes.get(i).changed();
			}
		}

		private void sort(int[] indexes, Object[] values) {
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
					Object tempVal = values[idx1];
					values[idx1] = values[idx2];
					values[idx2] = tempVal;
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
		Object[] pathArray = new Object[treePath.getPathCount()];
		for (int i = 0; i < pathArray.length; i++)
			pathArray[i] = treePath.getPathComponent(i);
		return (BetterList<T>) (BetterList<?>) BetterList.of(pathArray);
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
					BetterList<T> list = BetterList.of((List<T>) (List<?>) Arrays.asList(path.getPath()));
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
			public void treeNodesInserted(TreeModelEvent e) {}

			@Override
			public void treeNodesRemoved(TreeModelEvent e) {}

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
				if (!selPath.isDescendant(e.getTreePath()) || selPath.getPathCount() == e.getTreePath().getPathCount())
					return;
				Object selNode = selPath.getPathComponent(e.getTreePath().getPathCount());
				int found = -1;
				for (int c = 0; c < e.getChildren().length; c++) {
					if (e.getChildren()[c] == selNode) {
						found = c;
						break;
					}
				}
				if (found < 0)
					return;
				callbackLock[0] = true;
				try {
					List<T> list = new ArrayList<>(e.getPath().length + 1);
					list.addAll((List<T>) (List<?>) Arrays.asList(e.getPath()));
					list.add((T) e.getChildren()[found]);
					selection.set(BetterList.of(list), e);
				} finally {
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;
				TreeSelectionModel selModel = selectionModel[0];
				List<T> list;
				if (selModel.isSelectionEmpty())
					list = null;
				else if (singularOnly && selModel.getSelectionCount() > 1)
					list = null;
				else {
					TreePath path = tree.getPathForRow(selModel.getLeadSelectionRow());
					list = (List<T>) (List<?>) Arrays.asList(path.getPath());
				}
				if (!Objects.equals(list, selection.get())) {
					callbackLock[0] = true;
					try {
						selection.set(list == null ? null : BetterList.of(list), e);
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
					&& equivalence.elementEquals((T) selModel.getLeadSelectionPath().getLastPathComponent(), evt.getNewValue().getLast())) {
					if (selModel.getLeadSelectionRow() == 0)
						model.rootChanged();
					else {
						TreePath parentPath = selModel.getLeadSelectionPath().getParentPath();
						int parentRow = tree.getRowForPath(parentPath);
						int childIdx = tree.getRowForPath(selModel.getLeadSelectionPath()) - parentRow - 1;
						ObservableCollection<? extends T> children = model.getNode((T) parentPath.getLastPathComponent(), false)
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
					TreePath path = new TreePath(evt.getNewValue().toArray());
					Runnable select = new Runnable() {
						int tries = 0;

						@Override
						public void run() {
							if (callbackLock[0])
								return;
							callbackLock[0] = true;
							try {
								if (model.getNode((T) path.getLastPathComponent(), false) != null && tree.isExpanded(path.getParentPath()))
									selModel.setSelectionPath(path);
								else if (++tries < path.getPathCount() + 5) {
									for (TreePath p = path.getParentPath(); p != null; p = p.getParentPath()) {
										if (model.getNode((T) p.getLastPathComponent(), false) != null) {
											if (!tree.isExpanded(p))
												tree.expandPath(p);
											break;
										}
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
	public static <T> void syncSelection(JTree tree, ObservableCollection<BetterList<T>> multiSelection, Observable<?> until) {
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
					(better, treePath) -> isSamePath(better, treePath))//
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
						if (eventApplies(e, selected.get()))
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
		tree.getModel().addTreeModelListener(modelListener);
		// collection->tree selection
		Subscription msSub = multiSelection.simpleChanges().act(evt -> {
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try (Transaction t = multiSelection.lock(false, modelListener)) {
				TreePath[] selection = new TreePath[multiSelection.size()];
				int i = 0;
				for (BetterList<T> path : multiSelection)
					selection[i++] = new TreePath(path.toArray());
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

	private static boolean isSamePath(BetterList<?> better, TreePath treePath) {
		if (better.size() != treePath.getPathCount())
			return false;
		for (Object betterV : better.reverse()) {
			if (!Objects.equals(betterV, treePath.getLastPathComponent()))
				return false;
			treePath = treePath.getParentPath();
		}
		return true;
	}

	private static boolean eventApplies(TreeModelEvent e, BetterList<?> path) {
		if (path.size() <= e.getTreePath().getPathCount())
			return false;
		if (!isSamePath(path.subList(0, e.getTreePath().getPathCount()), e.getTreePath()))
			return false;
		return ArrayUtils.contains(e.getChildren(), path.get(e.getTreePath().getPathCount()));
	}
}
