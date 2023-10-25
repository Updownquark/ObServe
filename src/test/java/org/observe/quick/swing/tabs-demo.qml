<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-Base v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Quick Tabs Demo`" close-action="exit" width="window.width" height="window.height">
	<head>
		<models>
			<model name="app">
				<list name="tabSet1" type="String">
					<element>"T11"</element>
					<element>"T12"</element>
				</list>
				<list name="tabSet2" type="String">
					<element>"add"</element>
					<element>"blueO"</element>
					<element>"greenCheck"</element>
				</list>
				<value name="selectedTab" init="&quot;Second&quot;" />
				<value name="isFirstSelected" type="boolean" />
				<value name="isSecondSelected" type="boolean" />
			</model>
			<model name="window">
				<value name="width" init="700" />
				<value name="height" init="650" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<field-panel>
		<text-field field-label="`Selected Tab:`" fill="true" value="app.selectedTab" />
		<label field-label="`First Selected:`" value="app.isFirstSelected" />
		<label field-label="`Second Selected:`" value="app.isSecondSelected" />
		<button action="app.selectedTab=`First`">`Select First`</button>
		<table fill="true" rows="app.tabSet2" active-value-name="row">
			<column name="`Value`" value="row" />
		</table>
		<tabs fill="true" selected="app.selectedTab">
			<box tab-id="&quot;First&quot;" tab-name="`First`" layout="inline-layout" orientation="vertical" cross-align="leading">
				<model>
					<transform name="tabSet1Size" source="app.tabSet1">
						<size />
					</transform>
					<transform name="tabSet2Size" source="app.tabSet2">
						<size />
					</transform>
					<hook name="selectHook" on="tabSelected">app.isFirstSelected=tabSelected</hook>
				</model>
				<label>This is a static tab</label>
				<label value="&quot;There are &quot;+(2+tabSet1Size+tabSet2Size)+&quot; tabs total.&quot;" />
			</box>
			<tab-set values="app.tabSet1">
				<box tab-name="&quot;Tab 1:&quot;+tabId" layout="inline-layout" orientation="vertical" cross-align="leading">
					<label>This is a Dynamic tab</label>
					<box layout="inline-layout" orientation="horizontal">
						<label value="&quot;Its value is : &quot;" />
						<text-field value="tabId" />
					</box>
				</box>
			</tab-set>
			<box tab-id="&quot;Second&quot;" tab-name="`Second`" layout="inline-layout" orientation="vertical" cross-align="leading">
				<model>
					<hook name="selectHook" on="tabSelected">app.isSecondSelected=tabSelected</hook>
				</model>
				<label>This is also a static tab</label>
			</box>
			<tab-set values="app.tabSet2">
				<box tab-name="&quot;Tab 2:&quot;+tabId" tab-icon="&quot;/icons/&quot;+tabId+&quot;.png&quot;"
					on-select="System.out.println(&quot;Selected Tab 2:&quot;+tabId)"
					layout="inline-layout" orientation="vertical" cross-align="leading">
					<label>This is also a Dynamic tab</label>
					<box layout="inline-layout" orientation="horizontal">
						<label value="&quot;Its value is : &quot;" />
						<text-field value="tabId" />
					</box>
				</box>
			</tab-set>
		</tabs>
	</field-panel>
</quick>
