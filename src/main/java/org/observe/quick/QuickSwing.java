package org.observe.quick;

import java.awt.Component;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.observe.ObservableAction;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ExternalModelSet;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.quick.style.StyleQIS;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigParser;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Default interpretation for the Quick-Swing toolkit */
public class QuickSwing implements QonfigInterpretation {
	/** The name of the toolkit this interpreter is for */
	public static final String NAME = "Quick-Swing";
	/** The version of the toolkit this interpreter is for */
	public static final Version VERSION = new Version(0, 1, 0);

	private QuickDocument theDebugDoc;
	private QuickDocument theDebugOverlayDoc;

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter
		.extend(interpreter.getToolkit().getElement("quick"), interpreter.getToolkit().getElement("quick-debug"), QuickDocument.class,
			QuickDocument.class, //
			(doc, session) -> extendQuickDebug(doc, session.as(StyleQIS.class), interpreter.getToolkit()))//
		.modifyWith("quick", QuickDocument.class, (doc, session) -> modifyQuickDocument(doc, session.as(StyleQIS.class)))//
		;
		return interpreter;
	}

	private QuickDocument extendQuickDebug(QuickDocument doc, StyleQIS session, QonfigToolkit swing) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		if (theDebugDoc == null) {
			synchronized (QuickSwing.this) {
				if (theDebugDoc == null) {
					QonfigParser debugParser = new DefaultQonfigParser().withToolkit(swing);
					URL debugXml = QuickSwing.class.getResource("quick-debug.qml");
					try (InputStream in = debugXml.openStream()) {
						theDebugDoc = session.intepretRoot(debugParser.parseDocument(debugXml.toString(), in).getRoot())//
							.interpret(QuickDocument.class);
					} catch (IOException e) {
						throw new QonfigInterpretationException("Could not read quick-debug.qml", e);
					} catch (QonfigParseException e) {
						throw new QonfigInterpretationException("Could not interpret quick-debug.qml", e);
					}
					debugXml = QuickSwing.class.getResource("quick-debug-overlay.qml");
					try (InputStream in = debugXml.openStream()) {
						theDebugOverlayDoc = session.intepretRoot(debugParser.parseDocument(debugXml.toString(), in).getRoot())//
							.interpret(QuickDocument.class);
					} catch (IOException e) {
						throw new QonfigInterpretationException("Could not read quick-debug.qml", e);
					} catch (QonfigParseException e) {
						throw new QonfigInterpretationException("Could not interpret quick-debug.qml", e);
					}
				}
			}
		}

		ValueContainer<SettableValue<?>, SettableValue<Integer>> xVal, yVal, wVal, hVal;
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> vVal;
		xVal = exS.getAttribute("debug-x", ModelTypes.Value.forType(int.class),
			() -> msi -> SettableValue.build(int.class).withDescription("x").withValue(0).build());
		yVal = exS.getAttribute("debug-y", ModelTypes.Value.forType(int.class),
			() -> msi -> SettableValue.build(int.class).withDescription("y").withValue(0).build());
		wVal = exS.getAttribute("debug-width", ModelTypes.Value.forType(int.class),
			() -> msi -> SettableValue.build(int.class).withDescription("w").withValue(0).build());
		hVal = exS.getAttribute("debug-height", ModelTypes.Value.forType(int.class),
			() -> msi -> SettableValue.build(int.class).withDescription("h").withValue(0).build());
		vVal = exS.getAttribute("debug-visible", ModelTypes.Value.forType(boolean.class),
			() -> msi -> SettableValue.build(boolean.class).withDescription("v").withValue(true).build());

		ValueContainer<SettableValue<?>, SettableValue<QuickComponent>> selectedComponent = theDebugDoc.getHead()
			.getModels()
			.get("debug.selectedComponent", ModelTypes.Value.forType(QuickComponent.class));
		// ValueContainer<SettableValue<?>, SettableValue<Integer>> scX, scY, scW, scH;
		// ValueContainer<SettableValue<?>, SettableValue<Boolean>> scV;
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
			public ValueContainer<SettableValue<?>, SettableValue<String>> getTitle() {
				return doc.getTitle();
			}

			@Override
			public void setTitle(ValueContainer<SettableValue<?>, SettableValue<String>> title) {
				doc.setTitle(title);
			}

			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Image>> getIcon() {
				return doc.getIcon();
			}

			@Override
			public void setIcon(ValueContainer<SettableValue<?>, SettableValue<Image>> icon) {
				doc.setIcon(icon);
			}

			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Integer>> getX() {
				return doc.getX();
			}

			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Integer>> getY() {
				return doc.getY();
			}

			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Integer>> getWidth() {
				return doc.getWidth();
			}

			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Integer>> getHeight() {
				return doc.getHeight();
			}

			@Override
			public QuickDocument withBounds(ValueContainer<SettableValue<?>, SettableValue<Integer>> x,
				ValueContainer<SettableValue<?>, SettableValue<Integer>> y, ValueContainer<SettableValue<?>, SettableValue<Integer>> width,
				ValueContainer<SettableValue<?>, SettableValue<Integer>> height) {
				doc.withBounds(x, y, width, height);
				return this;
			}

			@Override
			public ValueContainer<SettableValue<?>, SettableValue<Boolean>> getVisible() {
				return doc.getVisible();
			}

			@Override
			public int getCloseAction() {
				return doc.getCloseAction();
			}

			@Override
			public QuickDocument setVisible(ValueContainer<SettableValue<?>, SettableValue<Boolean>> visible) {
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
							debugUiModels.with("x", ModelTypes.Value.forType(int.class), xVal.get(theContentUi.getModels()));
							debugUiModels.with("y", ModelTypes.Value.forType(int.class), yVal.get(theContentUi.getModels()));
							debugUiModels.with("width", ModelTypes.Value.forType(int.class), wVal.get(theContentUi.getModels()));
							debugUiModels.with("height", ModelTypes.Value.forType(int.class), hVal.get(theContentUi.getModels()));
							debugUiModels.with("ui", ModelTypes.Value.forType(QuickComponent.class), theRoot);
							debugUiModels.with("cursorX", ModelTypes.Value.forType(int.class), theCursorX);
							debugUiModels.with("cursorY", ModelTypes.Value.forType(int.class), theCursorY);
							debugUiModels.with("visible", ModelTypes.Value.forType(boolean.class), vVal.get(theContentUi.getModels()));
						} catch (QonfigInterpretationException e) {
							e.printStackTrace();
						}
						return debugExtModelsBuilder.build();
					}

					ExternalModelSet createOverlayModel() {
						SettableValue<QuickComponent> component = selectedComponent.get(theDebugUi.getModels());
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

	private QuickDocument modifyQuickDocument(QuickDocument doc, StyleQIS session) throws QonfigInterpretationException {
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
