package org.observe.util.swing;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.ParseException;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.observe.Observable;
import org.observe.SettableValue;
import org.qommons.io.Format;

public class ObservableTextField<E> extends JTextField {
	private final SettableValue<E> theValue;
	private final Format<E> theFormat;
	private boolean isInternallyChanging;
	private boolean isDirty;

	private final Color normal_bg = getBackground();
	private final Color disabled_bg;
	private final Color error_bg = new Color(255, 200, 200);
	private final Color error_disabled_bg = new Color(200, 150, 150);

	private String theError;
	private String theToolTip;

	public ObservableTextField(SettableValue<E> value, Format<E> format, Observable<?> until) {
		theValue = value;
		theFormat = format;
		if (until == null)
			until = Observable.empty;

		setEnabled(false);
		disabled_bg = getBackground();
		setEnabled(true);

		theValue.changes().takeUntil(until).act(evt -> {
			if (!isInternallyChanging)
				setValue(evt.getNewValue());
		});
		theValue.isEnabled().changes().takeUntil(until).act(evt -> {
			setEnabled(evt.getNewValue() == null);
			setErrorState(theError);
		});
		getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				if (!isInternallyChanging)
					isDirty = true;
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				if (!isInternallyChanging)
					isDirty = true;
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if (!isInternallyChanging)
					isDirty = true;
			}
		});
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					revertEdits();
				else if (e.getKeyCode() == KeyEvent.VK_ENTER)
					flushEdits(e);
			}
		});
	}

	@Override
	public void setToolTipText(String text) {
		theToolTip = text;
		setErrorState(theError);
	}

	public void revertEdits() {
		setValue(theValue.get());
	}

	public void flushEdits(Object cause) {
		if (!isDirty)
			return;
		isDirty = false;
		String text = getText();
		E parsed;
		try {
			parsed = theFormat.parse(text);
			setErrorState(null);
		} catch (ParseException e) {
			setErrorState(e.getMessage() == null ? "Parsing failed" : e.getMessage());
			return;
		}
		isInternallyChanging = true;
		try {
			theValue.set(parsed, cause);
		} catch (IllegalArgumentException | UnsupportedOperationException e) {
			setErrorState(e.getMessage());
		} finally {
			isInternallyChanging = false;
		}
	}

	private void setValue(E value) {
		String formatted;
		try {
			formatted = theFormat.format(value);
			setErrorState(null);
		} catch (RuntimeException e) {
			System.err.println(ObservableTextField.class.getName() + " could not format value " + value);
			e.printStackTrace();
			setErrorState("Could not format value " + value);
			return;
		}
		isInternallyChanging = true;
		try {
			setText(formatted);
		} finally {
			isInternallyChanging = false;
		}
	}

	private void setErrorState(String error) {
		theError = error;
		String disabled = theValue.isEnabled().get();
		if (theError != null) {
			if (disabled != null)
				setBackground(error_disabled_bg);
			else
				setBackground(error_bg);
		} else if (disabled != null)
			setBackground(disabled_bg);
		else
			setBackground(normal_bg);

		if (theError != null)
			super.setToolTipText(theError);
		else if (disabled != null)
			super.setToolTipText(disabled);
		else
			super.setToolTipText(theToolTip);
	}
}
