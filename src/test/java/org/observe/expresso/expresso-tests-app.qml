<?xml version="1.0" encoding="UTF-8"?>

<qonfig-app uses:app="Qonfig-App v0.1" app-file="expresso-tests.qml">
	<toolkit def="/org/observe/expresso/expresso-core.qtd">
		<value-type>org.observe.expresso.ExpressionValueType</value-type>
	</toolkit>
	<toolkit def="/org/observe/expresso/expresso-base.qtd" />
	<toolkit def="/org/observe/expresso/expresso-test-framework.qtd" />
	<toolkit def="/org/observe/expresso/expresso-test.qtd" />
	<special-session>org.observe.expresso.ExpressoSessionImplV0_1</special-session>
	<interpretation>org.observe.expresso.ExpressoBaseV0_1</interpretation>
	<interpretation>org.observe.expresso.ExpressoTestFrameworkInterpretation</interpretation>
	<interpretation>org.observe.expresso.TestInterpretation</interpretation>
</qonfig-app>
