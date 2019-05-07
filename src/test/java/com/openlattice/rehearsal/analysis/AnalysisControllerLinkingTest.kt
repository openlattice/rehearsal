/*
 * Copyright (C) 2019. OpenLattice, Inc.
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
package com.openlattice.rehearsal.analysis

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.openlattice.data.DataEdge
import com.openlattice.mapstores.TestDataFactory
import com.openlattice.rehearsal.SetupTestData
import com.openlattice.rehearsal.edm.EdmTestConstants
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.util.*


class AnalysisControllerLinkingTest : SetupTestData() {
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

        val pt = createPropertyType()
        val et = createEntityType(pt.id)
        val linkingEs = createEntitySet(et, true, setOf(personEs.id))

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
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)
        val entriesDst = testDataDst.values.toList()
        val idsDst = dataApi.createEntities(personEs.id, entriesDst)

        val edgesToBeCreated: ListMultimap<UUID, DataEdge> = ArrayListMultimap.create()
        val edgeData = createDataEdges(esEdge.id, esSrc.id, personEs.id, edge.properties, idsSrc, idsDst, numberOfEntries)
        edgesToBeCreated.putAll(edgeData.first, edgeData.second)
        dataApi.createAssociations(edgesToBeCreated)

        val neighborTypes = analysisApi.getNeighborTypes(linkingEs.id)

        Assert.assertEquals(1, neighborTypes.count())
        neighborTypes.forEach {
            Assert.assertEquals(edge, it.associationEntityType)
            Assert.assertEquals(src, it.neighborEntityType)
        }
    }
}