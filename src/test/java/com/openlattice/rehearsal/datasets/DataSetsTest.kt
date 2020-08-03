package com.openlattice.rehearsal.datasets

import com.openlattice.organization.OrganizationIntegrationAccount
import com.openlattice.organizations.Organization
import com.openlattice.rehearsal.authentication.MultipleAuthenticatedUsersBase
import com.openlattice.rehearsal.organization.TestAssemblerConnectionManager
import junit.framework.Assert
import org.junit.BeforeClass
import org.junit.Test
import java.util.*


open class DataSetsTest : MultipleAuthenticatedUsersBase() {
    companion object {
        lateinit var credentials: OrganizationIntegrationAccount
        lateinit var organization: Organization
        lateinit var organizationID: UUID
        lateinit var url: String
        lateinit var connectionProperties: Properties

        const val create_query = "CREATE TABLE IF NOT EXISTS " +
                "authors (id serial PRIMARY KEY, name VARCHAR(25));"
        const val insert_query = "INSERT INTO " +
                "authors(id, name) " +
                "VALUES(1, 'Jack London');"
        val delete_column_query = "ALTER TABLE authors " +
                "DROP COLUMN name;"
        val delete_table_query = "DROP TABLE authors;"
        val tableName = "authors"
        val columnName = "name"


        @JvmStatic
        @BeforeClass
        fun init() {
            loginAs("admin")

            organization = createOrganization()
            organizationID = organization.id
            credentials = organizationsApi.getOrganizationIntegrationAccount(organizationID)
            connectionProperties["jdbcUrl"] = "jdbc:postgresql://localhost:5432"
            connectionProperties["username"] = credentials.user
            connectionProperties["password"] = credentials.credential
            connectionProperties["maximumPoolSize"] = 5
            connectionProperties["connectionTimeout"] = 60000
        }

    }

    fun uploadData() {
        val id = 1
        val author = "Jack London"

        val organizationDataSource = TestAssemblerConnectionManager.connect(organizationID, Optional.of(connectionProperties))
        organizationDataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery(create_query)
                stmt.executeQuery(insert_query)
            }
        }
    }

    fun removeDataColumn() {
        val organizationDataSource = TestAssemblerConnectionManager.connect(organizationID, Optional.of(connectionProperties))
        organizationDataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery(delete_column_query)
            }
        }
    }

    fun removeDataTable() {
        val organizationDataSource = TestAssemblerConnectionManager.connect(organizationID, Optional.of(connectionProperties))
        organizationDataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeQuery(delete_table_query)
            }
        }
    }

    @Test
    fun getMetadata() {
        uploadData()
        Thread.sleep(30000L)

        // check if table is picked up in BackgroundExternalDatabaseSyncingService.kt
        var externalOne = datasetApi.getExternalDatabaseTablesWithColumnMetadata(organizationID)
        Assert.assertTrue(externalOne.any { it.table.name == tableName })
        logger.info("Yay ! Adding a dataset was a success !")
        val theTable = externalOne.filter { it.table.name == tableName }.first()


        // check if column removal is picked up in BackgroundExternalDatabaseSyncingService.kt
        removeDataColumn()
        Thread.sleep(30000L)
        var externalTwo = datasetApi.getExternalDatabaseTableWithColumnMetadata(organizationID, theTable.table.id)
        var cols = externalTwo.columns
        Assert.assertFalse(externalTwo.columns.any { it.name == columnName })
        logger.info("Omg ! Removing a column was ALSO a success !")


        // check if table deletion is picked up in BackgroundExternalDatabaseSyncingService.kt
        removeDataTable()
        Thread.sleep(30000L)
        var externalThree = datasetApi.getExternalDatabaseTablesWithColumnMetadata(organizationID)
        Assert.assertFalse(externalThree.any { it.table.name == tableName })
        logger.info("No way ! All tables were updated as we want them to !")

    }

}