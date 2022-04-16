@file:Suppress("UNUSED_PARAMETER")




// !DIAGNOSTICS: -UNUSED_VARIABLE


// TEST builtinsAndStdlib
import kotlin.reflect.KCallable

fun some() {
    val string: String = ""
    val any: Any = ""
    val callableRef: KCallable<*> = ::commonFun
    callableRef.name
    // should be unresolved
    callableRef.<!UNRESOLVED_REFERENCE!>call<!>()
}

fun commonFun() {
}


//Partitially test AliasesTypeMismatch
expect open class <!LINE_MARKER("descr='Has actuals in jvm module'")!>MyCancelException<!> : MyIllegalStateException

fun cancel(cause: MyCancelException) {}

expect open class <!LINE_MARKER("descr='Has actuals in jvm module'")!>OtherException<!> : MyIllegalStateException

fun other(cause: OtherException) {}


//Test commonSealedWithPlatformInheritor
sealed class <!LINE_MARKER("descr='Is subclassed by Derived PlatfromDerived  Click or press ... to navigate'")!>Base<!>

class Derived : Base()

fun test_1(b: Base) = <!NO_ELSE_IN_WHEN{JVM}!>when<!> (b) {
    is Derived -> 1
}

//Test constructisOfExpect
expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>A<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonMember<!>()
}

//Test correctOverloadResultionAmbiguity
// KT-34027
expect interface <!LINE_MARKER("descr='Has actuals in jsMain module'")!>A<!><T> {
    fun <!LINE_MARKER("descr='Has actuals in jsMain module'")!>foo<!>(x: T)
}

fun bar(): A<String> = null!!

