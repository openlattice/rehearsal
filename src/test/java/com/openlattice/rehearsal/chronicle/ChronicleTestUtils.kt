package com.openlattice.rehearsal.chronicle

import com.google.common.collect.HashMultimap
import com.google.common.collect.SetMultimap
import org.apache.commons.lang3.tuple.Pair
import java.time.OffsetDateTime
import java.util.*

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

    fun createDateTime(day: Int, month: Int, hour: Int, minute: Int): OffsetDateTime {
        return OffsetDateTime
                .now()
                .withMinute(minute)
                .withHour(hour)
                .withMonth(month)
                .withDayOfMonth(day)
    }


}