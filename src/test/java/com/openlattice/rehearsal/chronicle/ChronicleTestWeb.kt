package com.openlattice.rehearsal.chronicle

import com.openlattice.chronicle.data.DeleteType
import com.openlattice.rehearsal.SetupTestData
import org.junit.Assert
import org.junit.Test

class ChronicleTestWeb : ChronicleTestBase() {

    @Test
    fun testHardDelete() {
        // integrate test data for multiple studies and participants per study
        SetupTestData.importDataSet("test_chronicle_flight.yaml", "test_chronicle_data.csv")

        // count neighbors
        val countsAll = ChronicleTestBase.getParticipantCounts()
        chronicleStudyApi.deleteParticipantAndAllNeighbors(STUDY_ID2, PARTICIPANT1, DeleteType.Soft)
        val countsOneDown = ChronicleTestBase.getParticipantCounts()

        // the number of neighbors should be the same for all participants, except the one deleted
        countsAll.getValue(ChronicleTestBase.STUDY_ID2).remove((ChronicleTestBase.PARTICIPANT1))
        Assert.assertEquals(countsAll, countsOneDown)

        // the number of neighbors should be the same for all participants, except the ones in the deleted study
        chronicleStudyApi.deleteStudyAndAllNeighbors(ChronicleTestBase.STUDY_ID2, DeleteType.Soft)
        val countsTwoDown = ChronicleTestBase.getParticipantCounts()
        countsAll.remove(ChronicleTestBase.STUDY_ID2)
        Assert.assertEquals(countsAll, countsTwoDown)


    }
}