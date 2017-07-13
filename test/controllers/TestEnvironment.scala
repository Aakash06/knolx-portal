package controllers

import java.util.concurrent.TimeoutException

import akka.actor._
import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Module}
import helpers.BeforeAllAfterAll
import models.UsersRepository
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import play.api.Application
import play.api.http._
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.mvc._
import play.api.test._
import schedulers.SessionsScheduler._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

trait TestEnvironment extends SpecificationLike with BeforeAllAfterAll with Mockito {

  val usersRepository: UsersRepository

  object TestHelpers extends PlayRunners
    with HeaderNames
    with Status
    with MimeTypes
    with HttpProtocol
    with DefaultAwaitTimeout
    with ResultExtractors
    with Writeables
    with EssentialActionCaller
    with RouteInvokers
    with FutureAwaits
    with TestStubControllerComponentsFactory

  trait TestStubControllerComponentsFactory extends StubPlayBodyParsersFactory with StubBodyParserFactory with StubMessagesFactory {

    Helpers.stubControllerComponents()

    def stubControllerComponents: KnolxControllerComponents = {
      val bodyParser = stubBodyParser(AnyContentAsEmpty)
      val executionContext = ExecutionContext.global

      DefaultKnolxControllerComponents(
        DefaultActionBuilder(bodyParser)(executionContext),
        new UserActionBuilder(bodyParser, usersRepository)(executionContext),
        new AdminActionBuilder(bodyParser, usersRepository)(executionContext),
        stubPlayBodyParsers(NoMaterializer),
        stubMessagesApi(),
        stubLangs(),
        new DefaultFileMimeTypes(FileMimeTypesConfiguration()),
        executionContext)
    }

  }

  val actorSystem: ActorSystem = ActorSystem("TestEnvironment")
  val knolxControllerComponent = TestHelpers.stubControllerComponents

  override def afterAll(): Unit = {
    shutdownActorSystem(actorSystem)
  }

  protected def fakeApp: Application = {
    val sessionsScheduler = actorSystem.actorOf(Props(new DummySessionsScheduler))

    val testModule = Option(new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[ActorRef])
          .annotatedWith(Names.named("SessionsScheduler"))
          .toInstance(sessionsScheduler)

        bind(classOf[KnolxControllerComponents])
          .toInstance(knolxControllerComponent)
      }
    })

    new GuiceApplicationBuilder()
      .overrides(testModule.map(GuiceableModule.guiceable).toSeq: _*)
      .disable[Module]
      .build
  }

  protected def shutdownActorSystem(actorSystem: ActorSystem,
                                    duration: Duration = 10.seconds,
                                    verifySystemShutdown: Boolean = false): Unit = {
    actorSystem.terminate()

    try Await.ready(actorSystem.whenTerminated, duration) catch {
      case _: TimeoutException ⇒
        val msg = "Failed to stop [%s] within [%s]".format(actorSystem.name, duration)

        if (verifySystemShutdown) {
          throw new RuntimeException(msg)
        } else {
          actorSystem.log.warning(msg)
        }
    }
  }

  private class DummySessionsScheduler extends Actor {

    def receive: Receive = {
      case RefreshSessionsSchedulers         => sender ! ScheduledSessionsRefreshed
      case GetScheduledSessions              => sender ! ScheduledSessions(List.empty)
      case CancelScheduledSession(sessionId) => sender ! true
      case ScheduleSession(sessionId)        => sender ! true
    }

  }

}
