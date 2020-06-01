package com.openlattice.rehearsal.chronicle

import avro.shaded.com.google.common.cache.CacheBuilder
import avro.shaded.com.google.common.cache.CacheLoader
import avro.shaded.com.google.common.cache.LoadingCache
import avro.shaded.com.google.common.collect.ImmutableSet
import com.google.common.collect.HashMultimap
import com.google.common.collect.SetMultimap
import com.google.common.eventbus.EventBus
import com.google.inject.Inject
import com.openlattice.chronicle.ChronicleApi
import com.openlattice.chronicle.ChronicleServerUtil
import com.openlattice.chronicle.ChronicleStudyApi
import com.openlattice.chronicle.configuration.ChronicleConfiguration
import com.openlattice.chronicle.data.ChronicleAppsUsageDetails
import com.openlattice.chronicle.services.ChronicleService
import com.openlattice.chronicle.services.ChronicleServiceImpl
import com.openlattice.client.ApiClient
import com.openlattice.client.RetrofitFactory
import com.openlattice.data.DataApi
import com.openlattice.data.DeleteType
import com.openlattice.data.requests.NeighborEntityDetails
import com.openlattice.edm.EdmApi
import com.openlattice.entitysets.EntitySetsApi
import com.openlattice.search.SearchApi
import com.openlattice.search.requests.EntityNeighborsFilter
import org.apache.commons.lang3.tuple.Pair
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.stream.Collectors

import com.openlattice.edm.EdmConstants.Companion.ID_FQN
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase.loginAs

class ChronicleServerTests {

    companion object {

        internal var configuration = ChronicleConfiguration("user", "password")
        @Inject
        private val eventBus: EventBus? = null


        @JvmStatic
        @BeforeClass
        fun init() {
            loginAs("admin")
        }

//        internal var chronicleService: ChronicleService = ChronicleServiceImpl(eventBus, configuration)

        protected val logger = LoggerFactory.getLogger(ChronicleServerTests::class.java)

        // property type FQNS
        private val FULL_NAME_FQN = FullQualifiedName("general.fullname")
        private val DURATION = FullQualifiedName("general.Duration")
        private val DATE_LOGGED_FQN = FullQualifiedName("ol.datelogged")
        private val START_DATE_TIME = FullQualifiedName("ol.datetimestart")
        private val TITLE_FQN = FullQualifiedName("ol.title")
        private val DATETIME_FQN = FullQualifiedName("ol.datetime")
        private val USER_FQN = FullQualifiedName("ol.user")
        private val RECORD_TYPE_FQN = FullQualifiedName("ol.recordtype")

        private val STUDY_ES_NAME = "chronicle_study"
        private val DEVICES_ES_NAME = "chronicle_device"
        private val APP_DATA_ES_NAME = "chronicle_app_data"
        private val RECORDED_BY_ES_NAME = "chronicle_recorded_by"
        private val CHRONICLE_USER_APPS = "chronicle_user_apps"
        private val USED_BY_ES_NAME = "chronicle_used_by"

        private val DEVICE1 = "30d34cef1b0052e8"
        private val DEVICE3 = "b368482c2607fe37"

        private val STUDY_ID = UUID.fromString("36ba6fab-76fa-4fe4-ad65-df4eae1f307a")
        private val PARTICIPANT1 = "participant1"
        private val UN_ENROLLED_PARTICIPANT = "participant2"
        private val PARTICIPANT3 = "participant3"

        private val participant1EntityKeyId = UUID.fromString("00c60000-0000-0000-8000-000000000004")
        private val participant3EntityKeyId = UUID.fromString("3a870000-0000-0000-8000-000000000011")

        private val device1EntityKeyId = UUID.fromString("033d0000-0000-0000-8000-000000000004")
        private val device3EntityKeyId = UUID.fromString("a5210000-0000-0000-8000-000000000014")

        // test app data
        private val CAMERA = Pair
                .of("com.android.camera2", "Camera")
        private val GMAIL = Pair
                .of("Gmail", "com.google.android.gm")
        private val YOUTUBE = Pair
                .of("come.google.android.youtube", "YouTube")
        private val CHROME = Pair
                .of("com.android.chrome", "Chrome")
        private val MAPS = Pair
                .of("com.google.android.apps.maps", "Maps")
        private val PHONE = Pair
                .of("com.android.dialer", "Phone")

        private var entitySetNameIdMap: Map<String, UUID> = HashMap()
        private var fullNamePTID: UUID? = null
        private var durationPTID: UUID? = null
        private var titlePTID: UUID? = null
        private var startDateTimePTID: UUID? = null
        private var dateLoggedPTID: UUID? = null
        private var recordTypePTID: UUID? = null

        private var dataApi: DataApi? = null
        private var edmApi: EdmApi? = null
        private var searchApi: SearchApi? = null
        private var entitySetsApi: EntitySetsApi? = null

        private var chronicleApi: ChronicleApi? = null
        private var chronicleStudyApi: ChronicleStudyApi? = null

        @BeforeClass
        @Throws(Exception::class)
        fun chronicleServerTest() {


            setUpTestingEnvironment()
        }

        private fun deleteEntities() {

            val entitySetsIds = HashSet<UUID>()
            entitySetsIds.add(entitySetNameIdMap[CHRONICLE_USER_APPS]!!)
            entitySetsIds.add(entitySetNameIdMap[APP_DATA_ES_NAME]!!)
            // entitySetsIds.add( entitySetNameIdMap.get( RECORDED_BY_ES_NAME ) );
            // entitySetsIds.add( entitySetNameIdMap.get( USED_BY_ES_NAME ) );

            for (entitySetId in entitySetsIds) {
                dataApi!!.deleteAllEntitiesFromEntitySet(entitySetId, DeleteType.Hard)
            }
        }

        private fun getParticipantNeighbors(participantEKID: UUID, studyID: UUID): List<NeighborEntityDetails> {

            val participantES = ChronicleServerUtil.getParticipantEntitySetName(studyID)
            val participantEntitySetId = entitySetsApi!!.getEntitySetId(participantES)
            val userAppsESID = entitySetNameIdMap[CHRONICLE_USER_APPS]
            val usedByESID = entitySetNameIdMap[USED_BY_ES_NAME]

            val participantNeighbors = searchApi!!
                    .executeFilteredEntityNeighborSearch(
                            participantEntitySetId,
                            EntityNeighborsFilter(
                                    ImmutableSet.of(participantEKID),
                                    java.util.Optional.of(ImmutableSet.of<UUID>(userAppsESID)),
                                    java.util.Optional.of(ImmutableSet.of(participantEntitySetId)),
                                    java.util.Optional.of(ImmutableSet.of<UUID>(usedByESID))
                            )
                    )

            return (participantNeighbors as java.util.Map<UUID, List<NeighborEntityDetails>>).getOrDefault(participantEKID, listOf<NeighborEntityDetails>())
        }

        private fun setUpTestingEnvironment() {
            getPropertyTypeIdsAndEntitySets()
            deleteEntities()
        }

        private fun getPropertyTypeIdsAndEntitySets() {

            fullNamePTID = edmApi!!.getPropertyTypeId(FULL_NAME_FQN.namespace, FULL_NAME_FQN.name)
            durationPTID = edmApi!!.getPropertyTypeId(DURATION.namespace, DURATION.name)
            titlePTID = edmApi!!.getPropertyTypeId(TITLE_FQN.namespace, TITLE_FQN.name)
            startDateTimePTID = edmApi!!.getPropertyTypeId(START_DATE_TIME.namespace, START_DATE_TIME.name)
            dateLoggedPTID = edmApi!!.getPropertyTypeId(DATE_LOGGED_FQN.namespace, DATE_LOGGED_FQN.name)
            recordTypePTID = edmApi!!.getPropertyTypeId(RECORD_TYPE_FQN.namespace, RECORD_TYPE_FQN.name)

            entitySetNameIdMap = entitySetsApi!!.getEntitySetIds(setOf<String>(
                    CHRONICLE_USER_APPS,
                    STUDY_ES_NAME,
                    APP_DATA_ES_NAME,
                    RECORDED_BY_ES_NAME,
                    USED_BY_ES_NAME,
                    DEVICES_ES_NAME
            ))
        }

        @AfterClass
        fun resetTestingEnvironment() {

        }
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
        Assert.assertEquals(1, getParticipantNeighbors(participant3EntityKeyId, STUDY_ID).size.toLong())
        Assert.assertEquals(1, getDeviceNeighbors(device3EntityKeyId).size.toLong())
    }

    @Test
    fun testDataRecordType() {
        // data written in chronicle_user_apps and related associations should only be of type 'Usage Stat';
        // experiment: log data with 1 record type set to 'Usage Stat' and another record type set to any other value.

        // result: only the value set to 'Usage Stat should be written in user_apps entity set
        deleteEntities()

        val data = ArrayList<SetMultimap<UUID, Any>>()
        val item = createTestDataItem(YOUTUBE, OffsetDateTime.now(), OffsetDateTime.now(), Integer.toUnsignedLong(1000))
        data.add(item)

        val partialEntry = HashMultimap.create(item)
        partialEntry.removeAll(recordTypePTID)
        partialEntry.put(recordTypePTID, "Move to background")
        data.add(partialEntry)

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT3, DEVICE3, data)

        Assert.assertEquals(1, getParticipantNeighbors(participant3EntityKeyId, STUDY_ID).size.toLong())
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
                    createDateTime(21, 4, 4, 50),
                    createDateTime(21, 4, 4, 30),
                    java.lang.Long.parseLong("2000")
            )
            data.add(item)
        }

        val anotherItem = HashMultimap.create(item)
        anotherItem.removeAll(fullNamePTID)
        anotherItem.put(fullNamePTID, YOUTUBE.left)
        data.add(anotherItem)

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)

        val participantNeighbors = getParticipantNeighbors(participant1EntityKeyId, STUDY_ID)
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
                    createDateTime(10, 6, 2, 12),
                    createDateTime(10, 6, 2, 12),
                    Integer.toUnsignedLong(0)
            )
            data.add(item)

        }
        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)
        Assert.assertEquals(0, getParticipantNeighbors(participant3EntityKeyId, STUDY_ID).size.toLong())
        Assert.assertEquals(0, getDeviceNeighbors(device1EntityKeyId).size.toLong())

    }

    @Test
    fun testUnEnrolledParticipant() {
        // a participant must be enrolled for data to be logged
        // experiment: un_enroll participant, then try logging data

        val data = ArrayList<SetMultimap<UUID, Any>>()
        val item = createTestDataItem(
                GMAIL,
                createDateTime(30, 5, 1, 1),
                createDateTime(13, 5, 2, 1),
                Integer.toUnsignedLong(1000)
        )
        data.add(item)

        Assert.assertEquals(0, chronicleApi!!.upload(STUDY_ID, UN_ENROLLED_PARTICIPANT, DEVICE1, data)!!.toInt().toLong())
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
                    createDateTime(30, 5, 1, 1),
                    createDateTime(30, 5, 1, 1),
                    java.lang.Long.parseLong("1000")
            )
            data.add(item)
        }

        val anotherItem = HashMultimap.create(item)
        anotherItem.removeAll(dateLoggedPTID)
        anotherItem.put(dateLoggedPTID, createDateTime(13, 5, 2, 1).toString())
        data.add(anotherItem)

        chronicleApi!!.upload(STUDY_ID, PARTICIPANT1, DEVICE1, data)

        Assert.assertEquals(2, getParticipantNeighbors(participant1EntityKeyId, STUDY_ID).size.toLong())
    }


    private fun getDeviceNeighbors(deviceEntityKeyId: UUID): List<NeighborEntityDetails> {
        val recordedByESID = entitySetNameIdMap[RECORDED_BY_ES_NAME]
        val userAppsESID = entitySetNameIdMap[CHRONICLE_USER_APPS]
        val deviceESID = entitySetNameIdMap[DEVICES_ES_NAME]

        val neighbors = searchApi!!
                .executeFilteredEntityNeighborSearch(
                        entitySetNameIdMap[DEVICES_ES_NAME],
                        EntityNeighborsFilter(
                                        setOf(deviceEntityKeyId),
                                Optional.of(setOf<UUID>(userAppsESID!!)),
                                Optional.of(setOf<UUID>(deviceESID!!)),
                                Optional.of(setOf<UUID>(recordedByESID!!))
                        )
                )


        return (neighbors as java.util.Map<UUID, List<NeighborEntityDetails>>).getOrDefault(deviceEntityKeyId, listOf<NeighborEntityDetails>())
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

        val appsUsageDetails = chronicleStudyApi!!
                .getParticipantAppsUsageData(STUDY_ID, PARTICIPANT1, "2020-05-01")
        Assert.assertEquals(appsUsageDetails.size.toLong(), testApps.size.toLong())

        // validate data
        val appNames = testApps.stream().map( { it.getRight() }).collect(Collectors.toSet<String>())
        val packageNames = testApps.stream().map( { it.getLeft() }).collect(Collectors.toSet<String>())

        val resultPackageNames = appsUsageDetails.stream()
                .map { item -> item.entityDetails[FULL_NAME_FQN]!!.iterator().next().toString() }
        val resultAppNames = appsUsageDetails.stream()
                .map { item -> item.entityDetails[TITLE_FQN]!!.iterator().next().toString() }

        Assert.assertEquals(appNames, resultAppNames)
        Assert.assertEquals(packageNames, resultPackageNames)

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

        val userTypes = listOf<String>("child", "parent", "parent_and_child")
        associations
                .map {(k, v) -> k to v.toMutableMap()}
                .forEach { (entityKeyId, entityData) ->
            entityData.remove(ID_FQN)
            val users = ChronicleServerTestUtils.getRandomElements(userTypes)
            entityData.put(USER_FQN, users)
        }

        Assert.assertEquals(associations.size.toLong(),
                chronicleStudyApi!!.updateAppsUsageAssociationData(STUDY_ID, PARTICIPANT1, associations)!!.toInt().toLong())

        // verify that the associations were correctly updated
        val updateResult = getUserAppsAssociationDetails(STUDY_ID,
                PARTICIPANT1)
        updateResult.forEach { associationId, entityData ->
            Assert.assertEquals((entityData as java.util.Map<FullQualifiedName, Set<Any>>).getOrDefault(USER_FQN, setOf<Any>()),
                    (associations[associationId] as java.util.Map<FullQualifiedName, Set<Any>>).getOrDefault(USER_FQN, setOf<Any>()))
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

    private fun createDateTime(day: Int, month: Int, hour: Int, minute: Int): OffsetDateTime {
        return OffsetDateTime
                .now()
                .withMinute(minute)
                .withHour(hour)
                .withMonth(month)
                .withDayOfMonth(day)
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
