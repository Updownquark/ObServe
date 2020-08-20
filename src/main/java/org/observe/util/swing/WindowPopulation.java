package org.observe.util.swing;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
import java.awt.Frame;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.RootPaneContainer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.util.swing.PanelPopulation.DialogBuilder;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
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
		private final SimpleObservable<Object> theDisposeOnClose;
		private ObservableValue<String> theTitle;
		private SettableValue<Integer> theX;
		private SettableValue<Integer> theY;
		private SettableValue<Integer> theWidth;
		private SettableValue<Integer> theHeight;
		private SettableValue<Boolean> isVisible;

		DefaultWindowBuilder(W window, Observable<?> until, boolean disposeOnClose) {
			theWindow = window;
			if (disposeOnClose) {
				theDisposeOnClose = SimpleObservable.build().safe(false).build();
				theUntil = Observable.or(until.takeUntil(theDisposeOnClose), theDisposeOnClose);
			} else {
				theDisposeOnClose = null;
				theUntil = until;
			}
		}

		@Override
		public W getWindow() {
			return theWindow;
		}

		protected Observable<?> getUntil() {
			return theUntil;
		}

		@Override
		public P withTitle(ObservableValue<String> title) {
			theTitle = title;
			return (P) this;
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
					theTitle.changes().takeUntil(theUntil).act(evt -> ((Frame) theWindow).setTitle(evt.getNewValue()));
				else if (theWindow instanceof Dialog)
					theTitle.changes().takeUntil(theUntil).act(evt -> ((Dialog) theWindow).setTitle(evt.getNewValue()));
				else
					System.err.println(
						"Title configured, but window type " + theWindow.getClass().getName() + " is not a recognized titled window");
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
						try (Transaction t = Causable.use(evt)) {
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
						try (Transaction t = Causable.use(evt)) {
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
			if (visible != null || theDisposeOnClose != null) {
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
						if (theDisposeOnClose != null)
							theDisposeOnClose.onNext(e);
					}
				});
			}
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
			super.run(relativeTo);
			if (theModality != null)
				theModality.changes().takeUntil(getUntil()).act(evt -> getWindow().setModalityType(evt.getNewValue()));
			return (P) this;
		}
	}
}
