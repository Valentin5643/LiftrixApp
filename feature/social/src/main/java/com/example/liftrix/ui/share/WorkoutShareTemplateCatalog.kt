package com.example.liftrix.ui.share

object WorkoutShareTemplateCatalog {
    private const val AssetRoot = "workout_share_backgrounds"

    val templates: List<WorkoutShareTemplate> = listOf(
        WorkoutShareTemplate(
            id = "dark-cinematic-gym",
            displayName = "Cinematic",
            assetPath = "$AssetRoot/dark_cinematic_gym.webp"
        ),
        WorkoutShareTemplate(
            id = "futuristic-fitness-tech",
            displayName = "Tech",
            assetPath = "$AssetRoot/futuristic_fitness_tech.webp"
        ),
        WorkoutShareTemplate(
            id = "high-energy-training",
            displayName = "Energy",
            assetPath = "$AssetRoot/high_energy_training_atmosphere.webp"
        ),
        WorkoutShareTemplate(
            id = "luxury-performance",
            displayName = "Performance",
            assetPath = "$AssetRoot/luxury_performance_branding.webp"
        ),
        WorkoutShareTemplate(
            id = "minimal-clean-fitness",
            displayName = "Minimal",
            assetPath = "$AssetRoot/minimal_clean_fitness_gradient.webp"
        ),
        WorkoutShareTemplate(
            id = "motivational-athlete",
            displayName = "Athlete",
            assetPath = "$AssetRoot/motivational_athlete_lifestyle.webp"
        )
    )

    val defaultTemplate: WorkoutShareTemplate = templates.first()

    fun resolve(templateId: String?): WorkoutShareTemplate {
        return templates.firstOrNull { it.id == templateId } ?: defaultTemplate
    }
}
