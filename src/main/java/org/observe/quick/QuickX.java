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
import org.observe.expresso.DynamicModelValue;
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
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.ex.CheckedExceptionWrapper;

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
			TypeToken<BetterList<T>> modelType;
			String pathName;
			ValueContainer<SettableValue<?>, SettableValue<BetterList<T>>> pathValue;
			ValueContainer<SettableValue<?>, SettableValue<Integer>> columnIndex;

			TypeToken<CategoryRenderStrategy<BetterList<T>, ?>> columnType;
			ValueContainer<ObservableCollection<?>, ObservableCollection<CategoryRenderStrategy<BetterList<T>, ?>>> columnsAttr;
			List<QuickBase.Column<BetterList<T>, ?>> columns = new ArrayList<>();

			@Override
			public void configure(ObservableModelSet model, ValueContainer<SettableValue<?>, ? extends SettableValue<T>> root)
				throws QonfigInterpretationException {
				modelType = TypeTokens.get().keyFor(BetterList.class).<BetterList<T>> parameterized(root.getType().getType(0));
				session.put(QuickBase.MODEL_TYPE_KEY, modelType);
				pathName = exS.getAttributeText("path-name");
				pathValue = exS.getExpressoEnv().getModels().getValue(pathName,
					ModelTypes.Value.forType(TypeTokens.get().keyFor(BetterList.class).<BetterList<T>> parameterized(modelType)));
				DynamicModelValue.satisfyDynamicValueType(pathName, exS.getExpressoEnv().getModels(), ModelTypes.Value.forType(modelType));
				columnIndex = exS.getExpressoEnv().getModels().getValue("columnIndex", ModelTypes.Value.INT);

				columnType = TypeTokens.get().keyFor(CategoryRenderStrategy.class).<CategoryRenderStrategy<BetterList<T>, ?>> parameterized(//
					TypeTokens.get().keyFor(BetterList.class).parameterized(modelType), TypeTokens.get().WILDCARD);
				columnsAttr = exS.getAttribute("columns", ModelTypes.Collection.forType(columnType), null);
				session.put("model-type", modelType);
				for (StyleQIS columnEl : session.forChildren("column"))
					columns.add(columnEl.interpret(QuickBase.Column.class));
			}

			@Override
			public void makeTree(QuickComponent.Builder builder, PanelPopulator<?, ?> container, ObservableValue<T> root,
				Function<? super BetterList<T>, ? extends ObservableCollection<? extends T>> children, Consumer<E> treeData)
					throws QonfigEvaluationException {
				try {
					container.addTreeTable2(root, children, t -> {
						treeData.accept((E) t);
						if (columnsAttr != null) {
							// The flatten here is so columns can also be specified on the table.
							// Without this, additional columns could not be added if, as is likely, the columnsAttr collection is
							// unmodifiable.
							try {
								t.withColumns(ObservableCollection.flattenCollections(columnType, //
									columnsAttr.get(builder.getModels()), //
									ObservableCollection.build(columnType).build()).collect());
							} catch (QonfigEvaluationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						}
						for (QuickBase.Column<BetterList<T>, ?> column : columns)
							t.withColumn(column.createColumn(builder, () -> {
								try {
									ModelSetInstance columnModels = builder.getModels().copy().build();
									SettableValue<BetterList<T>> modelValueI = SettableValue.build(modelType).build();
									DynamicModelValue.satisfyDynamicValue(pathName, ModelTypes.Value.forType(modelType), columnModels,
										modelValueI);
									DynamicModelValue.satisfyDynamicValue("rowIndex", ModelTypes.Value.INT, columnModels,
										SettableValue.build(int.class).withValue(-1).build());
									DynamicModelValue.satisfyDynamicValue("columnIndex", ModelTypes.Value.INT, columnModels,
										SettableValue.build(int.class).withValue(-1).build());
									return columnModels;
								} catch (QonfigEvaluationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}, (columnModels, modelValueV) -> {
								try {
									pathValue.get(columnModels).set(modelValueV, null);
								} catch (QonfigEvaluationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}, (columnModels, cell) -> {
								try {
									pathValue.get(columnModels).set(cell.getModelValue(), null);
									columnIndex.get(columnModels).set(cell.getColumnIndex(), null);
								} catch (QonfigEvaluationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}, pathValue// builder, builder.getModels()));
								));
					});
				} catch (CheckedExceptionWrapper e) {
					throw (QonfigEvaluationException) e.getCause();
				}
			}
		});
	}
}
