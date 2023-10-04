package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.RootPaneContainer;
import javax.swing.WindowConstants;

import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.observe.util.swing.PanelPopulation.ButtonEditor;
import org.observe.util.swing.PanelPopulation.DialogBuilder;
import org.observe.util.swing.PanelPopulation.MenuBarBuilder;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.PanelPopulationImpl.SimpleButtonEditor;
import org.qommons.BreakpointHere;
import org.qommons.Causable;
import org.qommons.Transaction;

public class WindowPopulation {
	public static WindowBuilder<JFrame, ?> populateWindow(JFrame frame, Observable<?> until, boolean disposeOnClose, boolean exitOnClose) {
		if (frame == null)
			frame = new JFrame();
		if (exitOnClose)
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		return new DefaultWindowBuilder<>(frame, until == null ? Observable.empty() : until, disposeOnClose || exitOnClose);
	}

	public static DialogBuilder<JDialog, ?> populateDialog(JDialog dialog, Observable<?> until, boolean disposeOnClose) {
		return new DefaultDialogBuilder<>(dialog == null ? new JDialog() : dialog, until == null ? Observable.empty() : until,
			disposeOnClose);
	}

	static class DefaultWindowBuilder<W extends Window, P extends DefaultWindowBuilder<W, P>> implements WindowBuilder<W, P> {
		private final W theWindow;
		private final Observable<?> theUntil;
		private final SimpleObservable<Object> theDispose;
		private ObservableValue<String> theTitle;
		private ObservableValue<? extends Image> theIcon;
		private SettableValue<Integer> theX;
		private SettableValue<Integer> theY;
		private SettableValue<Integer> theWidth;
		private SettableValue<Integer> theHeight;
		private SettableValue<Boolean> isVisible;

		private int theCloseAction;

		DefaultWindowBuilder(W window, Observable<?> until, boolean disposeOnClose) {
			theWindow = window;
			theDispose = SimpleObservable.build().build();
			theUntil = Observable.or(until.takeUntil(theDispose), theDispose);
			disposeOnClose(disposeOnClose);

			theWindow.setLayout(new JustifiedBoxLayout(true).mainJustified().crossJustified());
			if (theWindow instanceof RootPaneContainer)
				((RootPaneContainer) theWindow).getContentPane().setLayout(new JustifiedBoxLayout(true).mainJustified().crossJustified());
		}

		@Override
		public W getWindow() {
			return theWindow;
		}

		protected Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public ObservableValue<String> getTitle() {
			return theTitle;
		}

		@Override
		public P withTitle(ObservableValue<String> title) {
			theTitle = title;
			return (P) this;
		}

		@Override
		public P withIcon(ObservableValue<? extends Image> icon) {
			theIcon = icon;
			return (P) this;
		}

		@Override
		public ObservableValue<? extends Image> getIcon() {
			return theIcon;
		}

		@Override
		public P withX(SettableValue<Integer> x) {
			theX = x;
			return (P) this;
		}

		@Override
		public P withY(SettableValue<Integer> y) {
			theY = y;
			return (P) this;
		}

		@Override
		public P withWidth(SettableValue<Integer> width) {
			theWidth = width;
			return (P) this;
		}

		@Override
		public P withHeight(SettableValue<Integer> height) {
			theHeight = height;
			return (P) this;
		}

		@Override
		public P withVisible(SettableValue<Boolean> visible) {
			isVisible = visible;
			return (P) this;
		}

		@Override
		public P withMenuBar(Consumer<MenuBarBuilder<?>> menuBar) {
			if (theWindow instanceof JFrame) {
				JMenuBar menu = ((JFrame) theWindow).getJMenuBar();
				if (menu == null) {
					menu = new JMenuBar();
					((JFrame) theWindow).setJMenuBar(menu);
				}
				menuBar.accept(new JMenuBarBuilder<>(menu, theUntil));
			} else
				System.err.println("WARNING: menu bars supported only for " + JFrame.class.getName()
					+ " instances: not for for window of type " + theWindow.getClass().getName());
			return (P) this;
		}

		@Override
		public P withCloseAction(int closeAction) {
			theCloseAction = closeAction;
			return (P) this;
		}

		@Override
		public P withVContent(Consumer<PanelPopulator<?, ?>> content) {
			if (!EventQueue.isDispatchThread())
				System.err.println(
					"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
			PanelPopulator<?, ?> populator;
			if (theWindow instanceof RootPaneContainer)
				populator = PanelPopulation.populateVPanel(((RootPaneContainer) theWindow).getContentPane(), theUntil);
			else
				populator = PanelPopulation.populateVPanel(null, theUntil);
			content.accept(populator);
			if (!(theWindow instanceof RootPaneContainer))
				withContent(populator.getContainer());
			return (P) this;
		}

		@Override
		public P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> content) {
			if (!EventQueue.isDispatchThread())
				System.err.println(
					"Calling panel population off of the EDT from " + BreakpointHere.getCodeLine(1) + "--could cause threading problems!!");
			PanelPopulator<?, ?> populator;
			if (theWindow instanceof RootPaneContainer)
				populator = PanelPopulation.populateHPanel(((RootPaneContainer) theWindow).getContentPane(), layout, theUntil);
			else
				populator = PanelPopulation.populateHPanel(null, layout, theUntil);
			content.accept(populator);
			if (!(theWindow instanceof RootPaneContainer))
				withContent(populator.getContainer());
			return (P) this;
		}

		@Override
		public P withContent(Component content) {
			if (theWindow instanceof RootPaneContainer)
				((RootPaneContainer) theWindow).getContentPane().add(content);
			else
				theWindow.add(content);
			return (P) this;
		}

		@Override
		public P run(Component relativeTo) {
			theWindow.pack();
			if (theTitle != null) {
				if (theWindow instanceof Frame)
					theTitle.changes().takeUntil(theUntil)
					.act(evt -> ObservableSwingUtils.onEQ(() -> ((Frame) theWindow).setTitle(evt.getNewValue())));
				else if (theWindow instanceof Dialog)
					theTitle.changes().takeUntil(theUntil)
					.act(evt -> ObservableSwingUtils.onEQ(() -> ((Dialog) theWindow).setTitle(evt.getNewValue())));
				else
					System.err.println(
						"Title configured, but window type " + theWindow.getClass().getName() + " is not a recognized titled window");
			}
			if (theIcon != null) {
				theIcon.changes().takeUntil(theUntil)
				.act(evt -> ObservableSwingUtils.onEQ(() -> theWindow.setIconImage(evt.getNewValue())));
			}
			SettableValue<Integer> x = theX;
			SettableValue<Integer> y = theY;
			SettableValue<Integer> w = theWidth;
			SettableValue<Integer> h = theHeight;
			boolean sizeSet = false;
			if (x != null || y != null || w != null || h != null) {
				class BoundsListener extends ComponentAdapter {
					boolean callbackLock;

					@Override
					public void componentResized(ComponentEvent e) {
						if (callbackLock)
							return;
						callbackLock = true;
						Causable evt = Causable.simpleCause(e);
						try (Transaction t = evt.use()) {
							boolean sizeRefused = false;
							if (w != null && (w.get() == null || w.get() != theWindow.getWidth())) {
								if (w.isAcceptable(theWindow.getWidth()) == null)
									w.set(theWindow.getWidth(), evt);
								else
									sizeRefused = true;
							}
							if (h != null && (h.get() == null || h.get() != theWindow.getHeight())) {
								if (h.isAcceptable(theWindow.getHeight()) == null)
									h.set(theWindow.getHeight(), evt);
								else
									sizeRefused = true;
							}
							if (sizeRefused) {
								EventQueue.invokeLater(() -> {
									theWindow.setSize(theWidth.get(), theHeight.get());
								});
							}
						} finally {
							callbackLock = false;
						}
					}

					@Override
					public void componentMoved(ComponentEvent e) {
						if (callbackLock)
							return;
						callbackLock = true;
						Causable evt = Causable.simpleCause(e);
						try (Transaction t = evt.use()) {
							if (x != null && (x.get() == null || x.get() != theWindow.getX()))
								x.set(theWindow.getX(), evt);
							if (y != null && (y.get() == null || y.get() != theWindow.getY()))
								y.set(theWindow.getY(), evt);
						} finally {
							callbackLock = false;
						}
					}
				}
				BoundsListener boundsListener = new BoundsListener();
				List<Observable<?>> boundsObs = new ArrayList<>(4);
				{
					Rectangle bounds = new Rectangle(theWindow.getBounds());
					if (x != null && x.get() != null) {
						boundsObs.add(x.noInitChanges());
						int xv = x.get();
						if (xv != -1) {
							sizeSet = true;
							bounds.x = xv;
						}
					}
					if (y != null && y.get() != null) {
						boundsObs.add(y.noInitChanges());
						int yv = y.get();
						if (yv != -1) {
							sizeSet = true;
							bounds.y = yv;
						}
					}
					if (w != null && w.get() != null) {
						boundsObs.add(w.noInitChanges());
						int wv = w.get();
						if (wv > 0)
							bounds.width = wv;
					}
					if (h != null && h.get() != null) {
						boundsObs.add(h.noInitChanges());
						int hv = h.get();
						if (hv > 0)
							bounds.height = hv;
					}
					if (sizeSet) {
						bounds = ObservableSwingUtils.fitBoundsToGraphicsEnv(bounds.x, bounds.y, bounds.width, bounds.height, //
							ObservableSwingUtils.getGraphicsBounds());
						try {
							if (x != null && x.get() != null && x.get() != bounds.x) {
								x.set(bounds.x, null);
							}
							if (y != null && y.get() != null && y.get() != bounds.y) {
								y.set(bounds.y, null);
							}
							if (w != null && w.get() != null && w.get() != bounds.width) {
								w.set(bounds.width, null);
							}
							if (h != null && h.get() != null && h.get() != bounds.height) {
								h.set(bounds.height, null);
							}
						} catch (RuntimeException e) {
							e.printStackTrace();
						}
						theWindow.setBounds(bounds);
					}
				}
				Observable.or(boundsObs.toArray(new Observable[boundsObs.size()])).takeUntil(theUntil).act(evt -> {
					if (boundsListener.callbackLock)
						return;
					boundsListener.callbackLock = true;
					try {
						Rectangle bounds = new Rectangle(theWindow.getBounds());
						boolean mod = false;
						if (x != null && x.get() != null) {
							bounds.x = x.get();
							mod = true;
						}
						if (y != null && y.get() != null) {
							bounds.y = y.get();
							mod = true;
						}
						if (w != null && w.get() != null) {
							bounds.width = w.get();
							mod = true;
						}
						if (h != null && h.get() != null) {
							bounds.height = h.get();
							mod = true;
						}
						if (mod)
							theWindow.setBounds(bounds);
					} finally {
						boundsListener.callbackLock = false;
					}
				});
				theWindow.addComponentListener(boundsListener);
			}
			SettableValue<Boolean> visible = isVisible;
			boolean disposeOnClose, exitOnClose;
			switch (theCloseAction) {
			case WindowConstants.EXIT_ON_CLOSE:
				disposeOnClose = true;
				exitOnClose = true;
				break;
			case WindowConstants.DISPOSE_ON_CLOSE:
				disposeOnClose = true;
				exitOnClose = false;
				break;
			default:
				disposeOnClose = exitOnClose = false;
			}
			theWindow.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentShown(ComponentEvent e) {
					if (visible != null && !visible.get())
						visible.set(true, e);
				}

				@Override
				public void componentHidden(ComponentEvent e) {
					if (visible != null && visible.get())
						visible.set(false, e);
					if (disposeOnClose) {
						if (theDispose != null)
							theDispose.onNext(e);
					}
					if (exitOnClose)
						System.exit(0);
				}
			});
			theUntil.take(1).act(__ -> {
				if (theWindow.isVisible())
					theWindow.setVisible(false);
				theWindow.dispose();
			});
			if (visible != null) {
				boolean[] reposition = new boolean[] { !sizeSet };
				visible.changes().takeUntil(theUntil).act(evt -> {
					EventQueue.invokeLater(() -> {
						if (evt.getNewValue() && reposition[0]) {
							reposition[0] = false;
							theWindow.setLocationRelativeTo(relativeTo);
						}
						theWindow.setVisible(evt.getNewValue());
					});
				});
			} else {
				if (!sizeSet)
					theWindow.setLocationRelativeTo(relativeTo);
				theWindow.setVisible(true);
			}
			return (P) this;
		}
	}

	static class DefaultDialogBuilder<D extends JDialog, P extends DefaultDialogBuilder<D, P>> extends DefaultWindowBuilder<D, P>
	implements DialogBuilder<D, P> {
		private ObservableValue<ModalityType> theModality;

		DefaultDialogBuilder(D dialog, Observable<?> until, boolean disposeOnClose) {
			super(dialog, until, disposeOnClose);
		}

		@Override
		public P withModality(ObservableValue<ModalityType> modality) {
			theModality = modality;
			return (P) this;
		}

		@Override
		public P run(Component relativeTo) {
			if (theModality != null)
				theModality.changes().takeUntil(getUntil()).act(evt -> getWindow().setModalityType(evt.getNewValue()));
			super.run(relativeTo);
			return (P) this;
		}
	}

	static class JMenuBarBuilder<M extends JMenuBarBuilder<M>> implements MenuBarBuilder<M> {
		private final JMenuBar theMenuBar;
		private final Observable<?> theUntil;

		JMenuBarBuilder(JMenuBar menuBar, Observable<?> until) {
			theMenuBar = menuBar;
			theUntil = until;
		}

		@Override
		public M withMenu(String menuName, Consumer<MenuBuilder<JMenu, ?>> menu) {
			JMenu jmenu = null;
			for (int m = 0; m < theMenuBar.getMenuCount(); m++) {
				if (theMenuBar.getMenu(m).getText().equals(menuName)) {
					jmenu = theMenuBar.getMenu(m);
					break;
				}
			}
			boolean found = jmenu != null;
			if (!found)
				jmenu = new JMenu(menuName);
			JMenuBuilder<JMenu> builder = new JMenuBuilder<>(jmenu, theUntil);
			menu.accept(builder);
			if (!found)
				theMenuBar.add((JMenu) builder.getComponent());
			return (M) this;
		}
	}

	public static interface AbstractMenuBuilder<M extends JComponent, B extends AbstractMenuBuilder<M, B>> extends MenuBuilder<M, B> {
		void addMenuItem(JMenuItem item);

		@Override
		default B withSubMenu(String name, Consumer<MenuBuilder<JMenu, ?>> subMenu) {
			JMenu jmenu = null;
			for (int m = 0; m < getEditor().getComponentCount(); m++) {
				if (getEditor().getComponent(m) instanceof JMenu && ((JMenu) getEditor().getComponent(m)).getText().equals(name)) {
					jmenu = (JMenu) getEditor().getComponent(m);
					break;
				}
			}
			boolean found = jmenu != null;
			if (!found)
				jmenu = new JMenu(name);
			JMenuBuilder<JMenu> builder = new JMenuBuilder<>(jmenu, getUntil());
			subMenu.accept(builder);
			if (!found)
				addMenuItem((JMenu) builder.getComponent());
			return (B) this;
		}

		@Override
		default B withAction(String name, ObservableAction action, Consumer<ButtonEditor<JMenuItem, ?>> ui) {
			JMenuItem item = new JMenuItem(name);
			ButtonEditor<JMenuItem, ?> button = new PanelPopulationImpl.SimpleButtonEditor<>(name, item, name, action, false, getUntil());
			if (ui != null) {
				ui.accept(button);
			}
			addMenuItem((JMenuItem) button.getComponent());
			return (B) this;
		}

		@Override
		default B withCheckBoxMenuItem(String name, SettableValue<Boolean> value,
			Consumer<ButtonEditor<JCheckBoxMenuItem, ?>> ui) {
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
			ButtonEditor<JCheckBoxMenuItem, ?> button = new PanelPopulationImpl.SimpleButtonEditor<>(name, item, name,
				ObservableAction.DO_NOTHING, false, getUntil());
			if (ui != null) {
				ui.accept(button);
			}
			Subscription sub = ObservableSwingUtils.checkFor(item, button.getTooltip(), value);
			getUntil().take(1).act(__ -> sub.unsubscribe());
			addMenuItem((JMenuItem) button.getComponent());
			return (B) this;
		}
	}

	public static class JMenuBuilder<M extends JMenu> extends SimpleButtonEditor<M, JMenuBuilder<M>>
	implements AbstractMenuBuilder<M, JMenuBuilder<M>> {
		public JMenuBuilder(M button, Observable<?> until) {
			super((String) null, button, button.getText(), ObservableAction.nullAction(), false, until);
		}

		@Override
		public void addMenuItem(JMenuItem item) {
			getEditor().add(item);
		}
	}
}
