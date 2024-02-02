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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.util.swing.ObservableTextEditor.ObservableTextEditorWidget;
import org.qommons.collect.ListenerList;
import org.qommons.io.Format;

/**
 * A text widget that supports multiple lines
 *
 * @param <E> The type of value edited by the text area
 */
public class ObservableTextArea<E> extends JTextPane implements ObservableTextEditorWidget<E, ObservableTextArea<E>> {
	/** A listener for text-positioned mouse events in a {@link ObservableTextArea} */
	public interface TextAreaMouseListener {
		/** @param position The document position of the mouse */
		public void mouseMoved(int position);
	}

	private final ObservableTextEditor<E> theEditor;
	private boolean isWordWrapped;
	private int theRows;

	private String theEmptyText;
	private final ListenerList<TextAreaMouseListener> theMouseListeners;

	/**
	 * @param value The value for the text field to interact with
	 * @param format The format to convert the value to text and back
	 * @param until An observable that, when fired will release this text field's resources
	 */
	public ObservableTextArea(SettableValue<E> value, Format<E> format, Observable<?> until) {
		if (value != null) {
			theEditor = new ObservableTextEditor<E>(this, value, format, until, //
				super::setEditable, //
				super::setToolTipText) {
				@Override
				protected ObservableTextEditor<E> setText(String text) {
					updateText(text);
					return this;
				}
			}.setSelectAllOnFocus(false);
		} else
			theEditor = null;
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
		MouseMotionListener mouseListener = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent evt) {
				int docPos = viewToModel(evt.getPoint());
				if (docPos > 0)
					theMouseListeners.forEach(//
						l -> l.mouseMoved(docPos - 1)); // Index from zero
			}
		};
		theMouseListeners = ListenerList.build().withInUse(inUse -> {
			if (inUse)
				addMouseMotionListener(mouseListener);
			else
				removeMouseMotionListener(mouseListener);
		}).build();
	}

	private void updateText(String text) {
		String oldText = getText();
		if (oldText == null || oldText.length() < 3) {
			setText(text);
			return;
		}
		int s, e;
		for (s = 0; s < text.length() && s < oldText.length() && text.charAt(s) == oldText.charAt(s); s++) {
		}
		if (s == text.length() && s == oldText.length())
			return;// No change
		for (e = 1; e < text.length() && e < oldText.length()
			&& text.charAt(text.length() - e) == oldText.charAt(oldText.length() - e); e++) {
		}
		e--;
		if (s + e < oldText.length()) {
			try {
				getDocument().remove(s, oldText.length() - s - e);
			} catch (BadLocationException x) {
				throw new IllegalStateException(x);
			}
		}
		if (s + e < text.length()) {
			try {
				getDocument().insertString(s, text.substring(s, text.length() - e), null);
			} catch (BadLocationException x) {
				throw new IllegalStateException(x);
			}
		}
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
	public ObservableTextArea<E> withWarning(Function<? super E, String> warning) {
		if (theEditor == null)
			return this;
		theEditor.withWarning(warning);
		return this;
	}

	@Override
	public ObservableTextArea<E> clearWarning() {
		if (theEditor == null)
			return this;
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
	public ObservableTextArea<E> setSelectAllOnFocus(boolean selectAll) {
		if (theEditor == null)
			return this;
		theEditor.setSelectAllOnFocus(selectAll);
		return this;
	}

	@Override
	public ObservableTextArea<E> setReformatOnCommit(boolean format) {
		if (theEditor == null)
			return this;
		theEditor.setReformatOnCommit(format);
		return this;
	}

	@Override
	public ObservableTextArea<E> setRevertOnFocusLoss(boolean revert) {
		if (theEditor == null)
			return this;
		theEditor.setRevertOnFocusLoss(revert);
		return this;
	}

	@Override
	public ObservableTextArea<E> setCommitOnType(boolean commit) {
		if (theEditor == null)
			return this;
		theEditor.setCommitOnType(commit);
		return this;
	}

	@Override
	public ObservableTextArea<E> setCommitAdjustmentImmediately(boolean commitImmediately) {
		if (theEditor == null)
			return this;
		theEditor.setCommitAdjustmentImmediately(commitImmediately);
		return this;
	}

	@Override
	public ObservableTextArea<E> onEnter(BiConsumer<? super E, ? super KeyEvent> action) {
		if (theEditor == null)
			return this;
		theEditor.onEnter(action);
		return this;
	}

	@Override
	public ObservableTextArea<E> withToolTip(String tooltip) {
		if (theEditor == null)
			return this;
		theEditor.withToolTip(tooltip);
		return this;
	}

	/**
	 * @param html Whether this text area should use HTML or plain text content type
	 * @return This text area
	 */
	public ObservableTextArea<E> asHtml(boolean html) {
		String contentType = html ? "text/html" : "text/plain";
		if (contentType.equals(getContentType()))
			return this;
		// When setting the content type, the document is cleared. If the value has already been set, this will result in a blank text area.
		String text = getText();
		setContentType(contentType);
		setText(text);
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
		if (rows > 0) {
			int h = (int) Math.ceil(g.getFont().getLineMetrics("Mgp!q", g.getFontRenderContext()).getHeight());
			setPreferredSize(new Dimension(getPreferredSize().width, h * rows));
			setMinimumSize(new Dimension(getMinimumSize().width, h * rows));
		} else {
			setPreferredSize(null);
			setMinimumSize(null);
		}
	}

	@Override
	public String getEmptyText() {
		return theEmptyText;
	}

	@Override
	public ObservableTextArea<E> setEmptyText(String emptyText) {
		theEmptyText = emptyText;
		return this;
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (theEditor != null)
			theEditor.setEnabled(enabled);
	}

	@Override
	public void setToolTipText(String text) {
		if (theEditor != null)
			theEditor.setToolTipText(text);
		else
			super.setToolTipText(text);
	}

	/**
	 * @param listener A listener for text-positioned mouse events
	 * @return A runnable to remove the listener
	 */
	public Runnable addMouseListener(TextAreaMouseListener listener) {
		return theMouseListeners.add(listener, true);
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

	@Override
	public boolean isDirty() {
		if (theEditor == null)
			return false;
		return theEditor.isDirty();
	}

	@Override
	public void revertEdits() {
		if (theEditor != null)
			theEditor.revertEdits();
	}

	@Override
	public boolean flushEdits(Object cause) {
		if (theEditor == null)
			return false;
		return theEditor.flushEdits(cause);
	}

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

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return isWordWrapped || super.getScrollableTracksViewportWidth();
	}
}
