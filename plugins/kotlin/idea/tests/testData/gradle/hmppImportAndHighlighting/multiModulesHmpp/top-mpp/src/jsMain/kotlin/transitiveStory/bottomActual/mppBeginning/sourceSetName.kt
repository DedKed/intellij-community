package transitiveStory.bottomActual.mppBeginning

actual val <!LINE_MARKER("descr='Has expects in multimod-hmpp.top-mpp.commonMain module'")!>sourceSetName<!>: String = "jsMain"


fun jsFun() {
}

fun test() {
    val <!HIGHLIGHTING("severity='WARNING'; descr='[UNUSED_VARIABLE] Variable 'string' is never used'")!>string<!>: String = ""
    val ref: kotlin.reflect.KCallable<*> = ::jsFun
    ref.name
    // should be unresolved
    ref.<!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: call'")!>call<!>()
}
