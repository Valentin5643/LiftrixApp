package com.example.liftrix.data.local

import timber.log.Timber

/**
 * Validates that the migration chain is complete and covers all necessary version transitions.
 */
object MigrationValidator {
    
    /**
     * Validates that we have a complete migration path from supported versions to the current version.
     * 
     * @param currentVersion The target database version
     * @param availableMigrations List of available migration version pairs
     */
    fun validateMigrationChain(currentVersion: Int, availableMigrations: List<Pair<Int, Int>>) {
        val supportedStartVersions = listOf(6, 7, 8, 9, 10, 11, 12, 13, 14) // Versions we support migrating from
        val gaps = mutableListOf<String>()
        
        // Check that we have migrations for all supported versions
        for (startVersion in supportedStartVersions) {
            if (!hasPathToVersion(startVersion, currentVersion, availableMigrations)) {
                gaps.add("$startVersion→$currentVersion")
            }
        }
        
        if (gaps.isNotEmpty()) {
            Timber.w("Migration chain gaps detected: ${gaps.joinToString(", ")}")
            Timber.w("These versions will fall back to destructive migration")
        } else {
            Timber.i("Migration chain validation complete - all supported versions can migrate to $currentVersion")
        }
        
        // Log the complete migration chain
        Timber.d("Available migrations: ${availableMigrations.joinToString(", ") { "${it.first}→${it.second}" }}")
    }
    
    /**
     * Checks if there's a migration path from startVersion to targetVersion
     */
    private fun hasPathToVersion(startVersion: Int, targetVersion: Int, migrations: List<Pair<Int, Int>>): Boolean {
        if (startVersion == targetVersion) return true
        if (startVersion > targetVersion) return false
        
        // Find the next step in the migration chain
        val nextMigration = migrations.find { it.first == startVersion }
        return if (nextMigration != null) {
            hasPathToVersion(nextMigration.second, targetVersion, migrations)
        } else {
            false
        }
    }
}