package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
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

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.RootPaneContainer;
import javax.swing.WindowConstants;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.util.TypeTokens;
import org.observe.util.swing.PanelPopulation.DialogBuilder;
import org.observe.util.swing.PanelPopulation.Iconized;
import org.observe.util.swing.PanelPopulation.MenuBarBuilder;
import org.observe.util.swing.PanelPopulation.MenuBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.Tooltipped;
import org.observe.util.swing.PanelPopulation.UiAction;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
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
			PanelPopulator<?, ?> populator = PanelPopulation.populateVPanel(null, theUntil);
			content.accept(populator);
			return withContent(populator.getContainer());
		}

		@Override
		public P withHContent(LayoutManager layout, Consumer<PanelPopulator<?, ?>> content) {
			PanelPopulator<?, ?> populator = PanelPopulation.populateHPanel(null, layout, theUntil);
			content.accept(populator);
			return withContent(populator.getContainer());
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
			theWindow.setLocationRelativeTo(relativeTo);
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
							if (w != null && (w.get() == null || w.get() != theWindow.getWidth()))
								w.set(theWindow.getWidth(), evt);
							if (h != null && (h.get() == null || h.get() != theWindow.getHeight()))
								h.set(theWindow.getHeight(), evt);
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
						bounds.x = x.get();
					}
					if (y != null && y.get() != null) {
						boundsObs.add(y.noInitChanges());
						bounds.y = y.get();
					}
					if (w != null && w.get() != null) {
						boundsObs.add(w.noInitChanges());
						bounds.width = w.get();
					}
					if (h != null && h.get() != null) {
						boundsObs.add(h.noInitChanges());
						bounds.height = h.get();
					}
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
				visible.changes().takeUntil(theUntil).act(evt -> theWindow.setVisible(evt.getNewValue()));
			} else
				theWindow.setVisible(true);
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
		public M withMenu(String menuName, Consumer<MenuBuilder<?>> menu) {
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
			JMenuBuilder<?> builder = new JMenuBuilder<>(jmenu, theUntil);
			menu.accept(builder);
			builder.install();
			if (!found)
				theMenuBar.add(jmenu);
			return (M) this;
		}
	}

	static abstract class AbstractMenuThingBuilder<M extends AbstractMenuThingBuilder<M>> implements Iconized<M>, Tooltipped<M> {
		protected final Observable<?> theUntil;
		private ObservableValue<Boolean> theVisibility;
		private ObservableValue<String> theDisabled;
		private ObservableValue<String> theText;
		private ObservableValue<String> theTooltip;
		private ObservableValue<? extends Icon> theIcon;

		AbstractMenuThingBuilder(Observable<?> until) {
			theUntil = until;
		}

		protected abstract JComponent getComponent();

		protected abstract void setText(String text);

		protected abstract void setIcon(Icon icon);

		@Override
		public M withIcon(ObservableValue<? extends Icon> icon) {
			theIcon = icon;
			return (M) this;
		}

		public M visibleWhen(ObservableValue<Boolean> visible) {
			theVisibility = visible;
			return (M) this;
		}

		public M decorate(Consumer<ComponentDecorator> decoration) {
			ComponentDecorator deco = new ComponentDecorator();
			decoration.accept(deco);
			deco.decorate(getComponent());
			return (M) this;
		}

		public M disableWith(ObservableValue<String> disabled) {
			theDisabled = disabled;
			return (M) this;
		}

		@Override
		public M withTooltip(ObservableValue<String> tooltip) {
			theTooltip = tooltip;
			return (M) this;
		}

		public M withText(ObservableValue<String> text) {
			theText = text;
			return (M) this;
		}

		void install() {
			JComponent c = getComponent();
			if (theVisibility != null)
				theVisibility.changes().takeUntil(theUntil).act(evt -> c.setVisible(evt.getNewValue()));
			if (theDisabled != null)
				theDisabled.changes().takeUntil(theUntil).act(evt -> c.setEnabled(evt.getNewValue() == null));
			if (theText != null)
				theText.changes().takeUntil(theUntil).act(evt -> setText(evt.getNewValue()));
			if (theIcon != null)
				theIcon.changes().takeUntil(theUntil).act(evt -> setIcon(evt.getNewValue()));
			ObservableValue<String> tooltip;
			if (theDisabled != null) {
				if (theTooltip != null)
					tooltip = ObservableValue.firstValue(TypeTokens.get().STRING, s -> s != null, () -> null, theDisabled, theTooltip);
				else
					tooltip = theDisabled;
			} else
				tooltip = theTooltip;
			if (tooltip != null)
				tooltip.changes().takeUntil(theUntil).act(evt -> c.setToolTipText(evt.getNewValue()));
		}
	}

	static class JMenuBuilder<M extends JMenuBuilder<M>> extends AbstractMenuThingBuilder<M> implements MenuBuilder<M> {
		private final JMenu theMenu;

		JMenuBuilder(JMenu menu, Observable<?> until) {
			super(until);
			theMenu = menu;
		}

		@Override
		protected JComponent getComponent() {
			return theMenu;
		}

		@Override
		protected void setText(String text) {
			theMenu.setText(text);
		}

		@Override
		protected void setIcon(Icon icon) {
			theMenu.setIcon(icon);
		}

		@Override
		public M withSubMenu(String name, Consumer<MenuBuilder<?>> subMenu) {
			JMenu jmenu = null;
			for (int m = 0; m < theMenu.getMenuComponentCount(); m++) {
				if (theMenu.getMenuComponent(m) instanceof JMenu && ((JMenu) theMenu.getMenuComponent(m)).getText().equals(name)) {
					jmenu = (JMenu) theMenu.getMenuComponent(m);
					break;
				}
			}
			boolean found = jmenu != null;
			if (!found)
				jmenu = new JMenu(name);
			JMenuBuilder<?> builder = new JMenuBuilder<>(jmenu, theUntil);
			subMenu.accept(builder);
			builder.install();
			if (!found)
				theMenu.add(jmenu);
			return (M) this;
		}

		@Override
		public M withAction(String name, Consumer<Object> action, Consumer<UiAction<?>> ui) {
			JMenuItem item = new JMenuItem(name);
			item.addActionListener(evt -> action.accept(evt));
			if (ui != null) {
				JMenuItemBuilder<?> itemBuilder = new JMenuItemBuilder<>(item, theUntil);
				ui.accept(itemBuilder);
				itemBuilder.install();
			}
			theMenu.add(item);
			return (M) this;
		}
	}

	static class JMenuItemBuilder<M extends JMenuItemBuilder<M>> extends AbstractMenuThingBuilder<M> implements UiAction<M> {
		private final JMenuItem theItem;

		JMenuItemBuilder(JMenuItem item, Observable<?> until) {
			super(until);
			theItem = item;
		}

		@Override
		protected JComponent getComponent() {
			return theItem;
		}

		@Override
		protected void setText(String text) {
			theItem.setText(text);
		}

		@Override
		protected void setIcon(Icon icon) {
			theItem.setIcon(icon);
		}
	}
}
