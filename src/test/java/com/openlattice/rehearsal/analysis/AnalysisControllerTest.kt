package com.openlattice.rehearsal.analysis

import com.google.common.collect.ImmutableList
import com.openlattice.analysis.requests.DateRangeFilter
import com.openlattice.analysis.requests.FilteredNeighborsRankingAggregation
import com.openlattice.analysis.requests.RankingAggregation
import com.openlattice.analysis.requests.ValueFilter
import com.openlattice.data.DataEdgeKey
import com.openlattice.data.EntityDataKey
import com.openlattice.data.requests.FileType
import com.openlattice.edm.type.PropertyType
import com.openlattice.mapstores.TestDataFactory
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate
import java.util.*
import kotlin.streams.toList


class AnalysisControllerTest : MultipleAuthenticatedUsersBase() {
    companion object {
        @JvmStatic
        @BeforeClass
        fun init() {
            loginAs("admin")
        }
    }

    @Test
    fun testSimpleTopUtilizers() {
        // test top utilizers without any aggregations or filters
        val numberOfEntries = 10

        val src = createEntityType()
        val dst = createEntityType()
        val edge = createEdgeEntityType()

        val esSrc = createEntitySet(src)
        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entriesSrc = ImmutableList.copyOf(testDataSrc.values)
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = ImmutableList.copyOf(testDataDst.values)
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = ImmutableList.copyOf(testDataEdge.values)
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edges = idsSrc.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc.id, idsSrc[index]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createAssociations(edges)

        val aggregation = RankingAggregation(listOf(
                FilteredNeighborsRankingAggregation(
                        edge.id, dst.id,
                        Optional.empty(), Optional.empty(),
                        mapOf(), mapOf(),
                        true, Optional.of(1.0))))
        val topUtilizers = analysisApi.getTopUtilizers(esSrc.id, 100, aggregation, FileType.json).toList()

        Assert.assertEquals(numberOfEntries, topUtilizers.size)
        Assert.assertEquals(1, topUtilizers.random()["assoc_0_count"])
        Assert.assertEquals(1, topUtilizers.random()["entity_0_count"])
        Assert.assertEquals(1.0, topUtilizers.random()["score"])
    }

    @Test
    fun testMultiNeighborTopUtilizers() {
        // test top utilizers without any aggregations or filters
        val numberOfEntries = 10

        val et = createEntityType()
        val src = createEntityType()
        val dst = createEntityType()
        val edge = createEdgeEntityType()

        val es = createEntitySet(et)
        val esSrc = createEntitySet(src)
        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        val testData = TestDataFactory.randomStringEntityData(numberOfEntries/2, et.properties)
        val testDataSrc = TestDataFactory.randomStringEntityData(numberOfEntries, src.properties)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = TestDataFactory.randomStringEntityData(numberOfEntries, edge.properties)

        val entries = ImmutableList.copyOf(testData.values)
        val ids = dataApi.createEntities(es.id, entries)

        val entriesSrc = ImmutableList.copyOf(testDataSrc.values)
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = ImmutableList.copyOf(testDataDst.values)
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = ImmutableList.copyOf(testDataEdge.values)
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edgesSrc = idsSrc.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(es.id, ids[index%5]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createAssociations(edgesSrc)

        val edgesDst = idsSrc.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc.id, idsSrc[index]),
                    EntityDataKey(es.id, ids[index%2]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createAssociations(edgesDst)


        val aggregation = RankingAggregation(listOf(
                FilteredNeighborsRankingAggregation(
                        edge.id, dst.id,
                        Optional.empty(), Optional.empty(),
                        mapOf(), mapOf(),
                        true, Optional.of(1.0)),
                FilteredNeighborsRankingAggregation(
                        edge.id, src.id,
                        Optional.empty(), Optional.empty(),
                        mapOf(), mapOf(),
                        false, Optional.of(2.0))))

        val topUtilizers = analysisApi.getTopUtilizers(es.id, 100, aggregation, FileType.json).toList()

        Assert.assertEquals(numberOfEntries, topUtilizers.size)
        Assert.assertEquals(2, topUtilizers.random()["assoc_0_count"])
        Assert.assertEquals(2, topUtilizers.random()["entity_0_count"])
        Assert.assertEquals(5, topUtilizers.random()["assoc_1_count"])
        Assert.assertEquals(5, topUtilizers.random()["entity_1_count"])
        Assert.assertEquals(12.0, topUtilizers.random()["score"])
    }

    @Test
    fun testFilteredTopUtilizers() {
        // test top utilizers without any aggregations or filters
        val numberOfEntries = 10

        val ptDate = createDatePropertyType()
        val ptString = createPropertyType()

        val src = createEntityType(ptDate.id)
        val dst = createEntityType()
        val edge = createEdgeEntityType(ptString.id)

        val esSrc = createEntitySet(src)
        val esDst = createEntitySet(dst)
        val esEdge = createEntitySet(edge)

        val start = LocalDate.parse("2000-01-01")
        val testDataSrc = createSinglePropertyTestData(
                start.datesUntil(start.plusDays((numberOfEntries/2).toLong())).toList(), ptDate)
        val testDataDst = TestDataFactory.randomStringEntityData(numberOfEntries, dst.properties)
        val testDataEdge = createSinglePropertyTestData("abcdefghij".chunked(1), ptString)

        val entriesSrc = ImmutableList.copyOf(testDataSrc.values)
        val idsSrc = dataApi.createEntities(esSrc.id, entriesSrc)

        val entriesDst = ImmutableList.copyOf(testDataDst.values)
        val idsDst = dataApi.createEntities(esDst.id, entriesDst)

        val entriesEdge = ImmutableList.copyOf(testDataEdge.values)
        val idsEdge = dataApi.createEntities(esEdge.id, entriesEdge)

        val edges = idsSrc.mapIndexed { index, _ ->
            DataEdgeKey(
                    EntityDataKey(esSrc.id, idsSrc[index%5]),
                    EntityDataKey(esDst.id, idsDst[index]),
                    EntityDataKey(esEdge.id, idsEdge[index])
            )
        }.toSet()
        dataApi.createAssociations(edges)


        val aggregation = RankingAggregation(listOf(
                FilteredNeighborsRankingAggregation(
                        edge.id, dst.id,
                        Optional.of(mapOf(ptString.id to setOf(ValueFilter(setOf("b", "c", "d"))))),
                        Optional.of(mapOf(ptString.id to setOf(
                                DateRangeFilter(Optional.of(start), Optional.of(true),
                                        Optional.of(start.plusDays(3)), Optional.of(false))))),
                        mapOf(), mapOf(),
                        true, Optional.of(1.0))))
        val topUtilizers = analysisApi.getTopUtilizers(esSrc.id, 100, aggregation, FileType.json).toList()

        Assert.assertEquals(2, topUtilizers.size)
        Assert.assertEquals(1, topUtilizers.random()["assoc_0_count"])
        Assert.assertEquals(1, topUtilizers.random()["entity_0_count"])
        Assert.assertEquals(1.0, topUtilizers.random()["score"])
    }

    private fun createSinglePropertyTestData(
            testData: List<Any>, propertyType: PropertyType
    ): Map<UUID, Map<UUID, Set<Any>>> {
        return testData
                .map { UUID.randomUUID() to mapOf(propertyType.id to setOf(it)) }
                .toMap()
    }
}