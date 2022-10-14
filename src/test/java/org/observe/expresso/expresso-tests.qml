<?xml version="1.0" encoding="UTF-8"?>

<expresso uses:expresso="Expresso-Base 0.1">
	<!-- TODO
		Test AND DOCUMENT!!

		Constant
		Simple value
		Derived value (+ test set)
		Simple Lists, Sorted Lists, Sets, Sorted Sets with configured elements
		Derived Lists, Sorted Lists, Sets, Sorted Sets (+ test set)
		Simple Maps, Sorted Maps, Multi-Maps, Sorted Multi-Maps with configured entries
		Derived Maps, Sorted Maps, Multi-Maps, Sorted Multi-Maps (+ test set)
		Event
		Action

		Transformation (values, collections, (multi-)maps, actions
			disable
			map-to
				reversal
				combination
				with and without type spec
			filter(-by-type)
			reverse
			refresh(-each)
			distinct
			sort
			with-equivalance
			unmodifiable
			filter-mod
			map-equivalent
			flatten (to value and to collection)
				with and without type spec
			cross
			where-contained
			group-by

			no-init
			skip
			take
			take-until
	
			first-value
		
		Test expressions
			Array access
			Assignment
			Unary operators
				!, ~, -
				++, - - (pre and post)
			Binary operators
				+, -, *, /, %
				==, !=, <, <=, >, >=
			Cast
			Class instance (e.g. 'int.class')
			Conditional (ternary)
			Constructor
				Value should not change upon repeated access (at least for simple values)
				Test with arguments that change (should be re-invoked when args change)
			External Literals (all types)
			instanceof
			Lambda (-> and ::)
			Method
				Value should not change upon repeated access (at least for simple values)
				Test with arguments that change (should be re-invoked when args change)
				Function
				Static
				Non-static
			Field
			Model value (e.g. 'model.value')
			Parenthetic

		In new file, test Expresso-Config
		In new file, test styles (make a test toolkit and impl?)
	-->
	<imports>
		<import>org.observe.expresso.ExpressoTestEntity</import>
	</imports>
	<models>
		<model name="model1">
			<value name="test" init="new ExpressoTestEntity()" />
			<value name="expected" init="new ExpressoTestEntity()" />

			<value name="testInt">test.getInt()</value>
			<value name="testDbl">test.getDouble()</value>
			<value name="testBool">test.getBoolean()</value>
			<value name="testStr">test.getString()</value>
			<value name="entityInst">test.getInstant()</value>

			<value name="anyInt" init="10" />
			<value name="anyDbl" init="1000.0" />
			<value name="anyBool" init="true" />
			<value name="anyStr" init="&quot;Something&quot;" />
			<value type="java.time.Instant" name="anyInst" init="`05May2022 10:15:43am`" />

			<action name="assignInt">test.setInt(anyInt)</action>
			<action name="assignDbl">test.setDouble(anyDbl)</action>
			<action name="assignBool">test.setBoolean(anyBool)</action>
			<action name="assignStr">test.setString(anyStr)</action>
			<action name="assignInst">test.setInstant(anyInst)</action>

			<value name="error">test.assertEquals(expected)</value>
		</model>
		<model name="model2">
			<action name="assignInst">model1.anyInst=`01Jan2022 12:00pm`</action>
		</model>
	</models>
</expresso>
