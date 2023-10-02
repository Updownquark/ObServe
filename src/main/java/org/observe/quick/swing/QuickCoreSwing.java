package org.observe.quick.swing;

import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.*;
import org.observe.quick.QuickTextElement.QuickTextStyle;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingBorder;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingDialog;
import org.observe.quick.swing.QuickSwingPopulator.QuickSwingEventListener;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ComponentPropertyManager;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.Transformer.Builder;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.ex.CheckedExceptionWrapper;

/** Quick interpretation of the core toolkit for Swing */
public class QuickCoreSwing implements QuickInterpretation {
	private static class QuickSwingComponentData {
		final QuickWidget widget;
		final ComponentPropertyManager<Component> propertyMgr;

		QuickSwingComponentData(QuickWidget widget, Component c) {
			this.widget = widget;
			propertyMgr = new ComponentPropertyManager<>(c);
		}
	}

	private static final WeakHashMap<Component, QuickSwingComponentData> QUICK_SWING_WIDGETS = new WeakHashMap<>();

	private static int isRendering;

	public static Transaction rendering() {
		isRendering++;
		return () -> isRendering--;
	}

	public static boolean isRendering() {
		return isRendering > 0;
	}

	@Override
	public void configure(Builder<ExpressoInterpretationException> tx) {
		initMouseListening();
		tx.with(QuickDocument.Interpreted.class, QuickApplication.class, (interpretedDoc, tx2) -> {
			QuickSwingPopulator<QuickWidget> interpretedBody = tx2.transform(interpretedDoc.getBody(), QuickSwingPopulator.class);
			return new QuickApplication() {
				@Override
				public void runApplication(QuickDocument doc, Observable<?> until) throws ModelInstantiationException {
					try {
						EventQueue.invokeAndWait(() -> {
							QuickWindow window = doc.getAddOn(QuickWindow.class);
							WindowBuilder<?, ?> w = WindowPopulation.populateWindow(new JFrame(), until, true, true);
							if (window != null) {
								switch (window.getCloseAction()) {
								case DoNothing:
									w.withCloseAction(JFrame.DO_NOTHING_ON_CLOSE);
									break;
								case Hide:
									w.withCloseAction(JFrame.HIDE_ON_CLOSE);
									break;
								case Dispose:
									w.withCloseAction(JFrame.DISPOSE_ON_CLOSE);
									break;
								case Exit:
									w.withCloseAction(JFrame.EXIT_ON_CLOSE);
									break;
								}
								if (window.getX() != null)
									w.withX(window.getX());
								if (window.getY() != null)
									w.withY(window.getY());
								if (window.getWidth() != null)
									w.withWidth(window.getWidth());
								if (window.getHeight() != null)
									w.withHeight(window.getHeight());
								if (window.getTitle() != null)
									w.withTitle(window.getTitle());
								if (window.isVisible() != null)
									w.withVisible(window.isVisible());
							}
							w.withHContent(new JustifiedBoxLayout(true).mainJustified().crossJustified(), content -> {
								try {
									interpretedBody.populate(content, doc.getBody());
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							});
							w.run(null);
						});
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (InvocationTargetException e) {
						if (e.getTargetException() instanceof CheckedExceptionWrapper
							&& e.getTargetException().getCause() instanceof ModelInstantiationException)
							throw (ModelInstantiationException) e.getTargetException().getCause();
						doc.reporting().error("Unhandled error", e);
					} catch (RuntimeException | Error e) {
						doc.reporting().error("Unhandled error", e);
					}
				}

				@Override
				public void update(QuickDocument doc) throws ModelInstantiationException {
					// TODO Auto-generated method stub
				}
			};
		});
		QuickSwingPopulator.modifyForWidget(tx, QuickWidget.Interpreted.class, (qw, qsp, tx2) -> {
			boolean renderer = qw.getAddOn(QuickRenderer.Interpreted.class) != null;
			List<QuickSwingEventListener<QuickEventListener>> listeners = BetterList.of2(//
				qw.getEventListeners().stream(), //
				l -> tx2.transform(l, QuickSwingEventListener.class));
			QuickSwingBorder border = tx2.transform(qw.getBorder(), QuickSwingBorder.class);
			String name = qw.getName();
			Map<Object, QuickSwingDialog<QuickDialog>> dialogs;
			if (qw.getDialogs().isEmpty())
				dialogs = Collections.emptyMap();
			else if (renderer) {
				qw.reporting().error("Dialogs are not supported for renderers in this implementation");
				dialogs = Collections.emptyMap();
			} else {
				dialogs = new HashMap<>();
				for (QuickDialog.Interpreted<?> dialog : ((QuickWidget.Interpreted<?>) qw).getDialogs())
					dialogs.put(dialog.getIdentity(), tx2.transform(dialog, QuickSwingDialog.class));
			}
			qsp.addModifier((comp, w) -> {
				comp.withName(name);
				FontAdjuster pmDecorator = new FontAdjuster();
				ComponentPropertyManager<Component>[] propertyManager = new ComponentPropertyManager[1];
				Component[] component = new Component[1];
				ObservableValue<Color> color = w.getStyle().getColor();
				ObservableValue<Cursor> cursor = w.getStyle().getMouseCursor().map(quickCursor -> {
					try {
						return quickCursor == null ? null : tx2.transform(quickCursor, Cursor.class);
					} catch (ExpressoInterpretationException e) {
						w.reporting().error("Supported cursor: " + quickCursor, e);
						return null;
					}
				});
				try {
					comp.modifyComponent(c -> {
						boolean[] firstTime = new boolean[1];
						QuickSwingComponentData scd = QUICK_SWING_WIDGETS.computeIfAbsent(c, c2 -> {
							firstTime[0] = true;
							return new QuickSwingComponentData(w, c2);
						});
						if (!renderer && !firstTime[0])
							return;

						component[0] = c;
						propertyManager[0] = scd.propertyMgr;
						if (renderer) {
							// We can just do all this dynamically for renderers
							adjustFont(pmDecorator.reset(), w.getStyle());
							propertyManager[0].setFont(pmDecorator::adjust);
							propertyManager[0].setForeground(pmDecorator.getForeground());
							Color bg = color.get();
							// if (c instanceof JLabel) { // DEBUGGING
							// System.out.println("Render '" + ((JLabel) c).getText() + "' bg " + Colors.toString(bg));
							// }
							propertyManager[0].setBackground(bg);
							propertyManager[0].setOpaque(bg == null ? null : true);
							c.setCursor(cursor.get());
						} else {
							propertyManager[0].setForeground(pmDecorator.getForeground());
							try {
								w.setContext(new QuickWidget.WidgetContext.Default(//
									new MouseValueSupport(c, "hovered", null), //
									new FocusSupport(c), //
									new MouseValueSupport(c, "pressed", true), //
									new MouseValueSupport(c, "rightPressed", false)));

								for (int i = 0; i < listeners.size(); i++)
									listeners.get(i).addListener(c, w.getEventListeners().get(i));

								if (!dialogs.isEmpty()) {
									for (QuickDialog dialog : w.getDialogs()) {
										QuickSwingDialog<QuickDialog> swingDialog = dialogs.get(dialog.getIdentity());
										swingDialog.initialize(dialog, c, Observable.or(dialog.onDestroy(), comp.getUntil()));
									}
								}
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						}
					});
					if (w.getTooltip() != null)
						comp.withTooltip(w.getTooltip());
					if (!renderer)
						comp.visibleWhen(w.isVisible());
					if (border != null) {
						comp.decorate(deco2 -> {
							try {
								border.decorate(deco2, w.getBorder(), component);
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
					}
				} catch (CheckedExceptionWrapper e) {
					if (e.getCause() instanceof ModelInstantiationException)
						throw (ModelInstantiationException) e.getCause();
					throw e;
				}
				if (!renderer) { // Don't keep any subscriptions for renderers
					adjustFont(pmDecorator, w.getStyle());
					cursor.noInitChanges().takeUntil(comp.getUntil()).act(evt -> component[0].setCursor(evt.getNewValue()));
					Observable.onRootFinish(Observable.or(color.noInitChanges(), fontChanges(w.getStyle()))).act(__ -> {
						adjustFont(pmDecorator.reset(), w.getStyle());
						if (propertyManager[0] != null) {
							propertyManager[0].setFont(pmDecorator::adjust);
							propertyManager[0].setForeground(pmDecorator.getForeground());
							propertyManager[0].setBackground(color.get());
						}
						if (component[0] != null) {
							if (!renderer && !isRendering())
								component[0].repaint();
						}
					});
				}
			});
		});
		tx.with(MouseCursor.StandardCursors.class, Cursor.class, (quickCursor, tx2) -> {
			switch (quickCursor) {
			case DEFAULT:
				return Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
			case CROSSHAIR:
				return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
			case HAND:
				return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
			case MOVE:
				return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
			case TEXT:
				return Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
			case WAIT:
				return Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
			case RESIZE_N:
				return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
			case RESIZE_E:
				return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
			case RESIZE_S:
				return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
			case RESIZE_W:
				return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
			case RESIZE_NE:
				return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
			case RESIZE_SE:
				return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
			case RESIZE_SW:
				return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
			case RESIZE_NW:
				return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
			default:
				throw new ExpressoInterpretationException("Unhandled standard cursor: " + quickCursor, null, 0);
			}
		});
		tx.with(QuickBorder.LineBorder.Interpreted.class, QuickSwingBorder.class, (iBorder, tx2) -> {
			return (deco, border, component) -> {
				ObservableValue<Color> color = border.getStyle().getBorderColor().map(c -> c != null ? c : Color.black);
				ObservableValue<Integer> thick = border.getStyle().getBorderThickness().map(t -> t != null ? t : 1);
				deco.withLineBorder(color.get(), thick.get(), false);
				Observable.or(color.noInitChanges(), thick.noInitChanges()).act(__ -> {
					deco.withLineBorder(color.get(), thick.get(), false);
					if (component[0] != null && !isRendering())
						component[0].repaint();
				});
			};
		});
		tx.with(QuickBorder.TitledBorder.Interpreted.class, QuickSwingBorder.class, (iBorder, tx2) -> {
			return (deco, border, component) -> {
				QuickBorder.TitledBorder titled = (QuickBorder.TitledBorder) border;
				ObservableValue<Color> color = titled.getStyle().getBorderColor().map(c -> c != null ? c : Color.black);
				ObservableValue<Integer> thick = titled.getStyle().getBorderThickness().map(t -> t != null ? t : 1);
				ObservableValue<String> title = titled.getTitle();
				Runnable[] revert = new Runnable[1];
				FontAdjuster font = new FontAdjuster();
				adjustFont(font, titled.getStyle());
				revert[0] = deco.withTitledBorder(title.get(), color.get(), font);
				Observable.onRootFinish(
					Observable.or(color.noInitChanges(), thick.noInitChanges(), title.noInitChanges(), fontChanges(titled.getStyle())))
				.act(__ -> {
					revert[0].run();
					adjustFont(font.reset(), titled.getStyle());
					revert[0] = deco.withTitledBorder(title.get(), color.get(), font);
					// This call will just modify the thickness of the titled border
					deco.withLineBorder(color.get(), thick.get(), false);
					if (component[0] != null && !isRendering())
						component[0].repaint();
				});
			};
		});
		tx.with(QuickMouseListener.QuickMouseButtonListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
			return (component, ql) -> {
				SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<QuickMouseListener.MouseButton> button = SettableValue.build(QuickMouseListener.MouseButton.class)
					.build();
				SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
				SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();

				QuickMouseListener.QuickMouseButtonListener mbl = (QuickMouseListener.QuickMouseButtonListener) ql;
				QuickMouseListener.MouseButton listenerButton = mbl.getButton();
				mbl.setListenerContext(
					new QuickMouseListener.MouseButtonListenerContext.Default(altPressed, ctrlPressed, shiftPressed, x, y, button));
				if (mbl instanceof QuickMouseListener.QuickMouseClickListener) {
					int clickCount = ((QuickMouseListener.QuickMouseClickListener) mbl).getClickCount();
					component.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent evt) {
							QuickMouseListener.MouseButton eventButton = checkMouseEventType(evt, listenerButton);
							if (eventButton == null)
								return;
							else if (clickCount > 0 && evt.getClickCount() != clickCount)
								return;
							button.set(eventButton, evt);
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							if (!ql.testFilter())
								return;
							ql.getAction().act(evt);
						}
					});
				} else if (mbl instanceof QuickMouseListener.QuickMousePressedListener) {
					component.addMouseListener(new MouseAdapter() {
						@Override
						public void mousePressed(MouseEvent evt) {
							QuickMouseListener.MouseButton eventButton = checkMouseEventType(evt, listenerButton);
							if (eventButton == null)
								return;
							button.set(eventButton, evt);
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							if (!ql.testFilter())
								return;
							ql.getAction().act(evt);
						}
					});
				} else if (mbl instanceof QuickMouseListener.QuickMouseReleasedListener) {
					component.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseReleased(MouseEvent evt) {
							QuickMouseListener.MouseButton eventButton = checkMouseEventType(evt, listenerButton);
							if (eventButton == null)
								return;
							button.set(eventButton, evt);
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							if (!ql.testFilter())
								return;
							ql.getAction().act(evt);
						}
					});
				} else
					throw new ModelInstantiationException("Unrecognized mouse button listener type: " + mbl.getClass().getName(),
						mbl.reporting().getPosition(), 0);
			};
		});
		tx.with(QuickMouseListener.QuickMouseMoveListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
			return (component, ql) -> {
				SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
				SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();

				QuickMouseListener.QuickMouseMoveListener mml = (QuickMouseListener.QuickMouseMoveListener) ql;
				mml.setListenerContext(
					new QuickMouseListener.MouseListenerContext.Default(altPressed, ctrlPressed, shiftPressed, x, y));
				switch (mml.getEventType()) {
				case Move:
					component.addMouseMotionListener(new MouseAdapter() {
						@Override
						public void mouseMoved(MouseEvent evt) {
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							if (!ql.testFilter())
								return;
							ql.getAction().act(evt);
						}
					});
					break;
				case Enter:
					component.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseEntered(MouseEvent evt) {
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							if (!ql.testFilter())
								return;
							ql.getAction().act(evt);
						}
					});
					break;
				case Exit:
					component.addMouseListener(new MouseAdapter() {
						@Override
						public void mouseExited(MouseEvent evt) {
							altPressed.set(evt.isAltDown(), evt);
							ctrlPressed.set(evt.isControlDown(), evt);
							shiftPressed.set(evt.isShiftDown(), evt);
							x.set(evt.getX(), evt);
							y.set(evt.getY(), evt);
							if (!ql.testFilter())
								return;
							ql.getAction().act(evt);
						}
					});
					break;
				default:
					throw new ModelInstantiationException("Unrecognized mouse move event type: " + mml.getEventType(),
						mml.reporting().getPosition(), 0);
				}
			};
		});
		tx.with(QuickMouseListener.QuickScrollListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
			return (component, ql) -> {
				SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Integer> x = SettableValue.build(int.class).withValue(0).build();
				SettableValue<Integer> y = SettableValue.build(int.class).withValue(0).build();
				SettableValue<Integer> scrollAmount = SettableValue.build(int.class).withValue(0).build();

				QuickMouseListener.QuickScrollListener sl = (QuickMouseListener.QuickScrollListener) ql;
				sl.setListenerContext(
					new QuickMouseListener.ScrollListenerContext.Default(altPressed, ctrlPressed, shiftPressed, x, y, scrollAmount));
				component.addMouseWheelListener(new MouseAdapter() {
					@Override
					public void mouseWheelMoved(MouseWheelEvent evt) {
						altPressed.set(evt.isAltDown(), evt);
						ctrlPressed.set(evt.isControlDown(), evt);
						shiftPressed.set(evt.isShiftDown(), evt);
						x.set(evt.getX(), evt);
						y.set(evt.getY(), evt);
						scrollAmount.set(evt.getUnitsToScroll(), evt);
						if (!ql.testFilter())
							return;
						ql.getAction().act(evt);
					}
				});
			};
		});
		tx.with(QuickKeyListener.QuickKeyTypedListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
			return (component, ql) -> {
				SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Character> charTyped = SettableValue.build(char.class).withValue((char) 0).build();
				QuickKeyListener.QuickKeyTypedListener tl = (QuickKeyListener.QuickKeyTypedListener) ql;
				tl.setListenerContext(new QuickKeyListener.KeyTypedContext.Default(altPressed, ctrlPressed, shiftPressed, charTyped));
				component.addKeyListener(new KeyAdapter() {
					@Override
					public void keyTyped(KeyEvent evt) {
						if (tl.getCharFilter() != 0 && evt.getKeyChar() != tl.getCharFilter())
							return;
						altPressed.set(evt.isAltDown(), evt);
						ctrlPressed.set(evt.isControlDown(), evt);
						shiftPressed.set(evt.isShiftDown(), evt);
						charTyped.set(evt.getKeyChar(), evt);
						if (!ql.testFilter())
							return;
						ql.getAction().act(evt);
					}
				});
			};
		});
		tx.with(QuickKeyListener.QuickKeyCodeListener.Interpreted.class, QuickSwingEventListener.class, (qil, tx2) -> {
			return (component, ql) -> {
				SettableValue<Boolean> altPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> ctrlPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<Boolean> shiftPressed = SettableValue.build(boolean.class).withValue(false).build();
				SettableValue<KeyCode> keyCode = SettableValue.build(KeyCode.class).build();
				QuickKeyListener.QuickKeyCodeListener kl = (QuickKeyListener.QuickKeyCodeListener) ql;
				kl.setListenerContext(new QuickKeyListener.KeyCodeContext.Default(altPressed, ctrlPressed, shiftPressed, keyCode));
				component.addKeyListener(new KeyAdapter() {
					@Override
					public void keyPressed(KeyEvent e) {
						keyEvent(e, true);
					}

					@Override
					public void keyReleased(KeyEvent e) {
						keyEvent(e, false);
					}

					private void keyEvent(KeyEvent evt, boolean pressed) {
						if (pressed != kl.isPressed())
							return;
						KeyCode code = getKeyCodeFromAWT(evt.getKeyCode(), evt.getKeyLocation());
						if (code == null)
							return;
						if (kl.getKeyCode() != null && code != kl.getKeyCode())
							return;
						altPressed.set(evt.isAltDown(), evt);
						ctrlPressed.set(evt.isControlDown(), evt);
						shiftPressed.set(evt.isShiftDown(), evt);
						keyCode.set(code, evt);
						if (!ql.testFilter())
							return;
						ql.getAction().act(evt);
					}
				});
			};
		});
	}

	public static ToIntFunction<Point> getTextOffset(Component component) {
		return getTextOffset(component, null, component::getWidth, component::getGraphics);
	}

	public static ToIntFunction<Point> getTextOffset(Component component, Supplier<String> textGetter, IntSupplier width,
		Graphics graphics) {
		return getTextOffset(component, textGetter, width, () -> graphics);
	}

	public static ToIntFunction<Point> getTextOffset(Component component, Supplier<String> textGetter, IntSupplier width,
		Supplier<Graphics> graphics) {
		if (component instanceof JTextComponent)
			return ((JTextComponent) component)::viewToModel;
		else if (component instanceof JLabel) {
			// TODO This needs a lot of perfecting as well as filling in the gaps
			JLabel label = (JLabel) component;
			return pos -> {
				if (label.getAccessibleContext().getAccessibleText() != null) {
					if (textGetter != null)
						label.setText(textGetter.get());
					int idx = label.getAccessibleContext().getAccessibleText().getIndexAtPoint(pos);
					if (idx > 0)
						return idx;
				}
				String text = textGetter == null ? label.getText() : textGetter.get();
				if (text == null || text.length() <= 1)
					return 0;
				Graphics g = graphics.get();
				if (!(g instanceof Graphics2D))
					return 0;
				FontRenderContext ctx = ((Graphics2D) g).getFontRenderContext();
				int availableLength = width.getAsInt() - label.getInsets().left - label.getInsets().right;
				if (label.getIcon() != null)
					availableLength -= label.getIcon().getIconWidth() - label.getIconTextGap();
				if (availableLength <= 0)
					return 0;

				int textOffset = label.getInsets().left;
				// Apologies, I just can't figure out how to determine whether the label is right-to-left right now
				boolean rtl = false;
				int align = label.getHorizontalTextPosition();
				if (label.getIcon() != null && (align == SwingConstants.LEFT || ((align == SwingConstants.TRAILING) == rtl)))
					textOffset += label.getIcon().getIconWidth() + label.getIconTextGap();
				return getPosition(//
					pos.x - textOffset, label.getFont(), text, ctx);
			};
		} else
			return null;
	}

	private static int getPosition(int x, Font font, String text, FontRenderContext ctx) {
		if (x <= 2)
			return 0;
		int totalWidth = (int) Math.round(font.getStringBounds(text, ctx).getWidth());
		if (x >= totalWidth - 1)
			return text.length() - 1;
		int min = 0, max = text.length() - 1;
		int guess = Math.round(x * 1.0f / totalWidth * text.length());
		if (guess >= max)
			return max;
		int pos = guess;
		while (min < max) {
			int width = (int) Math.round(font.getStringBounds(text, 0, guess, ctx).getWidth());
			if (width < x) {
				min = guess + 1;
				pos = max;
			} else if (width > x) {
				max = guess - 1;
				pos = max;
			} else
				return guess;
			guess = (max + min) / 2;
		}
		return pos;
	}

	private static int getPositionFromEnd(int x, Font font, String text, FontRenderContext ctx) {
		int min = 0, max = text.length() - 1;
		int mid = text.length() / 2;
		int pos = mid;
		while (min < max) {
			int width = (int) Math.round(font.getStringBounds(text, mid, text.length(), ctx).getWidth());
			if (width < x) {
				max = mid - 1;
				pos = max;
			} else if (width > x) {
				min = mid + 1;
				pos = mid;
			} else
				return mid;
			mid = (max + min) / 2;
		}
		return pos;
	}

	public static QuickMouseListener.MouseButton checkMouseEventType(MouseEvent evt, QuickMouseListener.MouseButton listenerButton) {
		QuickMouseListener.MouseButton eventButton;
		if (SwingUtilities.isLeftMouseButton(evt))
			eventButton = QuickMouseListener.MouseButton.Left;
		else if (SwingUtilities.isRightMouseButton(evt))
			eventButton = QuickMouseListener.MouseButton.Right;
		else if (SwingUtilities.isMiddleMouseButton(evt))
			eventButton = QuickMouseListener.MouseButton.Middle;
		else
			return null; // I dunno, can't handle it
		if (listenerButton != null && eventButton != listenerButton)
			return null;
		return eventButton;
	}

	/**
	 * @param keyCode The key code (see java.awt.KeyEvent.VK_*, {@link KeyEvent#getKeyCode()})
	 * @param keyLocation The key's location (see java.awt.KeyEvent.KEY_LOCATION_*, {@link KeyEvent#getKeyLocation()}
	 * @return The Quick key code for the AWT codes
	 */
	public static KeyCode getKeyCodeFromAWT(int keyCode, int keyLocation) {
		switch (keyCode) {
		case KeyEvent.VK_ENTER:
			return KeyCode.ENTER;
		case KeyEvent.VK_BACK_SPACE:
			return KeyCode.BACKSPACE;
		case KeyEvent.VK_TAB:
			return KeyCode.TAB;
		case KeyEvent.VK_CANCEL:
			return KeyCode.CANCEL;
		case KeyEvent.VK_CLEAR:
			return KeyCode.CLEAR;
		case KeyEvent.VK_SHIFT:
			if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
				return KeyCode.SHIFT_LEFT;
			else
				return KeyCode.SHIFT_RIGHT;
		case KeyEvent.VK_CONTROL:
			if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
				return KeyCode.CTRL_LEFT;
			else
				return KeyCode.CTRL_RIGHT;
		case KeyEvent.VK_ALT:
			if (keyLocation == KeyEvent.KEY_LOCATION_LEFT)
				return KeyCode.ALT_LEFT;
			else
				return KeyCode.ALT_RIGHT;
		case KeyEvent.VK_PAUSE:
			return KeyCode.PAUSE;
		case KeyEvent.VK_CAPS_LOCK:
			return KeyCode.CAPS_LOCK;
		case KeyEvent.VK_ESCAPE:
			return KeyCode.ESCAPE;
		case KeyEvent.VK_SPACE:
			return KeyCode.SPACE;
		case KeyEvent.VK_PAGE_UP:
			return KeyCode.PAGE_UP;
		case KeyEvent.VK_PAGE_DOWN:
			return KeyCode.PAGE_DOWN;
		case KeyEvent.VK_END:
			return KeyCode.END;
		case KeyEvent.VK_HOME:
			return KeyCode.HOME;
		case KeyEvent.VK_LEFT:
			return KeyCode.LEFT_ARROW;
		case KeyEvent.VK_UP:
			return KeyCode.UP_ARROW;
		case KeyEvent.VK_RIGHT:
			return KeyCode.RIGHT_ARROW;
		case KeyEvent.VK_DOWN:
			return KeyCode.DOWN_ARROW;
		case KeyEvent.VK_COMMA:
		case KeyEvent.VK_LESS:
			return KeyCode.COMMA;
		case KeyEvent.VK_MINUS:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_MINUS;
			else
				return KeyCode.MINUS;
		case KeyEvent.VK_UNDERSCORE:
			return KeyCode.MINUS;
		case KeyEvent.VK_PERIOD:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_DOT;
			else
				return KeyCode.DOT;
		case KeyEvent.VK_GREATER:
			return KeyCode.DOT;
		case KeyEvent.VK_SLASH:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_SLASH;
			else
				return KeyCode.FORWARD_SLASH;
		case KeyEvent.VK_0:
		case KeyEvent.VK_RIGHT_PARENTHESIS:
			return KeyCode.NUM_0;
		case KeyEvent.VK_1:
		case KeyEvent.VK_EXCLAMATION_MARK:
			return KeyCode.NUM_1;
		case KeyEvent.VK_2:
		case KeyEvent.VK_AT:
			return KeyCode.NUM_2;
		case KeyEvent.VK_3:
		case KeyEvent.VK_NUMBER_SIGN:
			return KeyCode.NUM_3;
		case KeyEvent.VK_4:
		case KeyEvent.VK_DOLLAR:
			return KeyCode.NUM_4;
		case KeyEvent.VK_5:
			return KeyCode.NUM_5;
		case KeyEvent.VK_6:
		case KeyEvent.VK_CIRCUMFLEX:
			return KeyCode.NUM_6;
		case KeyEvent.VK_7:
		case KeyEvent.VK_AMPERSAND:
			return KeyCode.NUM_7;
		case KeyEvent.VK_8:
		case KeyEvent.VK_ASTERISK:
			return KeyCode.NUM_8;
		case KeyEvent.VK_9:
		case KeyEvent.VK_LEFT_PARENTHESIS:
			return KeyCode.NUM_9;
		case KeyEvent.VK_SEMICOLON:
		case KeyEvent.VK_COLON:
			return KeyCode.SEMICOLON;
		case KeyEvent.VK_EQUALS:
			if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD)
				return KeyCode.PAD_EQUAL;
			else
				return KeyCode.EQUAL;
		case KeyEvent.VK_A:
			return KeyCode.A;
		case KeyEvent.VK_B:
			return KeyCode.B;
		case KeyEvent.VK_C:
			return KeyCode.C;
		case KeyEvent.VK_D:
			return KeyCode.D;
		case KeyEvent.VK_E:
			return KeyCode.E;
		case KeyEvent.VK_F:
			return KeyCode.F;
		case KeyEvent.VK_G:
			return KeyCode.G;
		case KeyEvent.VK_H:
			return KeyCode.H;
		case KeyEvent.VK_I:
			return KeyCode.I;
		case KeyEvent.VK_J:
			return KeyCode.J;
		case KeyEvent.VK_K:
			return KeyCode.K;
		case KeyEvent.VK_L:
			return KeyCode.L;
		case KeyEvent.VK_M:
			return KeyCode.M;
		case KeyEvent.VK_N:
			return KeyCode.N;
		case KeyEvent.VK_O:
			return KeyCode.O;
		case KeyEvent.VK_P:
			return KeyCode.P;
		case KeyEvent.VK_Q:
			return KeyCode.Q;
		case KeyEvent.VK_R:
			return KeyCode.R;
		case KeyEvent.VK_S:
			return KeyCode.S;
		case KeyEvent.VK_T:
			return KeyCode.T;
		case KeyEvent.VK_U:
			return KeyCode.U;
		case KeyEvent.VK_V:
			return KeyCode.V;
		case KeyEvent.VK_W:
			return KeyCode.W;
		case KeyEvent.VK_X:
			return KeyCode.X;
		case KeyEvent.VK_Y:
			return KeyCode.Y;
		case KeyEvent.VK_Z:
			return KeyCode.Z;
		case KeyEvent.VK_OPEN_BRACKET:
		case KeyEvent.VK_BRACELEFT:
			return KeyCode.LEFT_BRACE;
		case KeyEvent.VK_BACK_SLASH:
			return KeyCode.BACK_SLASH;
		case KeyEvent.VK_CLOSE_BRACKET:
		case KeyEvent.VK_BRACERIGHT:
			return KeyCode.RIGHT_BRACE;
		case KeyEvent.VK_NUMPAD0:
			return KeyCode.PAD_0;
		case KeyEvent.VK_NUMPAD1:
			return KeyCode.PAD_1;
		case KeyEvent.VK_NUMPAD2:
		case KeyEvent.VK_KP_DOWN:
			return KeyCode.PAD_2;
		case KeyEvent.VK_NUMPAD3:
			return KeyCode.PAD_3;
		case KeyEvent.VK_NUMPAD4:
		case KeyEvent.VK_KP_LEFT:
			return KeyCode.PAD_4;
		case KeyEvent.VK_NUMPAD5:
			return KeyCode.PAD_5;
		case KeyEvent.VK_NUMPAD6:
		case KeyEvent.VK_KP_RIGHT:
			return KeyCode.PAD_6;
		case KeyEvent.VK_NUMPAD7:
			return KeyCode.PAD_7;
		case KeyEvent.VK_NUMPAD8:
		case KeyEvent.VK_KP_UP:
			return KeyCode.PAD_8;
		case KeyEvent.VK_NUMPAD9:
			return KeyCode.PAD_9;
		case KeyEvent.VK_MULTIPLY:
			return KeyCode.PAD_MULTIPLY;
		case KeyEvent.VK_ADD:
			return KeyCode.PAD_PLUS;
		case KeyEvent.VK_SEPARATOR:
			return KeyCode.PAD_SEPARATOR;
		case KeyEvent.VK_SUBTRACT:
			return KeyCode.PAD_MINUS;
		case KeyEvent.VK_DECIMAL:
			return KeyCode.PAD_DOT;
		case KeyEvent.VK_DIVIDE:
			return KeyCode.PAD_SLASH;
		case KeyEvent.VK_DELETE:
			return KeyCode.PAD_BACKSPACE;
		case KeyEvent.VK_NUM_LOCK:
			return KeyCode.NUM_LOCK;
		case KeyEvent.VK_SCROLL_LOCK:
			return KeyCode.SCROLL_LOCK;
		case KeyEvent.VK_F1:
			return KeyCode.F1;
		case KeyEvent.VK_F2:
			return KeyCode.F2;
		case KeyEvent.VK_F3:
			return KeyCode.F3;
		case KeyEvent.VK_F4:
			return KeyCode.F4;
		case KeyEvent.VK_F5:
			return KeyCode.F5;
		case KeyEvent.VK_F6:
			return KeyCode.F6;
		case KeyEvent.VK_F7:
			return KeyCode.F7;
		case KeyEvent.VK_F8:
			return KeyCode.F8;
		case KeyEvent.VK_F9:
			return KeyCode.F9;
		case KeyEvent.VK_F10:
			return KeyCode.F10;
		case KeyEvent.VK_F11:
			return KeyCode.F11;
		case KeyEvent.VK_F12:
			return KeyCode.F12;
		case KeyEvent.VK_F13:
			return KeyCode.F13;
		case KeyEvent.VK_F14:
			return KeyCode.F14;
		case KeyEvent.VK_F15:
			return KeyCode.F15;
		case KeyEvent.VK_F16:
			return KeyCode.F16;
		case KeyEvent.VK_F17:
			return KeyCode.F17;
		case KeyEvent.VK_F18:
			return KeyCode.F18;
		case KeyEvent.VK_F19:
			return KeyCode.F19;
		case KeyEvent.VK_F20:
			return KeyCode.F20;
		case KeyEvent.VK_F21:
			return KeyCode.F21;
		case KeyEvent.VK_F22:
			return KeyCode.F22;
		case KeyEvent.VK_F23:
			return KeyCode.F23;
		case KeyEvent.VK_F24:
			return KeyCode.F24;
		case KeyEvent.VK_PRINTSCREEN:
			return KeyCode.PRINT_SCREEN;
		case KeyEvent.VK_INSERT:
			return KeyCode.INSERT;
		case KeyEvent.VK_HELP:
			return KeyCode.HELP;
		case KeyEvent.VK_META:
			return KeyCode.META;
		case KeyEvent.VK_BACK_QUOTE:
			return KeyCode.BACK_QUOTE;
		case KeyEvent.VK_QUOTE:
		case KeyEvent.VK_QUOTEDBL:
			return KeyCode.QUOTE;
		case KeyEvent.VK_WINDOWS:
			return KeyCode.COMMAND_KEY;
		case KeyEvent.VK_CONTEXT_MENU:
			return KeyCode.CONTEXT_MENU;
		default:
			return null;
		}
	}

	public static void adjustFont(FontAdjuster font, QuickTextStyle style) {
		Color color = style.getFontColor().get();
		if (color != null)
			font.withForeground(color);
		Double size = style.getFontSize().get();
		if (size != null)
			font.withFontSize(size.floatValue());
		Double weight = style.getFontWeight().get();
		if (weight != null)
			font.withFontWeight(weight.floatValue());
		Double slant = style.getFontSlant().get();
		if (slant != null)
			font.withFontSlant(slant.floatValue());
		Boolean underline = style.isUnderline().get();
		if (Boolean.TRUE.equals(underline))
			font.underline();
		Boolean strikeThrough = style.isStrikeThrough().get();
		if (Boolean.TRUE.equals(strikeThrough))
			font.strikethrough();
		Boolean superScript = style.isSuperScript().get();
		if (Boolean.TRUE.equals(superScript))
			font.deriveFont(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUPER);
		Boolean subScript = style.isSubScript().get();
		if (Boolean.TRUE.equals(subScript))
			font.deriveFont(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);
	}

	static Observable<Causable> fontChanges(QuickTextStyle style) {
		return Observable.or(style.getFontColor().noInitChanges(), style.getFontSize().noInitChanges(),
			style.getFontWeight().noInitChanges(), style.getFontSlant().noInitChanges(), style.isUnderline().noInitChanges(),
			style.isStrikeThrough().noInitChanges(), style.isSuperScript().noInitChanges(), style.isSubScript().noInitChanges());
	}

	private static boolean isMouseListening;
	private static volatile Point theMouseLocation;
	private static volatile boolean isLeftPressed;
	private static volatile boolean isRightPressed;

	private void initMouseListening() {
		boolean ml = isMouseListening;
		if (ml)
			return;
		synchronized (QuickCoreSwing.class) {
			ml = isMouseListening;
			if (ml)
				return;
			Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
				MouseEvent mouse = (MouseEvent) event;
				theMouseLocation = mouse.getLocationOnScreen();
				switch (mouse.getID()) {
				case MouseEvent.MOUSE_PRESSED:
					isLeftPressed |= SwingUtilities.isLeftMouseButton(mouse);
					isRightPressed |= SwingUtilities.isRightMouseButton(mouse);
					break;
				case MouseEvent.MOUSE_RELEASED:
					if (SwingUtilities.isLeftMouseButton(mouse))
						isLeftPressed = false;
					if (SwingUtilities.isRightMouseButton(mouse))
						isRightPressed = false;
					break;
				}
			}, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
			isMouseListening = true;
		}
	}

	static class ComponentIdentity {
		private final Component theComponent;

		ComponentIdentity(Component component) {
			theComponent = component;
		}

		@Override
		public int hashCode() {
			return theComponent.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof QuickCoreSwing.ComponentIdentity && theComponent.equals(((QuickCoreSwing.ComponentIdentity) obj).theComponent);
		}

		@Override
		public String toString() {
			String name = theComponent.getName();
			if (name != null)
				return theComponent.getClass().getSimpleName() + ":" + name;
			else
				return theComponent.getClass().getSimpleName();
		}
	}

	static class MouseValueSupport extends ObservableValue.LazyObservableValue<Boolean>
	implements SettableValue<Boolean>, MouseListener {
		private final Component theParent;
		private final String theName;
		private final Boolean theButton;
		private BiConsumer<Boolean, Object> theListener;
		private boolean isListening;

		public MouseValueSupport(Component parent, String name, Boolean button) {
			super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
			theParent = parent;
			theName = name;
			theButton = button;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(new ComponentIdentity(theParent), theName);
		}

		@Override
		protected Boolean getSpontaneous() {
			if (theParent == null)
				return false;
			boolean compVisible;
			if (theParent instanceof JComponent)
				compVisible = ((JComponent) theParent).isShowing();
			else
				compVisible = theParent.isVisible();
			if (!compVisible)
				return false;
			if (theButton == null) { // No button filter
			} else if (theButton.booleanValue()) { // Left
				if (!isLeftPressed)
					return false;
			} else { // Right
				if (!isRightPressed)
					return false;
			}
			Point screenPos;
			try {
				screenPos = theParent.getLocationOnScreen();
			} catch (IllegalComponentStateException e) {
				return false;
			}
			if (screenPos == null)
				return false;
			Point mousePos = theMouseLocation;
			if (mousePos == null || mousePos.x < screenPos.x || mousePos.y < screenPos.y)
				return false;
			if (mousePos.x >= screenPos.x + theParent.getWidth() || mousePos.y >= screenPos.y + theParent.getHeight())
				return false;
			Component child = theParent.getComponentAt(mousePos.x - screenPos.x, mousePos.y - screenPos.y);
			// If the mouse is over one of our visible Quick-sourced children, then we're not clicked ourselves
			while (child != null && child != theParent && (!child.isVisible() || QUICK_SWING_WIDGETS.get(child) == null))
				child = child.getParent();
			return child == null || child == theParent;
		}

		@Override
		protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
			theListener = listener;
			setListening(true);
			return () -> setListening(false);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Boolean set(Boolean value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String isAcceptable(Boolean value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}

		private void setListening(boolean listening) {
			if (listening == isListening)
				return;
			if (listening && theListener == null)
				return;
			isListening = listening;
			setListening(theParent, listening);
			if (!listening)
				theListener = null;
		}

		private void setListening(Component component, boolean listening) {
			if (listening)
				component.addMouseListener(this);
			else
				component.removeMouseListener(this);
			if (component instanceof Container) {
				for (Component child : ((Container) component).getComponents()) {
					if (QUICK_SWING_WIDGETS.get(child) == null)
						setListening(child, listening);
				}
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
				return;
			} else if (theButton.booleanValue()) { // Left
				if (!SwingUtilities.isLeftMouseButton(e))
					return;
			} else { // Right
				if (!SwingUtilities.isRightMouseButton(e))
					return;
			}
			fire(e, true);
		}

		private void fire(MouseEvent e, boolean pressOrEnter) {
			int offsetX = 0, offsetY = 0;
			Component parent = e.getComponent();
			while (parent != null && parent != theParent) {
				offsetX += parent.getX();
				offsetY += parent.getY();
				parent = parent.getParent();
			}
			if (parent != null)
				e.translatePoint(offsetX, offsetY);
			try {
				theListener.accept(pressOrEnter, e);
			} finally {
				if (parent != null)
					e.translatePoint(-offsetX, -offsetY);
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
				return;
			} else if (theButton.booleanValue()) { // Left
				if (!SwingUtilities.isLeftMouseButton(e))
					return;
			} else { // Right
				if (!SwingUtilities.isRightMouseButton(e))
					return;
			}
			fire(e, false);
		}

		@Override
		public void mouseEntered(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
			} else if (theButton.booleanValue()) { // Left
				if (!isLeftPressed)
					return;
			} else { // Right
				if (!isRightPressed)
					return;
			}
			fire(e, true);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			if (theListener == null)
				return;
			if (theButton == null) { // No button filter
			} else if (theButton.booleanValue()) { // Left
				if (!isLeftPressed)
					return;
			} else { // Right
				if (!isRightPressed)
					return;
			}
			fire(e, false);
		}

		@Override
		public void mouseClicked(MouseEvent e) { // No state change due to clicked
		}
	}

	class FocusSupport extends ObservableValue.LazyObservableValue<Boolean> implements SettableValue<Boolean>, FocusListener {
		private final Component theComponent;
		private BiConsumer<Boolean, Object> theListener;
		private boolean isListening;

		FocusSupport(Component component) {
			super(TypeTokens.get().BOOLEAN, Transactable.noLock(ThreadConstraint.EDT));
			theComponent = component;
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theComponent, "focused");
		}

		@Override
		protected Boolean getSpontaneous() {
			return theComponent.isFocusOwner();
		}

		@Override
		protected Subscription subscribe(BiConsumer<Boolean, Object> listener) {
			theListener = listener;
			setListening(true);
			return () -> setListening(false);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Boolean set(Boolean value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (value.booleanValue()) {
				if (theComponent.isFocusable())
					throw new IllegalArgumentException("This component cannot be focused");
				else if (!theComponent.isFocusOwner())
					theComponent.requestFocus();
			} else if (theComponent.isFocusOwner()) {
				Window w = SwingUtilities.getWindowAncestor(theComponent);
				if (w != null)
					w.requestFocus();
			}
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String isAcceptable(Boolean value) {
			if (value.booleanValue() && !theComponent.isFocusable())
				return "This component cannot be focused";
			return null;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}

		private void setListening(boolean listening) {
			if (listening == isListening)
				return;
			if (listening && theListener == null)
				return;
			isListening = listening;
			if (listening)
				theComponent.addFocusListener(this);
			else if (theComponent != null)
				theComponent.removeFocusListener(this);
			if (!listening)
				theListener = null;
		}

		@Override
		public void focusGained(FocusEvent e) {
			if (theListener != null)
				theListener.accept(true, e);
		}

		@Override
		public void focusLost(FocusEvent e) {
			if (theListener != null)
				theListener.accept(false, e);
		}
	}

	public static void scrollTo(Component component, Rectangle bounds) {
		Container parent = component.getParent();
		while (parent != null && component.isVisible()) {
			if (parent instanceof JViewport) {
				JViewport vp = (JViewport) parent;
				Point viewPos = vp.getViewPosition();
				bounds.x -= viewPos.x;
				bounds.y -= viewPos.y;
				vp.scrollRectToVisible(bounds);
			}
			bounds.x += component.getX();
			bounds.y += component.getY();
			component = parent;
			parent = parent.getParent();
		}
	}
}