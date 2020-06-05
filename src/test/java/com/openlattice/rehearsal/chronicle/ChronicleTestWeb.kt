package com.openlattice.rehearsal.chronicle

import com.openlattice.chronicle.data.DeleteType
import com.openlattice.rehearsal.SetupTestData
import org.apache.commons.lang3.tuple.Pair
import org.junit.Assert
import org.junit.Test
import java.time.OffsetDateTime

class ChronicleTestWeb : ChronicleTestBase() {

    @Test
    fun testHardDelete() {
        // integrate test data for multiple studies and participants per study
        SetupTestData.importDataSet("test_chronicle_flight.yaml", "test_chronicle_data.csv")

        // count neighbors
        var counts_all = ChronicleTestBase.getParticipantCounts()
        chronicleStudyApi!!.deleteParticipantAndAllNeighbors(STUDY_ID, PARTICIPANT1, DeleteType.Soft)
        var counts_one_down = ChronicleTestBase.getParticipantCounts()

        // the number of neighbors should be the same for all participants, except the one deleted
        counts_all.get(ChronicleTestBase.STUDY_ID)!!.remove((ChronicleTestBase.PARTICIPANT1))
        Assert.assertEquals(counts_all, counts_one_down)

        // the number of neighbors should be the same for all participants, except the ones in the deleted study
        chronicleStudyApi.deleteStudyAndAllNeighbors(ChronicleTestBase.STUDY_ID, DeleteType.Soft)
        var counts_two_down = ChronicleTestBase.getParticipantCounts()
        counts_all.remove(ChronicleTestBase.STUDY_ID)
        Assert.assertEquals(counts_all, counts_two_down)


    }
}