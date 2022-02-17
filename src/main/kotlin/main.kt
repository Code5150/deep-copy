
data class Man(private var name: String?, private var age: Int, private var favouriteBooks: List<String>?)
data class Man2(private var name: String?, private var age: Float, private var favouriteBooks: List<String>?)

data class Tree(val nodeId: String, val children: HashMap<String, Tree> = HashMap()) {

}

fun main(args: Array<String>) {
    val newMan = deepCopy(Man2("Vladislav", 22f, listOf("Pedagogicheskaya poema")))
    println("Hello World!")
}

fun deepCopy(obj: Any): Any {
    val fieldGraph = Tree(obj::class.java.simpleName)
    val fieldValues = HashMap<Any, String>()
    val constructor = obj::class.java.declaredConstructors[0]
    var result = constructor.newInstance(*constructor.parameterTypes.map { when(it.isPrimitive) {
        true -> 0
        else -> it.cast(null)
    }}.toTypedArray())
    return result
}