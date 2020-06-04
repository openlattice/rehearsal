package com.openlattice.rehearsal.chronicle

import avro.shaded.com.google.common.collect.ImmutableSet
import com.google.common.collect.HashMultimap
import com.google.common.collect.SetMultimap
import com.openlattice.ApiUtil
import com.openlattice.chronicle.ChronicleServerUtil
import com.openlattice.chronicle.constants.EdmConstants.*
import com.openlattice.chronicle.data.ParticipationStatus
import com.openlattice.data.DeleteType
import com.openlattice.data.EntityKey
import com.openlattice.data.UpdateType
import com.openlattice.data.requests.FileType
import com.openlattice.data.requests.NeighborEntityDetails
import com.openlattice.edm.EdmConstants.Companion.ID_FQN
import com.openlattice.edm.type.EntityType
import com.openlattice.rehearsal.SetupTestData
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import com.openlattice.search.requests.EntityNeighborsFilter
import org.apache.commons.lang3.tuple.Pair
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.HashMap

class ChronicleTest : MultipleAuthenticatedUsersBase() {

    companion object {

        // EDM Constants
        private val AUDIT_ENTITY_TYPE_FQN = FullQualifiedName("ol.audit")
        private val PARTICIPANT_ES_PREFIX = "chronicle_participants_"

        // data sent from devices
        private val PARTICIPANT1 = "OL_001"
        private val DEVICE1 = "fca44ee4bb3a3d03_0_0"
        private val PARTICIPANT2 = "OL_002"
        private val DEVICE2 = "fca44ee4bb3a3d03_0_1"
        private val PARTICIPANT3 = "OL_003"
        private val DEVICE3 = "fca44ee4bb3a3d03_0_2"
        private val STUDY_ID = UUID.fromString("3e18b5e0-8e02-4323-aba1-b8eeb3e1892b")

        // test app data
        private val CAMERA = Pair
                .of("com.android.camera2", "Camera")
        private val GMAIL = Pair
                .of("com.google.android.gm", "Gmail")
        private val YOUTUBE = Pair
                .of("com.google.android.youtube", "YouTube")
        private val CHROME = Pair
                .of("com.android.chrome", "Chrome")
        private val MAPS = Pair
                .of("com.google.android.apps.maps", "Maps")
        private val PHONE = Pair
                .of("com.android.dialer", "Phone")

        private var entitySetNameIdMap: Map<String, UUID> = HashMap()
        private lateinit var fullNamePTID: UUID
        private lateinit var durationPTID: UUID
        private lateinit var titlePTID: UUID
        private lateinit var startDateTimePTID: UUID
        private lateinit var dateLoggedPTID: UUID
        private lateinit var recordTypePTID: UUID
        private lateinit var subjectIdPTID: UUID
        private lateinit var statusPTID: UUID
        private lateinit var olIdPTID: UUID
        private lateinit var participantEntityType: EntityType
        private lateinit var auditEntityTypeId: UUID

        private lateinit var participant1EntityKeyId: UUID
        private lateinit var participant2EntityKeyId: UUID
        private lateinit var participant3EntityKeyId: UUID

        private lateinit var device1EntityKeyId: UUID
        private lateinit var device2EntityKeyId: UUID
        private lateinit var device3EntityKeyId: UUID

        @JvmStatic
        @BeforeClass
        fun init() {
            loginAs("admin")
            getPropertyTypeIdsAndEntitySets()
            getDataVariables()
            deleteEntities()
        }

        private fun deleteEntities() {
            val entitySetsIds = HashSet<UUID>()
            entitySetsIds.add(entitySetNameIdMap[CHRONICLE_USER_APPS]!!)
            for (entitySetId in entitySetsIds) {
                dataApi!!.deleteAllEntitiesFromEntitySet(entitySetId, DeleteType.Hard)
            }
        }

        private fun getEntityKeyIds(entitySetId: UUID, key: UUID, entityKey: String): UUID {
            return dataIntegrationApi.getEntityKeyIds(setOf(
                    EntityKey(
                            entitySetId,
                            ApiUtil.generateDefaultEntityId(mutableListOf(key), mapOf(key to setOf(entityKey)))
                    ))).first()
        }

        private fun getDataVariables() {
            val participantEntitySetId = entitySetsApi.getEntitySetId(PARTICIPANT_ES_PREFIX + STUDY_ID)

            participant1EntityKeyId = getEntityKeyIds(participantEntitySetId, subjectIdPTID!!, PARTICIPANT1)
            participant2EntityKeyId = getEntityKeyIds(participantEntitySetId, subjectIdPTID!!, PARTICIPANT2)
            participant3EntityKeyId = getEntityKeyIds(participantEntitySetId, subjectIdPTID!!, PARTICIPANT3)

            device1EntityKeyId = getEntityKeyIds(entitySetNameIdMap[DEVICES_ENTITY_SET_NAME]!!, olIdPTID!!, DEVICE1)
            device2EntityKeyId = getEntityKeyIds(entitySetNameIdMap[DEVICES_ENTITY_SET_NAME]!!, olIdPTID!!, DEVICE2)
            device3EntityKeyId = getEntityKeyIds(entitySetNameIdMap[DEVICES_ENTITY_SET_NAME]!!, olIdPTID!!, DEVICE3)

        }

        private fun getParticipantNeighbors(participantEKID: UUID, studyID: UUID): List<NeighborEntityDetails> {

            val participantES = ChronicleServerUtil.getParticipantEntitySetName(studyID)
            val participantEntitySetId = entitySetsApi!!.getEntitySetId(participantES)
            val userAppsESID = entitySetNameIdMap[CHRONICLE_USER_APPS]
            val usedByESID = entitySetNameIdMap[USED_BY_ENTITY_SET_NAME]

            val participantNeighbors = searchApi!!
                    .executeFilteredEntityNeighborSearch(
                            participantEntitySetId,
                            EntityNeighborsFilter(
                                    ImmutableSet.of(participantEKID),
                                    java.util.Optional.of(ImmutableSet.of(userAppsESID!!)),
                                    java.util.Optional.of(ImmutableSet.of(participantEntitySetId)),
                                    java.util.Optional.of(ImmutableSet.of(usedByESID!!))
                            )
                    )

            return participantNeighbors.getOrDefault(participantEKID, listOf())
        }

        private fun getEnrolledEntity(participantEKID: UUID, studyID: UUID): List<NeighborEntityDetails> {
            val participantES = ChronicleServerUtil.getParticipantEntitySetName(studyID)
            val participantEntitySetId = entitySetsApi!!.getEntitySetId(participantES)
            val studyESID = entitySetNameIdMap[STUDY_ENTITY_SET_NAME]
            val participatedInESID = entitySetNameIdMap[PARTICIPATED_IN_AESN]

            val participantNeighbors = searchApi!!
                    .executeFilteredEntityNeighborSearch(
                            participantEntitySetId,
                            EntityNeighborsFilter(
                                    ImmutableSet.of(participantEKID),
                                    java.util.Optional.of(ImmutableSet.of(participantEntitySetId)),
                                    java.util.Optional.of(ImmutableSet.of(studyESID!!)),
                                    java.util.Optional.of(ImmutableSet.of(participatedInESID!!))
                            )
                    )

            return participantNeighbors.getOrDefault(participantEKID, listOf())
        }

        private fun getParticipantCounts(): MutableMap<UUID, MutableMap<String, Int>> {
            var studies = dataApi
                    .loadEntitySetData(entitySetNameIdMap[STUDY_ENTITY_SET_NAME]!!, FileType.json, null)
                    .map { studyEntity ->
                        var studyId = UUID.fromString(studyEntity.get(GENERAL_ID_FQN)!!.first().toString())
                        var studyEntitySetId = entitySetsApi.getEntitySetId("${PARTICIPANT_ES_PREFIX}$studyId")
                        var participants = dataApi
                                .loadEntitySetData(studyEntitySetId, FileType.json, null)
                                .map { entity -> UUID.fromString(entity.get(ID_FQN).first().toString()) to entity.get(PERSON_ID_FQN).first() }
                                .toMap()
                        var neighbors = searchApi
                                .executeEntityNeighborSearchBulk(studyEntitySetId, participants.keys)
                                .map {
                                    (k, v) -> k to v.filter { k -> k.neighborEntitySet.get().entityTypeId != auditEntityTypeId }
                                }.toMap().toMutableMap()
                        var neighborSizes = neighbors.map {(personEntityKeyId,neighbors) ->
                                    participants
                                            .get(personEntityKeyId)!!
                                            .toString() to neighbors.size}.toMap().toMutableMap()
                        studyId to neighborSizes
                    }.toMap().toMutableMap();

            return studies;
        }


        private fun getPropertyTypeIdsAndEntitySets() {
            fullNamePTID = edmApi.getPropertyTypeId(FULL_NAME_FQN.namespace, FULL_NAME_FQN.name)
            durationPTID = edmApi.getPropertyTypeId(DURATION_FQN.namespace, DURATION_FQN.name)
            titlePTID = edmApi.getPropertyTypeId(TITLE_FQN.namespace, TITLE_FQN.name)
            startDateTimePTID = edmApi.getPropertyTypeId(START_DATE_TIME_FQN.namespace, START_DATE_TIME_FQN.name)
            dateLoggedPTID = edmApi.getPropertyTypeId(DATE_LOGGED_FQN.namespace, DATE_LOGGED_FQN.name)
            recordTypePTID = edmApi.getPropertyTypeId(RECORD_TYPE_FQN.namespace, RECORD_TYPE_FQN.name)
            subjectIdPTID = edmApi.getPropertyTypeId(PERSON_ID_FQN.namespace, PERSON_ID_FQN.name)
            statusPTID = edmApi.getPropertyTypeId(STATUS_FQN.namespace, STATUS_FQN.name)
            olIdPTID = edmApi.getPropertyTypeId(OL_ID_FQN.namespace, OL_ID_FQN.name)

            participantEntityType = edmApi.getEntityType(edmApi.getEntityTypeId(PERSON_FQN.namespace, PERSON_FQN.name))
            auditEntityTypeId = edmApi.getEntityTypeId(AUDIT_ENTITY_TYPE_FQN)

            entitySetNameIdMap = entitySetsApi.getEntitySetIds(setOf(
                    CHRONICLE_USER_APPS,
                    STUDY_ENTITY_SET_NAME,
                    DATA_ENTITY_SET_NAME,
                    RECORDED_BY_ENTITY_SET_NAME,
                    USED_BY_ENTITY_SET_NAME,
                    DEVICES_ENTITY_SET_NAME,
                    PARTICIPATED_IN_AESN
            ))
        }


    }

    @Test
    fun testHardDelete() {

        // integrate test data for multiple studies and participants per study
        deleteEntities()
        SetupTestData.importDataSet("test_chronicle_flight.yaml","test_chronicle_data.csv")

        // count neighbors
        var counts_all = getParticipantCounts()
        chronicleStudyApi.deleteParticipantAndAllNeighbors(STUDY_ID, PARTICIPANT1, DeleteType.Soft)
        var counts_one_down = getParticipantCounts()
        counts_all.get(STUDY_ID)!!.remove((PARTICIPANT1))
        Assert.assertEquals(counts_all, counts_one_down)

        chronicleStudyApi.deleteStudyAndAllNeighbors(STUDY_ID, DeleteType.Soft)
        var counts_two_down = getParticipantCounts()
        counts_all.remove(STUDY_ID)
        Assert.assertEquals(counts_all, counts_two_down)


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

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT3, DEVICE3, data)

        // only 1 entry will be written to chronicle_user_apps and related associations
        val neighbors = getParticipantNeighbors(participant3EntityKeyId!!, STUDY_ID)
        Assert.assertEquals(1, neighbors.size.toLong())
        Assert.assertEquals(1, getDeviceNeighbors(device3EntityKeyId!!).size.toLong())
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

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT3, DEVICE3, data)

        Assert.assertEquals(1, getParticipantNeighbors(participant3EntityKeyId!!, STUDY_ID).size.toLong())
        Assert.assertEquals(1, getDeviceNeighbors(device3EntityKeyId!!).size.toLong())
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

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)

        val participantNeighbors = getParticipantNeighbors(participant1EntityKeyId!!, STUDY_ID)
        Assert.assertEquals(2, participantNeighbors.size.toLong())

        val deviceNeighbors = getDeviceNeighbors(device1EntityKeyId!!)
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
        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)
        Assert.assertEquals(0, getParticipantNeighbors(participant3EntityKeyId!!, STUDY_ID).size.toLong())
        Assert.assertEquals(0, getDeviceNeighbors(device3EntityKeyId!!).size.toLong())

    }

    @Test
    fun testUnEnrolledParticipant() {
        // a participant must be enrolled for data to be logged
        // experiment: un_enroll participant, then try logging data

        val enrollment = getEnrolledEntity(participant2EntityKeyId!!, STUDY_ID)
        if (enrollment.size != 1) {
            throw IllegalArgumentException("There are multiple associations between $PARTICIPANT2 and $STUDY_ID")
        }
        val unEnrollmentEntityKeyId = UUID.fromString(enrollment.get(0).associationDetails.get(ID_FQN)!!.toSet().first().toString())
        val unEnrollmentEntity = mapOf(unEnrollmentEntityKeyId to mapOf(statusPTID!! to setOf(ParticipationStatus.NOT_ENROLLED)))
        dataApi!!.updateEntitiesInEntitySet(enrollment.get(0).associationEntitySet.id, unEnrollmentEntity, UpdateType.PartialReplace)


        val data = ArrayList<SetMultimap<UUID, Any>>()
        val item = createTestDataItem(
                GMAIL,
                ChronicleTestUtils.createDateTime(30, 5, 1, 1),
                ChronicleTestUtils.createDateTime(13, 5, 2, 1),
                Integer.toUnsignedLong(1000)
        )
        data.add(item)

        Assert.assertEquals(0, chronicleApi!!.upload(STUDY_ID, PARTICIPANT2, DEVICE2, data)!!.toInt().toLong())
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

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)

        Assert.assertEquals(2, getParticipantNeighbors(participant1EntityKeyId!!, STUDY_ID).size.toLong())
    }


    private fun getDeviceNeighbors(deviceEntityKeyId: UUID): List<NeighborEntityDetails> {
        val recordedByESID = entitySetNameIdMap[RECORDED_BY_ENTITY_SET_NAME]
        val userAppsESID = entitySetNameIdMap[CHRONICLE_USER_APPS]
        val deviceESID = entitySetNameIdMap[DEVICES_ENTITY_SET_NAME]

        val neighbors = searchApi!!
                .executeFilteredEntityNeighborSearch(
                        entitySetNameIdMap[DEVICES_ENTITY_SET_NAME],
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

        Assert.assertEquals(chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)!!.toInt().toLong(), testApps.size.toLong())

        val dateToday = OffsetDateTime.now().toLocalDate().toString()

        val appsUsageDetails = chronicleStudyApi!!
                .getParticipantAppsUsageData(STUDY_ID, PARTICIPANT1, dateToday)
        Assert.assertEquals(appsUsageDetails.size.toLong(), testApps.size.toLong())

        // validate data
        val appNames = testApps.map{ it.getRight() }
        val packageNames = testApps.map{ it.getLeft() }

        val resultPackageNames = appsUsageDetails
                .map { item -> item.entityDetails[FULL_NAME_FQN]!!.iterator().next().toString() }
        val resultAppNames = appsUsageDetails
                .map { item -> item.entityDetails[TITLE_FQN]!!.iterator().next().toString() }

        Assert.assertEquals(appNames.toSet(), resultAppNames.toSet())
        Assert.assertEquals(packageNames.toSet(), resultPackageNames.toSet())

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

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)

        // 1: get the associations and update them with ol.user property
        val associations = getUserAppsAssociationDetails(STUDY_ID,
                PARTICIPANT1)

        val userTypes = listOf("child", "parent", "parent_and_child")
        associations
                .map { (k, v) -> v.toMutableMap() }
                .forEach { entityData ->
                    entityData.remove(ID_FQN)
                    val users = ChronicleTestUtils.getRandomElements(userTypes)
                    entityData.put(USER_FQN, users)
                }

        Assert.assertEquals(associations.size.toLong(),
                chronicleStudyApi!!.updateAppsUsageAssociationData(STUDY_ID, PARTICIPANT1, associations)!!.toInt().toLong())

        // verify that the associations were correctly updated
        val updateResult = getUserAppsAssociationDetails(STUDY_ID,
                PARTICIPANT1)
        updateResult.forEach { associationId, entityData ->
            Assert.assertEquals(entityData.getOrDefault(USER_FQN, setOf()),
                    associations[associationId]!!.getOrDefault(USER_FQN, setOf()))
            Assert.assertEquals(entityData[DATETIME_FQN].toString(),
                    associations[associationId]!![DATETIME_FQN].toString())
        }
    }


    private fun getUserAppsAssociationDetails(
            studyId: UUID,
            participant: String): Map<UUID, Map<FullQualifiedName, Set<Any>>> {
        val appsUsageDetails = chronicleStudyApi!!
                .getParticipantAppsUsageData(studyId, participant, "2020-05-01")

        return appsUsageDetails
                .map { item ->
                    UUID.fromString(item.associationDetails[ID_FQN]!!.iterator().next().toString()) to item.associationDetails
                }.toMap()
    }

    private fun createTestDataItem(
            userApp: Pair<String, String>,
            startTime: OffsetDateTime,
            dateLogged: OffsetDateTime,
            duration: Long?
    ): SetMultimap<UUID, Any> {
        val data = HashMultimap.create<UUID, Any>()
        data.put(ChronicleTest.fullNamePTID, userApp.left)
        data.put(ChronicleTest.titlePTID, userApp.right)
        data.put(ChronicleTest.startDateTimePTID, startTime.toString())
        data.put(ChronicleTest.dateLoggedPTID, dateLogged.toString())
        data.put(ChronicleTest.durationPTID, duration)
        data.put(ChronicleTest.recordTypePTID, "Usage Stat")

        return data
    }


}
