package controllers

import javax.inject.Inject

import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter

class JavascriptRouter @Inject()(controllerComponents: KnolxControllerComponents) extends KnolxAbstractController(controllerComponents) {

  def jsRoutes: Action[AnyContent] = action { implicit request =>
    Ok(JavaScriptReverseRouter("jsRoutes")(
      controllers.routes.javascript.FeedbackFormsController.createFeedbackForm,
      controllers.routes.javascript.FeedbackFormsController.manageFeedbackForm,
      controllers.routes.javascript.FeedbackFormsController.updateFeedbackForm,
      controllers.routes.javascript.FeedbackFormsController.getFeedbackFormPreview,
      controllers.routes.javascript.UsersController.searchUser,
      controllers.routes.javascript.UsersController.getByEmail,
      controllers.routes.javascript.UsersController.deleteUser,
      controllers.routes.javascript.SessionsController.searchManageSession,
      controllers.routes.javascript.SessionsController.searchSessions,
      controllers.routes.javascript.SessionsController.shareContent,
      controllers.routes.javascript.SessionsController.update,
      controllers.routes.javascript.SessionsController.deleteSession,
      controllers.routes.javascript.FeedbackFormsResponseController.storeFeedbackFormResponse,
      controllers.routes.javascript.FeedbackFormsResponseController.fetchFeedbackFormResponse,
      controllers.routes.javascript.FeedbackFormsResponseController.getFeedbackFormsForToday,
      controllers.routes.javascript.FeedbackFormsReportController.searchAllResponsesBySessionId,
      controllers.routes.javascript.SessionsController.update,
      controllers.routes.javascript.SessionsController.deleteSession,
      controllers.routes.javascript.SessionsController.addPrimaryCategory,
      controllers.routes.javascript.SessionsController.addSubCategory,
      controllers.routes.javascript.SessionsController.modifyPrimaryCategory,
      controllers.routes.javascript.SessionsController.modifySubCategory,
      controllers.routes.javascript.SessionsController.getCategory,
      controllers.routes.javascript.SessionsController.deletePrimaryCategory,
      controllers.routes.javascript.SessionsController.getSubCategoryByPrimaryCategory,
      controllers.routes.javascript.SessionsController.deleteSubCategory,
      controllers.routes.javascript.SessionsController.getTopicsBySubCategory,
      controllers.routes.javascript.AnalysisController.pieChart
    )).as("text/javascript")
  }

}
