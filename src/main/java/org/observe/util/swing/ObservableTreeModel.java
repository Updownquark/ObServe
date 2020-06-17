package org.observe.util.swing;

import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.IdentityKey;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * A swing tree model well suited to visualizing observable structures
 *
 * @param <T> The type of values in the tree
 */
public abstract class ObservableTreeModel<T> implements TreeModel {
	private final ObservableValue<? extends T> theRoot;
	private final TreeNode theRootNode;

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

		root.noInitChanges().act(__ -> rootChanged());
	}

	@Override
	public Object getRoot() {
		return theRoot.get();
	}

	/** Fires a change event on the root (but not the entire tree) */
	public void rootChanged() {
		ObservableSwingUtils.onEQ(() -> {
			theRootNode.changed();
			TreeModelEvent event = new TreeModelEvent(theRootNode.get(), new Object[] { theRoot.get() }, null, null);

			for (TreeModelListener listener : theListeners)
				listener.treeNodesChanged(event);
		});
	}

	@Override
	public Object getChild(Object parent, int index) {
		TreeNode node = theNodes.get(new IdentityKey<>(parent));
		return node == null ? null : node.getChild(index);
	}

	@Override
	public int getChildCount(Object parent) {
		TreeNode node = theNodes.get(new IdentityKey<>(parent));
		return node == null ? 0 : node.getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		TreeNode node = theNodes.get(new IdentityKey<>(parent));
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
		theNodes.get(new IdentityKey<>(theRoot)).dispose();
		theNodes.clear();
		theListeners.clear();
	}

	private class TreeNode {
		private final TreeNode theParent;
		private final T theValue;
		private final List<TreeNode> theChildNodes;
		private ObservableCollection<? extends T> theChildren;
		private Subscription theChildrenSub;
		private boolean areChildrenInitialized;

		TreeNode(TreeNode parent, T value) {
			theParent = parent;
			theValue = value;
			theChildNodes = new ArrayList<>();
		}

		T get() {
			return theValue;
		}

		private synchronized void initChildren() {
			if (areChildrenInitialized) {
				return;
			}
			areChildrenInitialized = true;

			theChildren = getChildren(theValue);
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
					TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);
					for (TreeModelListener listener : theListeners)
						listener.treeNodesInserted(event);
				}

				theChildrenSub = theChildren.changes().act(event -> {
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
			if (theChildrenSub != null)
				theChildrenSub.unsubscribe();
			for (TreeNode child : theChildNodes)
				child.dispose();
		}

		TreePath getPath() {
			ArrayList<Object> path = new ArrayList<>();
			TreeNode node = this;
			do {
				path.add(node.theValue);
				node = node.theParent;
			} while (node != null);
			Collections.reverse(path);
			return new TreePath(path.toArray());
		}

		void changed() {
			if (!areChildrenInitialized)
				return;
			ObservableCollection<? extends T> children = getChildren(theValue);
			if (theChildren == children
				|| (children != null && (theChildren == null || children.getIdentity().equals(theChildren.getIdentity()))))
				return;
			try (Transaction t = Transactable.lock(theChildren, false, null); //
				Transaction t2 = Transactable.lock(children, false, null)) {
				if (theChildrenSub != null) {
					theChildrenSub.unsubscribe();
					theChildrenSub = null;
				}
				if (theChildren != null) {
					int[] indexes = new int[theChildNodes.size()];
					Object[] values = new Object[indexes.length];
					for (int i = 0; i < indexes.length; i++) {
						indexes[i] = i;
						values[i] = theChildNodes.get(i).get();
					}
					TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);
					for (TreeModelListener listener : theListeners)
						listener.treeNodesRemoved(event);

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

			for (TreeModelListener listener : theListeners)
				listener.treeNodesInserted(event);
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

			for (TreeModelListener listener : theListeners)
				listener.treeNodesRemoved(event);
		}

		private void changed(int[] indexes, Object[] values) {
			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners)
				listener.treeNodesChanged(event);

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

	public static <T> void syncSelection(JTree tree, SettableValue<List<T>> selection, Observable<?> until) {
		boolean[] callbackLock = new boolean[1];
		TreeSelectionModel[] selectionModel = new TreeSelectionModel[] { tree.getSelectionModel() };
		TreeSelectionListener selListener = e -> {
			TreeSelectionModel selModel = selectionModel[0];
			if (callbackLock[0])
				return;
			callbackLock[0] = true;
			try {
				if (selModel.getMinSelectionRow() >= 0 && selModel.getMinSelectionRow() == selModel.getMaxSelectionRow()) {
					TreePath path = tree.getPathForRow(selModel.getMinSelectionRow());
					List<T> list = (List<T>) (List<?>) Arrays.asList(path.getPath());
					selection.set(list, e);
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
				if (tree.isCollapsed(parentRow))
					return;
				TreeSelectionModel selModel = selectionModel[0];
				if (selModel.getMinSelectionRow() < 0 || selModel.getMinSelectionRow() != selModel.getMaxSelectionRow())
					return;
				callbackLock[0] = true;
				try {
					int selIdx = selModel.getMinSelectionRow();
					int found = ArrayUtils.binarySearch(0, e.getChildIndices().length, i -> Integer.compare(parentRow + i, selIdx));
					if (found >= 0) {
						List<T> list = new ArrayList<>(e.getPath().length + 1);
						list.addAll((List<T>) (List<?>) Arrays.asList(e.getPath()));
						list.add((T) e.getChildren()[found]);
						selection.set(list, e);
					}
				} finally {
					callbackLock[0] = false;
				}
			}

			@Override
			public void treeStructureChanged(TreeModelEvent e) {
				if (callbackLock[0])
					return;

				// TODO Auto-generated method stub

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
					return;
				} else if (evt.getOldValue() == evt.getNewValue()//
					&& !selModel.isSelectionEmpty() && selModel.getMinSelectionRow() == selModel.getMaxSelectionRow()//
					&& equivalence.elementEquals(model.getElementAt(selModel.getMinSelectionRow()), evt.getNewValue())) {
					if (update != null)
						update.accept(selModel.getMaxSelectionRow());
					return;
				}
				for (int i = 0; i < model.getSize(); i++) {
					if (equivalence.elementEquals(model.getElementAt(i), evt.getNewValue())) {
						selModel.setSelectionInterval(i, i);
						Rectangle rowBounds = null;
						if (component instanceof JTable)
							rowBounds = ((JTable) component).getCellRect(((JTable) component).convertRowIndexToModel(i), 0, false);
						else if (component instanceof JList)
							rowBounds = ((JList<?>) component).getCellBounds(i, i);
						if (rowBounds != null)
							((JComponent) component).scrollRectToVisible(rowBounds);
						break;
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

	public static <T> void syncSelection(JTree tree, ObservableCollection<List<T>> multiSelection, Observable<?> until) {
		// TODO Auto-generated method stub

	}
}
