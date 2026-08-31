package com.auditflow.app.domain.util

/**
 * Validated reference to a remote GitHub repository.
 */
data class GitHubRepoRef(
    val owner: String,
    val repo: String,
    val branch: String? = null
) {
    val slug: String get() = "$owner/$repo"
    val webUrl: String get() = "https://github.com/$owner/$repo"
}

/**
 * Deterministic parser for GitHub repository URLs and slugs.
 * Validates and extracts owner, repo, and optional branch without network access.
 */
object GitHubUrlParser {

    private val SLUG_REGEX = Regex("^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$")

    fun parse(input: String?): GitHubRepoRef? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        // Match slug: owner/repo
        if (SLUG_REGEX.matches(trimmed)) {
            val parts = trimmed.split("/")
            val owner = parts[0].trim()
            val repo = parts[1].trim().removeSuffix(".git")
            return if (owner.isNotBlank() && repo.isNotBlank()) {
                GitHubRepoRef(owner = owner, repo = repo)
            } else null
        }

        // Match URL formats: https://github.com/owner/repo or github.com/owner/repo
        val normalized = trimmed
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .trim('/')

        val segments = normalized.split("/").filter { it.isNotBlank() }
        if (segments.size < 2) return null

        val owner = segments[0]
        val rawRepo = segments[1].removeSuffix(".git")
        if (owner.isBlank() || rawRepo.isBlank()) return null

        // Check if a branch/tag is specified in the URL (e.g. /tree/main or /tree/v1.0.0)
        var branch: String? = null
        if (segments.size >= 4 && segments[2] == "tree") {
            branch = segments.subList(3, segments.size).joinToString("/")
        }

        return GitHubRepoRef(
            owner = owner,
            repo = rawRepo,
            branch = branch
        )
    }
}
