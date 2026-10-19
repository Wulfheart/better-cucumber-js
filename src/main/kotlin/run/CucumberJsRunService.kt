package com.wulfindustries.plugins.run

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/** Owns the coroutine scope for per-run background work (e.g. tailing the messages file); cancelled with the project. */
@Service(Service.Level.PROJECT)
class CucumberJsRunService(val cs: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): CucumberJsRunService = project.service()
    }
}
