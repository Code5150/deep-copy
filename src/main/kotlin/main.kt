import CopyUtils.Companion.deepCopy

data class Man(private var name: String?, private var age: Int, private var favouriteBooks: List<String>?, var man: Man? = null) {
    override fun toString(): String {
        return name + age
    }

    override fun hashCode(): Int {
        return name.hashCode() + age
    }
}
data class Man2(private var name: String?, private var age: Float, private var favouriteBooks: List<String>?)
data class Man3(private var name: String?, private var age: Int,
                private var favouriteBooks: List<String>?, private var anotherMan: Man?)

fun main(args: Array<String>) {
    val newMan = deepCopy(
        Man3("Vladislav", 22, listOf("Kak zakalyalas stal"),
            Man(
                "Vladislav", 22, listOf("Pedagogicheskaya poema")
            )
    ))
    println("Hello World!")
}

