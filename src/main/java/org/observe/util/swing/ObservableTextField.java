package org.observe.util.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.JPasswordField;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.util.swing.ObservableTextEditor.ObservableTextEditorWidget;
import org.qommons.io.Format;

/**
 * A text field that interacts with a {@link SettableValue}
 *
 * @param <E> The type of the value
 */
public class ObservableTextField<E> extends JPasswordField implements ObservableTextEditorWidget<E, ObservableTextField<E>> {
	private final ObservableTextEditor<E> theEditor;

	// Some of this is lifted and modified from https://gmigdos.wordpress.com/2010/03/30/java-a-custom-jtextfield-for-searching/
	private Icon theIcon;
	private Insets dummyInsets;
	private String theEmptyText;

	/**
	 * @param value The value for the text field to interact with
	 * @param format The format to convert the value to text and back
	 * @param until An observable that, when fired will release this text field's resources
	 */
	public ObservableTextField(SettableValue<E> value, Format<E> format, Observable<?> until) {
		if (value != null) {
			theEditor = new ObservableTextEditor<E>(this, value, format, until, //
				e -> super.setEnabled(e), //
				tt -> super.setToolTipText(tt)) {
				@Override
				protected void adjust(boolean up, Object cause) {
					if (getEchoChar() == 0)
						super.adjust(up, cause);
				}
			};
		} else
			theEditor = null;
		Border border = UIManager.getBorder("TextField.border");
		dummyInsets = border.getBorderInsets(this);
		asPassword((char) 0);

		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (theEmptyText != null)
					repaint();
			}

			@Override
			public void focusLost(FocusEvent e) {
				if (theEmptyText != null)
					repaint();
			}
		});
	}

	/** @return The value controlled by this text area */
	public SettableValue<E> getValue() {
		if (theEditor == null)
			return null;
		return theEditor.getValue();
	}

	@Override
	public Format<E> getFormat() {
		if (theEditor == null)
			return null;
		return theEditor.getFormat();
	}

	@Override
	public ObservableTextField<E> withWarning(Function<? super E, String> warning) {
		if (theEditor != null)
			theEditor.withWarning(warning);
		return this;
	}

	@Override
	public ObservableTextField<E> clearWarning() {
		if (theEditor != null)
			theEditor.clearWarning();
		return this;
	}

	@Override
	public boolean isSelectAllOnFocus() {
		if (theEditor == null)
			return false;
		return theEditor.isSelectAllOnFocus();
	}

	@Override
	public ObservableTextField<E> setSelectAllOnFocus(boolean selectAll) {
		if (theEditor != null)
			theEditor.setSelectAllOnFocus(selectAll);
		return this;
	}

	@Override
	public ObservableTextField<E> setReformatOnCommit(boolean format) {
		if (theEditor != null)
			theEditor.setReformatOnCommit(format);
		return this;
	}

	@Override
	public ObservableTextField<E> setRevertOnFocusLoss(boolean revert) {
		if (theEditor != null)
			theEditor.setRevertOnFocusLoss(revert);
		return this;
	}

	@Override
	public ObservableTextField<E> setCommitOnType(boolean commit) {
		if (theEditor != null)
			theEditor.setCommitOnType(commit);
		return this;
	}

	@Override
	public ObservableTextField<E> setCommitAdjustmentImmediately(boolean commitImmediately) {
		if (theEditor != null)
			theEditor.setCommitAdjustmentImmediately(commitImmediately);
		return this;
	}

	@Override
	public ObservableTextField<E> onEnter(BiConsumer<? super E, ? super KeyEvent> action) {
		if (theEditor != null)
			theEditor.onEnter(action);
		return this;
	}

	@Override
	public ObservableTextField<E> withToolTip(String tooltip) {
		if (theEditor != null)
			theEditor.withToolTip(tooltip);
		else
			super.setToolTipText(tooltip);
		return this;
	}

	/**
	 * @param cols The minimum number of columns of text to display
	 * @return This text field
	 */
	public ObservableTextField<E> withColumns(int cols) {
		setColumns(cols);
		return this;
	}

	/**
	 * @param echoChar The echo character to show for each typed character of the password, or <code>(char) 0</code> to show the text
	 * @return This text field
	 */
	public ObservableTextField<E> asPassword(char echoChar) {
		setEchoChar(echoChar);
		putClientProperty("JPasswordField.cutCopyAllowed", echoChar == 0); // Allow cut/copy for non-password field
		return this;
	}

	/** @return This text field's icon */
	public Icon getIcon() {
		return theIcon;
	}

	/**
	 * @param icon The icon to display on the left side of the text field
	 * @return This text field
	 */
	public ObservableTextField<E> setIcon(Icon icon) {
		theIcon = icon;
		if (theIcon != null) {
			int textX = 2;

			int iconWidth = theIcon.getIconWidth();
			int x = dummyInsets.left + 5;// this is our icon's x
			textX = x + iconWidth + 2; // this is the x where text should start

			setMargin(new Insets(2, textX, 2, 2));
		} else
			setMargin(dummyInsets);
		return this;
	}

	@Override
	public String getEmptyText() {
		return theEmptyText;
	}

	@Override
	public ObservableTextField<E> setEmptyText(String emptyText) {
		theEmptyText = emptyText;
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (theEditor != null)
			theEditor.setEnabled(enabled);
		else
			super.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(String text) {
		if (theEditor != null)
			theEditor.setToolTipText(text);
		else
			super.setToolTipText(text);
	}

	@Override
	public Dimension getMinimumSize() {
		if (getColumns() > 0)
			return getPreferredSize();
		Dimension dim = super.getMinimumSize();
		if (dim != null)
			dim.height = getPreferredSize().height;
		return dim;
	}

	@Override
	public Dimension getMaximumSize() {
		if (getColumns() > 0)
			return getPreferredSize();
		Dimension dim = super.getMaximumSize();
		if (dim != null)
			dim.height = getPreferredSize().height;
		return dim;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (theIcon != null) {
			int iconHeight = theIcon.getIconHeight();
			int x = dummyInsets.left + 5;// this is our icon's x
			int y = (this.getHeight() - iconHeight) / 2;
			theIcon.paintIcon(this, g, x, y);
		}

		if (!this.hasFocus() && this.getPassword().length == 0 && theEmptyText != null) {
			int height = getHeight();
			Font prev = g.getFont();
			Font italic = prev.deriveFont(Font.ITALIC);
			Color prevColor = g.getColor();
			g.setFont(italic);
			g.setColor(UIManager.getColor("textInactiveText"));
			int h = g.getFontMetrics().getHeight();
			int textBottom = (height - h) / 2 + h - 4;
			int x = this.getInsets().left;
			Graphics2D g2d = (Graphics2D) g;
			RenderingHints hints = g2d.getRenderingHints();
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.drawString(theEmptyText, x, textBottom);
			g2d.setRenderingHints(hints);
			g.setFont(prev);
			g.setColor(prevColor);
		}

	}

	/** @return Whether the user has entered text to change this field's value */
	@Override
	public boolean isDirty() {
		if (theEditor == null)
			return false;
		return theEditor.isDirty();
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	@Override
	public void revertEdits() {
		if (theEditor != null)
			theEditor.revertEdits();
	}

	/**
	 * Causes any edits in this field's text to take effect, parsing it and setting it in the value
	 *
	 * @param cause The cause of the action (e.g. a swing event)
	 * @return Whether the edits (if any) were successfully committed to the value or were rejected. If there were no edits, this returns
	 *         true.
	 */
	@Override
	public boolean flushEdits(Object cause) {
		if (theEditor == null)
			return false;
		return theEditor.flushEdits(cause);
	}

	/**
	 * @return The error for the current text of this field. Specifically, either:
	 *         <ol>
	 *         <li>The message in the {@link ParseException} thrown by this field's {@link #getFormat() format} when the text was parsed (or
	 *         "Invalid text" if the exception message was null) or</li>
	 *         <li>The message reported by the value ({@link SettableValue#isAcceptable(Object)}) for the parsed value</li>
	 *         <ol>
	 */
	@Override
	public String getEditError() {
		if (theEditor == null)
			return null;
		return theEditor.getEditError();
	}

	@Override
	public String getEditWarning() {
		if (theEditor == null)
			return null;
		return theEditor.getEditWarning();
	}

	@Override
	public ObservableValue<String> getErrorState() {
		if (theEditor == null)
			return null;
		return theEditor.getErrorState();
	}

	@Override
	public ObservableValue<String> getWarningState() {
		if (theEditor == null)
			return null;
		return theEditor.getWarningState();
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		if (theEditor != null)
			theEditor.redisplayErrorTooltip();
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		if (theEditor != null)
			theEditor.redisplayWarningTooltip();
	}
}
