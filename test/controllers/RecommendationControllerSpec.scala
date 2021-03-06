package controllers

import java.text.SimpleDateFormat
import java.time.LocalDateTime

import akka.actor.ActorRef
import com.google.inject.name.Names
import helpers.TestEnvironment
import models._
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import org.specs2.specification.Scope
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.Json
import play.api.{Application, mvc}
import play.api.mvc.Results
import play.api.test.{FakeRequest, PlaySpecification}
import play.api.test.CSRFTokenHelper._
import reactivemongo.api.commands.{DefaultWriteResult, UpdateWriteResult}
import reactivemongo.bson.{BSONDateTime, BSONObjectID}
import utilities.DateTimeUtility

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RecommendationControllerSpec extends PlaySpecification with Results {

  private val date = new SimpleDateFormat("yyyy-MM-dd").parse("1947-08-15")
  private val email = "test@knoldus.com"
  private val _id: BSONObjectID = BSONObjectID.generate()
  private val localDate = LocalDateTime.now()

  private val emailObject =
    Future.successful(Some(UserInfo("test@knoldus.com", "$2a$10$NVPy0dSpn8bbCNP5SaYQOOiQdwGzX0IvsWsGyKv.Doj1q0IsEFKH.",
      "BCrypt", active = true, admin = true, coreMember = false, superUser = false, BSONDateTime(date.getTime), 0, _id)))

  private val recommendations = Future.successful(
    List(
      RecommendationInfo(Some("test@knoldus.com"),
        "name",
        "topic",
        "recommendation",
        BSONDateTime(date.getTime),
        BSONDateTime(date.getTime)
      )))

  private val anonymousRecommendations = Future.successful(
    List(
      RecommendationInfo(None,
        "name",
        "topic",
        "recommendation",
        BSONDateTime(date.getTime),
        BSONDateTime(date.getTime)
      )))

  private val approvedRecommendations = Future.successful(
    List(
      RecommendationInfo(Some("test@knoldus.com"),
        "name",
        "topic",
        "recommendation",
        BSONDateTime(date.getTime),
        BSONDateTime(date.getTime),
        approved = true
      )))

  abstract class WithTestApplication extends Around with Scope with TestEnvironment {
    lazy val app: Application = fakeApp()

    val recommendationsRepository = mock[RecommendationsRepository]
    val recommendationsResponseRepository = mock[RecommendationsResponseRepository]
    val dateTimeUtility = mock[DateTimeUtility]
    val emailManager: ActorRef = app.injector.instanceOf(BindingKey(classOf[ActorRef], Some(QualifierInstance(Names.named("EmailManager")))))


    lazy val controller =
      new RecommendationController(
        knolxControllerComponent.messagesApi,
        recommendationsRepository,
        usersRepository,
        knolxControllerComponent,
        dateTimeUtility,
        config,
        recommendationsResponseRepository,
        emailManager
      )

    override def around[T: AsResult](t: => T): Result = {
      TestHelpers.running(app)(AsResult.effectively(t))
    }
  }

  "Recommendation Controller" should {

    "render recommendation page when user is logged in" in new WithTestApplication {
      val result = controller.renderRecommendationPage(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo OK
    }

    "render recommendation page when user is not logged in" in new WithTestApplication {
      val result = controller.renderRecommendationPage(FakeRequest().withCSRFToken)

      status(result) must be equalTo OK
    }

    "store recommendation when email exists" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      usersRepository.getAllAdminAndSuperUser returns Future.successful(List("test1@knoldus.com"))

      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"name", "topic":"topic", "description":"recommendation"}"""))
      )

      status(result) must be equalTo OK
    }

    "do not store recommendation when malformed data is received" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult

      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(
            s"""{"email":"test@knoldus.com", "name":111, "topic":"topic",
               | "description":"recommendation"}""".stripMargin))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "store recommendation when email is not specified" in new WithTestApplication {
      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      usersRepository.getAllAdminAndSuperUser returns Future.successful(List("test1@knoldus.com"))

      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(s"""{"name": "name", "topic":"topic", "description":"recommendation"}"""))
      )

      status(result) must be equalTo OK
    }

    "do not store recommendation when wrong email format is received" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(s"""{"email":"testknolduscom", "name":"name", "topic":"topic", "description":"recommendation"}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "do not store recommendation when name is not specified" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"", "topic":"topic", "description":"recommendation"}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "do not store recommendation when topic is not specified" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"name", "topic":"", "description":"recommendation"}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "do not store recommendation when topic length is more than 140 characters" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(
            s"""{"email":"test@knoldus.com", "name":"name",
               | "topic":"As recommendation's topic is greater than 140 character so it can stored in database. Please insert recommendation's topic in less than 140 characters",
               |  "description":"recommendation"}""".stripMargin))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "do not store recommendation when recommendation's description is not specified" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"name", "topic":"topic", "description":""}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "do not store recommendation when recommendation's data is invalid" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"name", "topic":"", "description":""}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "do not store recommendation when recommendation's description is greater than 280 characters" in new WithTestApplication {
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))
      val recommendationDescription = "a" * 290

      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(
            s"""{"email":"test@knoldus.com", "name":"name", "topic":"topic",
               | "description":"$recommendationDescription"}""".stripMargin))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "not store empty recommendation" in new WithTestApplication {
      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"name", "topic":"topic", "description":"recommendation"}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "not store recommendation" in new WithTestApplication {
      dateTimeUtility.nowMillis returns date.getTime
      dateTimeUtility.nowMillis returns date.getTime

      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      recommendationsRepository.insert(any[RecommendationInfo])(any[ExecutionContext]) returns writeResult
      val result = controller.addRecommendation()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withBody(Json.parse(s"""{"email":"test@knoldus.com", "name":"name", "topic":"topic", "description":"recommendation"}"""))
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "render recommendationList to admin/super user" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"
      val sortBy = "latest"

      recommendationsRepository.paginate(pageNumber, filter, sortBy) returns recommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("upvote")

      dateTimeUtility.toLocalDateTime(date.getTime) returns localDate

      val result: Future[mvc.Result] = controller.recommendationList(pageNumber, filter, sortBy)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=",
            "admin" -> "DqDK4jVae2aLvChuBPCgmfRWXKArji6AkjVhqSxpMFP6I6L/FkeK5HQz1dxzxzhP")
      )

      status(result) must be equalTo OK
    }

    "render recommendationList to admin/super user downvoted the recommendations" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"
      val sortBy = "latest"

      recommendationsRepository.paginate(pageNumber, filter, sortBy) returns recommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("downvote")

      dateTimeUtility.toLocalDateTime(date.getTime) returns localDate

      val result: Future[mvc.Result] = controller.recommendationList(pageNumber, filter, sortBy)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=",
            "admin" -> "DqDK4jVae2aLvChuBPCgmfRWXKArji6AkjVhqSxpMFP6I6L/FkeK5HQz1dxzxzhP")
      )

      status(result) must be equalTo OK
    }

    "render recommendationList to admin/super when recommendation is submitted by anonymous" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"
      val sortBy = "latest"

      recommendationsRepository.paginate(pageNumber, filter, sortBy) returns anonymousRecommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("downvote")

      dateTimeUtility.toLocalDateTime(date.getTime) returns localDate

      val result: Future[mvc.Result] = controller.recommendationList(pageNumber, filter, sortBy)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=",
            "admin" -> "DqDK4jVae2aLvChuBPCgmfRWXKArji6AkjVhqSxpMFP6I6L/FkeK5HQz1dxzxzhP")
      )

      status(result) must be equalTo OK
    }

    "render recommendationList to logged in / non-logged in user" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"
      val sortBy = "latest"

      recommendationsRepository.paginate(pageNumber, filter, sortBy) returns approvedRecommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("upvote")

      val result = controller.recommendationList(pageNumber, filter, sortBy)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "render recommendationList to logged in / non-logged in user when user downvoted the recommendation" in new WithTestApplication {
      val pageNumber = 1
      val filter = "all"
      val sortBy = "latest"

      recommendationsRepository.paginate(pageNumber, filter, sortBy) returns approvedRecommendations
      recommendationsResponseRepository.getVote(any[String], any[String]) returns Future.successful("downvote")

      val result = controller.recommendationList(pageNumber, filter, sortBy)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "approve recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.approveRecommendation(recommendationId) returns writeResult

      val result = controller.approveRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not approve recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      recommendationsRepository.approveRecommendation(recommendationId) returns writeResult

      val result = controller.approveRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "decline recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = true, 1, Seq(), None, None, None))

      recommendationsRepository.declineRecommendation(recommendationId) returns writeResult

      val result = controller.declineRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not decline recommendation" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val recommendationId = "RecommendationId"
      val writeResult = Future.successful(DefaultWriteResult(ok = false, 1, Seq(), None, None, None))

      recommendationsRepository.declineRecommendation(recommendationId) returns writeResult

      val result = controller.declineRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "upvote the recommendation if user has first downvoted" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("downvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = true) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationsResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "redirect to login page if user is not logged in while upvoting" in new WithTestApplication {
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession())

      status(result) must be equalTo SEE_OTHER
    }

    "not upvote the recommendation if user has already upvoted" in new WithTestApplication {
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("upvote")

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "upvote the recommendation if user has not given any response yet" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationsResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "throw bad request while upvoting the recommendation" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val wrongUpdateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("downvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationsResponseRepositoryInfo]) returns wrongUpdateWriteResult

      val result = controller.upVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "downvote the recommendation if user has first upvoted" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("upvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = true) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationsResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "redirect to login page if user is not logged in while downvoting" in new WithTestApplication {
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession())

      status(result) must be equalTo SEE_OTHER
    }

    "not downvote the recommendation if user has already downvoted" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("downvote")

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "downvote the recommendation if user has not given any response yet" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationsResponseRepositoryInfo]) returns updateWriteResult

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "throw bad request while downvoting the recommendation" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val wrongUpdateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsResponseRepository.getVote(email, recommendationId) returns Future.successful("upvote")
      recommendationsRepository.upVote(recommendationId, alreadyVoted = false) returns updateWriteResult
      recommendationsResponseRepository.upsert(any[RecommendationsResponseRepositoryInfo]) returns wrongUpdateWriteResult

      val result = controller.downVote(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "mark recommendation as done" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.doneRecommendation(recommendationId) returns updateWriteResult

      val result = controller.doneRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not mark recommendation as done" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.doneRecommendation(recommendationId) returns updateWriteResult

      val result = controller.doneRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "mark recommendation as pending" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = true, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.pendingRecommendation(recommendationId) returns updateWriteResult

      val result = controller.pendingRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "not mark recommendation as pending" in new WithTestApplication {
      val updateWriteResult = Future.successful(UpdateWriteResult(ok = false, 1, 1, Seq(), Seq(), None, None, None))
      val recommendationId = "RecommendationId"

      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.pendingRecommendation(recommendationId) returns updateWriteResult

      val result = controller.pendingRecommendation(recommendationId)(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo BAD_REQUEST
    }

    "return all sessions waiting for admin's action" in new WithTestApplication {
      usersRepository.getByEmail("test@knoldus.com") returns emailObject
      recommendationsRepository.allPendingRecommendations returns Future.successful(1)

      val result = controller.allPendingRecommendations()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
      )

      status(result) must be equalTo OK
    }

    "schedule a session" in new WithTestApplication {
      val result = controller.scheduleSession()(
        FakeRequest()
          .withSession("username" -> "F3S8qKBy5yvWCLZKmvTE0WSoLzcLN2ztG8qPvOvaRLc=")
          .withCSRFToken)

      status(result) must be equalTo SEE_OTHER
    }

  }

}
