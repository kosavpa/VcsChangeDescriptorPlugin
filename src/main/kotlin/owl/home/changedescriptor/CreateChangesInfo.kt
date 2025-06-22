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
import com.intellij.ui.components.JBCheckBox
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

        val fileNameToChangeMap = changes
            .filterNotNullVirtualFile()
            .associateBy { it.virtualFile!!.name }

        if (fileNameToChangeMap.isEmpty()) {
            logger.info("No files to commit")

            return
        }

        val dialog = ApproveCommitMessageTextDialog(fileNameToChangeMap.keys, project)

        if (dialog.showAndGet()) {
            val approvedFiles = dialog.getApprovedFileNames().mapNotNull { fileNameToChangeMap[it] }

            val branchName = GitBranchUtil.guessWidgetRepository(project, e.dataContext)
                ?.currentBranch
                ?.name
                .orEmpty()

            vcsPanel.commitMessage = buildCommitMessage(branchName, approvedFiles)

            logger.info("Commit message written")
        } else {
            logger.info("Dialog closed without confirmation")
        }
    }

    private fun Collection<Change>.filterNotNullVirtualFile(): List<Change> = filter { it.virtualFile != null }

    private fun buildCommitMessage(branchName: String, changes: List<Change>): String {
        val groupedChanges = changes.groupBy { it.type }

        return buildString {
            appendLine(branchName)
            groupedChanges.forEach { (type, files) ->
                appendLine("[${getProjectTypeByVcsType(type)}]:")
                files.forEach { file ->
                    appendLine("* ${file.virtualFile?.name}: /*Впишите сюда что именно изменилось*/")
                }
            }
        }
    }

    private fun getProjectTypeByVcsType(type: Change.Type): String {
        return when (type) {
            Change.Type.MOVED,
            Change.Type.MODIFICATION -> "edit"

            Change.Type.NEW -> "new"
            Change.Type.DELETED -> "delete"
            else -> "unknown"
        }
    }

    internal class ApproveCommitMessageTextDialog(private val commitFileNames: Set<String>, project: Project) :
        DialogWrapper(project) {
        private var checkBoxes: MutableList<Cell<JBCheckBox>> = mutableListOf()

        init {
            isResizable = true

            title = "Снимите галочки с файлов, имена которых не попадут в сообщение коммита"

            init()

            setOKButtonText("Ок")

            setCancelButtonText("Отмена")
        }

        fun getApprovedFileNames(): List<String> {
            return checkBoxes.filter { it.component.isSelected }.map { it.component.text }
        }

        override fun createCenterPanel(): DialogPanel = panel {
            commitFileNames.forEach {
                row {
                    val checkBox = checkBox(it)

                    checkBox.component.isSelected = true

                    checkBoxes.add(checkBox)
                }.resizableRow()
            }
        }
    }
}