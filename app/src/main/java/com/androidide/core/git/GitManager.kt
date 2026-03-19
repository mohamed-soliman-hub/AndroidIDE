package com.androidide.core.git

import com.androidide.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitManager @Inject constructor() {

    private val _status    = MutableStateFlow<GitStatus?>(null)
    val status: StateFlow<GitStatus?> = _status

    private val _commits   = MutableStateFlow<List<GitCommit>>(emptyList())
    val commits: StateFlow<List<GitCommit>> = _commits

    private val _branches  = MutableStateFlow<List<String>>(emptyList())
    val branches: StateFlow<List<String>> = _branches

    private val _log       = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private var git: Git? = null

    suspend fun openRepository(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val repo = FileRepositoryBuilder()
                .setGitDir(File(path, ".git"))
                .readEnvironment()
                .findGitDir()
                .build()
            git = Git(repo)
            refreshStatus()
            refreshBranches()
            refreshCommits()
            true
        } catch (e: Exception) {
            addLog("Failed to open repository: ${e.message}")
            false
        }
    }

    suspend fun initRepository(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            git = Git.init().setDirectory(File(path)).call()
            addLog("Initialized empty Git repository in $path/.git/")
            // Create .gitignore
            File(path, ".gitignore").writeText("""
*.iml
.gradle
/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
            """.trimIndent())
            refreshStatus()
            true
        } catch (e: Exception) {
            addLog("Init failed: ${e.message}")
            false
        }
    }

    suspend fun stageAll() = withContext(Dispatchers.IO) {
        try {
            git?.add()?.addFilepattern(".")?.call()
            addLog("Staged all changes")
            refreshStatus()
        } catch (e: GitAPIException) { addLog("Stage failed: ${e.message}") }
    }

    suspend fun stageFile(filePath: String) = withContext(Dispatchers.IO) {
        try {
            git?.add()?.addFilepattern(filePath)?.call()
            addLog("Staged: $filePath")
            refreshStatus()
        } catch (e: GitAPIException) { addLog("Stage failed: ${e.message}") }
    }

    suspend fun unstageFile(filePath: String) = withContext(Dispatchers.IO) {
        try {
            git?.reset()?.addPath(filePath)?.call()
            addLog("Unstaged: $filePath")
            refreshStatus()
        } catch (e: GitAPIException) { addLog("Unstage failed: ${e.message}") }
    }

    suspend fun commit(message: String, authorName: String = "User", email: String = "user@ide.local") = withContext(Dispatchers.IO) {
        try {
            val commit = git?.commit()
                ?.setMessage(message)
                ?.setAuthor(authorName, email)
                ?.call()
            addLog("Committed: ${commit?.name?.take(7)} - $message")
            refreshStatus()
            refreshCommits()
        } catch (e: GitAPIException) { addLog("Commit failed: ${e.message}") }
    }

    suspend fun push(remote: String = "origin", branch: String = "main",
                     username: String = "", password: String = "") = withContext(Dispatchers.IO) {
        try {
            addLog("Pushing to $remote/$branch...")
            val cmd = git?.push()?.setRemote(remote)?.add(branch)
            if (username.isNotEmpty()) {
                cmd?.setCredentialsProvider(UsernamePasswordCredentialsProvider(username, password))
            }
            val results = cmd?.call()
            results?.forEach { result ->
                result.remoteUpdates.forEach { update -> addLog("  ${update.status}: ${update.remoteName}") }
            }
            addLog("Push complete")
            refreshStatus()
        } catch (e: GitAPIException) { addLog("Push failed: ${e.message}") }
    }

    suspend fun pull(remote: String = "origin", branch: String = "main") = withContext(Dispatchers.IO) {
        try {
            addLog("Pulling from $remote/$branch...")
            val result = git?.pull()?.setRemote(remote)?.setRemoteBranchName(branch)?.call()
            if (result?.isSuccessful == true) addLog("Pull successful")
            else addLog("Pull result: ${result?.mergeResult?.mergeStatus}")
            refreshStatus()
            refreshCommits()
        } catch (e: GitAPIException) { addLog("Pull failed: ${e.message}") }
    }

    suspend fun createBranch(name: String, checkout: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            git?.branchCreate()?.setName(name)?.call()
            if (checkout) git?.checkout()?.setName(name)?.call()
            addLog("Created branch: $name")
            refreshBranches()
            refreshStatus()
        } catch (e: GitAPIException) { addLog("Branch creation failed: ${e.message}") }
    }

    suspend fun checkoutBranch(name: String) = withContext(Dispatchers.IO) {
        try {
            git?.checkout()?.setName(name)?.call()
            addLog("Checked out: $name")
            refreshStatus()
            refreshCommits()
        } catch (e: GitAPIException) { addLog("Checkout failed: ${e.message}") }
    }

    suspend fun deleteBranch(name: String) = withContext(Dispatchers.IO) {
        try {
            git?.branchDelete()?.setBranchNames(name)?.call()
            addLog("Deleted branch: $name")
            refreshBranches()
        } catch (e: GitAPIException) { addLog("Delete branch failed: ${e.message}") }
    }

    suspend fun discardChanges(filePath: String) = withContext(Dispatchers.IO) {
        try {
            git?.checkout()?.addPath(filePath)?.call()
            addLog("Discarded changes: $filePath")
            refreshStatus()
        } catch (e: GitAPIException) { addLog("Discard failed: ${e.message}") }
    }

    suspend fun getFileDiff(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            val outputStream = java.io.ByteArrayOutputStream()
            git?.diff()
                ?.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath))
                ?.setOutputStream(outputStream)
                ?.call()
            outputStream.toString()
        } catch (e: Exception) { "Failed to get diff: ${e.message}" }
    }

    private suspend fun refreshStatus() = withContext(Dispatchers.IO) {
        try {
            val repo = git?.repository ?: return@withContext
            val jgitStatus = git?.status()?.call()
            val branch = repo.branch ?: "HEAD"
            val tracking = BranchTrackingStatus.of(repo, branch)
            _status.value = GitStatus(
                branch   = branch,
                staged   = ((jgitStatus?.added ?: emptySet()) + (jgitStatus?.changed ?: emptySet())).toList(),
                modified = (jgitStatus?.modified ?: emptySet()).toList(),
                untracked= (jgitStatus?.untracked ?: emptySet()).toList(),
                deleted  = (jgitStatus?.removed ?: emptySet()).toList(),
                ahead    = tracking?.aheadCount ?: 0,
                behind   = tracking?.behindCount ?: 0
            )
        } catch (e: Exception) { addLog("Status error: ${e.message}") }
    }

    private suspend fun refreshBranches() = withContext(Dispatchers.IO) {
        try {
            val branchList = git?.branchList()?.call()?.map {
                it.name.removePrefix("refs/heads/")
            } ?: emptyList()
            _branches.value = branchList
        } catch (e: Exception) { addLog("Branch list error: ${e.message}") }
    }

    private suspend fun refreshCommits(limit: Int = 50) = withContext(Dispatchers.IO) {
        try {
            val logCmd = git?.log()?.setMaxCount(limit)?.call() ?: return@withContext
            _commits.value = logCmd.map { commit ->
                GitCommit(
                    hash      = commit.name,
                    shortHash = commit.name.take(7),
                    message   = commit.shortMessage,
                    author    = commit.authorIdent.name,
                    timestamp = commit.authorIdent.`when`.time
                )
            }
        } catch (e: Exception) { addLog("Log error: ${e.message}") }
    }

    private suspend fun addLog(msg: String) {
        _log.value = _log.value + msg
    }

    fun close() { git?.close() }
}
