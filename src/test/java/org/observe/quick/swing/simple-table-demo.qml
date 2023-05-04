<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Table Demo`" close-action="exit">
	<head>
		<models>
			<model name="app">
				<list name="values" type="int">
					<element>49</element>
					<element>512</element>
					<element>269</element>
				</list>
				<value name="newValue" type="int" />
				<value name="primes">new org.qommons.Primes()</value>
				<action-group name="_addPrime">
					<action>app.values.add(app.newValue)</action>
					<action>app.newValue=1</action>
				</action-group>
				<transform name="addPrime" source="_addPrime">
					<disable with="newValue&lt;=1 ? `Value must be greater than 1` : null" />
				</transform>
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline" orientation="vertical" cross-align="justify">
		<table rows="app.values">
			<titled-border title="`Factored Values`">
				<style attr="border-color" condition="hovered">`green`</style>
				<style condition="pressed">
					<style attr="font-slant">`italic`</style>
					<style attr="font-color">`orange`</style>
				</style>
			</titled-border>
			<column name="`Value`" value="value" />
			<column name="`Factorization`" value="app.primes.factorize(value, 100_000)" />
		</table>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<text-field value="app.newValue">
				<style condition="pressed">
					<style attr="font-weight">`ultra-bold`</style>
					<style attr="font-color">`purple`</style>
				</style>
			</text-field>
			<button action="app.addPrime">`Add`</button>
		</box>
	</box>
</quick>
