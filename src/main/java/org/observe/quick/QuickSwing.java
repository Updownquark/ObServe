package org.observe.quick;

import java.awt.Component;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.function.Function;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ExpressoInterpreter.ExpressoSession;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.Expresso;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigParser;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.QonfigToolkitAccess;

public class QuickSwing extends QuickBase {
	public static final QonfigToolkitAccess SWING = new QonfigToolkitAccess(QuickSwing.class, "quick-swing.qtd", CORE);

	private QuickDocument theDebugDoc;
	private QuickDocument theDebugOverlayDoc;

	@Override
	public <QIS extends ExpressoInterpreter.ExpressoSession<QIS>, B extends ExpressoInterpreter.Builder<QIS, B>> B configureInterpreter(
		B interpreter) {
		super.configureInterpreter(interpreter);
		QonfigToolkit swing = SWING.get();
		ExpressoInterpreter.Builder<?, ?> tkInt = interpreter.forToolkit(swing);
		tkInt.extend(CORE.get().getElement("quick"), swing.getElement("quick-debug"), QuickDocument.class, QuickDocument.class, //
			this::extendQuickDebug)//
		.modifyWith("quick", QuickDocument.class, this::modifyQuickDocument)//
		;
		return interpreter;
	}

	private QuickDocument extendQuickDebug(QuickDocument doc, ExpressoSession<?> session) throws QonfigInterpretationException {
		if (theDebugDoc == null) {
			synchronized (QuickSwing.this) {
				if (theDebugDoc == null) {
					QonfigParser debugParser = new DefaultQonfigParser().withToolkit(Expresso.EXPRESSO.get(), CORE.get(),
						BASE.get(), SWING.get());
					ExpressoInterpreter<?> debugInterp = configureInterpreter(
						ExpressoInterpreter.build(QuickSwing.class, BASE.get(), SWING.get())).build();
					URL debugXml = QuickSwing.class.getResource("quick-debug.qml");
					try (InputStream in = debugXml.openStream()) {
						theDebugDoc = debugInterp.interpret(debugParser.parseDocument(debugXml.toString(), in).getRoot())//
							.interpret(QuickDocument.class);
					} catch (IOException e) {
						throw new QonfigInterpretationException("Could not read quick-debug.qml", e);
					} catch (QonfigParseException e) {
						throw new QonfigInterpretationException("Could not interpret quick-debug.qml", e);
					}
					debugXml = QuickSwing.class.getResource("quick-debug-overlay.qml");
					try (InputStream in = debugXml.openStream()) {
						theDebugOverlayDoc = debugInterp.interpret(debugParser.parseDocument(debugXml.toString(), in).getRoot())//
							.interpret(QuickDocument.class);
					} catch (IOException e) {
						throw new QonfigInterpretationException("Could not read quick-debug.qml", e);
					} catch (QonfigParseException e) {
						throw new QonfigInterpretationException("Could not interpret quick-debug.qml", e);
					}
				}
			}
		}

		Function<ModelSetInstance, SettableValue<Integer>> xVal, yVal, wVal, hVal;
		Function<ModelSetInstance, SettableValue<Boolean>> vVal;
		ObservableExpression x = session.getAttribute("debug-x", ObservableExpression.class);
		ObservableExpression y = session.getAttribute("debug-y", ObservableExpression.class);
		ObservableExpression w = session.getAttribute("debug-width", ObservableExpression.class);
		ObservableExpression h = session.getAttribute("debug-height", ObservableExpression.class);
		ObservableExpression v = session.getAttribute("debug-visible", ObservableExpression.class);
		if (x != null) {
			xVal = x.evaluate(ModelTypes.Value.forType(int.class), doc.getHead().getModels(), doc.getHead().getImports());
		} else {
			xVal = msi -> SettableValue.build(int.class).withDescription("x").withValue(0).build();
		}
		if (y != null) {
			yVal = y.evaluate(ModelTypes.Value.forType(int.class), doc.getHead().getModels(), doc.getHead().getImports());
		} else {
			yVal = msi -> SettableValue.build(int.class).withDescription("y").withValue(0).build();
		}
		if (w != null) {
			wVal = w.evaluate(ModelTypes.Value.forType(int.class), doc.getHead().getModels(), doc.getHead().getImports());
		} else {
			wVal = msi -> SettableValue.build(int.class).withDescription("w").withValue(0).build();
		}
		if (h != null) {
			hVal = h.evaluate(ModelTypes.Value.forType(int.class), doc.getHead().getModels(), doc.getHead().getImports());
		} else {
			hVal = msi -> SettableValue.build(int.class).withDescription("h").withValue(0).build();
		}
		if (v != null) {
			vVal = v.evaluate(ModelTypes.Value.forType(boolean.class), doc.getHead().getModels(), doc.getHead().getImports());
		} else {
			vVal = msi -> SettableValue.build(boolean.class).withDescription("v").withValue(true).build();
		}

		Function<ModelSetInstance, SettableValue<QuickComponent>> selectedComponent = theDebugDoc.getHead().getModels()
			.get("debug.selectedComponent", ModelTypes.Value.forType(QuickComponent.class));
		// Function<ModelSetInstance, SettableValue<Integer>> scX, scY, scW, scH;
		// Function<ModelSetInstance, SettableValue<Boolean>> scV;
		// scX=msi->

		return new QuickDocument() {
			@Override
			public QonfigElement getElement() {
				return session.getElement();
			}

			@Override
			public QuickHeadSection getHead() {
				return doc.getHead();
			}

			@Override
			public QuickComponentDef getComponent() {
				return doc.getComponent();
			}

			@Override
			public Function<ModelSetInstance, SettableValue<String>> getTitle() {
				return doc.getTitle();
			}

			@Override
			public void setTitle(Function<ModelSetInstance, SettableValue<String>> title) {
				doc.setTitle(title);
			}

			@Override
			public Function<ModelSetInstance, SettableValue<Image>> getIcon() {
				return doc.getIcon();
			}

			@Override
			public void setIcon(Function<ModelSetInstance, SettableValue<Image>> icon) {
				doc.setIcon(icon);
			}

			@Override
			public Function<ModelSetInstance, SettableValue<Integer>> getX() {
				return doc.getX();
			}

			@Override
			public Function<ModelSetInstance, SettableValue<Integer>> getY() {
				return doc.getY();
			}

			@Override
			public Function<ModelSetInstance, SettableValue<Integer>> getWidth() {
				return doc.getWidth();
			}

			@Override
			public Function<ModelSetInstance, SettableValue<Integer>> getHeight() {
				return doc.getHeight();
			}

			@Override
			public QuickDocument withBounds(Function<ModelSetInstance, SettableValue<Integer>> x,
				Function<ModelSetInstance, SettableValue<Integer>> y, Function<ModelSetInstance, SettableValue<Integer>> width,
				Function<ModelSetInstance, SettableValue<Integer>> height) {
				doc.withBounds(x, y, width, height);
				return this;
			}

			@Override
			public Function<ModelSetInstance, SettableValue<Boolean>> getVisible() {
				return doc.getVisible();
			}

			@Override
			public int getCloseAction() {
				return doc.getCloseAction();
			}

			@Override
			public QuickDocument setVisible(Function<ModelSetInstance, SettableValue<Boolean>> visible) {
				doc.setVisible(visible);
				return this;
			}

			@Override
			public void setCloseAction(int closeAction) {
				doc.setCloseAction(closeAction);
			}

			@Override
			public QuickUiDef createUI(ExternalModelSet extModels) {
				return new QuickUiDef(this, extModels) {
					private final QuickUiDef theContentUi;
					private final SettableValue<QuickComponent> theRoot;
					private final SettableValue<Integer> theCursorX;
					private final SettableValue<Integer> theCursorY;
					private final QuickUiDef theDebugUi;
					private final QuickUiDef theDebugOverlayUi;

					{
						theContentUi = doc.createUI(extModels);
						theRoot = SettableValue.build(QuickComponent.class).build();
						theCursorX = SettableValue.build(int.class).withValue(0).build();
						theCursorY = SettableValue.build(int.class).withValue(0).build();
						theDebugUi = theDebugDoc.createUI(createDebugModel());
						theDebugOverlayUi = theDebugOverlayDoc.createUI(createOverlayModel());
					}

					ExternalModelSet createDebugModel() {
						ObservableModelSet.ExternalModelSetBuilder debugExtModelsBuilder = ObservableModelSet
							.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
						try {
							ObservableModelSet.ExternalModelSetBuilder debugUiModels = debugExtModelsBuilder.addSubModel("ext");
							debugUiModels.with("x", ModelTypes.Value.forType(int.class), xVal.apply(theContentUi.getModels()));
							debugUiModels.with("y", ModelTypes.Value.forType(int.class), yVal.apply(theContentUi.getModels()));
							debugUiModels.with("width", ModelTypes.Value.forType(int.class), wVal.apply(theContentUi.getModels()));
							debugUiModels.with("height", ModelTypes.Value.forType(int.class), hVal.apply(theContentUi.getModels()));
							debugUiModels.with("ui", ModelTypes.Value.forType(QuickComponent.class), theRoot);
							debugUiModels.with("cursorX", ModelTypes.Value.forType(int.class), theCursorX);
							debugUiModels.with("cursorY", ModelTypes.Value.forType(int.class), theCursorY);
							debugUiModels.with("visible", ModelTypes.Value.forType(boolean.class), vVal.apply(theContentUi.getModels()));
						} catch (QonfigInterpretationException e) {
							e.printStackTrace();
						}
						return debugExtModelsBuilder.build();
					}

					ExternalModelSet createOverlayModel() {
						SettableValue<QuickComponent> component = selectedComponent.apply(theDebugUi.getModels());
						SettableValue<Integer> x = SettableValue.build(int.class).withDescription("x").withValue(0).build();
						SettableValue<Integer> y = SettableValue.build(int.class).withDescription("y").withValue(0).build();
						SettableValue<Integer> w = SettableValue.build(int.class).withDescription("w").withValue(0).build();
						SettableValue<Integer> h = SettableValue.build(int.class).withDescription("h").withValue(0).build();
						SettableValue<Boolean> v = SettableValue.build(boolean.class).withDescription("v").withValue(false).build();
						ComponentAdapter listener = new ComponentAdapter() {
							@Override
							public void componentResized(ComponentEvent e) {
								w.set(e.getComponent().getWidth(), e);
								h.set(e.getComponent().getHeight(), e);
							}

							@Override
							public void componentMoved(ComponentEvent e) {
								x.set(e.getComponent().getX(), e);
								y.set(e.getComponent().getY(), e);
							}
						};
						component.changes().act(evt -> {
							if (evt.getOldValue() == evt.getNewValue())
								return;
							v.set(evt.getNewValue() != null, evt);
							if (evt.getOldValue() != null)
								evt.getOldValue().getComponent().removeComponentListener(listener);
							if (evt.getNewValue() == null) {
								x.set(0, evt);
								y.set(0, evt);
								w.set(0, evt);
								h.set(0, evt);
							} else {
								Component c = evt.getNewValue().getComponent();
								c.addComponentListener(listener);
								x.set(c.getX(), evt);
								y.set(c.getY(), evt);
								w.set(c.getWidth(), evt);
								h.set(c.getHeight(), evt);
							}
						});
						ObservableModelSet.ExternalModelSetBuilder debugExtModelsBuilder = ObservableModelSet
							.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER);
						try {
							ObservableModelSet.ExternalModelSetBuilder debugUiModels = debugExtModelsBuilder
								.addSubModel("selectedComponent");
							debugUiModels.with("visible", ModelTypes.Value.forType(boolean.class), v);
							debugUiModels.with("x", ModelTypes.Value.forType(int.class), x);
							debugUiModels.with("y", ModelTypes.Value.forType(int.class), y);
							debugUiModels.with("width", ModelTypes.Value.forType(int.class), w);
							debugUiModels.with("height", ModelTypes.Value.forType(int.class), h);
							debugUiModels.with("tooltip", ModelTypes.Value.forType(String.class),
								ObservableModelSet.literal("Not yet implemented", "tooltip"));
							debugUiModels.with("onMouse", ModelTypes.Action.forType(Void.class),
								ObservableAction.of(TypeTokens.get().VOID, evt -> {
									MouseEvent mEvt = (MouseEvent) evt;
									theCursorX.set(mEvt.getX(), evt);
									theCursorY.set(mEvt.getY(), evt);
									return null;
								}));
						} catch (QonfigInterpretationException e) {
							e.printStackTrace();
						}
						return debugExtModelsBuilder.build();
					}

					@Override
					public JFrame install(JFrame frame) {
						frame = super.install(frame);
						JPanel glassPane = new JPanel();
						glassPane.setOpaque(false);
						theDebugOverlayUi.installContent(glassPane);
						theDebugUi.install(new JDialog());
						return frame;
					}
				};
			}
		};
	}

	private QuickDocument modifyQuickDocument(QuickDocument doc, ExpressoSession<?> session) throws QonfigInterpretationException {
		String lAndFClass;
		switch (session.getAttributeText("look-and-feel")) {
		case "system":
			lAndFClass = UIManager.getSystemLookAndFeelClassName();
			break;
		case "cross-platform":
			lAndFClass = UIManager.getCrossPlatformLookAndFeelClassName();
			break;
		default:
			lAndFClass = session.getAttributeText("look-and-feel");
			break;
		}
		try {
			UIManager.setLookAndFeel(lAndFClass);
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			System.err.println("Could not load look-and-feel " + session.getAttributeText("look-and-feel"));
			e.printStackTrace();
		}
		return doc;
	}
}