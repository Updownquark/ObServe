package org.observe.util.swing;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.DataAction;
import org.observe.util.swing.PanelPopulation.TreeEditor;
import org.observe.util.swing.PanelPopulationImpl.AbstractComponentEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleButtonEditor;
import org.observe.util.swing.PanelPopulationImpl.SimpleDataAction;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.collect.BetterList;

import com.google.common.reflect.TypeToken;

public class SimpleTreeBuilder<F, P extends SimpleTreeBuilder<F, P>> extends AbstractComponentEditor<JTree, P> implements TreeEditor<F, P> {
	public static <F> SimpleTreeBuilder<F, ?> createTree(ObservableValue<? extends F> root,
		Function<? super F, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeBuilder<>(root, new PPTreeModel1<>(root, children), until);
	}

	public static <F> SimpleTreeBuilder<F, ?> createTree2(ObservableValue<? extends F> root,
		Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children, Observable<?> until) {
		return new SimpleTreeBuilder<>(root, new PPTreeModel2<>(root, children), until);
	}

	private final ObservableValue<? extends F> theRoot;

	private String theItemName;

	private CategoryRenderStrategy<BetterList<F>, F> theRenderer;
	private SettableValue<F> theValueSingleSelection;
	private SettableValue<BetterList<F>> thePathSingleSelection;
	private boolean isSingleSelection;
	private ObservableCollection<F> theValueMultiSelection;
	private ObservableCollection<BetterList<F>> thePathMultiSelection;
	private List<SimpleDataAction<BetterList<F>, ?>> theActions;

	private SimpleTreeBuilder(ObservableValue<? extends F> root, ObservableTreeModel<F> model, Observable<?> until) {
		super(null, new JTree(model), until);
		theRenderer = new CategoryRenderStrategy<>("Tree", (TypeToken<F>) root.getType(),
			LambdaUtils.printableFn(BetterList::getLast, "BetterList::getLast", null));
		theRoot = root;
		theActions = new ArrayList<>();
	}

	static abstract class PPTreeModel<F> extends ObservableTreeModel<F> {
		private Predicate<? super F> theLeafTest;

		PPTreeModel(ObservableValue<? extends F> root) {
			super(root);
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {}

		@Override
		public boolean isLeaf(Object node) {
			Predicate<? super F> leafTest = theLeafTest;
			return leafTest != null && leafTest.test((F) node);
		}

		public void setLeafTest(Predicate<? super F> leafTest) {
			theLeafTest = leafTest;
		}
	}

	static class PPTreeModel1<F> extends PPTreeModel<F> {
		private final Function<? super F, ? extends ObservableCollection<? extends F>> theChildren;

		PPTreeModel1(ObservableValue<? extends F> root, Function<? super F, ? extends ObservableCollection<? extends F>> children) {
			super(root);
			theChildren = children;
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(F parent) {
			return theChildren.apply(parent);
		}
	}

	static class PPTreeModel2<F> extends PPTreeModel<F> {
		private final Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> theChildren;

		PPTreeModel2(ObservableValue<? extends F> root, Function<? super BetterList<F>, ? extends ObservableCollection<? extends F>> children) {
			super(root);
			theChildren = children;
		}

		@Override
		protected ObservableCollection<? extends F> getChildren(F parent) {
			BetterList<F> path = getBetterPath(parent, false);
			if (path == null)
				throw new IllegalStateException("Asking for children of node " + parent + " which does not exist");
			return theChildren.apply(path);
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
	public P withRemove(Consumer<? super List<? extends BetterList<F>>> deletion, Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public P withMultiAction(String actionName, Consumer<? super List<? extends BetterList<F>>> action,
		Consumer<DataAction<BetterList<F>, ?>> actionMod) {
		SimpleDataAction<BetterList<F>, ?> ta = new SimpleDataAction<>(actionName, this, action, this::getSelection);
		actionMod.accept(ta);
		theActions.add(ta);
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

		getEditor().setExpandsSelectedPaths(true);
		if (!theActions.isEmpty()) {
			JPopupMenu popup = new JPopupMenu();
			SimpleDataAction<BetterList<F>, ?>[] actions = theActions.toArray(new SimpleDataAction[theActions.size()]);
			JMenuItem[] actionMenuItems = new JMenuItem[actions.length];
			for (int a = 0; a < actions.length; a++) {
				actionMenuItems[a] = new JMenuItem();
				SimpleDataAction<BetterList<F>, ?> action = actions[a];
				SimpleButtonEditor<?, ?> buttonEditor = new SimpleButtonEditor<>(null, actionMenuItems[a], null, action.theObservableAction,
					false, getUntil());
				action.modifyButtonEditor(buttonEditor);
				buttonEditor.getComponent();
				actionMenuItems[a].addActionListener(evt -> action.theObservableAction.act(evt));
			}
			getEditor().getSelectionModel().addTreeSelectionListener(evt -> {
				List<BetterList<F>> selection = getSelection();
				for (SimpleDataAction<BetterList<F>, ?> action : actions)
					action.updateSelection(selection, evt);
			});
			getEditor().addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent evt) {
					if (!SwingUtilities.isRightMouseButton(evt))
						return;
					popup.removeAll();
					for (int a = 0; a < actions.length; a++) {
						if (actions[a].isEnabled() == null)
							popup.add(actionMenuItems[a]);
					}
					if (popup.getComponentCount() > 0)
						popup.show(getEditor(), evt.getX(), evt.getY());
				}
			});
		}
		JScrollPane scroll = new JScrollPane(decorate(getEditor()));
		return scroll;
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
				return path != null//
					? ObservableTreeModel.betterPath(path)//
						: BetterList.of((F) tree.getModel().getRoot());
			};
			boolean hovered = theHoveredRowColumn[0] == row;
			ModelCell<BetterList<F>, F> cell = new ModelCell.Default<>(modelValue, (F) value, row, 0, selected, hasFocus,
				hovered, hovered, expanded, leaf);
			return theRenderer.getCellRendererComponent(tree, cell, null);
		}
	}
}