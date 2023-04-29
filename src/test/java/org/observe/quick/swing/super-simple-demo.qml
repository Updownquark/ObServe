<?xml version="1.0" encoding="UTF-8"?>

<!-- This is a header comment
A bunch of this random junk is in here just to test out the XML parser
blah -->

<quick uses:base="Quick-Base v0.1" uses:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Super Simple Quick Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<?CONTENTLESS-INTRUCTION?>
		<?INSTRUCTION ?>
		<?INSTRUCTION CONTENT?>
		<models>
			<model name="app">
				<value name="text" type="String" init="`This is some text`" />
				<value name="i1" type="int" />
				<value name="i2" type="int" />
				<value name="b" type="boolean" init="true" />
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="w" type="int" />
				<value name="h" type="int" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline" orientation="vertical" cross-align="justify" name="root">
		<box layout="inline" orientation="horizontal" main-align="center" name="box1">
			<label name="label1">The value from the text field should be reflected in the label to the right</label>
		</box>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<box layout="inline" orientation="horizontal">
				<text-field value="app.text" />
			</box>
			<box layout="inline" orientation="horizontal" main-align="trailing">
				<label value="app.text" tooltip="app.text"/>
			</box>
		</box>
		<label>The values in the text field on the right should be the sum of the values in the two text fields on the left</label>
		<label>Edits to the result field should be propagated back to the left-most field</label>
		<box layout="inline" orientation="horizontal" main-align="justify">
			<text-field value="app.i1" tooltip="(app.i1+app.i2)+&quot;-&quot;+app.i2+&quot;=&quot;+app.i1"/>
			<label>+</label>
			<text-field value="app.i2" />
			<label>=</label>
			<text-field value="app.i1+app.i2" tooltip="app.i1+&quot;-&quot;+app.i2+&quot;=&quot;+(app.i1+app.i2)" />
		</box>
		<box layout="inline" orientation="horizontal">
			<label>Window: </label>
			<check-box value="app.b" />
		</box>
		<field-panel>
			<box field-name="`Position: `" layout="inline" orientation="horizontal" visible="app.b">
				<label value="app.x" />
				<label>, </label>
				<label value="app.y" />
			</box>
			<box field-name="`Size: `" layout="inline" orientation="horizontal" visible="app.b">
				<label value="app.w" />
				<label>x</label>
				<label value="app.h" />
			</box>
		</field-panel>
	</box>
</quick>

<!-- This is a footer comment
blah -->

