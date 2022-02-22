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

    public fun addChild(fieldId: TreeInfo, childNode: Tree?) {
        children[fieldId] = childNode
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

fun deepCopy(obj: Any): Any? {
    var result: Any? = null
    try {
        val res = createBlankInstance(obj)
        val valuesToFields = HashMap<Any, String>()
        val fieldsToValues = HashMap<String, Any>()
        val newObjFieldsToValues = HashMap<String, Any>()
        val fieldGraph = createFieldGraph(obj, valuesToFields, fieldsToValues, "root")
        result = cloneValues(res, valuesToFields, fieldsToValues, newObjFieldsToValues, fieldGraph, fieldGraph)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
    return result
}

fun createFieldGraph(
    obj: Any, valuesToFields: HashMap<Any, String>, fieldsToValues: HashMap<String, Any>,
    nodeId: String, rootNode: Tree? = null
): Tree {
    val currentNode = Tree(nodeId)
    if (Collection::class.java.isAssignableFrom(obj::class.java)) {
        (obj as Collection<*>).forEachIndexed{ index, any -> any?.let {
            createGraphForArrayOrCollectionElement(it, valuesToFields, fieldsToValues, rootNode, currentNode, index)
        }}
    } else if (obj::class.java.isArray) {
        (obj as Array<*>).forEachIndexed{ index, any -> any?.let {
            createGraphForArrayOrCollectionElement(it, valuesToFields, fieldsToValues, rootNode, currentNode, index)
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
}

fun createGraphForArrayOrCollectionElement(el: Any, valuesToFields: HashMap<Any, String>,
                                           fieldsToValues: HashMap<String, Any>, rootNode: Tree?, currentNode: Tree,
                                           elNumber: Int) {
    val strId = currentNode.nodeId + "[$elNumber]"
    if (el::class.java.isAssignableFrom(String::class.java)) {
        if (valuesToFields[el] != null) {
            currentNode.addChild(
                TreeInfo(strId, isArrayOrCollection = false, isCycle = true),
                rootNode?.getChild(valuesToFields[el]!!)
                    ?: currentNode.getChild(valuesToFields[el]!!)
            )
        } else {
            currentNode.addChild(TreeInfo(strId), Tree(strId))
        }
    } else if (!(el::class.java.isPrimitive
                || el::class.java.isAssignableFrom(Number::class.java)
                || el::class.java.isAssignableFrom(Boolean::class.java)
                || el::class.java.isAssignableFrom(Char::class.java)
                || el::class.java.isEnum)
    ) {
        currentNode.addChild(
            TreeInfo(
                currentNode.nodeId + "[$elNumber]",
                el::class.java.isArray || Collection::class.java.isAssignableFrom(el::class.java)
            ),
            createFieldGraph(
                el, valuesToFields, fieldsToValues,
                currentNode.nodeId + "[$elNumber]", rootNode ?: currentNode
            )
        )
    }
    valuesToFields[el] = strId
    fieldsToValues[strId] = el
}

/*Should return list of constructor args*/
/*Rewrite it to search values in hashsets by graph*/
fun cloneValues(obj: Any?, valuesToFields: HashMap<Any, String>, fieldsToValues: HashMap<String, Any>,
                newObjFieldsToValues: HashMap<String, Any>,rootNode: Tree, currentNode: Tree): Any? {
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
                    for ((k, v) in currentNode.children) {
                        if (fieldId == k.fieldId) {
                            if (k.isCycle) {
                                /*There could be multiple cycle reference chain, the algorithm should unwrap it all*/
                                field.set(obj, newObjFieldsToValues[v!!.nodeId])
                            } else {
                                val newValue = String((fieldsToValues[fieldId] as String).toCharArray())
                                newObjFieldsToValues[fieldId] = newValue
                                field.set(obj, newValue)
                            }
                            break
                        }
                    }
                }
            }
        }
    }
    return obj
}