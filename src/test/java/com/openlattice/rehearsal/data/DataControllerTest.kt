/*
 * Copyright (C) 2018. OpenLattice, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the owner of the copyright at support@openlattice.com
 *
 *
 */

package com.openlattice.rehearsal.data

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.openlattice.authorization.*
import com.openlattice.data.*
import com.openlattice.data.requests.EntitySetSelection
import com.openlattice.data.requests.FileType
import com.openlattice.edm.EdmConstants
import com.openlattice.edm.requests.MetadataUpdate
import com.openlattice.edm.type.EntityType
import com.openlattice.mapstores.TestDataFactory
import com.openlattice.rehearsal.assertException
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import com.openlattice.rehearsal.edm.EdmTestConstants
import com.openlattice.search.requests.EntityNeighborsFilter
import com.openlattice.search.requests.SearchTerm
import org.apache.commons.lang.RandomStringUtils
import org.apache.olingo.commons.api.edm.FullQualifiedName
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.time.*
import java.util.*
import kotlin.math.abs

/**
 *
 * @author Matthew Tamayo-Rios &lt;matthew@openlattice.com&gt;
 */

private const val numberOfEntries = 10
private val random = Random()

class DataControllerTest : MultipleAuthenticatedUsersBase() {
    companion object {
        val fqnCache: LoadingCache<UUID, FullQualifiedName> = CacheBuilder.newBuilder()
                .build(
                        object : CacheLoader<UUID, FullQualifiedName>() {
                            override fun load(key: UUID?): FullQualifiedName {
                                return edmApi.getPropertyType(key!!).type
                            }

                        }
                )

        lateinit var personEt: EntityType

        @JvmStatic
        @BeforeClass
        fun init() {
            loginAs("admin")

            personEt = EdmTestConstants.personEt
        }
    }

    @Test
    fun testCreateAndLoadEntityData() {
        val et = createEntityType()
        val es = createEntitySet(et)

        val testData = TestDataFactory.randomStringEntityData(numberOfEntries, et.properties).values.toList()
        val entities = dataApi.createEntities(es.id, testData).toSet().zip(testData).toMap()
        val ess = EntitySetSelection(Optional.of(et.properties))
        val results1 = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toSet()

        Assert.assertEquals(numberOfEntries.toLong(), results1.size.toLong())
        results1.forEach {
            val id = it.getValue(EdmConstants.ID_FQN).first()
            val originalData = entities.getValue(UUID.fromString(id as String))
            it.forEach { (fqn, value) ->
                if (fqn != EdmConstants.ID_FQN) {
                    val propertyId = edmApi.getPropertyTypeId(fqn.namespace, fqn.name)
                    Assert.assertEquals(originalData.getValue(propertyId).first(), value.first())
                }
            }
        }

        // optional/nullable EntitySetSelection in loadSelectedEntitySetData cannot be tested from here, only manually
        // Retrofit will throw java.lang.IllegalArgumentException: Body parameter value must not be null.
    }

    @Test
    fun testLoadEntityDataEmptySelection() {
        val et = createEntityType()
        val es = createEntitySet(et)

        val testData = TestDataFactory.randomStringEntityData(numberOfEntries, et.properties).values.toList()
        dataApi.createEntities(es.id, testData)
        val ess = EntitySetSelection(Optional.empty(), Optional.empty())
        val results = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toSet()

        Assert.assertEquals(numberOfEntries.toLong(), results.size.toLong())
    }


    @Test
    fun testCreateAndLoadBinaryEntityData() {
        val pt = getBinaryPropertyType()
        val et = createEntityType(pt.id)
        val es = createEntitySet(et)

        val testData = TestDataFactory.randomBinaryData(numberOfEntries, et.key.iterator().next(), pt.id).values.toList()

        dataApi.createEntities(es.id, testData)

        val ess = EntitySetSelection(Optional.of(et.properties))
        val results = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toSet()

        Assert.assertEquals(numberOfEntries.toLong(), results.size.toLong())
    }

    @Test
    fun testCreateLoadReplaceLoadData() {
        val et = createEntityType()
        val es = createEntitySet(et)

        val testData = TestDataFactory.randomStringEntityData(numberOfEntries, et.properties)

        val entries = testData.values.toList()
        val ids = dataApi.createEntities(es.id, entries)

        val indexExpected = entries.mapIndexed { index, data -> ids[index] to keyByFqn(data) }.toMap()

        val ess = EntitySetSelection(
                Optional.of(et.properties),
                Optional.of(HashSet(ids))
        )
        val data = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json)
        val indexActual = index(data)

        //Remove the extra properties for easier equals.
        indexActual.forEach {
            it.value.remove(EdmConstants.ID_FQN)
            it.value.remove(EdmConstants.LAST_INDEX_FQN)
            it.value.remove(EdmConstants.LAST_WRITE_FQN)
        }

        Assert.assertEquals(indexExpected, indexActual)

        val propertySrc = entries[0]
        val replacementProperty = propertySrc.keys.first()
        val replacement = mapOf(replacementProperty to setOf(RandomStringUtils.random(10) as Any))

        val replacementMap = mapOf(ids[0] to replacement)

        Assert.assertEquals(1, dataApi.updateEntitiesInEntitySet(es.id, replacementMap, UpdateType.PartialReplace))

        val ess2 = EntitySetSelection(
                Optional.of(et.properties),
                Optional.of(setOf(ids[0]))
        )
        val data2 = dataApi.loadSelectedEntitySetData(es.id, ess2, FileType.json).toList()

        val indexActual2 = index(data2)

        //Remove the extra properties for easier equals.
        indexActual2.forEach {
            it.value.remove(EdmConstants.ID_FQN)
            it.value.remove(EdmConstants.LAST_INDEX_FQN)
            it.value.remove(EdmConstants.LAST_WRITE_FQN)
        }

        Assert.assertFalse(
                data2[0][fqnCache[replacementProperty]] == indexActual.getValue(ids[0])[fqnCache[replacementProperty]]
        )
    }

    @Test
    fun testCreateEdgesAuthorization() {
        val src = createEntityType()
        val dst = createEntityType()
        val edge = createEdgeEntityType()

        val esSrc = createEntitySet(src)
        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        // create association type with defining src and dst entity types
        createAssociationType(edge, setOf(src), setOf(dst))

        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entriesSrc = testDataSrc.values.toList()
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = testDataEdge.values.toList()
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edges = idsSrc.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc.id, idsSrc[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        val createdEdges = dataApi.createEdges(edges)

        Assert.assertNotNull(createdEdges)
        Assert.assertEquals(edges.size, createdEdges)

        // Test permissions on entity sets. First add write permission to src,dst
        val readWrite = EnumSet.of(Permission.WRITE, Permission.READ)
        val newAcl1 = Acl(AclKey(esSrc.id), setOf(Ace(user1, readWrite, OffsetDateTime.now(ZoneOffset.UTC))))
        val newAcl2 = Acl(AclKey(esDst.id), setOf(Ace(user1, readWrite, OffsetDateTime.now(ZoneOffset.UTC))))

        permissionsApi.updateAcl(AclData(newAcl1, Action.ADD))
        permissionsApi.updateAcl(AclData(newAcl2, Action.ADD))

        loginAs("user1")
        assertException(
                { dataApi.createEdges(edges) },
                "Insufficient permissions to perform operation."
        )
        loginAs("admin")

        val newAcl3 = Acl(AclKey(esEdge.id), setOf(Ace(user1, readWrite, OffsetDateTime.now(ZoneOffset.UTC))))
        permissionsApi.updateAcl(AclData(newAcl3, Action.ADD))

        loginAs("user1")
        dataApi.createEdges(edges)
        loginAs("admin")
    }

    @Test
    fun testCreateEdgesWithData() {
        val et = createEdgeEntityType()
        val es = createEntitySet(et)
        val src = createEntityType()
        val esSrc = createEntitySet(src)
        val dst = createEntityType()
        val esDst = createEntitySet(dst)

        // create association type with defining src and dst entity types
        createAssociationType(et, setOf(src), setOf(dst))

        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)

        val entriesSrc = testDataSrc.values.toList()
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val edgeData = createDataEdges(es.id, et.properties, esSrc.id, idsSrc, esDst.id, idsDst)

        val createdEdges = dataApi.createAssociations(mapOf(edgeData))

        Assert.assertNotNull(createdEdges)
        Assert.assertEquals(edgeData.second.size, createdEdges.getValue(edgeData.first).size)

        val ess = EntitySetSelection(
                Optional.of(et.properties),
                Optional.of(HashSet(createdEdges.values.flatten()))
        )

        // when loading entitysets, the result is grouped by entity key id
        val actualEdgeData = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json)
        val edgesCreatedData = edgeData.second
        actualEdgeData.mapIndexed { index, de ->
            val edgeDataLookup = lookupEdgeDataByFqn(
                    edgesCreatedData[index].data.mapValues { it.value.toMutableSet() }.toMutableMap()
            )
            de
                    .filter { it.key.name != EdmConstants.ID_FQN.name }
                    .forEach { (fqn, data) -> Assert.assertEquals(data, edgeDataLookup[fqn]) }
        }
    }

    @Test
    fun testCreateEdgesWithDifferentEntityTypes() {
        // Test for createAssociations( ListMultimap<UUID, DataEdge> associations )
        val edge1 = createEdgeEntityType()
        val esEdge1 = createEntitySet(edge1)
        val src = createEntityType()
        val esSrc = createEntitySet(src)
        val dst = createEntityType()
        val esDst = createEntitySet(dst)

        // create empty association type
        createAssociationType(edge1, setOf(), setOf())

        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)

        val entriesSrc = testDataSrc.values.toList()
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val edgeData = createDataEdges(esEdge1.id, edge1.properties, esSrc.id, idsSrc, esDst.id, idsDst)
        val edgesToBeCreated = mapOf(edgeData)

        assertException(
                { dataApi.createAssociations(edgesToBeCreated) },
                "differs from allowed entity types ([]) in association type of entity set ${esEdge1.id}"
        )

        // add src and dst to association type
        edmApi.addSrcEntityTypeToAssociationType(edge1.id, src.id)
        edmApi.addDstEntityTypeToAssociationType(edge1.id, dst.id)
        dataApi.createAssociations(edgesToBeCreated)


        // Test for createEdges( Set<DataEdgeKey> associations )
        val edge2 = createEdgeEntityType()
        val esEdge2 = createEntitySet(edge2)

        // create empty association type
        createAssociationType(edge2, setOf(), setOf())

        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge2.properties)
        val entriesEdge = testDataEdge.values.toList()
        val idsEdge = dataApi.createEntities(esEdge2.id, entriesEdge)

        val edges = idsSrc.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc.id, idsSrc[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge2.id, idsEdge[index])
            )
        }.toSet()

        assertException(
                { dataApi.createEdges(edges) },
                "differs from allowed entity types ([]) in association type of entity set ${esEdge2.id}"
        )

        // add src and dst to association type
        edmApi.addSrcEntityTypeToAssociationType(edge2.id, src.id)
        edmApi.addDstEntityTypeToAssociationType(edge2.id, dst.id)
        dataApi.createAssociations(edgesToBeCreated)
    }


    @Test
    fun testCreateBidirectionalEdgesWithDifferentEntityTypes() {
        // Test for createEdges
        val edge = createEdgeEntityType()
        val esEdge = createEntitySet(edge)
        val src = createEntityType()
        val esSrc = createEntitySet(src)
        val dst = createEntityType()
        val esDst = createEntitySet(dst)

        // create association type with only src
        createAssociationType(edge, setOf(src), setOf(), true)

        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entriesSrc = testDataSrc.values.toList()
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = testDataEdge.values.toList()
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edgeData1 = createDataEdges(esEdge.id, edge.properties, esSrc.id, idsSrc, esDst.id, idsDst)
        val edgesToBeCreated1 = mapOf(edgeData1)
        val edgesToBeCreated2 = createDataEdgeKeys(esEdge.id, idsEdge, esDst.id, idsDst, esSrc.id, idsSrc)

        // try to create edges
        assertException(
                { dataApi.createAssociations(edgesToBeCreated1) },
                "differs from allowed entity types src=[${src.id}], dst=[] in bidirectional association type of " +
                        "entity set ${esEdge.id}"
        )

        // try to create edges opposite direction
        assertException(
                { dataApi.createEdges(edgesToBeCreated2) },
                "differs from allowed entity types src=[${src.id}], dst=[] in bidirectional association type of " +
                        "entity set ${esEdge.id}"
        )

        // add dst to association type
        edmApi.addDstEntityTypeToAssociationType(edge.id, dst.id)

        // create edges
        dataApi.createAssociations(edgesToBeCreated1)

        // create edges is opposite direction
        dataApi.createEdges(edgesToBeCreated2)
    }

    private fun lookupEdgeDataByFqn(edgeData: MutableMap<UUID, MutableCollection<Any>>):
            Map<FullQualifiedName, MutableCollection<Any>> {
        return edgeData.mapKeys { entry -> edmApi.getPropertyType(entry.key).type }
    }


    private fun createDataEdges(
            entitySetId: UUID,
            properties: Set<UUID>,
            srcEntitySetId: UUID,
            srcIds: List<UUID>,
            dstEntitySetId: UUID,
            dstIds: List<UUID>
    ): Pair<UUID, List<DataEdge>> {
        val edgeData = TestDataFactory.randomStringEntityData(numberOfEntries, properties).values.toList()

        val edges = srcIds.mapIndexed { index, _ ->
            val srcDataKey = EntityDataKey(srcEntitySetId, srcIds[index])
            val dstDataKey = EntityDataKey(dstEntitySetId, dstIds[index])
            DataEdge(srcDataKey, dstDataKey, edgeData[index])
        }

        return entitySetId to edges
    }

    private fun createDataEdgeKeys(
            edgeEntitySetId: UUID,
            edgeIds: List<UUID>,
            srcEntitySetId: UUID,
            srcIds: List<UUID>,
            dstEntitySetId: UUID,
            dstIds: List<UUID>
    ): Set<DataEdgeKey> {
        return srcIds.mapIndexed { index, _ ->
            val srcDataKey = EntityDataKey(srcEntitySetId, srcIds[index])
            val dstDataKey = EntityDataKey(dstEntitySetId, dstIds[index])
            val edgeDataKey = EntityDataKey(edgeEntitySetId, edgeIds[index])
            DataEdgeKey(srcDataKey, dstDataKey, edgeDataKey)
        }.toSet()
    }

    private fun keyByFqn(data: Map<UUID, Set<Any>>): Map<FullQualifiedName, Set<Any>> {
        return data.map { fqnCache[it.key] to it.value }.toMap()
    }

    private fun index(
            data: Iterable<Map<FullQualifiedName, Set<Any>>>
    ): Map<UUID, MutableMap<FullQualifiedName, Set<Any>>> {
        return data.map {
            UUID.fromString(it.getValue(EdmConstants.ID_FQN).first() as String) to it.toMutableMap()
        }.toMap()
    }

    @Test
    fun testDateTypes() {
        val p1 = createDateTimePropertyType()
        val k = createPropertyType()
        val p2 = createDatePropertyType()

        val et = TestDataFactory.entityType(k)

        et.removePropertyTypes(et.properties)
        et.addPropertyTypes(setOf(k.id, p1.id, p2.id))

        val entityTypeId = edmApi.createEntityType(et)
        Assert.assertNotNull("Entity type creation shouldn't return null UUID.", entityTypeId)

        val es = createEntitySet(et)

        val d = LocalDate.now()
        val odt = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"))
        val testData = listOf(
                mapOf(
                        p1.id to setOf(odt as Any),
                        p2.id to setOf(d as Any),
                        k.id to setOf(RandomStringUtils.randomAlphanumeric(5) as Any)
                )
        )

        //added transformValues()
        dataApi.createEntities(es.id, testData)
        val ess = EntitySetSelection(Optional.of(et.properties))
        val results = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toSet()

        Assert.assertEquals(testData.size.toLong(), results.size.toLong())
        val result = results.iterator().next()
        val p1v = OffsetDateTime.parse(result.getValue(p1.type).first() as CharSequence)
        val p2v = LocalDate.parse(result.getValue(p2.type).first() as CharSequence)
        //There is a problem with the represenation of the DateTime of pv1, gets truncated. Instead code now
        //compares if odt and p1v are within 100 milliseconds
        val odtMillisec = odt.nano / 1000000
        val p1vMillisec = p1v.nano / 1000000
        Assert.assertTrue(abs(odtMillisec - p1vMillisec) < 100)
        Assert.assertEquals(d, p2v)
    }

    @Test
    fun testLoadSelectedEntityData() {
        val et = createEntityType()
        val es = createEntitySet(et)

        //added transformValues()
        val entities = TestDataFactory.randomStringEntityData(numberOfEntries, et.properties).values.toList()
        dataApi.createEntities(es.id, entities)

        // load selected data
        val selectedProperties = et.properties.filter { random.nextBoolean() }.toSet()
        val ess = EntitySetSelection(Optional.of(selectedProperties))
        val results = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json)

        // check results
        // For each entity, collect its property value in one set, and collect all these sets together.
        val resultValues = HashSet<Set<String>>()
        for (entity in results) {
            resultValues.add(
                    entity.entries
                            .filter { e -> !e.key.fullQualifiedNameAsString.contains("@") }
                            .flatMap { e -> e.value }
                            .map { o -> o as String }
                            .toSet()
            )
        }

        val expectedValues = HashSet<Set<String>>()
        for (entity in entities) {
            expectedValues
                    .add(
                            entity.entries
                                    // filter the entries with key (propertyId) in the selected set
                                    .filter { e -> selectedProperties.isEmpty() || selectedProperties.contains(e.key) }
                                    // Put all the property values in the same stream, and cast them back to strings
                                    .flatMap { e -> e.value }
                                    .map { o -> o as String }
                                    .toSet()
                    )
        }

        Assert.assertEquals(expectedValues, resultValues)
    }

    @Test
    fun testUpdatePropertyTypeMetadata() {
        val pt = createPropertyType()
        val et = createEntityType(pt.id)
        val es = createEntitySet(et)

        // add test data
        val testData = TestDataFactory.randomStringEntityData(1, et.properties).values.toList()

        dataApi.createEntities(es.id, testData)

        val oldNameSpace = pt.type.namespace
        val newNameSpace = oldNameSpace + "extrachars"

        // Update propertytype type
        val update = MetadataUpdate(
                Optional.of(pt.title),
                Optional.empty(),
                Optional.of(es.name),
                Optional.of(es.contacts),
                Optional.of(FullQualifiedName(newNameSpace, pt.type.name)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        )
        edmApi.updatePropertyTypeMetadata(pt.id, update)

        val ess = EntitySetSelection(Optional.of(et.properties))
        val results = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toSet()

        val fqns = results.iterator().next().keys
        Assert.assertEquals(1, fqns.asSequence().filter { it.namespace == newNameSpace }.count())
        Assert.assertEquals(0, fqns.asSequence().filter { it.namespace == oldNameSpace }.count())
    }

    @Test
    fun testLoadDataAuthorizations() {
        // create data with admin
        val et = createEntityType()
        val es = createEntitySet(et)

        val testData = TestDataFactory.randomStringEntityData(numberOfEntries, et.properties)

        val entries = testData.values.toList()
        val ids = dataApi.createEntities(es.id, entries)

        val indexExpected = entries.mapIndexed { index, data -> ids[index] to keyByFqn(data) }.toMap()
        val ess = EntitySetSelection(Optional.of(et.properties), Optional.of(HashSet(ids)))


        /* loadSelectedEntitySetData */

        // try to read data with no permissions on it
        loginAs("user1")
        assertException(
                { dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json) },
                "Insufficient permissions to read the entity set ${es.id} or it doesn't exists."
        )
        loginAs("admin")

        // add permission to read entityset but none of the properties
        val readPermission = EnumSet.of(Permission.READ)
        val esReadAcl = Acl(AclKey(es.id), setOf(Ace(user1, readPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(esReadAcl, Action.ADD))

        loginAs("user1")
        val noData = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList()
        Assert.assertEquals(numberOfEntries, noData.size)
        noData.forEach { Assert.assertEquals(setOf(EdmConstants.ID_FQN), it.keys) }
        loginAs("admin")


        // add permission on 1 property
        val pt1 = edmApi.getPropertyType(et.properties.first())
        val pt1ReadAcl = Acl(AclKey(es.id, pt1.id), setOf(Ace(user1, readPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(pt1ReadAcl, Action.ADD))
        loginAs("user1")
        val pt1Data = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList()
        Assert.assertEquals(numberOfEntries, pt1Data.size)
        pt1Data.forEach { Assert.assertEquals(setOf(EdmConstants.ID_FQN, pt1.type), it.keys) }
        loginAs("admin")


        // add permission on all properties
        et.properties.forEach {
            val ptReadAcl = Acl(AclKey(es.id, it), setOf(Ace(user1, readPermission, OffsetDateTime.MAX)))
            permissionsApi.updateAcl(AclData(ptReadAcl, Action.ADD))
        }

        loginAs("user1")
        val dataAll = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json)
        val indexActualAll = index(dataAll)

        //Remove the extra properties for easier equals.
        indexActualAll.forEach {
            it.value.remove(EdmConstants.ID_FQN)
            it.value.remove(EdmConstants.LAST_INDEX_FQN)
            it.value.remove(EdmConstants.LAST_WRITE_FQN)
        }

        Assert.assertEquals(indexExpected, indexActualAll)
        loginAs("admin")


        /* getEntity, getEntityPropertyValues */

        val et2 = createEntityType()
        val es2 = createEntitySet(et2)

        val testData2 = TestDataFactory.randomStringEntityData(1, et2.properties)

        val entries2 = testData2.values.toList()
        val id = dataApi.createEntities(es2.id, entries2)[0]
        val property = et2.properties.first()

        // try to read data with no permissions on it
        loginAs("user1")
        assertException(
                { dataApi.getEntity(es2.id, id) },
                "Object [${es2.id}] is not accessible."
        )
        assertException(
                { dataApi.getEntityPropertyValues(es2.id, id, property) },
                "Object [${es2.id}] is not accessible."
        )
        loginAs("admin")

        // add permission to read entityset but none of the properties
        val es2ReadAcl = Acl(AclKey(es2.id), setOf(Ace(user1, readPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(es2ReadAcl, Action.ADD))

        loginAs("user1")
        val noData2 = dataApi.getEntity(es2.id, id)
        Assert.assertEquals(1, noData2.size)
        noData2.forEach { Assert.assertEquals(EdmConstants.ID_FQN, it.key) }

        assertException(
                { dataApi.getEntityPropertyValues(es2.id, id, property) },
                "Not authorized to read property type $property in entity set ${es2.id}."
        )
        loginAs("admin")


        // add permission on 1 property
        val pt = edmApi.getPropertyType(property)
        val ptReadAcl = Acl(AclKey(es2.id, pt.id), setOf(Ace(user1, readPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(ptReadAcl, Action.ADD))

        loginAs("user1")
        val ptData1 = dataApi.getEntity(es2.id, id)
        Assert.assertEquals(1, ptData1.getValue(EdmConstants.ID_FQN).size)
        Assert.assertEquals(setOf(EdmConstants.ID_FQN, pt.type), ptData1.keys)
        val ptData2 = dataApi.getEntityPropertyValues(es2.id, id, property)
        Assert.assertEquals(1, ptData2.size)
        Assert.assertEquals(entries2[0][property], ptData2)
        loginAs("admin")


        // add permission on all properties
        et2.properties.forEach {
            permissionsApi.updateAcl(AclData(
                    Acl(AclKey(es2.id, it), setOf(Ace(user1, readPermission, OffsetDateTime.MAX))),
                    Action.ADD))
        }

        loginAs("user1")
        val dataAll1 = dataApi.getEntity(es2.id, id)
        Assert.assertEquals(1, dataAll1.getValue(EdmConstants.ID_FQN).size)
        val fqns = et2.properties.map { edmApi.getPropertyType(it).type }.toMutableSet()
        fqns.add(EdmConstants.ID_FQN)
        Assert.assertEquals(fqns, dataAll1.keys)
        val dataAll2 = dataApi.getEntityPropertyValues(es2.id, id, property)
        Assert.assertEquals(1, dataAll2.size)
        Assert.assertEquals(entries2[0][property], dataAll2)

        loginAs("admin")
    }


    /* Deletes */

    @Test
    fun testNotAuthorizedDelete() {
        val es = createEntitySet(personEt)

        val entries = (1..numberOfEntries)
                .map { mapOf(EdmTestConstants.personGivenNameId to setOf(RandomStringUtils.randomAscii(5))) }
        val newEntityIds = dataApi.createEntities(es.id, entries)

        // create edges with original entityset as source
        val dst = createEntityType()
        val edge = createEdgeEntityType()

        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        // create association type with defining src and dst entity types
        createAssociationType(edge, setOf(personEt), setOf(dst))

        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = testDataEdge.values.toList()
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edges = newEntityIds.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es.id, newEntityIds[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createEdges(edges)


        /*   HARD DELETE   */
        val ownerPermission = EnumSet.of(Permission.OWNER)

        loginAs("user1")
        assertException(
                { dataApi.deleteEntities(es.id, newEntityIds.toSet(), DeleteType.Hard) },
                listOf(
                        "Unable to delete from entity sets [${es.id}]: missing required permissions " +
                                "$ownerPermission for AclKeys",
                        "[${es.id}]", // no owner permission on es entity set
                        personEt.properties.random().toString() // no owner permission on any property type
                )
        )
        loginAs("admin")

        // add user1 as owner of entityset

        val esOwnerAcl = Acl(AclKey(es.id), setOf(Ace(user1, ownerPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(esOwnerAcl, Action.ADD))

        loginAs("user1")
        // should not contain es acl key ("[es.id]") anymore, only properties
        assertException(
                { dataApi.deleteEntities(es.id, newEntityIds.toSet(), DeleteType.Hard) },
                listOf(
                        "Unable to delete from entity sets [${es.id}]: missing required permissions " +
                                "$ownerPermission for AclKeys",
                        personEt.properties.random().toString() // no owner permission on any property type
                )
        )
        loginAs("admin")

        // add user1 as owner for all property types in entityset
        personEt.properties.forEach {
            val acl = Acl(AclKey(es.id, it), setOf(Ace(user1, ownerPermission, OffsetDateTime.MAX)))
            permissionsApi.updateAcl(AclData(acl, Action.ADD))
        }

        loginAs("user1")
        assertException(
                { dataApi.deleteEntities(es.id, newEntityIds.toSet(), DeleteType.Hard) },
                listOf(
                        "Unable to delete from entity set ${es.id}: missing required permissions $ownerPermission " +
                                "on associations for AclKeys",
                        "[${esEdge.id}]", // no owner permission on esEdge entity set
                        edge.properties.random().toString() // no owner permission on any property type
                )
        )
        loginAs("admin")

        // try to delete also neighbors
        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.empty()
                            ),
                            DeleteType.Hard
                    )
                },
                listOf(
                        "Unable to delete from entity set ${es.id}: missing required permissions $ownerPermission " +
                                "on associations for AclKeys",
                        "[${esEdge.id}]", // no owner permission on esEdge entity set
                        edge.properties.random().toString() // no owner permission on any property type
                )
        )
        loginAs("admin")

        // add user1 as owner of edge entity set
        val edgeOwnerAcl = Acl(AclKey(esEdge.id), setOf(Ace(user1, ownerPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(edgeOwnerAcl, Action.ADD))

        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.of(setOf(esEdge.id))
                            ),
                            DeleteType.Hard
                    )
                },
                listOf(
                        "Unable to delete from entity set ${es.id}: missing required permissions $ownerPermission " +
                                "on associations for AclKeys",
                        edge.properties.random().toString() // no owner permission on any property type
                )
        )
        loginAs("admin")

        // add owner to user1 for all property types in edge entityset
        edge.properties.forEach {
            val acl = Acl(AclKey(esEdge.id, it), setOf(Ace(user1, ownerPermission, OffsetDateTime.MAX)))
            permissionsApi.updateAcl(AclData(acl, Action.ADD))
        }

        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.empty()
                            ),
                            DeleteType.Hard
                    )
                },
                listOf(
                        "Unable to delete from entity sets [${esDst.id}]: missing required permissions " +
                                "$ownerPermission for AclKeys",
                        "[${esDst.id}]", // no read permission on es entity set
                        dst.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")


        // add owner to user1 for dst entity set
        val dstOwnerAcl = Acl(AclKey(esDst.id), setOf(Ace(user1, ownerPermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(dstOwnerAcl, Action.ADD))

        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.empty()
                            ),
                            DeleteType.Hard
                    )
                },
                listOf(
                        "Unable to delete from entity sets [${esDst.id}]: missing required permissions " +
                                "$ownerPermission for AclKeys",
                        dst.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")


        /*   SOFT DELETE   */
        val writePermission = EnumSet.of(Permission.WRITE)

        loginAs("user1")
        assertException(
                { dataApi.deleteEntities(es.id, newEntityIds.toSet(), DeleteType.Soft) },
                listOf(
                        "Unable to delete from entity sets [${es.id}]: missing required permissions $writePermission " +
                                "for AclKeys",
                        "[${es.id}]", // no read permission on es entity set
                        personEt.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")

        // add write to user1 for entityset
        val esAcl = Acl(AclKey(es.id), setOf(Ace(user1, writePermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(esAcl, Action.ADD))

        loginAs("user1")
        assertException(
                { dataApi.deleteEntities(es.id, newEntityIds.toSet(), DeleteType.Soft) },
                listOf(
                        "Unable to delete from entity sets [${es.id}]: missing required permissions $writePermission " +
                                "for AclKeys",
                        personEt.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")


        // add write to user1 for all property types in entityset
        personEt.properties.forEach {
            val acl = Acl(AclKey(es.id, it), setOf(Ace(user1, writePermission, OffsetDateTime.MAX)))
            permissionsApi.updateAcl(AclData(acl, Action.ADD))
        }

        loginAs("user1")
        assertException(
                { dataApi.deleteEntities(es.id, newEntityIds.toSet(), DeleteType.Soft) },
                listOf(
                        "Unable to delete from entity set ${es.id}: missing required permissions $writePermission " +
                                "on associations for AclKeys",
                        "[${esEdge.id}]", // no read permission on esEdge entity set
                        edge.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")


        // try to delete also neighbors
        // add write to user1 for edge entity set
        val edgeWriteAcl = Acl(AclKey(esEdge.id), setOf(Ace(user1, writePermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(edgeWriteAcl, Action.ADD))

        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.empty()
                            ),
                            DeleteType.Soft
                    )
                },
                listOf(
                        "Unable to delete from entity set ${es.id}: missing required permissions $writePermission " +
                                "on associations for AclKeys",
                        edge.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")


        // add write to user1 for all property types in edge entityset
        edge.properties.forEach {
            val acl = Acl(AclKey(esEdge.id, it), setOf(Ace(user1, writePermission, OffsetDateTime.MAX)))
            permissionsApi.updateAcl(AclData(acl, Action.ADD))
        }

        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.empty()
                            ),
                            DeleteType.Soft
                    )
                },
                listOf(
                        "Unable to delete from entity sets [${esDst.id}]: missing required permissions " +
                                "$writePermission for AclKeys",
                        "[${esDst.id}]", // no read permission on es entity set
                        dst.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")


        // add write to user1 for dst entity set
        val dstWriteAcl = Acl(AclKey(esDst.id), setOf(Ace(user1, writePermission, OffsetDateTime.MAX)))
        permissionsApi.updateAcl(AclData(dstWriteAcl, Action.ADD))

        loginAs("user1")
        assertException(
                {
                    dataApi.deleteEntitiesAndNeighbors(
                            EntityNeighborsFilter(
                                    mapOf(es.id to newEntityIds.toSet()),
                                    Optional.empty(),
                                    Optional.of(setOf(esDst.id)),
                                    Optional.empty()
                            ),
                            DeleteType.Soft
                    )
                },
                listOf(
                        "Unable to delete from entity sets [${esDst.id}]: missing required permissions " +
                                "$writePermission for AclKeys",
                        dst.properties.random().toString() // no read permission on any property type
                )
        )
        loginAs("admin")
    }

    @Test
    fun testDeleteEntities() {
        /* Regular entity set */
        val es = createEntitySet(personEt)

        val entries = (1..numberOfEntries)
                .map { mapOf(EdmTestConstants.personGivenNameId to setOf(RandomStringUtils.randomAscii(5))) }
        val newEntityIds = dataApi.createEntities(es.id, entries)

        val ess = EntitySetSelection(Optional.of(personEt.properties))
        Assert.assertEquals(numberOfEntries, dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList().size)

        dataApi.deleteEntities(es.id, setOf(newEntityIds[0]), DeleteType.Hard)

        val loadedEntries = dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList()

        Assert.assertEquals(numberOfEntries - 1, loadedEntries.size)
        Assert.assertTrue(loadedEntries.none {
            it[EdmTestConstants.personGivenNameFqn] == entries.first().values
        })

        dataApi.deleteEntities(es.id, newEntityIds.drop(1).toSet(), DeleteType.Hard)
        Assert.assertEquals(0, dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList().size)

        /* Association entity set */
        val et = createEdgeEntityType()
        val aes = createEntitySet(et)
        val src = createEntityType()
        val esSrc = createEntitySet(src)
        val dst = createEntityType()
        val esDst = createEntitySet(dst)
        createAssociationType(et, setOf(src), setOf(dst))

        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)

        val entriesSrc = testDataSrc.values.toList()
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val edgeData = createDataEdges(aes.id, et.properties, esSrc.id, idsSrc, esDst.id, idsDst)
        val edgesToBeCreated = mapOf(edgeData)

        val createdEdges = dataApi.createAssociations(edgesToBeCreated).getValue(aes.id)

        val aess = EntitySetSelection(Optional.of(et.properties))
        Assert.assertEquals(numberOfEntries, dataApi.loadSelectedEntitySetData(aes.id, aess, FileType.json).toList().size)

        dataApi.deleteEntities(aes.id, setOf(createdEdges[0]), DeleteType.Soft)
        val loadedAssociations = dataApi.loadSelectedEntitySetData(aes.id, aess, FileType.json).toList()
        Assert.assertEquals(numberOfEntries - 1, loadedAssociations.size)

        dataApi.deleteEntities(aes.id, createdEdges.drop(1).toSet(), DeleteType.Soft)
        Assert.assertEquals(0, dataApi.loadSelectedEntitySetData(aes.id, aess, FileType.json).toList().size)
    }

    @Test
    fun testDeleteEntitiesWithAssociations() {
        val es = createEntitySet(personEt)

        val entries = (1..numberOfEntries)
                .map { mapOf(EdmTestConstants.personGivenNameId to setOf(RandomStringUtils.randomAscii(5))) }
        val newEntityIds = dataApi.createEntities(es.id, entries)

        // create edges with original entityset as source
        val dst = createEntityType()
        val edge = createEdgeEntityType()

        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        // create association type with defining src and dst entity types
        createAssociationType(edge, setOf(personEt), setOf(dst))

        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = testDataEdge.values.toList()
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edges = newEntityIds.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es.id, newEntityIds[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createEdges(edges)


        // hard delete 1st entity
        dataApi.deleteEntities(es.id, setOf(newEntityIds[0]), DeleteType.Hard)

        val ess1 = EntitySetSelection(Optional.of(personEt.properties))
        val loadedEntries1 = dataApi.loadSelectedEntitySetData(es.id, ess1, FileType.json).toList()

        val essDst1 = EntitySetSelection(Optional.of(dst.properties))
        val loadedEntriesDst1 = dataApi.loadSelectedEntitySetData(esDst.id, essDst1, FileType.json).toList()

        val essEdge1 = EntitySetSelection(Optional.of(edge.properties))
        val loadedEntriesEdge1 = dataApi.loadSelectedEntitySetData(esEdge.id, essEdge1, FileType.json).toList()

        Assert.assertEquals(numberOfEntries - 1, loadedEntries1.size)
        Assert.assertEquals(numberOfEntries - 1, loadedEntriesEdge1.size)
        Assert.assertEquals(numberOfEntries, loadedEntriesDst1.size)
        Assert.assertTrue(loadedEntries1.none {
            it[EdmTestConstants.personGivenNameFqn] == entries.first().values
        })
        Assert.assertTrue(loadedEntries1.none {
            it.getValue(EdmConstants.ID_FQN).first() == newEntityIds.first().toString()
        })
        Assert.assertTrue(loadedEntriesEdge1.none {
            it.getValue(EdmConstants.ID_FQN).first() == idsEdge.first().toString()
        })

        // soft delete last entity
        dataApi.deleteEntities(es.id, setOf(newEntityIds[numberOfEntries - 1]), DeleteType.Soft)

        val ess2 = EntitySetSelection(Optional.of(personEt.properties))
        val loadedEntries2 = dataApi.loadSelectedEntitySetData(es.id, ess2, FileType.json).toList()

        val essDst2 = EntitySetSelection(Optional.of(dst.properties))
        val loadedEntriesDst = dataApi.loadSelectedEntitySetData(esDst.id, essDst2, FileType.json).toList()

        val essEdge2 = EntitySetSelection(Optional.of(edge.properties))
        val loadedEntriesEdge2 = dataApi.loadSelectedEntitySetData(esEdge.id, essEdge2, FileType.json).toList()

        Assert.assertEquals(numberOfEntries - 2, loadedEntries2.size)
        Assert.assertEquals(numberOfEntries - 2, loadedEntriesEdge2.size)
        Assert.assertEquals(numberOfEntries, loadedEntriesDst.size)
        Assert.assertTrue(loadedEntries2.none {
            it[EdmTestConstants.personGivenNameFqn] == entries.last().values
        })
        Assert.assertTrue(loadedEntries2.none {
            it.getValue(EdmConstants.ID_FQN).last() == newEntityIds.last().toString()
        })
        Assert.assertTrue(loadedEntriesEdge2.none {
            it.getValue(EdmConstants.ID_FQN).last() == idsEdge.last().toString()
        })
    }

    @Test
    fun testDeleteAllDataInEntitySet() {
        // hard delete entityset data
        val es = createEntitySet(personEt)

        val entries = (1..numberOfEntries)
                .map { mapOf(EdmTestConstants.personGivenNameId to setOf(RandomStringUtils.randomAscii(5))) }
        val newEntityIds = dataApi.createEntities(es.id, entries)

        // create edges with original entityset as source
        val dst = createEntityType()
        val edge = createEdgeEntityType()

        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        // create association type with defining src and dst entity types
        createAssociationType(edge, setOf(personEt), setOf(dst))

        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = testDataEdge.values.toList()
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edges = newEntityIds.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es.id, newEntityIds[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createEdges(edges)

        dataApi.deleteAllEntitiesFromEntitySet(es.id, DeleteType.Hard)

        val ess1 = EntitySetSelection(Optional.of(personEt.properties))
        val loadedEntries1 = dataApi.loadSelectedEntitySetData(es.id, ess1, FileType.json).toList()

        val essDst1 = EntitySetSelection(Optional.of(dst.properties))
        val loadedEntriesDst1 = dataApi.loadSelectedEntitySetData(esDst.id, essDst1, FileType.json).toList()

        val essEdge1 = EntitySetSelection(Optional.of(edge.properties))
        val loadedEntriesEdge1 = dataApi.loadSelectedEntitySetData(esEdge.id, essEdge1, FileType.json).toList()

        Assert.assertEquals(0, loadedEntries1.size)
        Assert.assertEquals(0, loadedEntriesEdge1.size)
        Assert.assertEquals(numberOfEntries, loadedEntriesDst1.size)

        Thread.sleep(10000L) // it takes some time to delete documents from elasticsearch
        Assert.assertEquals(0L, searchApi
                .executeEntitySetDataQuery(es.id, SearchTerm("*", 0, 10)).numHits)
        Assert.assertEquals(0L, searchApi
                .executeEntitySetDataQuery(esEdge.id, SearchTerm("*", 0, 10)).numHits)


        // soft delete entityset data
        val newEntityIds2 = dataApi.createEntities(es.id, entries)

        val idsEdge2 = dataApi.createEntities(esEdge.id, entriesEdge)
        val edges2 = newEntityIds.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es.id, newEntityIds2[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge2[index])
            )
        }.toSet()
        dataApi.createEdges(edges2)

        dataApi.deleteAllEntitiesFromEntitySet(es.id, DeleteType.Soft)

        val ess2 = EntitySetSelection(Optional.of(personEt.properties))
        val loadedEntries2 = dataApi.loadSelectedEntitySetData(es.id, ess2, FileType.json).toList()

        val essDst2 = EntitySetSelection(Optional.of(dst.properties))
        val loadedEntriesDst = dataApi.loadSelectedEntitySetData(esDst.id, essDst2, FileType.json).toList()

        val essEdge2 = EntitySetSelection(Optional.of(edge.properties))
        val loadedEntriesEdge2 = dataApi.loadSelectedEntitySetData(esEdge.id, essEdge2, FileType.json).toList()

        Assert.assertEquals(0, loadedEntries2.size)
        Assert.assertEquals(0, loadedEntriesEdge2.size)
        Assert.assertEquals(numberOfEntries, loadedEntriesDst.size)

        Thread.sleep(5000L) // it takes some time to delete documents from elasticsearch
        Assert.assertEquals(0L, searchApi
                .executeEntitySetDataQuery(es.id, SearchTerm("*", 0, 10)).numHits)
        Assert.assertEquals(0L, searchApi
                .executeEntitySetDataQuery(esEdge.id, SearchTerm("*", 0, 10)).numHits)
    }

    @Test
    fun testDeleteEntityProperties() {
        val es = createEntitySet(personEt)

        val people = (1..numberOfEntries)
                .map {
                    mapOf(
                            EdmTestConstants.personGivenNameId to setOf(RandomStringUtils.randomAscii(5)),
                            EdmTestConstants.personMiddleNameId to setOf(RandomStringUtils.randomAscii(5))
                    )
                }

        val newEntityIds = dataApi.createEntities(es.id, people)

        val ess = EntitySetSelection(Optional.of(personEt.properties))
        Assert.assertEquals(numberOfEntries, dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList().size)

        val entityId = newEntityIds[0]
        dataApi.deleteEntityProperties(es.id, entityId, setOf(EdmTestConstants.personGivenNameId), DeleteType.Hard)

        val loadedEntity = dataApi.getEntity(es.id, entityId)

        Assert.assertEquals(numberOfEntries, dataApi.loadSelectedEntitySetData(es.id, ess, FileType.json).toList().size)
        Assert.assertFalse(loadedEntity.keys.contains(EdmTestConstants.personGivenNameFqn))
    }

    @Test
    fun testDeleteEntitiesAndNeighbors() {
        /* Delete from both neighbors */

        // create entity sets and data for es entity set being both src and dst
        val et1 = createEntityType()
        val src1 = createEntityType()
        val edgeSrc1 = createEdgeEntityType()
        val dst1 = createEntityType()
        val edgeDst1 = createEdgeEntityType()

        val es1 = createEntitySet(et1)
        val esDst1 = createEntitySet(dst1)
        val esEdgeDst1 = createEntitySet(edgeDst1)
        val esSrc1 = createEntitySet(src1)
        val esEdgeSrc1 = createEntitySet(edgeSrc1)

        // create association type with defining src and dst entity types
        createAssociationType(edgeSrc1, setOf(src1), setOf(et1))
        createAssociationType(edgeDst1, setOf(et1), setOf(dst1))

        val testData1 = TestDataFactory.randomStringEntityData(numberOfEntries, et1.properties).values.toList()
        val testDataDst1 = TestDataFactory.randomStringEntityData(numberOfEntries, dst1.properties).values.toList()
        val testDataEdgeDst1 = TestDataFactory.randomStringEntityData(numberOfEntries, edgeDst1.properties).values.toList()
        val testDataSrc1 = TestDataFactory.randomStringEntityData(numberOfEntries, src1.properties).values.toList()
        val testDataEdgeSrc1 = TestDataFactory.randomStringEntityData(numberOfEntries, edgeSrc1.properties).values.toList()

        val ids1 = dataApi.createEntities(es1.id, testData1)
        val idsDst1 = dataApi.createEntities(esDst1.id, testDataDst1)
        val idsEdgeDst1 = dataApi.createEntities(esEdgeDst1.id, testDataEdgeDst1)
        val idsSrc1 = dataApi.createEntities(esSrc1.id, testDataSrc1)
        val idsEdgeSrc1 = dataApi.createEntities(esEdgeSrc1.id, testDataEdgeSrc1)

        val edgesDst1 = ids1.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es1.id, ids1[index]),
                    EntityDataKey(esDst1.id, idsDst1[index]),
                    EntityDataKey(esEdgeDst1.id, idsEdgeDst1[index])
            )
        }.toSet()
        dataApi.createEdges(edgesDst1)
        val edgesSrc1 = ids1.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc1.id, idsSrc1[index]),
                    EntityDataKey(es1.id, ids1[index]),
                    EntityDataKey(esEdgeSrc1.id, idsEdgeSrc1[index])
            )
        }.toSet()
        dataApi.createEdges(edgesSrc1)

        // delete from all neighbors
        val deleteCount1 = dataApi.deleteEntitiesAndNeighbors(
                EntityNeighborsFilter(
                        mapOf(es1.id to ids1.toSet()),
                        Optional.of(setOf(esSrc1.id)),
                        Optional.of(setOf(esDst1.id)),
                        Optional.empty()),
                DeleteType.Hard)
        Assert.assertEquals(40L, deleteCount1)

        // test if there is really no data
        val ess1 = EntitySetSelection(Optional.of(et1.properties))
        val loadedEntities1 = dataApi.loadSelectedEntitySetData(es1.id, ess1, FileType.json).toList()
        Assert.assertEquals(0, loadedEntities1.size)

        val essSrc1 = EntitySetSelection(Optional.of(src1.properties))
        val loadedSrcEntities1 = dataApi.loadSelectedEntitySetData(esSrc1.id, essSrc1, FileType.json).toList()
        Assert.assertEquals(0, loadedSrcEntities1.size)

        val essEdgeSrc1 = EntitySetSelection(Optional.of(edgeSrc1.properties))
        val loadedEdgeSrcEntities1 = dataApi.loadSelectedEntitySetData(esEdgeSrc1.id, essEdgeSrc1, FileType.json).toList()
        Assert.assertEquals(0, loadedEdgeSrcEntities1.size)

        val essDst1 = EntitySetSelection(Optional.of(dst1.properties))
        val loadedDstEntities1 = dataApi.loadSelectedEntitySetData(esDst1.id, essDst1, FileType.json).toList()
        Assert.assertEquals(0, loadedDstEntities1.size)

        val essEdgeDst1 = EntitySetSelection(Optional.of(edgeDst1.properties))
        val loadedEdgeDstEntities1 = dataApi.loadSelectedEntitySetData(esEdgeDst1.id, essEdgeDst1, FileType.json).toList()
        Assert.assertEquals(0, loadedEdgeDstEntities1.size)


        /* Delete from only 1 neighbor */

        // create entity sets and data for es entity set being both src and dst
        val et2 = createEntityType()
        val src2 = createEntityType()
        val edgeSrc2 = createEdgeEntityType()
        val dst2 = createEntityType()
        val edgeDst2 = createEdgeEntityType()

        val es2 = createEntitySet(et2)
        val esDst2 = createEntitySet(dst2)
        val esEdgeDst2 = createEntitySet(edgeDst2)
        val esSrc2 = createEntitySet(src2)
        val esEdgeSrc2 = createEntitySet(edgeSrc2)

        // create association type with defining src and dst entity types
        createAssociationType(edgeSrc2, setOf(src2), setOf(et2))
        createAssociationType(edgeDst2, setOf(et2), setOf(dst2))

        val testData2 = TestDataFactory.randomStringEntityData(numberOfEntries, et2.properties).values.toList()
        val testDataDst2 = TestDataFactory.randomStringEntityData(numberOfEntries, dst2.properties).values.toList()
        val testDataEdgeDst2 = TestDataFactory.randomStringEntityData(numberOfEntries, edgeDst2.properties).values.toList()
        val testDataSrc2 = TestDataFactory.randomStringEntityData(numberOfEntries, src2.properties).values.toList()
        val testDataEdgeSrc2 = TestDataFactory.randomStringEntityData(numberOfEntries, edgeSrc2.properties).values.toList()

        val ids2 = dataApi.createEntities(es2.id, testData2)
        val idsDst2 = dataApi.createEntities(esDst2.id, testDataDst2)
        val idsEdgeDst2 = dataApi.createEntities(esEdgeDst2.id, testDataEdgeDst2)
        val idsSrc2 = dataApi.createEntities(esSrc2.id, testDataSrc2)
        val idsEdgeSrc2 = dataApi.createEntities(esEdgeSrc2.id, testDataEdgeSrc2)

        val edgesDst2 = ids2.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es2.id, ids2[index]),
                    EntityDataKey(esDst2.id, idsDst2[index]),
                    EntityDataKey(esEdgeDst2.id, idsEdgeDst2[index])
            )
        }.toSet()
        dataApi.createEdges(edgesDst2)
        val edgesSrc2 = ids2.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc2.id, idsSrc2[index]),
                    EntityDataKey(es2.id, ids2[index]),
                    EntityDataKey(esEdgeSrc2.id, idsEdgeSrc2[index])
            )
        }.toSet()
        dataApi.createEdges(edgesSrc2)

        // delete from only src neighbor
        val deleteCount2 = dataApi.deleteEntitiesAndNeighbors(
                EntityNeighborsFilter(
                        mapOf(es2.id to ids2.toSet()),
                        Optional.of(setOf(esSrc2.id)),
                        Optional.empty(),
                        Optional.empty()),
                DeleteType.Hard)
        Assert.assertEquals(30L, deleteCount2)

        // test if there is really no data, what is deleted and data which is not
        val ess2 = EntitySetSelection(Optional.of(et2.properties))
        val loadedEntities2 = dataApi.loadSelectedEntitySetData(es2.id, ess2, FileType.json).toList()
        Assert.assertEquals(0, loadedEntities2.size)

        val essSrc2 = EntitySetSelection(Optional.of(src2.properties))
        val loadedSrcEntities2 = dataApi.loadSelectedEntitySetData(esSrc2.id, essSrc2, FileType.json).toList()
        Assert.assertEquals(0, loadedSrcEntities2.size)

        val essEdgeSrc2 = EntitySetSelection(Optional.of(edgeSrc2.properties))
        val loadedEdgeSrcEntities2 = dataApi.loadSelectedEntitySetData(esEdgeSrc2.id, essEdgeSrc2, FileType.json).toList()
        Assert.assertEquals(0, loadedEdgeSrcEntities2.size)

        val essDst2 = EntitySetSelection(Optional.of(dst2.properties))
        val loadedDstEntities2 = dataApi.loadSelectedEntitySetData(esDst2.id, essDst2, FileType.json).toList()
        Assert.assertEquals(numberOfEntries, loadedDstEntities2.size)
        loadedDstEntities2.forEach {
            idsDst2.contains(UUID.fromString(it.getValue(EdmConstants.ID_FQN).first() as String))
        }

        val essEdgeDst2 = EntitySetSelection(Optional.of(edgeDst2.properties))
        val loadedEdgeDstEntities2 = dataApi.loadSelectedEntitySetData(esEdgeDst2.id, essEdgeDst2, FileType.json).toList()
        Assert.assertEquals(0, loadedEdgeDstEntities2.size)
    }

    @Test
    fun testGetEntitySetSize() {
        val et = createEntityType()
        val es = createEntitySet(et)

        val testData = TestDataFactory.randomStringEntityData(numberOfEntries, et.properties)

        val entries = testData.values.toList()
        dataApi.createEntities(es.id, entries)

        Assert.assertEquals(0L, dataApi.getEntitySetSize(es.id))

        Thread.sleep(300_000)

        Assert.assertEquals(numberOfEntries.toLong(), dataApi.getEntitySetSize(es.id))
    }
}
