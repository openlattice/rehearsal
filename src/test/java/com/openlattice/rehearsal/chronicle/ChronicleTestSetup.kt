package com.openlattice.rehearsal.chronicle

import com.openlattice.authorization.*
import com.openlattice.rehearsal.SetupTestData
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import org.junit.Test
import java.util.*

class ChronicleTestSetup : MultipleAuthenticatedUsersBase() {
    /**
     * This class contains functionality to set up a complete local version
     * of the necessary data sets for Chronicle.
     *
     * This contains an integration from flat files, that allow:
     * (1) running ChronicleServer
     * (2) running ChronicleServerTests
     *
     * If the entity sets already exist locally, then permission should be granted
     * to the OpenLattice User Role to the entitysets (as in setPermissions)
     * to allow these tests to access the entity sets.
     */

    companion object {
        private val ORGANIZATION_ID = UUID.fromString("00000000-0000-0001-0000-000000000000")
        private val USERS_ROLE = "OpenLattice User Role"
        private val CHRONICLE_NAME = "chronicle_"
    }

    @Test
    fun integrateTestData() {
        /**
         * Integrate test data through shuttle.
         */
        loginAs("admin")
        SetupTestData.importDataSet("test_chronicle_empty_flight.yaml","test_chronicle_data.csv")
        SetupTestData.importDataSet("test_chronicle_dict_flight.yaml", "test_chronicle_dict_data.csv")
    }

    @Test
    fun setPermissions() {
        /**
         * Make sure users have access to the chronicle entity sets.
         */
        val rolePrincipal = organizationsApi
                .getRoles(ORGANIZATION_ID)
                .filter {x -> x.title.equals(USERS_ROLE)}
                .first()
                .principal
        val ace = Ace(rolePrincipal, EnumSet.of(Permission.OWNER, Permission.READ, Permission.WRITE))

        entitySetsApi.getEntitySets()
                .filter {x -> x.name.contains(CHRONICLE_NAME)}
                .forEach{ x ->
                    val entityType = edmApi.getEntityType(x.entityTypeId)
                    entityType.properties
                            .forEach { k ->
                                val aclData = AclData(
                                        Acl(listOf(x.id, k), listOf(ace)), Action.ADD);
                                permissionsApi.updateAcl(aclData)
                            }
                    val aclData = AclData(
                            Acl(listOf(x.id), listOf(ace)), Action.ADD);
                    permissionsApi.updateAcl(aclData)
                }


    }
}
