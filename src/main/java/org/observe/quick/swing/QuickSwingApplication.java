package org.observe.quick.swing;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.quick.QuickApplication;
import org.observe.quick.QuickDocument;
import org.observe.quick.QuickWidget;
import org.observe.quick.swing.QuickSwingPopulator.WindowModifier;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.ex.CheckedExceptionWrapper;

/** A {@link QuickApplication} that creates a {@link JFrame} populated by Quick UI content */
public class QuickSwingApplication implements QuickApplication {
	private final QuickDocument.Interpreted theDocument;
	private final QuickSwingPopulator<QuickWidget> theBody;
	private Map<Class<? extends ExAddOn<?>>, QuickSwingPopulator.WindowModifier<?>> theModifiers;

	/**
	 * @param document The interpreted QuickDocument
	 * @param body The swing populator for the UI body
	 * @param modifiers Available window modifiers by add-on
	 */
	public QuickSwingApplication(QuickDocument.Interpreted document, QuickSwingPopulator<QuickWidget> body,
		Map<Class<? extends ExAddOn<?>>, WindowModifier<?>> modifiers) {
		theDocument = document;
		theBody = body;
		theModifiers = modifiers;
	}

	/** @return The interpreted QuickDocument */
	public QuickDocument.Interpreted getDocument() {
		return theDocument;
	}

	@Override
	public void runApplication(QuickDocument doc, Observable<?> until) throws ModelInstantiationException {
		try {
			EventQueue.invokeAndWait(() -> {
				WindowBuilder<?, ?> w = WindowPopulation.populateWindow(new JFrame(), until, true, true);
				for (Map.Entry<Class<? extends ExAddOn<?>>, QuickSwingPopulator.WindowModifier<?>> modifier : theModifiers.entrySet()) {
					ExAddOn<?> addOn = doc.getAddOn(modifier.getKey());
					if (addOn != null) {
						try {
							((QuickSwingPopulator.WindowModifier<ExAddOn<?>>) modifier.getValue()).modifyWindow(w, addOn);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					} else
						doc.reporting()
						.warn("Interpretation of window modifier " + modifier.getKey().getName() + " found, but add-on not found");
				}
				w.withHContent(new JustifiedBoxLayout(true).mainJustified().crossJustified(), content -> {
					try {
						populate(doc, content);
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
		} catch (CheckedExceptionWrapper e) {
			throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
		} catch (RuntimeException | Error e) {
			doc.reporting().error("Unhandled error", e);
		}
	}

	@Override
	public void update(QuickDocument doc) throws ModelInstantiationException {
		// TODO Auto-generated method stub
	}

	/**
	 * @param doc The instantiated QuickDocument
	 * @param populator The populator for the UI body
	 * @throws ModelInstantiationException If the Quick UI population fails
	 */
	public void populate(QuickDocument doc, PanelPopulator<?, ?> populator) throws ModelInstantiationException {
		theBody.populate(populator, doc.getBody());
	}
}
