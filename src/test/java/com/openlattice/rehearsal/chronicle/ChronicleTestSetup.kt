package com.openlattice.rehearsal.chronicle

import com.openlattice.authorization.*
import com.openlattice.rehearsal.SetupTestData
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import org.junit.Test
import java.util.*

class ChronicleTestSetup : MultipleAuthenticatedUsersBase() {
    /**
     * This class contains functionality to set up a complete local version
     * of the necessary data sets for Chronicle, and should be run before
     * booting chronicle server.
     *
     * This contains an integration from flat files, that allow:
     * (1) running ChronicleServer
     * (2) running ChronicleServerTests
     *
     * If the entity sets already exist locally:
     * - the code below will not work (i.e. nobody will get permissions on existing data)
     * - the tests won't work
     *
     * To make the test work with existing entity sets, then permissions should be granted
     * to all users (the OpenLattice User Role), so that the test users can access
     * the necessary data.
     */

    companion object {
        private val ORGANIZATION_ID = UUID.fromString("00000000-0000-0001-0000-000000000000")
        private const val USERS_ROLE = "OpenLattice User Role"
        private const val CHRONICLE_NAME = "chronicle_"
    }

    @Test
    fun integrateTestData() {
        /**
         * Integrate test data through shuttle.
         */
        loginAs("admin")
        SetupTestData.importDataSet("test_chronicle_empty_flight.yaml", "test_chronicle_data.csv")
        SetupTestData.importDataSet("test_chronicle_dict_flight.yaml", "test_chronicle_dict_data.csv")
    }

    @Test
    fun setPermissions() {
        /**
         * Make sure users have access to the chronicle entity sets.
         */
        val rolePrincipal = organizationsApi
                .getRoles(ORGANIZATION_ID)
                .filter { it.title == USERS_ROLE }
                .first()
                .principal
        val ace = Ace(rolePrincipal, EnumSet.of(Permission.OWNER, Permission.READ, Permission.WRITE))

        entitySetsApi.getEntitySets()
                .filter { it.name.contains(CHRONICLE_NAME) }
                .forEach {
                    val entityType = edmApi.getEntityType(it.entityTypeId)
                    entityType.properties
                            .forEach { prop ->
                                val aclData = AclData(
                                        Acl(listOf(it.id, prop), listOf(ace)), Action.ADD)
                                permissionsApi.updateAcl(aclData)
                            }
                    val aclData = AclData(
                            Acl(listOf(it.id), listOf(ace)), Action.ADD)
                    permissionsApi.updateAcl(aclData)
                }


    }
}
