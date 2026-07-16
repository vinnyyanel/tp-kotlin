package com.esgis.chatapp.data

/**
 * Personnages IA : chaque persona = un system prompt différent.
 * (P0 fonctionnalité 5 du cahier des charges.)
 */
enum class AiPersona(
    val id: String,
    val label: String,
    val systemPrompt: String
) {
    GENERAL(
        id = "general",
        label = "Généraliste",
        systemPrompt = "Tu es un assistant généraliste amical et concis."
    ),
    JURIDIQUE(
        id = "juridique",
        label = "Juridique",
        systemPrompt = "Tu es un assistant juridique spécialisé en droit. " +
            "Tu rappelles que tes réponses ne remplacent pas un avocat."
    ),
    TECHNIQUE(
        id = "technique",
        label = "Technique",
        systemPrompt = "Tu es un assistant technique expert en développement."
    );

    companion object {
        fun fromId(id: String?): AiPersona =
            entries.firstOrNull { it.id == id } ?: GENERAL
    }
}
