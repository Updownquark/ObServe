package org.observe.util.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.TreeEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.observe.util.swing.PanelPopulationImpl.SimpleHPanel;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

/**
 * Utility for building a {@link JTree}
 *
 * @param <F> The super-type of values in the tree
 * @param <P> The sub-type of this builder
 */
public class SimpleTreeBuilder<F, P extends SimpleTreeBuilder<F, P>> extends AbstractComponentEditor<JTree, P> implements TreeEditor<F, P> {
	/**
	 * Creates a tree with a function that accepts the node value
	 *
	 * @param <F> The super-type of values in the tree
	 * @param root The root value for the tree
	 * @param children The function to produce children for each tree node value
	 * @param until The observable which, when it fires, will disconnect all of the tree's listeners to the values
	 * @return The tree builder to configure
	 */
	public static <F> SimpleTreeBuilder<F, ?> createTree(ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeBuilder<>(root, new PPTreeModel1<>(root, children), until);
	}

	/**
	 * Creates a tree with a function that accepts the node path
	 *
	 * @param <F> The super-type of values in the tree
	 * @param root The root value for the tree
	 * @param children The function to produce children for each tree node path
	 * @param until The observable which, when it fires, will disconnect all of the tree's listeners to the values
	 * @return The tree builder to configure
	 */
	public static <F> SimpleTreeBuilder<F, ?> createTree2(ObservableValue<? extends F> root,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return createTree3(root, (path, nodeUntil) -> children.apply(path), until);
	}

	/**
	 * Creates a tree with a function that accepts the node path and an observable that fires when the node is removed
	 *
	 * @param <F> The super-type of values in the tree
	 * @param root The root value for the tree
	 * @param children The function to produce children for each tree node path
	 * @param until The observable which, when it fires, will disconnect all of the tree's listeners to the values
	 * @return The tree builder to configure
	 */
	public static <F> SimpleTreeBuilder<F, ?> createTree3(ObservableValue<? extends F> root,
		BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeBuilder<>(root, new PPTreeModel3<>(root, children), until);
	}

	private final ObservableValue<? extends F> theRoot;

	private String theItemName;

	private CategoryRenderStrategy<BetterList<F>, F> theRenderer;
	private SettableValue<F> theValueSingleSelection;
	private SettableValue<BetterList<F>> thePathSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<F> theValueMultiSelection;
	private ObservableCollection<BetterList<F>> thePathMultiSelection;
	private ObservableValue<String> theDisablement;
	private boolean isRootVisible;
	private List<SimpleDataAction<BetterList<F>, ?>> theActions;
	private boolean theActionsOnTop;

	private SimpleTreeBuilder(ObservableValue<? extends F> root, ObservableTreeModel<F> model, Observable<?> until) {
		super(null, new JTree(model), until);
		theRenderer = new CategoryRenderStrategy<>("Tree", (TypeToken<F>) root.getType(),
			LambdaUtils.printableFn(BetterList::getLast, "BetterList::getLast", null));
		theRoot = root;
		isRootVisible = true;
		theActions = new ArrayList<>();
		theActionsOnTop = true;
	}

	static abstract class PPTreeModel<F> extends ObservableTreeModel<F> {
		private Predicate<? super F> theLeafTest;
		private Predicate<? super BetterList<F>> theLeafTest2;

		PPTreeModel(ObservableValue<? extends F> root) {
			super(root);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {}

		@Override
		public boolean isLeaf(Object node) {
			Predicate<? super F> leafTest = theLeafTest;
			if (leafTest != null)
				return leafTest.test((F) node);
			Predicate<? super BetterList<F>> leafTest2 = theLeafTest2;
			if (leafTest2 != null) {
				BetterList<F> path = getBetterPath((F) node, false);
				if (path != null)
					return leafTest2.test(path);
			}
			return false;
		}

		public void setLeafTest(Predicate<? super F> leafTest) {
			theLeafTest = leafTest;
		}

		public void setLeafTest2(Predicate<? super BetterList<F>> leafTest) {
			theLeafTest2 = leafTest;
		}
	}

	static class PPTreeModel1<F> extends PPTreeModel<F> {
		private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren;

		PPTreeModel1(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children) {
			super(root);
			theChildren = children;
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(BetterList<F> parentPath, Observable<?> until) {
			return theChildren.apply(parentPath.getLast());
		}
	}

	static class PPTreeModel3<F> extends PPTreeModel<F> {
		private final BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> theChildren;

		PPTreeModel3(ObservableValue<? extends F> root,
			BiFunction<? super BetterList<F>, Observable<?>, ? extends ObservableCollection<? extends F>> children) {
			super(root);
			theChildren = children;
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(BetterList<F> parentPath, Observable<?> nodeUntil) {
			return theChildren.apply(parentPath, nodeUntil);
		}
	}

	@Override
	public List<BetterList<F>> getSelection() {
		TreePath[] selection = getEditor().getSelectionPaths();
		if (selection == null)
			return BetterList.empty();
		return BetterList.of(Arrays.stream(selection)//
			.map(path -> (BetterList<F>) BetterList.of(path.getPath())));
	}

	@Override
	public P disableWith(ObservableValue<String> disabled) {
		if (theDisablement == null)
			theDisablement = disabled;
		else
			theDisablement = ObservableValue.firstValue(TypeTokens.get().STRING, msg -> msg != null, () -> null, theDisablement, disabled);
		return (P) this;
	}

	@Override
	public P withRemove(Consumer<? super List<? extends BetterList<F>>> deletion, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public P withMultiAction(String actionName, Consumer<? super List<? extends BetterList<F>>> action,
		Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		SimpleDataAction<BetterList<F>, ?> ta = new SimpleDataAction<>(actionName, this, action, this::getSelection, false, getUntil());
		actionMod.accept(ta);
		theActions.add(ta);
		return (P) this;
	}

	@Override
	public P withActionsOnTop(boolean actionsOnTop) {
		theActionsOnTop = actionsOnTop;
		return (P) this;
	}

	@Override
	public P withItemName(String itemName) {
		theItemName = itemName;
		return (P) this;
	}

	@Override
	public String getItemName() {
		return theItemName;
	}

	@Override
	public ObservableValue<? extends F> getRoot() {
		return theRoot;
	}

	@Override
	public CategoryRenderStrategy<BetterList<F>, F> getRender() {
		return theRenderer;
	}

	@Override
	public P withRender(CategoryRenderStrategy<BetterList<F>, F> render) {
		theRenderer = render;
		return (P) this;
	}

	@Override
	public P withSelection(SettableValue<BetterList<F>> selection, boolean enforceSingleSelection) {
		thePathSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withSelection(ObservableCollection<BetterList<F>> selection) {
		thePathMultiSelection = selection;
		return (P) this;
	}

	@Override
	public P withValueSelection(SettableValue<F> selection, boolean enforceSingleSelection) {
		theValueSingleSelection = selection;
		isSingleSelection = enforceSingleSelection;
		return (P) this;
	}

	@Override
	public P withValueSelection(ObservableCollection<F> selection) {
		theValueMultiSelection = selection;
		return (P) this;
	}

	@Override
	public P withLeafTest(Predicate<? super F> leafTest) {
		((SimpleTreeBuilder.PPTreeModel<F>) getEditor().getModel()).setLeafTest(leafTest);
		return (P) this;
	}

	@Override
	public P withLeafTest2(Predicate<? super BetterList<F>> leafTest) {
		((SimpleTreeBuilder.PPTreeModel<F>) getEditor().getModel()).setLeafTest2(leafTest);
		return (P) this;
	}

	@Override
	public P withRootVisible(boolean rootVisible) {
		isRootVisible = rootVisible;
		return (P) this;
	}

	@Override
	public boolean isVisible(List<? extends F> path) {
		return getEditor().isVisible(new TreePath(path.toArray()));
	}

	@Override
	public boolean isExpanded(List<? extends F> path) {
		return getEditor().isExpanded(new TreePath(path.toArray()));
	}

	@Override
	protected Component createComponent() {
		int[] hoveredRow = new int[] { -1 };
		if (theRenderer.getRenderer() != null)
			getEditor().setCellRenderer(new ObservableTreeCellRenderer<>(theRenderer.getRenderer(), hoveredRow));
		MouseMotionListener motion = new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				mouseMoved(e);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				hoveredRow[0] = -1;
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				TreePath path = getEditor().getPathForLocation(e.getX(), e.getY());
				int row = getEditor().getRowForLocation(e.getX(), e.getY());
				if (row != hoveredRow[0]) {
					if (hoveredRow[0] >= 0)
						getEditor().repaint(getEditor().getRowBounds(hoveredRow[0]));
					if (row >= 0)
						getEditor().repaint(getEditor().getRowBounds(row));
					hoveredRow[0] = row;
				}
				if (path == null || theRenderer.getTooltipFn() == null) {
					getEditor().setToolTipText(null);
				} else {
					F value = (F) path.getLastPathComponent();
					ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(() -> ObservableTreeModel.betterPath(path), value, row, 0,
						getEditor().getSelectionModel().isRowSelected(row), false, true, true, !getEditor().isCollapsed(row),
						getEditor().getModel().isLeaf(value));
					String tooltip = theRenderer.getTooltip(cell);
					getEditor().setToolTipText(tooltip);
				}
			}
		};
		getEditor().addMouseMotionListener(motion);
		getUntil().take(1).act(__ -> {
			getEditor().removeMouseMotionListener(motion);
		});
		ObservableTreeModel<F> model = (ObservableTreeModel<F>) getEditor().getModel();
		if (thePathMultiSelection != null)
			ObservableTreeModel.syncSelection(getEditor(), thePathMultiSelection, getUntil());
		if (theValueMultiSelection != null)
			ObservableTreeModel.syncSelection(getEditor(),
				theValueMultiSelection.flow()
				.transform(TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(getRoot().getType()),
					tx -> tx.map(v -> model.getBetterPath(v, true)))
				.filter(p -> p == null ? "Value not present" : null).collectActive(getUntil()),
				getUntil());
		if (thePathSingleSelection != null)
			ObservableTreeModel.syncSelection(getEditor(), thePathSingleSelection, false, Equivalence.DEFAULT, getUntil());
		if (theValueSingleSelection != null)
			ObservableTreeModel.syncSelection(getEditor(),
				theValueSingleSelection.safe(ThreadConstraint.EDT, getUntil()).transformReversible(//
					TypeTokens.get().keyFor(BetterList.class).<BetterList<F>> parameterized(getRoot().getType()),
					tx -> tx.map(v -> model.getBetterPath(v, true)).withReverse(path -> path == null ? null : path.getLast())),
				false, Equivalence.DEFAULT, getUntil());
		if (isSingleSelection)
			getEditor().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

		if (theDisablement != null) {
			theDisablement.changes().takeUntil(getUntil()).act(evt -> {
				// Let's not worry about tooltip here. We could mess up cell tooltips and stuff.
				getEditor().setEnabled(evt.getNewValue() == null);
			});
		}
		getEditor().setExpandsSelectedPaths(true);
		getEditor().setRootVisible(isRootVisible);
		JScrollPane scroll = new JScrollPane(getEditor());
		Component comp = scroll;
		if (!theActions.isEmpty()) {
			SimpleDataAction<BetterList<F>, ?>[] actions = theActions.toArray(new SimpleDataAction[theActions.size()]);
			getEditor().getSelectionModel().addTreeSelectionListener(evt -> {
				List<BetterList<F>> selection = getSelection();
				for (SimpleDataAction<BetterList<F>, ?> action : actions)
					action.updateSelection(selection, evt);
			});
			boolean hasPopups = false, hasButtons = false;
			for (SimpleDataAction<BetterList<F>, ?> action : actions) {
				if (action.isPopup())
					hasPopups = true;
				if (action.isButton())
					hasButtons = true;
			}
			if (hasPopups) {
				withPopupMenu(popupMenu -> {
					for (SimpleDataAction<BetterList<F>, ?> action : actions) {
						if (action.isPopup())
							popupMenu.withAction("Action", action.theObservableAction, action::modifyButtonEditor);
					}
				});
			}
			if (hasButtons) {
				SimpleHPanel<JPanel, ?> buttonPanel = new SimpleHPanel<>(null,
					new JPanel(new JustifiedBoxLayout(false).setMainAlignment(JustifiedBoxLayout.Alignment.LEADING)), getUntil());
				for (SimpleDataAction<BetterList<F>, ?> action : actions) {
					if (((SimpleDataAction<?, ?>) action).isButton())
						((SimpleDataAction<BetterList<F>, ?>) action).addButton(buttonPanel);
				}
				JPanel treePanel = new JPanel(new BorderLayout());
				treePanel.add(buttonPanel.getComponent(), theActionsOnTop ? BorderLayout.NORTH : BorderLayout.SOUTH);
				treePanel.add(scroll, BorderLayout.CENTER);
				comp = treePanel;
			}
		}
		return comp;
	}


	@Override
	public ObservableValue<String> getTooltip() {
		return null;
	}

	@Override
	protected Component createFieldNameLabel(Observable<?> until) {
		return null;
	}

	@Override
	protected Component createPostLabel(Observable<?> until) {
		return null;
	}

	static class ObservableTreeCellRenderer<F> implements TreeCellRenderer {
		private final ObservableCellRenderer<? super BetterList<F>, ? super F> theRenderer;
		private final int[] theHoveredRowColumn;

		ObservableTreeCellRenderer(ObservableCellRenderer<? super BetterList<F>, ? super F> renderer, int[] hoveredRowColumn) {
			theRenderer = renderer;
			theHoveredRowColumn = hoveredRowColumn;
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf,
			int row, boolean hasFocus) {
			Supplier<BetterList<F>> modelValue = () -> {
				TreePath path = tree.getPathForRow(row);
				if (path != null && path.getLastPathComponent() == value)
					return ObservableTreeModel.betterPath(path);
				else {
					// This can happen when this is called from an expand/collapse event, as the state may not have been updated
					return ((ObservableTreeModel<F>) tree.getModel()).getBetterPath((F) value, false);
				}
			};
			boolean hovered = theHoveredRowColumn[0] == row;
			ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(modelValue, (F) value, row, 0, selected, hasFocus,
				hovered, hovered, expanded, leaf);
			return theRenderer.getCellRendererComponent(tree, cell, null);
		}
	}
}