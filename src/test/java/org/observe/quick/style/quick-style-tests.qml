<?xml version="1.0" encoding="UTF-8"?>

<testing uses:style="Quick-Style-Test 0.1">
	<!--Test: (all with changes, observability)
		Simple style as fn of model value
		Simple style as fn of element value
		Simple style as fn of local value
		Prioritization of element values in a style with more than one possible values for an attribute
			Test observability as values of both elements change
		Inherited style from parent
		Style sheet styles
		Local styles
	-->
	<head>
		<imports>
			<import>org.junit.Assert.*</import>
			<import>org.observe.quick.style.QuickStyleTests.*</import>
		</imports>
		<models>
			<ext-model name="ext">
				<value name="actionName" type="String" />
			</ext-model>
			<model name="models">
				<value name="m0" type="boolean" />
				<value name="m1" type="int" />
				<value name="m2" type="boolean" />
				<value name="m3" type="int" />
			</model>
		</models>
	</head>
	<test name="test0">
		<model>
			<a name="a0" a="models.m0" b="models.m2" c="models.m1">
				<style attr="s0">a</style>
				<style attr="s1" condition="b">10</style>
				<style attr="s1">c*10</style>
			</a>
		</model>
		<action name="action1">models.m0=true</action>
		<action name="action2">assertEquals(a0.s0, a0.a)</action>
	</test>
</testing>
