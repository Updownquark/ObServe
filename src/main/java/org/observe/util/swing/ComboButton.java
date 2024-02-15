package org.observe.util.swing;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.util.TypeTokens;
import org.qommons.collect.ListenerList;

import com.google.common.reflect.TypeToken;

/**
 * Found this at https://stackoverflow.com/questions/36352707/actions-inside-of-another-action-like-netbeans/66303093#66303093
 *
 * A swing split button implementation. A JButton that has an additional section with an arrow icon on the right that when clicked shows a
 * JPopupMenu that is positioned flush with the button.
 *
 * The implementation sets the buttons pop-up menu using setComponentPopupMenu() meaning that in addition to clicking the drop-down arrow,
 * user can also right click the button to open the pop-up menu.
 *
 * Author: DUDSS - 21.02.2020 I modified the button to use a JPopupMenu instead of a custom JFrame to avoid hacky focus workarounds and fix
 * focus issues.
 *
 * Credit: Modified version of a split button by MadProgrammer.
 * https://stackoverflow.com/questions/36352707/actions-inside-of-another-action-like-netbeans It's original author seems to be unknown.
 *
 * Modification by Updownquark to work with observables.
 */
public class ComboButton<E> extends JButton {
	private int separatorSpacing = 4;
	private int splitWidth = 22;
	private int arrowSize = 8;
	private boolean onSplit;
	private Rectangle splitRectangle;
	private boolean alwaysDropDown;
	private Color arrowColor = Color.BLACK;
	private Color disabledArrowColor = Color.GRAY;
	private Image image;
	private MouseHandler mouseHandler;
	private boolean toolBarButton;

	private JPopupMenu jpopupMenu;

	private final ObservableCollection<E> theValues;
	private final CategoryRenderStrategy<E, ?> theColumn;
	private final ListenerList<BiConsumer<? super E, Object>> theListeners;

	/**
	 * Creates a button with initial text and an icon.
	 *
	 * @param text the text of the button
	 * @param windowIcon the Icon image to display on the button
	 */
	public ComboButton(ObservableCollection<E> values, CategoryRenderStrategy<E, ?> column, Observable<?> until) {
		setMargin(new Insets(2, 2, 2, 0));
		theValues = values;
		theColumn = column;

		addMouseMotionListener(getMouseHandler());
		addMouseListener(getMouseHandler());
		// Default for no "default" action...
		setAlwaysDropDown(true);

		InputMap im = getInputMap(WHEN_FOCUSED);
		ActionMap am = getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "PopupMenu.close");
		am.put("PopupMenu.close", new ClosePopupAction());

		theListeners = ListenerList.build().build();
		configureForToolBar();
		SettableValue<E> selection = SettableValue.build(TypeTokens.get().wrap(values.getType())).build();
		PanelPopulation.populateHPanel(getPopupMenu(), new JustifiedBoxLayout(true).mainJustified().crossJustified(), until)//
		.addTable(values, table -> table//
			.withColumnHeader(false)//
			.withColumn(column)//
			.withSelection(selection, true)//
			.withAdaptiveHeight(1, 6, 6)//
			);
		selection.noInitChanges().act(evt -> {
			if (evt.getNewValue() == null)
				return;
			closePopupMenu();
			fire(evt.getNewValue(), evt);
			EventQueue.invokeLater(() -> selection.set(null, null));
		});
	}

	private void fire(E value, Object cause) {
		theListeners.forEach(//
			l -> l.accept(value, cause));
	}

	public ObservableCollection<E> getValues() {
		return theValues;
	}

	public CategoryRenderStrategy<E, ?> getColumn() {
		return theColumn;
	}

	public ComboButton<E> addListener(BiConsumer<? super E, Object> listener) {
		theListeners.add(listener, true);
		return this;
	}

	public ComboButton<E> withText(String text) {
		setText(text);
		return this;
	}

	public ComboButton<E> withIcon(Icon icon) {
		setIcon(icon);
		return this;
	}

	// /**
	// * Creates a pre-configured button suitable for being used on a JToolBar
	// *
	// * @param defaultAction
	// * @param actions
	// * @return
	// */
	// public static ComboButton createToolBarButton(Action defaultAction, Action... actions) {
	// ComboButton btn = new ComboButton(defaultAction, actions);
	// btn.configureForToolBar();
	// return btn;
	// }
	//
	// /**
	// * Creates a pre-configured "options only" button suitable for being used on a JToolBar
	// *
	// * @param text
	// * @param icon
	// * @param actions
	// * @return
	// */
	// public static ComboButton createToolBarButton(String text, Icon icon, JPopupMenu popupMenu) {
	// ComboButton btn = new ComboButton(text, icon);
	// btn.setPopupMenu(popupMenu);
	// btn.setToolTipText(text);
	// btn.configureForToolBar();
	// return btn;
	// }

	@Override
	public void addActionListener(ActionListener l) {
		if (l != null) {
			setAlwaysDropDown(false);
		}
		super.addActionListener(l);
	}

	@Override
	public void setAction(Action a) {
		super.setAction(a);
		if (a != null) {
			setAlwaysDropDown(false);
		}
	}

	public void addActionAt(Action a, int index) {
		getPopupMenu().insert(a, index);
	}

	public void addAction(Action a) {
		getPopupMenu().add(a);
	}

	public void setPopupMenu(JPopupMenu popup) {
		jpopupMenu = popup;
		this.setComponentPopupMenu(popup);
	}

	/**
	 * Returns the buttons popup menu.
	 *
	 * @return
	 */
	public JPopupMenu getPopupMenu() {
		if (jpopupMenu == null) {
			jpopupMenu = new JPopupMenu();
		}
		return jpopupMenu;
	}

	/**
	 * Used to determine if the button is begin configured for use on a tool bar
	 *
	 * @return
	 */
	public boolean isToolBarButton() {
		return toolBarButton;
	}

	/**
	 * Configures this button for use on a tool bar...
	 */
	public void configureForToolBar() {
		toolBarButton = true;
		if (getIcon() != null) {
			setHideActionText(true);
		}
		setHorizontalTextPosition(JButton.CENTER);
		setVerticalTextPosition(JButton.BOTTOM);
		setFocusable(false);
	}

	protected MouseHandler getMouseHandler() {
		if (mouseHandler == null) {
			mouseHandler = new MouseHandler();
		}
		return mouseHandler;
	}

	protected int getOptionsCount() {
		return getPopupMenu().getComponentCount();
	}

	/*protected void addActionAt(Action action, int index) {
	    if (index < 0 || index >= getOptionsCount()) {
	        getPopupWindow().add(createMenuItem(action));
	    } else {
	        getPopupWindow().add(createMenuItem(action), index);
	    }
	}*/

	/*protected void removeAction(Action action) {
	    AbstractButton btn = getButtonFor(action);
	    if (btn != null) {
	        getPopupWindow().remove(btn);
	    }
	}*/

	@Override
	public Insets getInsets() {
		Insets insets = (Insets) super.getInsets().clone();
		insets.right += splitWidth;
		return insets;
	}

	@Override
	public Insets getInsets(Insets insets) {
		Insets insets1 = getInsets();
		insets.left = insets1.left;
		insets.right = insets1.right;
		insets.bottom = insets1.bottom;
		insets.top = insets1.top;
		return insets1;
	}

	protected void closePopupMenu() {
		getPopupMenu().setVisible(false);
	}

	protected void showPopupMenu() {
		if (getOptionsCount() > 0 && isEnabled()) {
			JPopupMenu menu = getPopupMenu();
			menu.setVisible(true); // Necessary to calculate pop-up menu width the first time it's displayed.
			Dimension sz = menu.getLayout().preferredLayoutSize(menu);
			int x = getWidth() - sz.width;
			if (x < 0)
				x = 0;
			menu.show(this, x, getHeight());
			menu.setSize(sz);
		}
	}

	/**
	 * Returns the separatorSpacing. Separator spacing is the space above and below the separator( the line drawn when you hover your mouse
	 * over the split part of the button).
	 *
	 * @return separatorSpacingimage = null; //to repaint the image with the new size
	 */
	public int getSeparatorSpacing() {
		return separatorSpacing;
	}

	/**
	 * Sets the separatorSpacing.Separator spacing is the space above and below the separator( the line drawn when you hover your mouse over
	 * the split part of the button).
	 *
	 * @param spacing
	 */
	public void setSeparatorSpacing(int spacing) {
		if (spacing != separatorSpacing && spacing >= 0) {
			int old = separatorSpacing;
			this.separatorSpacing = spacing;
			image = null;
			firePropertyChange("separatorSpacing", old, separatorSpacing);
			revalidate();
			repaint();
		}
	}

	/**
	 * Show the dropdown menu, if attached, even if the button part is clicked.
	 *
	 * @return true if alwaysDropdown, false otherwise.
	 */
	public boolean isAlwaysDropDown() {
		return alwaysDropDown;
	}

	/**
	 * Show the dropdown menu, if attached, even if the button part is clicked.
	 *
	 * If true, this will prevent the button from raising any actionPerformed events for itself
	 *
	 * @param value true to show the attached dropdown even if the button part is clicked, false otherwise
	 */
	public void setAlwaysDropDown(boolean value) {
		if (alwaysDropDown != value) {
			this.alwaysDropDown = value;
			firePropertyChange("alwaysDropDown", !alwaysDropDown, alwaysDropDown);
		}
	}

	/**
	 * Gets the color of the arrow.
	 *
	 * @return arrowColor
	 */
	public Color getArrowColor() {
		return arrowColor;
	}

	/**
	 * Set the arrow color.
	 *
	 * @param color
	 */
	public void setArrowColor(Color color) {
		if (arrowColor != color) {
			Color old = arrowColor;
			this.arrowColor = color;
			image = null;
			firePropertyChange("arrowColor", old, arrowColor);
			repaint();
		}
	}

	/**
	 * gets the disabled arrow color
	 *
	 * @return disabledArrowColor color of the arrow if no popup attached.
	 */
	public Color getDisabledArrowColor() {
		return disabledArrowColor;
	}

	/**
	 * sets the disabled arrow color
	 *
	 * @param color color of the arrow if no popup attached.
	 */
	public void setDisabledArrowColor(Color color) {
		if (disabledArrowColor != color) {
			Color old = disabledArrowColor;
			this.disabledArrowColor = color;
			image = null; // to repaint the image with the new color
			firePropertyChange("disabledArrowColor", old, disabledArrowColor);
		}
	}

	/**
	 * Splitwidth is the width of the split part of the button.
	 *
	 * @return splitWidth
	 */
	public int getSplitWidth() {
		return splitWidth;
	}

	/**
	 * Splitwidth is the width of the split part of the button.
	 *
	 * @param width
	 */
	public void setSplitWidth(int width) {
		if (splitWidth != width) {
			int old = splitWidth;
			this.splitWidth = width;
			firePropertyChange("splitWidth", old, splitWidth);
			revalidate();
			repaint();
		}
	}

	/**
	 * gets the size of the arrow.
	 *
	 * @return size of the arrow
	 */
	public int getArrowSize() {
		return arrowSize;
	}

	/**
	 * sets the size of the arrow
	 *
	 * @param size
	 */
	public void setArrowSize(int size) {
		if (arrowSize != size) {
			int old = arrowSize;
			this.arrowSize = size;
			image = null; // to repaint the image with the new size
			firePropertyChange("setArrowSize", old, arrowSize);
			revalidate();
			repaint();
		}
	}

	/**
	 * Gets the image to be drawn in the split part. If no is set, a new image is created with the triangle.
	 *
	 * @return image
	 */
	public Image getImage() {
		if (image == null) {
			Graphics2D g = null;
			BufferedImage img = new BufferedImage(arrowSize, arrowSize, BufferedImage.TYPE_INT_RGB);
			g = img.createGraphics();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			g.setColor(jpopupMenu != null ? arrowColor : disabledArrowColor);
			// this creates a triangle facing right >
			g.fillPolygon(new int[] { 0, 0, arrowSize / 2 }, new int[] { 0, arrowSize, arrowSize / 2 }, 3);
			g.dispose();
			// rotate it to face downwards
			img = rotate(img, 90);
			BufferedImage dimg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
			g = dimg.createGraphics();
			g.setComposite(AlphaComposite.Src);
			g.drawImage(img, null, 0, 0);
			g.dispose();
			for (int i = 0; i < dimg.getHeight(); i++) {
				for (int j = 0; j < dimg.getWidth(); j++) {
					if (dimg.getRGB(j, i) == Color.WHITE.getRGB()) {
						dimg.setRGB(j, i, 0x8F1C1C);
					}
				}
			}

			image = Toolkit.getDefaultToolkit().createImage(dimg.getSource());
		}
		return image;
	}

	/**
	 *
	 * @param g
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		// Graphics gClone = g.create();//EDIT: Herv� Guillaume
		Color oldColor = g.getColor();
		splitRectangle = new Rectangle(getWidth() - splitWidth, 0, splitWidth, getHeight());
		g.translate(splitRectangle.x, splitRectangle.y);
		int mh = getHeight() / 2;
		int mw = splitWidth / 2;
		g.drawImage(getImage(), mw - arrowSize / 2, mh + 2 - arrowSize / 2, null);
		if (!alwaysDropDown) {
			if (getModel().isRollover() || isFocusable()) {
				g.setColor(UIManager.getLookAndFeelDefaults().getColor("Button.background"));
				g.drawLine(1, separatorSpacing + 2, 1, getHeight() - separatorSpacing - 2);
				g.setColor(UIManager.getLookAndFeelDefaults().getColor("Button.shadow"));
				g.drawLine(2, separatorSpacing + 2, 2, getHeight() - separatorSpacing - 2);
			}
		}
		g.setColor(oldColor);
		g.translate(-splitRectangle.x, -splitRectangle.y);
	}

	/**
	 * Rotates the given image with the specified angle.
	 *
	 * @param img image to rotate
	 * @param angle angle of rotation
	 * @return rotated image
	 */
	private BufferedImage rotate(BufferedImage img, int angle) {
		int w = img.getWidth();
		int h = img.getHeight();
		BufferedImage dimg = dimg = new BufferedImage(w, h, img.getType());
		Graphics2D g = dimg.createGraphics();
		g.rotate(Math.toRadians(angle), w / 2, h / 2);
		g.drawImage(img, null, 0, 0);
		return dimg;
	}

	@Override
	protected void fireActionPerformed(ActionEvent event) {
		// This is a little bit of a nasty trick. Basically this is where
		// we try and decide if the buttons "default" action should
		// be fired or not. We don't want it firing if the button
		// is in "options only" mode or the user clicked on
		// on the "drop down arrow"....
		if (onSplit || isAlwaysDropDown()) {
			showPopupMenu();
		} else {
			super.fireActionPerformed(event);

		}
	}

	protected class MouseHandler extends MouseAdapter {
		@Override
		public void mouseExited(MouseEvent e) {
			onSplit = false;
			repaint(splitRectangle);
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			if (splitRectangle.contains(e.getPoint())) {
				onSplit = true;
			} else {
				onSplit = false;
			}
			repaint(splitRectangle);
		}
	}

	protected class ClosePopupAction extends AbstractAction {
		@Override
		public void actionPerformed(ActionEvent e) {
			closePopupMenu();
		}
	}

	public static <E> ComboButton<E> create(String text, ObservableCollection<E> available) {
		return new ComboButton<>(available, createDefaultComboBoxColumn(available.getType()), Observable.empty()).withText(text);
	}

	public static <E> CategoryRenderStrategy<E, E> createDefaultComboBoxColumn(TypeToken<E> type) {
		UIDefaults ui = UIManager.getDefaults();
		Color defaultSelectionBackground = ui.getColor("List.selectionBackground");
		Color defaultSelectionForeground = ui.getColor("List.selectionForeground");
		return new CategoryRenderStrategy<E, E>("Header", type, r -> r)//
			.useRenderingForSize(true)//
			.decorate((cell, deco) -> {
				if (cell.isRowHovered())
					deco.withBackground(defaultSelectionBackground).withForeground(defaultSelectionForeground);
			});
	}
}