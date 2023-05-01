<?xml version="1.0" encoding="UTF-8"?>

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick TableDemo`" close-action="exit">
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
				<action-group name="addPrime">
					<action>app.values.add(app.newValue)</action>
					<action>app.newValue=1</action>
				</action-group>
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline" orientation="vertical" cross-align="justify">
		<table rows="app.values">
			<column name="`Value`" value="value" />
			<column name="`Factorization`" value="app.primes.factorize(value, 100_000)" />
		</table>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<text-field value="app.newValue" />
			<button action="app.addPrime">`Add`</button>
		</box>
	</box>
</quick>

<!-- This is a footer comment
blah -->

