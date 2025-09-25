<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-X v0.1" xmlns:expresso="Expresso-Config v0.1" with-extension="window"
	title="`Text Filtering`" close-action="exit"
	x="config.x" y="config.y" width="config.width" height="config.height">
	<head>
		<imports>
			<import>org.observe.quick.textfilter.*</import>
		</imports>
		<models>
			<config name="config" config-name="text-filter">
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="width" type="int" default="1500" />
				<value name="height" type="int" default="1000" />
				
				<value name="patternsX" type="int" />
				<value name="patternsY" type="int" />
				<value name="patternsW" type="int" default="500" />
				<value name="patternsH" type="int" default="300" />
				
				<value name="searchX" type="int" />
				<value name="searchY" type="int" />
				<value name="searchW" type="int" default="400" />
				<value name="searchH" type="int" default="300" />
				
				<value name="colorX" type="int" />
				<value name="colorY" type="int" />
				<value name="colorW" type="int" default="400" />
				<value name="colorH" type="int" default="300" />
				
				<value name="mainSplit" type="double" default="0.5" />
				
				<value-set name="filters" type="QuickTextFilter" />
				<value name="multiRound" type="boolean" default="true" />
				<value name="preserveUnmatched" type="boolean" default="true" />
				
				<value-set name="searches" type="QuickTextPattern" />
				<value name="searching" type="boolean" default="false" />
			</config>
			<model name="init">
				<value name="i" init="0" />
				<loop name="initConfigFilters" init="i=0" while="i&lt;config.filters.getValues().size()">
					<action>config.filters.getValues().get(i).rebuild()</action>
					<action>i++</action>
				</loop>
				<loop name="initConfigSearches" init="i=0" while="i&lt;config.searches.getValues().size()">
					<action>config.searches.getValues().get(i).rebuild()</action>
					<action>i++</action>
				</loop>
				<hook name="initConfigEntities">{initConfigFilters, initConfigSearches}"</hook>
			</model>
			<model name="app">
				<list name="_filters" type="QuickTextFilter">config.filters.getValues()</list>
				<transform name="filters" source="_filters">
					<refresh on="config.preserveUnmatched || config.multiRound" />
				</transform>
				<list name="searches" type="QuickTextPattern">config.searches.getValues()</list>
				<value name="source" type="String" />
				<value name="replaced" type="String" />
				<value name="filterSetChanged" init="false" />
				<value name="resultStatus" type="String" />
				<action name="addFilter">config.filters.create().create().get().init()</action>
				<hook name="initFirstFilter">filters.isEmpty() ? addFilter : null</hook>
				
				<value name="searchSource" init="true" />
				<value name="sourceSelectAnchor" init="0" />
				<value name="sourceSelectLead" init="0" />
				<value name="replacedSelectAnchor" init="0" />
				<value name="replacedSelectLead" init="0" />
				<action name="addSearch">config.searches.create().create().get().init()</action>
				<hook name="initFirstSearch">searches.isEmpty() ? addSearch : null</hook>
				
				<transform name="anyFiltersDirty" source="filters">
					<refresh-each source-as="filter" on="filter.getUpdate()" />
					<map-to source-as="filter">filter.isDirty()</map-to>
					<reduce source-as="dirty" seed="false" temp-as="temp">temp || dirty</reduce>
				</transform>
				<transform name="anyErrors" source="filters">
					<filter source-as="filter" test="filter.isEnabled()" />
					<refresh-each source-as="filter" on="filter.getUpdate()" />
					<map-to source-as="filter">filter.getError()</map-to>
					<reduce source-as="error" seed="null" temp-as="temp">temp || error</reduce>
					<map-to source-as="error">error==null ? null : `One or more replacement filters is in an error state`</map-to>
				</transform>
				<value name="filterSetDirty">filterSetChanged || anyFiltersDirty</value>
				
				<value name="editingFilterColor" type="QuickTextFilter" />
				
				<value name="i" init="0" />
				<loop name="markFiltersClean" init="i=0" while="i&lt;filters.size()">
					<action>filters.get(i).clean()</action>
					<action>i++</action>
				</loop>
				<action-group name="doApplyFilters">
					<action always-enabled="true">
						replaced=QuickTextFilter.applyPatterns(source, filters, config.multiRound, config.preserveUnmatched, resultStatus)
					</action>
					<action>replacedSelectAnchor=0</action>
					<action>replacedSelectLead=0</action>
					<action>filterSetChanged=false</action>
					<action>markFiltersClean</action>
				</action-group>
				<value name="canApplyFilters">(filterSetDirty ? null : `All Filters Applied`) || anyErrors</value>
				<transform name="applyFilters" source="doApplyFilters">
					<disable with="canApplyFilters" />
				</transform>
				<hook name="applyOnSourceChange" on="source">(!filterSetDirty &amp; canApplyFilters==null) ? doApplyFilters : null</hook>
				<hook name="watchGlobalFilterSettings" on="source || config.multiRound || config.preserveUnmatched">filterSetChanged=true</hook>
			</model>
		</models>
		<style-sheet>
			<import-style-sheet name="base" ref="classpath://org/observe/quick/base/quick-base.qss" />
			<import-style-sheet name="ext" ref="classpath://org/observe/quick/ext/quick-ext.qss" />
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
		<general-dialog visible="config.searching" title="`Search `+(app.searchSource ? `Source` : `Replaced`)+` Text`" modal="false"
			x="config.searchX" y="config.searchY" width="config.searchW" height="config.searchH">
			<scroll>
				<box role="content" layout="inline-layout" orientation="vertical" cross-align="justify">
					<box layout="inline-layout" orientation="horizontal" cross-align="justify">
						<button icon="`/icons/add.png$16x16`" action="app.addSearch" />
					</box>
					<virtual-multi-pane values="app.searches" layout="inline-layout" orientation="vertical"
						main-align="justify" cross-align="justify"
						active-value-name="filter">
						<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
							<model>
								<transform name="filterWithUpdate" source="filter">
									<refresh on="filter.getUpdate()" />
								</transform>
								<field-value name="pattern" source="filterWithUpdate.getPattern()"
									target-as="newValue" save="filterWithUpdate.setPattern(newValue).rebuild()" />
								<field-value name="matchCase" source="filterWithUpdate.isMatchCase()"
									target-as="newValue" save="filterWithUpdate.setMatchCase(newValue).rebuild()" />
								<field-value name="extended" source="filterWithUpdate.isExtended()"
									target-as="newValue" save="filterWithUpdate.setExtended(newValue).rebuild()" />
								<field-value name="regex" source="filterWithUpdate.isRegex()"
									target-as="newValue" save="filterWithUpdate.setRegex(newValue).rebuild()" />
								<field-value name="dotMatchesNewLine" source="filterWithUpdate.isDotMatchesNewLine()"
									target-as="newValue" save="filterWithUpdate.setDotMatchesNewLine(newValue).rebuild()" />
								<value name="error" type="String">filterWithUpdate.getError()</value>
								<value name="status" type="String">filterWithUpdate.getStatus()</value>
								
								<value name="forward" init="true" />
								<action name="_search">filter.search(
									app.searchSource ? app.source : app.replaced, forward,
									app.searchSource ? app.sourceSelectAnchor : app.replacedSelectAnchor,
									app.searchSource ? app.sourceSelectLead : app.replacedSelectLead)
								</action>
								<transform name="search" source="_search">
									<disable with="error" />
								</transform>
								<action-group name="searchForward">
									<action>forward=true</action>
									<action>search</action>
								</action-group>
								<action-group name="searchBackward">
									<action>forward=false</action>
									<action>search</action>
								</action-group>
								<action name="_searchCount">filter.searchCount(app.searchSource ? app.source : app.replaced)</action>
								<transform name="searchCount" source="_searchCount">
									<disable with="error" />
								</transform>
							</model>
							<line-border>
								<style if="error!=null" attr="border-color">`red`</style>
							</line-border>
							<box layout="inline-layout" orientation="horizontal" main-align="justify">
								<label>Find:</label>
								<text-field value="pattern" commit-on-type="true">
									<on-key-press key="Enter">searchForward</on-key-press>
								</text-field>
								<button icon="`/icons/remove.png$16x16`" action="app.searches.remove(filter)" />
							</box>
							<box layout="inline-layout" orientation="horizontal">
								<check-box value="matchCase">`Match Case`</check-box>
								<check-box value="extended">`Extended (\\n, \\t...)`</check-box>
								<check-box value="regex">`Regex`</check-box>
								<check-box value="dotMatchesNewLine" visible="regex">`'.' Matches New Line`</check-box>
							</box>
							<label visible="error!=null" value="error">
								<style attr="font-color">`red`</style>
							</label>
							<box layout="inline-layout" orientation="horizontal" main-align="center" cross-align="justify">
								<button action="searchForward">`Search Forward`"</button>
								<button action="searchBackward">`Search Backward`"</button>
								<button action="searchCount">`Count`"</button>
							</box>
							<label visible="status!=null" value="status" />
						</box>
					</virtual-multi-pane>
				</box>
			</scroll>
		</general-dialog>
		<general-dialog visible="app.editingFilterColor!=null"
			x="config.colorX" y="config.colorY" width="config.colorW" height="config.colorH"
			title="`Modify Filter Color`" modal="false">
			<model>
				<transform name="filterWithUpdate" source="app.editingFilterColor">
					<refresh on="app.editingFilterColor.getUpdate()" />
				</transform>
				<field-value name="color" source="filterWithUpdate.getColor()"
					target-as="newValue" save="filterWithUpdate.setColor(newValue).update()" />
			</model>
			<color-chooser value="color" />
		</general-dialog>
		<general-dialog visible="true" title="`Replacement Filters`" modal="false"
			x="config.patternsX" y="config.patternsY" width="config.patternsW" height="config.patternsH">
			<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
				<box layout="inline-layout" orientation="horizontal">
					<check-box value="config.multiRound">`Multiple Replacement Rounds`</check-box>
					<check-box value="config.preserveUnmatched">`Preserve Unmatched Content`</check-box>
				</box>
				<box layout="inline-layout" orientation="horizontal" cross-align="justify">
					<button icon="`/icons/add.png$16x16`" action="{app.addFilter, app.filterSetChanged=true}" />
				</box>
				<virtual-multi-pane values="app.filters" layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify"
					active-value-name="filter">
					<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
						<model>
							<transform name="filterWithUpdate" source="filter">
								<refresh on="filter.getUpdate()" />
							</transform>
							<field-value name="enabled" source="filterWithUpdate.isEnabled()"
								target-as="newValue" save="filterWithUpdate.setEnabled(newValue).rebuild()" />
							<field-value name="color" source="filterWithUpdate.getColor()"
								target-as="newValue" save="filterWithUpdate.setColor(newValue)" />
							<field-value name="pattern" source="filterWithUpdate.getPattern()"
								target-as="newValue" save="filterWithUpdate.setPattern(newValue).rebuild()" />
							<field-value name="matchCase" source="filterWithUpdate.isMatchCase()"
								target-as="newValue" save="filterWithUpdate.setMatchCase(newValue).rebuild()" />
							<field-value name="extended" source="filterWithUpdate.isExtended()"
								target-as="newValue" save="filterWithUpdate.setExtended(newValue).rebuild()" />
							<field-value name="regex" source="filterWithUpdate.isRegex()"
								target-as="newValue" save="filterWithUpdate.setRegex(newValue).rebuild()" />
							<field-value name="dotMatchesNewLine" source="filterWithUpdate.isDotMatchesNewLine()"
								target-as="newValue" save="filterWithUpdate.setDotMatchesNewLine(newValue).rebuild()" />
							<field-value name="wholeLine" source="filterWithUpdate.isIncludeWholeLine()"
								target-as="newValue" save="filterWithUpdate.setIncludeWholeLine(newValue).update()" />
							<field-value name="printSourcePosition" source="filterWithUpdate.isPrintSourcePosition()"
								target-as="newValue" save="filterWithUpdate.setPrintSourcePosition(newValue).update()" />
							<field-value name="replacement" source="filterWithUpdate.getReplacement()"
								target-as="newValue" save="filterWithUpdate.setReplacement(newValue).rebuild()" />
							<value name="error" type="String">filterWithUpdate.getError()</value>
							<value name="status" type="String">filterWithUpdate.getStatus()</value>
						</model>
						<line-border>
							<style if="error!=null" attr="border-color">`red`</style>
							<style if="filterWithUpdate.isDirty()" attr="border-color">`orange`</style>
						</line-border>
						<box layout="border-layout">
							<check-box region="west" value="enabled" tooltip="`Whether to apply this filter`" />
							<!-- I can't think of a way to color the results at the moment
							<box region="west" layout="simple-layout" width="16" height="24">
								<style attr="color">color</style>
								<on-click>app.editingFilterColor=filter</on-click>
							</box>
							<spacer region="west" width="3" height="20" />
							-->
							<box region="west" layout="inline-layout" orientation="horizontal" main-align="justify" cross-align="center">
								<label>Find:</label>
							</box>
							<text-field region="center" value="pattern" commit-on-type="true" />
							<box region="east" layout="inline-layout" orientation="horizontal" main-align="justify" cross-align="center">
								<label icon="`/icons/remove.png$16x16`">
									<on-click>{app.filters.remove(filter), app.filterSetChanged=true}</on-click>
								</label>
							</box>
						</box>
						<box layout="inline-layout" orientation="horizontal">
							<check-box value="matchCase">`Match Case`</check-box>
							<check-box value="extended">`Extended (\\n, \\t...)`</check-box>
							<check-box value="regex">`Regex`</check-box>
							<check-box value="dotMatchesNewLine" visible="regex">`'.' Matches New Line`</check-box>
						</box>
						<box layout="inline-layout" orientation="horizontal">
							<check-box value="wholeLine" visible="!config.preserveUnmatched">`Include Whole Line`</check-box>
							<check-box value="printSourcePosition" visible="!config.multiRound">`Print Source Position`</check-box>
						</box>
						<field-panel>
							<text-field field-label="`Replace With:`" fill="true" value="replacement" commit-on-type="true" />
						</field-panel>
						<label visible="error!=null" value="error">
							<style attr="font-color">`red`</style>
						</label>
						<label visible="status!=null" value="status" />
					</box>
				</virtual-multi-pane>
				<box layout="inline-layout" orientation="vertical" /> <!-- Just to take up slack -->
				<box layout="inline-layout" orientation="horizontal" main-align="center" cross-align="justify">
					<button action="app.applyFilters">`Apply Filters`"</button>
				</box>
			</box>
		</general-dialog>
		<split orientation="horizontal" split-position="`100%`*config.mainSplit">
			<scroll>
				<model>
					<value name="lines">QuickTextFilterUI.getLineCount(app.source)</value>
				</model>
				<titled-border title="`Source Text (`+lines+` line`+(lines==1 ? `` : `s`)+`)`" />
				<text-area role="row-header" value="QuickTextFilterUI.genLineNumbers(app.source)" editable="false" />
				<text-area role="content" value="app.source" commit-on-type="true"
					selection-anchor="app.sourceSelectAnchor" selection-lead="app.sourceSelectLead">
					<model>
						<hook name="focusListener" on="focused">focused ? app.searchSource=true : null</hook>
					</model>
					<on-key-press key="F">
						<event-filter>ctrlPressed</event-filter>
						config.searching=true
					</on-key-press>
				</text-area>
			</scroll>
			<scroll>
				<model>
					<value name="lines">QuickTextFilterUI.getLineCount(app.replaced)</value>
				</model>
				<titled-border title="`Replaced Text (`+lines+` line`+(lines==1 ? `` : `s`)+`)`" />
				<text-area role="row-header" value="QuickTextFilterUI.genLineNumbers(app.replaced)" editable="false" />
				<text-area role="content" value="app.replaced" editable="false"
					selection-anchor="app.replacedSelectAnchor" selection-lead="app.replacedSelectLead">
					<model>
						<hook name="focusListener" on="focused">focused ? app.searchSource=false : null</hook>
					</model>
					<on-key-press key="F">
						<event-filter>ctrlPressed</event-filter>
						config.searching=true
					</on-key-press>
				</text-area>
			</scroll>
		</split>
		<label visible="app.resultStatus!=null" value="app.resultStatus" />
	</box>
</quick>
