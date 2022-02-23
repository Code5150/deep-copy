import sun.reflect.ReflectionFactory

data class Man(private var name: String?, private var age: Int, private var favouriteBooks: List<String>?)
data class Man2(private var name: String?, private var age: Float, private var favouriteBooks: List<String>?)
data class Man3(private var name: String?, private var age: Int,
                private var favouriteBooks: List<String>?, private var anotherMan: Man?)

data class TreeInfo(val fieldId: String, val isArrayOrCollection: Boolean = false, val isCycle: Boolean = false)
data class Tree(val nodeId: String,
                val children: HashMap<TreeInfo, Tree?> = HashMap()) {
    public fun getChild(childId: String): Tree? {
        var result: Tree? = null
        if (childId == nodeId) {
            result = this
        } else {
            for (v in children.values) {
                result = v?.getChild(childId)
                if (result != null) break
            }
        }
        return result
    }

    public fun addChild(fieldId: TreeInfo, childNode: Tree?): Tree? {
        children[fieldId] = childNode
        return childNode
    }

    public fun isCycleReference(fieldId: String): Tree? {
        for ((k, v) in children) {
            if (fieldId == k.fieldId) {
                return if (k.isCycle) {
                    v
                } else {
                    null
                }
            }
        }
        return null
    }
}

//deep copy stops on objects, which package name contains this strings,
//and considers them as cloneable by means of standard library
//val stopList = mutableListOf("java.", "kotlin.")

fun main(args: Array<String>) {
    val newMan = deepCopy(
        Man3("Vladislav", 22, listOf("Kak zakalyalas stal"),
            Man(
                "Vladislav", 22, listOf("Pedagogicheskaya poema")
            )
    ))
    println("Hello World!")
}

fun createBlankInstance(obj: Any): Any {
    val rf = ReflectionFactory.getReflectionFactory()
    val constructor = rf.newConstructorForSerialization(obj::class.java,
        Any::class.java.getDeclaredConstructor())
    return obj::class.java.cast(constructor.newInstance())
}

fun deepCopy(obj: Any?): Any? {
    var result: Any? = null
    if (obj != null) {
        try {
            val valuesToFields = mutableMapOf(obj to "root")
            val fieldsToValues = mutableMapOf("root" to obj)

            val fieldGraph = createFieldGraph(obj, valuesToFields, fieldsToValues, "root")
            if (fieldGraph != null) {
                val res = createBlankInstance(obj)
                val newObjFieldsToValues = mutableMapOf("root" to res)

                result = cloneValues(res, valuesToFields, fieldsToValues, newObjFieldsToValues, fieldGraph, fieldGraph)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
    return result
}

fun createFieldGraph(
    obj: Any?, valuesToFields: MutableMap<Any, String>, fieldsToValues: MutableMap<String, Any>,
    nodeId: String, rootNode: Tree? = null
): Tree? {
    val currentNode = Tree(nodeId)
    if (obj != null) {
        if (List::class.java.isAssignableFrom(obj::class.java)) {
            (obj as List<*>).forEachIndexed { index, any -> any?.let {
                createGraphForArrayOrListElement(it, valuesToFields, fieldsToValues, rootNode, currentNode, index)
            }}
        } else if (Set::class.java.isAssignableFrom(obj::class.java)) {
            (obj as Set<*>).forEachIndexed { index, any -> any?.let {
                createGraphForSetElement(it, valuesToFields, fieldsToValues, rootNode, currentNode, index)
            }}
        } else if (Map::class.java.isAssignableFrom(obj::class.java)) {
            (obj as Map<*, *>).forEach { (k, v) -> k?.let { v?.let {
                createGraphForMapElement(k, v, valuesToFields, fieldsToValues, rootNode, currentNode)
            }}}
        } else if (obj::class.java.isArray) {
            (obj as Array<*>).forEachIndexed { index, any -> any?.let {
                createGraphForArrayOrListElement(it, valuesToFields, fieldsToValues, rootNode, currentNode, index)
            }}
        } else {
            for (field in obj::class.java.declaredFields) {
                field.isAccessible = true
                if (field.type.isPrimitive
                    || field.type.isAssignableFrom(Number::class.java)
                    || field.type.isAssignableFrom(Boolean::class.java)
                    || field.type.isAssignableFrom(Char::class.java)
                    || field.type.isEnum
                ) { /*Check for cycle reference if string*/
                    val strId = (currentNode.nodeId) + "." + field.name
                    currentNode.addChild(TreeInfo(strId), Tree(strId))
                    valuesToFields[field.get(obj)] = strId
                    fieldsToValues[strId] = field.get(obj)
                } else if (field.type.isAssignableFrom(String::class.java)) {
                    val strId = (currentNode.nodeId) + "." + field.name
                    if (valuesToFields[field.get(obj)] != null) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = false, isCycle = true),
                            rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                                ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                        )
                    } else {
                        currentNode.addChild(TreeInfo(strId), Tree(strId))
                        valuesToFields[field.get(obj)] = strId
                        fieldsToValues[strId] = field.get(obj)
                    }
                } else if (field.type.isArray) {
                    val strId = (currentNode.nodeId) + "." + field.name
                    if (valuesToFields[field.get(obj)] != null) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = true, isCycle = true),
                            rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                                ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                        )
                    } else {
                        currentNode.addChild(
                            TreeInfo(strId, true),
                            createFieldGraph(
                                field.get(obj), valuesToFields, fieldsToValues,
                                strId, rootNode ?: currentNode
                            )
                        )
                        valuesToFields[field.get(obj)] = strId
                        fieldsToValues[strId] = field.get(obj)
                    }
                } else if (Collection::class.java.isAssignableFrom(field.type)) {
                    val strId = (currentNode.nodeId) + "." + field.name
                    //check for cycle reference
                    if (valuesToFields[field.get(obj)] != null) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = true, isCycle = true),
                            rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                                ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                        )
                    } else {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = true, isCycle = false),
                            createFieldGraph(
                                field.get(obj), valuesToFields, fieldsToValues,
                                strId, rootNode ?: currentNode
                            )
                        )
                        valuesToFields[field.get(obj)] = strId
                        fieldsToValues[strId] = field.get(obj)
                    }
                } else {
                    val strId = (currentNode.nodeId) + "." + field.name
                    if (valuesToFields[field.get(obj)] != null) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = false, isCycle = true),
                            rootNode?.getChild(valuesToFields[field.get(obj)]!!)
                                ?: currentNode.getChild(valuesToFields[field.get(obj)]!!)
                        )
                    } else {
                        currentNode.addChild(
                            TreeInfo(strId, false),
                            createFieldGraph(
                                field.get(obj), valuesToFields, fieldsToValues,
                                strId, rootNode ?: currentNode
                            )
                        )
                        valuesToFields[field.get(obj)] = strId
                        fieldsToValues[strId] = field.get(obj)
                    }
                }
            }
        }
        return currentNode
    } else return null
}

fun createGraphForArrayOrListElement(el: Any, valuesToFields: MutableMap<Any, String>,
                                     fieldsToValues: MutableMap<String, Any>, rootNode: Tree?, currentNode: Tree,
                                     elNumber: Int) {
    val strId = currentNode.nodeId + "[$elNumber]"
    createGraphForContainerElement(el, valuesToFields, fieldsToValues, rootNode, currentNode, strId)
    valuesToFields[el] = strId
    fieldsToValues[strId] = el
}

fun createGraphForSetElement(el: Any, valuesToFields: MutableMap<Any, String>,
                             fieldsToValues: MutableMap<String, Any>, rootNode: Tree?, currentNode: Tree,
                             elNumber: Int) {
    val strId = currentNode.nodeId + "[$elNumber]"
    createGraphForContainerElement(el, valuesToFields, fieldsToValues, rootNode, currentNode, strId)
    valuesToFields[el] = strId
    fieldsToValues[strId] = el
}

fun createGraphForMapElement(key: Any, value: Any, valuesToFields: MutableMap<Any, String>,
                             fieldsToValues: MutableMap<String, Any>, rootNode: Tree?, currentNode: Tree) {
    val keyId = currentNode.nodeId + ".key"
    val keyNode = createGraphForContainerElement(key, valuesToFields, fieldsToValues, rootNode, currentNode, keyId)!!
    valuesToFields[key] = keyId
    fieldsToValues[keyId] = key
    val valueId = currentNode.nodeId + ".value"
    createGraphForContainerElement(value, valuesToFields, fieldsToValues, rootNode, keyNode, valueId)
    valuesToFields[value] = valueId
    fieldsToValues[valueId] = value
}

fun createGraphForContainerElement(el: Any, valuesToFields: MutableMap<Any, String>,
                                   fieldsToValues: MutableMap<String, Any>, rootNode: Tree?, currentNode: Tree,
                                   strId: String): Tree? {
    var result: Tree? = null
    if (el::class.java.isPrimitive
        || el::class.java.isAssignableFrom(Number::class.java)
        || el::class.java.isAssignableFrom(Boolean::class.java)
        || el::class.java.isAssignableFrom(Char::class.java)
        || el::class.java.isEnum) {
        result = currentNode.addChild(TreeInfo(strId), Tree(strId))
    } else if (el::class.java.isAssignableFrom(String::class.java)) {
        if (valuesToFields[el] != null) {
            result = currentNode.addChild(
                TreeInfo(strId, isArrayOrCollection = false, isCycle = true),
                rootNode?.getChild(valuesToFields[el]!!)
                    ?: currentNode.getChild(valuesToFields[el]!!)
            )
        } else {
            result = currentNode.addChild(TreeInfo(strId), Tree(strId))
        }
    } else {
        result = currentNode.addChild(
            TreeInfo(
                strId,
                el::class.java.isArray || Collection::class.java.isAssignableFrom(el::class.java)
            ),
            createFieldGraph(
                el, valuesToFields, fieldsToValues,
                strId, rootNode ?: currentNode
            )
        )
    }
    return result
}

/*Should return list of constructor args*/
/*Rewrite it to search values in hashsets by graph*/
fun cloneValues(obj: Any?, valuesToFields: MutableMap<Any, String>, fieldsToValues: MutableMap<String, Any>,
                newObjFieldsToValues: MutableMap<String, Any>,rootNode: Tree, currentNode: Tree): Any? {
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
                var fieldId = currentNode.nodeId + "." + field.name
                if (field.type.isPrimitive
                    || field.type.isAssignableFrom(Number::class.java)
                    || field.type.isAssignableFrom(Boolean::class.java)
                    || field.type.isAssignableFrom(Char::class.java)
                    || field.type.isEnum
                ) {
                    field.set(obj, fieldsToValues[fieldId])
                } else if (field.type.isAssignableFrom(String::class.java)) {
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createStringClone = { id: String ->
                        val newValue = String((fieldsToValues[id] as String).toCharArray())
                        newObjFieldsToValues[id] = newValue
                        field.set(obj, newValue)
                    }
                    if (cycleRef != null) {
                        if (newObjFieldsToValues[cycleRef.nodeId] != null) {
                            field.set(obj, newObjFieldsToValues[cycleRef.nodeId])
                        } else {
                            createStringClone(cycleRef.nodeId)
                        }
                    } else {
                        createStringClone(fieldId)
                    }
                } else if (field.type.isArray) {

                } else if (Collection::class.java.isAssignableFrom(field.type)) {
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createCollectionClone = {id: String ->
                        val collectionValues = (fieldsToValues[id] as Collection<*>).mapIndexed { index, any ->
                                val myId = "$fieldId[$index]"
                                val clone = when {
                                    any == null -> null
                                    any::class.java.isPrimitive -> any
                                    any::class.java.isAssignableFrom(Number::class.java)-> any
                                    any::class.java.isAssignableFrom(Boolean::class.java)-> any
                                    any::class.java.isAssignableFrom(Char::class.java)-> any
                                    any::class.java.isAssignableFrom(String::class.java) -> any
                                    any::class.java.isEnum -> any
                                    else -> createBlankInstance(any)
                                }
                                return@mapIndexed cloneValues(
                                    clone, valuesToFields,
                                    fieldsToValues, newObjFieldsToValues,
                                    rootNode, currentNode.getChild(myId)!!
                                )
                            }
                        newObjFieldsToValues[fieldId] = collectionValues
                        field.set(obj, collectionValues)
                    }
                    if (cycleRef != null) {
                        if (newObjFieldsToValues[cycleRef.nodeId] != null) {
                            field.set(obj, newObjFieldsToValues[cycleRef.nodeId])
                        } else {
                            createCollectionClone(cycleRef.nodeId)
                        }
                    } else {
                        createCollectionClone(fieldId)
                    }
                } else {
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createObjClone = { id: String ->
                        fieldsToValues[id]?.let { v ->
                            currentNode.getChild(id)?.let {
                                val clonedFieldValue = cloneValues(
                                    createBlankInstance(v), valuesToFields, fieldsToValues,
                                    newObjFieldsToValues, rootNode, it
                                )
                                newObjFieldsToValues[id] = clonedFieldValue!!
                                field.set(obj, clonedFieldValue)
                            }
                        }
                    }
                    if (cycleRef != null) {
                        if (newObjFieldsToValues[cycleRef.nodeId] != null) {
                            field.set(obj, newObjFieldsToValues[cycleRef.nodeId])
                        } else {
                            createObjClone(cycleRef.nodeId)
                        }
                    } else {
                        createObjClone(fieldId)
                    }
                }
            }
        }
    }
    return obj
}