package owl.home.changedescriptor


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel


class CreateChangesInfo : AnAction() {
    private val logger = Logger.getInstance(CreateChangesInfo::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("start creating commit info")

        val project = e.project ?: return

        val vcsPanel = e.dataContext.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel ?: return

        val changeList = (ChangeListManager
            .getInstance(project)
            .getChangeList(project.projectFile ?: return)
            ?: return).changes ?: return

        logger.info("change list size:${changeList.size}")

        val fileNameToMap = changeList
            .filter { it.virtualFile?.name != null }
            .associateBy { it.virtualFile!!.name }

        logger.info("changed files:${fileNameToMap.keys}")

        val dialog = ApproveCommitMessageTextDialog(fileNameToMap.keys, project)

        if (dialog.showAndGet()) {
            vcsPanel.commitMessage = dialog.getApprovedFileNames()
                .asSequence()
                .map { fileNameToMap[it] }
                .groupBy { it?.type?.name }
                .map { entry ->
                    "[%s]:\n%s".format(
                        entry.key,
                        entry.value
                            .map { it?.virtualFile?.name }
                            .fold("\t* ") { acc, s -> acc.plus(s).plus("\n\t ") }
                    )
                }
                .reduce { acc, s -> acc.plus("\n").plus(s) }

            logger.info("commit message written")
        } else {
            logger.info("dialog closed without confirm")
        }
    }


    internal class ApproveCommitMessageTextDialog(private val commitFileNames: Set<String>, project: Project) :
        DialogWrapper(project) {
        private var checkBoxes: MutableList<Cell<JBCheckBox>> = mutableListOf()

        init {
            isResizable = true

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