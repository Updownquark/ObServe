package org.observe.quick.style;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoV0_1;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

public class QuickStyleSet {

	interface StyleModelValues {
		<T> StyleModelValues withModelValue(QuickModelValue<T> modelValue, SettableValue<T> value);
	}

	private final QonfigToolkit theCoreToolkit;
	private final QonfigAddOn theStyledAddOn;
	private final QonfigElementDef theWidgetEl;
	private final Map<QonfigElementOrAddOn, QuickStyleType> theStyleTypes;

	public QuickStyleSet(QonfigToolkit core) {
		theCoreToolkit = core;
		theStyledAddOn = core.getAddOn("styled");
		theWidgetEl = core.getElement("widget");
		theStyleTypes = new HashMap<>();
	}

	public QonfigToolkit getCoreToolkit() {
		return theCoreToolkit;
	}

	public QonfigAddOn getStyled() {
		return theStyledAddOn;
	}

	public QonfigElementDef getWidget() {
		return theWidgetEl;
	}

	public synchronized QuickStyleType styled(QonfigElementOrAddOn element, ExpressoQIS session)
		throws QonfigInterpretationException {
		QuickStyleType styled = theStyleTypes.get(element);
		if (styled != null)
			return styled;
		else if (session == null)
			return null;
		else if (!theStyledAddOn.isAssignableFrom(element))
			return null;
		List<QuickStyleType> parents = new ArrayList<>();
		QonfigElementOrAddOn superEl = element.getSuperElement();
		if (superEl != null) {
			QuickStyleType parent = styled(superEl, session);
			if (parent != null)
				parents.add(parent);
		}
		for (QonfigAddOn inh : element.getInheritance()) {
			QuickStyleType parent = styled(inh, session);
			if (parent != null)
				parents.add(parent);
		}
		if (parents.isEmpty())
			parents = Collections.emptyList();
		else
			parents = Collections.unmodifiableList(parents);
		Map<String, QuickModelValue<?>> declaredModelValues = new LinkedHashMap<>();
		Map<String, QuickModelValue<?>> modelValues = new LinkedHashMap<>();
		Map<String, QuickStyleAttribute<?>> declaredAttributes = new LinkedHashMap<>();
		BetterMultiMap<String, QuickStyleAttribute<?>> attributes = BetterHashMultiMap.<String, QuickStyleAttribute<?>> build().buildMultiMap();
		styled = new QuickStyleType(this, element, parents, Collections.unmodifiableMap(declaredModelValues),
			Collections.unmodifiableMap(modelValues), Collections.unmodifiableMap(declaredAttributes),
			BetterCollections.unmodifiableMultiMap(attributes));

		QonfigElement modelEl = element.getMetadata().getRoot().getChildrenByRole()
			.get(theCoreToolkit.getAddOn("styled").getMetaSpec().getChild("widget-model").getDeclared()).peekFirst();
		if (modelEl != null) {
			QonfigInterpreterCore.Builder interpreterBuilder = QonfigInterpreterCore.build(QuickStyleSet.class, theCoreToolkit)
				.forToolkit(theCoreToolkit);
			configureModelInterpretation(interpreterBuilder, session);
			QonfigAttributeDef.Declared nameAttr = theCoreToolkit.getAddOn("model-element").getAttribute("name").getDeclared();
			QonfigAttributeDef.Declared typeAttr = theCoreToolkit.getElement("model-value").getAttribute("type").getDeclared();
			QonfigAttributeDef.Declared priorityAttr = theCoreToolkit.getAddOn("widget-model-value").getAttribute("priority")
				.getDeclared();
			for (QonfigElement modelV : modelEl.getChildrenInRole(theCoreToolkit, "model", "value")) {
				String name = modelV.getAttributeText(nameAttr);
				if (declaredModelValues.containsKey(name)) {
					session.withError("Multiple model values named '" + name + "' declared");
					continue;
				}
				TypeToken<?> type;
				try {
					type = TypeTokens.get().parseType(modelV.getAttributeText(typeAttr));
				} catch (IllegalArgumentException | ParseException e) {
					throw new QonfigInterpretationException("Could not parse type of style model value '" + name + "'", e);
				}
				declaredModelValues.put(name, new QuickModelValue<>(styled, name, type, //
					Integer.parseInt(modelV.getAttributeText(priorityAttr))));
			}
		}

		QonfigElement stylesEl = element.getMetadata().getRoot().getChildrenByRole()
			.get(theCoreToolkit.getAddOn("styled").getMetaSpec().getChild("styles").getDeclared()).peekFirst();
		if (stylesEl != null) {
			QonfigAttributeDef.Declared nameAttr = theCoreToolkit.getElement("style-attribute").getAttribute("name").getDeclared();
			QonfigAttributeDef.Declared typeAttr = theCoreToolkit.getElement("style-attribute").getAttribute("type").getDeclared();
			QonfigAttributeDef.Declared trickleAttr = theCoreToolkit.getElement("style-attribute").getAttribute("trickle-down")
				.getDeclared();
			for (QonfigElement styleAttr : stylesEl.getChildrenInRole(theCoreToolkit, "styles", "style-attribute")) {
				String name = styleAttr.getAttributeText(nameAttr);
				if (declaredAttributes.containsKey(name)) {
					session.withError("Multiple style attributes named '" + name + "' declared");
					continue;
				}
				declaredAttributes.put(name, new QuickStyleAttribute<>(styled, name, //
					ExpressoV0_1.parseType(styleAttr.getAttributeText(typeAttr), session.getExpressoEnv()), //
					styleAttr.getAttribute(trickleAttr, boolean.class)));
			}
		}
		modelValues.putAll(declaredModelValues);
		attributes.putAll(declaredAttributes);

		for (QuickStyleType parent : parents) {
			for (QuickModelValue<?> mv : parent.getModelValues().values()) {
				if (modelValues.containsKey(mv.getName()))
					session.withWarning("Model value " + mv + " is name-eclipsed by " + modelValues.get(mv.getName()));
				else
					modelValues.putIfAbsent(mv.getName(), mv);
			}
			attributes.putAll(parent.getAttributes());
		}
		theStyleTypes.put(element, styled);
		return styled;
	}

	public Set<QuickModelValue<?>> getModelValues(QonfigElement element, ExpressoQIS session) throws QonfigInterpretationException {
		Set<QuickModelValue<?>> modelValues = new LinkedHashSet<>();
		QuickStyleType type = styled(element.getType(), session);
		if (type != null)
			modelValues.addAll(type.getModelValues().values());
		for (QonfigAddOn inh : element.getInheritance().values()) {
			type = styled(inh, session);
			if (type != null)
				modelValues.addAll(type.getModelValues().values());
		}
		return Collections.unmodifiableSet(modelValues);
	}

	private void configureModelInterpretation(QonfigInterpreterCore.Builder interpreterBuilder, ExpressoQIS session) {
		interpreterBuilder.createWith("action", ModelInstanceType.class, session2 -> {
			return ModelTypes.Action
				.forType(ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("value", ModelInstanceType.class, session2 -> {
			return ModelTypes.Value
				.forType(ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("list", ModelInstanceType.class, session2 -> {
			return ModelTypes.Collection
				.forType(ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("sorted-list", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedCollection
				.forType(ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("set", ModelInstanceType.class, session2 -> {
			return ModelTypes.Set
				.forType(ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("sorted-set", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedSet
				.forType(ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv()));
		}).createWith("map", ModelInstanceType.class, session2 -> {
			return ModelTypes.Map.forType(//
				ExpressoV0_1.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		}).createWith("sorted-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedMap.forType(//
				ExpressoV0_1.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		}).createWith("multi-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.MultiMap.forType(//
				ExpressoV0_1.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		}).createWith("sorted-multi-map", ModelInstanceType.class, session2 -> {
			return ModelTypes.SortedMultiMap.forType(//
				ExpressoV0_1.parseType(session2.getAttributeText("key-type"), session.getExpressoEnv()), //
				ExpressoV0_1.parseType(session2.getAttributeText("type"), session.getExpressoEnv())//
				);
		});
	}
}
