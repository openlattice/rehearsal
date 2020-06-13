package com.openlattice.rehearsal.chronicle

import com.google.common.collect.HashMultimap
import com.google.common.collect.SetMultimap
import com.openlattice.chronicle.constants.EdmConstants
import com.openlattice.chronicle.data.ParticipationStatus
import com.openlattice.data.UpdateType
import com.openlattice.data.requests.NeighborEntityDetails
import com.openlattice.search.requests.EntityNeighborsFilter
import org.apache.commons.lang3.tuple.Pair
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.junit.Assert
import org.junit.Test
import java.time.OffsetDateTime
import java.util.*


/**
 * @author alfoncenzioka &lt;alfonce@openlattice.com&gt;
 */
class ChronicleTestMobile : ChronicleTestBase() {

    @Test
    fun testGetUserAppsData() {

        deleteEntities()

        // log date with date logged set to today
        val testApps = setOf<Pair<String, String>>(GMAIL, YOUTUBE, CAMERA, CHROME, MAPS, PHONE)
        val data = ArrayList<SetMultimap<UUID, Any>>()
        for (app in testApps) {
            data.add(createTestDataItem(
                    app,
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    java.lang.Long.parseLong("2000")
            ))
        }

        Assert.assertEquals(chronicleApi!!.upload(STUDY_ID1, PARTICIPANT1, DEVICE1, data)!!.toInt().toLong(), testApps.size.toLong())

        val dateToday = OffsetDateTime.now().toLocalDate().toString()

        val appsUsageDetails = chronicleStudyApi!!
                .getParticipantAppsUsageData(STUDY_ID1, PARTICIPANT1, dateToday)
        Assert.assertEquals(appsUsageDetails.size.toLong(), testApps.size.toLong())

        // validate data
        val appNames = testApps.map{ it.getRight() }
        val packageNames = testApps.map{ it.getLeft() }

        val resultPackageNames = appsUsageDetails
                .map { item -> item.entityDetails[EdmConstants.FULL_NAME_FQN]!!.iterator().next().toString() }
        val resultAppNames = appsUsageDetails
                .map { item -> item.entityDetails[EdmConstants.TITLE_FQN]!!.iterator().next().toString() }

        Assert.assertEquals(appNames.toSet(), resultAppNames.toSet())
        Assert.assertEquals(packageNames.toSet(), resultPackageNames.toSet())

    }

    @Test
    fun testInCompleteData() {
        deleteEntities()
        // incomplete items (items missing required properties like 'general.fullname') shouldn't be written to chronicle_user_apps
        // result: only complete items will be logged.

        val data = ArrayList<SetMultimap<UUID, Any>>()
        val item = createTestDataItem(YOUTUBE, OffsetDateTime.now(), OffsetDateTime.now(), Integer.toUnsignedLong(1000))
        data.add(item)

        // incomplete
        val partialEntry = HashMultimap.create(item)
        partialEntry.removeAll(titlePTID)
        partialEntry.removeAll(fullNamePTID)
        data.add(partialEntry)

        chronicleApi!!.upload(STUDY_ID1, PARTICIPANT3, DEVICE3, data)

        // only 1 entry will be written to chronicle_user_apps and related associations
        val neighbors = getParticipantNeighbors(participant3EntityKeyId, STUDY_ID1)
        Assert.assertEquals(1, neighbors.size.toLong())
        Assert.assertEquals(1, getDeviceNeighbors(device3EntityKeyId).size.toLong())
    }

    @Test
    fun testDataRecordType() {
        // data written in chronicle_user_apps and related associations should only be of type 'Usage Stat';
        // experiment: log data with 1 record type set to 'Usage Stat' and another record type set to any other value.

        // result: only the value set to 'Usage Stat should be written in user_apps entity set
//        deleteEntities()

        val data = ArrayList<SetMultimap<UUID, Any>>()
        val item = createTestDataItem(YOUTUBE, OffsetDateTime.now(), OffsetDateTime.now(), Integer.toUnsignedLong(1000))
        data.add(item)

        val partialEntry = HashMultimap.create(item)
        partialEntry.removeAll(recordTypePTID)
        partialEntry.put(recordTypePTID, "Move to background")
        data.add(partialEntry)

        chronicleApi!!.upload(STUDY_ID1, PARTICIPANT3, DEVICE3, data)

        Assert.assertEquals(1, getParticipantNeighbors(participant3EntityKeyId, STUDY_ID1).size.toLong())
        Assert.assertEquals(1, getDeviceNeighbors(device3EntityKeyId).size.toLong())
    }

    @Test
    fun testUniquenessWrtToAppName() {
        deleteEntities()

        // chronicle_user_apps entities are unique for each app
        // log 4 items with matching app name, and 1 item with a different app name:
        // expected entities created in chronicle_user_apps = 2

        val data = ArrayList<SetMultimap<UUID, Any>>()

        var item: SetMultimap<UUID, Any> = HashMultimap.create()

        for (i in 0..3) {
            item = createTestDataItem(
                    GMAIL,
                    ChronicleTestUtils.createDateTime(21, 4, 4, 50),
                    ChronicleTestUtils.createDateTime(21, 4, 4, 30),
                    java.lang.Long.parseLong("2000")
            )
            data.add(item)
        }

        val anotherItem = HashMultimap.create(item)
        anotherItem.removeAll(fullNamePTID)
        anotherItem.put(fullNamePTID, YOUTUBE.left)
        data.add(anotherItem)

        chronicleApi!!.upload(STUDY_ID1, PARTICIPANT1, DEVICE1, data)

        val participantNeighbors = getParticipantNeighbors(participant1EntityKeyId, STUDY_ID1)
        Assert.assertEquals(2, participantNeighbors.size.toLong())

        val deviceNeighbors = getDeviceNeighbors(device1EntityKeyId)
        Assert.assertEquals(2, deviceNeighbors.size.toLong())
    }

    @Test
    fun testZeroDurationProperty() {
        deleteEntities()

        // entities created in chronicle_user_apps should have general.duration property > 0
        val data = ArrayList<SetMultimap<UUID, Any>>()

        for (i in 0..9) {
            val item = createTestDataItem(
                    GMAIL,
                    ChronicleTestUtils.createDateTime(10, 6, 2, 12),
                    ChronicleTestUtils.createDateTime(10, 6, 2, 12),
                    Integer.toUnsignedLong(0)
            )
            data.add(item)

        }
        chronicleApi!!.upload(STUDY_ID1, PARTICIPANT1, DEVICE1, data)
        Assert.assertEquals(0, getParticipantNeighbors(participant3EntityKeyId, STUDY_ID1).size.toLong())
        Assert.assertEquals(0, getDeviceNeighbors(device3EntityKeyId).size.toLong())

    }

    @Test
    fun testUnEnrolledParticipant() {
        // a participant must be enrolled for data to be logged
        // experiment: un_enroll participant, then try logging data

        val enrollment = getEnrolledEntity(participant2EntityKeyId, STUDY_ID1)
        if (enrollment.size != 1) {
            throw IllegalArgumentException("There are multiple associations between $PARTICIPANT2 and $STUDY_ID1")
        }
        val unEnrollmentEntityKeyId = UUID.fromString(enrollment.get(0).associationDetails.get(com.openlattice.edm.EdmConstants.ID_FQN)!!.toSet().first().toString())
        val unEnrollmentEntity = mapOf(unEnrollmentEntityKeyId to mapOf(statusPTID to setOf(ParticipationStatus.NOT_ENROLLED)))
        dataApi!!.updateEntitiesInEntitySet(enrollment.get(0).associationEntitySet.id, unEnrollmentEntity, UpdateType.PartialReplace)


        val data = ArrayList<SetMultimap<UUID, Any>>()
        val item = createTestDataItem(
                GMAIL,
                ChronicleTestUtils.createDateTime(30, 5, 1, 1),
                ChronicleTestUtils.createDateTime(13, 5, 2, 1),
                Integer.toUnsignedLong(1000)
        )
        data.add(item)

        Assert.assertEquals(0, chronicleApi!!.upload(STUDY_ID1, PARTICIPANT2, DEVICE2, data)!!.toInt().toLong())
    }

    @Test
    fun usedByAssociationUniquenessWrtDateLogged() {

        deleteEntities()

        // chronicle_used_by associations are unique for app + user + date logged
        // experiment: 2 matching items (w.r.t app + user + date), an additional item that only differs in the date logged
        // result: 2 used_by_associations

        val data = ArrayList<SetMultimap<UUID, Any>>()
        var item: SetMultimap<UUID, Any> = HashMultimap.create()
        for (i in 0..1) {
            item = createTestDataItem(
                    GMAIL,
                    ChronicleTestUtils.createDateTime(30, 5, 1, 1),
                    ChronicleTestUtils.createDateTime(30, 5, 1, 1),
                    java.lang.Long.parseLong("1000")
            )
            data.add(item)
        }

        val anotherItem = HashMultimap.create(item)
        anotherItem.removeAll(dateLoggedPTID)
        anotherItem.put(dateLoggedPTID, ChronicleTestUtils.createDateTime(13, 5, 2, 1).toString())
        data.add(anotherItem)

        chronicleApi!!.upload(STUDY_ID1, PARTICIPANT1, DEVICE1, data)

        Assert.assertEquals(2, getParticipantNeighbors(participant1EntityKeyId, STUDY_ID1).size.toLong())
    }


    private fun getDeviceNeighbors(deviceEntityKeyId: UUID): List<NeighborEntityDetails> {
        val recordedByESID = entitySetNameIdMap[EdmConstants.RECORDED_BY_ENTITY_SET_NAME]
        val userAppsESID = entitySetNameIdMap[EdmConstants.CHRONICLE_USER_APPS]
        val deviceESID = entitySetNameIdMap[EdmConstants.DEVICES_ENTITY_SET_NAME]

        val neighbors = searchApi!!
                .executeFilteredEntityNeighborSearch(
                        entitySetNameIdMap[EdmConstants.DEVICES_ENTITY_SET_NAME],
                        EntityNeighborsFilter(
                                setOf(deviceEntityKeyId),
                                Optional.of(setOf(userAppsESID!!)),
                                Optional.of(setOf(deviceESID!!)),
                                Optional.of(setOf(recordedByESID!!))
                        )
                )


        return neighbors.getOrDefault(deviceEntityKeyId, listOf())
    }


    @Test
    fun testUpdateUserAppsAssociations() {
        deleteEntities()

        val testApps = setOf<Pair<String, String>>(GMAIL, YOUTUBE, CAMERA, CHROME, MAPS, PHONE)
        val data = ArrayList<SetMultimap<UUID, Any>>()
        for (app in testApps) {
            data.add(createTestDataItem(
                    app,
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    java.lang.Long.parseLong("2000")
            ))
        }

        chronicleApi!!.upload(STUDY_ID1, PARTICIPANT1, DEVICE1, data)

        // 1: get the associations and update them with ol.user property
        val associations = getUserAppsAssociationDetails(STUDY_ID1,
                PARTICIPANT1)

        val userTypes = listOf("child", "parent", "parent_and_child")
        associations
                .map { k -> k.value.toMutableMap() }
                .forEach { entityData ->
                    entityData.remove(com.openlattice.edm.EdmConstants.ID_FQN)
                    val users = ChronicleTestUtils.getRandomElements(userTypes)
                    entityData.put(EdmConstants.USER_FQN, users)
                }

        Assert.assertEquals(associations.size.toLong(),
                chronicleStudyApi!!.updateAppsUsageAssociationData(STUDY_ID1, PARTICIPANT1, associations)!!.toInt().toLong())

        // verify that the associations were correctly updated
        val updateResult = getUserAppsAssociationDetails(STUDY_ID1,
                PARTICIPANT1)
        updateResult.forEach { associationId, entityData ->
            Assert.assertEquals(entityData.getOrDefault(EdmConstants.USER_FQN, setOf()),
                    associations[associationId]!!.getOrDefault(EdmConstants.USER_FQN, setOf()))
            Assert.assertEquals(entityData[EdmConstants.DATE_TIME_FQN].toString(),
                    associations[associationId]!![EdmConstants.DATE_TIME_FQN].toString())
        }
    }

    @Test
    fun testUpload() {
        chronicleStudyApi.submitQuestionnaire(STUDY_ID1, PARTICIPANT1, mapOf() )
    }


    private fun getUserAppsAssociationDetails(
            studyId: UUID,
            participant: String): Map<UUID, Map<FullQualifiedName, Set<Any>>> {
        val appsUsageDetails = chronicleStudyApi!!
                .getParticipantAppsUsageData(studyId, participant, "2020-05-01")

        return appsUsageDetails
                .map { item ->
                    UUID.fromString(item.associationDetails[com.openlattice.edm.EdmConstants.ID_FQN]!!.iterator().next().toString()) to item.associationDetails
                }.toMap()
    }

    private fun createTestDataItem(
            userApp: Pair<String, String>,
            startTime: OffsetDateTime,
            dateLogged: OffsetDateTime,
            duration: Long?
    ): SetMultimap<UUID, Any> {
        val data = HashMultimap.create<UUID, Any>()
        data.put(fullNamePTID, userApp.left)
        data.put(titlePTID, userApp.right)
        data.put(startDateTimePTID, startTime.toString())
        data.put(dateLoggedPTID, dateLogged.toString())
        data.put(durationPTID, duration)
        data.put(recordTypePTID, "Usage Stat")

        return data
    }


}