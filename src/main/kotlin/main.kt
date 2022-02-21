data class Man(private var name: String?, private var age: Int, private var favouriteBooks: List<String>?)
data class Man2(private var name: String?, private var age: Float, private var favouriteBooks: List<String>?)
data class Man3(private var name: String?, private var age: Int,
                private var favouriteBooks: List<String>?, private var anotherMan: Man?)

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
    val newMan = deepCopy(Man3("Vladislav", 22, listOf("Kak zakalyalas stal"), Man("Vladislav", 22, listOf("Pedagogicheskaya poema"))))
    println("Hello World!")
}

fun deepCopy(obj: Any): Any {
    val valuesToFields = HashMap<Any, String>()
    val fieldsToValues = HashMap<String, Any>()
    val constructor = obj::class.java.declaredConstructors[0]
    val fieldGraph = createFieldGraph(obj, valuesToFields, fieldsToValues, "root")
    assignCycleReferences(fieldGraph, valuesToFields, fieldsToValues)
    var result = constructor.newInstance(*constructor.parameterTypes.map { when(it.isPrimitive) {
        true -> 0
        else -> it.cast(null)
    }}.toTypedArray())
    result = cloneValues(result, valuesToFields, fieldsToValues,"root")
    return result
}

fun createFieldGraph(obj: Any, valuesToFields: HashMap<Any, String>, fieldsToValues: HashMap<String, Any>, nodeId: String, parentNode: Tree? = null, rootNode: Tree? = null): Tree {
    val parentId = when{
        nodeId.contains("Collection|") || nodeId.contains("Array|") -> ""
        nodeId.isNotBlank() && nodeId.isNotEmpty() -> ""
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
                createGraphForArrayOrCollectionElement(el, valuesToFields, fieldsToValues, rootNode, currentNode, elNumber)
                val strId = "[$elNumber]" + currentNode.nodeId
                valuesToFields[it] = strId
                fieldsToValues[strId] = it
            }
            elNumber++
        }
    } else if (nodeId.contains("Array|")) {
        var elNumber = 0
        for (el in obj as Array<*>) {
            el?.let {
                createGraphForArrayOrCollectionElement(el, valuesToFields, fieldsToValues, rootNode, currentNode, elNumber)
                val strId = "[$elNumber]" + currentNode.nodeId
                valuesToFields[it] = strId
                fieldsToValues[strId] = it
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
                val strId = (currentNode.nodeId) + "." + field.name
                currentNode.addChild(TreeInfo(field.name, false), Tree(strId))
                valuesToFields[field.get(obj)] = strId
                fieldsToValues[strId] = field.get(obj)
            } else if (field.type.isArray) {
                if (valuesToFields[field.get(obj)] != null) {
                    currentNode.addChild(
                        TreeInfo(field.name, isArrayOrCollection = true, isCycle = true),
                        rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                            ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                    )
                } else {
                    currentNode.addChild(
                        TreeInfo(field.name, true),
                        createFieldGraph(
                            field.get(obj), valuesToFields, fieldsToValues,
                            "Array|" + (currentNode.nodeId) + "." + field.name, currentNode, rootNode ?: currentNode
                        )
                    )
                    val strId = (currentNode.nodeId) + "." + field.name
                    valuesToFields[field.get(obj)] = strId
                    fieldsToValues[strId] = field.get(obj)
                }
            } else if (Collection::class.java.isAssignableFrom(field.type)) {
                val strId = (currentNode.nodeId) + "." + field.name
                //check for cycle reference
                if (valuesToFields[field.get(obj)] != null) {
                    currentNode.addChild(
                        TreeInfo(field.name, isArrayOrCollection = true, isCycle = true),
                        rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                            ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                    )
                } else {
                    currentNode.addChild(
                        TreeInfo(field.name, isArrayOrCollection = true, isCycle = false),
                        createFieldGraph(
                            field.get(obj), valuesToFields, fieldsToValues,
                            "Collection|$strId", currentNode, rootNode ?: currentNode
                        )
                    )
                    valuesToFields[field.get(obj)] = strId
                    fieldsToValues[strId] = field.get(obj)
                }
            } else {
                if (valuesToFields[field.get(obj)] != null) {
                    currentNode.addChild(
                        TreeInfo(field.name, isArrayOrCollection = true, isCycle = true),
                        rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                            ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                    )
                } else {
                    val strId = (currentNode.nodeId) + "." + field.name
                    currentNode.addChild(
                        TreeInfo(field.name, false),
                        createFieldGraph(
                            field.get(obj), valuesToFields, fieldsToValues,
                            field.name, currentNode, rootNode ?: currentNode
                        )
                    )
                    valuesToFields[field.get(obj)] = strId
                    fieldsToValues[strId] = field.get(obj)
                }
            }
        }
    }
    return currentNode
}

fun createGraphForArrayOrCollectionElement(el: Any, valuesToFields: HashMap<Any, String>, fieldsToValues: HashMap<String, Any>, rootNode: Tree?, currentNode: Tree, elNumber: Int) {
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
                el, valuesToFields, fieldsToValues,
                "[$elNumber]" + currentNode.nodeId, currentNode, rootNode ?: currentNode
            )
        )
    }
}

fun assignCycleReferences(rootNode: Tree, valuesToFields: HashMap<Any, String>, fieldsToValues: HashMap<String, Any>) {

}
/*Should return list of constructor args*/
/*Rewrite it to search values in hashsets by graph*/
fun cloneValues(obj: Any?, valuesToFields: HashMap<Any, String>, fieldsToValues: HashMap<String, Any>, nodeId: String): Any? {
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
                    for (i in valuesToFields) {
                        if (i.value == fieldId) {
                            field.set(obj, i.key)
                            break
                        }
                    }
                } else if (field.type.isAssignableFrom(String::class.java)) {
                    for (i in valuesToFields) {
                        if (i.value == fieldId) {
                            field.set(obj, String((i.key as String).toCharArray()))
                            break
                        }
                    }
                } else if (Collection::class.java.isAssignableFrom(field.type)) {
                    for (i in valuesToFields) {
                        if (i.value == fieldId) {
                            val resultCollection = when {
                                field.type == List::class.java -> MutableList((i.key as Collection<*>).size) {
                                    for (j in valuesToFields) {
                                        if (j.value == "[$it]Collection|$fieldId") {
                                            return@MutableList cloneValues(
                                                j.key, valuesToFields, fieldsToValues, "[$it]Collection|$fieldId"
                                            )
                                        }
                                    }
                                    return@MutableList null
                                }
                                else -> null
                            }
                            field.set(obj, resultCollection)
                        }
                    }
                } else /*if (field.type.isAssignableFrom(Any::class.java))*/ {
                    for (i in valuesToFields) {
                        if (i.value == fieldId) {
                            val keyConstructor = i.key::class.java.declaredConstructors[0]
                            val clonedKey = keyConstructor.newInstance(*keyConstructor.parameterTypes.map { when(it.isPrimitive) {
                                true -> 0
                                else -> it.cast(null)
                            }}.toTypedArray())
                            field.set(obj, cloneValues(clonedKey, valuesToFields, fieldsToValues, fieldId))
                            break
                        }
                    }
                }
            }
        }
    }
    return obj
}