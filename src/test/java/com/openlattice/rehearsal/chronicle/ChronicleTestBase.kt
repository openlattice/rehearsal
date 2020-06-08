package com.openlattice.rehearsal.chronicle

import avro.shaded.com.google.common.collect.ImmutableSet
import com.openlattice.ApiUtil
import com.openlattice.chronicle.ChronicleServerUtil
import com.openlattice.chronicle.constants.EdmConstants.*
import com.openlattice.data.DeleteType
import com.openlattice.data.EntityKey
import com.openlattice.data.requests.FileType
import com.openlattice.data.requests.NeighborEntityDetails
import com.openlattice.edm.EdmConstants.Companion.ID_FQN
import com.openlattice.edm.type.EntityType
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import com.openlattice.search.requests.EntityNeighborsFilter
import org.apache.commons.lang3.tuple.Pair
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.junit.BeforeClass
import java.util.*
import kotlin.collections.HashMap

open class ChronicleTestBase : MultipleAuthenticatedUsersBase() {

    companion object {

        // EDM Constants
        private val AUDIT_ENTITY_TYPE_FQN = FullQualifiedName("ol.audit")

        // data sent from devices
        const val PARTICIPANT1 = "OL_001"
        const val DEVICE1 = "fca44ee4bb3a3d03_0_0"
        const val PARTICIPANT2 = "OL_002"
        const val DEVICE2 = "fca44ee4bb3a3d03_0_1"
        const val PARTICIPANT3 = "OL_003"
        const val DEVICE3 = "fca44ee4bb3a3d03_0_2"
        val STUDY_ID = UUID.fromString("3e18b5e0-8e02-4323-aba1-b8eeb3e1892b")

        // test app data
        val CAMERA = Pair
                .of("com.android.camera2", "Camera")
        val GMAIL = Pair
                .of("com.google.android.gm", "Gmail")
        val YOUTUBE = Pair
                .of("com.google.android.youtube", "YouTube")
        val CHROME = Pair
                .of("com.android.chrome", "Chrome")
        val MAPS = Pair
                .of("com.google.android.apps.maps", "Maps")
        val PHONE = Pair
                .of("com.android.dialer", "Phone")

        var entitySetNameIdMap: Map<String, UUID> = HashMap()
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

        fun deleteEntities() {
            val entitySetsIds = HashSet<UUID>()
            entitySetsIds.add(entitySetNameIdMap.getValue(CHRONICLE_USER_APPS))
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
            val participantEntitySetId = entitySetsApi.getEntitySetId(PARTICIPANTS_PREFIX + STUDY_ID)

            participant1EntityKeyId = getEntityKeyIds(participantEntitySetId, subjectIdPTID, PARTICIPANT1)
            participant2EntityKeyId = getEntityKeyIds(participantEntitySetId, subjectIdPTID, PARTICIPANT2)
            participant3EntityKeyId = getEntityKeyIds(participantEntitySetId, subjectIdPTID, PARTICIPANT3)

            device1EntityKeyId = getEntityKeyIds(entitySetNameIdMap.getValue(DEVICES_ENTITY_SET_NAME), olIdPTID, DEVICE1)
            device2EntityKeyId = getEntityKeyIds(entitySetNameIdMap.getValue(DEVICES_ENTITY_SET_NAME), olIdPTID, DEVICE2)
            device3EntityKeyId = getEntityKeyIds(entitySetNameIdMap.getValue(DEVICES_ENTITY_SET_NAME), olIdPTID, DEVICE3)

        }

        fun getParticipantNeighbors(participantEKID: UUID, studyID: UUID): List<NeighborEntityDetails> {

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

        fun getEnrolledEntity(participantEKID: UUID, studyID: UUID): List<NeighborEntityDetails> {
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

        fun getParticipantCounts(): MutableMap<UUID, MutableMap<String, Int>> {
            val studies = dataApi
                    .loadEntitySetData(entitySetNameIdMap.getValue(STUDY_ENTITY_SET_NAME), FileType.json, null)
                    .map { studyEntity ->
                        val studyId = UUID.fromString(studyEntity.get(GENERAL_ID_FQN)!!.first().toString())
                        val studyEntitySetId = entitySetsApi.getEntitySetId("$PARTICIPANTS_PREFIX$studyId")
                        val participants = dataApi
                                .loadEntitySetData(studyEntitySetId, FileType.json, null)
                                .map { entity -> UUID.fromString(entity.get(ID_FQN).first().toString()) to entity.get(PERSON_ID_FQN).first() }
                                .toMap()
                        val neighbors = searchApi
                                .executeEntityNeighborSearchBulk(studyEntitySetId, participants.keys)
                                .map { (k, v) ->
                                    k to v.filter { it.neighborEntitySet.get().entityTypeId != auditEntityTypeId }
                                }.toMap().toMutableMap()
                        val neighborSizes = neighbors.map { (personEntityKeyId, neighbors) ->
                            participants
                                    .getValue(personEntityKeyId)
                                    .toString() to neighbors.size
                        }.toMap().toMutableMap()
                        studyId to neighborSizes
                    }.toMap().toMutableMap()

            return studies
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


}
