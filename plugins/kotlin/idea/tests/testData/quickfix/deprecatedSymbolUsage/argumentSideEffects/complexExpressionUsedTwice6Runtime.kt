// "Replace with 'newFun(p, p)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(p, p)"))
fun oldFun(p: Int?): Int {
    return newFun(p, p)
}

fun newFun(p1: Int?, p2: Int?): Int = 0

fun foo(): Int {
    return <caret>oldFun(bar())
}

fun bar(): Int? = null

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix