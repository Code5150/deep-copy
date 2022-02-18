
data class Man(private var name: String?, private var age: Int, private var favouriteBooks: List<String>?)
data class Man2(private var name: String?, private var age: Float, private var favouriteBooks: List<String>?)

data class Tree(val nodeId: String, private val children: HashMap<String, Tree?> = HashMap()) {
    public fun getChild(childId: String): Tree? {
        var result: Tree? = null
        if (childId == nodeId) {
            result = this
        } else {
            for (child in children.values) {
                result = child?.getChild(childId)
            }
        }
        return result
    }

    public fun addChild(fieldId: String, childNode: Tree?) {
        children[fieldId] = childNode
    }
}

//deep copy stops on objects, which package name contains this strings,
//and considers them as cloneable by means of standard library
//val stopList = mutableListOf("java.", "kotlin.")

fun main(args: Array<String>) {
    val newMan = deepCopy(Man("Vladislav", 22, listOf("Pedagogicheskaya poema")))
    println("Hello World!")
}

fun deepCopy(obj: Any): Any {
    val fieldValues = HashMap<Any, String>()
    val constructor = obj::class.java.declaredConstructors[0]
    val fieldGraph = createFieldGraph(obj, fieldValues, "root")
    var result = constructor.newInstance(*constructor.parameterTypes.map { when(it.isPrimitive) {
        true -> 0
        else -> it.cast(null)
    }}.toTypedArray())
    cloneValues(result, fieldValues,"root")
    return result
}

fun createFieldGraph(obj: Any, fieldValues: HashMap<Any, String>, nodeId: String, parentNode: Tree? = null, rootNode: Tree? = null): Tree {
    val parentId = when(parentNode) {
        null -> ""
        else -> parentNode.nodeId + "."
    }
    val currentNode = Tree(parentId + nodeId)
    for (field in obj::class.java.declaredFields) {
        field.isAccessible = true
        if (field.type.isPrimitive
            || field.type.isAssignableFrom(Number::class.java)
            || field.type.isAssignableFrom(Boolean::class.java)
            || field.type.isAssignableFrom(Char::class.java)
            || field.type.isAssignableFrom(String::class.java)) {
            currentNode.addChild(field.name, null)
            fieldValues[field.get(obj)] = (currentNode.nodeId) + "." + field.name
        }
    }
    return currentNode
}

fun cloneValues(obj: Any, fieldValues: HashMap<Any, String>, nodeId: String) {
    for (field in obj::class.java.declaredFields) {
        field.isAccessible = true
        if (field.type.isPrimitive
            || field.type.isAssignableFrom(Number::class.java)
            || field.type.isAssignableFrom(Boolean::class.java)
            || field.type.isAssignableFrom(Char::class.java)
            || field.type.isAssignableFrom(String::class.java)) {
                val fieldId = nodeId + "." + field.name
                for(i in fieldValues) {
                    if (i.value == fieldId) {
                        field.set(obj, i.key)
                        break
                    }
                }
        }
    }
}