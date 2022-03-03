package org.observe.util.swing;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.IdentityKey;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeToken;

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
	public ObservableTreeModel(T rootValue) {
		this(ObservableValue.<T> of(rootValue == null ? (TypeToken<T>) (TypeToken<?>) TypeTokens.get().OBJECT
			: (TypeToken<T>) TypeTokens.get().of(rootValue.getClass()), rootValue));
	}

	/** @param root The observable value for the root of the model */
	public ObservableTreeModel(ObservableValue<? extends T> root) {
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

	TreeNode getNode(T value) {
		if (value == null)
			return null;
		return theNodes.get(new IdentityKey<>(value));
	}

	public BetterList<T> getBetterPath(T value) {
		TreeNode node = getNode(value);
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
			theNodes.remove(new IdentityKey<>(theRootNode.get()));
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
		TreeNode node = getNode((T) parent);
		return node == null ? null : node.getChild(index);
	}

	@Override
	public int getChildCount(Object parent) {
		TreeNode node = getNode((T) parent);
		return node == null ? 0 : node.getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		TreeNode node = getNode((T) parent);
		return node == null ? -1 : node.indexOfChild(child);
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
	 * @param parent
	 *            The parent to get the children of
	 * @return An observable collection representing the parent's children
	 */
	protected abstract ObservableCollection<? extends T> getChildren(T parent);

	/** Releases this model's observers placed on child collections */
	public void dispose() {
		TreeNode node = getNode(theRoot.get());
		node.dispose();
		theNodes.clear();
		theListeners.clear();
	}

	class TreeNode {
		private final TreeNode theParent;
		private final T theValue;
		private final int theDepth;
		private final List<TreeNode> theChildNodes;
		private ObservableCollection<? extends T> theChildren;
		private SimpleObservable<Void> unsubscribe;
		private boolean areChildrenInitialized;

		TreeNode(TreeNode parent, T value) {
			theParent = parent;
			theValue = value;
			theDepth = parent == null ? 0 : parent.getDepth() + 1;
			theChildNodes = new ArrayList<>();
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

		private synchronized void initChildren() {
			if (areChildrenInitialized) {
				return;
			}
			areChildrenInitialized = true;

			unsubscribe = SimpleObservable.build().build();
			theChildren = ObservableTreeModel.this.getChildren(theValue);
			if (theChildren != null)
				theChildren = theChildren.safe(ThreadConstraint.EDT, unsubscribe);
			init(false);
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

				theChildren.changes().takeUntil(unsubscribe).act(event -> {
					ObservableSwingUtils.onEQ(() -> {
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
			ObservableCollection<? extends T> children = ObservableTreeModel.this.getChildren(theValue);
			if (theChildren == children
				|| (children != null && theChildren != null && children.getIdentity().equals(theChildren.getIdentity())))
				return;
			try (Transaction t = Transactable.lock(theChildren, false, null); //
				Transaction t2 = Transactable.lock(children, false, null)) {
				if (unsubscribe != null)
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
				theChildren = children;
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

			for (int i : indexes)
				theChildNodes.get(i).changed();
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
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel[] selectionModel = new TreeSelectionModel[] { tree.getSelectionModel() };
		TreeSelectionListener selListener = e -> {
			if (callbackLock[0])
				return;
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
					List<T> list = (List<T>) (List<?>) Arrays.asList(path.getPath());
					selection.set(BetterList.of(list), e);
				} else if (selection.get() != null)
					selection.set(null, e);
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
				int parentRow = tree.getRowForPath(e.getTreePath());
				if (parentRow < 0 || !tree.isExpanded(parentRow))
					return;
				TreeSelectionModel selModel = selectionModel[0];
				if (selModel.isSelectionEmpty())
					return;
				else if (singularOnly && selModel.getSelectionCount() > 1)
					return;
				int selIdx = selModel.getLeadSelectionRow();
				int found = ArrayUtils.binarySearch(0, e.getChildIndices().length, i -> Integer.compare(parentRow + i, selIdx));
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
						selection.set(BetterList.of(list), e);
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
		tree.getModel().addTreeModelListener(modelListener);
		selection.changes().takeUntil(until).act(evt -> ObservableSwingUtils.onEQ(() -> {
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
					ObservableTreeModel<T> model = (ObservableTreeModel<T>) tree.getModel();
					if (selModel.getLeadSelectionRow() == 0)
						model.rootChanged();
					else {
						TreePath parentPath = selModel.getLeadSelectionPath().getParentPath();
						int parentRow = tree.getRowForPath(parentPath);
						int childIdx = tree.getRowForPath(selModel.getLeadSelectionPath()) - parentRow - 1;
						ObservableCollection<? extends T> children = model.getNode((T) parentPath.getLastPathComponent()).getChildren();
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
					int row = tree.getRowForPath(path);
					if (row < 0) {
						tree.expandPath(path);
						selModel.setSelectionPath(path);
						tree.scrollPathToVisible(path);
					}
				}
			} finally {
				callbackLock[0] = false;
			}
		}));

		until.take(1).act(__ -> {
			tree.removePropertyChangeListener("selectionModel", selModelListener);
			selectionModel[0].removeTreeSelectionListener(selListener);
			tree.getModel().removeTreeModelListener(modelListener);
		});
	}

	public static <T> void syncSelection(JTree tree, ObservableCollection<BetterList<T>> multiSelection, Observable<?> until) {
		// TODO Auto-generated method stub
		System.err.println("Not implemented yet");
	}
}
