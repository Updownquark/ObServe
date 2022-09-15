package org.observe.quick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.quick.QuickContainer.AbstractQuickContainer;
import org.observe.quick.style.StyleQIS;
import org.observe.util.TypeTokens;
import org.observe.util.swing.CategoryRenderStrategy;
import org.observe.util.swing.JustifiedBoxLayout;
import org.observe.util.swing.PanelPopulation;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** Default interpretation for the Quick-X toolkit */
public class QuickX implements QonfigInterpretation {
	/** The name of the toolkit this interpreter is for */
	public static final String NAME = "Quick-X";
	/** The version of the toolkit this interpreter is for */
	public static final Version VERSION = new Version(0, 1, 0);

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("collapse-pane", QuickComponentDef.class, session -> interpretCollapsePane(session.as(StyleQIS.class)))//
			.createWith("tree-table", QuickComponentDef.class, session -> interpretTreeTable(session.as(StyleQIS.class)))//
		;
		return interpreter;
	}

	private QuickComponentDef interpretCollapsePane(StyleQIS session) throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> collapsed = exS.getAttributeAsValue("collapsed", boolean.class,
			() -> mis -> SettableValue.build(boolean.class).withDescription("collapsed").build());
		Boolean initCollapsed = session.getAttribute("init-collapsed", Boolean.class);
		QuickComponentDef header = session.interpretChildren("header", QuickComponentDef.class).getFirst();
		QuickComponentDef content = session.interpretChildren("content", QuickComponentDef.class).getFirst(); // Single content
		return new AbstractQuickContainer(session, Arrays.asList(header, content)) {
			@Override
			public QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
				Consumer<PanelPopulator<?, ?>> populator) {
				container.addCollapsePanel(false, new JustifiedBoxLayout(true).mainJustified().crossJustified(), cp -> {
					SettableValue<Boolean> collapsedV = collapsed.get(builder.getModels());
					if (initCollapsed != null)
						collapsedV.set(initCollapsed, null);
					cp.withCollapsed(collapsedV);
					QuickComponent.Builder headerBuilder = QuickComponent.build(header, builder, builder.getModels());
					QuickComponent.Builder contentBuilder = QuickComponent.build(content, builder, builder.getModels());
					cp.animated(false);
					cp.withHeader(hp -> {
						builder.withChild(header.install(hp, headerBuilder));
					});
					builder.withChild(content.install(cp, contentBuilder));
					modify(cp, builder);
				});
				return builder.build();
			}
		};
	}

	private <T, E extends PanelPopulation.TreeTableEditor<T, E>> QuickComponentDef interpretTreeTable(StyleQIS session)
		throws QonfigInterpretationException {
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		return QuickBase.interpretAbstractTree(session, new QuickBase.TreeMaker<T, E>() {
			TypeToken<CategoryRenderStrategy<BetterList<T>, ?>> columnType;
			Function<ModelSetInstance, ObservableCollection<CategoryRenderStrategy<BetterList<T>, ?>>> columnsAttr;
			List<QuickBase.Column<BetterList<T>, ?>> columns = new ArrayList<>();

			@Override
			public void configure(ObservableModelSet model, ValueContainer<SettableValue<?>, ? extends SettableValue<T>> root)
				throws QonfigInterpretationException {
				TypeToken<T> type = (TypeToken<T>) root.getType().getType(0);
				columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class).<CategoryRenderStrategy<BetterList<T>, ?>> parameterized(//
					TypeTokens.get().keyFor(BetterList.class).parameterized(type), TypeTokens.get().WILDCARD);
				columnsAttr = exS.getAttribute("columns", ModelTypes.Collection.forType(columnType), null);
				session.put("model-type", type);
				for (StyleQIS columnEl : session.forChildren("column"))
					columns.add(columnEl.interpret(QuickBase.Column.class));
			}

			@Override
			public void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
				Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData) {
				container.addTreeTable2(root, children, t -> {
					treeData.accept((E) t);
					if (columnsAttr != null) {
						// The flatten here is so columns can also be specified on the table.
						// Without this, additional columns could not be added if, as is likely, the columnsAttr collection is unmodifiable.
						t.withColumns(ObservableCollection.flattenCollections(columnType, //
							columnsAttr.apply(builder.getModels()), //
							ObservableCollection.build(columnType).build()).collect());
					}
					for (QuickBase.Column<BetterList<T>, ?> column : columns)
						t.withColumn(column.createColumn(builder.getModels()));
				});
			}
		});
	}
}
