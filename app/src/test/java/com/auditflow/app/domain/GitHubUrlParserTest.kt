package com.auditflow.app.domain

import com.auditflow.app.domain.util.GitHubUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubUrlParserTest {

    @Test
    fun parseSlug_standardOwnerRepo_returnsCorrectOwnerAndRepo() {
        val parsed = GitHubUrlParser.parse("google/auditflow")
        assertEquals("google", parsed?.owner)
        assertEquals("auditflow", parsed?.repo)
        assertNull(parsed?.branch)
    }

    @Test
    fun parseHttpsUrl_returnsCorrectOwnerAndRepo() {
        val parsed = GitHubUrlParser.parse("https://github.com/torvalds/linux")
        assertEquals("torvalds", parsed?.owner)
        assertEquals("linux", parsed?.repo)
        assertNull(parsed?.branch)
    }

    @Test
    fun parseHttpsUrlWithDotGit_stripsDotGit() {
        val parsed = GitHubUrlParser.parse("https://github.com/torvalds/linux.git")
        assertEquals("torvalds", parsed?.owner)
        assertEquals("linux", parsed?.repo)
        assertNull(parsed?.branch)
    }

    @Test
    fun parseHttpsUrlWithTreeBranch_extractsBranch() {
        val parsed = GitHubUrlParser.parse("https://github.com/facebook/react/tree/main")
        assertEquals("facebook", parsed?.owner)
        assertEquals("react", parsed?.repo)
        assertEquals("main", parsed?.branch)
    }

    @Test
    fun parseInvalidUrl_returnsNull() {
        assertNull(GitHubUrlParser.parse("not-a-valid-url"))
        assertNull(GitHubUrlParser.parse("https://gitlab.com/owner/repo"))
        assertNull(GitHubUrlParser.parse("https://bitbucket.org/owner/repo"))
        assertNull(GitHubUrlParser.parse("https://github.com/"))
        assertNull(GitHubUrlParser.parse("https://github.com/onlyowner"))
        assertNull(GitHubUrlParser.parse(""))
        assertNull(GitHubUrlParser.parse(null))
    }

    @Test
    fun parseHttpsUrlWithQueryParamsAndFragments_parsesCorrectly() {
        val parsed1 = GitHubUrlParser.parse("https://github.com/owner/repo?tab=readme")
        assertEquals("owner", parsed1?.owner)
        assertEquals("repo", parsed1?.repo)

        val parsed2 = GitHubUrlParser.parse("https://github.com/owner/repo#readme")
        assertEquals("owner", parsed2?.owner)
        assertEquals("repo", parsed2?.repo)

        val parsed3 = GitHubUrlParser.parse("  https://github.com/owner/repo/  ")
        assertEquals("owner", parsed3?.owner)
        assertEquals("repo", parsed3?.repo)
    }

    @Test
    fun parseHttpsUrlWithMultiSegmentBranch_extractsFullBranchPath() {
        val parsed = GitHubUrlParser.parse("https://github.com/owner/repo/tree/feature/audit-flow")
        assertEquals("owner", parsed?.owner)
        assertEquals("repo", parsed?.repo)
        assertEquals("feature/audit-flow", parsed?.branch)
    }
}
