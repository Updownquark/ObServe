<?xml version="1.0" encoding="UTF-8"?>

<testing xmlns:style="Quick-Style-Test 0.1">
	<head>
		<imports>
			<import>org.junit.Assert.*</import>
			<import>org.observe.quick.style.QuickStyleTests.*</import>
		</imports>
		<models>
			<model name="models">
				<value name="m0" type="boolean" />
				<value name="m1" type="int" />
				<value name="m2" type="boolean" />
				<value name="m3" type="int" />
				<value name="m4" type="boolean "/>
			</model>
		</models>
	</head>
	<test name="basicStyleNoWatch">
		<model>
			<!-- Basic styles that depend on model values with various conditions with varying priority and complexity -->
			<a name="a0" a="models.m0" b="models.m2" c="models.m1" d="models.m4">
				<style attr="s0">a</style>
				<style attr="s1">c*10</style>
				<style attr="s1" if="a">models.m1</style>
				<style attr="s1" if="b">models.m3</style>
				<style attr="s1" if="a &amp;&amp; d">models.m1 + models.m3</style>
			</a>
		</model>
		<action>models.m0=true</action>
		<action>assertEquals(models.m0, a0.s0)</action>

		<!-- Default s1 condition, c=models.m1=0, so s1 should be 0 -->
		<action>assertEquals(0, a0.s1)</action>

		<!-- Change the value of the active -->
		<action>models.m1=25</action>
		<action>assertEquals(25, a0.s1)</action>

		<!-- Revert to default condition -->
		<action>models.m0=false</action>
		<action>assertEquals(250, a0.s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action>models.m3=50</action>
		<action>assertEquals(250, a0.s1)</action>

		<!-- Change to b condition -->
		<action>models.m2=true</action>
		<action>assertEquals(50, a0.s1)</action>

		<!-- b=models.m2 has higher priority than a=models.m0, so making 'a' true won't affect s1 -->
		<action>models.m0=true</action>
		<action>assertEquals(50, a0.s1)</action>

		<!-- a && d has higher priority than b though -->
		<action>models.m4=true</action>
		<action>assertEquals(75, a0.s1)</action>
		<!-- Back to b condition -->
		<action>models.m4=false</action>
		<action>assertEquals(50, a0.s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action>models.m1=300</action>
		<action>assertEquals(50, a0.s1)</action>

		<!-- Back to a condition -->
		<action>models.m2=false</action>
		<action>assertEquals(300, a0.s1)</action>

		<!-- Revert to default condition -->
		<action>models.m0=false</action>
		<action>assertEquals(3000, a0.s1)</action>

		<!-- No change, needs both a and d -->
		<action>models.m4=true</action>
		<action>assertEquals(3000, a0.s1)</action>
	</test>
	<test name="basicStyleWithWatch">
		<!-- Basic styles that depend on model values with various conditions with varying priority and complexity -->
		<model>
			<a name="a0" a="models.m0" b="models.m2" c="models.m1" d="models.m4">
				<style attr="s0">a</style>
				<style attr="s1">c*10</style>
				<style attr="s1" if="a">models.m1</style>
				<style attr="s1" if="b">models.m3</style>
				<style attr="s1" if="a &amp;&amp; d">models.m1 + models.m3</style>
			</a>
			<watch name="w_a0_s0">a0.s0</watch>
			<watch name="w_a0_s1">a0.s1</watch>
		</model>
		<action>models.m0=true</action>
		<action>assertEquals(models.m0, w_a0_s0)</action>

		<!-- Default s1 condition, c=models.m1=0, so s1 should be 0 -->
		<action>assertEquals(0, w_a0_s1)</action>

		<!-- Change the value of the active -->
		<action>models.m1=25</action>
		<action>assertEquals(25, w_a0_s1)</action>

		<!-- Revert to default condition -->
		<action>models.m0=false</action>
		<action>assertEquals(250, w_a0_s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action>models.m3=50</action>
		<action>assertEquals(250, w_a0_s1)</action>

		<!-- Change to b condition -->
		<action>models.m2=true</action>
		<action>assertEquals(50, w_a0_s1)</action>

		<!-- b=models.m2 has higher priority than a=models.m0, so making 'a' true won't affect s1 -->
		<action>models.m0=true</action>
		<action>assertEquals(50, w_a0_s1)</action>

		<!-- a && d has higher priority than b though -->
		<action>models.m4=true</action>
		<action>assertEquals(75, w_a0_s1)</action>
		<!-- Back to b condition -->
		<action>models.m4=false</action>
		<action>assertEquals(50, w_a0_s1)</action>

		<!-- Verify no change for value with unmet condition -->
		<action>models.m1=300</action>
		<action>assertEquals(50, w_a0_s1)</action>

		<!-- Back to a condition -->
		<action>models.m2=false</action>
		<action>assertEquals(300, w_a0_s1)</action>

		<!-- Revert to default condition -->
		<action>models.m0=false</action>
		<action>assertEquals(3000, w_a0_s1)</action>

		<!-- No change, needs both a and d -->
		<action>models.m4=true</action>
		<action>assertEquals(3000, w_a0_s1)</action>
	</test>
	<test name="localStyleSheet">
		<model>
			<!-- Test styles that are prescribed by a style sheet defined in this document -->
			<a name="a0" a="localA" b="localB" c="localC" d="localD">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
				</model>
			</a>
			<b name="b0" e="localE" f="localF">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</b>
			<c name="c0" e="localE" f="localF" g="localG">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
					<value name="localG" type="boolean" init="false" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</c>
			<d name="d0" e="localE" f="localF" h="localH">
				<model>
					<value name="localA" type="boolean" init="false" />
					<value name="localB" type="boolean" init="false" />
					<value name="localC" type="int" init="0" />
					<value name="localD" type="boolean" init="false" />
					<value name="localE" type="boolean" init="false" />
					<value name="localF" type="int" init="0" />
					<value name="localH" type="int" init="0" />
				</model>
				<a a="localA" b="localB" c="localC" d="localD" />
			</d>
		</model>
		<style-sheet>
			<style element="a">
				<style attr="s0">!a</style>
				<style attr="s1">c</style>
				<style if="b">
					<style attr="s0">d</style>
					<style attr="s1">c*100</style>
					<style if="d">
						<style attr="s1">c*1_000_000</style>
					</style>
				</style>
			</style>
			<style element="b">
				<style attr="s3" if="e">models.m3</style>
				<style attr="s4" if="!e">models.m1</style>
				<style child="a">
					<style if="e"> <!-- A child style dependent on an element model value from the parent -->
						<style attr="s1">f</style>
					</style>
				</style>
			</style>
		</style-sheet>
		
		<action>models.m1=497683</action>
		<action>models.m3=-42</action>
		
		<!-- a, d, and e are false -->
		<action>assertEquals(true, a0.s0)</action>
		<action>assertEquals(0, a0.s1)</action>
		<action>assertEquals(null, b0.s3)</action>
		<action>assertEquals(models.m1, b0.s4)</action>

		<action>a0.b=true</action>
		<action>a0.c=250</action>
		<action>assertEquals(false, a0.s0)</action>
		<action>assertEquals(25_000, a0.s1)</action>

		<action>b0.e=true</action>
		<action>assertEquals(models.m3, b0.s3)</action>
		<action>assertEquals(null, b0.s4)</action>

		<action>b0.f=18</action>
		<action>b0.a.get(0).c=179</action>
		<action>assertEquals(18, b0.a.get(0).s1)</action>
		<action>b0.e=false</action>
		<action>assertEquals(179, b0.a.get(0).s1)</action>
		<!-- TODO Include local styles also-->
	</test>
	<test name="importedStyleSheet">
		<!-- TODO Test styles that are prescribed by a style sheet imported from outside this document -->
		<model>
		</model>
		<action>models.m0=true</action>
	</test>
	<test name="inheritedStyle">
		<!-- TODO Test inheritance of styles from parent elements -->
		<model>
		</model>
		<action>models.m0=true</action>
	</test>
	<!--
		This test is failing, and not only failing but breaking the rest of the tests.
		The issue is, I believe, that the model values are being loaded before the style sheet is,
		so the references to the style-sets fail.
		
		But if that's so, I don't understand why the localStyleSheet test is fine.
		
		It should be noted that style sets are currently working (they are used in Quick applications I've written),
		but it would be nice at some point to get this unit test working.
	<test name="withStyleSets">
		<model>
			<!- a1 has a copy of all the same styles as are contained in the style set ->
			<a name="a1" a="models.m0" b="true" c="0" d="models.m2">
				<style if="a" attr="s1">217
					<style if="d">856</style>
				</style>
			</a>
			<!- a2 uses the style set.  Its style is the same as a1 when the style set applies to it, i.e. when a is true ->
			<a name="a2" a="models.m0" b="true" c="0" d="models.m2">
				<style attr="s0">false</style>
				<style if="a" style-set="testStyle" />
			</a>
		</model>
		<style-sheet>
			<style-set name="testStyle">
				<style element="a" attr="s1">217
					<style if="d">856</style>
				</style>
				<style element="a" attr="s0">true</style> <!- Just to test multiple styles in a style set ->
			</style-set>
		</style-sheet>

		<action>assertEquals(0, a1.s1)</action>
		<action>models.m0=true</action>
		<action>assertEquals(217, a1.s1)</action>
		<action>models.m2=true</action>
		<action>assertEquals(856, a1.s1)</action>
		<action>models.m0=false</action>
		<action>assertEquals(0, a1.s1)</action>

		<action>models.m2=false</action>

		<action>assertEquals(0, a2.s1)</action>
		<action>models.m0=true</action>
		<action>assertEquals(217, a2.s1)</action>
		<action>models.m2=true</action>
		<action>assertEquals(856, a2.s1)</action>
		<action>models.m0=false</action>
		<action>assertEquals(0, a2.s1)</action>

		<action>assertEquals(false, a2.s0)</action>
		<action>models.m0=true</action>
		<action>assertEquals(true, a2.s0)</action>
	</test>
	-->
</testing>
