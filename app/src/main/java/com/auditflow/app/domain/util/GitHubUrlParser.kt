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

    private val IDENTIFIER_REGEX = Regex("^[a-zA-Z0-9_.-]+$")
    private val SLUG_REGEX = Regex("^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$")

    fun parse(input: String?): GitHubRepoRef? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        val cleanInput = trimmed.substringBefore('?').substringBefore('#').trim()

        // Match slug: owner/repo
        if (SLUG_REGEX.matches(cleanInput)) {
            val parts = cleanInput.split("/")
            val owner = parts[0].trim()
            val repo = parts[1].trim().removeSuffix(".git")
            return if (owner.isNotBlank() && repo.isNotBlank() &&
                IDENTIFIER_REGEX.matches(owner) && IDENTIFIER_REGEX.matches(repo)
            ) {
                GitHubRepoRef(owner = owner, repo = repo)
            } else null
        }

        // Match URL formats: must be hosted on github.com
        var withoutScheme = cleanInput
        if (withoutScheme.startsWith("https://", ignoreCase = true)) {
            withoutScheme = withoutScheme.substring(8)
        } else if (withoutScheme.startsWith("http://", ignoreCase = true)) {
            withoutScheme = withoutScheme.substring(7)
        }
        if (withoutScheme.startsWith("www.", ignoreCase = true)) {
            withoutScheme = withoutScheme.substring(4)
        }

        // Must explicitly start with github.com/
        if (!withoutScheme.startsWith("github.com/", ignoreCase = true)) {
            return null
        }

        val path = withoutScheme.substring("github.com/".length).trim('/')
        val segments = path.split("/").filter { it.isNotBlank() }
        if (segments.size < 2) return null

        val owner = segments[0].trim()
        val rawRepo = segments[1].trim().removeSuffix(".git")
        if (owner.isBlank() || rawRepo.isBlank()) return null
        if (!IDENTIFIER_REGEX.matches(owner) || !IDENTIFIER_REGEX.matches(rawRepo)) return null

        // Check if a branch/tag is specified in the URL (e.g. /tree/main or /tree/v1.0.0)
        var branch: String? = null
        if (segments.size >= 4 && segments[2] == "tree") {
            branch = segments.subList(3, segments.size).joinToString("/")
        } else if (segments.size > 2) {
            // Unrecognized path structure beyond owner/repo (e.g. issues, pulls without tree)
            return null
        }

        return GitHubRepoRef(
            owner = owner,
            repo = rawRepo,
            branch = branch
        )
    }
}
