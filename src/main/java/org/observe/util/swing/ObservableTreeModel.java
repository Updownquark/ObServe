package org.observe.util.swing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.observe.Subscription;
import org.observe.collect.ObservableOrderedCollection;
import org.qommons.ArrayUtils;

/** A swing tree model well suited to visualizing observable structures */
public abstract class ObservableTreeModel implements TreeModel {
	private final Object theRoot;

	private final Map<Object, TreeNode> theNodes;
	private final List<TreeModelListener> theListeners;

	/**
	 * @param root
	 *            The root of the model
	 */
	public ObservableTreeModel(Object root) {
		theRoot = root;

		theNodes = new IdentityHashMap<>();
		theListeners = new ArrayList<>();

		TreeNode rootNode = new TreeNode(null, root);
		theNodes.put(root, rootNode);
	}

	@Override
	public Object getRoot() {
		return theRoot;
	}

	@Override
	public Object getChild(Object parent, int index) {
		TreeNode node = theNodes.get(parent);
		return node == null ? null : node.getChild(index);
	}

	@Override
	public int getChildCount(Object parent) {
		TreeNode node = theNodes.get(parent);
		return node == null ? 0 : node.getChildCount();
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		TreeNode node = theNodes.get(parent);
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
	protected abstract ObservableOrderedCollection<?> getChildren(Object parent);

	/** Releases this model's observers placed on child collections */
	public void dispose() {
		theNodes.get(theRoot).dispose();
		theNodes.clear();
		theListeners.clear();
	}

	private class TreeNode {
		private final TreeNode theParent;
		private final Object theValue;
		private final List<TreeNode> theChildNodes;
		private ObservableOrderedCollection<?> theChildren;
		private Subscription theChildrenSub;
		private boolean areChildrenInitialized;

		TreeNode(TreeNode parent, Object value) {
			theParent = parent;
			theValue = value;
			theChildNodes = new ArrayList<>();
		}

		private void initChildren() {
			if (areChildrenInitialized) {
				return;
			}
			areChildrenInitialized = true;
			theChildren = getChildren(theValue);
			for (Object value : theChildren) {
				theChildNodes.add(newChild(value));
			}
			theChildrenSub = theChildren.changes().act(event -> {
				int[] indexes = event.indexes.toArray();
				switch (event.type) {
				case add:
					added(indexes, event.values.toArray());
					break;
				case remove:
					removed(indexes, event.values.toArray());
					break;
				case set:
					boolean justChanges = true;
					for (int i = 0; i < indexes.length && justChanges; i++) {
						justChanges &= event.oldValues.get(i) == event.values.get(i);
					}
					if (justChanges) {
						changed(indexes, event.values.toArray());
					} else {
						for (int i = 0; i < indexes.length; i++) {
							removed(new int[] { indexes[i] }, new Object[] { event.oldValues.get(i) });
							added(new int[] { indexes[i] }, new Object[] { event.values.get(i) });
						}
					}
					break;
				}
			});
		}

		private TreeNode newChild(Object value) {
			TreeNode ret = new TreeNode(this, value);
			theNodes.put(value, ret);
			return ret;
		}

		int getChildCount() {
			initChildren();
			return theChildNodes.size();
		}

		Object getChild(int index) {
			initChildren();
			return theChildNodes.get(index).theValue;
		}

		int indexOfChild(Object child) {
			initChildren();
			for (int i = 0; i < theChildNodes.size(); i++) {
				if (theChildNodes.get(i).theValue == child) {
					return i;
				}
			}
			return -1;
		}

		private void dispose() {
			if (theChildrenSub != null) {
				theChildrenSub.unsubscribe();
			}
			for (TreeNode child : theChildNodes) {
				child.dispose();
			}
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

		private void added(int[] indexes, Object[] values) {
			for (int i = 0; i < indexes.length; i++) {
				TreeNode newNode = newChild(values[i]);
				theChildNodes.add(indexes[i], newNode);
			}

			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners) {
				listener.treeNodesInserted(event);
			}
		}

		private void removed(int[] indexes, Object[] values) {
			for (int i = 0; i < indexes.length; i++) {
				TreeNode node = theChildNodes.get(indexes[i]);
				theNodes.remove(node.theValue);
				theChildNodes.remove(indexes[i]);
				node.dispose();
				values[i] = node.theValue;
			}

			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners) {
				listener.treeNodesRemoved(event);
			}
		}

		private void changed(int[] indexes, Object[] values) {
			// Swing expects indexes to be in ascending order
			sort(indexes, values);
			TreeModelEvent event = new TreeModelEvent(this, getPath(), indexes, values);

			for (TreeModelListener listener : theListeners) {
				listener.treeNodesChanged(event);
			}
		}

		private void sort(int[] indexes, Object[] values) {
			Integer[] indexList = new Integer[indexes.length];
			for (int i = 0; i < indexes.length; i++) {
				indexList[i] = indexes[i];
			}
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
}
