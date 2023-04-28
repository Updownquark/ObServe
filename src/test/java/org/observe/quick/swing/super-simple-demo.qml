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
				<value name="text" type="String" init="`This is some text`" />
				<value name="i1" type="int" />
				<value name="i2" type="int" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline" orientation="vertical" main-align="justify" cross-align="justify">
		<box layout="inline" orientation="horizontal" main-align="center">
			<label>The value from the text field should be reflected in the label to the right</label>
		</box>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<box layout="inline" orientation="horizontal">
				<text-field value="app.text" />
			</box>
			<box layout="inline" orientation="horizontal" main-align="trailing">
				<label value="app.text" />
			</box>
		</box>
		<label>The values in the text field on the right should be the sum of the values in the two text fields on the left</label>
		<label>Edits to the result field should be propagated back to the left-most field</label>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<text-field value="app.i1" />
			<label>+</label>
			<text-field value="app.i2" />
			<label>=</label>
			<text-field value="app.i1+app.i2" />
		</box>
	</box>
</quick>

<!-- This is a footer comment
blah -->

