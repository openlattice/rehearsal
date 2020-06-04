package com.openlattice.rehearsal.chronicle

import com.openlattice.chronicle.data.DeleteType
import com.openlattice.rehearsal.SetupTestData
import org.junit.Assert
import org.junit.Test

class ChronicleTestWeb : ChronicleTestBase() {

    @Test
    fun testHardDelete() {

        // integrate test data for multiple studies and participants per study
        ChronicleTestBase.deleteEntities()
        SetupTestData.importDataSet("test_chronicle_flight.yaml", "test_chronicle_data.csv")

        // count neighbors
        var counts_all = ChronicleTestBase.getParticipantCounts()
        chronicleStudyApi.deleteParticipantAndAllNeighbors(ChronicleTestBase.STUDY_ID, ChronicleTestBase.PARTICIPANT1, DeleteType.Soft)
        var counts_one_down = ChronicleTestBase.getParticipantCounts()
        counts_all.get(ChronicleTestBase.STUDY_ID)!!.remove((ChronicleTestBase.PARTICIPANT1))
        Assert.assertEquals(counts_all, counts_one_down)

        chronicleStudyApi.deleteStudyAndAllNeighbors(ChronicleTestBase.STUDY_ID, DeleteType.Soft)
        var counts_two_down = ChronicleTestBase.getParticipantCounts()
        counts_all.remove(ChronicleTestBase.STUDY_ID)
        Assert.assertEquals(counts_all, counts_two_down)


    }
}