package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.KeyCode;
import org.observe.quick.Positionable;
import org.observe.quick.QuickEventListener;
import org.observe.quick.QuickInterpretation;
import org.observe.quick.QuickKeyListener;
import org.observe.quick.QuickMouseListener;
import org.observe.quick.QuickMouseListener.MouseButton;
import org.observe.quick.QuickMouseListener.MouseMoveEventType;
import org.observe.quick.QuickMouseListener.QuickMouseButtonListener;
import org.observe.quick.QuickSize;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.Sizeable;
import org.observe.quick.draw.QuickBorderedShape;
import org.observe.quick.draw.QuickCanvas;
import org.observe.quick.draw.QuickDrawText;
import org.observe.quick.draw.QuickEllipse;
import org.observe.quick.draw.QuickFlexLine;
import org.observe.quick.draw.QuickLine;
import org.observe.quick.draw.QuickLinearShape;
import org.observe.quick.draw.QuickPoint;
import org.observe.quick.draw.QuickPolygon;
import org.observe.quick.draw.QuickRectangle;
import org.observe.quick.draw.QuickRotated;
import org.observe.quick.draw.QuickShape;
import org.observe.quick.draw.QuickShapeCollection;
import org.observe.quick.draw.QuickShapeContainer;
import org.observe.quick.draw.QuickShapePublisher;
import org.observe.quick.draw.QuickShapeView;
import org.observe.quick.draw.QuickSimpleShape;
import org.observe.quick.draw.Rotate;
import org.observe.quick.draw.Scale;
import org.observe.quick.draw.StrokeDashing;
import org.observe.quick.draw.TransformOp;
import org.observe.quick.draw.Translate;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.BiTuple;
import org.qommons.Colors;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.Transformer;
import org.qommons.collect.BetterHashSet;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MappedList;
import org.qommons.io.ErrorReporting;

/** Swing implementation for Quick-Draw */
public class QuickDrawSwing implements QuickInterpretation {
	/**
	 * The transformation type required for a {@link QuickShapePublisher} to be drawn by {@link QuickDrawSwing}
	 *
	 * @param <P> The type of shape publisher
	 */
	public interface InterpretedQuickShapePublisher<P extends QuickShapePublisher> {
		/**
		 * @param element The instantiated publisher element
		 * @return The instantiated shape publisher
		 * @throws ModelInstantiationException If the publisher cannot be instantiated
		 */
		QuickDrawShapePublisher interpret(P element) throws ModelInstantiationException;
	}

	/** Instantiated shape publisher class provided and understood by {@link QuickDrawSwing} implementations */
	public interface QuickDrawShapePublisher {
		/** @return The set of shapes this publisher exposes */
		ObservableCollection<QuickShapeInterpretation> getShapes();
	}

	/**
	 * An interpreted transformation operation
	 *
	 * @param <T> The type of the Quick tranformation this interpretation is for
	 */
	public interface InterpretedTransformOp<T extends TransformOp> {
		/**
		 * @param element The instantiated Quick transformation
		 * @return The transformation implementation
		 * @throws ModelInstantiationException If the transformation could not be understood
		 */
		QuickDrawTransform interpret(T element) throws ModelInstantiationException;
	}

	/** Implementation of a Quick transformation in this interpretation */
	public interface QuickDrawTransform {
		/** @param source The transform to modify with this operation */
		void transform(AffineTransform source);

		/** @return Any change sources in this transform */
		Observable<?> update();
	}

	/** A qualitative representation of opacity */
	public enum Opacity {
		/** Completely transparent */
		None,
		/** Partially transparent */
		Partial,
		/** Completely opaque */
		Full;

		/**
		 * @param other Another opacity
		 * @return The maximum of the two opacities
		 */
		public Opacity or(Opacity other) {
			return QommonsUtils.max(this, other);
		}
	}

	/** An individual shape implementation for {@link QuickDrawSwing} */
	public interface QuickShapeInterpretation {
		/** @return An observable that will cause this shape to be redrawn */
		Observable<?> update();

		/**
		 * Draws this shape to a graphics configuration
		 *
		 * @param gfx The graphics to draw to
		 * @param screen The screen bounds
		 */
		void draw(Graphics2D gfx, Rectangle screen);

		/**
		 * @param containerPoint The point in the container to test
		 * @return The corresponding location in this shape, or null if this shape does not contain the given point
		 */
		Point hit(Point containerPoint);

		/**
		 * @param point The point in this shape
		 * @return This shape's opacity at the given point
		 */
		Opacity getOpacity(Point point);

		/**
		 * @param e The mouse event
		 * @return The deepest-level shape at the event's location
		 */
		QuickShapeInterpretation mouseEntered(MouseEvent e);

		/**
		 * @param e The mouse event
		 * @return The deepest-level shape at the event's location
		 */
		QuickShapeInterpretation mouseMoved(MouseEvent e);

		/** @param e The mouse event */
		void mouseDragged(MouseEvent e);

		/** @param e The mouse event */
		void mouseExited(MouseEvent e);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mousePressed(MouseEvent e);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mouseReleased(MouseEvent e);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mouseClicked(MouseEvent e);

		/**
		 * @param e The mouse event
		 * @return This shape's opacity at the event's location
		 */
		Opacity mouseWheelMoved(MouseWheelEvent e);

		/**
		 * @param e The key event
		 * @return Whether this shape was able to use the event
		 */
		boolean keyPressed(KeyEvent e);

		/**
		 * @param e The key event
		 * @return Whether this shape was able to use the event
		 */
		boolean keyReleased(KeyEvent e);

		/**
		 * @param e The key event
		 * @return Whether this shape was able to use the event
		 */
		boolean keyTyped(KeyEvent e);

		/** @return This shape's tooltip at the most recent hovered location */
		String getTooltip();

		/**
		 * @param hovered Whether the mouse cursor is currently hovered over this shape
		 * @param focused Whether this shape is the focus
		 * @param pressed Whether the left mouse button is currently pressed over this shape
		 * @param rightPressed Whether the right mouse button is currently pressed over this shape
		 */
		void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed);
	}

	@Override
	public void configure(Transformer.Builder<ExpressoInterpretationException> tx) {
		tx.with(QuickCanvas.Interpreted.class, QuickSwingPopulator.class, QuickCanvasPopulator::new);
		tx.with(QuickShapeCollection.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedShapeCollection::new);
		tx.with(QuickRectangle.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedRectangle::new);
		tx.with(QuickEllipse.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedEllipse::new);
		tx.with(QuickPolygon.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedPolygon::new);
		tx.with(QuickDrawText.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedText::new);
		tx.with(QuickLine.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedLine::new);
		tx.with(QuickFlexLine.Interpreted.class, InterpretedQuickShapePublisher.class, InterpretedFlexLine::new);
		tx.with(QuickShapeView.Interpreted.class, InterpretedShapeView.class, InterpretedShapeView::new);
		tx.with(Translate.Interpreted.class, InterpretedTransformOp.class, InterpretedTranslate::new);
		tx.with(Scale.Interpreted.class, InterpretedTransformOp.class, InterpretedScale::new);
		tx.with(Rotate.Interpreted.class, InterpretedTransformOp.class, InterpretedRotate::new);
	}

	static class InterpretedShapeContainer {
		private final Map<Object, InterpretedQuickShapePublisher<?>> thePublishers;

		public InterpretedShapeContainer(ExElement.Interpreted<?> interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			thePublishers = new HashMap<>();
			for (QuickShapePublisher.Interpreted<?> shape : interpreted.getAddOn(QuickShapeContainer.Interpreted.class).getShapes()) {
				thePublishers.put(shape.getIdentity(), tx.transform(shape, InterpretedQuickShapePublisher.class));
			}
		}

		public List<QuickDrawShapePublisher> getPublishers(ExElement quick) throws ModelInstantiationException {
			List<QuickDrawShapePublisher> publishers = new ArrayList<>(thePublishers.size());
			for (QuickShapePublisher p : quick.getAddOn(QuickShapeContainer.class).getShapes()) {
				InterpretedQuickShapePublisher<?> interpP = thePublishers.get(p.getIdentity());
				if (interpP != null)
					publishers.add(((InterpretedQuickShapePublisher<QuickShapePublisher>) interpP).interpret(p));
			}
			return publishers;
		}
	}

	static abstract class SimpleShapeContainer implements QuickShapeInterpretation, QuickDrawShapePublisher {
		private final ObservableCollection<QuickShapeInterpretation> theContents;
		private final Observable<?> theUpdate;
		private final BetterSet<ElementId> theHovered;
		private boolean isPressed;
		private boolean isRightPressed;
		private boolean isDrawing;

		SimpleShapeContainer(List<QuickDrawShapePublisher> publishers, Observable<?> until) {
			theContents = ObservableCollection.of(publishers).flow()//
				.flatMap(pub -> pub.getShapes().flow())//
				.refreshEach(shape -> shape.update().filter(__ -> !isDrawing))//
				.collectActive(until);
			theUpdate = Observable.onRootFinish(theContents.simpleChanges());
			theHovered = BetterHashSet.build().build();
		}

		protected abstract ListElement<QuickShapeInterpretation> getFocus();

		protected abstract void setFocus(ListElement<QuickShapeInterpretation> shape);

		protected boolean isFocused(ListElement<QuickShapeInterpretation> shape) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			return focus != null && shape.getElementId().equals(focus.getElementId());
		}

		public boolean isPressed() {
			return isPressed;
		}

		public boolean isRightPressed() {
			return isRightPressed;
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return getContents();
		}

		public ObservableCollection<QuickShapeInterpretation> getContents() {
			return theContents;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			isDrawing = true;
			try {
				for (ListElement<QuickShapeInterpretation> shape = theContents.getTerminalElement(true); shape != null; shape = shape
					.getAdjacent(true)) {
					setState(shape);
					shape.get().draw(gfx, screen);
				}
			} finally {
				isDrawing = false;
			}
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e) {
			return mouseHover(e);
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e) {
			return mouseHover(e);
		}

		protected void setState(ListElement<QuickShapeInterpretation> shapeEl) {
			boolean hovered = theHovered.contains(shapeEl.getElementId());
			shapeEl.get().setState(hovered, isFocused(shapeEl), hovered && isPressed, hovered && isRightPressed);
		}

		protected QuickShapeInterpretation mouseHover(MouseEvent e) {
			theHovered.removeIf(el -> !el.isPresent());
			QuickShapeInterpretation first = null;
			boolean done = false;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); el != null; el = el.getAdjacent(false)) {
				setState(el);
				Point hit = el.get().hit(e.getPoint());
				if (done && theHovered.remove(el.getElementId())) {
					try (Transaction t = translateForHit(e, hit)) {
						setState(el);
						el.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
					}
				} else if (hit != null) {
					try (Transaction t = translateForHit(e, hit)) {
						boolean newHover = theHovered.add(el.getElementId());
						setState(el);
						if (newHover) {
							QuickShapeInterpretation target = el.get().mouseEntered(asType(e, MouseEvent.MOUSE_ENTERED));
							if (first == null)
								first = target;
						} else {
							QuickShapeInterpretation target = el.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED));
							if (first == null)
								first = target;
						}
					}
					done = el.get().getOpacity(hit) == Opacity.Full;
				} else if (theHovered.remove(el.getElementId())) {
					setState(el);
					el.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
				}
			}
			return first;
		}

		protected MouseEvent asType(MouseEvent source, int newId) {
			if (source.getID() == newId)
				return source;
			return new MouseEvent(source.getComponent(), MouseEvent.MOUSE_EXITED, source.getWhen(), source.getModifiers(), source.getX(),
				source.getY(), source.getClickCount(), source.isPopupTrigger(), source.getButton());
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			mouseAction(e, QuickShapeInterpretation::mouseDragged);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			isPressed = isRightPressed = true;
			Iterator<ElementId> hovered = theHovered.iterator();
			while (hovered.hasNext()) {
				ElementId el = hovered.next();
				if (!el.isPresent())
					continue;
				ListElement<QuickShapeInterpretation> shape = theContents.getElement(el);
				setState(shape);
				Point hit = shape.get().hit(e.getPoint());
				hovered.remove();
				try (Transaction t = translateForHit(e, hit)) {
					setState(shape);
					shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
				}
			}
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = true;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = true;
			ListElement<QuickShapeInterpretation> focus = null;
			Opacity opacity = Opacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				Point hit = shape.hit(e.getPoint());
				if (hit == null)
					continue;
				Opacity shapeOpacity;
				try (Transaction t = translateForHit(e, hit)) {
					shapeOpacity = shape.mousePressed(e);
				}
				if (shapeOpacity != Opacity.None)
					focus = el;
				opacity = opacity.or(shapeOpacity);
			}
			setFocus(focus);
			return opacity;
		}

		@Override
		public Opacity mouseReleased(MouseEvent e) {
			if (SwingUtilities.isLeftMouseButton(e))
				isPressed = false;
			else if (SwingUtilities.isRightMouseButton(e))
				isRightPressed = false;
			return mouseAction(e, QuickShapeInterpretation::mouseReleased);
		}

		@Override
		public Opacity mouseClicked(MouseEvent e) {
			return mouseAction(e, QuickShapeInterpretation::mouseClicked);
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e) {
			return mouseAction(e, QuickShapeInterpretation::mouseWheelMoved);
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyPressed(e);
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyReleased(e);
			return true;
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			ListElement<QuickShapeInterpretation> focus = getFocus();
			if (focus == null)
				return false;
			setState(focus);
			focus.get().keyTyped(e);
			return true;
		}

		@Override
		public Point hit(Point containerPoint) {
			return containerPoint;
		}

		@Override
		public Opacity getOpacity(Point point) {
			Opacity opacity = Opacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (shape == null)
					continue;
				setState(el);
				Point hit = shape.hit(point);
				if (hit == null)
					continue;
				opacity = opacity.or(shape.getOpacity(hit));
			}
			return opacity;
		}

		public <E extends MouseEvent> Opacity mouseAction(E e, BiConsumer<QuickShapeInterpretation, E> action) {
			Opacity opacity = Opacity.None;
			for (ListElement<QuickShapeInterpretation> el = theContents.getTerminalElement(false); //
				opacity != Opacity.Full && el != null; el = el.getAdjacent(false)) {
				QuickShapeInterpretation shape = el.get();
				if (e.isConsumed())
					break;
				else if (shape == null)
					continue;
				setState(el);
				Point hit = shape.hit(e.getPoint());
				if (hit == null)
					continue;
				try (Transaction t = translateForHit(e, hit)) {
					action.accept(shape, e);
				}
				opacity = opacity.or(shape.getOpacity(hit));
			}
			return opacity;
		}

		@Override
		public String getTooltip() {
			theHovered.removeIf(el -> !el.isPresent());
			for (ElementId hovered : theHovered.reverse()) {
				ListElement<QuickShapeInterpretation> shape = getShapes().getElement(hovered);
				setState(shape);
				String tooltip = shape.get().getTooltip();
				if (tooltip != null)
					return tooltip;
			}
			return null;
		}

		@Override
		public void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed) {
			isPressed = pressed;
			isRightPressed = rightPressed;
			if (!focused)
				setFocus(null);
		}
	}

	/**
	 * Translates a mouse event's point to a new location
	 *
	 * @param event The event to translate
	 * @param hit The new hit point
	 * @return A transaction to revert the change
	 */
	public static Transaction translateForHit(MouseEvent event, Point hit) {
		if (hit == null || (hit.x == event.getX() && hit.y == event.getY()))
			return Transaction.NONE;
		int xDiff = hit.x - event.getX();
		int yDiff = hit.y - event.getY();
		event.translatePoint(xDiff, yDiff);
		return () -> event.translatePoint(-xDiff, -yDiff);
	}

	/**
	 * @param point The point to transform
	 * @param transform The transformation
	 * @return The transformed point
	 */
	public static Point transform(Point point, AffineTransform transform) {
		if (transform == null)
			return point;
		Point2D.Float transformed = (Point2D.Float) transform.transform(new Point2D.Float(point.x, point.y), new Point2D.Float(0, 0));
		return new Point(Math.round(transformed.x), Math.round(transformed.y));
	}

	static class QuickCanvasPopulator extends QuickSwingPopulator.Abstract<QuickCanvas> {
		private final InterpretedShapeContainer thePublishing;

		QuickCanvasPopulator(QuickCanvas.Interpreted canvas, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			thePublishing = new InterpretedShapeContainer(canvas, tx);
		}

		@Override
		protected void doPopulate(PanelPopulator<?, ?> panel, QuickCanvas quick, Consumer<ComponentEditor<?, ?>> component)
			throws ModelInstantiationException {
			panel.addComponent(null, new QuickCanvasComponent(quick, thePublishing.getPublishers(quick), panel.getUntil()),
				component::accept);
		}
	}

	static class QuickCanvasComponent extends JComponent {
		private final QuickCanvas theCanvas;
		private final SimpleShapeContainer theContainer;
		private final Sizeable theWidth;
		private final Sizeable theHeight;
		private ListElement<QuickShapeInterpretation> theFocus;

		QuickCanvasComponent(QuickCanvas canvas, List<QuickDrawShapePublisher> publishers, Observable<?> until) {
			theCanvas = canvas;
			theContainer = new SimpleShapeContainer(publishers, canvas.onDestroy()) {
				@Override
				protected ListElement<QuickShapeInterpretation> getFocus() {
					return theFocus;
				}

				@Override
				protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
					theFocus = shape;
				}

				@Override
				public String getTooltip() {
					return null;
				}
			};
			theContainer.update().takeUntil(until).act(__ -> repaint());
			theWidth = canvas.getAddOn(Sizeable.Horizontal.class);
			theHeight = canvas.getAddOn(Sizeable.Vertical.class);
			addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					publishSize();
				}
			});
			publishSize();
			class EventListener extends MouseAdapter implements KeyListener {
				@Override
				public void mouseEntered(MouseEvent e) {
					theContainer.setState(true, false, theCanvas.isPressed().get(), theCanvas.isRightPressed().get());
					QuickShapeInterpretation shape = theContainer.mouseEntered(e);
					String tooltip = shape == null ? theCanvas.getTooltip().get() : shape.getTooltip();
					setToolTipText(tooltip);
				}

				@Override
				public void mouseMoved(MouseEvent e) {
					theContainer.setState(true, false, theCanvas.isPressed().get(), theCanvas.isRightPressed().get());
					QuickShapeInterpretation shape = theContainer.mouseMoved(e);
					String tooltip = shape == null ? theCanvas.getTooltip().get() : shape.getTooltip();
					setToolTipText(tooltip);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					theContainer.mouseDragged(e);
				}

				@Override
				public void mouseExited(MouseEvent e) {
					theContainer.mouseExited(e);
				}

				@Override
				public void mousePressed(MouseEvent e) {
					theContainer.mousePressed(e);
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					theContainer.mouseReleased(e);
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					theContainer.mouseClicked(e);
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent e) {
					theContainer.mouseWheelMoved(e);
				}

				@Override
				public void keyPressed(KeyEvent e) {
					theContainer.keyPressed(e);
				}

				@Override
				public void keyReleased(KeyEvent e) {
					theContainer.keyReleased(e);
				}

				@Override
				public void keyTyped(KeyEvent e) {
					theContainer.keyTyped(e);
				}
			}
			EventListener listener = new EventListener();
			addMouseListener(listener);
			addMouseMotionListener(listener);
			addMouseWheelListener(listener);
		}

		@Override
		public Dimension getPreferredSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getPreferred().get();
			if (size != null)
				w = size.evaluate(getParent().getWidth());
			else
				w = 300;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getPreferred().get();
			if (size != null)
				h = size.evaluate(getParent().getHeight());
			else
				h = 300;
			return new Dimension(w, h);
		}

		@Override
		public Dimension getMinimumSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getMinimum().get();
			if (size != null)
				w = size.evaluate(getParent().getWidth());
			else
				w = 0;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getMinimum().get();
			if (size != null)
				h = size.evaluate(getParent().getHeight());
			else
				h = 0;
			return new Dimension(w, h);
		}

		@Override
		public Dimension getMaximumSize() {
			int w, h;
			QuickSize size = theWidth.getSize().get();
			if (size == null)
				size = theWidth.getMaximum().get();
			if (size != null)
				w = size.evaluate(getParent().getWidth());
			else
				w = Integer.MAX_VALUE;

			size = theHeight.getSize().get();
			if (size == null)
				size = theHeight.getMaximum().get();
			if (size != null)
				h = size.evaluate(getParent().getHeight());
			else
				h = Integer.MAX_VALUE;
			return new Dimension(w, h);
		}

		void publishSize() {
			Integer preW = theCanvas.getPublishWidth().get();
			Integer preH = theCanvas.getPublishHeight().get();
			if ((preW == null || preW.intValue() != getWidth()) && theCanvas.getPublishWidth().isAcceptable(getWidth()) == null)
				theCanvas.getPublishWidth().set(getWidth());

			if ((preH == null || preH.intValue() != getHeight()) && theCanvas.getPublishHeight().isAcceptable(getHeight()) == null)
				theCanvas.getPublishHeight().set(getHeight());
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			theContainer.draw((Graphics2D) g, new Rectangle(0, 0, getWidth(), getHeight()));
		}
	}

	static class InterpretedShapeCollection<T> extends InterpretedShapeContainer
	implements InterpretedQuickShapePublisher<QuickShapeCollection<T>> {
		InterpretedShapeCollection(QuickShapeCollection.Interpreted<T> collection, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(collection, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickShapeCollection<T> element) throws ModelInstantiationException {
			return new QuickShapeCollectionPublisher<>(element, getPublishers(element));
		}
	}

	static class QuickShapeCollectionPublisher<T> extends SimpleShapeContainer {
		private final QuickShapeCollection<T> theCollection;
		private ListElement<T> theFocusElement;
		private ListElement<QuickShapeInterpretation> theFocus;
		private ListElement<T> theCurrentValue;
		private final BetterSet<BiTuple<ElementId, ElementId>> theHovered;
		private boolean isInAction;

		QuickShapeCollectionPublisher(QuickShapeCollection<T> collection, List<QuickDrawShapePublisher> shapes) {
			super(shapes, collection.onDestroy());
			theCollection = collection;
			theHovered = BetterHashSet.build().build();
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Observable<?> update() {
			return Observable.or(super.update(), Observable.onRootFinish(theCollection.getValues().simpleChanges()));
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			isInAction = true;
			try {
				SettableValue<T> activeValue = theCollection.getActiveValue();
				SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
				int i = 0;
				for (ListElement<T> value = theCollection.getValues().getTerminalElement(true); //
					value != null; value = value.getAdjacent(true)) {
					theCurrentValue = value;
					activeValue.set(value.get());
					activeIndex.set(i);
					super.draw(gfx, screen);
					i++;
				}
			} finally {
				isInAction = false;
			}
		}

		@Override
		protected QuickShapeInterpretation mouseHover(MouseEvent e) {
			theHovered.removeIf(el -> !el.getValue1().isPresent() || !el.getValue2().isPresent());
			QuickShapeInterpretation first = null;
			boolean done = false;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					for (CollectionElement<QuickShapeInterpretation> shapeEl = getContents().getTerminalElement(false); shapeEl != null; //
						shapeEl = shapeEl.getAdjacent(false)) {
						Point hit = shapeEl.get().hit(e.getPoint());
						if (done) {
							if (theHovered.remove(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
								try (Transaction t = translateForHit(e, hit)) {
									shapeEl.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
								}
							}
						} else if (hit != null) {
							try (Transaction t = translateForHit(e, hit)) {
								if (theHovered.add(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
									QuickShapeInterpretation target = shapeEl.get().mouseEntered(asType(e, MouseEvent.MOUSE_ENTERED));
									if (first == null)
										first = target;
								} else {
									QuickShapeInterpretation target = shapeEl.get().mouseMoved(asType(e, MouseEvent.MOUSE_MOVED));
									if (first == null)
										first = target;
								}
							}
							done = shapeEl.get().getOpacity(hit) == Opacity.Full;
						} else if (theHovered.remove(new BiTuple<>(valueEl.getElementId(), shapeEl.getElementId()))) {
							shapeEl.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
						}
					}
					i--;
				}
			} finally {
				isInAction = false;
			}
			return first;
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			ListElement<T> focusEl = null;
			Opacity opacity = Opacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != Opacity.Full && valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					Opacity valueOpacity = super.mousePressed(e);
					if (valueOpacity != Opacity.None)
						focusEl = valueEl;
					opacity = opacity.or(valueOpacity);
					i--;
				}
			} finally {
				isInAction = false;
			}
			theFocusElement = focusEl;
			return opacity;
		}

		@Override
		public void mouseExited(MouseEvent e) {
			Iterator<BiTuple<ElementId, ElementId>> hovered = theHovered.iterator();
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			while (hovered.hasNext()) {
				BiTuple<ElementId, ElementId> el = hovered.next();
				if (!el.getValue1().isPresent() || !el.getValue2().isPresent())
					continue;
				ListElement<T> valueEl = theCollection.getValues().getElement(el.getValue1());
				theCurrentValue = valueEl;
				activeValue.set(valueEl.get());
				activeIndex.set(valueEl.getElementsBefore());
				ListElement<QuickShapeInterpretation> shape = getContents().getElement(el.getValue2());
				setState(shape);
				Point hit = shape.get().hit(e.getPoint());
				hovered.remove();
				try (Transaction t = translateForHit(e, hit)) {
					setState(shape);
					shape.get().mouseExited(asType(e, MouseEvent.MOUSE_EXITED));
				}
			}
		}

		@Override
		public <E extends MouseEvent> Opacity mouseAction(E e, BiConsumer<QuickShapeInterpretation, E> action) {
			Opacity opacity = Opacity.None;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			int i = theCollection.getValues().size() - 1;
			isInAction = true;
			try {
				for (ListElement<T> valueEl = theCollection.getValues().getTerminalElement(false); //
					opacity != Opacity.Full && valueEl != null; valueEl = valueEl.getAdjacent(false)) {
					theCurrentValue = valueEl;
					activeValue.set(valueEl.get());
					activeIndex.set(i);
					opacity = opacity.or(super.mouseAction(e, action));
					i--;
				}
			} finally {
				isInAction = false;
			}
			return opacity;
		}

		@Override
		protected ListElement<QuickShapeInterpretation> getFocus() {
			if (!isInAction) {
				if (theFocusElement != null && !theFocusElement.getElementId().isPresent()) {
					theFocusElement = null;
					theFocus = null;
				}
				if (theFocusElement != null) {
					theCurrentValue = theFocusElement;
					theCollection.getActiveValue().set(theFocusElement.get());
					theCollection.getActiveValueIndex().set(theFocusElement.getElementsBefore());
				}
			}
			return theFocus;
		}

		@Override
		protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
			theFocus = shape;
		}

		@Override
		protected boolean isFocused(ListElement<QuickShapeInterpretation> shape) {
			return theFocusElement != null && theCurrentValue != null
				&& theFocusElement.getElementId().equals(theCurrentValue.getElementId()) && theFocus != null
				&& theFocus.getElementId().equals(shape.getElementId());
		}

		@Override
		public String getTooltip() {
			theHovered.removeIf(el -> !el.getValue1().isPresent() || !el.getValue2().isPresent());
			isInAction = true;
			SettableValue<T> activeValue = theCollection.getActiveValue();
			SettableValue<Integer> activeIndex = theCollection.getActiveValueIndex();
			try {
				for (BiTuple<ElementId, ElementId> hovered : theHovered.reverse()) {
					ListElement<T> valueEl = theCollection.getValues().getElement(hovered.getValue1());
					theCurrentValue = valueEl;
					QuickShapeInterpretation shape = getContents().getElement(hovered.getValue2()).get();
					activeValue.set(valueEl.get());
					activeIndex.set(valueEl.getElementsBefore());
					String tooltip = shape.getTooltip();
					if (tooltip != null)
						return tooltip;
				}
			} finally {
				isInAction = false;
			}
			return null;
		}

		@Override
		protected void setState(ListElement<QuickShapeInterpretation> shapeEl) {
			if (theCurrentValue != null) {
				boolean hovered = theHovered.contains(new BiTuple<>(theCurrentValue.getElementId(), shapeEl.getElementId()));
				shapeEl.get().setState(hovered, isFocused(shapeEl), hovered && isPressed(), hovered && isRightPressed());
			}
		}
	}

	static abstract class QuickDrawSingleShape<S extends QuickShape> implements QuickDrawShapePublisher, QuickShapeInterpretation {
		static BiConsumer<QuickEventListener, InputEvent> EVENT_NO_CONFIG = (ql, evt) -> {
		};

		static <L extends QuickEventListener, E extends InputEvent> BiConsumer<L, E> noConfig() {
			return (BiConsumer<L, E>) EVENT_NO_CONFIG;
		}

		private final ObservableValue<Color> theColor;
		private final ObservableValue<Float> theOpacity;
		private final S theShape;
		private final QuickWithBackground.BackgroundContext theBgCtx;
		private final Observable<?> theUpdate;

		protected QuickDrawSingleShape(S shape) {
			theShape = shape;
			theBgCtx = new QuickWithBackground.BackgroundContext.Default();
			theShape.setContext(theBgCtx);
			theColor = shape.getStyle().getColor();
			theOpacity = shape.getStyle().getOpacity();
			theUpdate = Observable.or(theColor.noInitChanges(), theOpacity.noInitChanges());
		}

		public S getShape() {
			return theShape;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		public boolean isVisible() {
			return getShape().isVisible().get();
		}

		public Color getColor() {
			Color color = theColor.get();
			if (color == null)
				color = Color.black;
			return color;
		}

		public float getOpacity() {
			Float opacity = theOpacity.get();
			if (opacity == null)
				return 1.0f;
			return opacity;
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Opacity getOpacity(Point point) {
			if (!isVisible())
				return Opacity.None;
			Color color = theColor.get();
			Float opacity = theOpacity.get();
			if (color == null) {
				if (opacity == null || opacity.floatValue() >= 1.0f)
					return Opacity.Full;
				else if (opacity.floatValue() <= 0.0f)
					return Opacity.None;
				else
					return Opacity.Partial;
			}
			int alpha;
			if (opacity == null || opacity.floatValue() == 1.0f)
				alpha = color.getAlpha();
			else
				alpha = Math.round(color.getAlpha() * opacity.floatValue());
			if (alpha <= 0)
				return Opacity.None;
			else if (alpha >= 255)
				return Opacity.Full;
			else
				return Opacity.Partial;
		}

		protected <E extends InputEvent, L extends QuickEventListener> void input(Class<L> listenerType, Predicate<? super L> filter, E evt,
			BiConsumer<L, E> install) {
			for (QuickEventListener lstnr : theShape.getEventListeners()) {
				if (!listenerType.isInstance(lstnr))
					continue;

				L listener = (L) lstnr;
				if (filter != null && !filter.test(listener))
					continue;

				SettableValue<Boolean> altPressed = listener.isAltPressed();
				SettableValue<Boolean> ctrlPressed = listener.isCtrlPressed();
				SettableValue<Boolean> shiftPressed = listener.isShiftPressed();
				altPressed.set(evt.isAltDown(), evt);
				ctrlPressed.set(evt.isControlDown(), evt);
				shiftPressed.set(evt.isShiftDown(), evt);
				install.accept(listener, evt);
				if (listener.testFilter() && listener.getAction().isEnabled().get() == null)
					listener.getAction().act(evt);
			}
		}

		protected <L extends QuickMouseListener, E extends MouseEvent> Opacity mouse(Class<L> listenerType, Predicate<L> filter, E evt,
			BiConsumer<L, E> install) {
			Point hit = hit(evt.getPoint());
			if (hit == null)
				return Opacity.None;
			try (Transaction t = translateForHit(evt, hit)) {
				input(listenerType, filter, evt, install.andThen((listener, evt2) -> {
					SettableValue<Integer> x = listener.getEventX();
					SettableValue<Integer> y = listener.getEventY();
					x.set(evt2.getX(), evt2);
					y.set(evt2.getY(), evt2);
				}));
			}
			return getOpacity(hit);
		}

		protected Opacity mouseMove(MouseMoveEventType eventType, MouseEvent evt) {
			return mouse(QuickMouseListener.QuickMouseMoveListener.class, mml -> mml.getEventType() == eventType, evt, noConfig());
		}

		protected <L extends QuickMouseButtonListener> Opacity mouseButton(Class<L> listenerType, Predicate<? super L> filter,
			MouseEvent evt, BiConsumer<L, MouseEvent> install) {
			return mouse(listenerType, lstnr -> {
				MouseButton button = QuickCoreSwing.checkMouseEventType(evt, lstnr.getButton());
				if (button == null)
					return false;
				lstnr.getEventButton().set(button, evt);
				if (filter != null && !filter.test(lstnr))
					return false;
				return true;
			}, evt, install);
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e) {
			if (mouseMove(MouseMoveEventType.Enter, e) != Opacity.None)
				return this;
			else
				return null;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e) {
			if (mouseMove(MouseMoveEventType.Move, e) != Opacity.None)
				return this;
			else
				return null;
		}

		@Override
		public void mouseDragged(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
			mouseMove(MouseMoveEventType.Exit, e);
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			return mouseButton(QuickMouseListener.QuickMousePressedListener.class, null, e, noConfig());
		}

		@Override
		public Opacity mouseReleased(MouseEvent e) {
			return mouseButton(QuickMouseListener.QuickMouseReleasedListener.class, null, e, noConfig());
		}

		@Override
		public Opacity mouseClicked(MouseEvent e) {
			return mouseButton(QuickMouseListener.QuickMouseClickListener.class, lstnr -> {
				if (lstnr.getClickCount() > 0 && e.getClickCount() != lstnr.getClickCount())
					return false;
				return true;
			}, e, noConfig());
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e) {
			return mouse(QuickMouseListener.QuickScrollListener.class, null, e, (lstnr, evt) -> {
				lstnr.getScrollAmount().set(evt.getScrollAmount());
			});
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			input(QuickKeyListener.QuickKeyCodeListener.class, lstnr -> {
				if (!lstnr.isPressed())
					return false;
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (lstnr.getKeyCode() != null && lstnr.getKeyCode() != code)
					return false;
				lstnr.getEventKeyCode().set(code, e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			input(QuickKeyListener.QuickKeyCodeListener.class, lstnr -> {
				if (lstnr.isPressed())
					return false;
				KeyCode code = QuickCoreSwing.getKeyCodeFromAWT(e.getKeyCode(), e.getKeyLocation());
				if (lstnr.getKeyCode() != null && lstnr.getKeyCode() != code)
					return false;
				lstnr.getEventKeyCode().set(code, e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			input(QuickKeyListener.QuickKeyTypedListener.class, lstnr -> {
				if (lstnr.getCharFilter() > 0 && lstnr.getCharFilter() != e.getKeyChar())
					return false;
				lstnr.getTypedChar().set(e.getKeyChar(), e);
				return true;
			}, e, noConfig());
			return true;
		}

		@Override
		public String getTooltip() {
			return theShape.getTooltip().get();
		}

		@Override
		public void setState(boolean hovered, boolean focused, boolean pressed, boolean rightPressed) {
			// System.out.println(theShape + ":" + (hovered ? " hovered" : "") + (focused ? " focused" : "") + (pressed ? " pressed" : "")
			// + (rightPressed ? "right-pressed" : ""));
			theBgCtx.isHovered().set(hovered);
			theBgCtx.isFocused().set(focused);
			theBgCtx.isPressed().set(pressed);
			theBgCtx.isRightPressed().set(rightPressed);
		}
	}

	static abstract class QuickDrawBorderedShape<S extends QuickBorderedShape> extends QuickDrawSingleShape<S> {
		private final ObservableValue<Integer> theBorderThickness;
		private final ObservableValue<Color> theBorderColor;
		private final ObservableValue<StrokeDashing> theStrokeDash;
		private final Observable<?> theUpdate;

		protected QuickDrawBorderedShape(S shape) {
			super(shape);
			QuickBorderedShape.QuickBorderedShapeStyle style = shape.getStyle();
			theBorderThickness = style.getBorderThickness();
			theBorderColor = style.getBorderColor();
			theStrokeDash = style.getStrokeDash();
			theUpdate = Observable.or(super.update(), theBorderThickness.noInitChanges(), theBorderColor.noInitChanges(),
				theStrokeDash.noInitChanges(), shape.getRepaint());
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		public int getBorderThickness() {
			Integer thick = theBorderThickness.get();
			return thick == null ? 0 : thick;
		}

		public Color getBorderColor() {
			Color color = theBorderColor.get();
			if (color == null)
				color = Color.black;
			return color;
		}

		public StrokeDashing getStrokeDash() {
			StrokeDashing dash = theStrokeDash.get();
			return dash == null ? StrokeDashing.full : dash;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}
	}

	/** Handles shapes with defined bounds and rotation */
	protected static class SimpleShapeHandling {
		private final QuickShape theShape;
		private final Rectangle theBounds;
		private AffineTransform theRotation;
		private AffineTransform theRotationInverse;

		SimpleShapeHandling(QuickShape shape) {
			theShape = shape;
			theBounds = new Rectangle();
		}

		Rectangle getBounds() {
			return theBounds;
		}

		/** @return The shape's rotation transform */
		public AffineTransform getRotation() {
			return theRotation;
		}

		/** @return The shape's inverse rotation transform */
		public AffineTransform getRotationInverse() {
			return theRotationInverse;
		}

		private static final IntConsumer DO_NOTHING = __ -> {
		};

		/**
		 * @param width The width of the shape
		 * @param height The height of the shape
		 * @param rotation The rotation of the shape
		 * @param screen The screen bounds
		 * @param rotateFirst Whether the rotation (if any) should be done before or after sizing
		 */
		public boolean updateBounds(QuickSize width, QuickSize height, double rotation, Rectangle screen, boolean rotateFirst,
			boolean heightIsTopDown) {
			theRotation = theRotationInverse = null;

			Positionable hPos = theShape.getAddOn(Positionable.Horizontal.class);
			Positionable vPos = theShape.getAddOn(Positionable.Vertical.class);

			// if (rotateFirst)
			// System.out.println(width + "x" + height + " " + hPos.getLeading().get() + ", " + vPos.getCenter().get());
			Point anchor = new Point();
			if (rotateFirst && rotation != 0) {
				theBounds.x = theBounds.y = 0;
				Dimension sourceSize = new Dimension();
				if (width.percent != 0.0f) {
					if (!evaluateDimension(DO_NOTHING, w -> sourceSize.width = w, DO_NOTHING, hPos, width, screen.x, screen.width, "width"))
						return false;
				} else
					sourceSize.width = width.pixels;
				if (height.percent != 0.0f) {
					if (!evaluateDimension(DO_NOTHING, h -> sourceSize.height = h, DO_NOTHING, vPos, height, screen.y, screen.height,
						"height"))
						return false;
				} else
					sourceSize.height = height.pixels;

				int degrees = (int) (rotation / Math.PI * 180);
				boolean flipDims = Math.abs(degrees + 90) % 180 > 45;
				if (flipDims) {
					theBounds.width = sourceSize.height;
					theBounds.height = sourceSize.width;
				} else {
					theBounds.width = sourceSize.width;
					theBounds.height = sourceSize.height;
				}
				if (hPos.getLeading().get() != null)
					anchor.x = 0;
				else if (hPos.getTrailing().get() != null)
					anchor.x = sourceSize.width;
				else if (hPos.getCenter().get() != null)
					anchor.x = sourceSize.width / 2;
				else
					anchor.x = 0;
				if (vPos.getLeading().get() != null)
					anchor.y = 0;
				else if (vPos.getTrailing().get() != null)
					anchor.y = sourceSize.height;
				else if (vPos.getCenter().get() != null)
					anchor.y = sourceSize.height / 2;
				else
					anchor.y = 0;

				theRotation = AffineTransform.getRotateInstance(rotation, sourceSize.width / 2.0, sourceSize.height / 2.0);

				// Rotate the bounds
				Rectangle rotatedBounds = new Rectangle();
				Point2D pt = new Point2D.Double(theBounds.getMinX(), theBounds.getMinY());
				theRotation.transform(pt, pt);
				rotatedBounds.setFrame(pt.getX(), pt.getY(), 0, 0);

				pt.setLocation(theBounds.getMinX(), theBounds.getMaxY());
				theRotation.transform(pt, pt);
				rotatedBounds.add(pt);

				pt.setLocation(theBounds.getMaxX(), theBounds.getMinY());
				theRotation.transform(pt, pt);
				rotatedBounds.add(pt);

				pt.setLocation(theBounds.getMaxX(), theBounds.getMaxY());
				theRotation.transform(pt, pt);
				rotatedBounds.add(pt);

				width = QuickSize.ofPixels((int) Math.round(rotatedBounds.getWidth()));
				height = QuickSize.ofPixels((int) Math.round(rotatedBounds.getHeight()));

				// anchor.x = width.pixels / 2;
				// anchor.y = height.pixels / 2;
				// anchor.x = anchor.y = 0;
			}

			// For post-positioning rotation, the positioning determines the center of the rotation via the anchor.
			// E.g. If the user specifies the center of the shape, the center should stay where it is regardless of rotation.
			// Similarly if the specify the upper left corner, we should rotate around that.
			if (!evaluateDimension(x -> theBounds.x = x, w -> theBounds.width = w, a -> anchor.x += a, hPos, width, screen.x, screen.width,
				"width"))
				return false;
			if (!evaluateDimension(y -> theBounds.y = y, h -> theBounds.height = h, a -> anchor.y += a, vPos, height, screen.y,
				screen.height, "height"))
				return false;

			// if (rotateFirst)
			// System.out.println(rotateFirst + " " + theBounds + " " + anchor);
			if (rotation != 0) {
				if (rotateFirst) {
					AffineTransform tx = AffineTransform.getTranslateInstance(theBounds.x + anchor.x, theBounds.y + anchor.y);
					tx.concatenate(theRotation);
					theRotation = tx;
				} else {
					theRotation = AffineTransform.getTranslateInstance(theBounds.x, theBounds.y);
					theRotation.rotate(rotation, anchor.x - theBounds.x, anchor.y - theBounds.y);
				}
				theBounds.x = theBounds.y = 0;
			}

			if (theRotation != null) {
				try {
					theRotationInverse = theRotation.createInverse();
				} catch (NoninvertibleTransformException e) {
					theShape.reporting().error(e.getMessage(), e);
				}
			}
			return true;
		}

		private boolean evaluateDimension(IntConsumer setPosition, IntConsumer setSize, IntConsumer setAnchor, Positionable positions,
			QuickSize specSize, int screenOffset, int screenSize, String sizeName) {
			QuickSize leading = positions.getLeading().get();
			QuickSize center = positions.getCenter().get();
			QuickSize trailing = positions.getTrailing().get();
			int pos, size, anchor;
			// There are many ways of specifying the positioning and height of the shape here.
			// The user will get a warning if any of the attributes conflict,
			// but we still have to decide which attributes will take priority in that case.
			// We'll respect size first, then leading, then trailing, and center last.
			if (specSize != null) {
				size = specSize.evaluate(screenSize);
				if (leading != null)
					anchor = pos = leading.evaluate(screenSize);
				else if (trailing != null) {
					anchor = trailing.evaluate(screenSize);
					pos = anchor - size;
				} else if (center != null) {
					anchor = center.evaluate(screenSize);
					pos = anchor - size / 2;
				} else
					anchor = pos = 0;
			} else if (leading != null) {
				anchor = pos = leading.evaluate(screenSize);
				if (trailing != null) {
					anchor = trailing.evaluate(screenSize);
					size = anchor - pos;
				} else if (center != null) {
					anchor = center.evaluate(screenSize);
					size = (anchor - pos) * 2;
				} else {
					theShape.reporting()
					.warn(StringUtils.capitalize(sizeName) + " is missing or null and cannot be deduced from positioning");
					return false;
				}
			} else if (center != null && trailing != null) { // Pretty weird, but we can deduce
				int centr = center.evaluate(screenSize);
				int trail = trailing.evaluate(screenSize);
				anchor = trail;
				pos = centr - (trail - centr);
				size = (trail - centr) * 2;
			} else {
				theShape.reporting().warn(StringUtils.capitalize(sizeName) + " is missing or null and cannot be deduced from positioning");
				return false;
			}
			if (size == 0)
				return false;
			else if (size < 0) {
				theShape.reporting().warn(StringUtils.capitalize(sizeName) + " evaluates negatively: " + size);
				return false;
			} else if (pos > screenOffset + screenSize || pos + size <= screenOffset)
				return false;

			setPosition.accept(pos);
			setSize.accept(size);
			setAnchor.accept(anchor);
			return true;
		}
	}

	static abstract class QuickDrawSimpleShape<S extends QuickSimpleShape> extends QuickDrawBorderedShape<S> {
		private final Observable<?> theUpdate;
		private Rectangle theScreen;
		private final SimpleShapeHandling theBounds;
		private SettableValue<Double> theRotationValue;

		protected QuickDrawSimpleShape(S shape) {
			super(shape);
			theBounds = new SimpleShapeHandling(shape);
			theRotationValue = shape.getAddOn(QuickRotated.class).getRotation();
			theUpdate = Observable.or(super.update(), theRotationValue.noInitChanges(), shape.getWidth().noInitChanges(),
				shape.getHeight().noInitChanges());
		}

		public double getRotation() {
			Double rotation = theRotationValue.get();
			return rotation == null ? 0.0f : rotation.floatValue();
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		protected Rectangle getBounds() {
			return theBounds.getBounds();
		}

		@Override
		public Point hit(Point containerPoint) {
			if (!isVisible() || theScreen == null)
				return null;

			if (!theBounds.updateBounds(getShape().getWidth().get(), getShape().getHeight().get(), theRotationValue.get(), theScreen, false,
				true))
				return null;
			if (theBounds.getRotationInverse() != null) {
				Point2D.Float transformed = (Point2D.Float) theBounds.getRotationInverse()
					.transform(new Point2D.Float(containerPoint.x, containerPoint.y), new Point2D.Float(0, 0));
				containerPoint = new Point(Math.round(transformed.x), Math.round(transformed.y));
			}
			if (!theBounds.getBounds().contains(containerPoint))
				return null;
			return getHit(containerPoint);
		}

		protected abstract Point getHit(Point point);

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			if (!isVisible())
				return;

			theScreen = screen;
			if (!theBounds.updateBounds(getShape().getWidth().get(), getShape().getHeight().get(), theRotationValue.get(), screen, false,
				true))
				return;
			if (theBounds.getRotation() != null) {
				gfx.transform(theBounds.getRotation());
			}
			try {
				doDraw(gfx, theBounds.getBounds());
			} finally {
				if (theBounds.getRotationInverse() != null) {
					gfx.transform(theBounds.getRotationInverse());
				}
			}
		}

		protected abstract void doDraw(Graphics2D gfx, Rectangle bounds);
	}

	static class InterpretedRectangle extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickRectangle> {
		InterpretedRectangle(QuickRectangle.Interpreted<?> rectangle, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(rectangle, tx);
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickRectangle element) throws ModelInstantiationException {
			return new QuickDrawRectangle(element, getPublishers(element));
		}
	}

	static class QuickDrawRectangle extends QuickDrawSimpleShape<QuickRectangle> {
		private final SimpleShapeContainer theContainer;
		private final Observable<?> theUpdate;
		private ListElement<QuickShapeInterpretation> theSubFocus;

		QuickDrawRectangle(QuickRectangle rectangle, List<QuickDrawShapePublisher> contents) {
			super(rectangle);
			theContainer = new SimpleShapeContainer(contents, rectangle.onDestroy()) {
				@Override
				protected ListElement<QuickShapeInterpretation> getFocus() {
					return theSubFocus;
				}

				@Override
				protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
					theSubFocus = shape;
				}
			};
			theUpdate = Observable.or(super.update(), theContainer.update());
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		protected void doDraw(Graphics2D gfx, Rectangle screen) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			// System.out
			// .println("Rect: " + screen.x + "," + screen.y + " " + screen.width + "x" + screen.height + ": " + Colors.toString(bg));
			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillRect(screen.x, screen.y, screen.width, screen.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawRect(screen.x, screen.y, screen.width, screen.height);
			}

			if (!theContainer.getShapes().isEmpty()) {
				Graphics2D contentGfx = (Graphics2D) gfx.create(screen.x, screen.y, screen.width, screen.height);
				try {
					theContainer.draw(contentGfx, new Rectangle(0, 0, screen.width, screen.height));
				} finally {
					contentGfx.dispose();
				}
			}
		}

		@Override
		protected Point getHit(Point point) {
			return point;
		}

		@Override
		public QuickShapeInterpretation mouseEntered(MouseEvent e) {
			if (!isVisible())
				return null;
			QuickShapeInterpretation hit = theContainer.mouseEntered(e);
			if (hit == null)
				hit = super.mouseEntered(e);
			return hit;
		}

		@Override
		public QuickShapeInterpretation mouseMoved(MouseEvent e) {
			if (!isVisible())
				return null;
			QuickShapeInterpretation hit = theContainer.mouseMoved(e);
			if (hit == null)
				hit = super.mouseMoved(e);
			return hit;
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if (!isVisible())
				return;
			theContainer.mouseDragged(e);
			super.mouseDragged(e);
		}

		@Override
		public void mouseExited(MouseEvent e) {
			if (!isVisible())
				return;
			theContainer.mouseExited(e);
			super.mouseExited(e);
		}

		@Override
		public Opacity mousePressed(MouseEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mousePressed(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mousePressed(e));
			return opacity;
		}

		@Override
		public Opacity mouseReleased(MouseEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseReleased(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseReleased(e));
			return opacity;
		}

		@Override
		public Opacity mouseClicked(MouseEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseClicked(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseClicked(e));
			return opacity;
		}

		@Override
		public Opacity mouseWheelMoved(MouseWheelEvent e) {
			if (!isVisible())
				return Opacity.None;
			Opacity opacity = theContainer.mouseWheelMoved(e);
			if (opacity != Opacity.Full)
				opacity = opacity.or(super.mouseWheelMoved(e));
			return opacity;
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyPressed(e))
				return true;
			return super.keyPressed(e);
		}

		@Override
		public boolean keyReleased(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyReleased(e))
				return true;
			return super.keyReleased(e);
		}

		@Override
		public boolean keyTyped(KeyEvent e) {
			if (!isVisible())
				return false;
			else if (theContainer.keyTyped(e))
				return true;
			return super.keyTyped(e);
		}

		@Override
		public String getTooltip() {
			String tooltip = theContainer.getTooltip();
			if (tooltip == null)
				tooltip = super.getTooltip();
			return tooltip;
		}
	}

	static class InterpretedEllipse implements InterpretedQuickShapePublisher<QuickEllipse> {
		InterpretedEllipse(QuickEllipse.Interpreted<?> ellipse, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickEllipse element) throws ModelInstantiationException {
			return new QuickDrawEllipse(element);
		}
	}

	static class QuickDrawEllipse extends QuickDrawSimpleShape<QuickEllipse> {
		public QuickDrawEllipse(QuickEllipse shape) {
			super(shape);
		}

		@Override
		protected Point getHit(Point point) {
			Rectangle bounds = getBounds();
			float hRad = bounds.width * 0.5f;
			float vRad = bounds.height * 0.5f;
			float xd = point.x - bounds.x - hRad;
			float yd = point.y - bounds.y - vRad;
			if (((xd * xd) / (hRad * hRad)) + ((yd * yd) / (vRad * vRad)) <= 1)
				return point;
			return null;
		}

		@Override
		protected void doDraw(Graphics2D gfx, Rectangle bounds) {
			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillOval(bounds.x, bounds.y, bounds.width, bounds.height);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawOval(bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
	}

	static class InterpretedPolygon<V> implements InterpretedQuickShapePublisher<QuickPolygon<V>> {
		InterpretedPolygon(QuickPolygon.Interpreted<V> polygon, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickPolygon<V> element) throws ModelInstantiationException {
			return new QuickDrawPolygon<>(element);
		}
	}

	static class QuickDrawPolygon<V> extends QuickDrawBorderedShape<QuickPolygon<V>> {
		public QuickDrawPolygon(QuickPolygon<V> shape) {
			super(shape);
		}

		protected int[][] getVertices() {
			QuickPolygon<V> poly = getShape();
			List<V> vertices = QommonsUtils.unmodifiableCopy(poly.getVertices());
			int[][] points = new int[2][vertices.size()];
			for (int v = 0; v < vertices.size(); v++) {
				poly.getActiveVertex().set(vertices.get(v));
				points[0][v] = poly.getVertexX().get();
				points[1][v] = poly.getVertexY().get();
			}
			return points;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			if (!isVisible())
				return;
			int[][] points = getVertices();
			if (points[0].length < 3)
				return;

			Color bg = getColor();
			float opacity = getOpacity();
			Color borderColor = getBorderColor();
			int borderThickness = getBorderThickness();
			StrokeDashing dash = getStrokeDash();

			if (opacity > 0 && bg.getAlpha() > 0) {
				if (opacity < 1)
					bg = Colors.transluce(bg, opacity);
				if (bg.getAlpha() > 0) {
					gfx.setColor(bg);
					gfx.fillPolygon(points[0], points[1], points[0].length);
				}
			}
			if (borderThickness > 0 && borderColor.getAlpha() > 0) {
				gfx.setColor(borderColor);
				dash.apply(gfx, borderThickness, false);
				gfx.drawPolygon(points[0], points[1], points[0].length);
			}
		}

		@Override
		public Point hit(Point containerPoint) {
			int[][] points = getVertices();
			if (points[0].length < 3)
				return null;

			int hits = 0;
			for (int i = 0; i < points[0].length; i++) {
				if (lineSegHit(points, i, containerPoint))
					hits++;
			}
			return hits % 2 == 1 ? containerPoint : null;
		}

		private boolean lineSegHit(int[][] points, int i, Point containerPoint) {
			int next = i + 1;
			if (next == points[0].length)
				next = 0;
			if (isBetween(points[1][i], points[1][next], containerPoint.y)) { // cp.y is between the 2 vertex y values
				int dx = points[0][next] - points[0][i];
				int pXdy = (containerPoint.y - points[1][i]);
				int dy = points[1][next] - points[1][i];
				int intersectX = points[0][i] + dx * pXdy / dy;
				return isBetween(points[0][i], points[0][next], intersectX) && intersectX <= containerPoint.x;
			}
			return false;
		}

		private boolean isBetween(int v0, int v1, int p) {
			if (p >= v0)
				return p < v1;
			else
				return p >= v1;
		}
	}

	static class InterpretedText implements InterpretedQuickShapePublisher<QuickDrawText> {
		private final Map<Object, InterpretedText> theSubTexts;

		InterpretedText(QuickDrawText.Interpreted text, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			theSubTexts = new HashMap<>();
			for (QuickDrawText.Interpreted subText : text.getSubTexts()) {
				theSubTexts.put(subText.getIdentity(), tx.transform(subText, InterpretedText.class));
			}
		}

		@Override
		public QuickDrawSwingText interpret(QuickDrawText element) throws ModelInstantiationException {
			return new QuickDrawSwingText(element,
				QommonsUtils.filterMapE(element.getSubTexts(), null, st -> theSubTexts.get(st.getIdentity()).interpret(st)));
		}
	}

	static class QuickDrawSwingText extends QuickDrawSingleShape<QuickDrawText> {
		private final List<QuickDrawSwingText> theSubTexts;
		private final Observable<?> theUpdate;
		private FontAdjuster theFontAdjuster;
		private Font theParentFont;
		private Font theFont;
		private String theText;
		private final SimpleShapeHandling theBounds;
		private SettableValue<Double> theRotationValue;

		private FontRenderContext theRenderContext;
		private Rectangle2D theTextBounds;

		QuickDrawSwingText(QuickDrawText shape, List<QuickDrawSwingText> subTexts) {
			super(shape);
			theSubTexts = subTexts;
			theUpdate = Observable.or(//
				Observable.onRootFinish(Observable.or(shape.getValue().noInitChanges(), shape.getStyle().changes())), //
				Observable.or(new MappedList<>(subTexts, QuickDrawSwingText::update)));
			theFontAdjuster = new FontAdjuster();
			theRotationValue = shape.getAddOnValue(QuickRotated.class, QuickRotated::getRotation);
			theBounds = new SimpleShapeHandling(shape);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			theParentFont = gfx.getFont();
			Rectangle bounds = updateBounds(gfx.getFontRenderContext());
			if (bounds == null)
				return;
			double rotation = theRotationValue == null ? 0.0 : theRotationValue.get();
			if (!theBounds.updateBounds(QuickSize.ofPixels(bounds.width), QuickSize.ofPixels(bounds.height), rotation, screen, true, true))
				return;
			// System.out.println(theText + " bounds=" + bounds + " screen=" + screen + " rot=" + (rotation / Math.PI * 180) + " tx="
			// + theBounds.getBounds());
			theBounds.getBounds().y += bounds.height;
			if (theBounds.getRotation() != null)
				gfx.transform(theBounds.getRotation());
			try {
				drawText(gfx, theBounds.getBounds().x, theBounds.getBounds().y);
			} finally {
				if (theBounds.getRotationInverse() != null)
					gfx.transform(theBounds.getRotationInverse());
			}
		}

		protected void drawText(Graphics2D gfx, int x, int y) {
			if (theText != null && !theText.isEmpty()) {
				Color preColor = gfx.getColor();
				try {
					gfx.setFont(theFont);
					Color color = getShape().getStyle().getFontColor().get();
					if (color == null)
						color = Color.black;
					gfx.setColor(color);
					gfx.drawString(theText, x, y);
					x += theTextBounds.getWidth();
				} finally {
					gfx.setFont(theParentFont);
					gfx.setColor(preColor);
				}
			}
			for (QuickDrawSwingText subText : theSubTexts)
				subText.drawText(gfx, x, y);
		}

		protected Rectangle updateBounds(FontRenderContext renderCtx) {
			if (renderCtx == null) {
				if (theText != null)
					System.err.println(Integer.toHexString(hashCode()) + " (" + theText + "): Null render context");
				return null;
			}
			theRenderContext = renderCtx;
			theText = getShape().getValue().get();
			if (theText != null && !theText.isEmpty()) {
				QuickCoreSwing.adjustFont(theFontAdjuster, getShape().getStyle());
				theFont = theFontAdjuster.adjust(theParentFont);
				theTextBounds = theFont.getStringBounds(theText, theRenderContext);
			} else {
				theFont = null;
				theTextBounds = null;
			}

			if (theTextBounds == null && theSubTexts.isEmpty())
				return null;
			Rectangle bounds = new Rectangle();
			if (theTextBounds != null) {
				bounds.x = (int) Math.round(theTextBounds.getX());
				bounds.y = (int) Math.round(theTextBounds.getY());
				bounds.width = (int) Math.round(theTextBounds.getWidth());
				bounds.height = (int) Math.round(theTextBounds.getHeight());
			}
			for (QuickDrawSwingText subText : theSubTexts) {
				Rectangle subBounds = subText.updateBounds(renderCtx);
				if (subBounds != null) {
					if (bounds.width > 0)
						subBounds.setLocation(subBounds.x + bounds.width, subBounds.y);
					bounds.add(subBounds);
				}
			}
			return bounds;
		}

		@Override
		public Point hit(Point containerPoint) {
			Rectangle bounds = updateBounds(theRenderContext);
			if (bounds == null)
				return null;
			Point txPoint = transform(containerPoint, theBounds.getRotationInverse());
			return bounds.contains(txPoint) ? containerPoint : null;
		}

		@Override
		public Opacity getOpacity(Point point) {
			return Opacity.Partial;
		}
	}

	static abstract class QuickDrawLinearShape<S extends QuickLinearShape> extends QuickDrawSingleShape<S> {
		private final ObservableValue<Double> theThickness;
		private final ObservableValue<StrokeDashing> theStrokeDash;
		private final Observable<?> theUpdate;

		protected QuickDrawLinearShape(S shape) {
			super(shape);
			theThickness = shape.getStyle().getThickness();
			theStrokeDash = shape.getStyle().getStrokeDash();
			theUpdate = Observable.or(super.update(), theThickness.noInitChanges(), theStrokeDash.noInitChanges(), shape.getRepaint());
		}

		public double getThickness() {
			Double thick = theThickness.get();
			return thick == null ? 1 : thick;
		}

		public StrokeDashing getStrokeDash() {
			StrokeDashing dash = theStrokeDash.get();
			return dash == null ? StrokeDashing.full : dash;
		}

		static int toInt(double value) {
			return (int) Math.round(value);
		}

		static class HitData {
			/** A number between 0 and 1, where zero represents a hit at p0 and 1 represents a hit at p1 */
			final double p;
			/** The distance of the hit away from the line */
			final double pDist;
			/** The distance between the two points on the line */
			final double d;
			/** The distance between the first point on the line and the point on the line closest to the hit */
			final double pd;
			/** The x coordinate of the point on the line closest to the hit */
			final double x;
			/** The y coordinate of the point on the line closest to the hit */
			final double y;

			public HitData(double p, double pDist, double d, double pd, double x, double y) {
				this.p = p;
				this.pDist = pDist;
				this.d = d;
				this.pd = pd;
				this.x = x;
				this.y = y;
			}
		}

		HitData getHit(double x0, double y0, double x1, double y1, int x, int y, double dLimit) {
			double dx = x1 - x0;
			double dy = y1 - y0;
			double dx0 = x - x0;
			double dy0 = y - y0;
			double dx1 = x - x1;
			double dy1 = y - y1;

			double d = Math.sqrt(dx * dx + dy * dy);
			double d02 = dx0 * dx0 + dy0 * dy0;
			double d0 = Math.sqrt(d02);
			double d1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);

			double theta = Math.atan2(d1, d);
			double pd = d0 * Math.cos(theta);
			double pDist2 = d02 - pd * pd;
			if (pDist2 > dLimit * dLimit)
				return null;
			double p = pd / d;
			double px = x0 + p * dx;
			double py = y0 + p * dy;
			return new HitData(p, Math.sqrt(pDist2), d, pd, px, py);
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}
	}

	static class InterpretedLine implements InterpretedQuickShapePublisher<QuickLine> {
		InterpretedLine(QuickLine.Interpreted line, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickLine element) throws ModelInstantiationException {
			return new QuickDrawLine(element);
		}
	}

	static class QuickDrawLine extends QuickDrawLinearShape<QuickLine> {
		private final Observable<?> theUpdate;

		QuickDrawLine(QuickLine shape) {
			super(shape);
			theUpdate = Observable.onRootFinish(Observable.or(shape.getStyle().changes(), //
				Observable.or(shape.getPoints().stream().flatMap(pt -> Stream.of(pt.getX().noInitChanges(), pt.getY().noInitChanges()))
					.collect(Collectors.toList()))));
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			if (!isVisible())
				return;
			Color color = getColor();
			float opacity = getOpacity();
			double thickness = getThickness();
			if (thickness <= 0 || opacity <= 0 || color.getAlpha() == 0)
				return;
			StrokeDashing dash = getStrokeDash();

			if (opacity < 1 && color.getAlpha() > 0)
				color = Colors.transluce(color, opacity);
			if (color.getAlpha() == 0)
				return;
			gfx.setColor(color);
			dash.apply(gfx, (float) thickness, false);

			boolean first = true;
			int prevX = 0, prevY = 0;
			// System.out.print("Drawing " + Colors.toString(color));
			for (QuickPoint point : getShape().getPoints()) {
				int x = toInt(point.getX().get());
				int y = toInt(point.getY().get());
				// System.out.print(" (" + x + ", " + y + ")");
				if (first)
					first = false;
				else
					gfx.drawLine(prevX, prevY, x, y);

				prevX = x;
				prevY = y;
			}
			// System.out.println();
		}

		@Override
		public Point hit(Point containerPoint) {
			if (!isVisible())
				return null;
			double thickness = getThickness();
			if (thickness <= 0)
				return null;

			QuickPoint prev = null;
			for (QuickPoint point : getShape().getPoints()) {
				if (prev == null) {
					prev = point;
					continue;
				}

				if (getHit(toInt(prev.getX().get()), toInt(prev.getY().get()), //
					toInt(point.getX().get()), toInt(point.getY().get()), //
					containerPoint.x, containerPoint.y, thickness) != null)
					return containerPoint;

				prev = point;
			}
			return null;
		}
	}

	static class InterpretedFlexLine<T> implements InterpretedQuickShapePublisher<QuickFlexLine<T>> {
		InterpretedFlexLine(QuickFlexLine.Interpreted<T> line, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickFlexLine<T> element) throws ModelInstantiationException {
			return new QuickDrawFlexLine<>(element);
		}
	}

	static class QuickDrawFlexLine<T> extends QuickDrawLinearShape<QuickFlexLine<T>> {
		private final Observable<?> theUpdate;

		public QuickDrawFlexLine(QuickFlexLine<T> shape) {
			super(shape);
			theUpdate = Observable.onRootFinish(Observable.or(shape.getStyle().changes(), shape.getPoints().simpleChanges()));
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			if (!isVisible())
				return;

			// Don't query the style info now, as it may vary by point, or even along each segment

			QuickFlexLine<T> line = getShape();
			double prevX = Double.NaN, prevY = Double.NaN;
			int index = 0;
			for (T point : line.getPoints()) {
				line.getActivePointAs().set(point);
				line.getPointIndexAs().set(index);
				double x = line.getPointX().get();
				double y = line.getPointY().get();
				if (!Double.isNaN(prevX) && !Double.isNaN(prevY) && !Double.isNaN(x) && !Double.isNaN(y)) {
					renderSegment(line, prevX, prevY, x, y, gfx);
				}
				prevX = x;
				prevY = y;
				index++;
			}
		}

		private void renderSegment(QuickFlexLine<T> line, double prevX, double prevY, double x, double y, Graphics2D gfx) {
			double dx, dy, d;
			if (line.isDistanceNeeded() || line.isStyleDynamic()) {
				dx = x - prevX;
				dy = y - prevY;
				d = Math.sqrt(dx * dx + dy * dy);
				line.getPointDistanceAs().set(d);
			} else
				dx = dy = d = 0;
			if (!line.isStyleDynamic()) { // Constant style
				renderConstStyleSegment(prevX, prevY, x, y, gfx);
				return;
			}
			Double svd = line.getStyleVarianceDistance().get();
			if (svd == null)
				svd = 1.0;
			int divs = toInt(d / svd);
			if (divs <= 1) {
				renderConstStyleSegment(prevX, prevY, dx, dy, gfx);
				return;
			}

			double x0 = prevX, y0 = prevY;
			for (int div = 0; div < divs; div++) {
				double p = div * 1.0 / divs;
				double x1 = prevX + p * dx;
				double y1 = prevY + p * dy;
				line.getLinearPAs().set(p);
				renderConstStyleSegment(x0, y0, x1, y1, gfx);
				x0 = x1;
				y0 = y1;
			}
		}

		private void renderConstStyleSegment(double prevX, double prevY, double x, double y, Graphics2D gfx) {
			float opacity = getOpacity();
			Color color = getColor();
			double thickness = getThickness();
			if (opacity <= 0 || color.getAlpha() == 0 || thickness <= 0)
				return;
			if (opacity < 1) {
				color = Colors.transluce(color, opacity);
				if (color.getAlpha() == 0)
					return;
			}
			StrokeDashing dashing = getStrokeDash();
			dashing.apply(gfx, (float) thickness, true);
			gfx.drawLine(toInt(prevX), toInt(prevY), toInt(x), toInt(y));
		}

		@Override
		public Point hit(Point containerPoint) {
			if (!isVisible())
				return null;

			// Don't query the style info now, as it may vary by point, or even along each segment

			QuickFlexLine<T> line = getShape();
			double prevX = Double.NaN, prevY = Double.NaN;
			int index = 0;
			for (T point : line.getPoints()) {
				line.getActivePointAs().set(point);
				line.getPointIndexAs().set(index);
				double x = line.getPointX().get();
				double y = line.getPointY().get();
				if (!Double.isNaN(prevX) && !Double.isNaN(prevY) && !Double.isNaN(x) && !Double.isNaN(y)) {
					double dx, dy, d;
					if (line.isDistanceNeeded() || line.isThicknessDynamic()) {
						dx = x - prevX;
						dy = y - prevY;
						d = Math.sqrt(dx * dx + dy * dy);
						line.getPointDistanceAs().set(d);
					} else
						dx = dy = d = 0;
					if (!line.isThicknessDynamic()) {
						Point hit = getConstThicknessHit(line, prevX, prevY, x, y, containerPoint);
						if (hit != null)
							return hit;
					} else {
						Double svd = line.getStyleVarianceDistance().get();
						if (svd == null)
							svd = 1.0;
						int divs = toInt(d / svd);
						if (divs <= 1)
							return getConstThicknessHit(line, prevX, prevY, x, y, containerPoint);

						double x0 = prevX, y0 = prevY;
						for (int div = 0; div < divs; div++) {
							double p = div * 1.0 / divs;
							double x1 = prevX + p * dx;
							double y1 = prevY + p * dy;
							line.getLinearPAs().set(p);
							Point hit = getConstThicknessHit(line, x0, y0, x1, y1, containerPoint);
							if (hit != null)
								return hit;
							x0 = x1;
							y0 = y1;
						}
					}
				}
				prevX = x;
				prevY = y;
				index++;
			}
			return null;
		}

		private Point getConstThicknessHit(QuickFlexLine<T> line, double prevX, double prevY, double x, double y, Point containerPoint) {
			HitData hit = getHit(prevX, prevY, x, y, containerPoint.x, containerPoint.y, getThickness());
			if (hit != null) {
				line.getLinearPAs().set(hit.p);
				return new Point(toInt(hit.x), toInt(hit.y));
			}
			return null;
		}
	}

	static class InterpretedShapeView extends InterpretedShapeContainer implements InterpretedQuickShapePublisher<QuickShapeView> {
		private final Map<Object, InterpretedTransformOp<?>> theTransformations;

		InterpretedShapeView(QuickShapeView.Interpreted shapeView, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
			super(shapeView, tx);
			theTransformations = new HashMap<>();
			for (TransformOp.Interpreted<?> transform : shapeView.getTransformations())
				theTransformations.put(transform.getIdentity(), tx.transform(transform, InterpretedTransformOp.class));
		}

		@Override
		public QuickDrawShapePublisher interpret(QuickShapeView element) throws ModelInstantiationException {
			List<QuickDrawTransform> transformations = new ArrayList<>(element.getTransformations().size());
			for (int i = 0; i < element.getTransformations().size(); i++) {
				TransformOp tx = element.getTransformations().get(element.getTransformations().size() - i - 1);
				transformations.add(((InterpretedTransformOp<TransformOp>) theTransformations.get(tx.getIdentity())).interpret(tx));
			}
			return new QuickDrawShapeView(element, getPublishers(element), Collections.unmodifiableList(transformations));
		}
	}

	static class QuickDrawShapeView extends SimpleShapeContainer {
		private final List<QuickDrawTransform> theTransformations;
		private final Observable<?> theUpdate;
		private ListElement<QuickShapeInterpretation> theFocus;

		private AffineTransform theTransform;
		private AffineTransform theReverseTransform;

		public QuickDrawShapeView(QuickShapeView shapeView, List<QuickDrawShapePublisher> publishers,
			List<QuickDrawTransform> transformations) {
			super(publishers, shapeView.onDestroy());
			theTransformations = transformations;
			Observable<?>[] txUpdates = new Observable[theTransformations.size() + 1];
			txUpdates[0] = super.update();
			for (int i = 0; i < theTransformations.size(); i++)
				txUpdates[i + 1] = theTransformations.get(i).update();
			theUpdate = Observable.or(txUpdates);
		}

		@Override
		public ObservableCollection<QuickShapeInterpretation> getShapes() {
			return ObservableCollection.of(this);
		}

		@Override
		protected ListElement<QuickShapeInterpretation> getFocus() {
			return theFocus;
		}

		@Override
		protected void setFocus(ListElement<QuickShapeInterpretation> shape) {
			theFocus = shape;
		}

		@Override
		public Observable<?> update() {
			return theUpdate;
		}

		private void updateTransform() {
			theTransform = new AffineTransform();
			for (QuickDrawTransform tx : theTransformations)
				tx.transform(theTransform);
			try {
				theReverseTransform = theTransform.createInverse();
			} catch (NoninvertibleTransformException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void draw(Graphics2D gfx, Rectangle screen) {
			updateTransform();
			if (theTransform.isIdentity()) {
				super.draw(gfx, screen);
				return;
			}
			gfx.transform(theTransform);
			Point ul = transform(new Point(screen.x, screen.y), theReverseTransform);
			Point ur = transform(new Point(screen.x + screen.width, screen.y), theReverseTransform);
			Point ll = transform(new Point(screen.x, screen.y + screen.height), theReverseTransform);
			Point lr = transform(new Point(screen.x + screen.width, screen.y + screen.height), theReverseTransform);
			int minX = min(ul.x, ur.x, ll.x, lr.x);
			int minY = min(ul.y, ur.y, ll.y, lr.y);
			int maxX = max(ul.x, ur.x, ll.x, lr.x);
			int maxY = max(ul.y, ur.y, ll.y, lr.y);
			Rectangle txScreen = new Rectangle(minX, minY, maxX - minX, maxY - minY);
			try {
				super.draw(gfx, txScreen);
			} finally {
				gfx.transform(theReverseTransform);
			}
		}

		private static int min(int x1, int x2, int x3, int x4) {
			int min = x1;
			if (x2 < min)
				min = x2;
			if (x3 < min)
				min = x3;
			if (x4 < min)
				min = x4;
			return min;
		}

		private static int max(int x1, int x2, int x3, int x4) {
			int max = x1;
			if (x2 > max)
				max = x2;
			if (x3 > max)
				max = x3;
			if (x4 > max)
				max = x4;
			return max;
		}

		@Override
		public Point hit(Point containerPoint) {
			updateTransform();
			if (theReverseTransform != null) {
				containerPoint = transform(containerPoint, theReverseTransform);
			}
			return super.hit(containerPoint);
		}
	}

	static class InterpretedTranslate implements InterpretedTransformOp<Translate> {
		InterpretedTranslate(Translate.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(Translate element) throws ModelInstantiationException {
			return new QuickDrawTranslate(element);
		}
	}

	static class QuickDrawTranslate implements QuickDrawTransform {
		private final SettableValue<Integer> theX;
		private final SettableValue<Integer> theY;

		QuickDrawTranslate(Translate element) {
			theX = element.getX();
			theY = element.getY();
		}

		@Override
		public void transform(AffineTransform source) {
			int x = theX.get();
			int y = theY.get();
			source.translate(x, y);
		}

		@Override
		public Observable<?> update() {
			return Observable.onRootFinish(Observable.or(theX.noInitChanges(), theY.noInitChanges()));
		}
	}

	static class InterpretedScale implements InterpretedTransformOp<Scale> {
		InterpretedScale(Scale.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(Scale element) throws ModelInstantiationException {
			return new QuickDrawScale(element);
		}
	}

	static class QuickDrawScale implements QuickDrawTransform {
		private final SettableValue<Float> theX;
		private final SettableValue<Float> theY;
		private final ErrorReporting theReporting;

		QuickDrawScale(Scale element) {
			theX = element.getX();
			theY = element.getY();
			theReporting = element.reporting();
		}

		@Override
		public void transform(AffineTransform source) {
			float x = theX.get();
			float y = theY.get();
			if (x <= 0 || y <= 0) {
				theReporting.error("Scale cannot be <=0 (" + x + ", " + y + ")");
				if (x <= 0)
					x = 1;
				if (y <= 0)
					y = 1;
			}
			source.scale(x, y);
		}

		@Override
		public Observable<?> update() {
			return Observable.onRootFinish(Observable.or(theX.noInitChanges(), theY.noInitChanges()));
		}
	}

	static class InterpretedRotate implements InterpretedTransformOp<Rotate> {
		InterpretedRotate(Rotate.Interpreted interpreted, Transformer<ExpressoInterpretationException> tx)
			throws ExpressoInterpretationException {
		}

		@Override
		public QuickDrawTransform interpret(Rotate element) throws ModelInstantiationException {
			return new QuickDrawRotate(element);
		}
	}

	static class QuickDrawRotate implements QuickDrawTransform {
		private final SettableValue<Integer> theAnchorX;
		private final SettableValue<Integer> theAnchorY;
		private final SettableValue<Double> theRadians;

		QuickDrawRotate(Rotate element) {
			theAnchorX = element.getAnchorX();
			theAnchorY = element.getAnchorY();
			theRadians = element.getRadians();
		}

		@Override
		public void transform(AffineTransform source) {
			double radians = theRadians.get();
			source.rotate(radians, theAnchorX.get(), theAnchorY.get());
		}

		@Override
		public Observable<?> update() {
			return Observable
				.onRootFinish(Observable.or(theAnchorX.noInitChanges(), theAnchorY.noInitChanges(), theRadians.noInitChanges()));
		}
	}
}
