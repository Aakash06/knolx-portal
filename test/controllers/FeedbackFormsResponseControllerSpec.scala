package controllers

import java.text.SimpleDateFormat
import java.time.LocalDate

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Names
import helpers._
import models._
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.Scope
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.test.CSRFTokenHelper._
import play.api.test.{FakeRequest, _}
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FeedbackFormsResponseControllerSpec extends PlaySpecification with Mockito with SpecificationLike with BeforeAllAfterAll {

  private val system = ActorSystem()

  private val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))
  private val writeResultFalse = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))
  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val _id = BSONObjectID.generate()
  private val sessionObjectWithSameEmail =
    Future.successful(List(SessionInfo(_id.stringify, "test@knoldus.com", BSONDateTime(date.getTime), "sessions",
      "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false,
      active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), temporaryYoutubeURL = None,
      reminder = false, notification = false, _id)))

  private val sessionObject =
    Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(date.getTime), "sessions",
      "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false,
      active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"), temporaryYoutubeURL = None,
      reminder = false, notification = false, _id)))

  private val noActiveSessionObject = Future.successful(Nil)
  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0,
      _id)))

  private val coreMemberObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = true, superUser = false, BSONDateTime(date.getTime), 0, _id)))

  private val feedbackForms =
    FeedbackForm(
      "form name",
      List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true),
      Question("How is the UI?", List("1"), "COMMENT", mandatory = true)),
      active = true,
      _id)

  private val questionResponseInformation =
    QuestionResponse("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "2")

  private val feedbackResponse =
    FeedbackFormsResponse("test@knoldus.com", false, "presenter@example.com", _id.stringify, _id.stringify, "topic",
      meetup = false, BSONDateTime(date.getTime), "session1", List(questionResponseInformation),
      BSONDateTime(date.getTime), 0.00, _id)

  abstract class WithTestApplication extends TestEnvironment with Scope {
    val feedbackFormsRepository = mock[FeedbackFormsRepository]
    val feedbackResponseRepository = mock[FeedbackFormsResponseRepository]
    val sessionsRepository = mock[SessionsRepository]

    val mailerClient = mock[MailerClient]
    val dateTimeUtility = mock[DateTimeUtility]

    lazy val app = fakeApp()
    lazy val emailManager =
      app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))

    lazy val controller =
      new FeedbackFormsResponseController(
        knolxControllerComponent.messagesApi,
        mailerClient,
        usersRepository,
        feedbackFormsRepository,
        feedbackResponseRepository,
        sessionsRepository,
        config,
        emailManager,
        dateTimeUtility,
        knolxControllerComponent)
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Feedback Response Controller" should {

    "not render feedback form for today if session associated feedback form not found" in new WithTestApplication {
      usersRepository.getActiveAndBanned("test@knoldus.com") returns Future.successful(None)
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(None)

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form exists and session has not expired" in new WithTestApplication {
      usersRepository.getActiveAndBanned("test@knoldus.com") returns Future.successful(None)
      val sessionObjectWithCurrentDate =
        Future.successful(List(SessionInfo(_id.stringify, "email", BSONDateTime(System.currentTimeMillis), "sessions",
          "category", "subCategory", "feedbackFormId", "topic", 1, meetup = true, "rating", 0.00, cancelled = false,
          active = true, BSONDateTime(date.getTime), Some("youtubeURL"), Some("slideShareURL"),
          temporaryYoutubeURL = None, reminder = false, notification = false, _id)))

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackResponseRepository.getByUsersSession(_id.stringify, _id.stringify) returns Future.successful(None)
      sessionsRepository.activeSessions() returns sessionObjectWithCurrentDate
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today if session associated feedback form exists and session has expired" in new WithTestApplication {
      usersRepository.getActiveAndBanned("test@knoldus.com") returns Future.successful(None)
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObject
      feedbackResponseRepository.getByUsersSession(_id.stringify, _id.stringify) returns Future.successful(None)
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(Some(feedbackForms))

      val response = controller.getFeedbackFormsForToday(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(response) must be equalTo OK
    }

    "render feedback form for today with immidiate explored sessions if no active sessions found" in new WithTestApplication {
      usersRepository.getActiveAndBanned("test@knoldus.com") returns Future.successful(None)
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.activeSessions() returns noActiveSessionObject
      sessionsRepository.immediatePreviousExpiredSessions returns sessionObject

      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=").withCSRFToken)

      status(response) must be equalTo OK
    }

    "not render feedback form for today if user is blocked" in new WithTestApplication {
      usersRepository.getActiveAndBanned("test@knoldus.com") returns emailObject
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      dateTimeUtility.toLocalDate(date.getTime) returns LocalDate.now
      dateTimeUtility.localDateIST returns LocalDate.now
      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=").withCSRFToken)

      status(response) must be equalTo UNAUTHORIZED
    }

    "not render feedback form for today if the session was given by the user himself" in new WithTestApplication {
      usersRepository.getActiveAndBanned("test@knoldus.com") returns Future.successful(None)
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.activeSessions() returns sessionObjectWithSameEmail
      feedbackFormsRepository.getByFeedbackFormId("feedbackFormId") returns Future.successful(None)
      val response = controller.getFeedbackFormsForToday(FakeRequest()
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=").withCSRFToken)

      status(response) must be equalTo OK
    }

    "not fetch response as no stored response found" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackResponseRepository.getByUsersSession(_id.stringify, _id.stringify) returns Future.successful(None)

      val response = controller.fetchFeedbackFormResponse(_id.stringify)(FakeRequest(GET, "fetch")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(response) must be equalTo NOT_FOUND

    }

    "fetch response" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      feedbackResponseRepository.getByUsersSession(_id.stringify, _id.stringify) returns Future.successful(Some(feedbackResponse))

      val response = controller.fetchFeedbackFormResponse(_id.stringify)(FakeRequest(GET, "fetch")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc="))

      status(response) must be equalTo OK

    }

    "throw a bad request as submit response form with invalid field submitted" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse("""{"feedbackFormId":"", "responses":[], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no feedback form id submitted" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"", "responses":["a"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no session id submitted" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"", "feedbackFormId":"${_id.stringify}", "responses":["a"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no feedback form response submitted" in new WithTestApplication {

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":[], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is no active session available with the session id submitted by form" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns Future.successful(None)

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is active session available but no feedback form available with feedback form  " +
      "id submitted by form" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(None)

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is more responses then the questions available in the feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a","b"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "store feedback form response" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResult
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"], "score":0.00}""")))

      status(response) must be equalTo OK
    }

    "not store feedback form response due to internal server error" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResultFalse
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"], "score":1.00}""")))

      status(response) must be equalTo INTERNAL_SERVER_ERROR
    }

    "throw a bad request if there is responses which are not present as multiple choices in feedback form" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["a"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there is responses of mandatory comment which is empty" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2",""], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "throw a bad request if there a mcq question and its not mandatory" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = false),
        Question("How is the UI?", List("1"), "COMMENT", mandatory = true)),
        active = true, _id)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "store feedback form response for comment type is false" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = true),
        Question("How is the UI?", List("1"), "COMMENT", mandatory = false)),
        active = true, _id)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResult
      dateTimeUtility.nowMillis returns date.getTime

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2",""], "score":0.00}""")))

      status(response) must be equalTo OK
    }

    "throw a bad request if question type or mandatory type is invalid" in new WithTestApplication {
      val feedbackForms = FeedbackForm("form name", List(Question("How good is knolx portal ?", List("1", "2", "3", "4", "5"), "MCQ", mandatory = false),
        Question("How is the UI?", List("1"), "Some other type", mandatory = false)),
        active = true, _id)

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2",""], "score":0.00}""")))

      status(response) must be equalTo BAD_REQUEST
    }

    "store feedback form response for core member" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns coreMemberObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResult
      dateTimeUtility.nowMillis returns date.getTime
      feedbackResponseRepository.getScoresOfMembers(_id.stringify, true) returns Future.successful(List(0D))
      sessionsRepository.updateRating(_id.stringify, List(0D)) returns updateWriteResult

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"], "score":0.00}""")))

      status(response) must be equalTo OK
    }

    "not store feedback form response for core member if something goes wrong" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))

      usersRepository.getByEmail("test@knoldus.com") returns coreMemberObject
      sessionsRepository.getActiveById(_id.stringify) returns sessionObject.map(_.headOption)
      feedbackFormsRepository.getByFeedbackFormId(_id.stringify) returns Future.successful(Some(feedbackForms))
      feedbackResponseRepository.upsert(any[FeedbackFormsResponse])(any[ExecutionContext]) returns writeResult
      dateTimeUtility.nowMillis returns date.getTime
      feedbackResponseRepository.getScoresOfMembers(_id.stringify, true) returns Future.successful(List(0D))
      sessionsRepository.updateRating(_id.stringify, List(0D)) returns updateWriteResult

      val response = controller.storeFeedbackFormResponse()(FakeRequest(POST, "store")
        .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
        .withBody(Json.parse(s"""{"sessionId":"${_id.stringify}", "feedbackFormId":"${_id.stringify}", "responses":["2","some comment"], "score":0.00}""")))

      status(response) must be equalTo INTERNAL_SERVER_ERROR
    }
  }

}
