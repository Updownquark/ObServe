<?xml version="1.0" encoding="UTF-8"?>

<!-- This is a header comment
A bunch of this random junk is in here just to test out the XML parser
blah -->

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Super Simple Quick Demo`" close-action="exit">
	<head>
		<?CONTENTLESS-INTRUCTION?>
		<?INSTRUCTION ?>
		<?INSTRUCTION CONTENT?>
		<models>
			<model name="app">
				<value name="text" type="String">`This is some text`</value>
				<value name="i1" type="int" />
				<value name="i2" type="int" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="qommons-searcher.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline" orientation="vertical" main-align="justify" cross-align="justify">
		<box layout="inline" orientation="horizontal" main-align="center">
			<label>The value from the text field should be reflected in the label to the right</label>
		</box>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<box layout="inline" orientation="horizontal">
				<text-field value="text" />
			</box>
			<box layout="inline" orientation="horizontal" main-align="trailing">
				<label value="text" />
			</box>
		</box>
		<box layout="inline" orientation="horizontal" main-align="center">
			<label>The values in the text field on the right should be the sum of the values in the two text fields on the left</label>
			<label>Edits to the result field should be propagated back to the left-most field</label>
		</box>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<text-field value="i1" />
			<label>+</label>
			<text-field value="i2" />
			<label>=</label>
			<text-field value="i1+i2" />
		</box>
	</box>
</quick>

<!-- This is a footer comment
blah -->

