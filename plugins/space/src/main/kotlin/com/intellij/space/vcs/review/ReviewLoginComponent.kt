package com.intellij.space.vcs.review

import com.intellij.openapi.project.Project
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.list.SpaceReviewsListVmImpl
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.Lifetime

internal class ReviewLoginComponent(lifetime: Lifetime,
                                    project: Project,
                                    spaceProjectInfo: SpaceProjectInfo,
                                    repoInfo: Set<SpaceRepoInfo>) {

  private val vm = ReviewVm(lifetime, project, spaceProjectInfo.key)

  val view = Wrapper().apply {
    background = UIUtil.getListBackground()
  }

  init {
    vm.isLoggedIn.forEach(lifetime) { isLoggedIn ->
      if (!isLoggedIn) {
        val loginLabel = LinkLabel.create(SpaceBundle.message("action.com.intellij.space.actions.SpaceLoginAction.text")) {
          SpaceSettingsPanel.openSettings(null)
        }
        view.setContent(loginLabel)
      }
      else {
        val circletReviewComponent = SpaceReviewComponent(project,
                                                          lifetime,
                                                          repoInfo,
                                                          space.workspace.value!!.client,
                                                          SpaceReviewsListVmImpl(lifetime,
                                                                                 space.workspace.value!!.client,
                                                                                 spaceProjectInfo.key,
                                                                                 space.workspace.value!!.me),
                                                          SpaceSelectedReviewVmImpl()
        )
        view.setContent(circletReviewComponent)
      }
      view.validate()
      view.repaint()
    }
  }
}
