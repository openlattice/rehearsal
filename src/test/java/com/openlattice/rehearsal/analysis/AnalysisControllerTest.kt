package com.openlattice.rehearsal.analysis

import com.openlattice.mapstores.TestDataFactory
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import com.openlattice.rehearsal.edm.EdmTestConstants
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test


class AnalysisControllerTest : MultipleAuthenticatedUsersBase() {
    companion object {
        @JvmStatic
        @BeforeClass
        fun init() {
            loginAs("admin")
        }
    }

    @Test
    fun testGetNeighborTypesOnLinkingEntitySet() {
        val numberOfEntries = 10

        // Create linking entityset and add person entityset to it
        val personEntityTypeId = EdmTestConstants.personEt.id
        val personEt = edmApi.getEntityType(personEntityTypeId)
        val personEs = createEntitySet(personEt)

        val linkingEs = createEntitySet(EdmTestConstants.personEt, true, setOf(personEs.id))

        // Create edge and src entitysets (linked entity set is dst)
        val edge = createEdgeEntityType()
        val esEdge = createEntitySet(edge)
        val src = createEntityType()
        val esSrc = createEntitySet(src)
        createAssociationType(edge, setOf(src), setOf(personEt))

        // Create entries for src, dst
        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(
                numberOfEntries,
                personEt.properties
                        .map { edmApi.getPropertyType(it) }
                        .filter { it.datatype == EdmPrimitiveTypeKind.String }
                        .map { it.id }
                        .toSet())


        val entriesSrc = testDataSrc.values.toList()
        val entriesDst = testDataDst.values.toList()
        val ids = dataApi.createEntities(mapOf(esSrc.id to entriesSrc, personEs.id to entriesDst))
        val idsSrc = ids.getValue(esSrc.id)
        val idsDst = ids.getValue(personEs.id)

        val edgeData = createDataEdges(esEdge.id, esSrc.id, personEs.id, edge.properties, idsSrc, idsDst, numberOfEntries)
        dataApi.createAssociations(edgeData)

        val neighborTypes = analysisApi.getNeighborTypes(linkingEs.id)

        Assert.assertEquals(1, neighborTypes.count())
        neighborTypes.forEach {
            Assert.assertEquals(edge, it.associationEntityType)
            Assert.assertEquals(src, it.neighborEntityType)
        }
    }
}