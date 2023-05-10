<?xml version="1.0" encoding="UTF-8"?>

<qonfig-app uses:app="Qonfig-App v0.1" app-file="quick-tests.qml">
	<toolkit def="/org/observe/expresso/expresso-core.qtd">
		<value-type>org.observe.expresso.ExpressionValueType</value-type>
	</toolkit>
	<toolkit def="/org/observe/expresso/expresso-base.qtd" />
	<toolkit def="/org/observe/expresso/expresso-test-framework.qtd" />
	<toolkit def="/org/observe/expresso/expresso-debug.qtd" />
	<toolkit def="/org/observe/quick/style/quick-style.qtd" />
	<toolkit def="/org/observe/quick/quick-core.qtd" />
	<toolkit def="/org/observe/quick/quick-base.qtd" />
	<toolkit def="/org/observe/quick/quick-testing.qtd" />
	<special-session>org.observe.expresso.ExpressoSessionImplV0_1</special-session>
	<special-session>org.observe.quick.style.StyleSessionImplV0_1</special-session>
	<interpretation>org.observe.expresso.ExpressoBaseV0_1</interpretation>
	<interpretation>org.observe.expresso.ExpressoTestFrameworkInterpretation</interpretation>
	<interpretation>org.observe.quick.style.QuickStyleInterpretation</interpretation>
	<interpretation>org.observe.quick.base.QuickBaseInterpretation</interpretation>
	<interpretation>org.observe.quick.TestInterpretation</interpretation>
</qonfig-app>