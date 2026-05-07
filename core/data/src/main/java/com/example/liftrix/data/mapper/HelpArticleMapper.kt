package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.HelpArticleEntity
import com.example.liftrix.domain.model.help.HelpArticle

/**
 * Mapper for converting between HelpArticle domain model and HelpArticleEntity
 */
object HelpArticleMapper {
    
    /**
     * Converts HelpArticleEntity to HelpArticle domain model
     */
    fun HelpArticleEntity.toDomainModel(): HelpArticle = HelpArticle(
        id = articleId,
        category = category,
        title = title,
        content = content,
        keywords = keywords?.split(",")?.map { it.trim() } ?: emptyList(),
        viewCount = viewCount,
        helpfulCount = helpfulCount,
        notHelpfulCount = notHelpfulCount,
        lastUpdated = lastUpdated,
        version = version,
        isFeatured = isFeatured,
        sortOrder = sortOrder
    )
    
    /**
     * Converts HelpArticle domain model to HelpArticleEntity
     */
    fun HelpArticle.toEntity(): HelpArticleEntity = HelpArticleEntity(
        articleId = id,
        category = category,
        title = title,
        content = content,
        keywords = keywords.joinToString(", "),
        viewCount = viewCount,
        helpfulCount = helpfulCount,
        notHelpfulCount = notHelpfulCount,
        lastUpdated = lastUpdated,
        version = version,
        isFeatured = isFeatured,
        sortOrder = sortOrder
    )
    
    /**
     * Converts list of HelpArticleEntity to list of HelpArticle domain models
     */
    fun List<HelpArticleEntity>.toDomainModels(): List<HelpArticle> = 
        map { it.toDomainModel() }
    
    /**
     * Converts list of HelpArticle domain models to list of HelpArticleEntity
     */
    fun List<HelpArticle>.toEntities(): List<HelpArticleEntity> = 
        map { it.toEntity() }
}