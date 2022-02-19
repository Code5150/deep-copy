import java.io.ObjectInputStream

data class Man(private var name: String?, private var age: Int, private var favouriteBooks: List<String>?)
data class Man2(private var name: String?, private var age: Float, private var favouriteBooks: List<String>?)

data class TreeInfo(val fieldId: String, val isArrayOrCollection: Boolean = false, val isCycle: Boolean = false)
data class Tree(val nodeId: String,
                private val children: HashMap<TreeInfo, Tree?> = HashMap()) {
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

    public fun addChild(fieldId: TreeInfo, childNode: Tree?) {
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
    assignCycleReferences(fieldGraph, fieldValues)
    var result = constructor.newInstance(*constructor.parameterTypes.map { when(it.isPrimitive) {
        true -> 0
        else -> it.cast(null)
    }}.toTypedArray())
    result = cloneValues(result, fieldValues,"root")
    return result
}

fun createFieldGraph(obj: Any, fieldValues: HashMap<Any, String>, nodeId: String, parentNode: Tree? = null, rootNode: Tree? = null): Tree {
    val parentId = when{
        nodeId.contains("Collection|") || nodeId.contains("Array|") -> ""
        else -> when(parentNode) {
            null -> ""
            else -> parentNode.nodeId + "."
        }
    }
    val currentNode = Tree(parentId + nodeId)
    if (nodeId.contains("Collection|")) {
        var elNumber = 0
        for (el in obj as Collection<*>) {
            el?.let {
                createGraphForArrayOrCollectionElement(el, fieldValues, rootNode, currentNode, elNumber)
                fieldValues[it] = "[$elNumber]" + currentNode.nodeId
            }
            elNumber++
        }
    } else if (nodeId.contains("Array|")) {
        var elNumber = 0
        for (el in obj as Array<*>) {
            el?.let {
                createGraphForArrayOrCollectionElement(el, fieldValues, rootNode, currentNode, elNumber)
                fieldValues[it] = "[$elNumber]" + currentNode.nodeId
            }
            elNumber++
        }
    } else {
        for (field in obj::class.java.declaredFields) {
            field.isAccessible = true
            if (field.type.isPrimitive
                || field.type.isAssignableFrom(Number::class.java)
                || field.type.isAssignableFrom(Boolean::class.java)
                || field.type.isAssignableFrom(Char::class.java)
                || field.type.isAssignableFrom(String::class.java)
                || field.type.isEnum
            ) {
                currentNode.addChild(TreeInfo(field.name, false), null)
                fieldValues[field.get(obj)] = (currentNode.nodeId) + "." + field.name
            } else if (field.type.isArray) {
                currentNode.addChild(
                    TreeInfo(field.name, true),
                    createFieldGraph(
                        field.get(obj), fieldValues,
                        "Array|" + (currentNode.nodeId) + "." + field.name, currentNode, rootNode ?: currentNode
                    )
                )
                fieldValues[field.get(obj)] = (currentNode.nodeId) + "." + field.name
            } else if (Collection::class.java.isAssignableFrom(field.type)) {
                currentNode.addChild(
                    TreeInfo(field.name, true),
                    createFieldGraph(
                        field.get(obj), fieldValues,
                        "Collection|" + (currentNode.nodeId) + "." + field.name, currentNode, rootNode ?: currentNode
                    )
                )
                fieldValues[field.get(obj)] = (currentNode.nodeId) + "." + field.name
            } else if (field.type.isAssignableFrom(Any::class.java)) {
                currentNode.addChild(
                    TreeInfo(field.name, true),
                    createFieldGraph(
                        field.get(obj), fieldValues,
                        (currentNode.nodeId) + "." + field.name, currentNode, rootNode ?: currentNode
                    )
                )
                fieldValues[field.get(obj)] = (currentNode.nodeId) + "." + field.name
            }
        }
    }
    return currentNode
}

fun createGraphForArrayOrCollectionElement(el: Any, fieldValues: HashMap<Any, String>, rootNode: Tree?, currentNode: Tree, elNumber: Int) {
    if (!(el::class.java.isPrimitive
        || el::class.java.isAssignableFrom(Number::class.java)
        || el::class.java.isAssignableFrom(Boolean::class.java)
        || el::class.java.isAssignableFrom(Char::class.java)
        || el::class.java.isAssignableFrom(String::class.java)
        || el::class.java.isEnum)) {
        currentNode.addChild(
            TreeInfo(
                "[$elNumber]" + currentNode.nodeId,
                el::class.java.isArray || Collection::class.java.isAssignableFrom(el::class.java)
            ),
            createFieldGraph(
                el, fieldValues,
                "[$elNumber]" + currentNode.nodeId, currentNode, rootNode ?: currentNode
            )
        )
    }
}

fun assignCycleReferences(rootNode: Tree, fieldValues: HashMap<Any, String>) {

}

fun cloneValues(obj: Any?, fieldValues: HashMap<Any, String>, nodeId: String): Any? {
    if(obj != null) {
        if (obj::class.java.isPrimitive
            || obj::class.java.isAssignableFrom(Number::class.java)
            || obj::class.java.isAssignableFrom(Boolean::class.java)
            || obj::class.java.isAssignableFrom(Char::class.java)
            || obj::class.java.isEnum
        ) {
            return obj
        } else if (obj::class.java.isAssignableFrom(String::class.java)) {
            return String((obj as String).toCharArray())
        } else {
            for (field in obj::class.java.declaredFields) {
                field.isAccessible = true
                var fieldId = nodeId + "." + field.name
                if (field.type.isPrimitive
                    || field.type.isAssignableFrom(Number::class.java)
                    || field.type.isAssignableFrom(Boolean::class.java)
                    || field.type.isAssignableFrom(Char::class.java)
                    || field.type.isEnum
                ) {
                    for (i in fieldValues) {
                        if (i.value == fieldId) {
                            field.set(obj, i.key)
                            break
                        }
                    }
                } else if (field.type.isAssignableFrom(String::class.java)) {
                    for (i in fieldValues) {
                        if (i.value == fieldId) {
                            field.set(obj, String((i.key as String).toCharArray()))
                            break
                        }
                    }
                } else if (Collection::class.java.isAssignableFrom(field.type)) {
                    for (i in fieldValues) {
                        if (i.value == fieldId) {
                            val resultCollection = when {
                                field.type == List::class.java -> MutableList((i.key as Collection<*>).size) {
                                    for (j in fieldValues) {
                                        if (j.value == "[$it]Collection|$fieldId") {
                                            return@MutableList cloneValues(j.key, fieldValues, "[$it]Collection|$fieldId")
                                        }
                                    }
                                    return@MutableList null
                                }
                                else -> null
                            }
                            field.set(obj, resultCollection)
                        }
                    }
                }
            }
        }
    }
    return obj
}