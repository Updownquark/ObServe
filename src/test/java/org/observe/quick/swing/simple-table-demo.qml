<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Table Demo`" close-action="exit">
	<head>
		<imports>
			<import>org.qommons.Primes</import>
		</imports>
		<models>
			<model name="app">
				<list name="values" type="int">
					<element>49</element>
					<element>512</element>
					<element>269</element>
				</list>
				<value name="newValue" type="int" />
				<value name="primes">new Primes()</value>
				<action-group name="_addPrime">
					<action>app.values.add(app.newValue)</action>
					<action>app.newValue=1</action>
				</action-group>
				<transform name="addPrime" source="_addPrime">
					<disable with="newValue&lt;=1 ? `Value must be greater than 1` : null" />
				</transform>
				<transform name="valueSize" source="values">
					<size />
				</transform>
				<value name="selected" type="Integer" />
				<list name="allSelected" type="Integer" />
				<value name="_allSelectedV">allSelected</value>
				<transform name="allSelectedValue" source="_allSelectedV">
					<refresh on="allSelected" />
				</transform>
				<hook name="hook" on="valueSize">System . out . println(valueSize+" values")</hook>
			</model>
		</models>
		<style-sheet>
			<import-style-sheet name="tests" ref="quick-tests.qss" />
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="vertical" cross-align="justify">
		<table rows="app.values" name="Simple Table" selection="app.selected" multi-selection="app.allSelected">
			<titled-border title="`Factored Values`">
				<style attr="border-color" condition="hovered">`green`</style>
				<style condition="pressed">
					<style attr="font-slant">`italic`</style>
					<style attr="font-color">`orange`</style>
				</style>
			</titled-border>
			<column name="`Value`" value="value">
				<label value="columnValue" />
				<column-edit type="replace-row-value" replacement="columnEditValue">
					<text-field />
				</column-edit>
			</column>
			<column name="`Prime`" value="app.primes.factorize(value, 100_000).size()==1" column-value-name="prime"
				header-tooltip="`Whether the value is prime`">
				<check-box value="prime" tooltip="prime ? `Prime` : `Not Prime`">
					<style attr="color">
						<style condition="hovered">`red`</style>
						<style condition="focused">`teal`</style>
						<style condition="focused &amp;&amp; hovered">`purple`</style>
					</style>
					<on-type>System.out.println("Typed "+typedChar)</on-type>
				</check-box>
			</column>
			<column name="`Factorization`" value="Primes.formatFactorization(app.primes.factorize(value, 100_000))"
				header-tooltip="&quot;The prime factors of each of the &quot;+app.valueSize+&quot; values&quot;">
				<label value="columnValue">
					<style attr="color" condition="hovered">`aqua`</style>
					<style attr="font-weight" condition="rightPressed">`bold`</style>
				</label>
			</column>
			<multi-value-action icon="&quot;icons/remove.png&quot;" as-popup="true" tooltip="`Remove selected rows`">
				app.values.removeAll(actionValues)
			</multi-value-action>
		</table>
		<box layout="inline-layout" orientation="horizontal" main-align="justify">
			<text-field value="app.newValue" commit-on-type="true">
				<style condition="pressed">
					<style attr="font-weight">`ultra-bold`</style>
					<style attr="font-color">`purple`</style>
				</style>
			</text-field>
			<button action="app.addPrime">`Add`</button>
		</box>
		<box layout="inline-layout" orientation="horizontal">
			<label>Selection:</label>
			<label value="app.selected" />
		</box>
		<box layout="inline-layout" orientation="horizontal">
			<label>All Selection:</label>
			<label value="app.allSelectedValue" />
		</box>
	</box>
</quick>
