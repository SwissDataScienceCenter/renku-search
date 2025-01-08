package io.renku.search.provision

import scala.concurrent.duration.FiniteDuration

import cats.effect.*
import cats.effect.kernel.Fiber
import cats.effect.kernel.Ref
import cats.effect.std.Supervisor
import cats.syntax.all.*

import io.renku.search.provision.BackgroundProcessManage.TaskName

trait BackgroundProcessManage[F[_]]:
  def register(name: TaskName, task: F[Unit]): F[Unit]

  /** Starts all registered tasks in the background. */
  def background(taskFilter: TaskName => Boolean): F[Unit]

  def startAll: F[Unit]

  /** Stop all tasks by filtering on their registered name. */
  def cancelProcesses(filter: TaskName => Boolean): F[Unit]

  /** Get the names of all processses currently running. */
  def currentProcesses: F[Set[TaskName]]

object BackgroundProcessManage:
  private type Process[F[_]] = Fiber[F, Throwable, Unit]

  trait TaskName:
    def equals(x: Any): Boolean
    def hashCode(): Int

  object TaskName:
    final case class Name(value: String) extends TaskName
    def fromString(name: String): TaskName = Name(name)

  private case class State[F[_]](
      tasks: Map[TaskName, F[Unit]],
      processes: Map[TaskName, Process[F]]
  ):
    def put(name: TaskName, p: F[Unit]): State[F] =
      State(tasks.updated(name, p), processes)

    def getTasks(filter: TaskName => Boolean): Map[TaskName, F[Unit]] =
      tasks.view.filterKeys(filter).toMap

    def getProcesses(filter: TaskName => Boolean): Map[TaskName, Process[F]] =
      processes.view.filterKeys(filter).toMap

    def setProcesses(ps: Map[TaskName, Process[F]]): State[F] =
      copy(processes = ps)

    def removeProcesses(names: Set[TaskName]): State[F] =
      copy(processes = processes.view.filterKeys(n => !names.contains(n)).toMap)

  private object State:
    def empty[F[_]]: State[F] = State[F](Map.empty, Map.empty)

  def apply[F[_]: Async](
      retryDelay: FiniteDuration,
      maxRetries: Option[Int] = None
  ): Resource[F, BackgroundProcessManage[F]] =
    Supervisor[F](await = false).flatMap { supervisor =>
      Resource.eval(Ref.of[F, State[F]](State.empty[F])).flatMap { state =>
        Resource
          .make(Async[F].pure(new Impl(supervisor, state, retryDelay, maxRetries)))(
            _.cancelProcesses(_ => true)
          )
      }
    }

  private class Impl[F[_]: Async](
      supervisor: Supervisor[F],
      state: Ref[F, State[F]],
      retryDelay: FiniteDuration,
      maxRetries: Option[Int] = None
  ) extends BackgroundProcessManage[F] {
    val logger = scribe.cats.effect[F]

    def register(name: TaskName, task: F[Unit]): F[Unit] =
      state.update(_.put(name, wrapTask(name, task)))

    def startAll: F[Unit] =
      state.get
        .flatMap(s => logger.info(s"Starting ${s.tasks.size} background tasks")) >>
        background(_ => true)

    def currentProcesses: F[Set[TaskName]] =
      state.get.map(_.processes.keySet)

    def background(taskFilter: TaskName => Boolean): F[Unit] =
      for {
        ts <- state.get.map(_.getTasks(taskFilter))
        _ <- ts.toList
          .traverse { case (name, task) =>
            supervisor.supervise(task).map(t => name -> t)
          }
          .map(_.toMap)
          .flatMap(ps => state.update(_.setProcesses(ps)))
      } yield ()

    /** Stop all tasks by filtering on their registered name. */
    def cancelProcesses(filter: TaskName => Boolean): F[Unit] =
      for
        current <- state.get
        ps = current.getProcesses(filter)
        _ <- ps.toList.traverse_ { case (name, p) =>
          logger.info(s"Cancel background process $name") >> p.cancel >> p.join
            .flatMap(out => logger.info(s"Task $name cancelled: $out"))
        }
        _ <- state.update(_.removeProcesses(ps.keySet))
      yield ()

    private def wrapTask(name: TaskName, task: F[Unit]): F[Unit] =
      def run(c: Ref[F, Long]): F[Unit] =
        logger.info(s"Starting process for: ${name}") >>
          task.handleErrorWith { err =>
            c.updateAndGet(_ + 1).flatMap {
              case n if maxRetries.exists(_ <= n) =>
                logger.error(
                  s"Max retries ($maxRetries) for process ${name} exceeded"
                ) >> Async[F].raiseError(err)
              case n =>
                val maxRetriesLabel = maxRetries.map(m => s"/$m").getOrElse("")
                logger.error(
                  s"Starting process for '${name}' failed ($n$maxRetriesLabel), retrying",
                  err
                ) >> Async[F].delayBy(run(c), retryDelay)
            }
          }
      Ref.of[F, Long](0).flatMap(run) >> state.update(_.removeProcesses(Set(name)))
  }
