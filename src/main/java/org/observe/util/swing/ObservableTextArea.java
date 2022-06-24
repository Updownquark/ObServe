package org.observe.util.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JEditorPane;
import javax.swing.UIManager;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.util.swing.ObservableTextEditor.ObservableTextEditorWidget;
import org.qommons.io.Format;

/**
 * A text widget that supports multiple lines
 *
 * @param <E> The type of value edited by the text area
 */
public class ObservableTextArea<E> extends JEditorPane implements ObservableTextEditorWidget<E, ObservableTextArea<E>> {
	private final ObservableTextEditor<E> theEditor;
	private boolean isWordWrapped;
	private int theRows;

	private String theEmptyText;

	/**
	 * @param value The value for the text field to interact with
	 * @param format The format to convert the value to text and back
	 * @param until An observable that, when fired will release this text field's resources
	 */
	public ObservableTextArea(SettableValue<E> value, Format<E> format, Observable<?> until) {
		theEditor = new ObservableTextEditor<E>(this, value, format, until, //
			e -> super.setEditable(e), //
			tt -> super.setToolTipText(tt)) {
			@Override
			protected ObservableTextEditor<E> setText(String text) {
				ObservableTextArea.this.setText(text);
				return this;
			}
		}.setSelectAllOnFocus(false);
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
		isWordWrapped = true;
	}

	@Override
	public SettableValue<E> getValue() {
		return theEditor.getValue();
	}

	@Override
	public Format<E> getFormat() {
		return theEditor.getFormat();
	}

	@Override
	public ObservableTextArea<E> withWarning(Function<? super E, String> warning) {
		theEditor.withWarning(warning);
		return this;
	}

	@Override
	public ObservableTextArea<E> clearWarning() {
		theEditor.clearWarning();
		return this;
	}

	@Override
	public boolean isSelectAllOnFocus() {
		return theEditor.isSelectAllOnFocus();
	}

	@Override
	public ObservableTextArea<E> setSelectAllOnFocus(boolean selectAll) {
		theEditor.setSelectAllOnFocus(selectAll);
		return this;
	}

	@Override
	public ObservableTextArea<E> setReformatOnCommit(boolean format) {
		theEditor.setReformatOnCommit(format);
		return this;
	}

	@Override
	public ObservableTextArea<E> setRevertOnFocusLoss(boolean revert) {
		theEditor.setRevertOnFocusLoss(revert);
		return this;
	}

	@Override
	public ObservableTextArea<E> setCommitOnType(boolean commit) {
		theEditor.setCommitOnType(commit);
		return this;
	}

	@Override
	public ObservableTextArea<E> setCommitAdjustmentImmediately(boolean commitImmediately) {
		theEditor.setCommitAdjustmentImmediately(commitImmediately);
		return this;
	}

	@Override
	public ObservableTextArea<E> onEnter(BiConsumer<? super E, ? super KeyEvent> action) {
		theEditor.onEnter(action);
		return this;
	}

	@Override
	public ObservableTextArea<E> withToolTip(String tooltip) {
		theEditor.withToolTip(tooltip);
		return this;
	}

	/**
	 * @param html Whether this text area should use HTML or plain text content type
	 * @return This text area
	 */
	public ObservableTextArea<E> asHtml(boolean html) {
		setContentType(html ? "text/html" : "text/plain");
		return this;
	}

	/**
	 * @param wordWrap Whether this text area should wrap lines that are longer than the text area's width
	 * @return This text area
	 */
	public ObservableTextArea<E> withWordWrap(boolean wordWrap) {
		isWordWrapped = wordWrap;
		return this;
	}

	/**
	 * @param rows The number of rows to display in this text area at once (the height of the widget, in lines of text)
	 * @return This text area
	 */
	public ObservableTextArea<E> withRows(int rows) {
		Graphics2D g = (Graphics2D) getGraphics();
		if (g == null) {
			theRows = rows;
			repaint();
			return this;
		}
		_withRows(rows, g);
		return this;
	}

	private void _withRows(int rows, Graphics2D g) {
		int h = (int) Math.ceil(g.getFont().getLineMetrics("Mgp!q", g.getFontRenderContext()).getHeight());
		setPreferredSize(new Dimension(getPreferredSize().width, h * rows));
		setMinimumSize(new Dimension(getMinimumSize().width, h * rows));
	}

	/** @return The text to display (grayed) when the text field's text is empty */
	@Override
	public String getEmptyText() {
		return theEmptyText;
	}

	/**
	 * @param emptyText The text to display (grayed) when the text field's text is empty
	 * @return This text field
	 */
	@Override
	public ObservableTextArea<E> setEmptyText(String emptyText) {
		theEmptyText = emptyText;
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		theEditor.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(String text) {
		theEditor.setToolTipText(text);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (theRows > 0) {
			_withRows(theRows, (Graphics2D) g);
			theRows = 0;
			invalidate();
			revalidate();
		}

		if (!this.hasFocus() && getText().length() == 0 && theEmptyText != null) {
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
		return theEditor.isDirty();
	}

	/** Undoes any edits in this field's text, reverting to the formatted current value */
	@Override
	public void revertEdits() {
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
		return theEditor.getEditError();
	}

	@Override
	public String getEditWarning() {
		return theEditor.getEditWarning();
	}

	@Override
	public ObservableValue<String> getErrorState() {
		return theEditor.getErrorState();
	}

	@Override
	public ObservableValue<String> getWarningState() {
		return theEditor.getWarningState();
	}

	/** Re-displays the parsing error message from this text field as a tooltip */
	public void redisplayErrorTooltip() {
		theEditor.redisplayErrorTooltip();
	}

	/** Re-displays the warning message from this text field as a tooltip */
	public void redisplayWarningTooltip() {
		theEditor.redisplayWarningTooltip();
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return isWordWrapped || super.getScrollableTracksViewportWidth();
	}
}
