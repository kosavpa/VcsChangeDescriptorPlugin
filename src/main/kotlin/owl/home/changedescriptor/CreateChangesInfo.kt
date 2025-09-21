package owl.home.changedescriptor


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import git4idea.branch.GitBranchUtil


class CreateChangesInfo : AnAction() {
    private val logger = Logger.getInstance(CreateChangesInfo::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("Start creating commit info")

        val project = e.project ?: run {
            logger.warn("No project found")

            return
        }

        val vcsPanel = e.dataContext.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel ?: run {
            logger.warn("No check-in panel found")

            return
        }

        val changes = ChangeListManager.getInstance(project).allChanges

        logger.info("Change list size: ${changes.size}")

        val changesFilesCount = changes
            .filterNotNullVirtualFile()
            .count()

        if (changesFilesCount == 0) {
            logger.info("No files to commit")

            return
        }

        val dialog = ApproveCommitMessageTextDialog(project)

        if (dialog.showAndGet()) {
            val branchName = GitBranchUtil.guessWidgetRepository(project, e.dataContext)
                ?.currentBranch
                ?.name
                .orEmpty()

            vcsPanel.commitMessage = buildCommitMessage(
                branchName,
                dialog.getApprovedTrailer(),
                changes.filterNotNullVirtualFile()
            )

            logger.info("Commit message written")
        } else {
            logger.info("Dialog closed without confirmation")
        }
    }

    private fun Collection<Change>.filterNotNullVirtualFile(): List<Change> =
        filter { it.virtualFile != null }

    private fun buildCommitMessage(
        branchName: String,
        trailer: String,
        changes: List<Change>
    ): String {
        return buildString {
            appendLine("$branchName: /*Впишите сюда сообщение коммита*/")
            appendLine("\nDetails:")

            changes.forEach {
                appendLine("${it.virtualFile?.name}: ${it.type.name}")
            }

            appendLine("\nChangelog: $trailer")
        }
    }

    internal class ApproveCommitMessageTextDialog(project: Project) : DialogWrapper(project) {
        private var radios: MutableList<Cell<JBRadioButton>> = mutableListOf()

        init {
            isResizable = true

            title = "Выберите тип gitlab trailer"

            init()

            setOKButtonText("Ок")

            setCancelButtonText("Отмена")
        }

        fun getApprovedTrailer(): String {
            return radios.first { it.component.isSelected }.component.text
        }

        override fun createCenterPanel(): DialogPanel = panel {
            val trailers = mutableSetOf(
                "added",
                "fixed",
                "changed",
                "deprecated",
                "removed",
                "security",
                "performance",
                "other"
            )

            buttonsGroup(
                "Trailers",
                true
            ) {
                trailers.forEach {
                    row { radios.add(radioButton(it)) }.resizableRow()
                }
            }
        }
    }
}