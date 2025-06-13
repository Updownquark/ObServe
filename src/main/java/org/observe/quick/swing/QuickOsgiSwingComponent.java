package org.observe.quick.swing;

import java.awt.Color;
import java.awt.Container;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import org.observe.expresso.ModelInstantiationException;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickOsgiComponent;
import org.observe.util.swing.FontAdjuster;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.ThreadConstraint;

/** An OSGi component that loads a Quick Swing user interface document */
public abstract class QuickOsgiSwingComponent extends QuickOsgiComponent {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

	private final JScrollPane theScroll;
	private final JPanel thePanel;
	private JTextPane theErrorDisplay;

	/** Creates the component */
	protected QuickOsgiSwingComponent() {
		super(ThreadConstraint.EDT);
		thePanel = new JPanel(new JustifiedBoxLayout(true).mainJustified().crossJustified());
		theScroll = new JScrollPane(theErrorDisplay);
		theScroll.getVerticalScrollBar().setUnitIncrement(10);
		getUntil().act(__ -> {
			while (thePanel.getComponentCount() > 0)
				thePanel.remove(thePanel.getComponentCount() - 1);
		});
	}

	@Override
	protected void error(String message, Throwable x) {
		System.err.println(message);
		if (x != null)
			x.printStackTrace();
		ThreadConstraint.EDT.invoke(() -> {
			if (theErrorDisplay == null) {
				theErrorDisplay = new JTextPane();
				new FontAdjuster().withForeground(Color.red).adjust(theErrorDisplay);
			}
			StringWriter writer = new StringWriter();
			writer.append(DATE_FORMAT.format(new Date())).append('\n');
			writer.append(message);
			if (x != null) {
				writer.append('\n');
				x.printStackTrace(new PrintWriter(writer));
			}
			theErrorDisplay.setText(writer.toString());
			theScroll.setViewportView(theErrorDisplay);
			installComponent(theScroll);
		});
	}

	@Override
	protected void installQuickUI(QuickApplication app, QuickDocument doc) {
		if (!(app instanceof QuickSwingApplication)) {
			error("For Quick UI " + getQuickApp().getAppFile() + ", expected an instance of " + QuickSwingApplication.class.getName()
				+ " but parsed " + app.getClass().getName(), null);
			return;
		}

		PanelPopulator<JPanel, ?> populator = PanelPopulation.populateHPanel(thePanel,
			new JustifiedBoxLayout(false).mainJustified().crossJustified(), getUntil());
		try {
			((QuickSwingApplication) app).populate(doc, populator);
		} catch (ModelInstantiationException e) {
			error("Could not assemble Swing components for Quick UI " + getQuickApp().getAppFile(), e);
			return;
		}
		JPanel panel = populator.getContainer();
		// Need to adjust the default Quick layout
		if (panel.getComponentCount() > 0 && panel.getComponent(0) instanceof Container
			&& ((Container) panel.getComponent(0)).getLayout() instanceof JustifiedBoxLayout) {
			((JustifiedBoxLayout) ((Container) panel.getComponent(0)).getLayout()).mainJustified().crossJustified();
		}
		installComponent(thePanel);
	}

	/** @param component The swing component to display */
	protected abstract void installComponent(JComponent component);
}
