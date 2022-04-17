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
expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>constructisOfExpect<!> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>commonMember<!>()
}

//Test correctOverloadResultionAmbiguity
// KT-34027
expect interface <!LINE_MARKER("descr='Has actuals in jsMain module'")!>correctOverloadResultionAmbiguity<!><T> {
    fun <!LINE_MARKER("descr='Has actuals in jsMain module'")!>foo<!>(x: T)
}

fun bar(): correctOverloadResultionAmbiguity<String> = null!!


//Test recursiveTypes
expect interface <!LINE_MARKER("descr='Has actuals in jvm module'"), LINE_MARKER("descr='Is subclassed by B  Click or press ... to navigate'")!>recursiveTypes<!><T : recursiveTypes<T>> {
    fun <!LINE_MARKER("descr='Has actuals in jvm module'")!>foo<!>(): T
}

interface B : recursiveTypes<B>

fun test(a: recursiveTypes<*>) {
    a.foo()
    a.foo().foo()
}

fun test(b: B) {
    b.foo()
    b.foo().foo()
}


//Test overrideExpect
expect class <!LINE_MARKER("descr='Has actuals in jvm module'")!>Expect<!>

interface <!LINE_MARKER("descr='Is implemented by Derived  Click or press ... to navigate'")!>Base<!> {
    fun <!LINE_MARKER("descr='Is implemented in Derived'")!>expectInReturnType<!>(): Expect

    fun expectInArgument(e: Expect)

    fun Expect.expectInReceiver()

    val <!LINE_MARKER("descr='Is implemented in Derived'")!>expectVal<!>: Expect

    var <!LINE_MARKER("descr='Is implemented in Derived'")!>expectVar<!>: Expect
}

