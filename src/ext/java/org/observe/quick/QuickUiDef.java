package org.observe.quick;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import javax.swing.JDialog;
import javax.swing.JFrame;

import org.observe.Observable;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.WindowBuilder;
import org.observe.util.swing.WindowPopulation;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.ex.CheckedExceptionWrapper;

public class QuickUiDef {
	private final QuickDocument theDocument;
	private final ExternalModelSet theExternalModels;
	private ModelSetInstance theModels;
	private Observable<?> theUntil;

	QuickUiDef(QuickDocument doc, ExternalModelSet extModels) {
		theDocument = doc;
		theExternalModels = extModels;
	}

	public QuickDocument getDocument() {
		return theDocument;
	}

	public QuickUiDef withUntil(Observable<?> until) {
		if (theUntil != null)
			throw new IllegalStateException("Until has already been installed");
		theUntil = until;
		return this;
	}

	public Observable<?> getUntil() {
		if (theUntil == null)
			theUntil = Observable.empty();
		return theUntil;
	}

	public ModelSetInstance getModels() throws QonfigEvaluationException {
		if (theModels == null)
			theModels = theDocument.getHead().getModels().createInstance(theExternalModels, getUntil()).build();
		return theModels;
	}

	public JFrame createFrame() throws QonfigEvaluationException {
		return install(new JFrame());
	}

	public JFrame install(JFrame frame) throws QonfigEvaluationException {
		return install(WindowPopulation.populateWindow(frame, getUntil(), false, false)).getWindow();
	}

	public JFrame run(JFrame frame, Component relativeTo) throws QonfigEvaluationException {
		if (frame == null)
			frame = new JFrame();
		PanelPopulation.WindowBuilder<JFrame, ?> window = WindowPopulation.populateWindow(frame, getUntil(), false, false);
		install(window);
		window.run(relativeTo);
		return window.getWindow();
	}

	public JDialog install(JDialog dialog) throws QonfigEvaluationException {
		return install(WindowPopulation.populateDialog(dialog, getUntil(), false)).getWindow();
	}

	public <W extends Window> WindowBuilder<W, ?> install(WindowBuilder<W, ?> builder) throws QonfigEvaluationException {
		if (theDocument.getTitle() != null)
			builder.withTitle(theDocument.getTitle().get(getModels()));
		if (theDocument.getIcon() != null)
			builder.withIcon(theDocument.getIcon().get(getModels()));
		if (theDocument.getVisible() != null)
			builder.withVisible(theDocument.getVisible().get(getModels()));
		if (theDocument.getX() != null)
			builder.withX(theDocument.getX().get(getModels()));
		if (theDocument.getY() != null)
			builder.withY(theDocument.getY().get(getModels()));
		if (theDocument.getWidth() != null)
			builder.withWidth(theDocument.getWidth().get(getModels()));
		if (theDocument.getHeight() != null)
			builder.withHeight(theDocument.getHeight().get(getModels()));
		builder.withCloseAction(theDocument.getCloseAction());
		try {
			builder.withHContent(new BorderLayout(), content -> {
				try {
					installContent(content);
				} catch (QonfigEvaluationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		} catch (CheckedExceptionWrapper e) {
			throw (QonfigEvaluationException) e.getCause();
		}
		builder.run(null);
		return builder;
	}

	public void installContent(Container container) throws QonfigEvaluationException {
		installContent(PanelPopulation.populatePanel(container, getUntil()));
	}

	public QuickComponent installContent(PanelPopulation.PanelPopulator<?, ?> container) throws QonfigEvaluationException {
		QuickComponent.Builder root = QuickComponent.build(theDocument.getComponent(), null, getModels());
		theDocument.getComponent().install(container, root);
		return root.build();
	}
}