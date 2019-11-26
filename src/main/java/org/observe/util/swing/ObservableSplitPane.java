package org.observe.util.swing;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JComponent;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;

public class ObservableSplitPane extends JComponent {
	private final JustifiedBoxLayout theLayout;
	private final ObservableCollection<Divider> theSplitLocations;
	private final ObservableCollection<Divider> theExposedSplitLocations;

	private boolean isAdding;

	public ObservableSplitPane(boolean vertical) {
		theLayout = new JustifiedBoxLayout(vertical) {
			{
				super.mainJustified();
				super.crossJustified();
				super.setPadding(0);
			}

			@Override
			public JustifiedBoxLayout setMainAlignment(Alignment mainAlignment) {
				if (mainAlignment != JustifiedBoxLayout.Alignment.JUSTIFIED)
					throw new IllegalArgumentException("Alignment for split pane layout must be " + JustifiedBoxLayout.Alignment.JUSTIFIED);
				return this;
			}

			@Override
			public JustifiedBoxLayout setCrossAlignment(Alignment crossAlignment) {
				if (crossAlignment != JustifiedBoxLayout.Alignment.JUSTIFIED)
					throw new IllegalArgumentException("Alignment for split pane layout must be " + JustifiedBoxLayout.Alignment.JUSTIFIED);
				return this;
			}
		};
		setLayout(theLayout);
		theSplitLocations = ObservableCollection.build(Divider.class).build();
		theSplitLocations.onChange(evt -> {
			if (evt.getType() == CollectionChangeType.add)
				evt.getNewValue().theId = evt.getElementId();
		});
		theExposedSplitLocations = theSplitLocations.flow().unmodifiable(true).collectPassive();
		theSplitLocations.simpleChanges().act(__ -> revalidate());
	}

	@Override
	public JustifiedBoxLayout getLayout() {
		return theLayout;
	}

	public ObservableSplitPane modifyLayout(Consumer<JustifiedBoxLayout> layout) {
		layout.accept(theLayout);
		return this;
	}

	public boolean isVertical() {
		return theLayout.isVertical();
	}

	public ObservableCollection<Divider> getSplitLocations() {
		return theExposedSplitLocations;
	}

	public Component add(Component component, boolean first) {
		if (!EventQueue.isDispatchThread()) {
			EventQueue.invokeLater(() -> add(component, first));
			return component;
		}
		if (isAdding)
			throw new IllegalStateException("Cannot add as a result of an add");
		isAdding = true;
		try (Transaction t = theSplitLocations.lock(true, null)) {
			CollectionElement<Divider> div = theSplitLocations.getTerminalElement(first);
			if (div == null) {
				div = theSplitLocations.addElement(new Divider(component, null), true);
				add(component, 0);
				add(div.get(), 1);
			} else if (div.get().getRight() == null) {
				// Only one component
				if (first) {
					Component old = div.get().getLeft();
					div.get().setLeft(component);
					div.get().setRight(old);
				} else
					div.get().setRight(component);
			} else {
				CollectionElement<Divider> newDiv = theSplitLocations.addElement(//
					new Divider(first ? component : div.get().getRight(), first ? div.get().getLeft() : component), //
					first ? null : div.getElementId(), first ? div.getElementId() : null, first);
				if (first) {
					add(component, 0);
					add(newDiv.get(), 1);
				} else {
					add(newDiv.get(), getComponentCount());
					add(component, getComponentCount());
				}
			}
		} finally {
			isAdding = false;
		}
		return component;
	}

	@Override
	public void remove(int index) {
		if (!EventQueue.isDispatchThread()) {
			EventQueue.invokeLater(() -> remove(index));
			return;
		}
		if (isAdding)
			throw new IllegalStateException("Cannot remove as a result of an add");
		if (index % 2 == 1)
			throw new IllegalArgumentException("Dividers cannot be removed directly");
		try (Transaction t = theSplitLocations.lock(true, null)) {
			if (index >= getComponentCount())
				throw new IndexOutOfBoundsException(index + " of " + getComponentCount());
			if (theSplitLocations.size() == 1) {
				Divider div = theSplitLocations.getFirst();
				if (index == 0) {
					Component right = div.getRight();
					if (right == null) {
						theSplitLocations.clear();
						div.removed();
						super.remove(1);
						super.remove(0);
					} else {
						div.setRight(null);
						div.setLeft(right);
						super.remove(1);
						super.remove(0);
						isAdding = true;
						add(div, 1);
						isAdding = false;
					}
				} else {
					super.remove(2);
					div.setRight(null);
				}
			} else if (index == 0) {
				theSplitLocations.removeFirst().removed();
				super.remove(index + 1);
				super.remove(index);
			} else {
				theSplitLocations.remove(index / 2).removed();
				super.remove(index);
				super.remove(index - 1);
			}
		}
	}

	@Override
	public void removeAll() {
		if (!EventQueue.isDispatchThread()) {
			EventQueue.invokeLater(() -> removeAll());
			return;
		}
		try (Transaction t = theSplitLocations.lock(true, null)) {
			for (Divider div : theSplitLocations)
				div.removed();
			theSplitLocations.clear();
			super.removeAll();
		}
	}

	@Override
	protected void addImpl(Component comp, Object constraints, int index) {
		if (!isAdding)
			throw new IllegalStateException("Components cannot be added here directly");
		super.addImpl(comp, constraints, index);
	}

	public class Divider extends JComponent {
		ElementId theId;
		private double thePosition;
		private Component theLeft;
		private Component theRight;

		private final ComponentListener theLeftListener;
		private final ComponentListener theRightListener;

		public Divider(Component left, Component right) {
			theLeftListener = new ComponentAdapter() {
				@Override
				public void componentShown(ComponentEvent e) {
					checkVisibility();
				}

				@Override
				public void componentHidden(ComponentEvent e) {
					checkVisibility();
				}
			};
			theRightListener = new ComponentAdapter() {
				@Override
				public void componentShown(ComponentEvent e) {
					checkVisibility();
				}

				@Override
				public void componentHidden(ComponentEvent e) {
					checkVisibility();
				}
			};
			setCursor(Cursor.getPredefinedCursor(isVertical() ? Cursor.N_RESIZE_CURSOR : Cursor.W_RESIZE_CURSOR));
			MouseAdapter dragger = new MouseAdapter() {
				private int theDragStart;

				@Override
				public void mousePressed(MouseEvent e) {
					theDragStart = isVertical() ? e.getY() : e.getX();
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					// TODO
				}
			};
			addMouseMotionListener(dragger);

			setLeft(left);
			setRight(right);

			thePosition = Double.NaN;
		}

		void checkVisibility() {
			if (theRight == null || !theRight.isVisible())
				super.setVisible(false);
			else
				super.setVisible(theLeft.isVisible() && theSplitLocations.getAdjacentElement(theId, false) == null);
		}

		@Override
		public void setVisible(boolean aFlag) {
			if (aFlag == isVisible())
				return;
			throw new IllegalStateException("Visibility of a divider cannot be set manually");
		}

		public ElementId getElement() {
			return theId;
		}

		public Component getLeft() {
			return theLeft;
		}

		public Component getRight() {
			return theRight;
		}

		void setLeft(Component left) {
			if (theLeft != null)
				theLeft.removeComponentListener(theLeftListener);
			theLeft = left;
			if (theLeft != null)
				theLeft.addComponentListener(theLeftListener);
		}

		void setRight(Component right) {
			if (theRight != null)
				theRight.removeComponentListener(theRightListener);
			theRight = right;
			if (theRight != null)
				theRight.addComponentListener(theRightListener);
		}

		void removed() {
			setLeft(null);
			setRight(null);
		}
	}
}
