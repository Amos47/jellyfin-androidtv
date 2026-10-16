import org.gradle.api.Project

/**
 * Run git in the project directory and return the trimmed output, or null when git is unavailable or fails.
 */
private fun Project.gitOutput(vararg args: String): String? = runCatching {
	providers.exec {
		commandLine("git", "-C", rootDir.absolutePath, *args)
		isIgnoreExitValue = true
	}.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
}.getOrNull()

/**
 * Version name for personal fork builds based on the closest upstream release tag.
 *
 * Sample output:
 * v0.20.0-beta.4 (on tag)          -> 0.20.0-beta.4
 * v0.20.0-beta.4 + 6 commits       -> 0.20.0-beta.4-6-g930b668e4
 * git unavailable                  -> null
 */
fun Project.getGitVersionName(): String? =
	gitOutput("describe", "--tags", "--match", "v*")
		?.removePrefix("v")

/**
 * Version code for personal fork builds: the number of commits in the current history. The fork only merges (never
 * rewrites history) so this increases with every commit and upstream merge, allowing each build to update the
 * previously installed one. Requires a full (non-shallow) clone.
 */
fun Project.getGitVersionCode(): Int? =
	gitOutput("rev-list", "--count", "HEAD")
		?.toIntOrNull()
