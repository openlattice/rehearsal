package com.openlattice.rehearsal.chronicle

import java.util.HashSet
import java.util.Random

/**
 * @author alfoncenzioka &lt;alfonce@openlattice.com&gt;
 */
object ChronicleTestUtils {

    fun getRandomElement(userTypes: List<String>): String {
        val random = Random()
        return userTypes[random.nextInt(userTypes.size)]
    }

    fun getRandomElements(userTypes: List<String>): Set<Any> {
        val result = HashSet<Any>()
        val random = Random()
        val numItems = random.nextInt(userTypes.size + 1)


        for (i in 0 until numItems) {
            result.add(getRandomElement(userTypes))
        }
        return result
    }
}