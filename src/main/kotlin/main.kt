import sun.reflect.ReflectionFactory
import java.lang.reflect.Field

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

data class TreeInfo(val fieldId: String, val isArrayOrCollection: Boolean = false, val isCycle: Boolean = false)
data class Tree(val nodeId: String,
                val children: HashMap<TreeInfo, Tree?> = HashMap()) {
    fun getChild(childId: String): Tree? {
        var result: Tree? = null
        if (childId == nodeId) {
            result = this
        } else {
            for ((k, v) in children) {
                if (!k.isCycle) {
                    result = v?.getChild(childId)
                } else if (v?.nodeId == nodeId) {
                    result = v
                }
                if (result != null) break
            }
        }
        return result
    }

    fun addChild(fieldId: TreeInfo, childNode: Tree?): Tree? {
        children[fieldId] = childNode
        return childNode
    }

    fun isCycleReference(fieldId: String): Tree? {
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

    override fun toString(): String {
        return nodeId
    }
}

//deep copy stops on objects, which package name contains this strings,
//and considers them as cloneable by means of standard library
//val stopList = mutableListOf("java.", "kotlin.")

fun main(args: Array<String>) {
    /*val newMan = deepCopy(
        Man3("Vladislav", 22, listOf("Kak zakalyalas stal"),
            Man(
                "Vladislav", 22, listOf("Pedagogicheskaya poema")
            )
    ))*/
    val man1 = Man(
        "Vladislav", 22, listOf("Pedagogicheskaya poema")
    )
    man1.man = man1
    //val newMap = deepCopy(mapOf(987L to true, 111150 to false, 884636 to null))
    val newMap2 = deepCopy(mapOf("V" to man1, "S" to Man(
        "Sergey", 23, listOf("Kak zakalyalas stal")
    )))
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
            /*Need to test with set, array and self reference*/
            val valuesToFields: MutableMap<Any?, String> = mutableMapOf(obj to "root")
            val fieldsToValues: MutableMap<String, Any?> = mutableMapOf("root" to obj)

            val fieldGraph = createFieldGraph(obj, valuesToFields, fieldsToValues, "root")
            if (fieldGraph != null) {
                val res = createBlankInstance(obj)
                val newObjFieldsToValues: MutableMap<String, Any?> = mutableMapOf("root" to res)

                result = cloneValues(res, valuesToFields, fieldsToValues, newObjFieldsToValues, fieldGraph, fieldGraph)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
    return result
}

fun isNumber(obj: Any) = obj::class.java.isAssignableFrom(Integer::class.java)
        || obj::class.java.isAssignableFrom(java.lang.Long::class.java)
        || obj::class.java.isAssignableFrom(java.lang.Short::class.java)
        || obj::class.java.isAssignableFrom(java.lang.Byte::class.java)
        || obj::class.java.isAssignableFrom(java.lang.Float::class.java)
        || obj::class.java.isAssignableFrom(java.lang.Double::class.java)
        || obj::class.java.isAssignableFrom(Int::class.java)
        || obj::class.java.isAssignableFrom(Long::class.java)
        || obj::class.java.isAssignableFrom(Short::class.java)
        || obj::class.java.isAssignableFrom(Byte::class.java)
        || obj::class.java.isAssignableFrom(Float::class.java)
        || obj::class.java.isAssignableFrom(Double::class.java)

fun createFieldGraph(
    obj: Any?, valuesToFields: MutableMap<Any?, String>, fieldsToValues: MutableMap<String, Any?>,
    nodeId: String, rootNode: Tree? = null
): Tree? {
    val currentNode = Tree(nodeId)
    if (obj != null) {
        if (obj::class.java.isPrimitive
            || isNumber(obj)
            || obj::class.java.isAssignableFrom(Boolean::class.java)
            || obj::class.java.isAssignableFrom(java.lang.Boolean::class.java)
            || obj::class.java.isAssignableFrom(Char::class.java)
            || obj::class.java.isAssignableFrom(Character::class.java)
            || obj::class.java.isAssignableFrom(String::class.java)
            || obj::class.java.isEnum
        ) {
            valuesToFields[obj] = nodeId
            fieldsToValues[nodeId] = obj
        } else if (List::class.java.isAssignableFrom(obj::class.java)) {
            (obj as List<*>).forEachIndexed { index, any -> any?.let {
                createGraphForArrayOrListElement(it, valuesToFields, fieldsToValues, rootNode, currentNode, index)
            }}
        } else if (Set::class.java.isAssignableFrom(obj::class.java)) {
            (obj as Set<*>).forEachIndexed { index, any ->
                createGraphForSetElement(any, valuesToFields, fieldsToValues, rootNode, currentNode, index)
            }
        } else if (Map::class.java.isAssignableFrom(obj::class.java)) {
            var mapIndex = 0
            (obj as Map<*, *>).forEach { (k, v) ->
                createGraphForMapElement(k, v, valuesToFields, fieldsToValues, rootNode, currentNode, mapIndex)
                mapIndex++
            }
        } else if (obj::class.java.isArray) {
            (obj as Array<*>).forEachIndexed { index, any ->
                createGraphForArrayOrListElement(any, valuesToFields, fieldsToValues, rootNode, currentNode, index)
            }
        } else {
            for (field in obj::class.java.declaredFields) {
                field.isAccessible = true
                if (field.type.isPrimitive
                    || isNumber(obj)
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
                    } else if (field.get(obj) == obj) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = false, isCycle = true),
                            currentNode
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
                    } else if (field.get(obj) == obj) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = true, isCycle = true),
                            currentNode
                        )
                    }else {
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
                    }else if (field.get(obj) == obj) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = true, isCycle = true),
                            currentNode
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
                    } else if (field.get(obj) == obj) {
                        currentNode.addChild(
                            TreeInfo(strId, isArrayOrCollection = false, isCycle = true),
                            currentNode
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

fun createGraphForArrayOrListElement(el: Any?, valuesToFields: MutableMap<Any?, String>,
                                     fieldsToValues: MutableMap<String, Any?>, rootNode: Tree?, currentNode: Tree,
                                     elNumber: Int) {
    val strId = currentNode.nodeId + "[$elNumber]"
    createGraphForContainerElement(el, valuesToFields, fieldsToValues, rootNode, currentNode, strId)
    valuesToFields[el] = strId
    fieldsToValues[strId] = el
}

fun createGraphForSetElement(el: Any?, valuesToFields: MutableMap<Any?, String>,
                             fieldsToValues: MutableMap<String, Any?>, rootNode: Tree?, currentNode: Tree,
                             elNumber: Int) {
    val strId = currentNode.nodeId + "[$elNumber]"
    createGraphForContainerElement(el, valuesToFields, fieldsToValues, rootNode, currentNode, strId)
    valuesToFields[el] = strId
    fieldsToValues[strId] = el
}

fun createGraphForMapElement(key: Any?, value: Any?, valuesToFields: MutableMap<Any?, String>,
                             fieldsToValues: MutableMap<String, Any?>, rootNode: Tree?, currentNode: Tree, mapIndex: Int) {
    val keyId = currentNode.nodeId + "[$mapIndex].key"
    createGraphForContainerElement(key, valuesToFields, fieldsToValues, rootNode, currentNode, keyId)
    valuesToFields[key] = keyId
    fieldsToValues[keyId] = key
    val valueId = currentNode.nodeId + "[$mapIndex].value"
    createGraphForContainerElement(value, valuesToFields, fieldsToValues, rootNode, currentNode, valueId)
    valuesToFields[value] = valueId
    fieldsToValues[valueId] = value
}

fun createGraphForContainerElement(el: Any?, valuesToFields: MutableMap<Any?, String>,
                                   fieldsToValues: MutableMap<String, Any?>, rootNode: Tree?, currentNode: Tree,
                                   strId: String): Tree? {
    var result: Tree? = null
        if (el == null
            || el::class.java.isPrimitive
            || isNumber(el)
            || el::class.java.isAssignableFrom(Boolean::class.java)
            || el::class.java.isAssignableFrom(java.lang.Boolean::class.java)
            || el::class.java.isAssignableFrom(Char::class.java)
            || el::class.java.isAssignableFrom(Character::class.java)
            || el::class.java.isEnum
        ) {
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

fun matchType(obj: Any?) = when {
    obj == null -> null
    obj::class.java.isPrimitive -> obj
    isNumber(obj)-> obj
    obj::class.java.isAssignableFrom(Boolean::class.java)-> obj
    obj::class.java.isAssignableFrom(Char::class.java)-> obj
    obj::class.java.isAssignableFrom(java.lang.Boolean::class.java)-> obj
    obj::class.java.isAssignableFrom(Character::class.java)-> obj
    obj::class.java.isAssignableFrom(String::class.java) -> obj
    obj::class.java.isEnum -> obj
    else -> createBlankInstance(obj)
}

fun clonePrimitiveWrapper(pr: Any): Any = when (pr) {
    is Boolean -> pr.toString().toBoolean()
    is Char -> pr.toString().toInt().toChar()
    is Int -> pr.toString().toInt()
    is Long -> pr.toString().toLong()
    is Byte -> pr.toString().toByte()
    is Short -> pr.toString().toShort()
    is Float -> pr.toString().toFloat()
    is Double -> pr.toString().toDouble()
    is UInt -> pr.toString().toUInt()
    is ULong -> pr.toString().toULong()
    is UByte -> pr.toString().toUByte()
    is UShort -> pr.toString().toUShort()
    else -> pr.toString()
}

/*Should return list of constructor args*/
/*Rewrite it to search values in hashsets by graph*/
fun cloneValues(obj: Any?, valuesToFields: MutableMap<Any?, String>, fieldsToValues: MutableMap<String, Any?>,
                newObjFieldsToValues: MutableMap<String, Any?>,rootNode: Tree, currentNode: Tree): Any? {
    if(obj != null) {
        if (obj::class.java.isPrimitive
            || obj::class.java.isEnum
        ) {
            return obj
        } else if (isNumber(obj)
            || obj::class.java.isAssignableFrom(Boolean::class.java)
            || obj::class.java.isAssignableFrom(Char::class.java)
            || obj::class.java.isAssignableFrom(java.lang.Boolean::class.java)
            || obj::class.java.isAssignableFrom(Character::class.java)) {
            return clonePrimitiveWrapper(obj)
        } else if (obj::class.java.isAssignableFrom(String::class.java)) {
            return (obj as String).toCharArray().toString()
        } else if (Map::class.java.isAssignableFrom(obj::class.java)) {
            return cloneMap(createBlankInstance(obj), valuesToFields, fieldsToValues,
                newObjFieldsToValues, rootNode, currentNode, "root", null)
        } else {
            for (field in obj::class.java.declaredFields) {
                field.isAccessible = true
                val fieldId = currentNode.nodeId + "." + field.name
                if (field.type.isPrimitive
                    || isNumber(obj)
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
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createArrayClone = {id: String ->
                        val collectionValues = (fieldsToValues[id] as Array<*>).mapIndexed { index, any ->
                            val myId = "$fieldId[$index]"
                            val clone = matchType(any)
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
                            createArrayClone(cycleRef.nodeId)
                        }
                    } else {
                        createArrayClone(fieldId)
                    }
                } else if (List::class.java.isAssignableFrom(field.type)) {
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createCollectionClone = {id: String ->
                        val collectionValues = (fieldsToValues[id] as List<*>).mapIndexed { index, any ->
                                val myId = "$fieldId[$index]"
                                val clone = matchType(any)
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
                } else if (Set::class.java.isAssignableFrom(field.type)) {
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createCollectionClone = {id: String ->
                        val collectionValues = (fieldsToValues[id] as Set<*>).mapIndexed { index, any ->
                            val myId = "$fieldId[$index]"
                            val clone = matchType(any)
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
                } else if (Map::class.java.isAssignableFrom(field.type)) {
                    val cycleRef = currentNode.isCycleReference(fieldId)
                    val createCollectionClone = {id: String ->
                        var index = 0
                        val collectionValues = (fieldsToValues[id] as Map<*, *>).map { (k, v) ->
                            val keyId = "$fieldId[$index].key"
                            val valueId = "$fieldId[$index].value"

                            val keyClone = cloneValues(matchType(k), valuesToFields,
                                fieldsToValues, newObjFieldsToValues,
                                rootNode, currentNode.getChild(keyId)!!
                            )
                            
                            val valueClone = cloneValues(matchType(v), valuesToFields,
                                fieldsToValues, newObjFieldsToValues,
                                rootNode, currentNode.getChild(valueId)!!
                            )
                            index++
                            return@map keyClone to valueClone
                        }.toMap()
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
                        if (cycleRef == currentNode) {
                            field.set(obj, obj)
                        } else {
                            if (newObjFieldsToValues[cycleRef.nodeId] != null) {
                                field.set(obj, newObjFieldsToValues[cycleRef.nodeId])
                            } else {
                                createObjClone(cycleRef.nodeId)
                            }
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

fun cloneMap(obj: Any, valuesToFields: MutableMap<Any?, String>, fieldsToValues: MutableMap<String, Any?>,
             newObjFieldsToValues: MutableMap<String, Any?>, rootNode: Tree, currentNode: Tree, fieldId: String, field: Field?): Map<*, *>? {
    val cycleRef = currentNode.isCycleReference(fieldId)
    var item: Map<*, *>? = null
    val createCollectionClone = {id: String ->
        var index = 0
        val collectionValues = (fieldsToValues[id] as Map<*, *>).map { (k, v) ->
            val keyId = "$fieldId[$index].key"
            val valueId = "$fieldId[$index].value"

            val keyClone = cloneValues(matchType(k), valuesToFields,
                fieldsToValues, newObjFieldsToValues,
                rootNode, currentNode.getChild(keyId)!!
            )

            val valueClone = cloneValues(matchType(v), valuesToFields,
                fieldsToValues, newObjFieldsToValues,
                rootNode, currentNode.getChild(valueId)!!
            )
            index++
            return@map keyClone to valueClone
        }.toMap()
        newObjFieldsToValues[fieldId] = collectionValues
        field?.set(obj, collectionValues)
        item = collectionValues
    }
    if (cycleRef != null) {
        if (newObjFieldsToValues[cycleRef.nodeId] != null) {
            field?.set(obj, newObjFieldsToValues[cycleRef.nodeId])
            item = newObjFieldsToValues[cycleRef.nodeId] as Map<*, *>?
        } else {
            createCollectionClone(cycleRef.nodeId)
        }
    } else {
        createCollectionClone(fieldId)
    }
    return item
}