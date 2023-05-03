package org.observe.quick;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoTesting.ExpressoTest;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.quick.style.StyleQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** Interpretation for {@link QuickTests} */
public class TestInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Quick-Testing";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.modifyWith("quick-test", ExpressoTest.class, new Expresso.ElementModelAugmentation<ExpressoTest>() {
			@Override
			public void augmentElementModel(ExpressoQIS session, ObservableModelSet.Builder builder) throws QonfigInterpretationException {
				ExpressoQIS bodySession = session.forChildren("body").getFirst();
				QuickWidget.Def<?> bodyDef = bodySession.interpret(QuickWidget.Def.class).update(bodySession);
				String quickModelName = bodySession.getAttributeText("name");
				ObservableModelSet.Builder quickModel = builder.createSubModel(quickModelName);
				QuickWidget.Interpreted<?>[] bodyInterpreted = new QuickWidget.Interpreted[1];
				quickModel.withMaker("$body$", CompiledModelValue.of("$body$", ModelTypes.Value, () -> {
					bodyInterpreted[0] = bodyDef.interpret(null).update(new QuickStyledElement.QuickInterpretationCache());
					TypeToken<QuickWidget> widgetType = (TypeToken<QuickWidget>) bodyInterpreted[0].getWidgetType();
					return ModelValueSynth.of(ModelTypes.Value.forType(widgetType), //
						msi -> SettableValue.of(widgetType, bodyInterpreted[0].create(null)//
							.update(msi), "Widgets are not settable"));
				}));
				populateQuickModel(quickModel, bodyDef, quickModelName, //
					(ModelComponentNode<SettableValue<?>, SettableValue<QuickWidget>>) quickModel.getComponentIfExists("$body$"),
					bodyInterpreted, new ArrayList<>());

			}
		});
		return interpreter;
	}

	static <W extends QuickWidget> void populateQuickModel(ObservableModelSet.Builder quickModel, QuickWidget.Def<?> widget,
		String modelName, ModelComponentNode<SettableValue<?>, SettableValue<QuickWidget>> bodyValue,
		QuickWidget.Interpreted<?>[] interpretedRoot, List<QuickWidget.Def<?>> path) {
		if (widget.getParent() != null && widget.getName() != null) {
			QuickWidget.Def<?>[] widgetPath = path.toArray(new QuickWidget.Def[path.size() + 1]);
			widgetPath[path.size()] = widget;
			quickModel.withMaker(widget.getName(), CompiledModelValue.of(widget.getName(), ModelTypes.Value, () -> {
				InterpretedValueSynth<SettableValue<?>, SettableValue<QuickWidget>> bodySynth = bodyValue.interpret();
				TypeToken<W> widgetType = (TypeToken<W>) find(interpretedRoot[0], widgetPath, 0).getWidgetType();
				return ModelValueSynth.of(ModelTypes.Value.forType(widgetType), models -> {
					QuickWidget body = bodySynth.get(models).get();
					return SettableValue.of(widgetType, (W) find(body, widgetPath, 0), "Widgets are not settable");
				});
			}));
		}
		if (widget instanceof QuickContainer2.Def) {
			if (widget.getParent() != null)
				path.add(widget);
			for (QuickWidget.Def<?> child : ((QuickContainer2.Def<?, ?>) widget).getContents())
				populateQuickModel(quickModel, child, modelName, bodyValue, interpretedRoot, path);
			if (widget.getParent() != null)
				path.remove(path.size() - 1);
		}
	}

	private static QuickWidget.Interpreted<?> find(QuickWidget.Interpreted<?> parent, QuickWidget.Def<?>[] widgetPath, int pathIndex) {
		if (pathIndex == widgetPath.length)
			return parent;
		for (QuickWidget.Interpreted<?> child : ((QuickContainer2.Interpreted<?, ?>) parent).getContents()) {
			if (child.getDefinition() == widgetPath[pathIndex])
				return find(child, widgetPath, pathIndex + 1);
		}
		throw new IllegalStateException("Could not find widget synth for " + widgetPath[widgetPath.length - 1].getName());
	}

	private static QuickWidget find(QuickWidget parent, QuickWidget.Def<?>[] widgetPath, int pathIndex) {
		if (pathIndex == widgetPath.length)
			return parent;
		for (QuickWidget child : ((QuickContainer2<?>) parent).getContents()) {
			if (child.getInterpreted().getDefinition() == widgetPath[pathIndex])
				return find(child, widgetPath, pathIndex + 1);
		}
		throw new IllegalStateException("Could not find widget " + widgetPath[widgetPath.length - 1].getName());
	}
}
